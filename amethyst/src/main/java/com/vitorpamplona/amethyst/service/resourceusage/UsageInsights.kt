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
 * Turns a multi-day summary into at most [MAX_INSIGHTS] actionable
 * recommendations, each mapping to a setting the user can actually change.
 * The numbers alone make the user the analyst; these rules encode the
 * analysis (thresholds informed by the 2026-07-12 ping study) and leave the
 * user only the decision.
 *
 * Candidates that fire are ranked by an estimated-impact [score] — a rough
 * conversion of each signal to "hours of extra radio/CPU activity" — so the
 * cut to [MAX_INSIGHTS] drops the smallest consumers, not whichever rule
 * happened to be declared last. Scores are order-of-magnitude heuristics for
 * RANKING ONLY and are never shown to the user.
 *
 * When the measured battery split says foreground use dominates, a non-action
 * [Target.FOREGROUND_INFO] note leads the list: most drain being screen-on
 * time is the honest headline, and without it users would act on a minor
 * insight expecting savings the settings can't deliver.
 *
 * Unlike [ResourceUsageAlerts] (which detects "something is wrong" and
 * interrupts), insights render passively on the usage screen and use much
 * lower thresholds — "worth knowing", not "pathological".
 */
object UsageInsights {
    /** Each target names the settings surface that can act on the insight. */
    enum class Target {
        /** Informational only — no button. Foreground/screen use dominates the measured drain. */
        FOREGROUND_INFO,
        NOTIFICATION_SETTINGS,
        MEDIA_SETTINGS,
        RELAY_SETTINGS,
        RELAY_CHURN,
        POW_SETTINGS,
        PUSH_PROCESSING,
        PRIVACY_SETTINGS,
    }

    data class Insight(
        val target: Target,
        /** ms for time-based insights, bytes for data, counts for relays/restarts, percent for FOREGROUND_INFO. */
        val value: Long,
        /** Estimated impact in "hours of extra radio/CPU activity" — ranking only, never displayed. */
        val score: Double,
    )

    /**
     * Evaluates a multi-day summary ([UsageSummary.dayCount] normalizes the
     * thresholds, so a 2-day-old install is judged on 2 days, not 7).
     */
    fun evaluate(s: UsageSummary): List<Insight> {
        val days = s.dayCount.coerceAtLeast(1)
        val candidates = mutableListOf<Insight>()

        // Background relay connections, attributed to the always-on service
        // ONLY when its uptime actually covers most of the background window —
        // a service that ran 20 minutes cannot own 30 hours of background
        // connections (long background audio sessions also hold relays).
        val bgConnMs = s.relayConnMsMobileBg + s.relayConnMsWifiBg
        val bgWallMs = (days * ResourceUsageAccountant.DAY_MS - s.foregroundMs).coerceAtLeast(1L)
        val serviceCoversBackground = s.alwaysOnMs * 2 >= bgWallMs
        if (serviceCoversBackground && bgConnMs > days * BG_RELAY_HOURS_PER_DAY * MS_PER_HOUR) {
            candidates += Insight(Target.NOTIFICATION_SETTINGS, bgConnMs, score = hours(s.alwaysOnMs))
        }

        // Cellular media: images/video/previews can be limited to Wi-Fi.
        // Score: scattered mobile downloads pay radio ramp+tail per burst;
        // ~60 MB of feed media ≈ an hour of radio-active time.
        val cellularMediaBytes =
            (s.mobileBytesPerSubsystem[UsageKeys.ROLE_IMAGE] ?: 0L) +
                (s.mobileBytesPerSubsystem[UsageKeys.ROLE_VIDEO] ?: 0L) +
                (s.mobileBytesPerSubsystem[UsageKeys.ROLE_PREVIEW] ?: 0L)
        if (cellularMediaBytes > days * CELLULAR_MEDIA_BYTES_PER_DAY) {
            candidates += Insight(Target.MEDIA_SETTINGS, cellularMediaBytes, score = cellularMediaBytes / (60.0 * 1024 * 1024))
        }

        // Average simultaneous relay connections: every open connection is
        // server-pinged every 30-70s. Score: marginal — the radio is often up
        // anyway; extra relays add pings, duplicates, and handshakes.
        val avgRelays = s.relayConnMs / (days * ResourceUsageAccountant.DAY_MS)
        if (avgRelays > AVG_RELAYS) {
            candidates += Insight(Target.RELAY_SETTINGS, avgRelays, score = hours(s.relayConnMs) / 100.0)
        }

        // Reconnect churn: a flaky relay in the list burns a TCP+TLS
        // handshake plus a radio burst per retry. Score: ~15s of radio each.
        val reconnects = s.relayConnects + s.relayConnectFails
        if (reconnects > days * RECONNECTS_PER_DAY) {
            candidates += Insight(Target.RELAY_CHURN, reconnects, score = reconnects * 15.0 / 3600.0)
        }

        // NIP-13 mining: multi-core CPU flat out — roughly 3x the drain rate
        // of an ordinary radio-active hour.
        if (s.powMs > days * POW_MINUTES_PER_DAY * MS_PER_MINUTE) {
            candidates += Insight(Target.POW_SETTINGS, s.powMs, score = hours(s.powMs) * 3.0)
        }

        // Push processing: wakelock time plus process cold starts (each
        // restart re-parses, re-connects, and pays a radio burst, ~10s each).
        val pushScore = hours(s.wakelockMs) + s.appStarts * 10.0 / 3600.0
        if (s.wakelockMs > days * WAKELOCK_MINUTES_PER_DAY * MS_PER_MINUTE || s.appStarts > days * APP_STARTS_PER_DAY) {
            candidates += Insight(Target.PUSH_PROCESSING, s.wakelockMs, score = pushScore)
        }

        // In-app Tor: circuit crypto + keep-alives are overhead ON TOP of
        // traffic that would exist anyway — count a fraction of its uptime.
        if (s.torMs > days * TOR_HOURS_PER_DAY * MS_PER_HOUR) {
            candidates += Insight(Target.PRIVACY_SETTINGS, s.torMs, score = hours(s.torMs) * 0.3)
        }

        val ranked = candidates.sortedByDescending { it.score }.take(MAX_INSIGHTS)

        // Honesty note: when the measured split says the battery went to
        // screen-on use, say so first — settings mainly reduce the background
        // share, and the user deserves to know how big that share is.
        val fgDominates =
            s.batteryDrainFg >= MIN_DRAIN_SIGNAL_PCT &&
                s.batteryDrainFg >= 3 * s.batteryDrainBg.coerceAtLeast(1L)
        return if (fgDominates) {
            listOf(Insight(Target.FOREGROUND_INFO, s.batteryDrainFg, score = 0.0)) + ranked
        } else {
            ranked
        }
    }

    private fun hours(ms: Long): Double = ms / MS_PER_HOUR.toDouble()

    const val MAX_INSIGHTS = 3
    private const val MS_PER_HOUR = 60L * 60L * 1000L
    private const val MS_PER_MINUTE = 60L * 1000L

    // Per-day thresholds — deliberately well below the alert levels.
    const val BG_RELAY_HOURS_PER_DAY = 3L
    const val CELLULAR_MEDIA_BYTES_PER_DAY = 20L * 1024L * 1024L
    const val AVG_RELAYS = 25L
    const val RECONNECTS_PER_DAY = 500L
    const val POW_MINUTES_PER_DAY = 10L
    const val WAKELOCK_MINUTES_PER_DAY = 5L
    const val APP_STARTS_PER_DAY = 15L
    const val TOR_HOURS_PER_DAY = 4L

    /** Foreground drain below this many percent points is too noisy to call a trend. */
    const val MIN_DRAIN_SIGNAL_PCT = 5L
}
