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

import kotlin.concurrent.Volatile

/**
 * How much narrower a feed card's media is than the card: its side padding and author column, less
 * what edge-to-edge media takes back. [com.vitorpamplona.amethyst.commons.ui.layouts.NoteComposeLayout]
 * reports it as it lays out a top-level note, and the prefetch of a list of note cards
 * ([PrefetchFeedMedia] with `mediaAtNoteWidth`) decodes images at the list's width less it: the width the card draws them at, so on the desktop they
 * draw 1:1 with no copy, and nowhere does a bitmap wider than its card sit in memory. It depends on
 * the layout settings and the density, not on the width of the list, so one value serves every feed
 * and deck column.
 */
object FeedMediaGutter {
    @Volatile
    var px: Int = UNKNOWN
        private set

    fun report(gutterPx: Int) {
        if (gutterPx >= 0 && gutterPx != px) px = gutterPx
    }

    /** The width a card [listWidth] wide draws its media at; the list's width until a card has laid out. */
    fun mediaWidth(listWidth: Int): Int = mediaWidth(listWidth, px)

    internal fun mediaWidth(
        listWidth: Int,
        gutterPx: Int,
    ): Int = if (gutterPx in 0 until listWidth) listWidth - gutterPx else listWidth

    private const val UNKNOWN = -1
}
