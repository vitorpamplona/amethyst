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
package com.vitorpamplona.amethyst.desktop.ui.chats

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.test.Test
import kotlin.test.assertEquals

class InsertAtCursorTest {
    @Test
    fun insertsAtCaretAndMovesCursorAfter() {
        val result = insertAtCursor(TextFieldValue("ab", TextRange(1)), "X")
        assertEquals("aXb", result.text)
        assertEquals(TextRange(2), result.selection)
    }

    @Test
    fun insertsAtEnd() {
        val result = insertAtCursor(TextFieldValue("hi ", TextRange(3)), "🔥")
        assertEquals("hi 🔥", result.text)
        assertEquals(3 + "🔥".length, result.selection.start)
    }

    @Test
    fun replacesSelection() {
        // Selecting "bc" (range 1..3) and inserting "X" replaces the selection.
        val result = insertAtCursor(TextFieldValue("abcd", TextRange(1, 3)), "X")
        assertEquals("aXd", result.text)
        assertEquals(TextRange(2), result.selection)
    }
}
