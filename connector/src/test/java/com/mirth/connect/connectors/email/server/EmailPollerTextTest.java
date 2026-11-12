/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */

package com.mirth.connect.connectors.email.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** The text helpers that need no mail: HTML to text, cleaning, and XML-illegal characters. */
class EmailPollerTextTest {

    /** Characters by code point, so the control and non-ASCII cases are explicit in the source. */
    static String chars(int... codePoints) {
        return new String(codePoints, 0, codePoints.length);
    }

    private static final String NBSP = chars(0xA0);

    @Test
    void htmlToTextDropsMarkupScriptsAndComments() {
        String html = "<html><head><title>T</title></head><body>"
                + "<style>p { color: red }</style><script>alert(1)</script><!-- note -->"
                + "<p>Hello <b>world</b></p><div>Line<br>break<br/>again</div></body></html>";

        assertEquals("Hello world\nLine\nbreak\nagain\n", EmailPoller.htmlToText(html));
    }

    @Test
    void htmlToTextKeepsListsAndTableCellsReadable() {
        String html = "<ul><li>One</li><li>Two</li></ul><table><tr><td>A</td><td>B</td></tr></table>";

        assertEquals("- One\n- Two\n\nA B \n\n", EmailPoller.htmlToText(html));
    }

    @Test
    void htmlToTextDecodesEntities() {
        String decoded = EmailPoller.htmlToText("a &amp; b &lt; c &gt; d &quot; &apos; &ndash; &mdash; &hellip; "
                + "&euro; &copy; &#233; &#xE9; &unknown;");

        assertEquals("a & b < c > d \" ' " + chars(0x2013) + " " + chars(0x2014) + " " + chars(0x2026) + " "
                + chars(0x20AC) + " " + chars(0xA9) + " " + chars(0xE9) + " " + chars(0xE9) + " &unknown;", decoded);
    }

    @Test
    void htmlToTextDecodesNbspToAPlainSpace() {
        assertEquals("a b", EmailPoller.htmlToText("a&nbsp;b"));
    }

    @Test
    void cleanTextNormalizesLineEndingsSpacesAndBlankLines() {
        assertEquals("", EmailPoller.cleanText(null));
        assertEquals("a b\nc\n\nd", EmailPoller.cleanText("  a" + NBSP + "b  \r\nc\r\r\n\n\n\nd \t\n"));
    }

    @Test
    void cleanTextDropsCharactersThatXmlCannotHold() {
        assertEquals("ab", EmailPoller.cleanText("a" + chars(0x0, 0x8) + "b"));
    }

    @Test
    void removeInvalidXmlCharsKeepsWhitespaceAndSupplementaryCharacters() {
        String kept = "tab\tlf\ncr\r " + chars(0x1F4E7) + " " + chars(0xE000) + " " + chars(0xFFFD);
        assertEquals(kept, EmailPoller.removeInvalidXmlChars(kept));
        assertEquals("ab", EmailPoller.removeInvalidXmlChars("a" + chars(0x1, 0x1F, 0xFFFE, 0xFFFF) + "b"));
    }
}
