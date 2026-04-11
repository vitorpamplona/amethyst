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
    private var loaded = false

    private fun ensureLoaded() {
        if (!loaded) {
            System.loadLibrary("secp256k1_amethyst_jni")
            nativeInit()
            loaded = true
        }
    }

    private external fun nativeInit()

    private external fun nativePubkeyCreate(seckey: ByteArray): ByteArray?

    private external fun nativePubkeyCompress(pubkey: ByteArray): ByteArray?

    private external fun nativeSecKeyVerify(seckey: ByteArray): Boolean

    private external fun nativeSchnorrSign(
        msg: ByteArray,
        seckey: ByteArray,
        auxrand: ByteArray?,
    ): ByteArray?

    private external fun nativeSchnorrSignXOnly(
        msg: ByteArray,
        seckey: ByteArray,
        xonlyPub: ByteArray,
        auxrand: ByteArray?,
    ): ByteArray?

    private external fun nativeSchnorrVerify(
        sig: ByteArray,
        msg: ByteArray,
        pub: ByteArray,
    ): Boolean

    private external fun nativeSchnorrVerifyFast(
        sig: ByteArray,
        msg: ByteArray,
        pub: ByteArray,
    ): Boolean

    private external fun nativeSchnorrVerifyBatch(
        pub: ByteArray,
        sigs: Array<ByteArray>,
        msgs: Array<ByteArray>,
    ): Boolean

    private external fun nativePrivKeyTweakAdd(
        seckey: ByteArray,
        tweak: ByteArray,
    ): ByteArray?

    private external fun nativePubKeyTweakMul(
        pubkey: ByteArray,
        tweak: ByteArray,
    ): ByteArray?

    private external fun nativeEcdhXOnly(
        xonlyPub: ByteArray,
        scalar: ByteArray,
    ): ByteArray?

    actual fun init() = ensureLoaded()

    actual fun compressedPubKeyFor(privKey: ByteArray): ByteArray {
        ensureLoaded()
        val pub65 = nativePubkeyCreate(privKey) ?: error("Invalid private key")
        return nativePubkeyCompress(pub65) ?: error("Compression failed")
    }

    actual fun isPrivateKeyValid(il: ByteArray): Boolean {
        ensureLoaded()
        return nativeSecKeyVerify(il)
    }

    actual fun signSchnorr(
        data: ByteArray,
        privKey: ByteArray,
        nonce: ByteArray?,
    ): ByteArray {
        ensureLoaded()
        return nativeSchnorrSign(data, privKey, nonce) ?: error("Sign failed")
    }

    actual fun signSchnorrWithXOnlyPubKey(
        data: ByteArray,
        privKey: ByteArray,
        xOnlyPubKey: ByteArray,
        nonce: ByteArray?,
    ): ByteArray {
        ensureLoaded()
        return nativeSchnorrSignXOnly(data, privKey, xOnlyPubKey, nonce) ?: error("Sign failed")
    }

    actual fun verifySchnorr(
        signature: ByteArray,
        hash: ByteArray,
        pubKey: ByteArray,
    ): Boolean {
        ensureLoaded()
        return nativeSchnorrVerify(signature, hash, pubKey)
    }

    actual fun verifySchnorrFast(
        signature: ByteArray,
        hash: ByteArray,
        pubKey: ByteArray,
    ): Boolean {
        ensureLoaded()
        return nativeSchnorrVerifyFast(signature, hash, pubKey)
    }

    actual fun verifySchnorrBatch(
        pubKey: ByteArray,
        signatures: List<ByteArray>,
        messages: List<ByteArray>,
    ): Boolean {
        ensureLoaded()
        return nativeSchnorrVerifyBatch(
            pubKey,
            signatures.toTypedArray(),
            messages.toTypedArray(),
        )
    }

    actual fun privateKeyAdd(
        first: ByteArray,
        second: ByteArray,
    ): ByteArray {
        ensureLoaded()
        return nativePrivKeyTweakAdd(first, second) ?: error("Tweak add failed")
    }

    actual fun pubKeyTweakMulCompact(
        pubKey: ByteArray,
        privateKey: ByteArray,
    ): ByteArray {
        ensureLoaded()
        val compressedPub = ByteArray(33)
        compressedPub[0] = 0x02
        pubKey.copyInto(compressedPub, 1, 0, 32)
        val result = nativePubKeyTweakMul(compressedPub, privateKey) ?: error("Tweak mul failed")
        return result.copyOfRange(1, 33)
    }

    actual fun ecdhXOnly(
        xOnlyPub: ByteArray,
        scalar: ByteArray,
    ): ByteArray {
        ensureLoaded()
        return nativeEcdhXOnly(xOnlyPub, scalar) ?: error("ECDH failed")
    }
}
