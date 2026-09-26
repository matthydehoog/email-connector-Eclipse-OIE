/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */

package com.mirth.connect.connectors.email.server;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import jakarta.mail.BodyPart;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.internet.ContentType;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.internet.MimePart;
import jakarta.mail.internet.MimePartDataSource;
import jakarta.mail.internet.MimeUtility;
import jakarta.mail.search.FlagTerm;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mirth.connect.connectors.email.shared.EmailReceiverProperties;

/**
 * Connects to one POP3 or IMAP mailbox, reads whatever is waiting, hands each
 * message to a callback as a simple XML string and afterwards (optionally)
 * deletes it from the server or, for IMAP, marks it as read. No dependency on
 * the engine; EmailReceiver wires it into the channel.
 */
public class EmailPoller {

    private final Logger logger = LogManager.getLogger(getClass());
    private final boolean imap;
    private final String host;
    private final int port;
    private final boolean useSsl;
    private final String username;
    private final String password;
    private final String folderName;
    private final boolean unreadOnly;
    private final boolean markAsRead;
    private final boolean deleteAfterFetch;

    public EmailPoller(EmailReceiverProperties properties, int port) {
        this.imap = properties.isImap();
        this.host = properties.getHost().trim();
        this.port = port;
        this.useSsl = properties.isUseSsl();
        this.username = properties.getUsername();
        this.password = properties.getPassword();
        this.folderName = properties.getFolder().trim();
        this.unreadOnly = properties.isUnreadOnly();
        this.markAsRead = properties.isMarkAsRead();
        this.deleteAfterFetch = properties.isDeleteAfterFetch();
    }

    public interface MessageHandler {
        /**
         * @param rawXml the fetched message serialized as XML
         * @return true if the message was handled successfully (safe to
         *         delete from the server, if deleteAfterFetch is on)
         */
        boolean handle(String rawXml);
    }

    /**
     * Connects, processes every message currently in the mailbox (POP3: the
     * INBOX; IMAP: the configured folder, optionally only unread mail), then
     * disconnects. Safe to call repeatedly from a scheduled task - it
     * does not keep a connection open between polls.
     */
    public void poll(MessageHandler handler) throws MessagingException {
        String label = label();
        String protocol = (imap ? "imap" : "pop3") + (useSsl ? "s" : "");
        // Only IMAP has a read flag; POP3 can just delete.
        boolean setSeen = imap && markAsRead && !deleteAfterFetch;

        Properties mailProps = new Properties();
        mailProps.setProperty("mail.store.protocol", protocol);
        mailProps.setProperty("mail." + protocol + ".host", host);
        mailProps.setProperty("mail." + protocol + ".port", String.valueOf(port));
        mailProps.setProperty("mail." + protocol + ".connectiontimeout", "15000");
        mailProps.setProperty("mail." + protocol + ".timeout", "15000");
        if (imap) {
            // Reading a mail must not mark it as read: only a successfully dispatched mail may be.
            mailProps.setProperty("mail." + protocol + ".peek", "true");
        }

        Session session = Session.getInstance(mailProps);
        Store store = null;
        Folder folder = null;

        try {
            store = session.getStore(protocol);
            store.connect(host, port, username, password);

            folder = store.getFolder(imap ? folderName : "INBOX");
            // Deleting (and, on IMAP, setting the read flag) needs write access.
            folder.open(deleteAfterFetch || setSeen ? Folder.READ_WRITE : Folder.READ_ONLY);

            Message[] messages = imap && unreadOnly
                    ? folder.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false))
                    : folder.getMessages();
            logger.info("{} poll of {}@{}{}: {} message(s) found", label, username, host,
                    imap ? "/" + folderName : "", messages.length);

            for (Message message : messages) {
                try {
                    String xml = toXml(message);
                    boolean handled = handler.handle(xml);
                    if (handled && deleteAfterFetch) {
                        message.setFlag(Flags.Flag.DELETED, true);
                    } else if (handled && setSeen) {
                        message.setFlag(Flags.Flag.SEEN, true);
                    }
                } catch (Exception e) {
                    logger.error("Failed to process one {} message; leaving it on the server", label, e);
                }
            }
        } finally {
            closeQuietly(folder, deleteAfterFetch);
            closeQuietly(store);
        }
    }

    /**
     * Serializes a JavaMail Message into a small, self-contained XML
     * document. This is deliberately simple - keep transformation logic
     * (parsing this XML, extracting attachments, etc.) in the channel's
     * source transformer where it belongs, not in the plugin.
     */
    private String toXml(Message message) throws MessagingException, IOException {
        StringBuilder xml = new StringBuilder();
        xml.append("<email>\n");
        xml.append("  <subject>").append(escape(message.getSubject())).append("</subject>\n");
        xml.append("  <from>").append(escape(addressesToString(message.getFrom()))).append("</from>\n");
        xml.append("  <to>").append(escape(addressesToString(message.getRecipients(Message.RecipientType.TO)))).append("</to>\n");
        xml.append("  <sentDate>").append(message.getSentDate()).append("</sentDate>\n");
        String body = cleanText(extractText(message)).replace("]]>", "]]]]><![CDATA[>");
        xml.append("  <body><![CDATA[").append(body).append("]]></body>\n");
        xml.append("</email>\n");
        return xml.toString();
    }

    private String addressesToString(jakarta.mail.Address[] addresses) {
        if (addresses == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (jakarta.mail.Address address : addresses) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(address instanceof InternetAddress
                    ? ((InternetAddress) address).getAddress()
                    : address.toString());
        }
        return sb.toString();
    }

    /**
     * Returns the plain-text body of a message. In a multipart/alternative
     * the text/plain version wins; HTML is only used (converted to plain
     * text) when no plain-text version exists. Attachments and non-text
     * parts are skipped.
     */
    private String extractText(Part part) throws MessagingException, IOException {
        if (part.isMimeType("multipart/alternative")) {
            String plain = null;
            String fallback = "";
            Multipart multipart = openMultipart(part);
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart bodyPart = multipart.getBodyPart(i);
                if (isAttachment(bodyPart)) {
                    continue;
                }
                if (bodyPart.isMimeType("text/plain")) {
                    if (plain == null) {
                        plain = readText(bodyPart);
                    }
                } else {
                    String other = extractText(bodyPart);
                    if (!other.isBlank()) {
                        fallback = other;
                    }
                }
            }
            return plain != null && !plain.isBlank() ? plain : fallback;
        }
        if (part.isMimeType("multipart/*")) {
            StringBuilder text = new StringBuilder();
            Multipart multipart = openMultipart(part);
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart bodyPart = multipart.getBodyPart(i);
                if (isAttachment(bodyPart)) {
                    continue;
                }
                String piece = extractText(bodyPart);
                if (!piece.isBlank()) {
                    if (text.length() > 0) {
                        text.append('\n');
                    }
                    text.append(piece);
                }
            }
            return text.toString();
        }
        if (part.isMimeType("text/html")) {
            return htmlToText(readText(part));
        }
        if (part.isMimeType("text/*")) {
            return readText(part);
        }
        return "";
    }

    private Multipart openMultipart(Part part) throws MessagingException {
        // Parse via a DataSource instead of getContent(): the engine ships an older
        // com.sun.mail / javax.activation on the shared classpath, and its mailcap
        // handlers clash with the jakarta.activation this plugin bundles.
        return new MimeMultipart(new MimePartDataSource((MimePart) part));
    }

    private boolean isAttachment(Part part) throws MessagingException {
        return Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition());
    }

    private String readText(Part part) throws MessagingException, IOException {
        try (InputStream in = part.getInputStream();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            in.transferTo(out);
            return out.toString(charsetOf(part));
        }
    }

    /** Converts an HTML fragment/document to readable plain text (no external libraries). */
    static String htmlToText(String html) {
        String s = html;
        s = s.replaceAll("(?is)<(script|style|head)\\b.*?</\\1\\s*>", "");
        s = s.replaceAll("(?s)<!--.*?-->", "");
        s = s.replaceAll("(?i)<br\\s*/?>", "\n");
        s = s.replaceAll("(?i)<li\\b[^>]*>", "- ");
        s = s.replaceAll("(?i)</(p|div|tr|li|h[1-6]|table|ul|ol|blockquote)\\s*>", "\n");
        s = s.replaceAll("(?i)</t[dh]\\s*>", " ");
        s = s.replaceAll("(?s)<[^>]*>", "");
        return decodeEntities(s);
    }

    private static String decodeEntities(String s) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("&(#x?[0-9a-fA-F]+|[a-zA-Z]+);").matcher(s);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String name = m.group(1);
            String repl = null;
            if (name.startsWith("#")) {
                try {
                    int cp = name.startsWith("#x") || name.startsWith("#X")
                            ? Integer.parseInt(name.substring(2), 16)
                            : Integer.parseInt(name.substring(1));
                    repl = new String(Character.toChars(cp));
                } catch (IllegalArgumentException e) {
                    repl = null;
                }
            } else {
                switch (name) {
                    case "amp": repl = "&"; break;
                    case "lt": repl = "<"; break;
                    case "gt": repl = ">"; break;
                    case "quot": repl = "\""; break;
                    case "apos": repl = "'"; break;
                    case "nbsp": repl = " "; break;
                    case "ndash": repl = "–"; break;
                    case "mdash": repl = "—"; break;
                    case "hellip": repl = "…"; break;
                    case "euro": repl = "€"; break;
                    case "copy": repl = "©"; break;
                    default: repl = null;
                }
            }
            m.appendReplacement(out, java.util.regex.Matcher.quoteReplacement(repl != null ? repl : m.group()));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** Normalizes line endings, drops characters that are illegal in XML 1.0, trims and squeezes blank lines. */
    static String cleanText(String value) {
        if (value == null) {
            return "";
        }
        String s = value.replace("\r\n", "\n").replace('\r', '\n').replace(' ', ' ');
        s = removeInvalidXmlChars(s);
        s = s.replaceAll("[ \\t]+\\n", "\n").replaceAll("\\n{3,}", "\n\n");
        return s.strip();
    }

    static String removeInvalidXmlChars(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        value.codePoints().forEach(cp -> {
            boolean ok = cp == 0x9 || cp == 0xA || cp == 0xD
                    || (cp >= 0x20 && cp <= 0xD7FF)
                    || (cp >= 0xE000 && cp <= 0xFFFD)
                    || (cp >= 0x10000 && cp <= 0x10FFFF);
            if (ok) {
                sb.appendCodePoint(cp);
            }
        });
        return sb.toString();
    }

    private Charset charsetOf(Part part) {
        try {
            String contentType = part.getContentType();
            String charset = contentType == null ? null : new ContentType(contentType).getParameter("charset");
            if (charset != null) {
                return Charset.forName(MimeUtility.javaCharset(charset));
            }
        } catch (Exception e) {
            logger.debug("Unknown charset on message part, falling back to UTF-8", e);
        }
        return StandardCharsets.UTF_8;
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return removeInvalidXmlChars(value).replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private void closeQuietly(Folder folder, boolean expunge) {
        if (folder != null && folder.isOpen()) {
            try {
                folder.close(expunge);
            } catch (MessagingException e) {
                logger.warn("Error closing {} folder", label(), e);
            }
        }
    }

    private void closeQuietly(Store store) {
        if (store != null && store.isConnected()) {
            try {
                store.close();
            } catch (MessagingException e) {
                logger.warn("Error closing {} store", label(), e);
            }
        }
    }

    /** POP3 or IMAP, for log messages. */
    private String label() {
        return imap ? "IMAP" : "POP3";
    }
}
