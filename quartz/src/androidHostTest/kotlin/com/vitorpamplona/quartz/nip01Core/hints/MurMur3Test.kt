/**
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
package com.vitorpamplona.quartz.nip01Core.hints

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.hints.bloom.MurmurHash3
import junit.framework.TestCase.assertEquals
import org.junit.Test

class MurMur3Test {
    class Case(
        val bytesHex: HexKey,
        val seed: Int,
        val result: Int,
    )

    val testCases =
        listOf(
            Case("9fd4e9a905ca9e1a3086fa4c0a1ed829dbf18c15ec05af95c76b78d3d2f5651b", 886838366, -525456393),
            Case("e6c8f70f0d35a983bfebd00e5f29787c009c52971cfb4ac3a49b534b256b59cc", 1717487548, 1605080838),
            Case("7f7113833feb31e877f193e2fc75a64e9c70252c3ae3c73373ff34430ae40ea6", 1275582690, 225480992),
            Case("61770be6ec9df0f490743318e796e28ae34609732b61d365947871532d77d697", 514559346, 1424957638),
            Case("375f46b4687ba3cd035db303fa294d943816e64ca6b3adcda2ae40e8ac9d91a0", 1898708424, 1730418066),
            Case("c67044cd1d07a2aeb92b7bec973b6feb8abb9197840c59c101cacaa992489d49", 294602161, -1944496371),
            Case("49db4bfcc4da62e38c4076843cdde1425570806f09f121f5e7f2507c5ee1db85", 910710684, 944243368),
            Case("c5e98a30dead5ade4900b26eabae3435cfcdb64ff5e55c99641915a0c6ee73fc", 1107230285, 1550302684),
            Case("b0ed2e7568e6b4e1d5e5bab46fde01149331b824e48a281798d7216dde8f5890", 1013875681, -1265544300),
            Case("805f290e865bde094d77e82fb8b338d83347bc5449a4aed9fb08afb6a53a079b", 1674416787, -1821262025),
            // special cases
            Case("805f290e865bde094d77e82fb8b338d83347bc5449a4aed9fb08afb6a53a079b", Int.MAX_VALUE, -422576759),
            Case("805f290e865bde094d77e82fb8b338d83347bc5449a4aed9fb08afb6a53a079b", Int.MAX_VALUE + 1, 851385048),
            Case("805f290e865bde094d77e82fb8b338d83347bc5449a4aed9fb08afb6a53a079b", Int.MIN_VALUE, 851385048),
            Case("805f290e865bde094d77e82fb8b338d83347bc5449a4aed9fb08afb6a53a079b", 0, 1615518380),
            Case("fd", 1, 975430984),
            Case("00", 1, 0),
            Case("FF", 1, -797126820),
            Case("3033", 1, 1435178296),
            Case("0000", 1, -2047822809),
            Case("FFFF", 1, 1459517456),
            Case("3652a8", 1, 103723868),
            Case("000000", 1, 821347078),
            Case("FFFFFF", 1, -761438248),
            Case("00000000", 1, 2028806445),
            Case("FFFFFFFF", 1, 919009801),
        )

    @Test
    fun testMurMur() {
        val hasher = MurmurHash3()
        testCases.forEach {
            assertEquals(
                it.result,
                hasher.hash(it.bytesHex.hexToByteArray(), it.seed),
            )
        }
    }
}
