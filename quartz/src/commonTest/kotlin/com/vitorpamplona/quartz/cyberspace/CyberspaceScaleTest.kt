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

import com.vitorpamplona.quartz.cyberspace.deck0003Sno.SnoPayload
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * One model unit as a length, checked against the reference the whole way up.
 *
 * Every value DECK-0003 §1.8 permits for `unit` — 0 to 84 — is here, taken
 * from `sno-core/scale.ts` run over the same range, because the point of the
 * readout is that the same object reads the same size in both clients. The
 * boundaries are where the two could drift: where the measure changes, where a
 * figure rounds up past its own magnitude, and `unit: 28`, which is 3.125 cm
 * exactly — a tie, and the one entry where a language's default rounding shows.
 * ECMAScript's `toPrecision` takes the larger, so 3.13, and `roundToLong` here
 * rounds half up and agrees.
 */
class CyberspaceScaleTest {
    private val reference =
        (
            "116 pm,233 pm,466 pm,931 pm,1.86 nm,3.73 nm,7.45 nm,14.9 nm,29.8 nm,59.6 nm,119 nm," +
                "238 nm,477 nm,954 nm,1.91 µm,3.81 µm,7.63 µm,15.3 µm,30.5 µm,61 µm," +
                "122 µm,244 µm,488 µm,977 µm,1.95 mm,3.91 mm,7.81 mm,1.56 cm,3.13 cm,6.25 cm," +
                "0.125 m,0.25 m,0.5 m,1 m,2 m,4 m,8 m,16 m,32 m,64 m,128 m,256 m,512 m,1.02 km,2.05 km," +
                "4.1 km,8.19 km,16.4 km,32.8 km,65.5 km,131 km,262 km,524 km,1.05 Mm,2.1 Mm,4.19 Mm," +
                "8.39 Mm,16.8 Mm,33.6 Mm,67.1 Mm,134 Mm,268 Mm,537 Mm,0.00718 AU,0.0144 AU,0.0287 AU," +
                "0.0574 AU,0.115 AU,0.23 AU,0.459 AU,0.919 AU,1.84 AU,3.67 AU,7.35 AU,14.7 AU,29.4 AU," +
                "58.8 AU,118 AU,235 AU,470 AU,941 AU,1880 AU,3760 AU,7530 AU,15100 AU"
        ).split(",")

    @Test
    fun everyUnitReadsAsTheReferenceReadsIt() {
        assertEquals(SnoPayload.MAX_UNIT + 1, reference.size, "the table should cover every unit the deck allows")
        for (unit in reference.indices) {
            assertEquals(reference[unit], CyberspaceScale.describeUnit(unit), "unit $unit")
        }
    }

    @Test
    fun theAnchorsAreTheOnesTheSpecStates() {
        // §9.3: "Cantor Height 33 = 1 meter", and 34 is the two metres the
        // scale was calibrated on.
        assertEquals("1 m", CyberspaceScale.describeUnit(33))
        assertEquals("2 m", CyberspaceScale.describeUnit(34))
        // §9.2: a gibson is 2^-33 m, "roughly the size of a hydrogen atom".
        assertEquals("116 pm", CyberspaceScale.describeUnit(0))
        assertEquals(1.0, CyberspaceScale.unitInMetres(33), 1e-12)
    }

    @Test
    fun aUnitOutsideTheDeckIsHeldAtTheEdgeRatherThanOverflowing() {
        // The parser rejects these long before here, so this is only about not
        // producing a nonsense string if some other caller asks.
        assertEquals(CyberspaceScale.describeUnit(0), CyberspaceScale.describeUnit(-5))
        assertEquals(CyberspaceScale.describeUnit(SnoPayload.MAX_UNIT), CyberspaceScale.describeUnit(9999))
        assertTrue(CyberspaceScale.unitInMetres(9999).isFinite())
    }
}
