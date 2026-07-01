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
package com.vitorpamplona.quartz.podcasts

/**
 * Accumulates *played* time for Podcasting-2.0 per-minute "streaming" payments and tells the caller
 * how many whole minutes are now billable.
 *
 * The safety contract — a listener must never be charged for time they did not actually listen to —
 * lives here, kept pure so it can be unit-tested away from the audio stack:
 *
 * - Only time the caller reports via [accrue] counts. The caller feeds it elapsed time *only while
 *   audio is genuinely playing*; paused/stopped/backgrounded time is simply never accrued.
 * - Only **whole** minutes are ever billed. A partial minute stays pending and is dropped on the
 *   floor if the session ends (just discard the instance) — so an interrupted minute is free, never
 *   rounded up.
 */
class PodcastStreamingAccrual(
    private val minuteMillis: Long = 60_000L,
) {
    private var pendingMillis = 0L

    /**
     * Adds [playingMillis] of genuinely-played time and returns the number of whole minutes that
     * just became billable (already subtracted from the pending remainder). Non-positive input is
     * ignored and returns 0.
     */
    fun accrue(playingMillis: Long): Int {
        if (playingMillis <= 0L) return 0
        pendingMillis += playingMillis
        val minutes = (pendingMillis / minuteMillis).toInt()
        pendingMillis -= minutes * minuteMillis
        return minutes
    }

    /** Played time accrued toward the next minute but not yet billed. Dropped if the session ends. */
    fun pendingMillis(): Long = pendingMillis
}
