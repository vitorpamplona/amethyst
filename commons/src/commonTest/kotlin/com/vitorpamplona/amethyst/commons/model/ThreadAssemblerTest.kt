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

import com.vitorpamplona.amethyst.commons.model.cache.ICacheEventStream
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Thread anchoring rules: replies and comments (kind 1 / 1111) load the full
 * parent thread; reactions and zaps anchor their own thread — the event is the
 * root and only its reply subtree is included, not the conversation of the
 * post they target.
 */
class ThreadAssemblerTest {
    private val originalId = "a".repeat(64)
    private val zapId = "b".repeat(64)
    private val reactionId = "c".repeat(64)
    private val replyToZapId = "d".repeat(64)
    private val authorKey = "e".repeat(64)
    private val sig = "f".repeat(128)

    private val original =
        Note(originalId).apply {
            event = TextNoteEvent(originalId, authorKey, 1000, arrayOf(arrayOf("t", "test")), "the zapped post", sig)
            replyTo = emptyList()
        }

    private val zap =
        Note(zapId).apply {
            event = LnZapEvent(zapId, authorKey, 1001, arrayOf(arrayOf("e", originalId), arrayOf("p", authorKey)), "", sig)
            replyTo = listOf(original)
        }

    private val reaction =
        Note(reactionId).apply {
            event = ReactionEvent(reactionId, authorKey, 1001, arrayOf(arrayOf("e", originalId), arrayOf("p", authorKey)), "+", sig)
            replyTo = listOf(original)
        }

    private val replyToZap =
        Note(replyToZapId).apply {
            event = CommentEvent(replyToZapId, authorKey, 1002, arrayOf(arrayOf("E", zapId), arrayOf("e", zapId)), "nice zap!", sig)
            replyTo = listOf(zap)
        }

    private val cache =
        StubCache(
            mapOf(
                originalId to original,
                zapId to zap,
                reactionId to reaction,
                replyToZapId to replyToZap,
            ),
        )

    init {
        // Mirrors LocalCache: replies link bidirectionally, but zaps and
        // reactions are credited via addZap/addReaction, not addReply, so the
        // original note's `replies` does not contain them.
        zap.addReply(replyToZap)
    }

    @Test
    fun zapAnchorsItsOwnThread() {
        val info = ThreadAssembler(cache).findThreadFor(zapId)!!

        assertEquals(zap, info.root)
        assertTrue(info.allNotes.contains(zap))
        assertTrue(info.allNotes.contains(replyToZap), "the zap's replies belong to its thread")
        assertFalse(info.allNotes.contains(original), "the zapped post's thread must not be loaded")
    }

    @Test
    fun reactionAnchorsItsOwnThread() {
        val info = ThreadAssembler(cache).findThreadFor(reactionId)!!

        assertEquals(reaction, info.root)
        assertTrue(info.allNotes.contains(reaction))
        assertFalse(info.allNotes.contains(original), "the liked post's thread must not be loaded")
    }

    @Test
    fun commentReplyingToAZapStillLoadsTheFullParentThread() {
        val info = ThreadAssembler(cache).findThreadFor(replyToZapId)!!

        assertEquals(replyToZap, info.root)
        assertTrue(info.allNotes.contains(replyToZap))
        assertTrue(info.allNotes.contains(zap), "parents load for replies/comments")
        assertTrue(info.allNotes.contains(original), "parents load all the way to the thread root")
    }

    private class StubCache(
        private val notesById: Map<HexKey, Note>,
    ) : ICacheProvider {
        override fun getAnyChannel(note: Note): Channel? = null

        override fun getUserIfExists(pubkey: HexKey): User? = null

        override fun countUsers(predicate: (String, User) -> Boolean): Int = 0

        override fun getNoteIfExists(hexKey: HexKey): Note? = notesById[hexKey]

        override fun checkGetOrCreateNote(hexKey: HexKey): Note? = notesById[hexKey]

        override fun getOrCreateAddressableNote(key: Address): AddressableNote = error("not used by ThreadAssembler in this test")

        override fun getEventStream(): ICacheEventStream = error("not used by ThreadAssembler in this test")

        override fun hasBeenDeleted(event: Any): Boolean = false

        override fun getOrCreateUser(pubkey: HexKey): User? = null

        override fun justConsumeMyOwnEvent(event: Event): Boolean = false
    }
}
