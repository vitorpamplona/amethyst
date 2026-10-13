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
package com.vitorpamplona.amethyst.desktop.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.awt.RenderSettings
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.LocalWindowExceptionHandlerFactory
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import java.awt.Color
import java.awt.Dialog
import java.awt.EventQueue
import java.awt.Frame
import java.awt.GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSLUCENT
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.SwingUtilities
import javax.swing.WindowConstants
import kotlin.math.roundToInt
import java.awt.Window as AwtWindow
import java.awt.event.KeyEvent as AwtKeyEvent

/**
 * What a window's content gets: its AWT frame, and, when Compose's own [Window] hosts it, that
 * window's [FrameWindowScope] (the macOS menu bar and title bar need it; neither exists on Linux).
 */
class AppWindowScope(
    val window: JFrame,
    val frameScope: FrameWindowScope?,
)

/**
 * A top-level window: Compose's [Window] everywhere it can draw, and a Swing-drawn [WaylandWindow]
 * when the app runs natively on Wayland ([WaylandDisplay]), where Compose's window surface can't.
 */
@Composable
fun AppWindow(
    onCloseRequest: () -> Unit,
    state: WindowState,
    title: String,
    icon: Painter? = null,
    undecorated: Boolean = false,
    transparent: Boolean = false,
    resizable: Boolean = true,
    alwaysOnTop: Boolean = false,
    onPreviewKeyEvent: (KeyEvent) -> Boolean = { false },
    content: @Composable AppWindowScope.() -> Unit,
) {
    if (WaylandDisplay.isActive) {
        WaylandWindow(onCloseRequest, state, title, icon, undecorated, transparent, resizable, alwaysOnTop, onPreviewKeyEvent, content)
    } else {
        Window(
            onCloseRequest = onCloseRequest,
            state = state,
            title = title,
            icon = icon,
            undecorated = undecorated,
            transparent = transparent,
            resizable = resizable,
            alwaysOnTop = alwaysOnTop,
            onPreviewKeyEvent = onPreviewKeyEvent,
        ) {
            val scope = remember(window) { AppWindowScope(window, this) }
            scope.content()
        }
    }
}

/**
 * A [JFrame] holding a [ComposePanel] that draws through Swing ([RenderSettings.SwingGraphics]):
 * Skiko renders each frame on the CPU and Swing hands it to the compositor. Compose's [Window] can't
 * be told to draw that way (its panel always asks Skiko for a native surface, which needs X11), so
 * this does what it does for the parts the app uses: the [WindowState] both ways, the close request,
 * window-wide key handling and the crash window.
 *
 * Wayland doesn't let a window place itself or learn where it is, so the position in [state] is
 * whatever AWT reports; the compositor ignores it on the way in.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun WaylandWindow(
    onCloseRequest: () -> Unit,
    state: WindowState,
    title: String,
    icon: Painter?,
    undecorated: Boolean,
    transparent: Boolean,
    resizable: Boolean,
    alwaysOnTop: Boolean,
    onPreviewKeyEvent: (KeyEvent) -> Boolean,
    content: @Composable AppWindowScope.() -> Unit,
) {
    val currentContent by rememberUpdatedState(content)
    val currentOnCloseRequest by rememberUpdatedState(onCloseRequest)
    val currentOnPreviewKeyEvent by rememberUpdatedState(onPreviewKeyEvent)
    val exceptionHandlerFactory = LocalWindowExceptionHandlerFactory.current

    val frame = remember { WaylandFrame() }

    SideEffect {
        frame.title = title
        frame.isResizable = resizable
        frame.isAlwaysOnTop = alwaysOnTop
    }

    DisposableEffect(frame) {
        val panel = ComposePanel(renderSettings = RenderSettings.SwingGraphics())
        val scope = AppWindowScope(frame, frameScope = null)
        panel.exceptionHandler = exceptionHandlerFactory.exceptionHandler(frame)
        panel.setContent { currentContent(scope) }

        frame.defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
        frame.isUndecorated = undecorated
        if (transparent && frame.graphicsConfiguration.device.isWindowTranslucencySupported(PERPIXEL_TRANSLUCENT)) {
            frame.background = Color(0, 0, 0, 0)
        }
        icon?.let { frame.iconImage = it.toAwtImage(Density(1f), LayoutDirection.Ltr, Size(ICON_SIZE, ICON_SIZE)) }
        frame.contentPane.add(panel)

        frame.applySize(state.size)
        when (val position = state.position) {
            is WindowPosition.Absolute -> frame.setLocation(position.x.value.roundToInt(), position.y.value.roundToInt())
            is WindowPosition.Aligned -> frame.setLocationRelativeTo(null)
            WindowPosition.PlatformDefault -> frame.isLocationByPlatform = true
        }

        val closeListener =
            object : WindowAdapter() {
                override fun windowClosing(e: WindowEvent) = currentOnCloseRequest()

                override fun windowStateChanged(e: WindowEvent) {
                    state.isMinimized = frame.extendedState and Frame.ICONIFIED != 0
                    state.placement = frame.placement()
                }
            }
        frame.addWindowListener(closeListener)
        frame.addWindowStateListener(closeListener)

        val boundsListener =
            object : ComponentAdapter() {
                override fun componentResized(e: ComponentEvent) = saveFloatingBounds()

                override fun componentMoved(e: ComponentEvent) = saveFloatingBounds()

                private fun saveFloatingBounds() {
                    if (frame.placement() != WindowPlacement.Floating) return
                    state.size = DpSize(frame.width.dp, frame.height.dp)
                    state.position = WindowPosition(frame.x.dp, frame.y.dp)
                }
            }
        frame.addComponentListener(boundsListener)

        // Compose's Window runs onPreviewKeyEvent ahead of the focused composable; a dispatcher on
        // this window's AWT keys does the same here.
        val keyDispatcher =
            KeyEventDispatcher { event ->
                val source = event.component
                val owner = source as? AwtWindow ?: source?.let { SwingUtilities.getWindowAncestor(it) }
                owner === frame && currentOnPreviewKeyEvent(event.toComposeKeyEvent())
            }
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(keyDispatcher)

        frame.isVisible = true
        frame.applyPlacement(state.placement)

        onDispose {
            KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(keyDispatcher)
            frame.removeWindowListener(closeListener)
            frame.removeWindowStateListener(closeListener)
            frame.removeComponentListener(boundsListener)
            if (frame.graphicsConfiguration?.device?.fullScreenWindow === frame) {
                frame.graphicsConfiguration.device.fullScreenWindow = null
            }
            panel.dispose()
            frame.dispose()
        }
    }

    // The app changing the state (going full screen, say) after the window opened.
    LaunchedEffect(frame) {
        snapshotFlow { state.placement }.collect { if (frame.placement() != it) frame.applyPlacement(it) }
    }
    LaunchedEffect(frame) {
        snapshotFlow { state.size }.collect { size ->
            if (frame.placement() == WindowPlacement.Floating && size.isSpecified && DpSize(frame.width.dp, frame.height.dp) != size) {
                frame.applySize(size)
            }
        }
    }
}

/**
 * The Compose event for an AWT key event, as Compose's own window builds it before dispatching. The
 * builder is internal to Compose (its own conversion is `internal`), hence the opt-in.
 */
@OptIn(InternalComposeUiApi::class)
private fun AwtKeyEvent.toComposeKeyEvent(): KeyEvent =
    KeyEvent(
        key = Key(keyCode, keyLocation),
        type =
            when (id) {
                AwtKeyEvent.KEY_PRESSED -> KeyEventType.KeyDown
                AwtKeyEvent.KEY_RELEASED -> KeyEventType.KeyUp
                else -> KeyEventType.Unknown
            },
        codePoint = keyChar.code,
        isCtrlPressed = isControlDown,
        isMetaPressed = isMetaDown,
        isAltPressed = isAltDown,
        isShiftPressed = isShiftDown,
        nativeEvent = this,
    )

/**
 * The JetBrains Runtime's Wayland toolkit tears every window down from a shutdown hook, on its own
 * thread, and a [ComposePanel] refuses to be detached off the event thread: each logout or kill
 * left a "crash" report for the next start. The JVM is exiting then, and the compositor drops the
 * window's surfaces when the process disconnects, so off the event thread the teardown is skipped.
 * Closing a window normally detaches it on the event thread, as before.
 */
internal class WaylandFrame : JFrame() {
    override fun removeNotify() {
        if (EventQueue.isDispatchThread()) super.removeNotify()
    }
}

/** A dialog that skips the same shutdown-time teardown as [WaylandFrame]. */
internal class WaylandDialog(
    owner: AwtWindow?,
    modality: Dialog.ModalityType,
) : JDialog(owner, modality) {
    override fun removeNotify() {
        if (EventQueue.isDispatchThread()) super.removeNotify()
    }
}

private fun JFrame.applySize(size: DpSize) {
    val width = if (size.width.isSpecified) size.width.value.roundToInt() else DEFAULT_WIDTH
    val height = if (size.height.isSpecified) size.height.value.roundToInt() else DEFAULT_HEIGHT
    setSize(width, height)
}

private fun JFrame.placement(): WindowPlacement =
    when {
        graphicsConfiguration?.device?.fullScreenWindow === this -> WindowPlacement.Fullscreen
        extendedState and Frame.MAXIMIZED_BOTH == Frame.MAXIMIZED_BOTH -> WindowPlacement.Maximized
        else -> WindowPlacement.Floating
    }

private fun JFrame.applyPlacement(placement: WindowPlacement) {
    val device = graphicsConfiguration?.device ?: return
    when (placement) {
        WindowPlacement.Fullscreen -> {
            if (device.isFullScreenSupported) device.fullScreenWindow = this
        }

        WindowPlacement.Maximized -> {
            if (device.fullScreenWindow === this) device.fullScreenWindow = null
            extendedState = extendedState or Frame.MAXIMIZED_BOTH
        }

        WindowPlacement.Floating -> {
            if (device.fullScreenWindow === this) device.fullScreenWindow = null
            extendedState = extendedState and Frame.MAXIMIZED_BOTH.inv()
        }
    }
}

private const val ICON_SIZE = 256f
private const val DEFAULT_WIDTH = 800
private const val DEFAULT_HEIGHT = 600
