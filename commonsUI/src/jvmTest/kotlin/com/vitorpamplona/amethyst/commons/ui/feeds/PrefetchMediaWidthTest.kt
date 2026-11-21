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
package com.vitorpamplona.amethyst.commons.ui.feeds

import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals

class PrefetchMediaWidthTest {
    private val viewport = IntSize(600, 760)

    @Test
    fun aFeedOfOtherCardsDecodesAtTheListWidth() {
        // Picture and video cards, a thread's main post: media across the whole card.
        FeedMediaGutter.report(89)
        val ends = VisibleEnds("a", "b", viewport)
        assertEquals(600, ends.boxFor(1).width)
        assertEquals(600, ends.boxFor(3).width)
    }

    @Test
    fun aFeedOfNoteCardsDecodesAtTheNoteContentWidth() {
        FeedMediaGutter.report(89)
        val ends = VisibleEnds("a", "b", viewport, mediaAtNoteWidth = true)
        assertEquals(511, ends.boxFor(1).width)
        assertEquals(760, ends.boxFor(1).height)
        assertEquals(511, ends.boxFor(3).height)
    }

    @Test
    fun aGridDecodesAtItsCell() {
        val ends = VisibleEnds("a", "b", viewport, cell = IntSize(200, 200), mediaAtNoteWidth = true)
        assertEquals(200, ends.boxFor(1).width)
    }
}
