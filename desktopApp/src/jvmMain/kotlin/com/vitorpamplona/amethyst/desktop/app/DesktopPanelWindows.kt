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

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import com.vitorpamplona.amethyst.commons.model.dock.PanelId
import com.vitorpamplona.amethyst.commons.ui.navigation.dock.DockController
import java.awt.MouseInfo
import javax.swing.JFrame
import kotlin.math.hypot

/** How far the pointer moves before a press on a panel window's header drags it. */
private const val DRAG_SLOP_PX = 6

/**
 * Dragging a panel window by its header: the window follows the pointer, as by its title bar.
 * Over the main window it stops following and the dock lights up where the panel would land;
 * releasing there puts the panel back in the dock, which closes this window. The pointer is read on
 * the screen (AWT's [MouseInfo]), since the window moves under it.
 */
internal fun Modifier.panelWindowDrag(
    frame: JFrame,
    id: PanelId,
    windows: DesktopWindowManager,
    dock: DockController,
): Modifier =
    pointerInput(frame, id) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (down.type == PointerType.Mouse && !currentEvent.buttons.isPrimaryPressed) return@awaitEachGesture
            val startMouse = MouseInfo.getPointerInfo()?.location ?: return@awaitEachGesture
            val startFrame = frame.location
            var dragging = false
            while (true) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                if (change == null) {
                    if (dragging) dock.drag.cancel()
                    break
                }
                if (!change.pressed) {
                    if (dragging) {
                        change.consume()
                        if (dock.drag.hit != null) dock.drag.end() else dock.drag.cancel()
                    }
                    break
                }
                val now = MouseInfo.getPointerInfo()?.location ?: continue
                val dx = now.x - startMouse.x
                val dy = now.y - startMouse.y
                if (!dragging && hypot(dx.toDouble(), dy.toDouble()) >= DRAG_SLOP_PX) dragging = true
                if (!dragging) continue
                change.consume()
                val over = windows.inMainContent(now)
                dock.drag.moveFromOutside(id, over)
                // Off the main window the panel's window follows the pointer; over it, it stays put
                // so the drop zones under the pointer show.
                if (over == null) frame.setLocation(startFrame.x + dx, startFrame.y + dy)
            }
        }
    }
