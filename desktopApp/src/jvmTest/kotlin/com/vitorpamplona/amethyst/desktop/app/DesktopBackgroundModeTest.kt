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
package com.vitorpamplona.amethyst.desktop.app

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopBackgroundModeTest {
    @Test
    fun closingQuitsUnlessASignerIsRunning() {
        assertEquals(BackgroundClose.QUIT, BackgroundCloseRules.onClose(signerRunning = false, trayAvailable = true))
        assertEquals(BackgroundClose.QUIT, BackgroundCloseRules.onClose(signerRunning = false, trayAvailable = false))
    }

    @Test
    fun aRunningSignerKeepsTheAppInTheTrayOnlyWhereThereIsOne() {
        assertEquals(BackgroundClose.TRAY, BackgroundCloseRules.onClose(signerRunning = true, trayAvailable = true))
        // No tray to come back from: quit, as without a signer.
        assertEquals(BackgroundClose.QUIT, BackgroundCloseRules.onClose(signerRunning = true, trayAvailable = false))
    }

    private class Window : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }

    @Test
    fun aHiddenWindowLooksStoppedToTheScreens() {
        val window = Window().apply { registry.currentState = Lifecycle.State.RESUMED }
        val screens = HiddenWindowLifecycle(window)
        val events = mutableListOf<Lifecycle.Event>()
        screens.lifecycle.addObserver(LifecycleEventObserver { _, event -> events += event })
        events.clear()

        assertEquals(Lifecycle.State.RESUMED, screens.lifecycle.currentState)

        // Closed to the tray: AWT still calls the window STARTED, the screens see it stopped, so
        // the lifecycle-aware feed subscriptions start their unsubscribe grace.
        screens.hidden = true
        assertEquals(Lifecycle.State.CREATED, screens.lifecycle.currentState)
        assertTrue(Lifecycle.Event.ON_STOP in events)

        // The window losing focus meanwhile changes nothing for the screens.
        window.registry.currentState = Lifecycle.State.STARTED
        assertEquals(Lifecycle.State.CREATED, screens.lifecycle.currentState)

        // Shown again: back to the window's own state, and the feeds resubscribe.
        events.clear()
        screens.hidden = false
        assertEquals(Lifecycle.State.STARTED, screens.lifecycle.currentState)
        assertEquals(listOf(Lifecycle.Event.ON_START), events)
    }

    @Test
    fun minimizedOrDestroyedWindowsPassThrough() {
        assertEquals(Lifecycle.State.CREATED, HiddenWindowLifecycle.capped(Lifecycle.State.CREATED, hidden = false))
        assertEquals(Lifecycle.State.DESTROYED, HiddenWindowLifecycle.capped(Lifecycle.State.DESTROYED, hidden = true))
        assertEquals(Lifecycle.State.CREATED, HiddenWindowLifecycle.capped(Lifecycle.State.RESUMED, hidden = true))
    }

    @Test
    fun aWindowInTheTrayKeepsTheAppRunning() {
        val windows = DesktopWindowManager { File.createTempFile("sizes", ".properties").apply { deleteOnExit() } }
        windows.hideToTray(layout = null)

        assertFalse(windows.mainWindowVisible)
        assertTrue(windows.keptInTray)
        // Hidden in the tray is not "everything closed": the app must not quit.
        assertFalse(windows.allClosed)

        windows.showMainWindow(raise = false)
        assertTrue(windows.mainWindowVisible)
        assertFalse(windows.keptInTray)
    }

    @Test
    fun closingTheLastScreenWindowAsksTheBackgroundFirst() {
        val windows = DesktopWindowManager { File.createTempFile("sizes", ".properties").apply { deleteOnExit() } }
        var quit = false
        // The main window was hidden while screen windows stayed open; now the last of them closed.
        windows.onAllClosed(layout = null, exit = { quit = true }, keepRunning = { true })
        assertFalse(quit)

        windows.onAllClosed(layout = null, exit = { quit = true }, keepRunning = { false })
        assertTrue(quit)
    }

    @Test
    fun aCloseThatWouldQuitAsksTheBackgroundFirst() {
        val windows = DesktopWindowManager { File.createTempFile("sizes", ".properties").apply { deleteOnExit() } }
        var quit = false
        windows.closeMainWindow(layout = null, exit = { quit = true }, keepRunning = { true })
        assertFalse(quit)

        windows.closeMainWindow(layout = null, exit = { quit = true }, keepRunning = { false })
        assertTrue(quit)
    }
}
