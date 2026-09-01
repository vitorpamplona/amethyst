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

import kotlin.native.concurrent.ObsoleteWorkersApi
import kotlin.native.concurrent.TransferMode
import kotlin.native.concurrent.Worker
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The linux actual is the only [LargeCache] whose thread safety is hand-rolled rather
 * than delegated to a concurrent map, so it gets its own multi-threaded test. The
 * copy-on-write version this replaced would fail every assertion here: its
 * read-copy-write was not a CAS loop, so concurrent writers silently dropped each
 * other's entries.
 *
 * Uses `Worker` rather than coroutines on purpose — a coroutine dispatcher gives no
 * guarantee of genuine parallelism, and parallelism is the whole point.
 */
@OptIn(ObsoleteWorkersApi::class)
class LargeCacheConcurrencyTest {
    private val workerCount = 4
    private val perWorker = 5_000

    private fun <R> inParallel(job: (workerId: Int) -> R): List<R> {
        val workers = List(workerCount) { Worker.start() }
        val futures =
            workers.mapIndexed { id, worker ->
                worker.execute(TransferMode.SAFE, { Pair(job, id) }) { (block, workerId) -> block(workerId) }
            }
        val results = futures.map { it.result }
        workers.forEach { it.requestTermination().result }
        return results
    }

    @Test
    fun concurrentPutsKeepEveryEntry() {
        val cache = LargeCache<Int, Int>()

        inParallel { id ->
            repeat(perWorker) { i -> cache.put(id * perWorker + i, i) }
        }

        assertEquals(workerCount * perWorker, cache.size())
        for (id in 0 until workerCount) {
            assertEquals(0, cache.get(id * perWorker))
            assertEquals(perWorker - 1, cache.get(id * perWorker + perWorker - 1))
        }
    }

    @Test
    fun concurrentGetOrCreateBuildsOneValuePerKey() {
        val cache = LargeCache<Int, String>()

        // Every worker races for the same 500 keys. Whoever wins, all of them must end
        // up holding the identical instance, and the cache must hold exactly 500.
        val seen =
            inParallel { _ ->
                (0 until 500).map { key -> cache.getOrCreate(key) { "v$it" } }
            }

        assertEquals(500, cache.size())
        seen.forEach { perWorkerValues ->
            perWorkerValues.forEachIndexed { key, value ->
                assertEquals(cache.get(key), value, "getOrCreate handed out a value it did not publish")
            }
        }
    }

    @Test
    fun concurrentCreateIfAbsentReportsExactlyOneInsertPerKey() {
        val cache = LargeCache<Int, Int>()

        val insertsPerWorker = inParallel { _ -> (0 until 500).count { key -> cache.createIfAbsent(key) { key } } }

        assertEquals(500, cache.size())
        assertEquals(500, insertsPerWorker.sum(), "exactly one caller per key may report an insert")
    }

    @Test
    fun bulkReadsStayConsistentWhileWritesLand() {
        val cache = LargeCache<Int, Int>()
        repeat(1_000) { cache.put(it, it) }

        // Writers churn the map while readers walk snapshots of it. A reader must never
        // see a torn map, and must never crash on a concurrent modification.
        inParallel { id ->
            if (id % 2 == 0) {
                repeat(perWorker) { i -> cache.put(1_000 + id * perWorker + i, i) }
            } else {
                repeat(200) {
                    val sum = cache.sumOfLong { _, v -> v.toLong() }
                    check(sum >= 0) { "unexpected negative sum $sum" }
                    cache.count { _, _ -> true }
                }
            }
        }

        assertEquals(1_000 + (workerCount / 2) * perWorker, cache.size())
    }
}
