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

import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.UserContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HiddenThreadRepliesTest {
    private val context = UserContext { addr -> AddressableNote(addr) }

    private fun key(n: Int) = n.toString(16).padStart(64, '0')

    private fun note(
        n: Int,
        author: Int = 100 + n,
    ) = Note(key(n)).also { it.author = User(key(author), context) }

    @Test
    fun aHiddenReplyNobodyAnsweredIsDropped() {
        // root, A (1, hidden), B (1)
        val root = note(0)
        val a = note(1)
        val b = note(2)
        val thread = listOf(root, a, b)
        val levels = mapOf(root to 0, a to 1, b to 1)

        val hidden = hiddenThreadReplies(thread, levels, root.idHex, setOf(a))

        assertEquals(listOf(root, b), hidden.thread)
        assertEquals(emptySet(), hidden.placeholders)
    }

    @Test
    fun aHiddenReplyWithAVisibleAnswerKeepsItsPlace() {
        // root, A (1, hidden), A1 (2) — A1 still needs A's place to read as an answer to it.
        val root = note(0)
        val a = note(1)
        val a1 = note(2)
        val thread = listOf(root, a, a1)
        val levels = mapOf(root to 0, a to 1, a1 to 2)

        val hidden = hiddenThreadReplies(thread, levels, root.idHex, setOf(a))

        assertEquals(thread, hidden.thread)
        assertEquals(setOf(a.idHex), hidden.placeholders)
    }

    @Test
    fun aHiddenReplyAnsweredOnlyByHiddenRepliesIsDroppedWithThem() {
        // root, A (1, hidden), A1 (2, hidden), B (1)
        val root = note(0)
        val a = note(1)
        val a1 = note(2)
        val b = note(3)
        val thread = listOf(root, a, a1, b)
        val levels = mapOf(root to 0, a to 1, a1 to 2, b to 1)

        val hidden = hiddenThreadReplies(thread, levels, root.idHex, setOf(a, a1))

        assertEquals(listOf(root, b), hidden.thread)
        assertEquals(emptySet(), hidden.placeholders)
    }

    @Test
    fun theOpenedNoteAndTheHiddenRepliesAboveItKeepTheirPlace() {
        // root, A (1, hidden), A1 (2, hidden, opened from a notification)
        val root = note(0)
        val a = note(1)
        val a1 = note(2)
        val thread = listOf(root, a, a1)
        val levels = mapOf(root to 0, a to 1, a1 to 2)

        val hidden = hiddenThreadReplies(thread, levels, a1.idHex, setOf(a, a1))

        assertEquals(thread, hidden.thread)
        assertEquals(setOf(a.idHex, a1.idHex), hidden.placeholders)
    }

    @Test
    fun aVisibleReplyDeepDownKeepsEveryHiddenReplyAboveIt() {
        // root, A (1, hidden), A1 (2, hidden), A2 (3), B (1, hidden)
        val root = note(0)
        val a = note(1)
        val a1 = note(2)
        val a2 = note(3)
        val b = note(4)
        val thread = listOf(root, a, a1, a2, b)
        val levels = mapOf(root to 0, a to 1, a1 to 2, a2 to 3, b to 1)

        val hidden = hiddenThreadReplies(thread, levels, root.idHex, setOf(a, a1, b))

        assertEquals(listOf(root, a, a1, a2), hidden.thread)
        assertEquals(setOf(a.idHex, a1.idHex), hidden.placeholders)
    }

    @Test
    fun theHiddenRepliesUnderAHiddenOpenedNoteAreDropped() {
        // root, A (1, hidden, opened), A1 (2, hidden)
        val root = note(0)
        val a = note(1)
        val a1 = note(2)
        val thread = listOf(root, a, a1)
        val levels = mapOf(root to 0, a to 1, a1 to 2)

        val hidden = hiddenThreadReplies(thread, levels, a.idHex, setOf(a, a1))

        assertEquals(listOf(root, a), hidden.thread)
        assertEquals(setOf(a.idHex), hidden.placeholders)
    }

    @Test
    fun theRootIsLeftToTheThreadHeader() {
        val root = note(0)
        val a = note(1)
        val thread = listOf(root, a)
        val levels = mapOf(root to 0, a to 1)

        val hidden = hiddenThreadReplies(thread, levels, root.idHex, setOf(root, a))

        assertEquals(listOf(root), hidden.thread)
        assertEquals(emptySet(), hidden.placeholders)
    }

    @Test
    fun theNetworkCollapseSkipsHiddenReplies() {
        // root, A (1, hidden, outside), A1 (2, outside), B (1, outside). A keeps its place for A1,
        // so it is in the thread, but the collapse leaves it to the "hidden by you" card.
        val root = note(0)
        val a = note(1)
        val a1 = note(2)
        val b = note(3)
        val thread = listOf(root, a, a1, b)
        val levels = mapOf(root to 0, a to 1, a1 to 2, b to 1)
        val outside: (String) -> Boolean? = { false }

        val collapsed = outOfNetworkReplies(thread, levels, root.idHex, skip = setOf(a.idHex), isKnown = outside)

        assertEquals(setOf(a1.idHex, b.idHex), collapsed)
    }

    @Test
    fun aHiddenAuthorInTheNetworkDoesNotKeepTheReplyAboveItOpen() {
        // root, A (1, outside), A1 (2, hidden, in the network). A known answer keeps A open, but
        // a hidden one is not shown as an answer.
        val root = note(0)
        val a = note(1)
        val a1 = note(2)
        val thread = listOf(root, a, a1)
        val levels = mapOf(root to 0, a to 1, a1 to 2)
        val known: (String) -> Boolean? = { it == key(102) } // A1's author

        assertEquals(emptySet(), outOfNetworkReplies(thread, levels, root.idHex, skip = emptySet(), isKnown = known))
        assertTrue(a.idHex in outOfNetworkReplies(thread, levels, root.idHex, skip = setOf(a1.idHex), isKnown = known))
    }
}
