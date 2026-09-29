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
package com.vitorpamplona.quartz.buzz.stream

import com.vitorpamplona.quartz.buzz.stream.tags.ExpectedRevisionTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CanvasEventTest {
    private val channelId = "3f2504e0-4f89-41d3-9a0c-0305e82c3301"

    @Test
    fun buildEmitsChannelWithMarkdownContent() {
        val markdown = "# Roadmap\n\n- ship it"
        val template =
            CanvasEvent.build(
                channelId = channelId,
                markdown = markdown,
                createdAt = 1_700_000_000L,
            )

        assertEquals(CanvasEvent.KIND, template.kind)
        assertEquals(40100, template.kind)
        assertEquals(markdown, template.content)
        assertEquals(channelId, template.tags.single { it[0] == "h" }[1])
    }

    @Test
    fun accessorReadsChannelAndIndexableContent() {
        val event =
            CanvasEvent(
                id = "00",
                pubKey = "00",
                createdAt = 0L,
                tags = arrayOf(arrayOf("h", channelId)),
                content = "# Doc",
                sig = "00",
            )

        assertEquals(channelId, event.channel())
        assertEquals("# Doc", event.indexableContent())
    }

    private val headId = "ab".repeat(32)

    @Test
    fun buildOmitsExpectedRevisionByDefault() {
        val template = CanvasEvent.build(channelId, "x", createdAt = 1L)
        assertTrue(template.tags.none { it[0] == ExpectedRevisionTag.TAG_NAME })
    }

    @Test
    fun buildEmitsExpectedRevisionHeadAndNone() {
        val onHead = CanvasEvent.build(channelId, "x", createdAt = 1L, expectedRevision = headId)
        assertEquals(listOf("expected-revision", headId), onHead.tags.single { it[0] == "expected-revision" }.toList())

        val onNone = CanvasEvent.build(channelId, "x", createdAt = 1L, expectedRevision = ExpectedRevisionTag.NONE)
        assertEquals(listOf("expected-revision", "none"), onNone.tags.single { it[0] == "expected-revision" }.toList())
    }

    @Test
    fun buildRejectsMalformedExpectedRevision() {
        assertFailsWith<IllegalArgumentException> { CanvasEvent.build(channelId, "x", expectedRevision = "") }
        assertFailsWith<IllegalArgumentException> { CanvasEvent.build(channelId, "x", expectedRevision = "NONE") }
        assertFailsWith<IllegalArgumentException> { CanvasEvent.build(channelId, "x", expectedRevision = "ab".repeat(31)) }
        assertFailsWith<IllegalArgumentException> { CanvasEvent.build(channelId, "x", expectedRevision = "zz".repeat(32)) }
    }

    @Test
    fun expectedRevisionParsesOnlyTheExactTwoElementShape() {
        fun canvas(vararg tags: Array<String>) = CanvasEvent("00", "00", 0L, arrayOf(arrayOf("h", channelId), *tags), "", "00")

        assertEquals(headId, canvas(arrayOf("expected-revision", headId)).expectedRevision())
        assertEquals("none", canvas(arrayOf("expected-revision", "none")).expectedRevision())
        assertNull(canvas().expectedRevision())
        assertNull(canvas(arrayOf("expected-revision", headId, "extra")).expectedRevision())
        assertNull(canvas(arrayOf("expected-revision", "bogus")).expectedRevision())
    }

    @Test
    fun writeCreatedAtStampsStrictlyAheadOfTheHead() {
        val now = 1_700_000_000L
        assertEquals(now, CanvasEvent.writeCreatedAt(null, now))
        assertEquals(now, CanvasEvent.writeCreatedAt(now - 100, now))
        assertEquals(now + 1, CanvasEvent.writeCreatedAt(now, now))
        assertEquals(now + 11, CanvasEvent.writeCreatedAt(now + 10, now))
    }

    @Test
    fun writeCreatedAtRefusesAHeadPastTheSixtySecondCeiling() {
        val now = 1_700_000_000L
        // now + 60 is the last head a writer may ratchet past; the write lands at now + 61,
        // inside the relay's 300 s canvas bound.
        assertEquals(now + 61, CanvasEvent.writeCreatedAt(now + 60, now))
        assertTrue(now + 61 <= now + CanvasEvent.RELAY_MAX_FUTURE_SECS)
        assertNull(CanvasEvent.writeCreatedAt(now + 61, now))
        assertNull(CanvasEvent.writeCreatedAt(Long.MAX_VALUE, now))
    }

    @Test
    fun headOrderIsCreatedAtDescThenIdAsc() {
        val small = "01".repeat(32)
        val big = "ff".repeat(32)
        assertTrue(CanvasEvent.isNewerHead(10, big, null, null))
        assertTrue(CanvasEvent.isNewerHead(11, big, 10, small))
        assertFalse(CanvasEvent.isNewerHead(9, small, 10, big))
        // Same second: the smallest id wins, regardless of arrival order.
        assertTrue(CanvasEvent.isNewerHead(10, small, 10, big))
        assertFalse(CanvasEvent.isNewerHead(10, big, 10, small))
        // A revision never displaces itself.
        assertFalse(CanvasEvent.isNewerHead(10, small, 10, small))
    }

    @Test
    fun conflictPrefixMatchesTheRelayMarkers() {
        assertTrue(CanvasEvent.isConflict("conflict: canvas changed since it was loaded"))
        assertTrue(CanvasEvent.isConflict("conflict: canvas revision does not exist"))
        assertFalse(CanvasEvent.isConflict("invalid: bad expected canvas revision"))
        assertFalse(CanvasEvent.isConflict(null))
    }
}
