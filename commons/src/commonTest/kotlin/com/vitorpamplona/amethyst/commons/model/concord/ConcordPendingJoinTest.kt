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
package com.vitorpamplona.amethyst.commons.model.concord

import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Channel
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.ICacheEventStream
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEntry
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEvent
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListFragmentEvent
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.hints.HintIndexer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Accepting an invite must show the community at once. The List write that makes the membership
 * durable waits for the List to load from every stock and outbox relay (up to ~30 s on a real
 * network), and the hub used to sit on "You haven't joined any…" for all of it. A join the List
 * cannot take yet is held as pending: visible in [ConcordChannelListState.liveCommunities] (so the
 * session is built and the hub lists it) without ever being written.
 */
class ConcordPendingJoinTest {
    private val signer = NostrSignerInternal(KeyPair("0000000000000000000000000000000000000000000000000000000000000009".hexToByteArray()))

    private val alpha = "a".repeat(64)

    private fun entry(id: String) =
        ConcordCommunityListEntry(
            id = id,
            owner = signer.pubKey,
            ownerSalt = "1".repeat(64),
            root = "2".repeat(64),
            rootEpoch = 3,
            relays = listOf("ws://127.0.0.1:7777"),
            name = "Alpha",
            addedAt = 1000,
        )

    private class NoBackupRepository : ConcordListRepository {
        override fun concordList(): ConcordCommunityListEvent? = null

        override fun updateConcordListTo(newConcordList: ConcordCommunityListEvent?) {}
    }

    private class StubCache : ICacheProvider {
        private val notes = HashMap<String, AddressableNote>()

        override fun getAnyChannel(note: Note): Channel? = null

        override val relayHints = HintIndexer()

        override fun getUserIfExists(pubkey: HexKey): User? = null

        override fun countUsers(predicate: (String, User) -> Boolean): Int = 0

        override fun getNoteIfExists(hexKey: HexKey): Note? = null

        override fun checkGetOrCreateNote(hexKey: HexKey): Note? = null

        override fun getOrCreateAddressableNote(address: Address): AddressableNote = notes.getOrPut(address.toValue()) { AddressableNote(address) }

        override fun getEventStream(): ICacheEventStream = error("not used")

        override fun hasBeenDeleted(event: Any): Boolean = false

        override fun getOrCreateUser(pubkey: HexKey): User? = null

        override fun consumeEmbedded(event: Event) = Unit

        override fun justConsumeMyOwnEvent(event: Event): Boolean = false
    }

    private fun state() =
        ConcordChannelListState(
            signer = signer,
            cache = StubCache(),
            scope = CoroutineScope(Dispatchers.Unconfined),
            settings = NoBackupRepository(),
        )

    @Test
    fun aPendingJoinShowsBeforeTheListHasLoaded() =
        runTest {
            val state = state()
            assertFalse(state.relaysConfirmed)
            assertFalse(state.loaded.value, "the hub shows a loading state, not an empty one")

            state.addPendingJoin(entry(alpha))

            withTimeout(5000) { while (state.liveCommunities.value.isEmpty()) yield() }
            assertEquals(listOf(alpha), state.liveCommunities.value.map { it.id })
            // Shown, never written: the List itself still holds nothing.
            assertTrue(state.entries().isEmpty())
        }

    @Test
    fun aPendingJoinIsShownOnceWhenTheListAlsoHoldsIt() =
        runTest {
            val state = state()
            state.markRelaysConfirmed()
            assertTrue(state.loaded.value)
            state.addPendingJoin(entry(alpha))
            val events = state.follow(entry(alpha))
            val author = User(signer.pubKey) { addr -> Note(addr.toValue()) }
            val fragment = events.filterIsInstance<ConcordCommunityListFragmentEvent>().single()
            state.cache.getOrCreateAddressableNote(fragment.address()).loadEvent(fragment, author, emptyList())

            withTimeout(5000) { while (state.entries().isEmpty()) yield() }
            assertEquals(listOf(alpha), state.liveCommunities.value.map { it.id })

            state.dropPendingJoin(alpha)
            withTimeout(5000) { while (state.liveCommunities.value.isEmpty()) yield() }
            assertEquals(listOf(alpha), state.liveCommunities.value.map { it.id }, "the written entry stays after the pending one goes")
        }

    @Test
    fun droppingAFailedPendingJoinHidesIt() =
        runTest {
            val state = state()
            state.addPendingJoin(entry(alpha))
            withTimeout(5000) { while (state.liveCommunities.value.isEmpty()) yield() }
            state.dropPendingJoin(alpha)
            withTimeout(5000) { while (state.liveCommunities.value.isNotEmpty()) yield() }
            assertTrue(state.liveCommunities.value.isEmpty())
        }
}
