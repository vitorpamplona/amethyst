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
package com.vitorpamplona.amethyst.service.resourceusage

import android.os.Process

/**
 * Samples whole-process CPU time (user+system, [Process.getElapsedCpuTime])
 * into the ledger as day deltas. This is the honest aggregate cost of
 * everything that runs on the CPU — event parsing, signature verification,
 * coroutines, recomposition — without per-subsystem guesswork. Sampled from
 * the accountant's pre-flush hook, so it costs one syscall per flush and
 * nothing while idle. Per-process monotonic: a fresh process simply starts a
 * fresh baseline.
 *
 * Each delta is booked twice: to [UsageKeys.CPU_MS] and to the
 * [UsageKeys.CPU_FG_MS] / [UsageKeys.CPU_BG_MS] pair, by the visibility at
 * sample time. The split is what makes the number actionable — CPU burned with
 * the screen off is work nobody is waiting for — while the undimensioned total
 * keeps reports from before the split comparable. Whole intervals are
 * attributed to the visibility observed at their end, which is accurate at the
 * flush cadence (30s) against the timescale that matters here (hours of
 * background).
 *
 * [ThreadCpuSampler] breaks the same total down by subsystem; the two are
 * independent (separate baselines, separate `/proc` access) so that losing the
 * per-thread breakdown on a restricted kernel does not cost us the total.
 */
class ProcessCpuSampler(
    private val accountant: ResourceUsageAccountant,
    private val isForeground: () -> Boolean,
    private val cpuMs: () -> Long = { Process.getElapsedCpuTime() },
) {
    private var lastSampleMs = cpuMs()

    fun register() {
        accountant.addPreFlushHook(::sample)
    }

    @Synchronized
    fun sample() {
        val now = cpuMs()
        val delta = now - lastSampleMs
        lastSampleMs = now
        if (delta <= 0) return
        accountant.add(UsageKeys.CPU_MS, delta)
        accountant.add(if (isForeground()) UsageKeys.CPU_FG_MS else UsageKeys.CPU_BG_MS, delta)
    }
}
