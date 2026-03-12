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

import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.refTo
import kotlin.experimental.ExperimentalNativeApi

actual object LibSodiumInstance {
    @OptIn(ExperimentalForeignApi::class)
    actual fun cryptoAeadXChaCha20Poly1305IetfDecrypt(
        message: ByteArray,
        nSec: ByteArray,
        ciphertext: ByteArray,
        ad: ByteArray,
        nPub: ByteArray,
        k: ByteArray,
    ): Boolean {
        val returnCode = Clibsodium.crypto_aead_xchacha20poly1305_ietf_decrypt(
            m = message.uRefTo(0),
            mlen_p = ulongArrayOf(message.size.toULong()).refTo(0),
            nsec = nSec.uRefTo(0),
            c = ciphertext.uRefTo(0),
            clen = ciphertext.size.toULong(),
            ad = ad.uRefTo(0),
            adlen = ad.size.toULong(),
            npub = nPub.uRefTo(0),
            k = k.uRefTo(0)
        )
        return returnCode == 0
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun cryptoAeadXChaCha20Poly1305IetfEncrypt(
        ciphertext: ByteArray,
        message: ByteArray,
        ad: ByteArray,
        nSec: ByteArray,
        nPub: ByteArray,
        k: ByteArray,
    ): Boolean {
        val retCode = Clibsodium.crypto_aead_xchacha20poly1305_ietf_encrypt(
            c = ciphertext.uRefTo(0),
            clen_p = ulongArrayOf(ciphertext.size.toULong()).refTo(0),
            m = message.uRefTo(0),
            mlen = message.size.toULong(),
            ad = ad.uRefTo(0),
            adlen = ad.size.toULong(),
            nsec = nSec.uRefTo(0),
            npub = nPub.uRefTo(0),
            k = k.uRefTo(0)
        )

        return retCode == 0
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun cryptoStreamChaCha20IetfXor(
        message: ByteArray,
        nonce: ByteArray?,
        key: ByteArray?,
    ): ByteArray {
        val ciphertext = ByteArray(message.size)
        Clibsodium.crypto_stream_chacha20_ietf_xor(
            c = ciphertext.uRefTo(0),
            m = message.uRefTo(0),
            mlen = message.size.toULong(),
            n = nonce?.uRefTo(0),
            k = key?.uRefTo(0)
        )
        return ciphertext
    }

    @OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)
    actual fun cryptoStreamXChaCha20Xor(
        messageBytes: ByteArray,
        nonce: ByteArray,
        key: ByteArray,
    ): ByteArray {
        val cipher = ByteArray(messageBytes.size)
        val k2 = ByteArray(32)

        val nonceChaCha = nonce.drop(16).toByteArray()
        assert(nonceChaCha.size == 8)

        Clibsodium.crypto_core_hchacha20(
            out = k2.uRefTo(0),
            nonce.uRefTo(0),
            k = key.uRefTo(0),
            c = null
        )

        val resultCode = Clibsodium.crypto_stream_chacha20_xor_ic(
            c = cipher.uRefTo(0),
            m = messageBytes.uRefTo(0),
            mlen = messageBytes.size.toULong(),
            n = nonceChaCha.uRefTo(0),
            ic = 0L.toULong(),
            k = k2.uRefTo(0)
        )
        return if (resultCode == 0) cipher else throw IllegalStateException("Could not decrypt message")
    }
}

@OptIn(ExperimentalForeignApi::class)
@Suppress("UNCHECKED_CAST")
private fun ByteArray.uRefTo(index: Int) = refTo(index) as CValuesRef<UByteVar>
