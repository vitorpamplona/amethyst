/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.quartz.nip17Dm

import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.nip01Core.toHexKey
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class AESGCM(
    val keyBytes: ByteArray = CryptoUtils.random(32),
    val nonce: ByteArray = CryptoUtils.random(16),
) : NostrCipher {
    private fun newCipher() = Cipher.getInstance("AES/GCM/NoPadding")

    private fun keySpec() = SecretKeySpec(keyBytes, "AES")

    private fun param() = GCMParameterSpec(128, nonce)

    override fun name() = NAME

    fun copyUsingUTF8Nonce(): AESGCM =
        AESGCM(
            keyBytes,
            nonce.toHexKey().toByteArray(Charsets.UTF_8),
        )

    override fun encrypt(bytesToEncrypt: ByteArray): ByteArray =
        with(newCipher()) {
            init(Cipher.ENCRYPT_MODE, keySpec(), param())
            doFinal(bytesToEncrypt)
        }

    override fun decrypt(bytesToDecrypt: ByteArray): ByteArray =
        with(newCipher()) {
            init(Cipher.DECRYPT_MODE, keySpec(), param())
            doFinal(bytesToDecrypt)
        }

    companion object {
        const val NAME = "aes-gcm"
    }
}
