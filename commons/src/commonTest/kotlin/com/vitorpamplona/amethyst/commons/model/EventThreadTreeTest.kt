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
package com.vitorpamplona.amethyst.commons.model

import com.vitorpamplona.amethyst.commons.actions.ReplyActions
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EventThreadTreeTest {
    private val alice = NostrSignerInternal(KeyPair("0000000000000000000000000000000000000000000000000000000000000007".hexToByteArray()))
    private val bob = NostrSignerInternal(KeyPair("0000000000000000000000000000000000000000000000000000000000000008".hexToByteArray()))

    @Test
    fun layoutIsDepthFirstAndOldestFirst() =
        runTest {
            val root = alice.sign(TextNoteEvent.build("root", createdAt = 100))
            val a = bob.sign(TextNoteEvent.build("a", EventHintBundle(root), createdAt = 200))
            val b = bob.sign(TextNoteEvent.build("b", EventHintBundle(root), createdAt = 150))
            val a1 = alice.sign(TextNoteEvent.build("a1", EventHintBundle(a), createdAt = 300))
            val like = bob.sign(ReactionEvent.build("+", EventHintBundle(root)))

            val entries = EventThreadTree.layout(root.id, listOf(a1, like, a, root, b))

            assertEquals(listOf(root.id, b.id, a.id, a1.id), entries.map { it.event.id }, "reactions are not replies")
            assertEquals(listOf(0, 1, 1, 2), entries.map { it.depth })
            assertEquals(a.id, entries.last().parentId)
            assertTrue(entries.none { it.parentMissing })
        }

    @Test
    fun repliesToUnfetchedParentsHangUnderTheRoot() =
        runTest {
            val root = alice.sign(TextNoteEvent.build("root"))
            val mid = ReplyActions.replyTo(EventHintBundle(root), "mid", bob)
            val leaf = ReplyActions.replyTo(EventHintBundle(mid), "leaf", alice)

            val entries = EventThreadTree.layout(root.id, listOf(root, leaf))
            assertEquals(listOf(root.id, leaf.id), entries.map { it.event.id })
            assertTrue(entries.last().parentMissing)
            assertEquals(1, entries.last().depth)
        }

    @Test
    fun missingRootStartsRepliesAtDepthOne() =
        runTest {
            val root = alice.sign(TextNoteEvent.build("root"))
            val reply = ReplyActions.replyTo(EventHintBundle(root), "reply", bob)

            val entries = EventThreadTree.layout(root.id, listOf(reply))
            assertEquals(1, entries.single().depth)
            assertFalse(entries.single().parentMissing)
        }

    @Test
    fun rootAndParentOf() =
        runTest {
            val root = alice.sign(TextNoteEvent.build("root"))
            val mid = ReplyActions.replyTo(EventHintBundle(root), "mid", bob)
            val leaf = ReplyActions.replyTo(EventHintBundle(mid), "leaf", alice)

            assertNull(EventThreadTree.rootOf(root))
            assertNull(EventThreadTree.parentOf(root))
            assertEquals(root.id, EventThreadTree.rootOf(leaf))
            assertEquals(mid.id, EventThreadTree.parentOf(leaf))
        }
}
