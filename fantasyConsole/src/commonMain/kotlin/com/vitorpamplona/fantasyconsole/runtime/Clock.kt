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
package com.vitorpamplona.fantasyconsole.runtime

import kotlin.time.ExperimentalTime

/** `stat(80..85)` and `stat(90..95)`: year, month, day, hour, minute, second. */
internal object Clock {
    @OptIn(ExperimentalTime::class)
    private fun nowSeconds(): Long =
        kotlin.time.Clock.System
            .now()
            .epochSeconds

    fun utc(): IntArray = civil(nowSeconds())

    fun local(offsetSeconds: Int): IntArray = civil(nowSeconds() + offsetSeconds)

    /** Days-from-epoch to a calendar date (Howard Hinnant's algorithm), then the time of day. */
    private fun civil(epochSeconds: Long): IntArray {
        val days = Math.floorDiv(epochSeconds, 86400L)
        val secondOfDay = (epochSeconds - days * 86400L).toInt()
        val z = days + 719468
        val era = Math.floorDiv(z, 146097L)
        val doe = z - era * 146097
        val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val day = (doy - (153 * mp + 2) / 5 + 1).toInt()
        val month = (if (mp < 10) mp + 3 else mp - 9).toInt()
        val year = (yoe + era * 400 + if (month <= 2) 1 else 0).toInt()
        return intArrayOf(year, month, day, secondOfDay / 3600, secondOfDay / 60 % 60, secondOfDay % 60)
    }

    private object Math {
        fun floorDiv(
            a: Long,
            b: Long,
        ): Long {
            val q = a / b
            return if ((a % b != 0L) && ((a xor b) < 0)) q - 1 else q
        }
    }
}
