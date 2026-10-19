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
package com.vitorpamplona.amethyst.commons.service.pow

import com.vitorpamplona.quartz.nip13Pow.miner.PoWMiner
import com.vitorpamplona.quartz.utils.sha256.sha256Into
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.pow
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.TimeSource

/**
 * Rough on-device estimate of how long mining a given NIP-13 difficulty takes,
 * so the settings picker can say "≈ 45 s per post" instead of leaving the user
 * to guess what "24 bits" means for their phone.
 *
 * The benchmark hashes a short-note-sized payload for ~a quarter second on the
 * first call and caches the rate. Expected attempts for `d` leading zero bits
 * are `2^d` (geometric mean), so the estimate is `2^d / rate` — right on
 * average, but any individual post can be much luckier or unluckier.
 */
object PoWEstimator {
    // representative serialized-event size for a short note; the miner hashes
    // the full JSON on every attempt, so payload size sets the rate.
    private const val PAYLOAD_BYTES = 300
    private const val BATCH = 2_000
    private val BENCH_DURATION = 250.milliseconds

    // Long enough to cover every worker's flush (one per ~6,000 hashes, under a millisecond) many
    // times over and to smooth a mining pass's restamp.
    private val LIVE_WINDOW = 1.seconds

    // cached per worker count; guarded by benchLock (single-flight so
    // concurrent first callers — e.g. the settings screen recomposing while
    // the composer chip opens — share one ~250 ms benchmark instead of each
    // burning the cores).
    private val cachedRates = mutableMapOf<Int, Double>()
    private val benchLock = Mutex()

    suspend fun hashesPerSecond(dispatcher: CoroutineDispatcher = PoWMiner.MiningDispatcher): Double = hashesPerSecond(1, dispatcher)

    /**
     * Aggregate hash rate with [workers] concurrent miners — what
     * [com.vitorpamplona.quartz.nip13Pow.miner.PoWMiner.mine] achieves when
     * racing that many workers. Each worker runs its own ~250 ms benchmark
     * loop concurrently and the rates are summed, so contention between
     * cores is priced in.
     */
    suspend fun hashesPerSecond(
        workers: Int,
        dispatcher: CoroutineDispatcher = PoWMiner.MiningDispatcher,
    ): Double {
        val count = workers.coerceAtLeast(1)
        benchLock.withLock {
            cachedRates[count]?.let { return it }
            return withContext(dispatcher) {
                val rate =
                    if (count == 1) {
                        benchmark()
                    } else {
                        coroutineScope { List(count) { async { benchmark() } }.awaitAll().sum() }
                    }
                cachedRates[count] = rate
                rate
            }
        }
    }

    /**
     * The rate the miners running right now reach: [PoWMiner.hashesTried] sampled [LIVE_WINDOW]
     * apart. Null when nothing is mining. A benchmark run then would compete with the miners for
     * the same cores and read low (32% low against 10 desktop workers, measured), and that low
     * rate would stay cached; the miners' own count costs nothing and is exact.
     */
    suspend fun liveHashesPerSecond(): Double? {
        if (!PoWMiner.isMining()) return null
        val start = PoWMiner.hashesTried()
        val mark = TimeSource.Monotonic.markNow()
        delay(LIVE_WINDOW)
        val tried = PoWMiner.hashesTried() - start
        if (tried <= 0L || !PoWMiner.isMining()) return null
        return tried / mark.elapsedNow().toDouble(DurationUnit.SECONDS)
    }

    /**
     * The rate an estimate should use: the miners' live rate while one runs, else the [workers]
     * benchmark, kept only when nothing mined while it ran. A post usually starts mining just as
     * its banner or notification asks, so a benchmark the miners overlapped is dropped for the
     * live rate.
     */
    suspend fun currentHashesPerSecond(
        workers: Int,
        dispatcher: CoroutineDispatcher = PoWMiner.MiningDispatcher,
    ): Double {
        liveHashesPerSecond()?.let { return it }
        val before = PoWMiner.hashesTried()
        val benchmarked = hashesPerSecond(workers, dispatcher)
        if (PoWMiner.hashesTried() == before) return benchmarked
        benchLock.withLock { cachedRates.remove(workers.coerceAtLeast(1)) }
        return liveHashesPerSecond() ?: benchmarked
    }

    fun estimateSeconds(
        difficulty: Int,
        hashesPerSecond: Double,
    ): Double = 2.0.pow(difficulty) / hashesPerSecond.coerceAtLeast(1.0)

    private fun benchmark(): Double {
        val payload = ByteArray(PAYLOAD_BYTES) { (it % 251).toByte() }
        // same allocation-free hashing the miner's hot loop uses
        val out = ByteArray(32)

        // warm up JIT/caches so the measured window reflects steady state
        repeat(3 * BATCH) { sha256Into(out, payload, payload.size) }

        val mark = TimeSource.Monotonic.markNow()
        var count = 0L
        while (mark.elapsedNow() < BENCH_DURATION) {
            repeat(BATCH) { sha256Into(out, payload, payload.size) }
            count += BATCH
        }
        return count / mark.elapsedNow().toDouble(DurationUnit.SECONDS)
    }
}
