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
package com.vitorpamplona.quartz.nip17Dm

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.utils.ciphers.AESGCM
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AESGCMTest {
    val decryptionNonce = "01e77c94bd5aba3e3cbb69594e7ba07c"
    val decryptionKey = "c128ecffab90ee7810e3df08e7fb2cc39a8d40f24201f48b2b36e23b34ac50ee"

    val cipher =
        AESGCM(
            decryptionKey.hexToByteArray(),
            decryptionNonce.toByteArray(Charsets.UTF_8),
        )

    @Test
    fun encryptDecrypt() {
        val encrypted = cipher.encrypt("Testing".toByteArray(Charsets.UTF_8))
        val decrypted = cipher.decrypt(encrypted)

        assertEquals("Testing", String(decrypted))
    }

    @Test
    fun imageTest() {
        val image =
            javaClass.classLoader?.getResourceAsStream("ovxxk2vz.jpg")?.use {
                it.readAllBytes()
            }!!

        val decrypted = cipher.decrypt(image)

        assertEquals(44201, decrypted.size)
    }

    @Test
    fun videoTest2() {
        val myCipher =
            AESGCM(
                "373d19850ebc8ed5b0fefcca5cd6f27fde9cb6ac54fd32f6b4fad9d68ebe8ee0".hexToByteArray(),
                "95e67b6874784a54299b58b8990499bd".hexToByteArray(),
            )

        val encrypted =
            javaClass.classLoader?.getResourceAsStream("trouble_video")?.use {
                it.readAllBytes()
            }!!

        val decrypted = myCipher.decrypt(encrypted)

        assertEquals(1277122, decrypted.size)
    }
}
