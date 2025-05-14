/**
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

import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid

object LibSodiumInstance {
    private val libSodium = SodiumAndroid()
    private val lazySodium = LazySodiumAndroid(libSodium)

    fun cryptoAeadXChaCha20Poly1305IetfDecrypt(
        message: ByteArray,
        nSec: ByteArray,
        ciphertext: ByteArray,
        ad: ByteArray,
        nPub: ByteArray,
        k: ByteArray,
    ): Boolean =
        lazySodium.cryptoAeadXChaCha20Poly1305IetfDecrypt(
            message,
            longArrayOf(message.size.toLong()),
            nSec,
            ciphertext,
            ciphertext.size.toLong(),
            ad,
            ad.size.toLong(),
            nPub,
            k,
        )

    fun cryptoAeadXChaCha20Poly1305IetfEncrypt(
        ciphertext: ByteArray,
        message: ByteArray,
        ad: ByteArray,
        nSec: ByteArray,
        nPub: ByteArray,
        k: ByteArray,
    ): Boolean =
        lazySodium.cryptoAeadXChaCha20Poly1305IetfEncrypt(
            ciphertext,
            longArrayOf(ciphertext.size.toLong()),
            message,
            message.size.toLong(),
            ad,
            ad.size.toLong(),
            nSec,
            nPub,
            k,
        )

    fun cryptoStreamChaCha20IetfXor(
        message: ByteArray,
        nonce: ByteArray?,
        key: ByteArray?,
    ): ByteArray {
        val ciphertext = ByteArray(message.size)
        lazySodium.cryptoStreamChaCha20IetfXor(ciphertext, message, message.size.toLong(), nonce, key)
        return ciphertext
    }

    // This function wasn't available in the bindings library. I had to move them here from C
    fun cryptoStreamXChaCha20Xor(
        messageBytes: ByteArray,
        nonce: ByteArray,
        key: ByteArray,
    ): ByteArray? {
        val cipher = ByteArray(messageBytes.size)
        val k2 = ByteArray(32)

        val nonceChaCha = nonce.drop(16).toByteArray()
        assert(nonceChaCha.size == 8)

        libSodium.crypto_core_hchacha20(k2, nonce, key, null)
        val resultCode =
            libSodium.crypto_stream_chacha20_xor_ic(
                cipher,
                messageBytes,
                messageBytes.size.toLong(),
                nonceChaCha,
                0,
                k2,
            )

        return if (resultCode == 0) cipher else null
    }
}
