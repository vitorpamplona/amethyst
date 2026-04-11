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

object Secp256k1C {
    private var loaded = false

    fun ensureLoaded() {
        if (!loaded) {
            System.loadLibrary("secp256k1_amethyst_jni")
            nativeInit()
            loaded = true
        }
    }

    @JvmStatic external fun nativeInit()

    @JvmStatic external fun nativePubkeyCreate(seckey: ByteArray): ByteArray?

    @JvmStatic external fun nativePubkeyCompress(pubkey: ByteArray): ByteArray?

    @JvmStatic external fun nativeSecKeyVerify(seckey: ByteArray): Boolean

    @JvmStatic external fun nativeSchnorrSign(
        msg: ByteArray,
        seckey: ByteArray,
        auxrand: ByteArray?,
    ): ByteArray?

    @JvmStatic external fun nativeSchnorrSignXOnly(
        msg: ByteArray,
        seckey: ByteArray,
        xonlyPub: ByteArray,
        auxrand: ByteArray?,
    ): ByteArray?

    @JvmStatic external fun nativeSchnorrVerify(
        sig: ByteArray,
        msg: ByteArray,
        pub: ByteArray,
    ): Boolean

    @JvmStatic external fun nativeSchnorrVerifyFast(
        sig: ByteArray,
        msg: ByteArray,
        pub: ByteArray,
    ): Boolean

    @JvmStatic external fun nativeSchnorrVerifyBatch(
        pub: ByteArray,
        sigs: Array<ByteArray>,
        msgs: Array<ByteArray>,
    ): Boolean

    @JvmStatic external fun nativePrivKeyTweakAdd(
        seckey: ByteArray,
        tweak: ByteArray,
    ): ByteArray?

    @JvmStatic external fun nativePubKeyTweakMul(
        pubkey: ByteArray,
        tweak: ByteArray,
    ): ByteArray?

    @JvmStatic external fun nativeEcdhXOnly(
        xonlyPub: ByteArray,
        scalar: ByteArray,
    ): ByteArray?
}
