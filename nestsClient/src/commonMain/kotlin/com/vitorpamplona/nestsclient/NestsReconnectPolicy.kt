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
package com.vitorpamplona.nestsclient

/**
 * Exponential-backoff settings for the reconnect path that
 * [connectNestsListener] / [connectNestsSpeaker] consult when the
 * underlying WebTransport session drops mid-room. Mirrors the
 * `delay: { initial, multiplier, max }` shape kixelated/moq's JS
 * reference uses.
 *
 * Defaults match the JS reference (1 s → 30 s ceiling, doubling).
 * `maxAttempts` defaults to unbounded — a long-running room should
 * keep trying as long as the user hasn't left the screen; the
 * Composable's `DisposableEffect.onDispose` is the cancel signal.
 */
data class NestsReconnectPolicy(
    val initialDelayMs: Long = 1_000,
    val multiplier: Double = 2.0,
    val maxDelayMs: Long = 30_000,
    val maxAttempts: Int = Int.MAX_VALUE,
) {
    init {
        require(initialDelayMs > 0) { "initialDelayMs must be > 0, got $initialDelayMs" }
        require(multiplier > 1.0) { "multiplier must be > 1.0 to grow, got $multiplier" }
        require(maxDelayMs >= initialDelayMs) {
            "maxDelayMs ($maxDelayMs) must be >= initialDelayMs ($initialDelayMs)"
        }
        require(maxAttempts >= 1) { "maxAttempts must be >= 1, got $maxAttempts" }
    }

    /**
     * Delay for the [attempt]-th retry (1-indexed). attempt=1 →
     * [initialDelayMs]; subsequent attempts multiply by [multiplier]
     * and clamp at [maxDelayMs]. attempt < 1 returns 0.
     */
    fun delayForAttempt(attempt: Int): Long {
        if (attempt < 1) return 0L
        // Compute attempt-1 doublings so attempt=1 returns initial.
        var d = initialDelayMs.toDouble()
        repeat(attempt - 1) { d *= multiplier }
        val clamped = d.coerceAtMost(maxDelayMs.toDouble())
        return clamped.toLong()
    }

    /** True when [attempt] has hit [maxAttempts] (the next retry is forbidden). */
    fun isExhausted(attempt: Int): Boolean = attempt >= maxAttempts

    companion object {
        /** Off-switch for callers that want first-shot-or-fail (tests, single-room demos). */
        val NoRetry = NestsReconnectPolicy(maxAttempts = 1)
    }
}
