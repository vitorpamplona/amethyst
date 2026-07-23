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
package com.vitorpamplona.quartz.nipXXBolt12Zaps.bolt12

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Bolt12Bech32Test {
    @Test
    fun canonicalizeStripsContinuationsAndWhitespaceAndLowercases() {
        assertEquals("lno1abcdef", Bolt12Bech32.canonicalize("LNO1ABC+ DEF"))
        assertEquals("lno1abcdef", Bolt12Bech32.canonicalize("lno1abc+\n  def"))
        assertEquals("lno1abcdef", Bolt12Bech32.canonicalize("lno1abc + def"))
        assertEquals("lno1abcdef", Bolt12Bech32.canonicalize("  lno1abcdef  "))
    }

    @Test
    fun roundTripsArbitraryBytesWithoutChecksumOrLengthCap() {
        // 200 bytes — far beyond the BIP-173 90-char cap that plain bech32 enforces.
        val bytes = ByteArray(200) { (it * 7 + 3).toByte() }
        val encoded = Bolt12Bech32.encode(Bolt12Bech32.OFFER_HRP, bytes)
        assertTrue(encoded.startsWith("lno1"))
        assertContentEquals(bytes, Bolt12Bech32.decodeToBytes(encoded, Bolt12Bech32.OFFER_HRP))
    }

    @Test
    fun recognizesOfferAndProofPrefixes() {
        val offer = Bolt12Bech32.encode(Bolt12Bech32.OFFER_HRP, byteArrayOf(1, 2, 3, 4))
        val proof = Bolt12Bech32.encode(Bolt12Bech32.PAYER_PROOF_HRP, byteArrayOf(1, 2, 3, 4))

        assertTrue(Bolt12Bech32.isOffer(offer))
        assertFalse(Bolt12Bech32.isPayerProof(offer))
        assertTrue(Bolt12Bech32.isPayerProof(proof))
        assertFalse(Bolt12Bech32.isOffer(proof))

        // Non-BOLT12 or malformed strings are rejected.
        assertFalse(Bolt12Bech32.isOffer("not an offer"))
        assertFalse(Bolt12Bech32.isOffer("lno1"))
        assertFalse(Bolt12Bech32.isPayerProof("lnbc1abc"))
    }

    @Test
    fun decodingWithMismatchedPrefixFails() {
        val offer = Bolt12Bech32.encode(Bolt12Bech32.OFFER_HRP, byteArrayOf(9, 9, 9))
        assertFailsWith<IllegalArgumentException> {
            Bolt12Bech32.decodeToBytes(offer, Bolt12Bech32.PAYER_PROOF_HRP)
        }
        assertEquals(null, Bolt12Bech32.decodeToBytesOrNull("garbage", Bolt12Bech32.OFFER_HRP))
    }
}
