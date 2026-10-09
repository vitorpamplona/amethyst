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

import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import java.awt.Rectangle
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SavedWindowBoundsTest {
    private val laptop = Rectangle(0, 0, 1440, 900)
    private val monitorOnTheRight = Rectangle(1440, 0, 2560, 1440)

    private fun store(vararg screens: Rectangle) = SavedWindowBounds(File(createTempDirectory().toFile(), "window-bounds.properties")) { screens.toList() }

    @Test
    fun opensWhereItWasLeft() {
        val store = store(laptop)
        val bounds = WindowBounds(x = 40f, y = 60f, width = 1200f, height = 780f, maximized = false)
        store.save(bounds)
        assertEquals(bounds, store.load())
    }

    @Test
    fun keepsMaximized() {
        val store = store(laptop)
        val bounds = WindowBounds(x = 40f, y = 60f, width = 1200f, height = 780f, maximized = true)
        store.save(bounds)
        assertEquals(WindowPlacement.Maximized, store.load()?.placement)
    }

    @Test
    fun nothingSavedOpensAtTheDefault() {
        assertNull(store(laptop).load())
    }

    @Test
    fun aWindowLeftOnAnUnpluggedMonitorOpensAtTheDefault() {
        val file = File(createTempDirectory().toFile(), "window-bounds.properties")
        SavedWindowBounds(file) { listOf(laptop, monitorOnTheRight) }
            .save(WindowBounds(x = 2000f, y = 100f, width = 1380f, height = 920f, maximized = false))

        assertNull(SavedWindowBounds(file) { listOf(laptop) }.load())
    }

    @Test
    fun aWindowHalfOffTheScreenStillOpensThereWhileItsTitleBarCanBeGrabbed() {
        val store = store(laptop)
        val bounds = WindowBounds(x = 1000f, y = 100f, width = 1380f, height = 920f, maximized = false)
        store.save(bounds)
        assertEquals(bounds, store.load())
    }

    @Test
    fun aDamagedFileOpensAtTheDefault() {
        val file = File(createTempDirectory().toFile(), "window-bounds.properties")
        file.writeText("x=left\ny=10\n")
        assertNull(SavedWindowBounds(file) { listOf(laptop) }.load())
    }

    @Test
    fun fullScreenIsNotSaved() {
        val state = WindowState(placement = WindowPlacement.Fullscreen, position = WindowPosition(10.dp, 10.dp), size = DpSize(800.dp, 600.dp))
        assertNull(state.boundsToSave(lastFloating = null))
    }

    @Test
    fun maximizedKeepsTheBoundsToGoBackTo() {
        val floating = WindowBounds(x = 40f, y = 60f, width = 1200f, height = 780f, maximized = false)
        val state = WindowState(placement = WindowPlacement.Maximized, position = WindowPosition(0.dp, 0.dp), size = DpSize(1440.dp, 860.dp))
        assertEquals(floating.copy(maximized = true), state.boundsToSave(floating))
    }

    @Test
    fun aCenteredWindowIsNotSavedBeforeItHasAPosition() {
        val state = WindowState(position = WindowPosition.Aligned(Alignment.Center), size = DpSize(800.dp, 600.dp))
        assertNull(state.boundsToSave(lastFloating = null))
    }
}
