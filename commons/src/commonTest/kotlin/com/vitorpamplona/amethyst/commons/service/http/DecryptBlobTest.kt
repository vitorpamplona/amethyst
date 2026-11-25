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
package com.vitorpamplona.amethyst.commons.service.http

import com.vitorpamplona.crypto.aes.AESGCM
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNull

class DecryptBlobTest {
    private val key = ByteArray(32) { it.toByte() }
    private val nonce = ByteArray(12) { (it * 7).toByte() }
    private val plain = "an encrypted voice note".encodeToByteArray()

    @Test
    fun opensABlobEncryptedTheStandardWay() {
        val sealed = AESGCM(key, nonce).encrypt(plain)
        assertContentEquals(plain, DecryptInformation(AESGCM(key, nonce), "audio/mp4").decryptBlob(sealed))
    }

    @Test
    fun opensAnEarly0xChatBlobWhoseNonceWasItsHexText() {
        val sealed = AESGCM(key, nonce.toHexKey().encodeToByteArray()).encrypt(plain)
        assertContentEquals(plain, DecryptInformation(AESGCM(key, nonce), "image/jpeg").decryptBlob(sealed))
    }

    @Test
    fun aWrongKeyOpensNothing() {
        val sealed = AESGCM(key, nonce).encrypt(plain)
        assertNull(DecryptInformation(AESGCM(ByteArray(32), nonce), null).decryptBlob(sealed))
    }
}
