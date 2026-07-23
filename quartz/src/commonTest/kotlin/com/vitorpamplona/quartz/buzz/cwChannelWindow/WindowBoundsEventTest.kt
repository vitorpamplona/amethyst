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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WindowBoundsEventTest {
    private val channel = "buzz-general"
    private val boundaryId = "a".repeat(64)

    @Test
    fun buildHasMoreOverlayRoundTrips() {
        val cursor = "1713957000:$boundaryId"
        val bounds = WindowBoundsContent(hasMore = true, nextCursor = NextCursor(createdAt = 1713957000L, id = boundaryId))
        val tpl = WindowBoundsEvent.build(channel, bounds, cursor)
        val ev = WindowBoundsEvent("00".repeat(32), "dd".repeat(32), tpl.createdAt, tpl.tags, tpl.content, "ff".repeat(64))

        assertEquals(39006, WindowBoundsEvent.KIND)
        assertEquals("$channel:$cursor", ev.windowKey())
        assertEquals(channel, ev.channelId())

        val parsed = ev.bounds()
        assertTrue(parsed.hasMore)
        assertEquals(1713957000L, parsed.nextCursor?.createdAt)
        assertEquals(boundaryId, parsed.nextCursor?.id)
    }

    @Test
    fun buildExhaustedHeadWindowOmitsCursor() {
        val bounds = WindowBoundsContent(hasMore = false, nextCursor = null)
        val tpl = WindowBoundsEvent.build(channel, bounds)
        val ev = WindowBoundsEvent("00".repeat(32), "dd".repeat(32), tpl.createdAt, tpl.tags, tpl.content, "ff".repeat(64))

        assertEquals("$channel:head", ev.windowKey())
        val parsed = ev.bounds()
        assertEquals(false, parsed.hasMore)
        assertNull(parsed.nextCursor)
    }
}
