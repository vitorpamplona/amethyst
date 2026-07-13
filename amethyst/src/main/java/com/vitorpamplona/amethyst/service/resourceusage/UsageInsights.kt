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

/**
 * Turns a week of counters into at most [MAX_INSIGHTS] actionable
 * recommendations, each mapping to a setting the user can actually change.
 * The numbers alone make the user the analyst; these rules encode the
 * analysis (thresholds informed by the 2026-07-12 ping study) and leave the
 * user only the decision.
 *
 * Rules are ordered by typical battery impact; unlike [ResourceUsageAlerts]
 * (which detects "something is wrong" and interrupts), insights render
 * passively on the usage screen and use much lower thresholds — "worth
 * knowing", not "pathological".
 */
object UsageInsights {
    /** Each target names the settings surface that can act on the insight. */
    enum class Target {
        NOTIFICATION_SETTINGS,
        MEDIA_SETTINGS,
        RELAY_SETTINGS,
        PRIVACY_SETTINGS,
    }

    data class Insight(
        val target: Target,
        /** ms for time-based insights, bytes for data, count for relays. */
        val value: Long,
    )

    /**
     * Evaluates a multi-day summary ([UsageSummary.dayCount] normalizes the
     * thresholds, so a 2-day-old install is judged on 2 days, not 7).
     */
    fun evaluate(s: UsageSummary): List<Insight> {
        val days = s.dayCount.coerceAtLeast(1)
        val insights = mutableListOf<Insight>()

        // Background relay connections dominate drain while the always-on
        // service is in use — the one consumer with a dedicated off switch.
        val bgConnMs = s.relayConnMsMobileBg + s.relayConnMsWifiBg
        if (s.alwaysOnMs > 0 && bgConnMs > days * BG_RELAY_HOURS_PER_DAY * MS_PER_HOUR) {
            insights += Insight(Target.NOTIFICATION_SETTINGS, bgConnMs)
        }

        // Cellular media: images/video/previews can be limited to Wi-Fi.
        val cellularMediaBytes =
            (s.mobileBytesPerSubsystem[UsageKeys.ROLE_IMAGE] ?: 0L) +
                (s.mobileBytesPerSubsystem[UsageKeys.ROLE_VIDEO] ?: 0L) +
                (s.mobileBytesPerSubsystem[UsageKeys.ROLE_PREVIEW] ?: 0L)
        if (cellularMediaBytes > days * CELLULAR_MEDIA_BYTES_PER_DAY) {
            insights += Insight(Target.MEDIA_SETTINGS, cellularMediaBytes)
        }

        // Average simultaneous relay connections across the whole period:
        // every open connection is server-pinged every 30-70s, so the radio
        // never sleeps while they're up. Fewer relays = less radio time.
        val avgRelays = s.relayConnMs / (days * ResourceUsageAccountant.DAY_MS)
        if (avgRelays > AVG_RELAYS) {
            insights += Insight(Target.RELAY_SETTINGS, avgRelays)
        }

        // In-app Tor pays circuit crypto + keep-alives for as long as it runs.
        if (s.torMs > days * TOR_HOURS_PER_DAY * MS_PER_HOUR) {
            insights += Insight(Target.PRIVACY_SETTINGS, s.torMs)
        }

        return insights.take(MAX_INSIGHTS)
    }

    const val MAX_INSIGHTS = 3
    private const val MS_PER_HOUR = 60L * 60L * 1000L

    // Per-day thresholds — deliberately well below the alert levels.
    const val BG_RELAY_HOURS_PER_DAY = 3L
    const val CELLULAR_MEDIA_BYTES_PER_DAY = 20L * 1024L * 1024L
    const val AVG_RELAYS = 25L
    const val TOR_HOURS_PER_DAY = 4L
}
