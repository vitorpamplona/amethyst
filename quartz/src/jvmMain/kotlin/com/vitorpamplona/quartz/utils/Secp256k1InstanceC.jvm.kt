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
        Secp256k1C.ensureLoaded()
    }

    actual fun compressedPubKeyFor(privKey: ByteArray): ByteArray {
        Secp256k1C.ensureLoaded()
        val pub65 = Secp256k1C.nativePubkeyCreate(privKey) ?: error("Invalid private key")
        return Secp256k1C.nativePubkeyCompress(pub65) ?: error("Compression failed")
    }

    actual fun isPrivateKeyValid(il: ByteArray): Boolean {
        Secp256k1C.ensureLoaded()
        return Secp256k1C.nativeSecKeyVerify(il)
    }

    actual fun signSchnorr(
        data: ByteArray,
        privKey: ByteArray,
        nonce: ByteArray?,
    ): ByteArray {
        Secp256k1C.ensureLoaded()
        return Secp256k1C.nativeSchnorrSign(data, privKey, nonce) ?: error("Sign failed")
    }

    actual fun signSchnorrWithXOnlyPubKey(
        data: ByteArray,
        privKey: ByteArray,
        xOnlyPubKey: ByteArray,
        nonce: ByteArray?,
    ): ByteArray {
        Secp256k1C.ensureLoaded()
        return Secp256k1C.nativeSchnorrSignXOnly(data, privKey, xOnlyPubKey, nonce) ?: error("Sign failed")
    }

    actual fun verifySchnorr(
        signature: ByteArray,
        hash: ByteArray,
        pubKey: ByteArray,
    ): Boolean {
        Secp256k1C.ensureLoaded()
        return Secp256k1C.nativeSchnorrVerify(signature, hash, pubKey)
    }

    actual fun verifySchnorrFast(
        signature: ByteArray,
        hash: ByteArray,
        pubKey: ByteArray,
    ): Boolean {
        Secp256k1C.ensureLoaded()
        return Secp256k1C.nativeSchnorrVerifyFast(signature, hash, pubKey)
    }

    actual fun verifySchnorrBatch(
        pubKey: ByteArray,
        signatures: List<ByteArray>,
        messages: List<ByteArray>,
    ): Boolean {
        Secp256k1C.ensureLoaded()
        return Secp256k1C.nativeSchnorrVerifyBatch(
            pubKey,
            signatures.toTypedArray(),
            messages.toTypedArray(),
        )
    }

    actual fun privateKeyAdd(
        first: ByteArray,
        second: ByteArray,
    ): ByteArray {
        Secp256k1C.ensureLoaded()
        return Secp256k1C.nativePrivKeyTweakAdd(first, second) ?: error("Tweak add failed")
    }

    actual fun pubKeyTweakMulCompact(
        pubKey: ByteArray,
        privateKey: ByteArray,
    ): ByteArray {
        Secp256k1C.ensureLoaded()
        val compressedPub = ByteArray(33)
        compressedPub[0] = 0x02
        pubKey.copyInto(compressedPub, 1, 0, 32)
        val result = Secp256k1C.nativePubKeyTweakMul(compressedPub, privateKey) ?: error("Tweak mul failed")
        return result.copyOfRange(1, 33)
    }

    actual fun ecdhXOnly(
        xOnlyPub: ByteArray,
        scalar: ByteArray,
    ): ByteArray {
        Secp256k1C.ensureLoaded()
        return Secp256k1C.nativeEcdhXOnly(xOnlyPub, scalar) ?: error("ECDH failed")
    }
}
