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
package com.vitorpamplona.quartz.experimental.postingStreak

import androidx.compose.runtime.Immutable

/**
 * An unbroken run of creative activity, `start..end` in unix seconds, read and maintained with the
 * timezone-independent rules of Ditto's kind-13473 spec, so every client computes the same value
 * from the same event.
 */
@Immutable
data class PostingStreak(
    val start: Long,
    val end: Long,
) {
    /** "The streak is live while `now - end <= W`." A broken streak MUST display nothing. */
    fun isLive(now: Long) = now - end <= WINDOW_SECONDS

    /** "It expires at `end + W`." */
    fun expiresAt() = end + WINDOW_SECONDS

    /** "Days = `max(1, ceil((end - start) / 86400))`", meaningful while [isLive]. */
    fun days(): Long = maxOf(1L, (end - start + DAY_SECONDS - 1) / DAY_SECONDS)

    /** The days to display at [now]: [days] while live, 0 once broken. */
    fun daysAt(now: Long): Long = if (isLive(now)) days() else 0L

    /**
     * The spec's update rule for a new qualifying event at [timestamp]: no change when it is not
     * newer than [end] ("streaks never move backwards"), a fresh streak when it comes more than
     * [WINDOW_SECONDS] after [end], else [end] moves to it.
     */
    fun extendedWith(timestamp: Long): PostingStreak =
        when {
            timestamp <= end -> this
            timestamp - end > WINDOW_SECONDS -> PostingStreak(timestamp, timestamp)
            else -> PostingStreak(start, timestamp)
        }

    companion object {
        /** W: the longest gap a streak survives, 36 hours. */
        const val WINDOW_SECONDS = 129_600L

        const val DAY_SECONDS = 86_400L

        /**
         * How far in the future `end` may be before the event is rejected ("more than a few
         * minutes in the future"); five minutes here.
         */
        const val MAX_CLOCK_SKEW_SECONDS = 300L

        /** "No streak yet: `start = end = t`." */
        fun startingAt(timestamp: Long) = PostingStreak(timestamp, timestamp)

        /**
         * A streak that passes the spec's sanity checks at [now], or null: both values present
         * and non-negative, `start <= end`, and `end` no more than [MAX_CLOCK_SKEW_SECONDS] ahead.
         */
        fun validOrNull(
            start: Long?,
            end: Long?,
            now: Long,
        ): PostingStreak? {
            if (start == null || end == null) return null
            if (start < 0 || end < 0 || start > end) return null
            if (end > now + MAX_CLOCK_SKEW_SECONDS) return null
            return PostingStreak(start, end)
        }
    }
}
