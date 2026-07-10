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

import com.vitorpamplona.quartz.utils.sha256.sha256
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.pow
import kotlin.time.Duration.Companion.milliseconds
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

    private var cachedRate: Double? = null

    suspend fun hashesPerSecond(dispatcher: CoroutineDispatcher = Dispatchers.Default): Double =
        cachedRate ?: withContext(dispatcher) {
            cachedRate ?: benchmark().also { cachedRate = it }
        }

    fun estimateSeconds(
        difficulty: Int,
        hashesPerSecond: Double,
    ): Double = 2.0.pow(difficulty) / hashesPerSecond.coerceAtLeast(1.0)

    private fun benchmark(): Double {
        val payload = ByteArray(PAYLOAD_BYTES) { (it % 251).toByte() }

        // warm up JIT/caches so the measured window reflects steady state
        repeat(3 * BATCH) { sha256(payload) }

        val mark = TimeSource.Monotonic.markNow()
        var count = 0L
        while (mark.elapsedNow() < BENCH_DURATION) {
            repeat(BATCH) { sha256(payload) }
            count += BATCH
        }
        return count / mark.elapsedNow().toDouble(DurationUnit.SECONDS)
    }
}
