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
package com.vitorpamplona.quartz.cordn.spec02Envelopes

import com.vitorpamplona.quartz.cordn.spec02Envelopes.CordnMessageReferences.PinOp
import com.vitorpamplona.quartz.cordn.spec02Envelopes.CordnMessageReferences.Target
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The authorization rules for annotations, which is what this index really is.
 *
 * Nothing about "fold reactions onto messages" is interesting. What is
 * interesting is who is allowed to change whose message, and those rules differ
 * per annotation — author-only for edits and deletions, any-member for pins —
 * so each one gets a test that fails loudly if it is loosened.
 */
class CordnAnnotationIndexTest {
    private val alice = "aa".repeat(32)
    private val bob = "bb".repeat(32)

    private var cursor = 0L

    private fun message(
        author: HexKey,
        kind: Int,
        content: String,
        tags: Array<Array<String>> = emptyArray(),
        createdAt: Long = 1_757_000_000L,
    ): CordnDeliveredMessage =
        CordnDeliveredMessage(
            envelope = CordnEnvelope.build(author, createdAt, kind, tags, content),
            cursor = ++cursor,
        )

    private fun CordnDeliveredMessage.asTarget() = Target(envelope.id, envelope.pubKey, envelope.kind, envelope.tags)

    @Test
    fun `reactions gather by emoji and by who sent them`() {
        val note = message(alice, CordnMessageKinds.TEXT, "ship it")
        val index =
            CordnAnnotationIndex.of(
                listOf(
                    note,
                    message(bob, CordnMessageKinds.REACTION, "+", CordnMessageReferences.reactionTags(note.asTarget())),
                    message(alice, CordnMessageKinds.REACTION, "+", CordnMessageReferences.reactionTags(note.asTarget())),
                    message(bob, CordnMessageKinds.REACTION, "🎉", CordnMessageReferences.reactionTags(note.asTarget())),
                ),
            )

        assertEquals(setOf(alice, bob), index.reactions[note.envelope.id]?.get("+"))
        assertEquals(setOf(bob), index.reactions[note.envelope.id]?.get("🎉"))
    }

    @Test
    fun `only the author may edit, and the newest edit wins`() {
        val note = message(alice, CordnMessageKinds.TEXT, "original", createdAt = 100)
        val tags = CordnMessageReferences.editTags(note.asTarget())

        val index =
            CordnAnnotationIndex.of(
                listOf(
                    note,
                    message(bob, CordnMessageKinds.EDIT, "bob rewrote this", tags, createdAt = 200),
                    message(alice, CordnMessageKinds.EDIT, "first fix", tags, createdAt = 300),
                    message(alice, CordnMessageKinds.EDIT, "final fix", tags, createdAt = 400),
                ),
            )

        assertEquals("final fix", index.contentOf(note.envelope.id))
        assertTrue(index.isEdited(note.envelope.id))
    }

    @Test
    fun `an edit by someone else changes nothing at all`() {
        // Stated separately because it is the whole point: without the author
        // check, anyone in the group can rewrite anyone's words.
        val note = message(alice, CordnMessageKinds.TEXT, "original")
        val index =
            CordnAnnotationIndex.of(
                listOf(
                    note,
                    message(
                        bob,
                        CordnMessageKinds.EDIT,
                        "bob rewrote this",
                        CordnMessageReferences.editTags(note.asTarget()),
                    ),
                ),
            )

        assertEquals("original", index.contentOf(note.envelope.id))
        assertTrue(!index.isEdited(note.envelope.id))
    }

    @Test
    fun `only the author may delete`() {
        val mine = message(alice, CordnMessageKinds.TEXT, "mine")
        val theirs = message(bob, CordnMessageKinds.TEXT, "theirs")

        val index =
            CordnAnnotationIndex.of(
                listOf(
                    mine,
                    theirs,
                    message(alice, CordnMessageKinds.DELETION, "", CordnMessageReferences.deleteTags(mine.asTarget())),
                    message(alice, CordnMessageKinds.DELETION, "", CordnMessageReferences.deleteTags(theirs.asTarget())),
                ),
            )

        assertTrue(index.isDeleted(mine.envelope.id))
        assertTrue(!index.isDeleted(theirs.envelope.id), "alice must not be able to delete bob's message")
    }

    @Test
    fun `a deletion whose k disagrees with the target is not a deletion`() {
        val note = message(alice, CordnMessageKinds.TEXT, "mine")
        val wrongKind =
            arrayOf(
                arrayOf("e", note.envelope.id, "", alice),
                arrayOf("k", CordnMessageKinds.THREAD_REPLY.toString()),
            )

        val index = CordnAnnotationIndex.of(listOf(note, message(alice, CordnMessageKinds.DELETION, "", wrongKind)))

        assertTrue(!index.isDeleted(note.envelope.id), "a mismatched k is a mismatch, not a typo to forgive")
    }

    @Test
    fun `a deleted message stops accepting edits`() {
        // Order is load-bearing in the fold: deletions are resolved before
        // edits so a late edit cannot resurrect withdrawn text.
        val note = message(alice, CordnMessageKinds.TEXT, "regrettable", createdAt = 100)
        val index =
            CordnAnnotationIndex.of(
                listOf(
                    note,
                    message(
                        alice,
                        CordnMessageKinds.DELETION,
                        "",
                        CordnMessageReferences.deleteTags(note.asTarget()),
                        createdAt = 200,
                    ),
                    message(
                        alice,
                        CordnMessageKinds.EDIT,
                        "back again",
                        CordnMessageReferences.editTags(note.asTarget()),
                        createdAt = 300,
                    ),
                ),
            )

        assertTrue(index.isDeleted(note.envelope.id))
        assertTrue(!index.isEdited(note.envelope.id), "an edit must not undo a deletion")
    }

    @Test
    fun `any member may pin, and the last write wins`() {
        val note = message(alice, CordnMessageKinds.TEXT, "important", createdAt = 100)
        val t = note.asTarget()

        val pinnedByBob =
            CordnAnnotationIndex.of(
                listOf(note, message(bob, CordnMessageKinds.PIN, "", CordnMessageReferences.pinTags(t, PinOp.ADD), 200)),
            )
        assertTrue(pinnedByBob.isPinned(note.envelope.id), "pinning is any-member, unlike editing")
        assertEquals(bob, pinnedByBob.pins[note.envelope.id]?.pinnedBy)

        val thenUnpinnedByAlice =
            CordnAnnotationIndex.of(
                listOf(
                    note,
                    message(bob, CordnMessageKinds.PIN, "", CordnMessageReferences.pinTags(t, PinOp.ADD), 200),
                    message(alice, CordnMessageKinds.PIN, "", CordnMessageReferences.pinTags(t, PinOp.REMOVE), 300),
                ),
            )
        assertTrue(!thenUnpinnedByAlice.isPinned(note.envelope.id), "the newest op wins")
    }

    @Test
    fun `same-second pins break the tie on the cursor`() {
        // Two clients acting in the same second must agree on the outcome. The
        // coordinator's cursor is the only total order both can see.
        val note = message(alice, CordnMessageKinds.TEXT, "important", createdAt = 100)
        val t = note.asTarget()

        val index =
            CordnAnnotationIndex.of(
                listOf(
                    note,
                    message(bob, CordnMessageKinds.PIN, "", CordnMessageReferences.pinTags(t, PinOp.ADD), 200),
                    message(alice, CordnMessageKinds.PIN, "", CordnMessageReferences.pinTags(t, PinOp.REMOVE), 200),
                ),
            )

        assertTrue(!index.isPinned(note.envelope.id), "the later cursor decides when the timestamps match")
    }

    @Test
    fun `pinned messages come back newest first`() {
        val first = message(alice, CordnMessageKinds.TEXT, "one", createdAt = 100)
        val second = message(alice, CordnMessageKinds.TEXT, "two", createdAt = 110)

        val index =
            CordnAnnotationIndex.of(
                listOf(
                    first,
                    second,
                    message(alice, CordnMessageKinds.PIN, "", CordnMessageReferences.pinTags(first.asTarget(), PinOp.ADD), 200),
                    message(bob, CordnMessageKinds.PIN, "", CordnMessageReferences.pinTags(second.asTarget(), PinOp.ADD), 300),
                ),
            )

        assertEquals(listOf(second.envelope.id, first.envelope.id), index.pinnedIds())
    }

    @Test
    fun `an annotation pointing at nothing is ignored`() {
        // Real case: catch-up starts after the target, so the annotation
        // arrives without it. Dropping it beats rendering an orphan row.
        val absent = Target("f".repeat(64), alice, CordnMessageKinds.TEXT)
        val index =
            CordnAnnotationIndex.of(
                listOf(
                    message(alice, CordnMessageKinds.EDIT, "x", CordnMessageReferences.editTags(absent)),
                    message(alice, CordnMessageKinds.DELETION, "", CordnMessageReferences.deleteTags(absent)),
                ),
            )

        assertTrue(index.edits.isEmpty())
        assertTrue(index.deleted.isEmpty())
    }
}
