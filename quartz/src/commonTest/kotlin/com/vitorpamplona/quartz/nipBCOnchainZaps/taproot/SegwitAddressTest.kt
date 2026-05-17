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
    fun encodesP2trAgainstBip341WalletTestVectors() {
        // All seven `scriptPubKey` entries from the BIP-341 wallet-test-vectors:
        // (tweaked output key, expected bip350Address).
        val vectors =
            listOf(
                "53a1f6e454df1aa2776a2814a721372d6258050de330b3c6d10ee8f4e0dda343" to
                    "bc1p2wsldez5mud2yam29q22wgfh9439spgduvct83k3pm50fcxa5dps59h4z5",
                "147c9c57132f6e7ecddba9800bb0c4449251c92a1e60371ee77557b6620f3ea3" to
                    "bc1pz37fc4cn9ah8anwm4xqqhvxygjf9rjf2resrw8h8w4tmvcs0863sa2e586",
                "e4d810fd50586274face62b8a807eb9719cef49c04177cc6b76a9a4251d5450e" to
                    "bc1punvppl2stp38f7kwv2u2spltjuvuaayuqsthe34hd2dyy5w4g58qqfuag5",
                "712447206d7a5238acc7ff53fbe94a3b64539ad291c7cdbc490b7577e4b17df5" to
                    "bc1pwyjywgrd0ffr3tx8laflh6228dj98xkjj8rum0zfpd6h0e930h6saqxrrm",
                "77e30a5522dd9f894c3f8b8bd4c4b2cf82ca7da8a3ea6a239655c39c050ab220" to
                    "bc1pwl3s54fzmk0cjnpl3w9af39je7pv5ldg504x5guk2hpecpg2kgsqaqstjq",
                "91b64d5324723a985170e4dc5a0f84c041804f2cd12660fa5dec09fc21783605" to
                    "bc1pjxmy65eywgafs5tsunw95ruycpqcqnev6ynxp7jaasylcgtcxczs6n332e",
                "75169f4001aa68f15bbed28b218df1d0a62cbbcf1188c6665110c293c907b831" to
                    "bc1pw5tf7sqp4f50zka7629jrr036znzew70zxyvvej3zrpf8jg8hqcssyuewe",
            )
        for ((outputKey, address) in vectors) {
            assertEquals(address, SegwitAddress.encodeP2TR(outputKey.hexToByteArray()))
            // And it must decode back to the same output key.
            assertEquals(outputKey, SegwitAddress.decode(address).program.toHexKey())
        }
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
