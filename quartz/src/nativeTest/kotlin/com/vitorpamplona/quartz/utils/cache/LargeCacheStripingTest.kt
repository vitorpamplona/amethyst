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
package com.vitorpamplona.quartz.utils.cache

import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.native.concurrent.ObsoleteWorkersApi
import kotlin.native.concurrent.TransferMode
import kotlin.native.concurrent.Worker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Stresses the one path the rest of the suite never reaches: **many writers inserting into
 * the same bucket at the same time.**
 *
 * A striped table is only sound if the stripe is a function of the bucket. Derive the two
 * from different parts of the hash and the locks stop partitioning the table: two keys can
 * share a bucket while holding different locks, so two writers read the same chain head and
 * both publish over it, and one insert vanishes. The first cut of [StripedHashMap] did
 * exactly that — bucket from the low bits, stripe from bits 16-19 — and nothing here caught
 * it, because every other suite uses small `Int` keys or a constant `hashCode`, which all
 * collapse onto stripe 0 and serialise by accident.
 *
 * So the keys are built on purpose: groups of four sharing their low 16 bits, which puts a
 * group in one bucket at any table size this reaches, while differing in bits 16-19 — the
 * part the broken version striped on. Only [BUCKETS] buckets are used, so chains run to
 * hundreds of nodes and each insert holds its lock for a while, which is what widens the
 * window; with well-spread keys it is a few nanoseconds and nothing is observable.
 *
 * Being straight about what this is: a stress test, not a deterministic reproducer. It did
 * not fail against the broken striping in the runs attempted, which says that race is rare
 * rather than absent — the defect is a plain lost update, provable by reading
 * [StripedHashMap]'s stripe selection against its bucket index, and was fixed on that
 * basis. What this test is worth is being the only coverage of concurrent same-bucket
 * inserts at all, and it would catch a coarser regression.
 */
@OptIn(ObsoleteWorkersApi::class, ExperimentalAtomicApi::class)
class LargeCacheStripingTest {
    /**
     * `hashOf` in [StripedHashMap] spreads with `h xor (h ushr 16)`, so to land on a final
     * hash of `(member shl 16) or bucket` the raw hashCode has to be
     * `(member shl 16) or (bucket xor member)`. The low 16 bits are then exactly `bucket`,
     * shared by all four members of a group at every table size this test reaches.
     *
     * Only [BUCKETS] distinct buckets are used, so chains run to hundreds of nodes. That
     * matters: a writer walks its chain *holding the stripe lock*, so a long chain is what
     * makes the window wide enough for two writers on two different locks to overlap
     * inside the same bucket. With well-spread keys the window is a few nanoseconds and
     * the defect hides.
     */
    private data class GroupedKey(
        val group: Int,
        val member: Int,
    ) {
        override fun hashCode(): Int {
            val bucket = group and (BUCKETS - 1)
            return (member shl 16) or (bucket xor member)
        }
    }

    /** Runs [job] on [WORKERS] threads released together, so they actually contend. */
    private fun inParallel(job: (workerId: Int) -> Unit) {
        val ready = AtomicInt(0)
        val go = AtomicInt(0)
        val workers = List(WORKERS) { Worker.start() }

        val futures =
            workers.mapIndexed { id, worker ->
                worker.execute(TransferMode.SAFE, { Triple(job, id, ready to go) }) { (block, workerId, gates) ->
                    val (readyGate, goGate) = gates
                    readyGate.fetchAndAdd(1)
                    while (goGate.load() == 0) { }
                    block(workerId)
                }
            }

        while (ready.load() < WORKERS) { }
        go.store(1)

        futures.forEach { it.result }
        workers.forEach { it.requestTermination().result }
    }

    @Test
    fun concurrentInsertsAcrossSharedBucketsKeepEveryEntry() {
        repeat(ROUNDS) { round -> insertRound(round) }
    }

    private fun insertRound(round: Int) {
        val cache = LargeCache<GroupedKey, Int>()

        inParallel { worker ->
            for (group in 0 until GROUPS) {
                cache.put(GroupedKey(group, worker), group)
            }
        }

        val total = GROUPS * WORKERS

        val seen = mutableSetOf<GroupedKey>()
        cache.forEach { key, _ -> seen.add(key) }

        assertEquals(total, seen.size, "round $round: iteration lost or duplicated entries in a shared bucket")
        assertEquals(total, cache.size(), "round $round: size() disagrees with what the table holds")

        for (group in 0 until GROUPS) {
            for (member in 0 until WORKERS) {
                assertEquals(
                    group,
                    assertNotNull(
                        cache.get(GroupedKey(group, member)),
                        "round $round: entry ($group, $member) was dropped by a concurrent insert",
                    ),
                )
            }
        }
    }

    @Test
    fun concurrentCreateIfAbsentAcrossSharedBucketsReportsOneInsertEach() {
        repeat(ROUNDS) { createIfAbsentRound() }
    }

    private fun createIfAbsentRound() {
        val cache = LargeCache<GroupedKey, Int>()
        val contended = GROUPS / 4

        // Every worker races for the same keys this time, so a lost update shows up as a
        // duplicate in the chain rather than a missing entry.
        inParallel { _ ->
            for (group in 0 until contended) {
                for (member in 0 until WORKERS) {
                    cache.createIfAbsent(GroupedKey(group, member)) { group }
                }
            }
        }

        val total = contended * WORKERS
        val seen = mutableSetOf<GroupedKey>()
        var visited = 0
        cache.forEach { key, _ ->
            seen.add(key)
            visited++
        }

        assertEquals(total, visited, "a key was inserted twice into the same chain")
        assertEquals(total, seen.size)
        assertEquals(total, cache.size())
    }

    companion object {
        private const val WORKERS = 4

        /** Large enough that the four workers overlap for essentially the whole run. */
        private const val GROUPS = 4_096

        /** Few enough that chains grow long and every insert holds its lock for a while. */
        private const val BUCKETS = 64

        /** Repeated on a fresh table, because only the *insert* path can lose a write. */
        private const val ROUNDS = 20
    }
}
