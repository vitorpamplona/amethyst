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

import kotlin.test.Test
import kotlin.test.assertEquals

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
}
