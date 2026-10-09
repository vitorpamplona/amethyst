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

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import com.vitorpamplona.quartz.utils.Log
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.io.File
import java.util.Properties

/**
 * Where the main window was when the app last ran: its size and position (in the same logical
 * units Compose sizes windows in) and whether it was maximized. Kept in a small file in the
 * profile's data directory, so each profile opens where it was left.
 */
data class WindowBounds(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val maximized: Boolean,
) {
    val size get() = DpSize(width.dp, height.dp)
    val position get() = WindowPosition(x.dp, y.dp)
    val placement get() = if (maximized) WindowPlacement.Maximized else WindowPlacement.Floating
}

class SavedWindowBounds(
    private val file: File,
    /** The screens' bounds now, in the units windows are positioned in. */
    private val screens: () -> List<Rectangle> = {
        GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.map { it.defaultConfiguration.bounds }
    },
) {
    /** The saved bounds, or null when there are none or they would open off every screen now. */
    fun load(): WindowBounds? {
        if (!file.isFile) return null
        return try {
            val props = Properties().apply { file.inputStream().use { load(it) } }
            val bounds =
                WindowBounds(
                    x = props.getProperty(X)?.toFloat() ?: return null,
                    y = props.getProperty(Y)?.toFloat() ?: return null,
                    width = props.getProperty(WIDTH)?.toFloat() ?: return null,
                    height = props.getProperty(HEIGHT)?.toFloat() ?: return null,
                    maximized = props.getProperty(MAXIMIZED).toBoolean(),
                )
            bounds.takeIf { it.width >= MIN_SIZE && it.height >= MIN_SIZE && isTitleBarOnAScreen(it) }
        } catch (e: Exception) {
            Log.w("SavedWindowBounds", "Could not read the window bounds", e)
            null
        }
    }

    fun save(bounds: WindowBounds) {
        try {
            val props =
                Properties().apply {
                    setProperty(X, bounds.x.toString())
                    setProperty(Y, bounds.y.toString())
                    setProperty(WIDTH, bounds.width.toString())
                    setProperty(HEIGHT, bounds.height.toString())
                    setProperty(MAXIMIZED, bounds.maximized.toString())
                }
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.outputStream().use { props.store(it, null) }
            if (!temp.renameTo(file)) {
                file.delete()
                temp.renameTo(file)
            }
        } catch (e: Exception) {
            Log.w("SavedWindowBounds", "Could not save the window bounds", e)
        }
    }

    companion object {
        private const val X = "x"
        private const val Y = "y"
        private const val WIDTH = "width"
        private const val HEIGHT = "height"
        private const val MAXIMIZED = "maximized"
        private const val MIN_SIZE = 200f

        /** How much of the title bar must be on a screen to grab and move the window. */
        private const val GRAB_WIDTH = 100
        private const val GRAB_HEIGHT = 30
    }

    /**
     * A screen that was there when the window was saved may be gone (an unplugged monitor): then the
     * window would open where it can't be seen or moved, so it opens at the default instead.
     */
    private fun isTitleBarOnAScreen(bounds: WindowBounds): Boolean {
        val titleBar = Rectangle(bounds.x.toInt(), bounds.y.toInt(), bounds.width.toInt(), GRAB_HEIGHT)
        return screens().any { screen ->
            val visible = titleBar.intersection(screen)
            !visible.isEmpty && visible.width >= minOf(GRAB_WIDTH, titleBar.width) && visible.height >= GRAB_HEIGHT
        }
    }
}

/**
 * The window's bounds to save, or null while it has none worth keeping: before it has a position,
 * or in full screen, which it should not reopen in. A maximized window keeps the floating bounds it
 * had before, [lastFloating], so un-maximizing after a restart goes back to them.
 */
fun WindowState.boundsToSave(lastFloating: WindowBounds?): WindowBounds? {
    val position = position
    return when (placement) {
        WindowPlacement.Fullscreen -> null
        WindowPlacement.Maximized -> lastFloating?.copy(maximized = true)
        WindowPlacement.Floating ->
            if (position is WindowPosition.Absolute && size.isSpecified) {
                WindowBounds(position.x.value, position.y.value, size.width.value, size.height.value, maximized = false)
            } else {
                null
            }
    }
}
