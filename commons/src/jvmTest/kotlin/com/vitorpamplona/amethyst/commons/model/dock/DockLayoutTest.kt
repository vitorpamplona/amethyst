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
package com.vitorpamplona.amethyst.commons.model.dock

import com.vitorpamplona.amethyst.commons.model.dock.PanelId.Companion.ASSISTANT
import com.vitorpamplona.amethyst.commons.model.dock.PanelId.Companion.DEVICES
import com.vitorpamplona.amethyst.commons.model.dock.PanelId.Companion.NOTIFICATIONS
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DockLayoutTest {
    private val all = listOf(NOTIFICATIONS, ASSISTANT, DEVICES)
    private val everywhere = all.toSet()

    private fun default() = DockLayout().ensure(all)

    @Test
    fun theDefaultDockIsTodaysNotificationPanel() {
        val layout = default()
        val view = layout.view(everywhere)
        assertEquals(1, view.columns.size)
        assertEquals(listOf(NOTIFICATIONS), view.columns[0].slots.map { it.active })
        // The assistant waits under the feed, the devices in a column of their own, both folded.
        assertEquals(PanelLocation(0, 1, 0), layout.locate(ASSISTANT))
        assertEquals(PanelLocation(1, 0, 0), layout.locate(DEVICES))
        assertEquals(PanelState.FOLDED, layout.stateOf(ASSISTANT))
        assertEquals(listOf(NOTIFICATIONS, ASSISTANT, DEVICES), layout.ordered())
    }

    @Test
    fun ensureKeepsWhatIsThere() {
        val layout = default().open(ASSISTANT).fold(NOTIFICATIONS)
        assertEquals(layout, layout.ensure(all))
    }

    @Test
    fun openingTheAssistantSplitsTheColumnUnderTheFeed() {
        val view = default().open(ASSISTANT).view(everywhere)
        assertEquals(1, view.columns.size)
        assertEquals(listOf(NOTIFICATIONS, ASSISTANT), view.columns[0].slots.map { it.active })
    }

    @Test
    fun aPanelTheplatformLacksKeepsItsPlaceButIsNotDrawn() {
        val layout = default().open(ASSISTANT).open(DEVICES)
        val view = layout.view(setOf(NOTIFICATIONS))
        assertEquals(1, view.columns.size)
        assertEquals(listOf(NOTIFICATIONS), view.columns[0].slots.map { it.active })
        assertNotNull(layout.locate(DEVICES))
    }

    @Test
    fun droppingOnASlotMakesTabsAndTheDroppedOneShows() {
        val layout = assertNotNull(default().open(ASSISTANT).move(ASSISTANT, DropTarget.Tab(0, 0)))
        assertEquals(1, layout.columns[0].slots.size)
        assertEquals(listOf(NOTIFICATIONS, ASSISTANT), layout.columns[0].slots[0].panels)
        assertEquals(
            ASSISTANT,
            layout
                .view(everywhere)
                .columns[0]
                .slots[0]
                .active,
        )
        // Showing the other tab again.
        assertEquals(
            NOTIFICATIONS,
            layout
                .open(NOTIFICATIONS)
                .view(everywhere)
                .columns[0]
                .slots[0]
                .active,
        )
    }

    @Test
    fun foldingTheShownTabShowsTheNextOpenOne() {
        val layout = assertNotNull(default().open(ASSISTANT).move(ASSISTANT, DropTarget.Tab(0, 0))).fold(ASSISTANT)
        val slot =
            layout
                .view(everywhere)
                .columns[0]
                .slots
                .single()
        assertEquals(listOf(NOTIFICATIONS), slot.tabs)
        assertEquals(NOTIFICATIONS, slot.active)
    }

    @Test
    fun splittingAboveMovesThePanelUp() {
        val layout = assertNotNull(default().open(ASSISTANT).move(ASSISTANT, DropTarget.Split(0, 0, below = false)))
        assertEquals(
            listOf(ASSISTANT, NOTIFICATIONS),
            layout
                .view(everywhere)
                .columns[0]
                .slots
                .map { it.active },
        )
    }

    @Test
    fun aNewColumnTakesThePanelOutOfItsSlot() {
        val layout = assertNotNull(default().open(ASSISTANT).move(ASSISTANT, DropTarget.NewColumn(1)))
        assertEquals(listOf(listOf(NOTIFICATIONS), listOf(ASSISTANT), listOf(DEVICES)), layout.columns.map { c -> c.slots.flatMap { it.panels } })
        assertEquals(2, layout.view(everywhere).columns.size)
    }

    @Test
    fun aColumnLeftEmptyGoes() {
        val layout = assertNotNull(default().move(DEVICES, DropTarget.Split(0, 1, below = true)))
        assertEquals(1, layout.columns.size)
        assertEquals(listOf(NOTIFICATIONS, ASSISTANT, DEVICES), layout.columns[0].slots.map { it.panels.single() })
        assertTrue(layout.isOpen(DEVICES))
    }

    @Test
    fun theLimitsHold() {
        // Three slots in one column: a fourth is not offered.
        val full = assertNotNull(default().move(DEVICES, DropTarget.Split(0, 1, below = true)))
        val extra = PanelId("extra")
        val withExtra = full.ensure(listOf(extra), ownColumn = setOf(extra))
        assertFalse(withExtra.canMove(extra, DropTarget.Split(0, 0, below = true)))
        assertTrue(withExtra.canMove(extra, DropTarget.Tab(0, 0)))
        // Moving a panel within its own full column stays allowed: it leaves a slot as it takes one.
        assertTrue(full.canMove(DEVICES, DropTarget.Split(0, 0, below = false)))
    }

    @Test
    fun targetsThatAreNotThereAreRefused() {
        assertNull(default().move(ASSISTANT, DropTarget.Tab(5, 0)))
        assertNull(default().move(ASSISTANT, DropTarget.NewColumn(9)))
        assertNull(default().move(PanelId("unknown"), DropTarget.Tab(0, 0)))
    }

    @Test
    fun droppingOnItsOwnSlotOnlyShowsIt() {
        val layout = default()
        assertEquals(layout.open(ASSISTANT), layout.move(ASSISTANT, DropTarget.Tab(0, 1)))
    }

    @Test
    fun theStripFoldsAndTheWindowPopsOutWithoutMoving() {
        val layout = default().open(ASSISTANT)
        val folded = assertNotNull(layout.move(ASSISTANT, DropTarget.Strip))
        assertEquals(PanelState.FOLDED, folded.stateOf(ASSISTANT))
        assertEquals(layout.locate(ASSISTANT), folded.locate(ASSISTANT))
        val windowed = assertNotNull(layout.move(ASSISTANT, DropTarget.Window))
        assertTrue(windowed.isInWindow(ASSISTANT))
        assertEquals(
            listOf(NOTIFICATIONS),
            windowed
                .view(everywhere)
                .columns[0]
                .slots
                .map { it.active },
        )
        // Back from its window, it is where it was.
        val back = windowed.dockBack(ASSISTANT)
        assertEquals(
            listOf(NOTIFICATIONS, ASSISTANT),
            back
                .view(everywhere)
                .columns[0]
                .slots
                .map { it.active },
        )
    }

    @Test
    fun overlayPanelsAreNeverDocked() {
        val layout = default().open(ASSISTANT).setOverlayMode(ASSISTANT, true)
        assertTrue(layout.isOverlay(ASSISTANT))
        assertEquals(
            listOf(NOTIFICATIONS),
            layout
                .view(everywhere)
                .columns[0]
                .slots
                .map { it.active },
        )
        assertEquals(
            listOf(NOTIFICATIONS, ASSISTANT),
            layout
                .setOverlayMode(ASSISTANT, false)
                .open(ASSISTANT)
                .view(everywhere)
                .columns[0]
                .slots
                .map { it.active },
        )
    }

    @Test
    fun foldAllAndBack() {
        val layout = default().open(ASSISTANT)
        val open = layout.states.filterValues { it == PanelState.OPEN }.keys
        val hidden = layout.foldAll()
        assertTrue(hidden.view(everywhere).isEmpty)
        assertEquals(layout.view(everywhere), hidden.openAll(open).view(everywhere))
    }

    @Test
    fun resizingKeepsTheRanges() {
        val layout = default().resizeColumn(0, 9_000f)
        assertEquals(DockLayout.COLUMN_WIDTH_RANGE.endInclusive, layout.columns[0].width)
        val slots = layout.resizeSlots(0, 0, 1, 1.9f, 0f).columns[0].slots
        assertEquals(1.9f, slots[0].weight)
        assertEquals(DockLayout.MIN_SLOT_WEIGHT, slots[1].weight)
    }

    @Test
    fun itSurvivesARoundTripAndUnknownPanelsKeepTheirPlace() {
        val layout = assertNotNull(default().open(ASSISTANT).move(ASSISTANT, DropTarget.NewColumn(0))).resizeColumn(0, 400f).setOverlayMode(DEVICES, true)
        assertEquals(layout, DockLayout.fromJson(layout.toJson()))
        val fromNewerBuild = """{"columns":[{"slots":[{"panels":["notifications","hologram"],"active":"hologram"}]}],"states":{"hologram":"OPEN"},"later":1}"""
        val decoded = assertNotNull(DockLayout.fromJson(fromNewerBuild))
        assertNotNull(decoded.locate(PanelId("hologram")))
        assertTrue(decoded.view(everywhere).isEmpty, "the notification tab is folded, the unknown one not drawable")
    }

    @Test
    fun garbageDoesNotDecode() {
        assertNull(DockLayout.fromJson("not json"))
        assertNull(DockLayout.fromJson("""{"states":{"notifications":"SIDEWAYS"}}"""))
    }

    @Test
    fun aPinnedScreenOpensUnderTheFirstColumnThenInItsOwn() {
        val route = DockLayout.encodeRoute(Route.Profile("abc"))
        val first = PanelId.newScreen()
        val one = default().pinScreen(first, route)
        assertTrue(first.isScreen)
        assertEquals(PanelLocation(0, 2, 0), one.locate(first), "the first column holds two slots, so a third fits")
        assertTrue(one.isOpen(first))
        assertEquals(first, one.screenShowing(route))

        val second = PanelId.newScreen()
        val two = one.pinScreen(second, DockLayout.encodeRoute(Route.Home))
        assertEquals(PanelLocation(2, 0, 0), two.locate(second), "the first column is full: a column of its own")
    }

    @Test
    fun aFullDockTakesAPinnedScreenAsATab() {
        var layout = default()
        repeat(6) { layout = layout.pinScreen(PanelId.newScreen(), DockLayout.encodeRoute(Route.Profile("p$it"))) }
        val last = PanelId.newScreen()
        layout = layout.pinScreen(last, DockLayout.encodeRoute(Route.Home))
        val at = assertNotNull(layout.locate(last))
        assertEquals(0, at.column)
        assertEquals(DockLayout.MAX_SLOTS - 1, at.slot)
        assertEquals(last, layout.columns[0].slots[at.slot].active)
    }

    @Test
    fun unpinningLeavesNoTrace() {
        val id = PanelId.newScreen()
        val pinned = default().pinScreen(id, DockLayout.encodeRoute(Route.Home)).setOverlayMode(id, true)
        val unpinned = pinned.unpin(id)
        assertNull(unpinned.locate(id))
        assertNull(unpinned.screenShowing(DockLayout.encodeRoute(Route.Home)))
        assertFalse(id in unpinned.states)
        assertFalse(id in unpinned.overlayMode)
        assertEquals(default(), unpinned)
    }

    @Test
    fun pinnedScreensSurviveARoundTrip() {
        val id = PanelId.newScreen()
        val layout = default().pinScreen(id, DockLayout.encodeRoute(Route.Profile("abc")))
        val decoded = assertNotNull(DockLayout.fromJson(layout.toJson()))
        assertEquals(layout, decoded)
        assertEquals(Route.Profile("abc"), DockLayout.decodeRoute(assertNotNull(decoded.screens[id])))
        assertNull(DockLayout.decodeRoute("""{"type":"NoSuchRoute"}"""))
    }
}
