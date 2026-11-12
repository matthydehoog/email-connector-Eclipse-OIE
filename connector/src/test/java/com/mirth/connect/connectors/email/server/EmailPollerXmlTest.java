/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */

package com.mirth.connect.connectors.email.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.Test;

import com.mirth.connect.connectors.email.shared.EmailReceiverProperties;

/** The XML a mail becomes, for each body shape the connector handles. Needs no mail server. */
class EmailPollerXmlTest {

    private static final EmailPoller POLLER = new EmailPoller(new EmailReceiverProperties(), 110);
    private static final Pattern BODY = Pattern.compile("(?s)<body><!\\[CDATA\\[(.*)\\]\\]></body>");

    private static String xml(String raw) throws Exception {
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()),
                new ByteArrayInputStream(raw.replace("\n", "\r\n").getBytes(StandardCharsets.ISO_8859_1)));
        return POLLER.toXml(message);
    }

    private static String body(String xml) {
        Matcher m = BODY.matcher(xml);
        assertTrue(m.find(), "no body in " + xml);
        return m.group(1);
    }

    @Test
    void plainMailEscapesHeadersAndKeepsTheBody() throws Exception {
        String xml = xml("From: Lab <lab@example.org>\n"
                + "To: intake@example.org, Second <second@example.org>\n"
                + "Subject: Results & <notes>\n"
                + "Content-Type: text/plain; charset=UTF-8\n"
                + "\n"
                + "Line one\n"
                + "Line two\n");

        assertTrue(xml.startsWith("<email>\n"), xml);
        assertTrue(xml.contains("<subject>Results &amp; &lt;notes&gt;</subject>"), xml);
        assertTrue(xml.contains("<from>lab@example.org</from>"), xml);
        assertTrue(xml.contains("<to>intake@example.org, second@example.org</to>"), xml);
        assertEquals("Line one\nLine two", body(xml));
    }

    @Test
    void htmlMailBecomesText() throws Exception {
        String xml = xml("Subject: html\n"
                + "Content-Type: text/html; charset=UTF-8\n"
                + "\n"
                + "<p>Hello&nbsp;<b>world</b></p><ul><li>One</li></ul>\n");

        assertEquals("Hello world\n- One", body(xml));
    }

    @Test
    void alternativeMailPrefersThePlainText() throws Exception {
        String xml = xml("Subject: alt\n"
                + "Content-Type: multipart/alternative; boundary=\"b\"\n"
                + "\n"
                + "--b\n"
                + "Content-Type: text/plain; charset=UTF-8\n"
                + "\n"
                + "Plain version\n"
                + "--b\n"
                + "Content-Type: text/html; charset=UTF-8\n"
                + "\n"
                + "<p>HTML version</p>\n"
                + "--b--\n");

        assertEquals("Plain version", body(xml));
    }

    @Test
    void alternativeMailUsesTheHtmlWhenThePlainTextIsBlank() throws Exception {
        String xml = xml("Subject: alt\n"
                + "Content-Type: multipart/alternative; boundary=\"b\"\n"
                + "\n"
                + "--b\n"
                + "Content-Type: text/plain; charset=UTF-8\n"
                + "\n"
                + " \n"
                + "--b\n"
                + "Content-Type: text/html; charset=UTF-8\n"
                + "\n"
                + "<p>HTML version</p>\n"
                + "--b--\n");

        assertEquals("HTML version", body(xml));
    }

    @Test
    void mixedMailJoinsTextPartsAndSkipsAttachments() throws Exception {
        String xml = xml("Subject: mixed\n"
                + "Content-Type: multipart/mixed; boundary=\"m\"\n"
                + "\n"
                + "--m\n"
                + "Content-Type: text/plain; charset=UTF-8\n"
                + "\n"
                + "First part\n"
                + "--m\n"
                + "Content-Type: text/plain; charset=UTF-8\n"
                + "Content-Disposition: attachment; filename=\"secret.txt\"\n"
                + "\n"
                + "ATTACHMENT TEXT\n"
                + "--m\n"
                + "Content-Type: application/pdf\n"
                + "Content-Transfer-Encoding: base64\n"
                + "\n"
                + "JVBERi0xLjQK\n"
                + "--m\n"
                + "Content-Type: text/plain; charset=UTF-8\n"
                + "\n"
                + "Second part\n"
                + "--m--\n");

        String body = body(xml);
        assertEquals("First part\nSecond part", body);
        assertFalse(xml.contains("ATTACHMENT TEXT"), xml);
    }

    @Test
    void bodyIsDecodedWithItsCharset() throws Exception {
        String xml = xml("Subject: latin\n"
                + "Content-Type: text/plain; charset=ISO-8859-1\n"
                + "Content-Transfer-Encoding: quoted-printable\n"
                + "\n"
                + "Caf=E9 cr=E8me\n");

        assertEquals("Caf" + EmailPollerTextTest.chars(0xE9) + " cr" + EmailPollerTextTest.chars(0xE8) + "me", body(xml));
    }

    @Test
    void unknownCharsetFallsBackToUtf8() throws Exception {
        String xml = xml("Subject: unknown\n"
                + "Content-Type: text/plain; charset=x-no-such-charset\n"
                + "Content-Transfer-Encoding: quoted-printable\n"
                + "\n"
                + "Caf=C3=A9\n");

        assertEquals("Caf" + EmailPollerTextTest.chars(0xE9), body(xml));
    }

    @Test
    void cdataTerminatorInTheBodyIsSplit() throws Exception {
        String xml = xml("Subject: cdata\n"
                + "Content-Type: text/plain; charset=UTF-8\n"
                + "\n"
                + "before ]]> after\n");

        assertTrue(xml.contains("<body><![CDATA[before ]]]]><![CDATA[> after]]></body>"), xml);
    }
}
