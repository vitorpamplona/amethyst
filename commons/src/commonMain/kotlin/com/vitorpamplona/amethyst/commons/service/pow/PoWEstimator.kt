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

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.EventHasherSerializer
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip13Pow.miner.PoWMiner
import com.vitorpamplona.quartz.nip13Pow.miner.Sha256Midstate
import com.vitorpamplona.quartz.nip13Pow.tags.PoWTag
import com.vitorpamplona.quartz.utils.sha256.sha256Into
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.pow
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit
import kotlin.time.TimeSource

/**
 * How much of one event the miner hashes per candidate, in 64-byte SHA-256
 * blocks: [fullBlocks] for the whole serialized event, [hashedBlocks] for the
 * part from the nonce's block on, which is all the midstate hasher redoes.
 */
@Immutable
data class PoWEventShape(
    val fullBlocks: Int,
    val hashedBlocks: Int,
) {
    companion object {
        /**
         * A ~300-byte short note: the nonce lands in the second block, so four of
         * five blocks follow it. What the settings picker and the composer menu
         * estimate with, since no event exists yet there.
         */
        val SHORT_NOTE = PoWEventShape(fullBlocks = 5, hashedBlocks = 4)

        fun <T : Event> of(
            template: EventTemplate<T>,
            pubKey: HexKey,
            difficulty: Int,
        ): PoWEventShape {
            // the miner's first nonce is 5 chars; a few more rarely move a block
            val bytes =
                EventHasherSerializer.fastMakeJsonForId(
                    pubKey,
                    template.createdAt,
                    template.kind,
                    template.tags + PoWTag.assemble("00000", difficulty),
                    template.content,
                )
            val fullBlocks = blocksFor(bytes.size)
            return PoWEventShape(fullBlocks, fullBlocks - nonceStart(bytes) / Sha256Midstate.BLOCK)
        }

        // found by structure, not by the nonce's value: zeros also open the ids of
        // mined events an e tag may cite. Unescaped quotes only occur between JSON
        // strings, so this sequence is the nonce tag itself, the last tag.
        private val NONCE_TAG = "[\"${PoWTag.TAG_NAME}\",\"".encodeToByteArray()

        private fun nonceStart(bytes: ByteArray): Int {
            for (i in bytes.size - NONCE_TAG.size downTo 0) {
                if (NONCE_TAG.indices.all { bytes[i + it] == NONCE_TAG[it] }) return i + NONCE_TAG.size
            }
            error("serialized event has no nonce tag")
        }

        /** SHA-256 blocks for a [length]-byte message, padding included. */
        internal fun blocksFor(length: Int) = (length + 9 + Sha256Midstate.BLOCK - 1) / Sha256Midstate.BLOCK
    }
}

/**
 * One worker's measured cost per candidate, as `base + perBlock × blocks`
 * nanoseconds for each hasher. The full hasher's base is the per-call cost of
 * the platform SHA-256 (large through Conscrypt's JNI on Android); the
 * midstate hasher has almost none but a dearer block.
 */
@Immutable
data class PoWHashCosts(
    val fullBaseNanos: Double,
    val fullPerBlockNanos: Double,
    val midstateBaseNanos: Double,
    val midstatePerBlockNanos: Double,
) {
    fun fullNanos(shape: PoWEventShape) = fullBaseNanos + fullPerBlockNanos * shape.fullBlocks

    fun midstateNanos(shape: PoWEventShape) = midstateBaseNanos + midstatePerBlockNanos * shape.hashedBlocks

    /** PoWMiner's AUTO mode times both on the event and keeps the faster. */
    fun nanosPerHash(shape: PoWEventShape) = minOf(fullNanos(shape), midstateNanos(shape))
}

/** Aggregate rate of a set of concurrent workers, each with its own measured costs. */
@Immutable
class PoWHashRate(
    val workers: List<PoWHashCosts>,
) {
    fun hashesPerSecond(shape: PoWEventShape = PoWEventShape.SHORT_NOTE): Double = workers.sumOf { 1e9 / it.nanosPerHash(shape).coerceAtLeast(1.0) }
}

/**
 * Rough on-device estimate of how long mining a given NIP-13 difficulty takes,
 * so the settings picker can say "≈ 45 s per post" instead of leaving the user
 * to guess what "24 bits" means for their phone.
 *
 * The miner hashes each event with whichever of its two hashers is faster for
 * that event (the whole JSON through the platform SHA-256, or only the blocks
 * from the nonce on through [Sha256Midstate]), so the rate depends on the
 * event's shape: a reply with many tags mines several times faster than a long
 * note. The benchmark therefore measures both hashers at two message sizes
 * (~a quarter second in all, on the first call; cached after) and fits a
 * per-hash cost line to each, which [PoWHashRate.hashesPerSecond] evaluates
 * for a given [PoWEventShape].
 *
 * Expected attempts for `d` leading zero bits are `2^d` (geometric mean), so
 * the estimate is `2^d / rate` — right on average, but any individual post can
 * be much luckier or unluckier.
 */
object PoWEstimator {
    private const val SMALL_BLOCKS = 1
    private const val LARGE_BLOCKS = 9
    private const val BATCH = 500

    // warm-up plus four measurements share the old single-payload budget
    private val WARM_UP_DURATION = 50.milliseconds
    private val MEASURE_DURATION = 50.milliseconds

    // cached per worker count; guarded by benchLock (single-flight so
    // concurrent first callers — e.g. the settings screen recomposing while
    // the composer chip opens — share one ~250 ms benchmark instead of each
    // burning the cores).
    private val cachedRates = mutableMapOf<Int, PoWHashRate>()
    private val benchLock = Mutex()

    /** Rate for a [PoWEventShape.SHORT_NOTE]-sized event, for screens with no event at hand. */
    suspend fun hashesPerSecond(dispatcher: CoroutineDispatcher = PoWMiner.MiningDispatcher): Double = hashRate(1, dispatcher).hashesPerSecond()

    /** [hashRate] for a [PoWEventShape.SHORT_NOTE]-sized event. */
    suspend fun hashesPerSecond(
        workers: Int,
        dispatcher: CoroutineDispatcher = PoWMiner.MiningDispatcher,
    ): Double = hashRate(workers, dispatcher).hashesPerSecond()

    /**
     * Aggregate hash rate with [workers] concurrent miners — what
     * [com.vitorpamplona.quartz.nip13Pow.miner.PoWMiner.mine] achieves when
     * racing that many workers. Each worker runs its own ~250 ms benchmark
     * concurrently and keeps its own costs, so contention between cores is
     * priced in.
     */
    suspend fun hashRate(
        workers: Int,
        dispatcher: CoroutineDispatcher = PoWMiner.MiningDispatcher,
    ): PoWHashRate {
        val count = workers.coerceAtLeast(1)
        benchLock.withLock {
            cachedRates[count]?.let { return it }
            return withContext(dispatcher) {
                val rate =
                    if (count == 1) {
                        PoWHashRate(listOf(benchmark()))
                    } else {
                        PoWHashRate(coroutineScope { List(count) { async { benchmark() } }.awaitAll() })
                    }
                cachedRates[count] = rate
                rate
            }
        }
    }

    fun estimateSeconds(
        difficulty: Int,
        hashesPerSecond: Double,
    ): Double = 2.0.pow(difficulty) / hashesPerSecond.coerceAtLeast(1.0)

    /** Message length that pads out to exactly [blocks] SHA-256 blocks. */
    private fun payloadOf(blocks: Int) = ByteArray(blocks * Sha256Midstate.BLOCK - 9) { (it % 251).toByte() }

    private fun benchmark(): PoWHashCosts {
        // same allocation-free hashing the miner's hot loop uses
        val out = ByteArray(32)
        val small = payloadOf(SMALL_BLOCKS)
        val large = payloadOf(LARGE_BLOCKS)
        // changesFrom = 0: every block is redone per candidate, like the tail
        val smallMid = Sha256Midstate(small, 0)
        val largeMid = Sha256Midstate(large, 0)

        // the JIT compiles the pure-Kotlin compression only once it is hot; timed
        // cold, midstate would look ~35% slower than it mines.
        val warmUp = TimeSource.Monotonic.markNow()
        while (warmUp.elapsedNow() < WARM_UP_DURATION) {
            repeat(BATCH) { sha256Into(out, small, small.size) }
            repeat(BATCH / LARGE_BLOCKS) { sha256Into(out, large, large.size) }
            repeat(BATCH) { smallMid.hasLeadingZeroBits(256) }
            repeat(BATCH / LARGE_BLOCKS) { largeMid.hasLeadingZeroBits(256) }
        }

        val fullSmall = nanosPerHash { sha256Into(out, small, small.size) }
        val fullLarge = nanosPerHash { sha256Into(out, large, large.size) }
        val midSmall = nanosPerHash { smallMid.hasLeadingZeroBits(256) }
        val midLarge = nanosPerHash { largeMid.hasLeadingZeroBits(256) }

        // a line through the two sizes: per-call base + per-block cost
        val fullPerBlock = ((fullLarge - fullSmall) / (LARGE_BLOCKS - SMALL_BLOCKS)).coerceAtLeast(MIN_BLOCK_NANOS)
        val midPerBlock = ((midLarge - midSmall) / (LARGE_BLOCKS - SMALL_BLOCKS)).coerceAtLeast(MIN_BLOCK_NANOS)
        return PoWHashCosts(
            fullBaseNanos = (fullSmall - fullPerBlock * SMALL_BLOCKS).coerceAtLeast(0.0),
            fullPerBlockNanos = fullPerBlock,
            midstateBaseNanos = (midSmall - midPerBlock * SMALL_BLOCKS).coerceAtLeast(0.0),
            midstatePerBlockNanos = midPerBlock,
        )
    }

    private inline fun nanosPerHash(hash: () -> Unit): Double {
        val mark = TimeSource.Monotonic.markNow()
        var count = 0L
        while (mark.elapsedNow() < MEASURE_DURATION) {
            repeat(BATCH) { hash() }
            count += BATCH
        }
        return mark.elapsedNow().toDouble(DurationUnit.NANOSECONDS) / count
    }

    // a timer hiccup between the two sizes must not yield a free (or negative) block
    private const val MIN_BLOCK_NANOS = 1.0
}
