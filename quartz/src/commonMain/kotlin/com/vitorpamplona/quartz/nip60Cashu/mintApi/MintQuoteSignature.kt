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

import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.utils.secp256k1.Secp256k1
import com.vitorpamplona.quartz.utils.sha256.sha256

/**
 * NUT-20 mint-quote signing.
 *
 * When the wallet asks for a mint quote with a [MintQuoteBolt11RequestDto.pubkey]
 * set, the mint binds the quote to that pubkey and refuses to issue blind
 * signatures unless the matching mint request carries a Schnorr signature
 * over a deterministic payload of (quote_id, outputs).
 *
 * Payload: concatenate the UTF-8 bytes of the quote id with the UTF-8
 * bytes of every output's `B_` hex string, in the order they appear in the
 * mint request. SHA-256 the result. Sign with BIP-340 Schnorr.
 *
 * The wallet's pubkey is the 33-byte compressed form (hex) of the
 * private key it'll use to sign — the same as the `pubkey` field on the
 * quote request.
 *
 * Threat model: NUT-20 prevents a third party who can observe the quote
 * id (e.g. via a relay log, app screenshot, or a malicious sibling
 * client) from front-running the wallet by submitting their own
 * `/v1/mint/bolt11` first and stealing the proofs. Without NUT-20 the
 * quote id alone is sufficient credentials.
 */
object MintQuoteSignature {
    /**
     * Sign the mint request for [quoteId] + [blindedMessageHexes].
     * Returns the 64-byte BIP-340 Schnorr signature as hex.
     */
    fun sign(
        quoteId: String,
        blindedMessageHexes: List<String>,
        signingPrivkey: ByteArray,
    ): String {
        require(signingPrivkey.size == 32) { "Signing key must be 32 bytes" }
        val msg = buildSigningPayload(quoteId, blindedMessageHexes)
        val hash = sha256(msg)
        return Secp256k1.signSchnorr(hash, signingPrivkey, auxrand = null).toHexKey()
    }

    /**
     * Convenience: sign the request given the full DTOs. The signing key
     * must correspond to the pubkey carried on the original
     * [MintQuoteBolt11RequestDto] — checking that contract is the caller's
     * responsibility (mints will reject otherwise).
     */
    fun sign(
        quoteResponse: MintQuoteBolt11ResponseDto,
        outputs: List<BlindedMessageDto>,
        signingPrivkey: ByteArray,
    ): String = sign(quoteResponse.quote, outputs.map { it.bTick }, signingPrivkey)

    /**
     * The pre-hash payload — exposed so tests can inspect it and the
     * mint operations layer can attach it to a kind:7376 audit row if
     * we ever want post-hoc proof of what was signed.
     */
    fun buildSigningPayload(
        quoteId: String,
        blindedMessageHexes: List<String>,
    ): ByteArray {
        val quoteBytes = quoteId.encodeToByteArray()
        val outputBytes = blindedMessageHexes.map { it.encodeToByteArray() }
        val total = quoteBytes.size + outputBytes.sumOf { it.size }
        val buf = ByteArray(total)
        var pos = 0
        quoteBytes.copyInto(buf, pos)
        pos += quoteBytes.size
        for (b in outputBytes) {
            b.copyInto(buf, pos)
            pos += b.size
        }
        return buf
    }
}
