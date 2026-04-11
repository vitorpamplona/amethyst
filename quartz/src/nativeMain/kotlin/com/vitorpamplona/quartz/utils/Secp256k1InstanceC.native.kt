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

actual object Secp256k1InstanceC {
    actual fun init() {
        // No-op on native — uses Kotlin implementation
    }

    actual fun compressedPubKeyFor(privKey: ByteArray): ByteArray = Secp256k1InstanceKotlin.compressedPubKeyFor(privKey)

    actual fun isPrivateKeyValid(il: ByteArray): Boolean = Secp256k1InstanceKotlin.isPrivateKeyValid(il)

    actual fun signSchnorr(
        data: ByteArray,
        privKey: ByteArray,
        nonce: ByteArray?,
    ): ByteArray = Secp256k1InstanceKotlin.signSchnorr(data, privKey, nonce)

    actual fun signSchnorrWithXOnlyPubKey(
        data: ByteArray,
        privKey: ByteArray,
        xOnlyPubKey: ByteArray,
        nonce: ByteArray?,
    ): ByteArray = Secp256k1InstanceKotlin.signSchnorrWithXOnlyPubKey(data, privKey, xOnlyPubKey, nonce)

    actual fun verifySchnorr(
        signature: ByteArray,
        hash: ByteArray,
        pubKey: ByteArray,
    ): Boolean = Secp256k1InstanceKotlin.verifySchnorr(signature, hash, pubKey)

    actual fun verifySchnorrFast(
        signature: ByteArray,
        hash: ByteArray,
        pubKey: ByteArray,
    ): Boolean = Secp256k1InstanceKotlin.verifySchnorrFast(signature, hash, pubKey)

    actual fun verifySchnorrBatch(
        pubKey: ByteArray,
        signatures: List<ByteArray>,
        messages: List<ByteArray>,
    ): Boolean = Secp256k1InstanceKotlin.verifySchnorrBatch(pubKey, signatures, messages)

    actual fun privateKeyAdd(
        first: ByteArray,
        second: ByteArray,
    ): ByteArray = Secp256k1InstanceKotlin.privateKeyAdd(first, second)

    actual fun pubKeyTweakMulCompact(
        pubKey: ByteArray,
        privateKey: ByteArray,
    ): ByteArray = Secp256k1InstanceKotlin.pubKeyTweakMulCompact(pubKey, privateKey)

    actual fun ecdhXOnly(
        xOnlyPub: ByteArray,
        scalar: ByteArray,
    ): ByteArray {
        val compressedPub = ByteArray(33)
        compressedPub[0] = 0x02
        xOnlyPub.copyInto(compressedPub, 1, 0, 32)
        val result = Secp256k1InstanceKotlin.pubKeyTweakMulCompact(xOnlyPub, scalar)
        return result
    }
}
