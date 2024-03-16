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
package com.vitorpamplona.amethyst.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitorpamplona.amethyst.commons.robohash.Robohash
import com.vitorpamplona.amethyst.commons.robohash.RobohashAssembler
import okio.Buffer
import okio.buffer
import okio.source
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.charset.Charset

/**
 * Benchmark, which will execute on an Android device.
 *
 * The body of [BenchmarkRule.measureRepeated] is measured in a loop, and Studio will output the
 * result. Modify your code to see how it affects performance.
 */
@RunWith(AndroidJUnit4::class)
class RobohashBenchmark {
    @get:Rule val benchmarkRule = BenchmarkRule()

    val warmHex = "f4f016c739b8ec0d6313540a8b12cf48a72b485d38338627ec9d427583551f9a"
    val testHex = "48a72b485d38338627ec9d427583551f9af4f016c739b8ec0d6313540a8b12cf"

    @Test
    fun createSVGInString() {
        // warm up
        Robohash.assemble(warmHex, true)
        benchmarkRule.measureRepeated {
            Robohash.assemble(testHex, true)
        }
    }

    @Test
    fun createSVGFromPaths() {
        // warm up
        benchmarkRule.measureRepeated {
            RobohashAssembler().build(testHex, true)
        }
    }

    @Test
    fun createSVGInBufferCopy() {
        // warm up
        Robohash.assemble(warmHex, true)
        benchmarkRule.measureRepeated {
            val buffer = Buffer()
            buffer.writeString(Robohash.assemble(testHex, true), Charset.defaultCharset())
        }
    }

    @Test
    fun createSVGInBufferViaInputStream() {
        // warm up
        Robohash.assemble(warmHex, true)
        benchmarkRule.measureRepeated {
            Robohash.assemble(testHex, true).byteInputStream(Charset.defaultCharset()).source().buffer()
        }
    }
}
