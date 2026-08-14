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
package com.vitorpamplona.amethyst.commons.ui.note

import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Channel
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.ICacheEventStream
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.hints.HintIndexer
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertSame

class ParentNoteTest {
    private val communityOwner = "9ca0bd7450742d6a20319c0e3d4c679c9e046a9dc70e8ef55c2905e24052340b"
    private val definitionEventId = "575117c37d66a698ddd81169f88fcdab5d5d63687f79da0c5c65f1d72cb99a57"
    private val moviesAddress = Address(34550, communityOwner, "movies")
    private val movies = moviesAddress.toValue()

    private fun comment(vararg tags: Array<String>) =
        CommentEvent(
            id = "00".repeat(32),
            pubKey = "11".repeat(32),
            createdAt = 1_700_000_000L,
            tags = arrayOf(*tags),
            content = "Ciao a tutti",
            sig = "22".repeat(64),
        )

    private fun noteOf(event: Event) = Note(event.id).apply { this.event = event }

    /**
     * The reported bug. A mostr-bridged top-level post in the "movies" community, viewed before
     * the community definition has been fetched: the cache hands back an empty AddressableNote,
     * which the old `event?.kind != KIND` test accepted (null != 34550) and rendered as a blank
     * "replying to" card.
     */
    @Test
    fun topLevelCommunityPostHasNoParentWhenTheDefinitionIsNotCachedYet() {
        val event =
            comment(
                arrayOf("A", movies, "wss://relay.mostr.pub/"),
                arrayOf("a", movies, "wss://relay.mostr.pub/"),
                arrayOf("E", definitionEventId, "wss://relay.mostr.pub/", communityOwner),
                arrayOf("K", "34550"),
                arrayOf("e", definitionEventId, "wss://relay.mostr.pub/", communityOwner),
                arrayOf("k", "34550"),
            )

        // An AddressableNote that exists in the cache but whose definition event has not arrived.
        val unloadedCommunity = AddressableNote(moviesAddress)
        val cache = StubCache(notesById = mapOf(movies to unloadedCommunity))

        assertNull(replyingDirectlyTo(noteOf(event), cache))
    }

    /** Same post once the definition has loaded: still no parent card, via the address kind. */
    @Test
    fun topLevelCommunityPostHasNoParentOnceTheDefinitionLoads() {
        val event =
            comment(
                arrayOf("A", movies),
                arrayOf("a", movies),
                arrayOf("K", "34550"),
                arrayOf("k", "34550"),
            )

        val community = AddressableNote(moviesAddress)
        val cache = StubCache(notesById = mapOf(movies to community))

        assertNull(replyingDirectlyTo(noteOf(event), cache))
    }

    /**
     * The bridged shape that points at the definition by *id*. That id can never resolve, because
     * addressable events are cached by address -- so it must not be offered as a parent either.
     */
    @Test
    fun bridgedCommunityPostPointingAtTheDefinitionByIdHasNoParent() {
        val event =
            comment(
                arrayOf("E", definitionEventId, "", communityOwner),
                arrayOf("K", "34550"),
                arrayOf("e", definitionEventId, "", communityOwner),
                arrayOf("k", "34550"),
            )

        val note = noteOf(event)
        // What LocalCache builds for an `e` tag: a plain Note keyed by id that stays empty.
        val neverLoads = Note(definitionEventId)
        note.replyTo = listOf(neverLoads)
        val cache = StubCache(notesById = mapOf(definitionEventId to neverLoads))

        assertNull(replyingDirectlyTo(note, cache))
    }

    /**
     * A reply to a post *inside* a community keeps `K` = 34550 but points `k` at the parent's
     * kind. The real parent must still be resolved -- the fix must not swallow these.
     */
    @Test
    fun nestedReplyInsideACommunityStillResolvesItsParent() {
        val parentId = "33".repeat(32)
        val event =
            comment(
                arrayOf("A", movies),
                arrayOf("K", "34550"),
                arrayOf("e", parentId),
                arrayOf("k", "1111"),
            )

        val parent = Note(parentId)
        val note = noteOf(event)
        note.replyTo = listOf(AddressableNote(moviesAddress), parent)
        val cache = StubCache(notesById = mapOf(parentId to parent, movies to AddressableNote(moviesAddress)))

        assertSame(parent, replyingDirectlyTo(note, cache))
    }

    /** A post in a channel is rendered by the channel header, not as a parent note. */
    @Test
    fun noteInsideAChannelIsNotOfferedAsAParent() {
        val parentId = "44".repeat(32)
        val event = comment(arrayOf("e", parentId), arrayOf("k", "1111"))
        val parent = Note(parentId)
        val note = noteOf(event)
        val cache = StubCache(notesById = mapOf(parentId to parent), channelFor = setOf(parentId))

        assertNull(replyingDirectlyTo(note, cache))
    }

    private class StubCache(
        private val notesById: Map<HexKey, Note>,
        private val channelFor: Set<HexKey> = emptySet(),
    ) : ICacheProvider {
        override val relayHints = HintIndexer()

        override fun getAnyChannel(note: Note): Channel? =
            if (note.idHex in channelFor) {
                object : Channel() {
                    override fun toBestDisplayName() = "a channel"
                }
            } else {
                null
            }

        override fun getUserIfExists(pubkey: HexKey): User? = null

        override fun countUsers(predicate: (String, User) -> Boolean): Int = 0

        override fun getNoteIfExists(hexKey: HexKey): Note? = notesById[hexKey]

        override fun checkGetOrCreateNote(hexKey: HexKey): Note? = notesById[hexKey]

        override fun getOrCreateAddressableNote(address: Address): AddressableNote = error("not used by replyingDirectlyTo")

        override fun getEventStream(): ICacheEventStream = error("not used by replyingDirectlyTo")

        override fun hasBeenDeleted(event: Any): Boolean = false

        override fun getOrCreateUser(pubkey: HexKey): User? = null

        override fun justConsumeMyOwnEvent(event: Event): Boolean = false
    }
}
