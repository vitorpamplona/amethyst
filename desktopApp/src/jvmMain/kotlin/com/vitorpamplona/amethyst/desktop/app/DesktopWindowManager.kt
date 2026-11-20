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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import com.vitorpamplona.amethyst.commons.model.navigation.NavArrangement
import com.vitorpamplona.amethyst.commons.ui.navigation.windows.ScreenWindowSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.withContext
import java.io.File
import javax.swing.JFrame

private const val CASCADE_STEP = 32
private const val WINDOW_GAP = 8
private const val SIZE_SAVE_DEBOUNCE_MS = 500L

/**
 * Every OS window the app has open: the main window, which hides rather than closes while screen
 * windows remain, and the screen windows by entry. It knows which one has the focus (Cmd/Ctrl+W
 * closes it, its toasts show there), where a new one goes, and the size each kind was last left at.
 */
class DesktopWindowManager(
    sizesFile: () -> File,
) {
    /** How big each kind of screen window was last left, so the next one opens at that size. */
    private val sizes by lazy { ScreenWindowSizes(sizesFile()) }

    private var mainFrame: JFrame? = null

    private val screenFrames = mutableStateMapOf<Long, JFrame>()

    /** False while the main window is closed with screen windows still open: the app keeps running. */
    var mainWindowVisible by mutableStateOf(true)
        private set

    /** The screen window with the focus, if one has it. */
    var focusedScreenWindow by mutableStateOf<Long?>(null)
        private set

    /** The layout the main window was hidden in. */
    private var hiddenIn: NavArrangement? = null

    /**
     * True while the main window is hidden because the reader closed it and chose to keep a print
     * going: the app runs on, unseen, until the print ends or the window is shown again.
     */
    var keptForPrint by mutableStateOf(false)
        private set

    /** Whether any window of the app is on screen. */
    val anyShowing: Boolean get() = mainWindowVisible || screenFrames.isNotEmpty()

    /** Nothing is left on screen: the main window is hidden and the last screen window is gone (not a window kept hidden for a print). */
    val allClosed: Boolean get() = !mainWindowVisible && screenFrames.isEmpty() && !keptForPrint

    /** Screen windows are open beside the main one. */
    val hasScreenWindows: Boolean get() = screenFrames.isNotEmpty()

    /**
     * Closing the main window in [layout] quits, unless screen windows are open: then it only hides.
     * While a print runs ([printing]) it asks first ([ask]) instead of quitting.
     */
    fun closeMainWindow(
        layout: NavArrangement?,
        exit: () -> Unit,
        printing: Boolean = false,
        ask: () -> Unit = exit,
    ) {
        when (PrintQuitRules.onClose(printing, screenFrames.isNotEmpty())) {
            CloseAction.QUIT -> exit()
            CloseAction.ASK -> ask()
            CloseAction.HIDE -> {
                hiddenIn = layout
                mainWindowVisible = false
            }
        }
    }

    /** "Keep printing": the main window hides, and the app runs on until the print ends. */
    fun hideForPrint() {
        keptForPrint = true
        mainWindowVisible = false
    }

    /**
     * Called once [allClosed] holds, with the [layout] the app is in now. The user closed the last
     * window: quit, but while a print runs ([printing]) ask first ([ask]), as closing the main
     * window does: quitting would stop a print that may be paid for. A layout switch closed them
     * instead (it drops the screen windows in the same frame): bring the main window back. Telling
     * the two apart by the layout, rather than by which effect runs first, keeps a settings change
     * from quitting the app.
     */
    fun onAllClosed(
        layout: NavArrangement?,
        exit: () -> Unit,
        printing: Boolean = false,
        ask: () -> Unit = exit,
    ) {
        if (layout != hiddenIn) {
            showMainWindow(raise = false)
            return
        }
        when (PrintQuitRules.onQuit(printing)) {
            CloseAction.ASK -> ask()
            else -> exit()
        }
    }

    /** Brings the main window back, and to the front when [raise]. */
    fun showMainWindow(raise: Boolean = true) {
        mainWindowVisible = true
        keptForPrint = false
        if (raise) {
            mainFrame?.let {
                it.toFront()
                it.requestFocus()
            }
        }
    }

    /** Raises every Amethyst window, the main one last so it lands on top. */
    fun bringAllToFront() {
        screenFrames.values.forEach { it.toFront() }
        showMainWindow()
    }

    /** Keeps track of the main window's frame, the anchor for screen windows without an opener. */
    @Composable
    fun MainWindowEffects(frame: JFrame) {
        DisposableEffect(frame) {
            mainFrame = frame
            onDispose { if (mainFrame === frame) mainFrame = null }
        }
    }

    /** Where screen window [spec] opens: beside its opener, at the size the last one of its kind was left at. */
    fun initialState(spec: ScreenWindowSpec): WindowState {
        val size = sizes.sizeFor(spec.kind)
        return WindowState(size = size, position = placeBeside(spec.openerId, size))
    }

    /**
     * Registers screen window [spec]'s [frame] while it is open, follows its focus, raises it when
     * its screen is asked for again, and remembers the size it is left at for the next of its kind.
     */
    @OptIn(FlowPreview::class)
    @Composable
    fun ScreenWindowEffects(
        spec: ScreenWindowSpec,
        frame: JFrame,
        state: WindowState,
        focused: Boolean,
    ) {
        LaunchedEffect(focused) {
            if (focused) {
                focusedScreenWindow = spec.id
            } else if (focusedScreenWindow == spec.id) {
                focusedScreenWindow = null
            }
        }
        DisposableEffect(frame) {
            screenFrames[spec.id] = frame
            onDispose {
                screenFrames.remove(spec.id)
                if (focusedScreenWindow == spec.id) focusedScreenWindow = null
            }
        }
        LaunchedEffect(spec.focus) {
            if (spec.focus != null) {
                frame.toFront()
                frame.requestFocus()
            }
        }
        val kind = remember(spec.id) { spec.kind }
        LaunchedEffect(state) {
            snapshotFlow { state.size }
                .drop(1)
                .debounce(SIZE_SAVE_DEBOUNCE_MS)
                .collect { withContext(Dispatchers.IO) { sizes.save(kind, it) } }
        }
    }

    /**
     * Where a new screen window goes: right of the window that opened it when it fits on that
     * screen, else left of it, else stepped down from it. Without an opener on screen, the main
     * window is the anchor; without that, the OS picks.
     */
    private fun placeBeside(
        openerId: Long?,
        size: DpSize,
    ): WindowPosition {
        val anchor = openerId?.let { screenFrames[it] } ?: mainFrame ?: return WindowPosition.PlatformDefault
        if (!anchor.isShowing) return WindowPosition.PlatformDefault
        val screen = anchor.graphicsConfiguration.bounds
        val width = size.width.value.toInt()
        val height = size.height.value.toInt()
        val y = anchor.y.coerceIn(screen.y, (screen.y + screen.height - height).coerceAtLeast(screen.y))
        val right = anchor.x + anchor.width + WINDOW_GAP
        val left = anchor.x - width - WINDOW_GAP
        val x =
            when {
                right + width <= screen.x + screen.width -> right
                left >= screen.x -> left
                else -> (anchor.x + CASCADE_STEP).coerceAtMost((screen.x + screen.width - width).coerceAtLeast(screen.x))
            }
        val cascadeY = if (x == anchor.x + CASCADE_STEP) (anchor.y + CASCADE_STEP).coerceAtMost((screen.y + screen.height - height).coerceAtLeast(screen.y)) else y
        return WindowPosition(x.dp, cascadeY.dp)
    }
}
