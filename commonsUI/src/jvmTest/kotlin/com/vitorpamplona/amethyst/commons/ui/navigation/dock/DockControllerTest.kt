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

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.ViewModel
import com.vitorpamplona.amethyst.commons.model.dock.DockLayoutPreferences
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The dock controller's pinned screens: pinning again, their history, and taking them out while drawn. */
@OptIn(ExperimentalCoroutinesApi::class)
class DockControllerTest {
    /** Preferences in memory: the controller only needs them to hold the layout. */
    private class MemoryStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = transform(state.value).also { state.value = it }
    }

    private class Probe : ViewModel() {
        var cleared = false

        override fun onCleared() {
            cleared = true
        }
    }

    private val account: AccountViewModel = mockk(relaxed = true)

    private fun controller() =
        DockController(
            DockLayoutPreferences(MemoryStore(), CoroutineScope(UnconfinedTestDispatcher())),
            emptyList(),
            account,
        )

    private val profile = Route.Profile("abc")

    @Test
    fun pinningAScreenInAWindowAgainRaisesItsWindow() {
        val dock = controller().apply { supportsWindows = true }
        val id = dock.pinScreen(profile)
        dock.popOut(id)
        assertEquals(id, dock.pinScreen(profile))
        assertTrue(dock.layout().isInWindow(id), "it stays in its window")
        assertEquals(1, dock.windowRaise[id])
    }

    @Test
    fun pinningAgainShowsAPanelThatCannotDockOverTheScreen() {
        val dock = controller().apply { canDock = true }
        val id = dock.pinScreen(profile)
        assertNull(dock.overlay, "the dock has room: it docks")

        dock.setOverlayMode(id, true)
        dock.dismissOverlay()
        dock.pinScreen(profile)
        assertEquals(id, dock.overlay, "an overlay-mode panel opens over the screen")

        dock.dismissOverlay()
        dock.setOverlayMode(id, false)
        dock.fold(id)
        dock.foldedByWidth = setOf(id)
        dock.pinScreen(profile)
        assertEquals(id, dock.overlay, "a column the window is too narrow for opens over the screen")
    }

    @Test
    fun onAFullWidthScreenOnlyThePanelsMadeForItDock() {
        val dock = controller().apply { canDock = true }
        val beside = dock.pinScreen(profile)
        val other = dock.pinScreen(Route.Profile("def"))
        // What the shell sets on a screen in FullWidthRoutes: only the panels marked besideFullWidth.
        dock.dockOnly = setOf(beside)

        dock.fold(beside)
        dock.show(beside)
        assertNull(dock.overlay, "the panel made to sit beside the screen docks")
        assertTrue(dock.isShowing(beside))

        dock.fold(other)
        dock.show(other)
        assertEquals(other, dock.overlay, "any other opens over the screen")

        dock.dismissOverlay()
        dock.dockOnly = null
        dock.show(other)
        assertNull(dock.overlay, "elsewhere every panel docks")
    }

    @Test
    fun aPinnedScreenKeepsItsHistoryUntilUnpinned() {
        val dock = controller()
        val id = dock.pinScreen(profile)
        val stacks = dock.screenStacks(id, profile)
        stacks.push(Route.Home)
        dock.fold(id)
        dock.show(id)
        assertSame(stacks, dock.screenStacks(id, profile), "folding and showing it again keeps its history")
        assertEquals(Route.Home, dock.screenStacks(id, profile).topRoute)

        dock.unpin(id)
        assertNotSame(stacks, dock.screenStacks(id, profile))
    }

    @Test
    fun unpinnedWhileDrawnItLetsGoWhenThePanelDoes() {
        val dock = controller()
        val id = dock.pinScreen(profile)
        val site = Any()
        dock.claim(id, site)
        val probe = Probe()
        dock.storeFor(id).put("probe", probe)

        dock.unpin(id)
        assertFalse(probe.cleared, "still on screen until the next frame")
        // The panel's dispose, as PanelState and PanelHost run it.
        dock.keepState(id, mapOf("scroll" to listOf(12)))
        assertNull(dock.savedState(id), "an unpinned panel's state is not kept")
        dock.release(id, site)
        assertTrue(probe.cleared)
        assertTrue(dock.mayDraw(id, Any()))
    }

    @Test
    fun unpinnedOffScreenItsViewModelsGoAtOnce() {
        val dock = controller()
        val id = dock.pinScreen(profile)
        val probe = Probe()
        dock.storeFor(id).put("probe", probe)
        dock.unpin(id)
        assertTrue(probe.cleared)
    }
}
