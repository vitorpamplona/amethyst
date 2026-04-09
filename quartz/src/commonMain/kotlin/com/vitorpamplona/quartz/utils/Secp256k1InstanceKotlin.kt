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
package com.vitorpamplona.quartz.utils

import com.vitorpamplona.quartz.utils.secp256k1.Secp256k1

object Secp256k1InstanceKotlin {
    private val h02 = Hex.decode("02")

    fun compressedPubKeyFor(privKey: ByteArray) = Secp256k1.pubKeyCompress(Secp256k1.pubkeyCreate(privKey))

    fun isPrivateKeyValid(il: ByteArray): Boolean = Secp256k1.secKeyVerify(il)

    fun signSchnorr(
        data: ByteArray,
        privKey: ByteArray,
        nonce: ByteArray? = RandomInstance.bytes(32),
    ): ByteArray = Secp256k1.signSchnorr(data, privKey, nonce)

    fun signSchnorr(
        data: ByteArray,
        privKey: ByteArray,
    ): ByteArray = Secp256k1.signSchnorr(data, privKey, null)

    /** Fast signing with pre-computed compressed public key (skips G multiplication). */
    fun signSchnorrWithPubKey(
        data: ByteArray,
        privKey: ByteArray,
        compressedPubKey: ByteArray,
        nonce: ByteArray? = RandomInstance.bytes(32),
    ): ByteArray = Secp256k1.signSchnorrWithPubKey(data, privKey, compressedPubKey, nonce)

    /** Fast signing with pre-computed x-only public key — zero-copy, zero-allocation. */
    fun signSchnorrWithXOnlyPubKey(
        data: ByteArray,
        privKey: ByteArray,
        xOnlyPubKey: ByteArray,
        nonce: ByteArray? = RandomInstance.bytes(32),
    ): ByteArray = Secp256k1.signSchnorrWithXOnlyPubKey(data, privKey, xOnlyPubKey, nonce)

    fun verifySchnorr(
        signature: ByteArray,
        hash: ByteArray,
        pubKey: ByteArray,
    ): Boolean = Secp256k1.verifySchnorr(signature, hash, pubKey)

    /**
     * Fast Nostr verify — skips BIP-340 y-parity inversion (~15% faster).
     * Safe for Nostr event verification. See Secp256k1.verifySchnorrFast KDoc.
     */
    fun verifySchnorrFast(
        signature: ByteArray,
        hash: ByteArray,
        pubKey: ByteArray,
    ): Boolean = Secp256k1.verifySchnorrFast(signature, hash, pubKey)

    /**
     * Batch-verify multiple signatures from the same pubkey.
     * Uses scalar+point summation — one mulDoubleG for the whole batch.
     */
    fun verifySchnorrBatch(
        pubKey: ByteArray,
        signatures: List<ByteArray>,
        messages: List<ByteArray>,
    ): Boolean = Secp256k1.verifySchnorrBatch(pubKey, signatures, messages)

    fun privateKeyAdd(
        first: ByteArray,
        second: ByteArray,
    ): ByteArray = Secp256k1.privKeyTweakAdd(first, second)

    fun pubKeyTweakMulCompact(
        pubKey: ByteArray,
        privateKey: ByteArray,
    ): ByteArray = Secp256k1.ecdhXOnly(pubKey, privateKey)
}
