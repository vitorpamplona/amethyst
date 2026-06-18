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
package com.vitorpamplona.quartz.experimental.roadstr.tags

import kotlin.math.abs
import kotlin.math.round

/**
 * Formatting for the `lat`/`lon` tag values of Roadstr events. The spec requires
 * a decimal string with exactly 7 fractional digits (e.g. `48.8566140`), which is
 * locale-independent here (unlike `String.format`, which is unavailable in
 * `commonMain` anyway).
 */
object RoadCoordinate {
    private const val SCALE = 10_000_000L // 7 decimal places

    fun format(value: Double): String {
        val scaled = round(value * SCALE).toLong()
        val negative = scaled < 0L
        val absValue = abs(scaled)
        val intPart = absValue / SCALE
        val fracPart = (absValue % SCALE).toString().padStart(7, '0')
        return (if (negative) "-" else "") + intPart + "." + fracPart
    }
}
