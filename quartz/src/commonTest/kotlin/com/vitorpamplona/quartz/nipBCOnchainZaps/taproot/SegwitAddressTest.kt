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
package com.vitorpamplona.quartz.nipBCOnchainZaps.taproot

import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Tests for the segwit (BIP-173 / BIP-350) address encoder.
 *
 * Vectors come from BIP-350 and BIP-341.
 */
class SegwitAddressTest {
    // ============================================================
    // BIP-350 / BIP-173 known-good vectors
    // ============================================================

    @Test
    fun encodeMainnetWitnessV0_20Byte() {
        // bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4
        val program = "751e76e8199196d454941c45d1b3a323f1433bd6".hexToByteArray()
        val address = SegwitAddress.encode("bc", 0, program)
        assertEquals("bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4", address)
    }

    @Test
    fun encodeMainnetWitnessV16_2Byte() {
        // BIP-350 short address: BC1SW50QGDZ25J (lowercased)
        val program = "751e".hexToByteArray()
        val address = SegwitAddress.encode("bc", 16, program)
        assertEquals("bc1sw50qgdz25j", address)
    }

    @Test
    fun encodedTaprootIsLowercaseAndCorrectLength() {
        // P2TR encoded address must be 62 chars, lowercase, with 'bc1p' prefix.
        val outputKey = "53a1f6e454df1aa2776a2814a721372d6258050de330b3c6d10ee8f4e0dda343".hexToByteArray()
        val address = SegwitAddress.encodeP2TR(outputKey)
        assertEquals(62, address.length)
        assertEquals(address, address.lowercase())
        assertEquals("bc1p", address.substring(0, 4))
    }

    // ============================================================
    // Round-trip
    // ============================================================

    @Test
    fun roundTripTaproot() {
        val outputKey = "53a1f6e454df1aa2776a2814a721372d6258050de330b3c6d10ee8f4e0dda343".hexToByteArray()
        val address = SegwitAddress.encodeP2TR(outputKey)
        val decoded = SegwitAddress.decode(address)
        assertEquals("bc", decoded.hrp)
        assertEquals(1, decoded.witnessVersion)
        assertEquals(outputKey.toHexKey(), decoded.program.toHexKey())
    }

    @Test
    fun roundTripV0_20Byte() {
        val program = "751e76e8199196d454941c45d1b3a323f1433bd6".hexToByteArray()
        val address = SegwitAddress.encode("bc", 0, program)
        val decoded = SegwitAddress.decode(address)
        assertEquals(0, decoded.witnessVersion)
        assertEquals(program.toHexKey(), decoded.program.toHexKey())
    }

    // ============================================================
    // Validation
    // ============================================================

    @Test
    fun rejectsTaprootProgramOfWrongLength() {
        val tooShort = ByteArray(31)
        assertFailsWith<IllegalArgumentException> {
            SegwitAddress.encodeP2TR(tooShort)
        }
    }

    @Test
    fun rejectsInvalidWitnessVersion() {
        assertFailsWith<IllegalArgumentException> {
            SegwitAddress.encode("bc", 17, ByteArray(32))
        }
    }

    @Test
    fun rejectsV0WrongProgramLength() {
        assertFailsWith<IllegalArgumentException> {
            // v0 must be 20 or 32 bytes — 21 is invalid.
            SegwitAddress.encode("bc", 0, ByteArray(21))
        }
    }
}
