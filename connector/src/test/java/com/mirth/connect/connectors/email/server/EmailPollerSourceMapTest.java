/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */

package com.mirth.connect.connectors.email.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.Test;

class EmailPollerSourceMapTest {

    private static final ZoneId AMSTERDAM = ZoneId.of("Europe/Amsterdam");

    private static MimeMessage parse(String raw) throws Exception {
        return new MimeMessage(Session.getInstance(new Properties()),
                new ByteArrayInputStream(raw.replace("\n", "\r\n").getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void imapMailFillsAllVariables() throws Exception {
        MimeMessage message = parse("Message-ID: <abc123@lab.example.org>\n"
                + "Date: Thu, 17 Sep 2026 08:12:00 +0200\n"
                + "From: \"Lab\" <lab@lab.example.org>\n"
                + "To: intake@example.org, Second <second@example.org>\n"
                + "Subject: =?UTF-8?Q?Lab_result_12345_=E2=9C=93?=\n"
                + "Content-Type: text/plain; charset=UTF-8\n"
                + "\n"
                + "body\n");

        Map<String, Object> map = EmailPoller.sourceMap(message, "intake@imap.example.org", "INBOX/Lab", AMSTERDAM);

        assertEquals("Lab result 12345 ✓", map.get("subject"));
        assertEquals("lab@lab.example.org", map.get("from"));
        assertEquals("intake@example.org, second@example.org", map.get("to"));
        assertEquals("<abc123@lab.example.org>", map.get("messageId"));
        assertEquals("2026-09-17T08:12:00+02:00", map.get("sentDate"));
        assertEquals("intake@imap.example.org", map.get("mailbox"));
        assertEquals("INBOX/Lab", map.get("folder"));
        assertEquals(List.of("subject", "from", "to", "messageId", "sentDate", "mailbox", "folder"),
                List.copyOf(map.keySet()));
    }

    @Test
    void sentDateUsesTheGivenZone() throws Exception {
        MimeMessage message = parse("Date: Thu, 17 Sep 2026 06:12:00 +0000\nSubject: x\n\nbody\n");

        assertEquals("2026-09-17T08:12:00+02:00", EmailPoller.sourceMap(message, "a@b", null, AMSTERDAM).get("sentDate"));
        assertEquals("2026-09-17T06:12:00Z", EmailPoller.sourceMap(message, "a@b", null, ZoneId.of("UTC")).get("sentDate"));
    }

    @Test
    void pop3MailHasNoFolderAndMissingHeadersAreEmpty() throws Exception {
        MimeMessage message = parse("Content-Type: text/plain\n\nbody\n");

        Map<String, Object> map = EmailPoller.sourceMap(message, "user@pop.example.org", null, AMSTERDAM);

        assertFalse(map.containsKey("folder"));
        assertEquals("", map.get("subject"));
        assertEquals("", map.get("from"));
        assertEquals("", map.get("to"));
        assertEquals("", map.get("messageId"));
        assertEquals("", map.get("sentDate"));
        assertEquals("user@pop.example.org", map.get("mailbox"));
    }
}
