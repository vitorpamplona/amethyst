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
package com.vitorpamplona.amethyst.commons.notifications

import com.vitorpamplona.amethyst.commons.model.Note
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class MergeMessageSetsTest {
    private val chatA = Pair("alice", 20000L)
    private val chatB = Pair("bob", 20000L)

    @Test
    fun aNewMessageFoldsIntoItsChatsCard() {
        val old = MessageSetCard(persistentListOf(Note("a1"), Note("a2")), chatA)
        val other = NoteCard(Note("reply"))
        val incoming = MessageSetCard(persistentListOf(Note("a3")), chatA)

        val merged = mergeMessageSets(listOf(old, other), listOf(incoming))

        assertEquals(2, merged.size, "the new message joins the old card instead of adding one")
        val card = merged[0] as MessageSetCard
        assertEquals(setOf("a1", "a2", "a3"), card.messages.map { it.idHex }.toSet())
        assertEquals(chatA, card.groupKey)
        assertSame(other, merged[1])
    }

    @Test
    fun anotherChatKeepsItsOwnCard() {
        val old = MessageSetCard(persistentListOf(Note("a1")), chatA)
        val incoming = MessageSetCard(persistentListOf(Note("b1")), chatB)

        val merged = mergeMessageSets(listOf(old), listOf(incoming))

        assertEquals(listOf<Any>(old, incoming), merged)
    }

    @Test
    fun aMessageAlreadyInTheCardIsNotCountedTwice() {
        val shared = Note("a1")
        val old = MessageSetCard(persistentListOf(shared), chatA)
        val incoming = MessageSetCard(persistentListOf(shared), chatA)

        val merged = mergeMessageSets(listOf(old), listOf(incoming))

        assertEquals(1, (merged.single() as MessageSetCard).messages.size)
    }

    @Test
    fun cardsWithoutAGroupKeyAreLeftAlone() {
        val old = MessageSetCard(Note("a1"))
        val incoming = MessageSetCard(Note("a2"))

        val merged = mergeMessageSets(listOf(old), listOf(incoming))

        assertEquals(listOf<Any>(old, incoming), merged)
    }
}
