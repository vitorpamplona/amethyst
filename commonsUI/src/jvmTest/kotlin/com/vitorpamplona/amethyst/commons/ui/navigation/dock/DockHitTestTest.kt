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
package com.vitorpamplona.amethyst.commons.ui.navigation.dock

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.vitorpamplona.amethyst.commons.model.dock.DockLayout
import com.vitorpamplona.amethyst.commons.model.dock.DropTarget
import com.vitorpamplona.amethyst.commons.model.dock.PanelId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A 1600 x 1000 window: the main screen up to 1100, one dock column from 1100 to 1552 with the
 * notification feed over the assistant, and the strip from 1552.
 */
class DockHitTestTest {
    private val layout = DockLayout().ensure(listOf(PanelId.NOTIFICATIONS, PanelId.ASSISTANT, PanelId.DEVICES)).open(PanelId.ASSISTANT)

    private val zones =
        DropZones().apply {
            window = Size(1600f, 1000f)
            center = Rect(300f, 0f, 1100f, 1000f)
            columns[0] = Rect(1100f, 0f, 1552f, 1000f)
            slots[0 to 0] = Rect(1100f, 0f, 1552f, 500f)
            slots[0 to 1] = Rect(1100f, 500f, 1552f, 1000f)
            strip = Rect(1552f, 0f, 1600f, 1000f)
            edgePx = 40f
        }

    private fun hit(
        x: Float,
        y: Float,
        id: PanelId = PanelId.DEVICES,
        canWindow: Boolean = true,
    ) = dockHitTest(id, Offset(x, y), layout, zones, canWindow)?.target

    @Test
    fun theMiddleOfASlotSharesIt() = assertEquals(DropTarget.Tab(0, 1), hit(1300f, 750f))

    @Test
    fun aSlotsTopAndBottomQuartersSplitIt() {
        assertEquals(DropTarget.Split(0, 0, below = false), hit(1300f, 50f))
        assertEquals(DropTarget.Split(0, 1, below = true), hit(1300f, 950f))
    }

    @Test
    fun aColumnsEdgesOpenNewColumns() {
        assertEquals(DropTarget.NewColumn(0), hit(1110f, 300f))
        assertEquals(DropTarget.NewColumn(1), hit(1540f, 300f))
    }

    @Test
    fun theMainScreensRightEdgeOpensAFirstColumn() = assertEquals(DropTarget.NewColumn(0), hit(1050f, 300f))

    @Test
    fun theStripFolds() = assertEquals(DropTarget.Strip, hit(1570f, 300f))

    @Test
    fun outsideTheWindowIsAWindowOfItsOwnWhereThePlatformHasThem() {
        assertEquals(DropTarget.Window, hit(1700f, 300f))
        assertNull(hit(1700f, 300f, canWindow = false))
    }

    @Test
    fun theMiddleOfTheMainScreenIsNothing() = assertNull(hit(600f, 300f))

    @Test
    fun aSplitPastTheLimitIsNoTarget() {
        // The column already stacks three slots once the devices join it.
        val full = checkNotNull(layout.move(PanelId.DEVICES, DropTarget.Split(0, 1, below = true)))
        val extra = PanelId("extra")
        val crowded = full.ensure(listOf(extra), ownColumn = setOf(extra))
        assertNull(dockHitTest(extra, Offset(1300f, 950f), crowded, zones, canWindow = true))
        assertEquals(DropTarget.Tab(0, 1), dockHitTest(extra, Offset(1300f, 750f), crowded, zones, canWindow = true)?.target)
    }
}
