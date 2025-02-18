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
package com.vitorpamplona.quartz.nip04Dm.crypto

import com.vitorpamplona.quartz.nip17Dm.files.encryption.NostrCipher
import com.vitorpamplona.quartz.utils.RandomInstance
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class AESCBC(
    val keyBytes: ByteArray = RandomInstance.bytes(32),
    val iv: ByteArray = RandomInstance.bytes(16),
) : NostrCipher {
    private fun newCipher() = Cipher.getInstance("AES/CBC/PKCS5Padding")

    private fun keySpec() = SecretKeySpec(keyBytes, "AES")

    private fun param() = IvParameterSpec(iv)

    override fun name() = NAME

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
        const val NAME = "aes-cbc"
    }
}
