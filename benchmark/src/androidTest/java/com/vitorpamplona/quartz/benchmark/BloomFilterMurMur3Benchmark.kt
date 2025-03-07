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
package com.vitorpamplona.quartz.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.hints.bloom.BloomFilterMurMur3
import com.vitorpamplona.quartz.utils.RandomInstance
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BloomFilterMurMur3Benchmark {
    @get:Rule val benchmarkRule = BenchmarkRule()

    val testEncoded = "100:10:AKiEIEQKALgRACEABA==:3"

    val key1 = "ca29c211f1c72d5b6622268ff43d2288ea2b2cb5b9aa196ff9f1704fc914b71b".hexToByteArray()
    val key2 = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c".hexToByteArray()
    val key3 = "560c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c".hexToByteArray()

    val keys =
        mutableListOf<ByteArray>().apply {
            for (seed in 0..1000000) {
                add(RandomInstance.bytes(32))
            }
        }

    val keys2 =
        mutableListOf<ByteArray>().apply {
            for (seed in 0..1000000) {
                add(RandomInstance.bytes(32))
            }
        }

    @Test
    fun addExisting() {
        val filter = BloomFilterMurMur3.decode(testEncoded)
        benchmarkRule.measureRepeated {
            filter.add(key1)
        }
    }

    @Test
    fun addNew() {
        val filter = BloomFilterMurMur3.decode(testEncoded)
        benchmarkRule.measureRepeated {
            filter.add(key3)
        }
    }

    @Test
    fun mightContainTrue() {
        val filter = BloomFilterMurMur3(10_000_000, 5)
        filter.add(key1)
        keys.forEach(filter::add)
        benchmarkRule.measureRepeated {
            filter.mightContain(key1)
        }
    }

    @Test
    fun mightContainFalse() {
        val filter = BloomFilterMurMur3(10_000_000, 5)
        keys.forEach(filter::add)
        benchmarkRule.measureRepeated {
            filter.mightContain(key3)
        }
    }

    @Test
    fun decode() {
        benchmarkRule.measureRepeated {
            BloomFilterMurMur3.decode(testEncoded)
        }
    }

    @Test
    fun encode() {
        val filter = BloomFilterMurMur3.decode(testEncoded)
        benchmarkRule.measureRepeated {
            filter.encode()
        }
    }

    @Test
    fun largeFilterBuild() {
        val bloomFilter = BloomFilterMurMur3(10_000_000, 5)

        benchmarkRule.measureRepeated {
            keys.forEach(bloomFilter::add)
        }
    }

    @Test
    fun largeFilterCheckExisting() {
        val bloomFilter = BloomFilterMurMur3(10_000_000, 5)
        keys.forEach(bloomFilter::add)

        benchmarkRule.measureRepeated {
            keys.forEach(bloomFilter::mightContain)
        }
    }

    @Test
    fun largeFilterCheckNew() {
        val bloomFilter = BloomFilterMurMur3(10_000_000, 5)
        keys.forEach(bloomFilter::add)

        benchmarkRule.measureRepeated {
            keys2.forEach(bloomFilter::mightContain)
        }
    }
}
