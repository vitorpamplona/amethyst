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

import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.hints.bloom.BloomFilterMurMur3
import com.vitorpamplona.quartz.utils.RandomInstance
import kotlin.test.DefaultAsserter.assertTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BloomFilterMurMur3Test {
    val testEncoded = "100:10:AKiEIEQKALgRACEABA==:3"
    val testInBinary = "00000000000101010010000100000100001000100101000000000000000111011000100000000000100001000000000000100000000000000000000000000000"

    val key1 = "ca29c211f1c72d5b6622268ff43d2288ea2b2cb5b9aa196ff9f1704fc914b71b".hexToByteArray()
    val key2 = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c".hexToByteArray()
    val key3 = "560c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c".hexToByteArray()

    val keys =
        mutableListOf<ByteArray>().apply {
            repeat(1_000_000) {
                add(RandomInstance.bytes(32))
            }
        }

    @Test
    fun testCreate() {
        val bloomFilter = BloomFilterMurMur3(100, 10, commonSalt = 3)
        bloomFilter.add(key1)
        bloomFilter.add(key2)

        assertEquals(testEncoded, bloomFilter.encode())
        assertEquals(testInBinary, bloomFilter.printBits())

        assertTrue(bloomFilter.mightContain(key1))
        assertTrue(bloomFilter.mightContain(key2))

        assertFalse(bloomFilter.mightContain(key3))
    }

    @Test
    fun testDecoding() {
        val bloomFilter = BloomFilterMurMur3.decode(testEncoded)

        assertEquals(testEncoded, bloomFilter.encode())
        assertEquals(testInBinary, bloomFilter.printBits())

        assertTrue(bloomFilter.mightContain(key1))
        assertTrue(bloomFilter.mightContain(key2))

        assertFalse(bloomFilter.mightContain(key3))
    }

    @Test
    fun runProb() {
        val bloomFilter = BloomFilterMurMur3.decode(testEncoded)

        var failureCounter = 0
        repeat(1_000_000) {
            if (bloomFilter.mightContain(RandomInstance.bytes(32))) {
                failureCounter++
            }
        }
        assertTrue(failureCounter <= 2)
    }

    @Test
    fun runProb10MbitFilterShouldStore1MKeysWith1PercentFalsePositive() {
        val bloomFilter = BloomFilterMurMur3(10_000_000, 4)
        keys.forEach(bloomFilter::add)

        var failureCounter = 0

        keys.forEach { key ->
            if (!bloomFilter.mightContain(key)) {
                failureCounter++
            }
        }

        // Bloom filters always know if a key is in.
        assertEquals(0, failureCounter)

        failureCounter = 0
        repeat(1_000_000) {
            if (bloomFilter.mightContain(RandomInstance.bytes(32))) {
                failureCounter++
            }
        }

        assertTrue("Failures $failureCounter ${failureCounter / 1_000_000.0}", failureCounter / 1_000_000.0f < 0.015)
    }
}
