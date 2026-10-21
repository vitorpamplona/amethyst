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
package com.vitorpamplona.quartz.nip13Pow.miner

import com.vitorpamplona.crypto.sha2.Sha256Midstate
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/** How [PoWMiner] hashes each candidate. */
enum class PoWHashMode {
    /** The whole serialized event through the platform SHA-256 (MessageDigest on JVM/Android). */
    FULL,

    /** Only the blocks from the nonce on, through [Sha256Midstate]. */
    MIDSTATE,

    /** Whichever of the two is faster for this event on this device; see [prefersMidstate]. */
    AUTO,
}

/**
 * Picks [PoWHashMode.MIDSTATE] or [PoWHashMode.FULL] for one serialized event.
 *
 * Neither wins everywhere. Midstate skips every block before the nonce, so it
 * wins big on tag-heavy events (replies, threads), but its pure-Kotlin
 * compression costs more per block than the platform's SHA-256, so it loses
 * when most blocks are content after the nonce (long notes). Where the
 * break-even sits depends on the device's SHA-256 (JIT intrinsic, Conscrypt,
 * ARM SHA instructions), so this times both on the real payload instead of
 * guessing: a few interleaved rounds of [TRIAL_ROUND] each, the first of each
 * mode discarded as warm-up, about 20 ms in all.
 *
 * Below [TRIAL_MIN_POW] the whole job is cheaper than the trial, so it decides
 * by block count alone.
 */
internal fun prefersMidstate(
    bytes: ByteArray,
    nonceStart: Int,
    nonceLength: Int,
    desiredPoW: Int,
): Boolean {
    val midstate = Sha256Midstate(bytes, nonceStart)
    val hashedBlocks = midstate.tail.size / Sha256Midstate.BLOCK
    val fullBlocks = hashedBlocks + midstate.prefixLength / Sha256Midstate.BLOCK

    if (hashedBlocks == fullBlocks) return false
    if (desiredPoW < TRIAL_MIN_POW) return 2 * hashedBlocks <= fullBlocks

    // each trial miner varies only the last nonce byte at an unreachable
    // difficulty, so one run() is exactly one alphabet's worth of candidates.
    val fullMiner =
        PoWMiner(
            MiningBuffer(bytes.copyOf(), nonceStart, nonceStart + nonceLength),
            256,
            searchFrom = nonceStart + nonceLength - 1,
        )
    val midStart = nonceStart - midstate.prefixLength
    val midMiner =
        PoWMiner(
            MiningBuffer(midstate.tail, midStart, midStart + nonceLength),
            256,
            searchFrom = midStart + nonceLength - 1,
            midstate = midstate,
        )

    var fullRate = 0.0
    var midRate = 0.0
    repeat(TRIAL_ROUNDS + 1) { round ->
        val full = runsPerSecond(fullMiner)
        val mid = runsPerSecond(midMiner)
        if (round > 0) {
            fullRate += full
            midRate += mid
        }
    }
    return midRate > fullRate
}

private fun runsPerSecond(miner: PoWMiner): Double {
    val start = TimeSource.Monotonic.markNow()
    var runs = 0
    do {
        miner.run()
        runs++
    } while (start.elapsedNow() < TRIAL_ROUND)
    return runs / start.elapsedNow().inWholeNanoseconds.toDouble()
}

private const val TRIAL_ROUNDS = 4
private val TRIAL_ROUND = 2.milliseconds

// 2^16 expected hashes: around the trial's own cost on a phone.
internal const val TRIAL_MIN_POW = 16
