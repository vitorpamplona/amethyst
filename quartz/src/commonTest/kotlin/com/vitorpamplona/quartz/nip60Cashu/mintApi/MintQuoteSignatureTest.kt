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
package com.vitorpamplona.quartz.nip60Cashu.mintApi

import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.utils.secp256k1.Secp256k1
import com.vitorpamplona.quartz.utils.sha256.sha256
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class MintQuoteSignatureTest {
    private val privkey =
        "0000000000000000000000000000000000000000000000000000000000001234".hexToByteArray()
    private val quoteId = "abcd1234-quote-id"

    @Test
    fun payloadIsQuoteIdThenOutputsConcatenated() {
        val msg =
            MintQuoteSignature.buildSigningPayload(
                quoteId = "Q",
                blindedMessageHexes = listOf("AA", "BB"),
            )
        // Expected: 'Q' || 'AA' || 'BB' = the literal ASCII bytes of these strings.
        assertEquals("QAABB", msg.decodeToString())
    }

    @Test
    fun emptyOutputListIsJustQuoteId() {
        val msg = MintQuoteSignature.buildSigningPayload(quoteId, emptyList())
        assertEquals(quoteId, msg.decodeToString())
    }

    @Test
    fun signatureIs64BytesAfterHexDecode() {
        val sig =
            MintQuoteSignature.sign(
                quoteId = quoteId,
                blindedMessageHexes = listOf("02abc1", "02def2"),
                signingPrivkey = privkey,
            )
        // 128 hex chars = 64 bytes (BIP-340 Schnorr).
        assertEquals(128, sig.length)
        assertEquals(64, sig.hexToByteArray().size)
    }

    @Test
    fun signatureVerifiesAgainstMatchingPubkey() {
        val outputs = listOf("02aabb", "02ccdd", "02eeff")
        val sig = MintQuoteSignature.sign(quoteId, outputs, privkey)
        val expectedMsg = MintQuoteSignature.buildSigningPayload(quoteId, outputs)
        val expectedHash = sha256(expectedMsg)
        val pubkey = Secp256k1.pubkeyCreate(privkey)
        // x-only pubkey for BIP-340. pubkeyCreate gives 65 bytes uncompressed;
        // x is bytes 1..33.
        val xOnly = pubkey.copyOfRange(1, 33)
        assertTrue(
            Secp256k1.verifySchnorr(sig.hexToByteArray(), expectedHash, xOnly),
            "signature must verify against derived x-only pubkey",
        )
    }

    @Test
    fun signatureChangesWhenOutputsChange() {
        val sig1 = MintQuoteSignature.sign(quoteId, listOf("02aa"), privkey)
        val sig2 = MintQuoteSignature.sign(quoteId, listOf("02bb"), privkey)
        assertNotEquals(sig1, sig2)
    }

    @Test
    fun signatureChangesWhenQuoteIdChanges() {
        val outputs = listOf("02aa", "02bb")
        val sig1 = MintQuoteSignature.sign("quote-a", outputs, privkey)
        val sig2 = MintQuoteSignature.sign("quote-b", outputs, privkey)
        assertNotEquals(sig1, sig2)
    }

    @Test
    fun signRejectsWrongKeyLength() {
        try {
            MintQuoteSignature.sign(quoteId, listOf("02aa"), ByteArray(31))
            error("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun convenienceOverloadMatchesPlainSign() {
        val outputs =
            listOf(
                BlindedMessageDto(amount = 1L, id = "00aa", bTick = "02aabb"),
                BlindedMessageDto(amount = 2L, id = "00aa", bTick = "02ccdd"),
            )
        val response =
            MintQuoteBolt11ResponseDto(
                quote = quoteId,
                request = "lnbc...",
                state = "PAID",
            )
        val viaDto = MintQuoteSignature.sign(response, outputs, privkey)
        val viaStrings = MintQuoteSignature.sign(quoteId, outputs.map { it.bTick }, privkey)
        assertEquals(viaStrings, viaDto)
    }
}
