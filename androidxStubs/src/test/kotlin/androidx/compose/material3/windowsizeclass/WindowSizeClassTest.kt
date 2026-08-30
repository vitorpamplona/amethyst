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
package androidx.compose.material3.windowsizeclass

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The breakpoints decide the desktop's navigation shell — bottom bar, nav rail
 * or permanent drawer — so getting one wrong hands a wide window a phone
 * layout, or a narrow one a drawer it cannot fit.
 */
class WindowSizeClassTest {
    private fun widthAt(dp: Int) = WindowSizeClass.calculateFromSize(DpSize(dp.dp, 800.dp)).widthSizeClass

    private fun heightAt(dp: Int) = WindowSizeClass.calculateFromSize(DpSize(1000.dp, dp.dp)).heightSizeClass

    @Test
    fun `width uses Material's 600 and 840 dp breakpoints`() {
        assertEquals(WindowWidthSizeClass.Compact, widthAt(0))
        assertEquals(WindowWidthSizeClass.Compact, widthAt(599))
        assertEquals(WindowWidthSizeClass.Medium, widthAt(600))
        assertEquals(WindowWidthSizeClass.Medium, widthAt(839))
        assertEquals(WindowWidthSizeClass.Expanded, widthAt(840))
        assertEquals(WindowWidthSizeClass.Expanded, widthAt(2560))
    }

    @Test
    fun `height uses Material's 480 and 900 dp breakpoints`() {
        assertEquals(WindowHeightSizeClass.Compact, heightAt(479))
        assertEquals(WindowHeightSizeClass.Medium, heightAt(480))
        assertEquals(WindowHeightSizeClass.Medium, heightAt(899))
        assertEquals(WindowHeightSizeClass.Expanded, heightAt(900))
    }

    @Test
    fun `the two axes are independent`() {
        // A wide, short window — a maximised laptop window in a shallow
        // display, or a desktop window dragged flat — is Expanded across and
        // Compact down, and the shell reads only the width.
        val sizeClass = WindowSizeClass.calculateFromSize(DpSize(1600.dp, 400.dp))
        assertEquals(WindowWidthSizeClass.Expanded, sizeClass.widthSizeClass)
        assertEquals(WindowHeightSizeClass.Compact, sizeClass.heightSizeClass)
    }

    @Test
    fun `classes order from narrow to wide`() {
        // MessagesScreen branches on != Compact, and other code compares; the
        // ordering has to be the obvious one.
        assert(WindowWidthSizeClass.Compact < WindowWidthSizeClass.Medium)
        assert(WindowWidthSizeClass.Medium < WindowWidthSizeClass.Expanded)
    }

    @Test
    fun `equal sizes compare equal`() {
        assertEquals(
            WindowSizeClass.calculateFromSize(DpSize(1000.dp, 700.dp)),
            WindowSizeClass.calculateFromSize(DpSize(1200.dp, 800.dp)),
        )
    }
}
