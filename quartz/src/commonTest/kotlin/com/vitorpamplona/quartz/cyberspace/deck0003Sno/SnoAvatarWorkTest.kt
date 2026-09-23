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
import kotlin.test.assertTrue

/**
 * The work an avatar owes (`CYBERSPACE_V2.md` §8.10).
 *
 * The expected values are computed independently from the section's own
 * normative pseudocode rather than lifted from a client, so this checks the
 * Kotlin against the specification and not against another port of it.
 */
class SnoAvatarWorkTest {
    private data class Vector(
        val name: String,
        val expectedBits: Int,
        val json: String,
    )

    private val vectors =
        listOf(
            Vector(
                name = "one gibson",
                expectedBits = 16,
                json = "{\"v\":2,\"name\":\"one gibson\",\"unit\":0,\"mode\":\"solid\",\"vertices\":[[0,0,0],[1,0,0],[0,1,0]],\"colors\":[225,225,225],\"faces\":[[0,1,2]]}",
            ),
            Vector(
                name = "tetra at unit 0",
                expectedBits = 18,
                json = "{\"v\":2,\"name\":\"tetra at unit 0\",\"unit\":0,\"mode\":\"solid\",\"vertices\":[[0,0,0],[2,0,0],[1,0,2],[1,2,1]],\"colors\":[225,225,225,225],\"faces\":[[0,1,2],[0,1,3],[1,2,3],[0,2,3]]}",
            ),
            Vector(
                name = "tetra at unit 12",
                expectedBits = 42,
                json = "{\"v\":2,\"name\":\"tetra at unit 12\",\"unit\":12,\"mode\":\"solid\",\"vertices\":[[0,0,0],[2,0,0],[1,0,2],[1,2,1]],\"colors\":[225,225,225,225],\"faces\":[[0,1,2],[0,1,3],[1,2,3],[0,2,3]]}",
            ),
            Vector(
                name = "tetra at unit 33",
                expectedBits = 84,
                json = "{\"v\":2,\"name\":\"tetra at unit 33\",\"unit\":33,\"mode\":\"solid\",\"vertices\":[[0,0,0],[2,0,0],[1,0,2],[1,2,1]],\"colors\":[225,225,225,225],\"faces\":[[0,1,2],[0,1,3],[1,2,3],[0,2,3]]}",
            ),
            Vector(
                name = "sub-unit only",
                expectedBits = 16,
                json = "{\"v\":2,\"name\":\"sub-unit only\",\"unit\":0,\"mode\":\"solid\",\"vertices\":[[0,0,0],[0,0,0],[0,0,0]],\"colors\":[225,225,225],\"faces\":[[0,1,2]],\"ticks\":[[0,0,0],[40,0,0],[0,60,0]]}",
            ),
            Vector(
                name = "detailed",
                expectedBits = 25,
                json = "{\"v\":2,\"name\":\"detailed\",\"unit\":0,\"mode\":\"solid\",\"vertices\":[[0,0,0],[1,0,0],[2,0,0],[3,0,0],[4,0,0],[0,1,0],[1,1,0],[2,1,0],[3,1,0],[4,1,0],[0,2,0],[1,2,0],[2,2,0],[3,2,0],[4,2,0],[0,3,0],[1,3,0],[2,3,0],[3,3,0],[4,3,0],[0,4,0],[1,4,0],[2,4,0],[3,4,0],[4,4,0],[0,0,1],[1,0,1],[2,0,1],[3,0,1],[4,0,1],[0,1,1],[1,1,1],[2,1,1],[3,1,1],[4,1,1],[0,2,1],[1,2,1],[2,2,1],[3,2,1],[4,2,1],[0,3,1],[1,3,1],[2,3,1],[3,3,1],[4,3,1],[0,4,1],[1,4,1],[2,4,1],[3,4,1],[4,4,1],[0,0,2],[1,0,2],[2,0,2],[3,0,2],[4,0,2],[0,1,2],[1,1,2],[2,1,2],[3,1,2],[4,1,2]],\"colors\":[225,225,225,225,225,225,225,225,225,225,225,225,225,225,225,225,225,225,225,225,225,225,225,225,225,225,225,225,225,225,225,225,225,225,225,225,225,225,225,225,225,225,225,225,225,225,225,225,225,225,225,225,225,225,225,225,225,225,225,225],\"faces\":[[0,1,2],[0,1,2],[0,1,2],[0,1,2],[0,1,2],[0,1,2],[0,1,2],[0,1,2],[0,1,2],[0,1,2],[0,1,2],[0,1,2],[0,1,2],[0,1,2],[0,1,2],[0,1,2],[0,1,2],[0,1,2],[0,1,2],[0,1,2],[0,1,2],[0,1,2],[0,1,2],[0,1,2],[0,1,2],[0,1,2],[0,1,2],[0,1,2],[0,1,2],[0,1,2],[0,1,2],[0,1,2],[0,1,2],[0,1,2],[0,1,2],[0,1,2],[0,1,2],[0,1,2],[0,1,2],[0,1,2]]}",
            ),
        )

    @Test
    fun theLadderMatchesTheSpecification() {
        vectors.forEach { vector ->
            val payload = SnoParser.parse(vector.json).payloadOrNull()
            assertTrue(payload != null, "${vector.name} did not parse")
            assertEquals(vector.expectedBits, SnoAvatarWork.required(payload), vector.name)
        }
    }

    @Test
    fun theFloorIsSixteenBits() {
        // "reach: ... never below one gibson", and detail below the free
        // thirty-two contributes nothing, so the smallest avatar owes the floor.
        val tiny = SnoParser.parse("""{"v":2,"name":"dot","unit":0,"mode":"points","vertices":[[0,0,0]],"colors":[225],"faces":[]}""").payloadOrNull()!!
        assertEquals(SnoAvatarWork.FLOOR_BITS, SnoAvatarWork.required(tiny))
    }

    @Test
    fun reachCostsTwoBitsPerDoubling() {
        // The ladder §8.10 names: one gibson 16 bits, two 18, four 20, sixteen 24.
        fun atUnit(unit: Int) =
            SnoAvatarWork.required(
                SnoParser.parse("""{"v":2,"name":"r","unit":$unit,"mode":"points","vertices":[[0,0,0],[1,0,0]],"colors":[225,225],"faces":[]}""").payloadOrNull()!!,
            )
        assertEquals(16, atUnit(0), "one gibson")
        assertEquals(18, atUnit(1), "two gibsons")
        assertEquals(20, atUnit(2), "four gibsons")
        assertEquals(24, atUnit(4), "sixteen gibsons")
    }

    @Test
    fun aSectorSizedAvatarIsOutOfReachOfAnyHashPower() {
        // "one the size of cyberspace about 190, which is to say never."
        val huge = SnoParser.parse("""{"v":2,"name":"vast","unit":84,"mode":"points","vertices":[[0,0,0],[1,0,0]],"colors":[225,225],"faces":[]}""").payloadOrNull()!!
        assertTrue(SnoAvatarWork.required(huge) > 150, "a cyberspace-sized avatar must be unmineable")
    }
}
