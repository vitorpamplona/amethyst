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

import android.os.SystemClock

/**
 * Splits wall time into "the device was running" and "the device was suspended",
 * by differencing the two clocks Android exposes for exactly this:
 * [SystemClock.elapsedRealtime] counts time in deep sleep,
 * [SystemClock.uptimeMillis] does not.
 *
 * **Why this matters more than any CPU counter.** With the screen off Android
 * suspends the SoC. Background work therefore costs a wake transition plus a
 * minimum residency before the device can go back down — and that fixed cost,
 * multiplied by how often something wakes it, dominates the energy. The same
 * [UsageKeys.CPU_BG_MS] spread over thousands of wake-ups rather than dozens is
 * the difference between a healthy app and a flat battery by lunchtime, and the
 * two are indistinguishable in a CPU figure. The ping study reached the same
 * conclusion from the other direction: the dominant energy proxy was
 * connection-time, not computation.
 *
 * Device-wide, not app-isolated, exactly like [BatteryDrainSampler] — it is a
 * ground truth to correlate the app's own counters against across many reports,
 * not an accusation. "This device barely slept while Amethyst was backgrounded"
 * is the finding; who kept it awake is the next question, not this one's answer.
 *
 * Piggybacks on the accountant's pre-flush hook: two clock reads per flush,
 * nothing scheduled, and nothing that could itself keep the device awake. The
 * accountant's own debounce is a plain `delay`, which does not wake a sleeping
 * device — so a long suspend simply means no flush happened, and the interval
 * that eventually closes accounts for the whole gap with its sleep share intact.
 */
class DeviceSleepSampler(
    private val accountant: ResourceUsageAccountant,
    private val isForeground: () -> Boolean,
    private val elapsedRealtimeMs: () -> Long = { SystemClock.elapsedRealtime() },
    private val uptimeMs: () -> Long = { SystemClock.uptimeMillis() },
) {
    private var lastElapsedMs = 0L
    private var lastUptimeMs = 0L
    private var primed = false

    fun register() {
        sample()
        accountant.addPreFlushHook(::sample)
    }

    @Synchronized
    fun sample() {
        // Read as close together as possible: the gap between them is measured
        // as sleep, and a scheduling hiccup between the two reads would be
        // attributed as such.
        val elapsedNow = elapsedRealtimeMs()
        val uptimeNow = uptimeMs()

        val wallMs = elapsedNow - lastElapsedMs
        val awakeMs = uptimeNow - lastUptimeMs
        lastElapsedMs = elapsedNow
        lastUptimeMs = uptimeNow

        if (!primed) {
            primed = true
            return
        }
        if (wallMs <= 0) return

        val visibility = if (isForeground()) UsageKeys.FG else UsageKeys.BG
        // uptime can never outrun elapsed time, but clamp rather than trust it:
        // a bogus negative would be booked as sleep the device never had.
        val awake = awakeMs.coerceIn(0, wallMs)
        if (awake > 0) accountant.add(UsageKeys.deviceAwakeMs(visibility), awake)
        val asleep = wallMs - awake
        if (asleep > 0) accountant.add(UsageKeys.deviceSleepMs(visibility), asleep)
    }
}
