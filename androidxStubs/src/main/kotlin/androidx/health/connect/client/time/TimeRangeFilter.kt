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
package androidx.health.connect.client.time

import java.time.Instant
import java.time.LocalDateTime

/** JVM stand-in for androidx.health.connect.client.time.TimeRangeFilter. */
class TimeRangeFilter private constructor(
    val startTime: Instant?,
    val endTime: Instant?,
    val localStartTime: LocalDateTime?,
    val localEndTime: LocalDateTime?,
) {
    companion object {
        fun between(
            startTime: Instant,
            endTime: Instant,
        ) = TimeRangeFilter(startTime, endTime, null, null)

        fun between(
            startTime: LocalDateTime,
            endTime: LocalDateTime,
        ) = TimeRangeFilter(null, null, startTime, endTime)

        fun after(startTime: Instant) = TimeRangeFilter(startTime, null, null, null)

        fun before(endTime: Instant) = TimeRangeFilter(null, endTime, null, null)

        fun none() = TimeRangeFilter(null, null, null, null)
    }
}
