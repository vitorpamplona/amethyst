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
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The "leave a Concord community" path: `unfollow` read-modify-writes the private kind-13302 list.
 * Everything that makes leaving safe lives here — it must drop only the named community, keep the
 * other memberships (and their secrets) intact, and be a pure local list edit that never depends on
 * the community's own (possibly dead) relays.
 */
class ConcordChannelListLeaveTest {
    private val signer = NostrSignerInternal(KeyPair("0000000000000000000000000000000000000000000000000000000000000007".hexToByteArray()))

    private val alpha = "a".repeat(64)
    private val beta = "b".repeat(64)

    private fun entry(
        id: String,
        name: String,
    ) = ConcordCommunityListEntry(
        id = id,
        owner = signer.pubKey,
        ownerSalt = "1".repeat(64),
        root = "2".repeat(64),
        rootEpoch = 3,
        relays = listOf("ws://127.0.0.1:7777"),
        name = name,
        addedAt = 1000,
    )

    /** Serves the list only from the offline backup — the state a dead-relay community lands in. */
    private class BackupOnlyRepository(
        var saved: ConcordCommunityListEvent?,
    ) : ConcordListRepository {
        override fun concordList() = saved

        override fun updateConcordListTo(newConcordList: ConcordCommunityListEvent?) {
            saved = newConcordList
        }
    }

    private class StubCache : ICacheProvider {
        override fun getAnyChannel(note: Note): Channel? = null

        override fun getUserIfExists(pubkey: HexKey): User? = null

        override fun countUsers(predicate: (String, User) -> Boolean): Int = 0

        override fun getNoteIfExists(hexKey: HexKey): Note? = null

        override fun checkGetOrCreateNote(hexKey: HexKey): Note? = null

        override fun getOrCreateAddressableNote(key: Address): AddressableNote = AddressableNote(key)

        override fun getEventStream(): ICacheEventStream = error("not used")

        override fun hasBeenDeleted(event: Any): Boolean = false

        override fun getOrCreateUser(pubkey: HexKey): User? = null

        override fun justConsumeMyOwnEvent(event: Event): Boolean = false
    }

    private suspend fun state(vararg entries: ConcordCommunityListEntry) =
        ConcordChannelListState(
            signer = signer,
            cache = StubCache(),
            scope = CoroutineScope(Dispatchers.Unconfined),
            // The cached note is empty (nothing folded from relays), so every read falls back to the
            // offline backup — exactly the situation for a community whose relays no longer answer.
            settings = BackupOnlyRepository(ConcordCommunityListEvent.create(signer, entries.toList())),
        )

    @Test
    fun leavingDropsOnlyThatCommunity() =
        runTest {
            val list = state(entry(alpha, "Alpha"), entry(beta, "Beta"))

            val left = list.unfollow(alpha)!!
            val remaining = left.decrypt(signer)

            assertEquals(1, remaining.size)
            assertEquals(beta, remaining[0].id)
            // The surviving membership keeps its secrets — leaving one community must not damage another.
            assertEquals("2".repeat(64), remaining[0].root)
            assertEquals(3L, remaining[0].rootEpoch)
        }

    @Test
    fun leavingTheLastCommunityEmptiesTheList() =
        runTest {
            val list = state(entry(alpha, "Alpha"))

            val left = list.unfollow(alpha)!!

            assertTrue(left.decrypt(signer).isEmpty())
        }

    /** Nothing to publish when we weren't a member: the caller's publish is a no-op on null. */
    @Test
    fun leavingSomethingWeNeverJoinedIsANoOp() =
        runTest {
            val list = state(entry(alpha, "Alpha"))

            assertNull(list.unfollow(beta))
        }

    /**
     * The list is only readable by its owner, so the leave write must stay self-encrypted — a leave
     * that leaked the remaining memberships in cleartext would be worse than not leaving at all.
     */
    @Test
    fun theRewrittenListStaysSelfEncrypted() =
        runTest {
            val list = state(entry(alpha, "Alpha"), entry(beta, "Beta"))

            val left = list.unfollow(alpha)!!

            assertEquals(ConcordCommunityListEvent.KIND, left.kind)
            assertTrue(beta !in left.content)
            val stranger = NostrSignerInternal(KeyPair("0000000000000000000000000000000000000000000000000000000000000009".hexToByteArray()))
            assertTrue(left.decrypt(stranger).isEmpty())
        }
}
