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
package com.vitorpamplona.quartz.experimental.bitchat

import com.vitorpamplona.quartz.experimental.bitchat.geohash.GeohashChannelLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GeohashChannelLevelTest {
    @Test
    fun charCountsMatchBitchat() {
        assertEquals(2, GeohashChannelLevel.REGION.chars)
        assertEquals(4, GeohashChannelLevel.PROVINCE.chars)
        assertEquals(5, GeohashChannelLevel.CITY.chars)
        assertEquals(6, GeohashChannelLevel.NEIGHBORHOOD.chars)
        assertEquals(7, GeohashChannelLevel.BLOCK.chars)
        assertEquals(8, GeohashChannelLevel.BUILDING.chars)
    }

    @Test
    fun cellForTruncatesToLevel() {
        val fix = "u4pruydq" // 8 chars
        assertEquals("u4", GeohashChannelLevel.REGION.cellFor(fix))
        assertEquals("u4pr", GeohashChannelLevel.PROVINCE.cellFor(fix))
        assertEquals("u4pru", GeohashChannelLevel.CITY.cellFor(fix))
        assertEquals("u4pruy", GeohashChannelLevel.NEIGHBORHOOD.cellFor(fix))
        assertEquals("u4pruyd", GeohashChannelLevel.BLOCK.cellFor(fix))
        assertEquals("u4pruydq", GeohashChannelLevel.BUILDING.cellFor(fix))
    }

    @Test
    fun cellForNullWhenTooShort() {
        assertNull(GeohashChannelLevel.BUILDING.cellFor("u4pru")) // only 5 chars
        assertEquals("u4pru", GeohashChannelLevel.CITY.cellFor("u4pru"))
    }

    @Test
    fun orderedIsCoarseToFine() {
        assertEquals(listOf(2, 4, 5, 6, 7, 8), GeohashChannelLevel.ordered.map { it.chars })
    }

    @Test
    fun forCharsResolvesNamedLevels() {
        assertEquals(GeohashChannelLevel.CITY, GeohashChannelLevel.forChars(5))
        assertNull(GeohashChannelLevel.forChars(3))
    }
}
