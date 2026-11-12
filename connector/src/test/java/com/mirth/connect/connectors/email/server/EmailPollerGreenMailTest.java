/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */

package com.mirth.connect.connectors.email.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.user.GreenMailUser;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetup;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.mirth.connect.connectors.email.shared.EmailReceiverProperties;

/**
 * Polls a real POP3 and IMAP server (GreenMail, in-process) through {@link EmailPoller#poll}:
 * fetching, Only unread, Mark as read, Delete after fetch, and a failed dispatch, which must
 * leave the mail on the server.
 */
class EmailPollerGreenMailTest {

    private static final String LOGIN = "intake";
    private static final String PASSWORD = "secret";
    private static final String ADDRESS = "intake@example.org";

    @RegisterExtension
    static final GreenMailExtension GREENMAIL = new GreenMailExtension(ServerSetupTest.SMTP_POP3_IMAP)
            .withConfiguration(GreenMailConfiguration.aConfig().withUser(ADDRESS, LOGIN, PASSWORD))
            .withPerMethodLifecycle(true);

    /** What one poll handed to the channel. */
    private static final class Received {
        final List<String> subjects = new ArrayList<>();
        final List<String> xml = new ArrayList<>();
        final List<Map<String, Object>> sourceMaps = new ArrayList<>();
    }

    private static void deliver(String... subjects) throws Exception {
        GreenMailUser user = GREENMAIL.getUserManager().getUser(LOGIN);
        for (String subject : subjects) {
            MimeMessage message = GreenMailUtil.createTextEmail(ADDRESS, "lab@example.org", subject,
                    "Body of " + subject, GREENMAIL.getSmtp().getServerSetup());
            user.deliver(message);
        }
    }

    private static EmailPoller poller(String protocol, boolean unreadOnly, boolean markAsRead, boolean deleteAfterFetch) {
        boolean imap = EmailReceiverProperties.MAIL_IMAP.equals(protocol);
        ServerSetup setup = imap ? GREENMAIL.getImap().getServerSetup() : GREENMAIL.getPop3().getServerSetup();

        EmailReceiverProperties properties = new EmailReceiverProperties();
        properties.setMailProtocol(protocol);
        properties.setHost(setup.getBindAddress());
        properties.setUseSsl(false);
        properties.setUsername(LOGIN);
        properties.setPassword(PASSWORD);
        properties.setFolder("INBOX");
        properties.setUnreadOnly(unreadOnly);
        properties.setMarkAsRead(markAsRead);
        properties.setDeleteAfterFetch(deleteAfterFetch);
        return new EmailPoller(properties, setup.getPort());
    }

    /** Polls once; the handler accepts every mail the predicate accepts. */
    private static Received poll(EmailPoller poller, Predicate<String> accept) throws Exception {
        Received received = new Received();
        poller.poll((rawXml, sourceMap) -> {
            String subject = (String) sourceMap.get("subject");
            received.subjects.add(subject);
            received.xml.add(rawXml);
            received.sourceMaps.add(sourceMap);
            return accept.test(subject);
        });
        return received;
    }

    private static Received poll(EmailPoller poller) throws Exception {
        return poll(poller, subject -> true);
    }

    // ---- POP3

    @Test
    void pop3FetchesEveryMailAndLeavesItWithoutDelete() throws Exception {
        deliver("one", "two");
        EmailPoller poller = poller(EmailReceiverProperties.MAIL_POP3, false, false, false);

        Received first = poll(poller);
        assertEquals(List.of("one", "two"), first.subjects);
        assertTrue(first.xml.get(0).contains("<![CDATA[Body of one]]>"), first.xml.get(0));
        assertEquals(ADDRESS, first.sourceMaps.get(0).get("to"));
        assertTrue(!first.sourceMaps.get(0).containsKey("folder"), "POP3 has no folder");

        assertEquals(List.of("one", "two"), poll(poller).subjects);
    }

    @Test
    void pop3DeleteAfterFetchRemovesHandledMail() throws Exception {
        deliver("one", "two");
        EmailPoller poller = poller(EmailReceiverProperties.MAIL_POP3, false, false, true);

        assertEquals(2, poll(poller).subjects.size());
        assertEquals(List.of(), poll(poller).subjects);
    }

    @Test
    void pop3FailedDispatchLeavesTheMailOnTheServer() throws Exception {
        deliver("kept", "taken");
        EmailPoller poller = poller(EmailReceiverProperties.MAIL_POP3, false, false, true);

        poll(poller, subject -> !subject.equals("kept"));
        assertEquals(List.of("kept"), poll(poller).subjects);
    }

    @Test
    void handlerExceptionLeavesThatMailAndContinues() throws Exception {
        deliver("broken", "fine");
        EmailPoller poller = poller(EmailReceiverProperties.MAIL_POP3, false, false, true);

        List<String> seen = new ArrayList<>();
        poller.poll((rawXml, sourceMap) -> {
            String subject = (String) sourceMap.get("subject");
            seen.add(subject);
            if (subject.equals("broken")) {
                throw new IllegalStateException("channel failed");
            }
            return true;
        });

        assertEquals(List.of("broken", "fine"), seen);
        assertEquals(List.of("broken"), poll(poller).subjects);
    }

    // ---- IMAP

    @Test
    void imapFetchFillsTheFolderAndMailbox() throws Exception {
        deliver("one");
        EmailPoller poller = poller(EmailReceiverProperties.MAIL_IMAP, true, false, false);

        Received received = poll(poller);
        assertEquals(List.of("one"), received.subjects);
        assertEquals("INBOX", received.sourceMaps.get(0).get("folder"));
        assertEquals(LOGIN + "@" + GREENMAIL.getImap().getServerSetup().getBindAddress(),
                received.sourceMaps.get(0).get("mailbox"));
    }

    @Test
    void imapMarkAsReadHidesHandledMailFromTheNextUnreadPoll() throws Exception {
        deliver("one", "two");
        EmailPoller poller = poller(EmailReceiverProperties.MAIL_IMAP, true, true, false);

        assertEquals(List.of("one", "two"), poll(poller).subjects);
        assertEquals(List.of(), poll(poller).subjects);
        // Only unread off: the read mail is still there.
        assertEquals(List.of("one", "two"), poll(poller(EmailReceiverProperties.MAIL_IMAP, false, true, false)).subjects);
    }

    @Test
    void imapReadingWithoutMarkAsReadKeepsMailUnread() throws Exception {
        deliver("one");
        EmailPoller poller = poller(EmailReceiverProperties.MAIL_IMAP, true, false, false);

        assertEquals(List.of("one"), poll(poller).subjects);
        assertEquals(List.of("one"), poll(poller).subjects);
    }

    @Test
    void imapFailedDispatchStaysUnread() throws Exception {
        deliver("kept", "taken");
        EmailPoller poller = poller(EmailReceiverProperties.MAIL_IMAP, true, true, false);

        poll(poller, subject -> !subject.equals("kept"));
        assertEquals(List.of("kept"), poll(poller).subjects);
    }

    @Test
    void imapDeleteAfterFetchRemovesHandledMail() throws Exception {
        deliver("kept", "taken");
        EmailPoller poller = poller(EmailReceiverProperties.MAIL_IMAP, false, false, true);

        poll(poller, subject -> !subject.equals("kept"));
        assertEquals(List.of("kept"), poll(poller).subjects);
    }
}
