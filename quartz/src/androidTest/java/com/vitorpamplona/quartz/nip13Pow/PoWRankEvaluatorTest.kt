/**
 * Copyright (c) 2024 Vitor Pamplona
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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip13Pow.miner.PoWRankEvaluator
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PoWRankEvaluatorTest {
    val tests =
        mapOf(
            "000006d8c378af1779d2feebc7603a125d99eca0ccf1085959b307f64e5dd358" to 21,
            "6bf5b4f434813c64b523d2b0e6efe18f3bd0cbbd0a5effd8ece9e00fd2531996" to 1,
            "00003479309ecdb46b1c04ce129d2709378518588bed6776e60474ebde3159ae" to 18,
            "01a76167d41add96be4959d9e618b7a35f26551d62c43c11e5e64094c6b53c83" to 7,
            "ac4f44bae06a45ebe88cfbd3c66358750159650a26c0d79e8ccaa92457fca4f6" to 0,
            "0000000000000000006cfbd3c66358750159650a26c0d79e8ccaa92457fca4f6" to 73,
            "00000026c91e9fc75fdb95b367776e2594b931cebda6d5ca3622501006669c9e" to 26,
        )

    @Test
    fun testHex() {
        tests.forEach {
            assertEquals(it.value, PoWRankEvaluator.calculatePowRankOf(it.key))
        }
    }

    @Test
    fun testByte() {
        tests.forEach {
            assertEquals(it.value, PoWRankEvaluator.calculatePowRankOf(it.key.hexToByteArray()))
        }
    }

    val commitmentTest = "00000026c91e9fc75fdb95b367776e2594b931cebda6d5ca3622501006669c9e"

    @Test
    fun setPoWIfCommited25() {
        assertEquals(25, PoWRankEvaluator.compute(commitmentTest, 25))
    }

    @Test
    fun setPoWIfCommited26() {
        assertEquals(26, PoWRankEvaluator.compute(commitmentTest, 26))
    }

    @Test
    fun setPoWIfCommited27() {
        assertEquals(26, PoWRankEvaluator.compute(commitmentTest, 27))
    }
}
