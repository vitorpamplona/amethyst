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
package com.vitorpamplona.quartz.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitorpamplona.quartz.utils.Hex
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Benchmark, which will execute on an Android device.
 *
 * The body of [BenchmarkRule.measureRepeated] is measured in a loop, and Studio will output the
 * result. Modify your code to see how it affects performance.
 */
@RunWith(AndroidJUnit4::class)
class HexBenchmark {
    @get:Rule val r = BenchmarkRule()

    val hex = "48a72b485d38338627ec9d427583551f9af4f016c739b8ec0d6313540a8b12cf"
    val bytes =
        fr.acinq.secp256k1.Hex
            .decode(hex)

    @Test
    fun hexIsEqual() {
        r.measureRepeated {
            assert(Hex.isEqual(hex, bytes))
        }
    }

    @Test
    fun hexDecodeOurs() {
        r.measureRepeated {
            Hex
                .decode(hex)
        }
    }

    @Test
    fun hexEncodeOurs() {
        r.measureRepeated {
            Hex
                .encode(bytes)
        }
    }

    @Test
    fun hexDecodeBaseSecp() {
        r.measureRepeated {
            fr.acinq.secp256k1.Hex
                .decode(hex)
        }
    }

    @Test
    fun hexEncodeBaseSecp() {
        r.measureRepeated {
            fr.acinq.secp256k1.Hex
                .encode(bytes)
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun hexDecodeKotlin() {
        r.measureRepeated { hex.hexToByteArray(HexFormat.Default) }
    }

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun hexEncodeKotlin() {
        r.measureRepeated { bytes.toHexString(HexFormat.Default) }
    }

    @Test
    fun isHex() {
        r.measureRepeated { Hex.isHex(hex) }
    }

    @Test
    fun isHex64() {
        r.measureRepeated { Hex.isHex64(hex) }
    }
}
