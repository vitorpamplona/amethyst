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
package com.vitorpamplona.quartz.cyberspace

import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.roundToLong

/**
 * How big one model unit actually is.
 *
 * DECK-0003 §1.6 says a `unit` of `u` means one model unit is `2^u` of the
 * application's base unit, and leaves what that base unit *is* to the
 * application. In Cyberspace it is the gibson, and `CYBERSPACE_V2.md` §9.2 and
 * §9.7 fix the gibson at `2^-33` metres — about the width of a hydrogen atom.
 * Those two facts together are the only thing standing between the integer in
 * the payload and a size a reader can picture.
 *
 * They matter more than they look. Two objects with byte-identical geometry at
 * `unit: 0` and `unit: 40` are a molecule and a mountain, and a client that
 * prints "8 vertices · 12 faces" for both has told the reader nothing about
 * the one field that separates them. The reference workshop puts this on
 * screen as a scale ladder (`sno-core/scale.ts`); the numbers and the unit
 * thresholds here are that file's, so the same object reads the same size in
 * both.
 *
 * Nothing here is normative and nothing downstream depends on the exact
 * wording: it is a length, rendered the way a person reads lengths.
 */
object CyberspaceScale {
    /** The gibson in metres: `2^-33` (§9.2). */
    private const val GIBSON_METRES = 1.1641532182693481e-10

    /** The largest `unit` DECK-0003 §1.8 allows, and the range these cover. */
    private const val MAX_UNIT = 84

    private class Measure(
        /** Used below this many metres. */
        val limit: Double,
        val symbol: String,
        val perMetre: Double,
    )

    // µ is the micro sign; written as an escape so the file stays
    // unambiguous about which of the two lookalike code points it carries.
    private val MEASURES =
        listOf(
            Measure(1e-9, "pm", 1e12),
            Measure(1e-6, "nm", 1e9),
            Measure(1e-3, "µm", 1e6),
            Measure(1e-2, "mm", 1e3),
            Measure(1e-1, "cm", 1e2),
            Measure(1e3, "m", 1.0),
            Measure(1e6, "km", 1e-3),
            Measure(1e9, "Mm", 1e-6),
            Measure(1.496e11, "AU", 1.0 / 1.496e11),
        )

    /** One model unit in metres, at this scale exponent. */
    fun unitInMetres(unit: Int): Double {
        var metres = GIBSON_METRES
        repeat(unit.coerceIn(0, MAX_UNIT)) { metres *= 2.0 }
        return metres
    }

    /**
     * One model unit as a length a person reads: `"116 pm"`, `"1 m"`, `"2 AU"`.
     *
     * Three significant figures, in the largest measure the length fits inside,
     * with nothing trailing that carries no information.
     */
    fun describeUnit(unit: Int): String {
        val metres = unitInMetres(unit)
        val measure = MEASURES.firstOrNull { metres < it.limit } ?: MEASURES.last()
        return significant(metres * measure.perMetre) + " " + measure.symbol
    }

    /**
     * A positive number to three significant figures, without a trailing zero
     * or a trailing point.
     *
     * Built from a Long rather than a platform formatter, because there is no
     * locale-independent one in common Kotlin and a size that renders as
     * "1,91 µm" for half the world and "1.91 µm" for the other half is a
     * number two readers cannot compare.
     */
    private fun significant(value: Double): String {
        if (value <= 0.0 || !value.isFinite()) return "0"
        val exponent = floor(log10(value)).toInt()

        // Four digits or more: three of them carry the figure and the rest are
        // zeros, so 1876 reads 1880 and 15060 reads 15100.
        if (exponent >= 2) {
            var step = 1L
            repeat((exponent - 2).coerceAtMost(17)) { step *= 10L }
            return ((value / step).roundToLong() * step).toString()
        }

        val places = (2 - exponent).coerceAtMost(12)
        var scale = 1L
        repeat(places) { scale *= 10L }
        val scaled = (value * scale).roundToLong()
        val whole = scaled / scale
        val fraction = (scaled % scale).toString().padStart(places, '0').trimEnd('0')
        return if (fraction.isEmpty()) whole.toString() else "$whole.$fraction"
    }
}
