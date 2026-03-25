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
package com.vitorpamplona.quartz.nip45Count

import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HyperLogLogTest {
    @Test
    fun testEmptyRegistersEstimateZero() {
        val registers = ByteArray(256)
        val estimate = HyperLogLog.estimate(registers)
        assertEquals(0L, estimate)
    }

    @Test
    fun testEncodeDecodeRoundTrip() {
        val registers = ByteArray(256) { (it % 10).toByte() }
        val hex = HyperLogLog.encode(registers)
        assertEquals(512, hex.length)

        val decoded = HyperLogLog.decode(hex)
        assertNotNull(decoded)
        assertTrue(registers.contentEquals(decoded))
    }

    @Test
    fun testDecodeInvalidHex() {
        assertNull(HyperLogLog.decode("too_short"))
        assertNull(HyperLogLog.decode(""))
    }

    @Test
    fun testMergeSelectsMaxPerRegister() {
        val hll1 = ByteArray(256) { 1 }
        val hll2 = ByteArray(256) { 2 }
        val hll3 = ByteArray(256) { 0 }

        // Set some specific registers higher in hll1
        hll1[0] = 5
        hll1[100] = 10

        val merged = HyperLogLog.merge(listOf(hll1, hll2, hll3))

        // Register 0: max(5, 2, 0) = 5
        assertEquals(5, merged[0].toInt() and 0xFF)
        // Register 1: max(1, 2, 0) = 2
        assertEquals(2, merged[1].toInt() and 0xFF)
        // Register 100: max(10, 2, 0) = 10
        assertEquals(10, merged[100].toInt() and 0xFF)
    }

    @Test
    fun testMergeSingleHll() {
        val hll = ByteArray(256) { (it % 5).toByte() }
        val merged = HyperLogLog.merge(listOf(hll))
        assertTrue(hll.contentEquals(merged))
    }

    @Test
    fun testMergeEmptyList() {
        val merged = HyperLogLog.merge(emptyList())
        assertEquals(256, merged.size)
        assertTrue(merged.all { it == 0.toByte() })
    }

    @Test
    fun testEstimateNonZeroRegisters() {
        // Set all registers to a uniform value > 0
        val registers = ByteArray(256) { 3 }
        val estimate = HyperLogLog.estimate(registers)
        // With all registers at 3, the estimate should be positive
        assertTrue(estimate > 0)
    }

    @Test
    fun testEstimateIncreaseWithHigherValues() {
        val lowRegisters = ByteArray(256) { 2 }
        val highRegisters = ByteArray(256) { 5 }

        val lowEstimate = HyperLogLog.estimate(lowRegisters)
        val highEstimate = HyperLogLog.estimate(highRegisters)

        assertTrue(highEstimate > lowEstimate)
    }

    @Test
    fun testComputeOffsetWithEventIdFilter() {
        // Event ID hex: the char at position 32 determines the offset
        val eventId = "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789"
        val filter = Filter(tags = mapOf("e" to listOf(eventId)))
        val offset = HyperLogLog.computeOffset(filter)

        // char at position 32 is 'a' = 10, offset = 10 + 8 = 18
        assertNotNull(offset)
        assertEquals(18, offset)
    }

    @Test
    fun testComputeOffsetWithPubkeyFilter() {
        val pubkey = "0000000000000000000000000000000000000000000000000000000000000000"
        val filter = Filter(tags = mapOf("p" to listOf(pubkey)))
        val offset = HyperLogLog.computeOffset(filter)

        // char at position 32 is '0' = 0, offset = 0 + 8 = 8
        assertNotNull(offset)
        assertEquals(8, offset)
    }

    @Test
    fun testComputeOffsetWithNoTags() {
        val filter = Filter(kinds = listOf(1))
        val offset = HyperLogLog.computeOffset(filter)
        assertNull(offset)
    }

    @Test
    fun testComputeOffsetWithEmptyTags() {
        val filter = Filter(tags = mapOf("e" to emptyList()))
        val offset = HyperLogLog.computeOffset(filter)
        assertNull(offset)
    }

    @Test
    fun testComputeOffsetWithNonHexValue() {
        // Non-hex value should be SHA-256 hashed
        val filter = Filter(tags = mapOf("t" to listOf("nostr")))
        val offset = HyperLogLog.computeOffset(filter)
        assertNotNull(offset)
        assertTrue(offset in 8..23)
    }

    @Test
    fun testComputeOffsetDeterministic() {
        val filter = Filter(tags = mapOf("e" to listOf("abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789")))
        val offset1 = HyperLogLog.computeOffset(filter)
        val offset2 = HyperLogLog.computeOffset(filter)
        assertEquals(offset1, offset2)
    }
}
