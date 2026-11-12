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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.frontpage

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

/**
 * What an inline paper's surface knows about its own page's scroll, and how it moves it, in the
 * screen's pixels. A surface that can't tell (Android, where touch scrolls the page itself) never
 * binds one, and the screen scrolls on its own.
 */
interface InlinePaperScroll {
    /** How far the page can still scroll toward its end ([towardEnd]) or its start; 0 when it can't. */
    fun room(towardEnd: Boolean): Float

    /** Scrolls the page by [px], positive toward its end, and returns how much it took. */
    fun scrollBy(px: Float): Float
}

/**
 * Where a wheel turn over the inline paper goes, as one continuous page: the screen scrolls until
 * the paper fills it, then the paper's own page scrolls, and when that reaches its end (or its
 * start, on the way back) the screen takes the rest.
 */
object PaperScrollSplit {
    /**
     * The part of [delta] (screen pixels, positive toward the end) the paper's page takes. [top] is
     * where the paper starts below the top of the screen's [viewport] (negative once it scrolled
     * past), [height] its height, [room] how far its page can still go that way. The screen takes
     * what is left: first, until the paper fills the viewport (or, shorter than the viewport, is all
     * in sight), then whatever the page has no room for.
     */
    fun pageShare(
        delta: Float,
        top: Float,
        height: Float,
        viewport: Float,
        room: Float,
    ): Float {
        if (delta == 0f || room <= 0f) return 0f
        val bottom = top + height
        val lead =
            if (delta > 0f) {
                max(0f, min(top, bottom - viewport))
            } else {
                max(0f, min(viewport - bottom, -top))
            }
        val left = abs(delta) - lead
        if (left <= 0f) return 0f
        return sign(delta) * min(left, room)
    }
}

/**
 * The Observer screen's wheel, shared with the paper inside it ([PaperScrollSplit]). The screen's
 * own scroll turns the wheel into pixels and animates it as it does anywhere; [connection], above
 * that scroll, hands the paper's page its share of each step. A turn the screen can't follow at
 * all (it is at its end) reaches the surface instead ([pageTakesWheel]), which scrolls the page.
 *
 * Inert until a surface binds [page], so a platform without one keeps the plain screen scroll.
 */
@Stable
class PaperWheel(
    private val outer: ScrollState,
) {
    /** The paper's page, bound by a surface that can tell how far it scrolls; null otherwise. */
    var page: InlinePaperScroll? = null

    // Whether the last wheel turn was over the paper: set by every turn, on its way down the tree.
    private var overPaper = false
    private var contentCoordinates: LayoutCoordinates? = null
    private var paperCoordinates: LayoutCoordinates? = null

    val connection: NestedScrollConnection =
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                val page = page ?: return Offset.Zero
                if (!overPaper || available.y == 0f) return Offset.Zero
                val paper = paperCoordinates?.takeIf { it.isAttached } ?: return Offset.Zero
                val content = contentCoordinates?.takeIf { it.isAttached } ?: return Offset.Zero
                // Where the paper sits in the content doesn't move as the screen scrolls; the scroll does.
                val top = content.localPositionOf(paper, Offset.Zero).y - outer.value
                // Positive toward the end, as the screen's scroll reads a turn down.
                val delta = -available.y
                val share = PaperScrollSplit.pageShare(delta, top, paper.size.height.toFloat(), outer.viewportSize.toFloat(), page.room(delta > 0f))
                if (share == 0f) return Offset.Zero
                return Offset(0f, -page.scrollBy(share))
            }
        }

    /**
     * Whether a wheel turn over the paper, [towardEnd] or back, goes straight to its page: the screen
     * can't scroll that way, so it would never hand the page a share, and the page still can.
     */
    fun pageTakesWheel(towardEnd: Boolean): Boolean {
        val page = page ?: return false
        val screenMoves = if (towardEnd) outer.canScrollForward else outer.canScrollBackward
        return !screenMoves && page.room(towardEnd) > 0f
    }

    /** Around the screen's scroll (before it in the chain): hands the paper its share, and sees every turn. */
    val screen: Modifier =
        Modifier.nestedScroll(connection).pointerInput(this) {
            awaitPointerEventScope {
                while (true) {
                    if (awaitPointerEvent(PointerEventPass.Initial).type == PointerEventType.Scroll) overPaper = false
                }
            }
        }

    /** Right after the screen's scroll: its content, which the paper's place is measured in. */
    val content: Modifier = Modifier.onPlaced { contentCoordinates = it }

    /** On the paper: its place and size, and the turns over it (after [screen] saw them). */
    val paper: Modifier =
        Modifier.onPlaced { paperCoordinates = it }.pointerInput(this) {
            awaitPointerEventScope {
                while (true) {
                    if (awaitPointerEvent(PointerEventPass.Initial).type == PointerEventType.Scroll) overPaper = true
                }
            }
        }
}
