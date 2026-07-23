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
package com.vitorpamplona.quartz.buzz.cwChannelWindow

import kotlin.test.Test
import kotlin.test.assertEquals

class ThreadSummaryEventTest {
    private val root = "a".repeat(64)
    private val channel = "buzz-general"
    private val p1 = "b".repeat(64)
    private val p2 = "c".repeat(64)

    @Test
    fun buildOverlayCarriesTagsAndJsonContent() {
        val summary = ThreadSummaryContent(replyCount = 3, descendantCount = 5, lastReplyAt = 1713957000L, participants = listOf(p1, p2))
        val tpl = ThreadSummaryEvent.build(root, channel, summary)
        val ev = ThreadSummaryEvent("00".repeat(32), "dd".repeat(32), tpl.createdAt, tpl.tags, tpl.content, "ff".repeat(64))

        assertEquals(39005, ThreadSummaryEvent.KIND)
        assertEquals(root, ev.rootId())
        assertEquals(root, ev.rootEventTag())
        assertEquals(channel, ev.channelId())

        val parsed = ev.summary()
        assertEquals(3, parsed.replyCount)
        assertEquals(5, parsed.descendantCount)
        assertEquals(1713957000L, parsed.lastReplyAt)
        assertEquals(listOf(p1, p2), parsed.participants)
    }

    @Test
    fun contentUsesSnakeCaseFieldNames() {
        val json = ThreadSummaryContent(replyCount = 1, descendantCount = 1, lastReplyAt = null, participants = emptyList()).encodeToJson()
        assertEquals(true, json.contains("\"reply_count\""))
        assertEquals(true, json.contains("\"descendant_count\""))
        // explicitNulls = false: a null last_reply_at is omitted entirely.
        assertEquals(false, json.contains("last_reply_at"))
    }
}
