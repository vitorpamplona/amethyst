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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HllBuilderTest {
    /** Builds a 32-byte array (64 hex chars) with the given byte overrides. */
    private fun pubkey(vararg overrides: Pair<Int, Int>): ByteArray {
        val b = ByteArray(32)
        for ((i, v) in overrides) b[i] = v.toByte()
        return b
    }

    @Test
    fun registerIndexIsByteAtOffset() {
        val regs = ByteArray(HyperLogLog.NUM_REGISTERS)
        // offset 8: index = byte[8]=0x05 ; value = leadingZeros(from byte 9) + 1.
        // byte[9]=0x80 (1000_0000) -> 0 leading zero bits -> value 1.
        HyperLogLog.addPubKey(regs, pubkey(8 to 0x05, 9 to 0x80), offset = 8)
        assertEquals(1, regs[5].toInt() and 0xFF)
    }

    @Test
    fun valueCountsLeadingZeroBitsAcrossBytes() {
        val regs = ByteArray(HyperLogLog.NUM_REGISTERS)
        // index = byte[8]=0x00 -> register 0.
        // byte[9]=0x00 (8 zero bits) then byte[10]=0x40 (0100_0000 -> 1 leading zero)
        // => 8 + 1 = 9 leading zero bits, +1 => value 10.
        HyperLogLog.addPubKey(regs, pubkey(8 to 0x00, 10 to 0x40), offset = 8)
        assertEquals(10, regs[0].toInt() and 0xFF)
    }

    @Test
    fun keepsTheLargerValuePerRegister() {
        val regs = ByteArray(HyperLogLog.NUM_REGISTERS)
        // Both map to register 0x00; first yields a larger value than the second.
        HyperLogLog.addPubKey(regs, pubkey(8 to 0x00, 10 to 0x40), offset = 8) // value 10
        HyperLogLog.addPubKey(regs, pubkey(8 to 0x00, 9 to 0x80), offset = 8) // value 1
        assertEquals(10, regs[0].toInt() and 0xFF)
    }

    @Test
    fun builderProducesApproximateCountResultWithRegisters() {
        val builder = HllBuilder(offset = 8)
        builder.add(pubkey(8 to 0x01, 9 to 0x80))
        builder.add(pubkey(8 to 0x02, 9 to 0x80))

        val result = builder.toCountResult()
        assertTrue(result.approximate)
        assertEquals(HyperLogLog.NUM_REGISTERS, result.hll?.size)
        assertEquals(1, result.hll!![1].toInt() and 0xFF)
        assertEquals(1, result.hll!![2].toInt() and 0xFF)
    }

    @Test
    fun twoBuildersOverSameCorpusMergeToSameRegisters() {
        val a = HllBuilder(8).add(pubkey(8 to 0x01, 9 to 0x80)).build()
        val b = HllBuilder(8).add(pubkey(8 to 0x01, 9 to 0x80)).build()
        val merged = HyperLogLog.merge(listOf(a, b))
        // Idempotent: the same pubkey counted twice estimates as one element.
        assertTrue(merged.contentEquals(a))
    }

    @Test
    fun malformedHexIsIgnored() {
        val builder = HllBuilder(8)
        builder.add("not-hex")
        assertTrue(builder.build().all { it.toInt() == 0 })
    }
}
