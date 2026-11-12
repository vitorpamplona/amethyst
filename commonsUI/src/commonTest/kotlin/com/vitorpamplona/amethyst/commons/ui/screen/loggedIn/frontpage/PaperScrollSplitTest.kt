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

import kotlin.test.Test
import kotlin.test.assertEquals

class PaperScrollSplitTest {
    // A 900 px paper in a 700 px viewport.
    private val height = 900f
    private val viewport = 700f

    private fun share(
        delta: Float,
        top: Float,
        room: Float = 5000f,
        height: Float = this.height,
    ) = PaperScrollSplit.pageShare(delta, top, height, viewport, room)

    @Test
    fun theScreenScrollsFirstUntilThePaperReachesTheTop() {
        // Paper starts 300 px down: a 100 px turn is all the screen's.
        assertEquals(0f, share(100f, top = 300f))
        // 60 px down: the screen takes 60 to line it up, the page the other 40.
        assertEquals(40f, share(100f, top = 60f))
        // Lined up, or past: all the page's.
        assertEquals(100f, share(100f, top = 0f))
        assertEquals(100f, share(100f, top = -150f))
    }

    @Test
    fun atItsEndThePageTakesWhatItHasAndTheScreenTheRest() {
        assertEquals(30f, share(100f, top = -150f, room = 30f))
        assertEquals(0f, share(100f, top = -150f, room = 0f))
    }

    @Test
    fun backUpTheScreenFirstBringsThePaperBottomDownThenThePageScrolls() {
        // The paper's bottom is 80 px above the viewport's (top -280 + 900 = 620): the screen takes 80.
        assertEquals(-20f, share(-100f, top = -280f))
        assertEquals(0f, share(-60f, top = -280f))
        // Filling the viewport: all the page's, until its start.
        assertEquals(-100f, share(-100f, top = -100f))
        assertEquals(-25f, share(-100f, top = -100f, room = 25f))
        assertEquals(0f, share(-100f, top = -100f, room = 0f))
    }

    @Test
    fun aPaperShorterThanTheViewportScrollsOnceAllInSight() {
        // 500 px paper, 100 px down, bottom at 600 < 700: in sight, so the page scrolls at once.
        assertEquals(100f, share(100f, top = 100f, height = 500f))
        assertEquals(-100f, share(-100f, top = 100f, height = 500f))
        // Half out of sight below: the screen brings it up first (its bottom is 200 px under).
        assertEquals(0f, share(100f, top = 400f, height = 500f))
        assertEquals(50f, share(250f, top = 400f, height = 500f))
    }

    @Test
    fun noTurnNoShare() {
        assertEquals(0f, share(0f, top = 0f))
    }
}
