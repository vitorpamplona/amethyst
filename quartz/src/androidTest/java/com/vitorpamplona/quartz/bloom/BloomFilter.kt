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
package com.vitorpamplona.quartz.bloom

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.encoders.hexToByteArray
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigInteger
import java.security.MessageDigest
import java.util.Base64
import java.util.BitSet
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class BloomFilter(
    private val size: Int,
    private val rounds: Int,
    private val bits: BitSet = BitSet(size),
) {
    private val hash = MessageDigest.getInstance("SHA-256")
    private val lock = ReentrantReadWriteLock()

    fun add(value: HexKey) = add(value.hexToByteArray())

    fun print(): String {
        val builder = StringBuilder()
        for (seed in 0 until bits.size()) {
            builder.append(if (bits.get(seed)) "1" else "0")
        }
        return builder.toString()
    }

    fun add(value: ByteArray) {
        lock.write {
            for (seed in 0 until rounds) {
                bits.set(hash(seed, value))
            }
        }
    }

    fun mightContains(value: HexKey): Boolean = mightContains(value.hexToByteArray())

    fun mightContains(value: ByteArray): Boolean {
        lock.read {
            for (seed in 0 until rounds) {
                if (!bits.get(hash(seed, value))) {
                    return false
                }
            }
        }
        return true
    }

    fun encode() = "$size:$rounds:${Base64.getEncoder().encodeToString(bits.toByteArray())}"

    fun hash(
        seed: Int,
        value: ByteArray,
    ) = BigInteger(1, hash.digest(value + seed.toByte()))
        .remainder(BigInteger.valueOf(size.toLong()))
        .toInt()

    companion object {
        fun decode(encodedStr: String): BloomFilter {
            val parts = encodedStr.split(":")
            val size = parts[0].toInt()
            val rounds = parts[1].toInt()
            val bitSet = BitSet.valueOf(Base64.getDecoder().decode(parts[2]))
            return BloomFilter(size, rounds, bitSet)
        }
    }
}

@RunWith(AndroidJUnit4::class)
class BloomFilterTest {
    val testEncoded = "100:10:QGKCgBEBAAhIAApO"
    val testInBinary = "00000010010001100100000100000001100010001000000000000000000100000001001000000000010100000111001000000000000000000000000000000000"

    @Test
    fun testCreate() {
        val bloomFilter = BloomFilter(100, 10)
        bloomFilter.add("ca29c211f1c72d5b6622268ff43d2288ea2b2cb5b9aa196ff9f1704fc914b71b")
        bloomFilter.add("460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c")

        assertEquals(testEncoded, bloomFilter.encode())
        assertEquals(testInBinary, bloomFilter.print())

        assertTrue(bloomFilter.mightContains("ca29c211f1c72d5b6622268ff43d2288ea2b2cb5b9aa196ff9f1704fc914b71b"))
        assertTrue(bloomFilter.mightContains("460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"))

        assertFalse(bloomFilter.mightContains("560c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"))
    }

    @Test
    fun testDecoding() {
        val bloomFilter = BloomFilter.decode(testEncoded)

        assertEquals(testEncoded, bloomFilter.encode())
        assertEquals(testInBinary, bloomFilter.print())

        assertTrue(bloomFilter.mightContains("ca29c211f1c72d5b6622268ff43d2288ea2b2cb5b9aa196ff9f1704fc914b71b"))
        assertTrue(bloomFilter.mightContains("460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"))

        assertFalse(bloomFilter.mightContains("560c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"))
    }

    @Test
    fun runProb() {
        val bloomFilter = BloomFilter.decode(testEncoded)

        var failureCounter = 0
        for (seed in 0..10000000) {
            if (bloomFilter.mightContains(CryptoUtils.pubkeyCreate(CryptoUtils.privkeyCreate()))) {
                failureCounter++
            }
        }
        assertEquals(0, failureCounter)
    }
}
