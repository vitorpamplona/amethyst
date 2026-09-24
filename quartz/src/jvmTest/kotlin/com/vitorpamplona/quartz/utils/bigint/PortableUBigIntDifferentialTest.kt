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
package com.vitorpamplona.quartz.utils.bigint

import java.math.BigInteger
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [PortableUBigInt] against `java.math.BigInteger`, on random inputs, operation by
 * operation.
 *
 * This is the whole safety argument for the second implementation. JVM and
 * Android alias the platform's; Apple and Linux run this one. What it computes becomes a decryption key
 * (`CYBERSPACE_V2.md` §7.2), so a carry dropped in one limb is not a wrong
 * number, it is an object that will not open — and it would open everywhere
 * else, because everyone else is running the reference. A hand-written example
 * suite cannot cover a carry chain; a few thousand random pairs across the
 * sizes the Cantor tree actually reaches can, and the oracle is the
 * implementation the rest of the world's JVMs already agree with.
 *
 * The sizes are chosen to straddle the Karatsuba threshold in both directions
 * and to include the degenerate ends, because that boundary is where a
 * split-and-recombine bug lives.
 */
class PortableUBigIntDifferentialTest {
    private val random = Random(20260923)

    private fun PortableUBigInt.toBig(): BigInteger = BigInteger(1, toMinimalBytes())

    private fun randomOf(bits: Int): Pair<PortableUBigInt, BigInteger> {
        if (bits == 0) return PortableUBigInt.ZERO to BigInteger.ZERO
        val bytes = ByteArray((bits + 7) / 8)
        random.nextBytes(bytes)
        // Force the top bit so the value really is this wide, which is also the
        // case where BigInteger's own toByteArray grows a sign byte.
        bytes[0] = (bytes[0].toInt() or 0x80).toByte()
        val mine = PortableUBigInt.ofBytes(bytes)
        return mine to BigInteger(1, bytes)
    }

    /** Sizes around the split threshold, plus the degenerate ends. */
    private val widths = listOf(0, 1, 7, 8, 31, 32, 33, 64, 127, 128, 1_200, 1_280, 1_281, 4_096, 9_001)

    @Test
    fun addingAgrees() {
        for (a in widths) {
            for (b in widths) {
                repeat(4) {
                    val (mineA, theirsA) = randomOf(a)
                    val (mineB, theirsB) = randomOf(b)
                    assertEquals(theirsA.add(theirsB), (mineA + mineB).toBig(), "$a + $b")
                }
            }
        }
    }

    @Test
    fun multiplyingAgreesOnBothSidesOfTheKaratsubaThreshold() {
        for (a in widths) {
            for (b in widths) {
                repeat(4) {
                    val (mineA, theirsA) = randomOf(a)
                    val (mineB, theirsB) = randomOf(b)
                    assertEquals(theirsA.multiply(theirsB), (mineA * mineB).toBig(), "$a * $b")
                }
            }
        }
    }

    @Test
    fun multiplyingAgreesOnTheSizesACantorTreeReaches() {
        // A root at height h is about 86 * 2^h bits, so these are the operands
        // of the last few pairings at heights 12 to 16 — where Karatsuba
        // recurses several levels deep and a mis-split would show.
        for (bits in listOf(44_000, 88_000, 176_000, 352_000)) {
            val (mineA, theirsA) = randomOf(bits)
            val (mineB, theirsB) = randomOf(bits)
            assertEquals(theirsA.multiply(theirsB), (mineA * mineB).toBig(), "$bits bits squared")
        }
    }

    @Test
    fun shiftingRightAgrees() {
        for (bits in widths) {
            for (by in listOf(0, 1, 7, 31, 32, 33, 64, 1_000, 100_000)) {
                val (mine, theirs) = randomOf(bits)
                assertEquals(theirs.shiftRight(by), mine.shiftRight(by).toBig(), "$bits >> $by")
            }
        }
    }

    @Test
    fun comparingAndEqualityAgree() {
        for (a in widths) {
            for (b in widths) {
                repeat(4) {
                    val (mineA, theirsA) = randomOf(a)
                    val (mineB, theirsB) = randomOf(b)
                    assertEquals(theirsA.compareTo(theirsB), mineA.compareTo(mineB), "$a <=> $b")
                    assertEquals(theirsA == theirsB, mineA == mineB)
                }
            }
        }
    }

    @Test
    fun bitLengthAgrees() {
        for (bits in widths) {
            val (mine, theirs) = randomOf(bits)
            assertEquals(theirs.bitLength(), mine.bitLength, "$bits")
        }
    }

    @Test
    fun theMinimalBytesAreTheReferencesAndNotJavas() {
        // The reference's `int_to_bytes_be_min` is `n.to_bytes((bit_length + 7)
        // // 8, "big")`, with a single zero byte for zero. BigInteger's own
        // toByteArray is two's complement and prepends 0x00 whenever the top
        // bit is set, which is half of all numbers — and §7.2 hashes these
        // bytes, so the spare byte would be a different key.
        assertEquals(listOf<Byte>(0), PortableUBigInt.ZERO.toMinimalBytes().toList())
        assertEquals(listOf<Byte>(1), PortableUBigInt.ONE.toMinimalBytes().toList())
        assertEquals(listOf<Byte>(-1), PortableUBigInt.of(255).toMinimalBytes().toList())
        assertEquals(listOf<Byte>(1, 0), PortableUBigInt.of(256).toMinimalBytes().toList())

        for (bits in widths.filter { it > 0 }) {
            val (mine, theirs) = randomOf(bits)
            val mineBytes = mine.toMinimalBytes()
            // Same value, and never longer than the bit length demands.
            assertEquals(theirs, BigInteger(1, mineBytes), "$bits round trip")
            assertEquals((theirs.bitLength() + 7) / 8, mineBytes.size, "$bits has no spare byte")
            assertTrue(mineBytes[0] != 0.toByte(), "$bits leads with a significant byte")
        }
    }

    @Test
    fun theTwoActualsAgreeWithEachOther() {
        // The contract between the platforms, asserted rather than assumed: on
        // this JVM `UBigInt` is `java.math.BigInteger`, and on Apple and Linux
        // it is `PortableUBigInt`. A region key derived on a phone has to equal
        // one derived on a desktop, so the two must agree on every operation
        // and, above all, on the bytes that get hashed.
        for (a in widths) {
            for (b in widths) {
                val (mineA, theirsA) = randomOf(a)
                val (mineB, theirsB) = randomOf(b)
                val fastA = UBigInt.ofBytes(mineA.toMinimalBytes())
                val fastB = UBigInt.ofBytes(mineB.toMinimalBytes())

                assertEquals((mineA + mineB).toMinimalBytes().toList(), (fastA + fastB).toMinimalBytes().toList(), "$a + $b")
                assertEquals((mineA * mineB).toMinimalBytes().toList(), (fastA * fastB).toMinimalBytes().toList(), "$a * $b")
                assertEquals(mineA.shiftRight(33).toMinimalBytes().toList(), fastA.shiftRight(33).toMinimalBytes().toList(), "$a >> 33")
                assertEquals(mineA.compareTo(mineB), fastA.compareTo(fastB), "$a <=> $b")
                assertEquals(mineA.bitLength, fastA.bitLength, "$a bits")
                assertEquals(mineA.isZero, fastA.isZero, "$a zero")
                // And the JVM actual must have stripped the sign byte its
                // `toByteArray` grows, which is the one place aliasing would
                // have silently changed a key.
                assertEquals(theirsA.toString(16).trimStart('0').ifEmpty { "0" }, fastA.toMinimalBytes().toHex(), "$a bytes")
            }
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }.trimStart('0').ifEmpty { "0" }

    @Test
    fun longsAndBytesRoundTrip() {
        for (value in listOf(0L, 1L, 255L, 256L, Int.MAX_VALUE.toLong(), 1L shl 32, Long.MAX_VALUE)) {
            assertEquals(BigInteger.valueOf(value), PortableUBigInt.of(value).toBig(), "$value")
        }
        for (bits in widths) {
            val (mine, theirs) = randomOf(bits)
            assertEquals(theirs, PortableUBigInt.ofBytes(mine.toMinimalBytes()).toBig(), "$bits")
        }
    }
}
