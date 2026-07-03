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
package com.vitorpamplona.quartz.nip01Core.relay

import com.vitorpamplona.geode.fixtures.SyntheticEvents
import com.vitorpamplona.geode.testing.RelayClientTest
import com.vitorpamplona.geode.testing.preload
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.negentropyReconcile
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.negentropyReconcileIds
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.store.IdAndTime
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end tests for the pure-reconcile API ([negentropyReconcile] /
 * [negentropyReconcileIds]): the id diff comes back in both directions —
 * `needIds` (relay has, we lack) and `haveIds` (we have, relay lacks) — and
 * nothing is downloaded or uploaded.
 */
class NostrClientNegentropyReconcileTest : RelayClientTest() {
    private fun events(range: IntRange): List<Event> = range.map { SyntheticEvents.fakeEvent(idSeed = it, kind = 1, createdAt = it.toLong()) }

    private fun List<Event>.entries(): List<IdAndTime> = map { IdAndTime(it.createdAt, it.id) }

    private fun List<Event>.ids(): Set<HexKey> = map { it.id }.toSet()

    @Test
    fun emptyLocalSetNeedsEverythingHasNothing() =
        runBlocking {
            val onRelay = events(1..20)
            defaultRelay.preload(onRelay)

            val diff =
                withTimeout(20_000) {
                    client.negentropyReconcileIds(
                        relay = defaultRelayUrl,
                        filter = Filter(kinds = listOf(1)),
                    )
                }

            assertEquals(onRelay.ids(), diff.needIds.toSet(), "need = everything on the relay")
            assertTrue(diff.haveIds.isEmpty(), "nothing to publish")
        }

    @Test
    fun partialOverlapSplitsBothWays() =
        runBlocking {
            // relay holds A ∪ B; local set is B ∪ C.
            val a = events(1..10) // relay-only → expected needIds
            val b = events(101..110) // shared → in neither diff
            val c = events(201..205) // local-only → expected haveIds
            defaultRelay.preload(a + b)

            val diff =
                withTimeout(20_000) {
                    client.negentropyReconcileIds(
                        relay = defaultRelayUrl,
                        filter = Filter(kinds = listOf(1)),
                        localEntries = (b + c).entries(),
                    )
                }

            assertEquals(a.ids(), diff.needIds.toSet(), "need = relay-only events")
            assertEquals(c.ids(), diff.haveIds.toSet(), "have = local-only events")
        }

    @Test
    fun identicalSetsProduceEmptyDiff() =
        runBlocking {
            val shared = events(1..15)
            defaultRelay.preload(shared)

            val diff =
                withTimeout(20_000) {
                    client.negentropyReconcileIds(
                        relay = defaultRelayUrl,
                        filter = Filter(kinds = listOf(1)),
                        localEntries = shared.entries(),
                    )
                }

            assertTrue(diff.needIds.isEmpty(), "in sync: nothing to download")
            assertTrue(diff.haveIds.isEmpty(), "in sync: nothing to publish")
        }

    @Test
    fun streamingCallbacksReportCountsAndBatches() =
        runBlocking {
            val onRelay = events(1..30)
            val localOnly = events(301..312)
            defaultRelay.preload(onRelay)

            val needBatches = mutableListOf<List<HexKey>>()
            val haveBatches = mutableListOf<List<HexKey>>()
            val result =
                withTimeout(20_000) {
                    client.negentropyReconcile(
                        relay = defaultRelayUrl,
                        filter = Filter(kinds = listOf(1)),
                        localEntries = localOnly.entries(),
                        batchSize = 8,
                        onHaveIds = { haveBatches.add(it) },
                        onNeedIds = { needBatches.add(it) },
                    )
                }

            assertEquals(30, result.needCount)
            assertEquals(12, result.haveCount)
            assertEquals(onRelay.ids(), needBatches.flatten().toSet())
            assertEquals(localOnly.ids(), haveBatches.flatten().toSet())
            assertTrue(needBatches.all { it.size <= 8 }, "need ids arrive in <= batchSize chunks")
            assertTrue(haveBatches.all { it.size <= 8 }, "have ids arrive in <= batchSize chunks")
        }

    @Test
    fun sinceUntilBoundsTheDiffAndLocalEntriesOutsideWindowAreIgnored() =
        runBlocking {
            // relay: createdAt 1..20. Window [5..15]. Local set holds 8..12 plus
            // entries OUTSIDE the window (createdAt 950..960) that must not be
            // reported as haves because the filter excludes them on both sides.
            val onRelay = events(1..20)
            val localShared = onRelay.filter { it.createdAt in 8L..12L }
            val localOutside = events(950..960)
            defaultRelay.preload(onRelay)

            val diff =
                withTimeout(20_000) {
                    client.negentropyReconcileIds(
                        relay = defaultRelayUrl,
                        filter = Filter(kinds = listOf(1), since = 5, until = 15),
                        localEntries = (localShared + localOutside).entries(),
                    )
                }

            val expectedNeed = onRelay.filter { it.createdAt in 5L..15L && it.createdAt !in 8L..12L }.ids()
            assertEquals(expectedNeed, diff.needIds.toSet(), "need = window events we lack")
            assertTrue(diff.haveIds.isEmpty(), "out-of-window local entries are not haves")
        }
}
