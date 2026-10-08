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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.threadview

import com.vitorpamplona.amethyst.commons.model.Note
import kotlin.test.Test
import kotlin.test.assertEquals

class FocusedNoteAncestorsTest {
    private fun note(n: Int) = Note(n.toString(16).padStart(64, '0'))

    @Test
    fun keepsTheFocusedNoteAndEveryReplyAboveIt() {
        // Depth-first: root, A (1), A1 (2), A1a (3), B (1), B1 (2) — focus A1a.
        val root = note(0)
        val a = note(1)
        val a1 = note(2)
        val a1a = note(3)
        val b = note(4)
        val b1 = note(5)
        val thread = listOf(root, a, a1, a1a, b, b1)
        val levels = mapOf(root to 0, a to 1, a1 to 2, a1a to 3, b to 1, b1 to 2)

        assertEquals(setOf(a1a.idHex, a1.idHex, a.idHex, root.idHex), focusedNoteAndAncestors(thread, levels, a1a.idHex))
        // A sibling branch is not an ancestor.
        assertEquals(setOf(b1.idHex, b.idHex, root.idHex), focusedNoteAndAncestors(thread, levels, b1.idHex))
    }
}
