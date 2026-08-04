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
package com.vitorpamplona.quartz.nip01Core.relay.client.accessories

import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.concurrent.ConcurrentMap
import kotlin.concurrent.Volatile

/**
 * How far a paged walk has got, measured on the time axis — the only axis
 * whose end is known in advance.
 *
 * A paged fetch ([fetchAllPages]) has no event denominator: how many events
 * exist is exactly what it is finding out, so every count-based percentage
 * degenerates to `downloaded/downloaded = 100%`. The time axis has both ends
 * before the first request — the filter's `until` (or now) down to its
 * `since` (or [SyncBands.PLAUSIBLE_FLOOR]) — with each page's new `until`
 * reporting the exact position between them. It needs no COUNT support.
 *
 * The estimate assumes events are spread evenly over time, which they are
 * not — so it errs pessimistic on the tail, and is a bound, not a promise.
 *
 * One instance can serve many concurrent walks: keys are `"group|walk"`, and
 * the group prefix scopes [fraction], [reached] and [etaMs] so two groups
 * never report each other's numbers.
 */
class PagingProgress(
    private val nowMillis: () -> Long = { TimeUtils.nowMillis() },
) {
    private class Walk(
        val top: Long,
        val bottom: Long,
        val startedMs: Long,
        @Volatile var current: Long,
    )

    private val walks = ConcurrentMap<String, Walk>()

    /** Begin a walk over `[bottom, top]` seconds. An inverted window is not a walk. */
    fun begin(
        key: String,
        top: Long,
        bottom: Long,
    ) {
        if (top > bottom) walks[key] = Walk(top, bottom, nowMillis(), top)
    }

    /** The walk reached [until]; monotonic, so a page that jumps back cannot un-advance it. */
    fun mark(
        key: String,
        until: Long,
    ) {
        walks[key]?.let {
            // Clamped to the walk's own floor: relays serve events stamped 0,
            // and one of those would drag the position to the epoch. Below the
            // floor means the walk is done, not time travel.
            val reached = until.coerceAtLeast(it.bottom)
            if (reached < it.current) it.current = reached
        }
    }

    fun finish(key: String) {
        walks.remove(key)
    }

    /**
     * Fraction of the walk complete, averaged over every walk still going in
     * [group] (or all of them when null) — averaged rather than summed
     * because each covers its own span, so "half the walks done and half at
     * zero" is 50%.
     */
    fun fraction(group: String? = null): Double? {
        val live = live(group)
        if (live.isEmpty()) return null
        return live.sumOf { w ->
            val span = (w.top - w.bottom).coerceAtLeast(1)
            ((w.top - w.current).toDouble() / span).coerceIn(0.0, 1.0)
        } / live.size
    }

    private fun live(group: String?): List<Walk> =
        if (group == null) {
            walks.snapshot().values.toList()
        } else {
            walks
                .snapshot()
                .entries
                .filter { it.key.startsWith("$group|") }
                .map { it.value }
        }

    /** The oldest second [group] has reached, or null when it is not walking. */
    fun reached(group: String? = null): Long? = live(group).minOfOrNull { it.current }

    /** Milliseconds left at the rate achieved so far, or null before it means anything. */
    fun etaMs(group: String? = null): Long? {
        val f = fraction(group) ?: return null
        // Under a few percent the extrapolation is dominated by connect time
        // and produces numbers worse than saying nothing.
        if (f < 0.02) return null
        val oldestStart = live(group).minOfOrNull { it.startedMs } ?: return null
        val elapsed = nowMillis() - oldestStart
        if (elapsed < 5_000) return null
        return ((elapsed / f) - elapsed).toLong()
    }
}
