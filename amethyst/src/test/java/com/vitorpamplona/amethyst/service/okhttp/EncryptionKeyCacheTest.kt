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
package com.vitorpamplona.amethyst.service.okhttp

import com.vitorpamplona.quartz.utils.ciphers.NostrCipher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class EncryptionKeyCacheTest {
    @Test
    fun firstWriteWins() {
        val cache = EncryptionKeyCache()
        val first = FakeCipher(1)
        val second = FakeCipher(2)
        val url = "https://cdn.example/blob"

        cache.add(url, first, "image/png")
        cache.add(url, second, "video/mp4")

        val stored = cache.get(url)!!
        assertSame(first, stored.cipher)
        assertEquals("image/png", stored.mimeType)
    }

    @Test
    fun addSkipsNullUrl() {
        val cache = EncryptionKeyCache()
        cache.add(null, FakeCipher(1), "image/png")
        assertNull(cache.get("https://cdn.example/blob"))
    }

    @Test
    fun distinctUrlsStayIndependent() {
        val cache = EncryptionKeyCache()
        val a = FakeCipher(1)
        val b = FakeCipher(2)
        cache.add("https://cdn.example/a", a, "image/png")
        cache.add("https://cdn.example/b", b, "video/mp4")

        assertSame(a, cache.get("https://cdn.example/a")!!.cipher)
        assertSame(b, cache.get("https://cdn.example/b")!!.cipher)
    }

    private class FakeCipher(
        val id: Int,
    ) : NostrCipher {
        override fun name() = "fake-$id"

        override fun encrypt(bytesToEncrypt: ByteArray) = bytesToEncrypt

        override fun decrypt(bytesToDecrypt: ByteArray) = bytesToDecrypt

        override fun decryptOrNull(bytesToDecrypt: ByteArray) = bytesToDecrypt
    }
}
