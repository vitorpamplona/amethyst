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
package com.vitorpamplona.amethyst.commons.ui.layouts

import org.junit.Assert.assertEquals
import org.junit.Test

class FitPanesTest {
    private val range = 260f..720f

    private fun column(
        width: Float?,
        droppable: Boolean = true,
    ) = PaneFit(width, 360f, range, droppable)

    private val drawer = PaneFit(300f, 300f, DrawerWidthRange, droppable = false)

    @Test
    fun twoColumnsFitAWideWindow() {
        // 300 drawer + 480 centre + 48 strip leaves 612 for the dock.
        assertEquals(listOf(360f, 300f, 300f), fitPanes(1500f, listOf(column(null), column(300f), drawer), reserved = 48f))
    }

    @Test
    fun theOuterColumnNarrowsFirst() {
        assertEquals(listOf(260f, 320f, 300f), fitPanes(1408f, listOf(column(360f), column(360f), drawer), reserved = 48f))
    }

    @Test
    fun aColumnThatCannotFitFoldsAndGivesTheOthersTheirRoomBack() {
        // At 1200 dp one column of 360 fits; two can't, even at their narrowest with the drawer at its narrowest.
        assertEquals(listOf(0f, 360f, 300f), fitPanes(1200f, listOf(column(360f), column(360f), drawer), reserved = 48f))
    }

    @Test
    fun nothingDroppableLeftKeepsTheMinimums() {
        assertEquals(listOf(0f, 0f, 220f), fitPanes(500f, listOf(column(360f), column(360f), drawer), reserved = 48f))
    }

    @Test
    fun aHiddenPaneTakesNothing() {
        assertEquals(listOf(0f, 300f), fitPanes(1600f, listOf(column(400f).copy(shown = false), drawer)))
    }
}
