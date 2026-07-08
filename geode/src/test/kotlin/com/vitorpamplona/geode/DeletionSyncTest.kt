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
package com.vitorpamplona.geode

import com.vitorpamplona.geode.testing.RelayClientTest
import com.vitorpamplona.geode.testing.preload
import com.vitorpamplona.geode.testing.publish
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.deletionSideChannelFilter
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.excludesDeletionKinds
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.negentropyPropagateDeletions
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.shouldPropagateDeletionUp
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip09Deletions.DeletionEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip62RequestToVanish.RequestToVanishEvent
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A NIP-77 sync reconciles by id over the content filter, so a scoped sync
 * (`--kind 1`) never carries the kind-5/62 that would delete one of those notes
 * — the deletion is stuck on whichever side issued it. The deletion side-channel
 * ([negentropyPropagateDeletions]) closes that gap by reconciling kinds 5 & 62
 * on their own, both directions, independent of the content filter.
 *
 * Scenario under test (the user's "Relay A has a deletion, Relay B doesn't"):
 * the note lives on both sides; a kind-5 deleting it lives on only one. After the
 * side-channel runs, the deletion has reached the other side and the note is gone
 * there too.
 */
class DeletionSyncTest : RelayClientTest() {
    private val signer = NostrSignerSync(KeyPair())

    private fun note(text: String): Event = signer.sign(TextNoteEvent.build(text))

    private fun deletionOf(target: Event): Event = signer.sign(DeletionEvent.build(listOf(target), createdAt = target.createdAt + 1))

    // ---- filter helpers (pure) --------------------------------------------

    @Test
    fun unscopedFilterNeedsNoSideChannel() {
        assertFalse(Filter().excludesDeletionKinds(), "no kinds constraint already matches deletions")
        assertFalse(Filter(kinds = listOf(1, 5)).excludesDeletionKinds(), "explicit kind 5 is covered")
        assertFalse(Filter(kinds = listOf(62)).excludesDeletionKinds(), "explicit kind 62 is covered")
        assertTrue(Filter(kinds = listOf(1)).excludesDeletionKinds(), "kind-1-only drops deletions")
    }

    @Test
    fun sideChannelFilterCarriesAuthorsNotWindow() {
        val authored = Filter(kinds = listOf(1), authors = listOf("aa", "bb"), since = 100, until = 200)
        val side = authored.deletionSideChannelFilter()

        assertEquals(listOf(DeletionEvent.KIND, RequestToVanishEvent.KIND), side.kinds)
        assertEquals(listOf("aa", "bb"), side.authors, "author scope is inherited")
        assertNull(side.since, "no time window: a deletion's created_at is not its target's")
        assertNull(side.until)
    }

    @Test
    fun vanishGateHonorsDeclaredTargets() {
        val here = defaultRelayUrl
        val elsewhere = RelayUrlNormalizer.normalize("wss://elsewhere.example/")

        val delete = deletionOf(note("x"))
        val vanishHere = signer.sign(RequestToVanishEvent.build(here))
        val vanishElsewhere = signer.sign(RequestToVanishEvent.build(elsewhere))
        val vanishEverywhere = signer.sign(RequestToVanishEvent.buildVanishFromEverywhere())

        assertTrue(shouldPropagateDeletionUp(delete, elsewhere), "kind-5 is always safe to propagate")
        assertTrue(shouldPropagateDeletionUp(vanishHere, here), "vanish targeting this relay goes")
        assertFalse(shouldPropagateDeletionUp(vanishElsewhere, here), "vanish for another relay does not")
        assertTrue(shouldPropagateDeletionUp(vanishEverywhere, here), "ALL_RELAYS vanish goes anywhere")
    }

    @Test
    fun noOpWhenFilterAlreadyCoversDeletions() =
        runBlocking {
            val result =
                withTimeout(20_000) {
                    client.negentropyPropagateDeletions(
                        relay = defaultRelayUrl,
                        contentFilter = Filter(kinds = listOf(1, 5)),
                        localDeletions = listOf(deletionOf(note("x"))),
                        download = { error("must not download") },
                        upload = { error("must not upload") },
                    )
                }
            assertNull(result, "a filter that already covers 5/62 skips the side-channel")
        }

    // ---- up: local has the deletion, relay does not -----------------------

    @Test
    fun pushesLocalDeletionUpSoRelayRemovesTarget() =
        runBlocking {
            val note = note("delete me")
            val deletion = deletionOf(note)

            // Relay B: has the note, no deletion.
            defaultRelay.preload(listOf(note))
            assertEquals(1, defaultRelay.store.query<Event>(Filter(ids = listOf(note.id))).size)

            // Local side (Relay A) already applied the deletion, so it holds only
            // the kind-5. A content sync over kind 1 would never carry it.
            val uploaded = mutableListOf<Event>()
            withTimeout(20_000) {
                client.negentropyPropagateDeletions(
                    relay = defaultRelayUrl,
                    contentFilter = Filter(kinds = listOf(1)),
                    localDeletions = listOf(deletion),
                    download = { error("relay has no deletions to pull") },
                    upload = { event ->
                        uploaded += event
                        defaultRelay.publish(event)
                    },
                )
            }

            assertEquals(listOf(deletion.id), uploaded.map { it.id }, "the deletion was pushed up")
            assertTrue(
                defaultRelay.store.query<Event>(Filter(ids = listOf(note.id))).isEmpty(),
                "relay applied the pushed deletion and removed the note",
            )
        }

    // ---- down: relay has the deletion, local does not ---------------------

    @Test
    fun pullsRelayDeletionDownSoLocalRemovesTarget() =
        runBlocking {
            val note = note("delete me too")
            val deletion = deletionOf(note)

            // Remote relay already applied the deletion → holds only the kind-5.
            defaultRelay.preload(listOf(note, deletion))
            assertTrue(
                defaultRelay.store.query<Event>(Filter(ids = listOf(note.id))).isEmpty(),
                "precondition: relay removed the note when it ingested the deletion",
            )

            // Local side: a second store still holding the note, no deletion.
            val localUrl = RelayUrlNormalizer.normalize("ws://local-a/")
            val local = hub.getOrCreate(localUrl)
            local.preload(listOf(note))
            assertEquals(1, local.store.query<Event>(Filter(ids = listOf(note.id))).size)

            withTimeout(20_000) {
                client.negentropyPropagateDeletions(
                    relay = defaultRelayUrl,
                    contentFilter = Filter(kinds = listOf(1)),
                    localDeletions = emptyList(),
                    download = { ids: List<HexKey> ->
                        // Stand-in for REQ-by-id + verify + store: pull from the
                        // remote in-process store and ingest into the local one.
                        defaultRelay.store.query<Event>(Filter(ids = ids)).forEach { local.store.insert(it) }
                    },
                    upload = { error("local has no deletions to push") },
                )
            }

            assertTrue(
                local.store.query<Event>(Filter(ids = listOf(note.id))).isEmpty(),
                "local store applied the pulled deletion and removed the note",
            )
        }
}
