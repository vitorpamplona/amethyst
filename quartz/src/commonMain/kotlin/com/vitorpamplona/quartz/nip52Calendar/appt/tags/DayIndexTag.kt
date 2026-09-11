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
package com.vitorpamplona.quartz.nip52Calendar.appt.tags

import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.ensure

/**
 * NIP-52's uppercase `D` day index on a kind:31923 time-based calendar event: the
 * day-granularity unix timestamp `floor(unix_seconds / 86400)`, one tag per UTC calendar
 * day the event's `start`..`end` range spans.
 *
 * It is what makes an event discoverable by date — a client browsing "what's on the 14th"
 * queries `{"kinds":[31923],"#D":["<index>"]}` instead of pulling every calendar event in
 * existence and filtering client-side. Uppercase single-letter tags are indexed by relays
 * exactly like lowercase ones; the case only distinguishes it from the `d` identifier.
 */
class DayIndexTag {
    companion object {
        const val TAG_NAME = "D"

        const val SECONDS_IN_DAY = 86400L

        /**
         * The most `D` tags one event may carry. NIP-52 puts no ceiling on the range, but a
         * multi-year event would otherwise emit thousands of tags and blow past relay event-size
         * limits; capping keeps a mistyped end date from producing an unpublishable event. Beyond
         * the cap the range is truncated (the start day is always emitted), never dropped.
         */
        const val MAX_DAYS = 366

        /** The day index containing [timestamp] (unix seconds). */
        fun dayIndex(timestamp: Long): Long = timestamp.floorDiv(SECONDS_IN_DAY)

        fun parse(tag: Array<String>): Long? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            return tag[1].toLongOrNull()
        }

        fun assemble(dayIndex: Long) = arrayOf(TAG_NAME, dayIndex.toString())

        /**
         * Every day index a `start`..`end` range touches, in order.
         *
         * `end` is **exclusive** per NIP-52, so an event that finishes exactly at midnight does not
         * claim the following day; an event with no end (or an end at/before start) is instantaneous
         * and occupies only its start day. Result is capped at [MAX_DAYS].
         */
        fun dayIndexes(
            start: Long,
            end: Long? = null,
        ): List<Long> {
            val first = dayIndex(start)
            if (end == null || end <= start) return listOf(first)

            // An exclusive end lands on the first instant NOT in the range, so the last day is the
            // one holding end-1. Without the -1, an event ending at 00:00:00 would tag the next day.
            val last = dayIndex(end - 1)
            if (last <= first) return listOf(first)

            return (first..minOf(last, first + MAX_DAYS - 1)).toList()
        }

        /** [dayIndexes] already assembled into tags. */
        fun assembleAll(
            start: Long,
            end: Long? = null,
        ) = dayIndexes(start, end).map { assemble(it) }
    }
}
