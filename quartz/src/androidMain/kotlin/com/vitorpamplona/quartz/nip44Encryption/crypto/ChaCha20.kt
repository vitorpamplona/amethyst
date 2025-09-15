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
package com.vitorpamplona.quartz.nip44Encryption.crypto

import com.vitorpamplona.quartz.utils.LibSodiumInstance
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec

/**
 * Encapsulates the ChaCha20 options. LibSodium is faster on real hardware: 851ns vs 2,535ns encrypt/decrypt times
 */
class ChaCha20 {
    fun encrypt(
        message: ByteArray,
        nonce: ByteArray,
        key: ByteArray,
    ) = encryptLibSodium(message, nonce, key)

    fun decrypt(
        message: ByteArray,
        nonce: ByteArray,
        key: ByteArray,
    ) = decryptLibSodium(message, nonce, key)

    fun encryptNative(
        message: ByteArray,
        nonce: ByteArray,
        key: ByteArray,
    ): ByteArray {
        val cipher = Cipher.getInstance("ChaCha20")
        cipher.init(Cipher.ENCRYPT_MODE, FixedKey(key, "ChaCha20"), IvParameterSpec(nonce))
        return cipher.doFinal(message)
    }

    fun decryptNative(
        message: ByteArray,
        nonce: ByteArray,
        key: ByteArray,
    ): ByteArray {
        val cipher = Cipher.getInstance("ChaCha20")
        cipher.init(Cipher.DECRYPT_MODE, FixedKey(key, "ChaCha20"), IvParameterSpec(nonce))
        return cipher.doFinal(message)
    }

    fun encryptLibSodium(
        message: ByteArray,
        nonce: ByteArray,
        key: ByteArray,
    ) = LibSodiumInstance.cryptoStreamChaCha20IetfXor(message, nonce, key)

    fun decryptLibSodium(
        message: ByteArray,
        nonce: ByteArray,
        key: ByteArray,
    ) = LibSodiumInstance.cryptoStreamChaCha20IetfXor(message, nonce, key)
}
