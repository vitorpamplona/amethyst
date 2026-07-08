/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.quartz.nip29RelayGroups

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers the de-facto `<relay>'<groupId>[?code=<code>]` invite-link format shared by
 * Wisp and 0xchat. [GroupInviteLink.parse] is the deep-link / inline-tap entry point;
 * [GroupInviteLink.suffixLength] is the primitive the note linkifier uses to keep the
 * `'<groupId>…` span glued to the relay URL the detector already found.
 */
class GroupInviteLinkTest {
    @Test
    fun parsesBareInvite() {
        val link = GroupInviteLink.parse("wss://groups.0xchat.com'abc123")!!
        assertEquals("abc123", link.groupId)
        assertNull(link.code)
        assertTrue(link.relayUrl.url.startsWith("wss://groups.0xchat.com"))
    }

    @Test
    fun parsesInviteWithCode() {
        val link = GroupInviteLink.parse("wss://chat.wisp.talk'my-group?code=xyz789")!!
        assertEquals("my-group", link.groupId)
        assertEquals("xyz789", link.code)
    }

    @Test
    fun parsesDefaultUnderscoreGroup() {
        val link = GroupInviteLink.parse("wss://relay.example.com'_")!!
        assertEquals("_", link.groupId)
    }

    @Test
    fun ignoresExtraQueryParams() {
        val link = GroupInviteLink.parse("wss://relay.example.com'g1?foo=bar&code=abc")!!
        assertEquals("g1", link.groupId)
        assertEquals("abc", link.code)
    }

    @Test
    fun rejectsNoApostrophe() {
        assertNull(GroupInviteLink.parse("wss://relay.example.com"))
    }

    @Test
    fun rejectsEmptyGroupId() {
        assertNull(GroupInviteLink.parse("wss://relay.example.com'"))
        assertNull(GroupInviteLink.parse("wss://relay.example.com'?code=abc"))
    }

    @Test
    fun rejectsNonRelayHost() {
        // A leading apostrophe (no host) or a plainly invalid host must not parse.
        assertNull(GroupInviteLink.parse("'abc123"))
    }

    @Test
    fun rejectsSingleCharPossessive() {
        // `wss://relay.damus.io's uptime` — the `'s` is a possessive, not a group id.
        assertNull(GroupInviteLink.parse("wss://relay.damus.io's"))
    }

    @Test
    fun acceptsUnderscoreDefaultGroupButNotOtherSingleChars() {
        // `_` is the relay-wide default group and is a legitimate 1-char id; other lone
        // chars are rejected as contractions.
        assertEquals("_", GroupInviteLink.parse("wss://relay.example.com'_")?.groupId)
        assertNull(GroupInviteLink.parse("wss://relay.example.com'x"))
    }

    @Test
    fun suffixLengthRejectsPossessiveS() {
        // "wss://r.com's uptime" — apostrophe at 11, `s` at 12 → not a link.
        assertEquals(0, GroupInviteLink.suffixLength("wss://r.com's uptime", 12))
    }

    @Test
    fun suffixLengthAcceptsUnderscore() {
        assertEquals(1, GroupInviteLink.suffixLength("wss://r.com'_ hi", 12))
    }

    @Test
    fun suffixLengthMeasuresGroupIdOnly() {
        // "wss://r.com'abc def" — apostrophe at index 11, group id starts at 12.
        val content = "wss://r.com'abc def"
        assertEquals("abc".length, GroupInviteLink.suffixLength(content, 12))
    }

    @Test
    fun suffixLengthIncludesCode() {
        val content = "wss://r.com'abc?code=xyz rest"
        assertEquals("abc?code=xyz".length, GroupInviteLink.suffixLength(content, 12))
    }

    @Test
    fun suffixLengthStopsAtProsePunctuation() {
        // A possessive apostrophe in prose: "it's" — after the ' there is no id run
        // that we would want, but here we assert the scanner is bounded to id chars
        // and stops at the space.
        val content = "wss://r.com'group, and more"
        assertEquals("group".length, GroupInviteLink.suffixLength(content, 12))
    }

    @Test
    fun suffixLengthZeroWhenNoGroupId() {
        val content = "wss://r.com' next"
        assertEquals(0, GroupInviteLink.suffixLength(content, 12))
    }

    @Test
    fun suffixLengthDropsDanglingCodePrefix() {
        // "?code=" with no actual code after it must not extend the span.
        val content = "wss://r.com'abc?code="
        assertEquals("abc".length, GroupInviteLink.suffixLength(content, 12))
    }
}
