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
package com.vitorpamplona.geode.interop

import com.vitorpamplona.geode.LocalRelayServer
import com.vitorpamplona.geode.Relay
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAll
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.publishAndConfirm
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.BasicOkHttpWebSocket
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Kotlin counterpart to strfry's `test/syncTest.pl`: stand up two
 * Geode relays on real WebSocket endpoints, give each a partially
 * overlapping corpus, and converge them via NIP-77.
 *
 * The driver mirrors `strfry sync ws://other --dir both`:
 *
 *  1. Read the local relay's snapshot for the negotiated filter.
 *  2. Open NEG-OPEN against the remote with that snapshot.
 *  3. Drive NEG-MSG round trips until the client-side
 *     [com.vitorpamplona.quartz.nip77Negentropy.NegentropySession]
 *     reports completion.
 *  4. `needIds`: REQ them from the remote, insert into the local relay.
 *  5. `haveIds`: fetch from the local relay, publish to the remote.
 *
 * After the round, both relays must hold the union of the original
 * corpora. We assert via REQ on each side; an `id`-filter that returns
 * every event we expect, and nothing more, proves convergence
 * end-to-end through the NIP-77 server pipeline (`NegSessionRegistry`
 * → `NegentropyServerSession` → `IEventStore.snapshotIdsForNegentropy`).
 *
 * Equivalent to strfry's `runSyncTests.pl` "full DB sync" case at
 * small scale. Larger corpora belong in `LoadBenchmark`.
 */
class GeodeVsGeodeNegentropySyncTest {
    private lateinit var relayA: Relay
    private lateinit var relayB: Relay
    private lateinit var serverA: LocalRelayServer
    private lateinit var serverB: LocalRelayServer
    private lateinit var scope: CoroutineScope
    private lateinit var client: NostrClient
    private val httpClient = OkHttpClient.Builder().build()

    @BeforeTest
    fun setup() {
        // The placeholder URLs are normalised so the relay accepts
        // them; ports come from the autobind below via [server.url].
        relayA = Relay(url = "ws://127.0.0.1:7771/".normalizeRelayUrl())
        relayB = Relay(url = "ws://127.0.0.1:7772/".normalizeRelayUrl())
        serverA = LocalRelayServer(relayA, host = "127.0.0.1", port = 0).start()
        serverB = LocalRelayServer(relayB, host = "127.0.0.1", port = 0).start()
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        client = NostrClient(BasicOkHttpWebSocket.Builder { _ -> httpClient }, scope)
    }

    @AfterTest
    fun teardown() {
        client.disconnect()
        scope.cancel()
        serverA.stop()
        serverB.stop()
        relayA.close()
        relayB.close()
    }

    /** Generates [count] signed text notes with monotonic createdAt. */
    private fun makeEvents(
        count: Int,
        seed: Long = 1_700_000_000L,
    ): List<Event> {
        val signer = NostrSignerSync(KeyPair())
        return List(count) { i ->
            signer.sign(TextNoteEvent.build("event-$seed-$i", createdAt = seed + i))
        }
    }

    /**
     * One-way reconciliation from `srcRelay` ⇒ `dstRelay`'s perspective
     * (the destination is the side initiating the sync, mirroring
     * `strfry sync <other>` semantics where the calling instance pulls
     * from the remote).
     *
     * Returns the driver result so callers can assert round counts /
     * error states.
     */
    private fun pullSync(
        sourceWsUrl: String,
        destLocalEvents: List<Event>,
        filter: Filter,
    ): InteropSyncDriver.Result =
        InteropSyncDriver(httpClient).reconcile(
            wsUrl = sourceWsUrl,
            filter = filter,
            localEvents = destLocalEvents,
        )

    @Test
    fun bidirectionalSyncConvergesTwoRelays() =
        runBlocking {
            // Universe of 20 events. Relay A holds [0..14], Relay B
            // holds [5..19]. Overlap [5..14], A-only [0..4], B-only
            // [15..19]. After bidirectional sync both must hold [0..19].
            val all = makeEvents(20)
            val aEvents = all.subList(0, 15)
            val bEvents = all.subList(5, 20)
            relayA.preload(aEvents)
            relayB.preload(bEvents)

            val filter = Filter(kinds = listOf(1))

            // --- B pulls from A ---
            val pullAtoB = pullSync(serverA.url, bEvents, filter)
            assertNull(pullAtoB.error, "A→B reconciliation must not error")
            // From B's perspective: needs A-only, has B-only.
            assertEquals(
                aEvents.subList(0, 5).map { it.id }.toSet(),
                pullAtoB.needIds,
                "B should NEED [0..4] from A",
            )
            assertEquals(
                bEvents.subList(10, 15).map { it.id }.toSet(),
                pullAtoB.haveIds,
                "B should announce HAVE for [15..19]",
            )

            // Close the loop: fetch needs from A, push haves to A.
            val needFromA =
                client.fetchAll(
                    relay = serverA.url.normalizeRelayUrl(),
                    filter = Filter(ids = pullAtoB.needIds.toList()),
                )
            for (e in needFromA) relayB.preload(e)

            for (id in pullAtoB.haveIds) {
                val ev = bEvents.first { it.id == id }
                client.publishAndConfirm(ev, setOf(serverA.url.normalizeRelayUrl()))
            }

            // --- Verify convergence ---
            val expectedAll = all.map { it.id }.toSet()
            val onA = relaySnapshotIds(serverA.url, filter)
            val onB = relaySnapshotIds(serverB.url, filter)
            assertEquals(expectedAll, onA, "Relay A should hold every event after sync")
            assertEquals(expectedAll, onB, "Relay B should hold every event after sync")
        }

    @Test
    fun negentropyConvergesInBoundedRoundsOnSmallCorpus() =
        runBlocking {
            val all = makeEvents(200)
            val aEvents = all.subList(0, 150)
            val bEvents = all.subList(50, 200)
            relayA.preload(aEvents)
            relayB.preload(bEvents)

            val filter = Filter(kinds = listOf(1))
            val res = pullSync(serverA.url, bEvents, filter)
            assertNull(res.error)
            assertEquals(50, res.needIds.size, "B should need [0..49]")
            assertEquals(50, res.haveIds.size, "B should have [150..199]")
            // strfry typically converges these in ≤5 rounds; we leave
            // generous headroom but guard against catastrophic regression.
            assertTrue(res.rounds <= 16, "expected ≤16 NEG-MSG rounds, got ${res.rounds}")
        }

    /** Pulls every id matching [filter] from a relay via REQ. */
    private suspend fun relaySnapshotIds(
        wsUrl: String,
        filter: Filter,
    ): Set<HexKey> =
        client
            .fetchAll(
                relay = wsUrl.normalizeRelayUrl(),
                filter = filter,
            ).map { it.id }
            .toSet()
}
