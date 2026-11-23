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
package com.vitorpamplona.quartz.nip85TrustedAssertions.users.index

import com.vitorpamplona.geode.KtorRelay
import com.vitorpamplona.geode.RelayEngine
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.NegentropyLocalIndex
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.publishAndConfirm
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.BasicOkHttpWebSocket
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The trust network sync against a real (local, geode) relay: a cold download, updates that
 * must not re-download what is held, and the weekly full check.
 *
 * The provider signs a whole run with one `created_at`, like Brainstorm. The relay first holds
 * only part of that batch, as if the sync ran while the provider was still publishing.
 */
class TrustNetworkSyncTest {
    private val provider = NostrSignerInternal(KeyPair())
    private val random = Random(11)
    private val batchTime = 1_700_000_000L

    private fun hex() = Hex.encode(random.nextBytes(32))

    private suspend fun card(
        subject: String,
        rank: Int,
        at: Long,
    ): Event = provider.sign(at, USER_ASSERTION_KIND, arrayOf(arrayOf("d", subject), arrayOf("rank", rank.toString())), "")

    private suspend fun deletion(
        at: Long,
        vararg subjects: String,
    ): Event = provider.sign(at, 5, subjects.map { arrayOf("a", "$USER_ASSERTION_KIND:${provider.pubKey}:$it") }.toTypedArray(), "")

    @Test
    fun updatesOnlyFetchWhatChanged() =
        runBlocking {
            val batchTime = TimeUtils.now() - 2 * 3600
            val subjects = List(300) { hex() }
            val batch = subjects.mapIndexed { i, s -> card(s, 1 + i % 90, batchTime) }

            val engine = RelayEngine(url = "ws://127.0.0.1:7773/".normalizeRelayUrl())
            engine.store.batchInsert(batch.take(200))
            val server = KtorRelay(engine, port = 0).start()
            val client = NostrClient(BasicOkHttpWebSocket.Builder { OkHttpClient() })
            val relay = server.url.normalizeRelayUrl()

            // Later events go through the relay's publish path, like a real provider's: writing to
            // the store directly would bypass the relay's negentropy snapshot invalidation.
            suspend fun publish(events: List<Event>) = events.forEach { assertTrue(client.publishAndConfirm(it, setOf(relay)), "relay refused ${it.id}") }

            try {
                // Cold: the 200 cards published so far.
                val cold = client.downloadTrustNetwork(provider.pubKey, relay)
                assertTrue(cold.complete)
                assertEquals(200, cold.index.size)
                assertEquals(batchTime, cold.header.syncCursor)

                // The rest of the batch lands with the same created_at as the cursor: no REQ past
                // the cursor sees it, so the update finds nothing and the weekly reconcile gets it.
                publish(batch.drop(200))
                assertTrue(client.updateTrustNetwork(cold.header, cold.index, cold.ids, relay).unchanged)
                val caughtUp = assertNotNull(client.reconcileTrustNetwork(cold.header, cold.index, cold.ids, relay))
                assertTrue(caughtUp.complete)
                assertEquals(300, caughtUp.index.size)
                assertEquals(100, caughtUp.received, "only the missing part of the batch is fetched")
                assertEquals(300, caughtUp.header.heldAtCursor, "the whole batch shares the cursor's second")
                assertFalse(client.trustNetworkNews(caughtUp.header, relay).any, "the header alone answers 'anything new?'")

                // Nothing new: one REQ ends the update without reading anything else.
                val idle = client.updateTrustNetwork(caughtUp.header, caughtUp.index, caughtUp.ids, relay)
                assertTrue(idle.unchanged, idle.detail)
                assertEquals(0, idle.received)

                // The provider re-ranks one person, removes another with a rank-0 card (its
                // removal marker) and deletes a third.
                val later = batchTime + 10
                publish(
                    listOf(
                        card(subjects[0], 77, later),
                        card(subjects[1], 0, later),
                        deletion(later, subjects[2]),
                    ),
                )
                val changed = client.updateTrustNetwork(idle.header, idle.index, idle.ids, relay)
                assertTrue(changed.complete)
                assertEquals(77, changed.index.rankOf(subjects[0]), "detail=${changed.detail} received=${changed.received} cursor=${changed.header.syncCursor}")
                assertNull(changed.index.rankOf(subjects[1]))
                assertNull(changed.index.rankOf(subjects[2]))
                assertEquals(298, changed.index.size, "one re-ranked, one tombstoned, one deleted")
                // The rank-0 card is a tombstone, and so is the deleted card: the relay applied the
                // kind 5, but only the weekly reconcile learns it no longer serves the card.
                assertEquals(2, changed.ids.tombstones, "the rank-0 card and the deleted one")
                assertEquals(later, changed.header.syncCursor)
                // The update window reaches an hour before the cursor: the earlier batch is in it too.
                assertEquals(300, changed.header.heldAtCursor, "every card held in the update window")
                assertEquals(0, changed.header.heldAfterCursor, "the re-rank and the rank-0 card are at the cursor, none after it")

                // The tombstone keeps the pre-check exact: nothing new again.
                val idleAgain = client.updateTrustNetwork(changed.header, changed.index, changed.ids, relay)
                assertTrue(idleAgain.unchanged)

                // The full check fetches nothing (rank-0 cards included) and drops the deleted card.
                val full = assertNotNull(client.reconcileTrustNetwork(idleAgain.header, idleAgain.index, idleAgain.ids, relay))
                assertTrue(full.complete)
                assertEquals("need 0, gone 1", full.detail)
                assertEquals(1, full.ids.tombstones)
                assertEquals(298, full.index.size)

                // The counts said something changed, but the REQ finds nothing and the counts then
                // agree: the same index comes back, so nothing is rewritten.
                val noNews = client.updateTrustNetwork(full.header, full.index, full.ids, relay, knownNews = TrustNetworkNews(cards = true))
                assertTrue(noNews.unchanged)
                assertSame(full.index, noNews.index)

                // The cursor moves far past the batch, then the relay drops a batch card (a kind 5
                // signed at the cursor's second, so no REQ past the cursor sees it, and the card
                // sits below the hour an update counts). The update leaves it to the weekly
                // reconcile, which drops the card the relay stopped serving: no kind 5 downloaded.
                val far = TimeUtils.now() - 60
                publish(listOf(card(subjects[3], 50, far)))
                val moved = client.updateTrustNetwork(full.header, full.index, full.ids, relay)
                assertEquals(far, moved.header.syncCursor)
                assertEquals(50, moved.index.rankOf(subjects[3]))
                assertEquals(299, moved.header.heldTotal, "298 entries and the tombstone")

                publish(listOf(deletion(far, subjects[4])))
                assertTrue(client.trustNetworkNews(moved.header, relay).any, "the total count sees a card gone below the window")
                val missed = client.updateTrustNetwork(moved.header, moved.index, moved.ids, relay)
                assertNotNull(missed.index.rankOf(subjects[4]), "below the hour an update counts: ${missed.detail}")
                val dropped = assertNotNull(client.reconcileTrustNetwork(missed.header, missed.index, missed.ids, relay))
                assertTrue(dropped.complete)
                assertNull(dropped.index.rankOf(subjects[4]), dropped.detail)
                assertEquals(297, dropped.index.size)
                assertEquals("need 0, gone 1", dropped.detail)
                assertFalse(client.trustNetworkNews(dropped.header, relay).any, "and the counts agree again")

                // The ids file round-trips with the tombstone.
                val decoded = assertNotNull(TrustNetworkCodec.decodeIds(TrustNetworkCodec.encodeIds(full.ids)))
                assertEquals(1, decoded.tombstones)
                assertEquals(full.ids.countSince(later), decoded.countSince(later))
                assertNotEquals(0, decoded.countSince(batchTime))
                assertFalse(decoded.entriesSince(later).isEmpty())
            } finally {
                client.close()
                server.stop(gracePeriodMillis = 200, timeoutMillis = 1_000)
                engine.close()
            }
        }

    /**
     * What a provider publishing new cards costs an update: REQs for what is newer than the
     * cursor, no COUNT and no negentropy. A card no REQ past the cursor can see (one that reached
     * the relay late, at the cursor's own second) is the weekly reconcile's.
     */
    @Test
    fun newCardsArriveByReqAndALateCardWaitsForTheWeeklyCheck() =
        runBlocking {
            val batchTime = TimeUtils.now() - 2 * 3600
            val subjects = List(300) { hex() }
            val batch = subjects.map { card(it, 50, batchTime) }

            val engine = RelayEngine(url = "ws://127.0.0.1:7774/".normalizeRelayUrl())
            engine.store.batchInsert(batch)
            val server = KtorRelay(engine, port = 0).start()
            val client = NostrClient(BasicOkHttpWebSocket.Builder { OkHttpClient() })
            val relay = server.url.normalizeRelayUrl()
            val negOpens = AtomicInteger()
            client.addConnectionListener(
                object : RelayConnectionListener {
                    override fun onSent(
                        relay: IRelayClient,
                        cmdStr: String,
                        cmd: Command,
                        success: Boolean,
                    ) {
                        if (cmdStr.startsWith("[\"NEG-OPEN\"")) negOpens.incrementAndGet()
                    }
                },
            )

            suspend fun publish(events: List<Event>) = events.forEach { assertTrue(client.publishAndConfirm(it, setOf(relay)), "relay refused ${it.id}") }

            try {
                val cold = client.downloadTrustNetwork(provider.pubKey, relay)
                assertEquals(300, cold.index.size)

                // A recompute: five new people and one re-ranked, all newer than the cursor.
                val later = batchTime + 600
                val newcomers = List(5) { hex() }
                publish(newcomers.map { card(it, 40, later) } + card(subjects[0], 90, later))
                negOpens.set(0)
                val updated = client.updateTrustNetwork(cold.header, cold.index, cold.ids, relay)
                assertTrue(updated.complete, updated.detail)
                assertEquals(305, updated.index.size)
                assertEquals(90, updated.index.rankOf(subjects[0]))
                assertEquals(40, updated.index.rankOf(newcomers[0]))
                assertEquals(0, negOpens.get(), "a REQ caught up, no reconcile: ${updated.detail}")
                assertTrue(client.updateTrustNetwork(updated.header, updated.index, updated.ids, relay).unchanged, "the counts agree afterwards")

                // An on-demand provider's next card: one more REQ, still no negentropy.
                negOpens.set(0)
                val single = hex()
                publish(listOf(card(single, 60, later + 300)))
                val next = client.updateTrustNetwork(updated.header, updated.index, updated.ids, relay)
                assertEquals(60, next.index.rankOf(single), next.detail)
                assertEquals(0, negOpens.get(), next.detail)

                // A card signed at the cursor's second that reaches the relay only now: no REQ
                // past the cursor sees it, so it waits for the weekly reconcile.
                val late = hex()
                publish(listOf(card(late, 30, later + 300)))
                assertTrue(client.updateTrustNetwork(next.header, next.index, next.ids, relay).unchanged)
                val weekly = assertNotNull(client.reconcileTrustNetwork(next.header, next.index, next.ids, relay))
                assertEquals(30, weekly.index.rankOf(late), weekly.detail)
                assertEquals("need 1, gone 0", weekly.detail)
            } finally {
                client.close()
                server.stop(gracePeriodMillis = 200, timeoutMillis = 1_000)
                engine.close()
            }
        }

    @Test
    fun theIdsNegentropyIndexMatchesAListIndex() =
        runBlocking {
            val subjects = List(500) { hex() }
            val builder = TrustNetworkBuilder(provider.pubKey)
            subjects.forEachIndexed { i, s -> builder.add(card(s, if (i % 10 == 0) 0 else 1 + i % 90, batchTime + i % 7)) }
            val ids = builder.build().second
            val packed = ids.negentropyIndex()
            val list = NegentropyLocalIndex.of(ids.entriesSince())
            for (window in listOf(Filter(), Filter(since = batchTime + 3), Filter(until = batchTime + 2), Filter(since = batchTime + 2, until = batchTime + 4), Filter(since = batchTime + 99))) {
                assertEquals(list.count(window), packed.count(window), "count of $window")
                assertEquals(list.entriesFor(window).map { it.id }.toSet(), packed.entriesFor(window).map { it.id }.toSet(), "entries of $window")
            }
        }

    companion object {
        const val USER_ASSERTION_KIND = 30382
    }
}
