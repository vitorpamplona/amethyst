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
package com.vitorpamplona.quartz.cyberspace.deck0003Sno

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integer overflow in the run-length encodings and the position bound.
 *
 * Both of §1's run-length forms say "a **negative** integer -N", and both were
 * read by negating the entry. `-Int.MIN_VALUE` is `Int.MIN_VALUE` — still
 * negative — so the guards that assumed a positive count did not hold, and
 * `abs()` on the position bound had the same hole.
 *
 * These payloads reach the parser straight off a relay and the parse runs
 * inside composition, so the ticks case was an uncaught crash in a feed from a
 * hostile kind 33331, 3330 or 11333 event, and the other two were silent
 * corruption. Every case here is a payload a stranger can publish.
 */
class SnoParserOverflowTest {
    private fun payload(
        vertices: String = "[[0,0,0],[2,0,0],[1,0,2],[1,2,1]]",
        colors: String = "[238,235,239,225]",
        ticks: String = "",
        extra: String = "",
    ) = """{"v":2,"name":"t","unit":0,"mode":"solid","vertices":$vertices$ticks,"colors":$colors,"faces":[[0,1,2]]$extra}"""

    @Test
    fun aTicksRunOfIntMinValueIsRefusedRatherThanCrashing() {
        // Negating this gives itself, so `written` went negative and the next
        // triple indexed out of the buffer.
        val result = SnoParser.parse(payload(ticks = ""","ticks":[-2147483648,[1,2,3]]"""))
        assertTrue(result is SnoResult.Invalid)
        assertEquals("7", result.rule)
    }

    @Test
    fun aTicksRunLongerThanTheVertexListIsRefused() {
        assertTrue(SnoParser.parse(payload(ticks = ""","ticks":[-5]""")) is SnoResult.Invalid)
        assertTrue(SnoParser.parse(payload(ticks = ""","ticks":[-2147483647]""")) is SnoResult.Invalid)
    }

    @Test
    fun aFaceColourRunOfIntMinValueIsRefused() {
        // This one parsed Valid: the run was swallowed whole and the expansion
        // never reached the length check.
        val result = SnoParser.parse(payload(extra = ""","facecolors":[0,-2147483648]"""))
        assertTrue(result is SnoResult.Invalid)
        assertEquals("8c", result.rule)
    }

    @Test
    fun aVertexOfIntMinValueIsRefusedRatherThanRewritten() {
        // abs(Int.MIN_VALUE) is negative, so the ±64-unit bound passed; then
        // `* 120` overflowed to exactly 0 and the payload parsed Valid with the
        // coordinate silently moved to the origin.
        val result = SnoParser.parse(payload(vertices = "[[0,0,0],[2,0,0],[1,0,2],[-2147483648,2,1]]"))
        assertTrue(result is SnoResult.Invalid)
        assertEquals("bound", result.rule)
    }

    @Test
    fun aVertexOfIntMaxValueIsRefused() {
        val result = SnoParser.parse(payload(vertices = "[[0,0,0],[2,0,0],[1,0,2],[2147483647,2,1]]"))
        assertTrue(result is SnoResult.Invalid)
        assertEquals("bound", result.rule)
    }

    @Test
    fun theOrdinaryRunLengthFormsStillWork() {
        // The guards must not have cost the encodings they protect.
        val whole = SnoParser.parse(payload(ticks = ""","ticks":[-4]""")).payloadOrNull()
        assertNotNull(whole)
        assertEquals(0, whole.tickAt(0, 0))

        val flat = SnoParser.parse(payload(extra = ""","facecolors":[238]""")).payloadOrNull()
        assertNotNull(flat)
        assertEquals(1, flat.faceColors?.size)

        val mixed = SnoParser.parse(payload(ticks = ""","ticks":[[40,0,0],-3]""")).payloadOrNull()
        assertNotNull(mixed)
        // tickAt is the TOTAL: vertex 0 is [0,0,0] plus a 40-tick remainder,
        // and vertex 1 is [2,0,0] with the run's zero remainder, so 2 units.
        assertEquals(40, mixed.tickAt(0, 0))
        assertEquals(2 * SnoPayload.TICKS_PER_UNIT, mixed.tickAt(1, 0))
    }
}
