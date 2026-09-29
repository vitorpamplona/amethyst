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
package com.vitorpamplona.quartz.concord.cord03Channels

import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConcordDisappearingTest {
    private val author = KeyPair().pubKey.toHexKey()
    private val channel = "cc".repeat(32)

    @Test
    fun onlyDurableChatKindsExpire() {
        assertEquals(1_000 + 86_400L, ConcordDisappearing.expirationFor(9, 1_000, 86_400))
        assertEquals(1_000 + 86_400L, ConcordDisappearing.expirationFor(1111, 1_000, 86_400))
        assertEquals(1_000 + 86_400L, ConcordDisappearing.expirationFor(7, 1_000, 86_400))
        assertEquals(1_000 + 86_400L, ConcordDisappearing.expirationFor(3302, 1_000, 86_400))
        // Deletes and notices never expire; ephemeral kinds carry nothing; an unset timer tags nothing.
        assertNull(ConcordDisappearing.expirationFor(5, 1_000, 86_400))
        assertNull(ConcordDisappearing.expirationFor(1740, 1_000, 86_400))
        assertNull(ConcordDisappearing.expirationFor(23311, 1_000, 86_400))
        assertNull(ConcordDisappearing.expirationFor(9, 1_000, null))
        assertNull(ConcordDisappearing.expirationFor(9, 1_000, 0))
    }

    @Test
    fun theRumorsOwnTagDecidesExpiry() {
        val tags = ConcordDisappearing.withExpiration(arrayOf(arrayOf("channel", channel)), 2_000)
        val rumor = ChannelChat.message(author, channel, 0, "gone soon", 1_000, extraTags = tags)
        assertEquals(2_000L, ConcordDisappearing.expirationOf(rumor))
        assertFalse(ConcordDisappearing.isExpired(rumor, now = 1_999))
        assertTrue(ConcordDisappearing.isExpired(rumor, now = 2_000), "NIP-40: exp <= now")
        assertFalse(ConcordDisappearing.isExpired(ChannelChat.message(author, channel, 0, "forever", 1_000), now = Long.MAX_VALUE))
    }

    @Test
    fun timerNoticeRoundTripsAndRejectsGarbage() {
        val notice = ConcordDisappearing.timerNotice(author, channel, 4, 2_592_000, 10)
        assertEquals(1740, notice.kind)
        assertEquals("", notice.content)
        assertEquals(channel, ChannelChat.channelOf(notice))
        assertEquals(4L, ChannelChat.epochOf(notice))
        assertEquals(2_592_000L, ConcordDisappearing.noticeTimerSecs(notice))
        assertEquals(0L, ConcordDisappearing.noticeTimerSecs(ConcordDisappearing.timerNotice(author, channel, 4, 0, 10)))

        val bad = ChannelChat.message(author, channel, 4, "", 10, extraTags = arrayOf(arrayOf("timer", "04")))
        assertNull(ConcordDisappearing.noticeTimerSecs(bad), "not a 1740")
    }
}
