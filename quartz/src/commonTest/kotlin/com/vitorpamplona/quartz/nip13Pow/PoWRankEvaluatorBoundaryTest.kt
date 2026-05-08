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
package com.vitorpamplona.quartz.nip13Pow

import com.vitorpamplona.quartz.nip13Pow.miner.PoWRankEvaluator
import kotlin.test.Test
import kotlin.test.assertEquals

class PoWRankEvaluatorBoundaryTest {
    @Test
    fun allZeroHexIdReturnsFullRankWithoutThrowing() {
        val id = "0".repeat(64)
        assertEquals(256, PoWRankEvaluator.calculatePowRankOf(id))
    }

    @Test
    fun emptyStringReturnsZero() {
        assertEquals(0, PoWRankEvaluator.calculatePowRankOf(""))
    }

    @Test
    fun shortAllZeroIdsReturnFourBitsPerNibble() {
        for (len in 1..16) {
            val id = "0".repeat(len)
            assertEquals(len * 4, PoWRankEvaluator.calculatePowRankOf(id), "len=$len")
        }
    }

    @Test
    fun leadingOneNibbleAddsThreeBitsThenBreaks() {
        // '1' = 0001b → 3 leading zero bits; loop must break after, not keep scanning.
        assertEquals(3, PoWRankEvaluator.calculatePowRankOf("1" + "0".repeat(63)))
    }

    @Test
    fun leadingTwoOrThreeNibbleAddsTwoBitsThenBreaks() {
        // '2' = 0010b, '3' = 0011b → 2 leading zero bits.
        assertEquals(2, PoWRankEvaluator.calculatePowRankOf("2" + "0".repeat(63)))
        assertEquals(2, PoWRankEvaluator.calculatePowRankOf("3" + "0".repeat(63)))
    }

    @Test
    fun leadingFourThroughSevenNibbleAddsOneBitThenBreaks() {
        // '4'..'7' all start with bit pattern 01xx → exactly 1 leading zero bit.
        for (c in '4'..'7') {
            assertEquals(1, PoWRankEvaluator.calculatePowRankOf(c + "0".repeat(63)), "leading=$c")
        }
    }

    @Test
    fun zerosThenLowNibbleAccumulatesThenBreaks() {
        // Two zero nibbles (8 bits) + a '1' nibble (+3) = 11; remainder must not be scanned.
        assertEquals(11, PoWRankEvaluator.calculatePowRankOf("001" + "f".repeat(61)))
        // Three zero nibbles (12 bits) + a '4' nibble (+1) = 13.
        assertEquals(13, PoWRankEvaluator.calculatePowRankOf("0004" + "f".repeat(60)))
    }

    @Test
    fun leadingHighNibbleReturnsZero() {
        // '8'..'f' start with bit 1 → no leading zero bits, immediate break with rank 0.
        for (c in listOf('8', '9', 'a', 'c', 'f')) {
            assertEquals(0, PoWRankEvaluator.calculatePowRankOf(c + "0".repeat(63)), "leading=$c")
        }
    }

    @Test
    fun knownNonZeroHashesRegression() {
        // Fixtures mirrored from the existing androidDeviceTest so the commonTest
        // target also covers the well-known cases.
        val cases =
            mapOf(
                "000006d8c378af1779d2feebc7603a125d99eca0ccf1085959b307f64e5dd358" to 21,
                "6bf5b4f434813c64b523d2b0e6efe18f3bd0cbbd0a5effd8ece9e00fd2531996" to 1,
                "00003479309ecdb46b1c04ce129d2709378518588bed6776e60474ebde3159ae" to 18,
                "01a76167d41add96be4959d9e618b7a35f26551d62c43c11e5e64094c6b53c83" to 7,
                "ac4f44bae06a45ebe88cfbd3c66358750159650a26c0d79e8ccaa92457fca4f6" to 0,
                "0000000000000000006cfbd3c66358750159650a26c0d79e8ccaa92457fca4f6" to 73,
                "00000026c91e9fc75fdb95b367776e2594b931cebda6d5ca3622501006669c9e" to 26,
            )
        cases.forEach { (id, expected) ->
            assertEquals(expected, PoWRankEvaluator.calculatePowRankOf(id), id)
        }
    }
}
