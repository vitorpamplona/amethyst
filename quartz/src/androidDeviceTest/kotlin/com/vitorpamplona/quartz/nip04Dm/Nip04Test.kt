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
package com.vitorpamplona.quartz.nip04Dm

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitorpamplona.quartz.nip01Core.crypto.Nip01Crypto
import com.vitorpamplona.quartz.nip04Dm.crypto.Nip04
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Nip04Test {
    @Test
    fun encryptDecryptNIP4Test() {
        val msg = "Hi"

        val privateKey = Nip01Crypto.privKeyCreate()
        val publicKey = Nip01Crypto.pubKeyCreate(privateKey)

        val encrypted = Nip04.encrypt(msg, privateKey, publicKey)
        val decrypted = Nip04.decrypt(encrypted, privateKey, publicKey)

        assertEquals(msg, decrypted)
    }

    @Test
    fun encryptSharedSecretDecryptNIP4Test() {
        val msg = "Hi"

        val privateKey = Nip01Crypto.privKeyCreate()
        val publicKey = Nip01Crypto.pubKeyCreate(privateKey)

        val encrypted = Nip04.encrypt(msg, privateKey, publicKey)
        val decrypted = Nip04.decrypt(encrypted, privateKey, publicKey)

        assertEquals(msg, decrypted)
    }
}
