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
package com.vitorpamplona.amethyst.desktop.service.media

import com.vitorpamplona.amethyst.commons.service.http.EncryptedBlobInterceptor
import com.vitorpamplona.amethyst.commons.service.http.EncryptionKeyCache
import com.vitorpamplona.quartz.utils.ciphers.AESGCM
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DecryptedMediaFilesTest {
    private val plaintext = ByteArray(200_000) { (it * 31 % 251).toByte() }
    private val cipher = AESGCM()
    private val keyCache = EncryptionKeyCache()
    private var requests = 0

    /** Plays the Blossom server: serves the ciphertext, or a 404 for any other path. */
    private val server =
        Interceptor { chain ->
            requests++
            val request = chain.request()
            val found = request.url.encodedPath == "/blob.mp4"
            Response
                .Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(if (found) 200 else 404)
                .message(if (found) "OK" else "Not Found")
                .body((if (found) cipher.encrypt(plaintext) else ByteArray(0)).toResponseBody("application/octet-stream".toMediaType()))
                .build()
        }

    // The app's media client decrypts in an interceptor; this one runs it in front of the fake server.
    private val client =
        OkHttpClient
            .Builder()
            .addInterceptor(EncryptedBlobInterceptor(keyCache))
            .addInterceptor(server)
            .build()

    init {
        MediaHttp.install({ client }, keyCache)
    }

    @AfterTest
    fun cleanUp() {
        MediaHttp.install({ client }, EncryptionKeyCache())
    }

    @Test
    fun anEncryptedBlobBecomesAFileOfItsPlaintext() =
        runTest {
            val url = "https://blossom.example/blob.mp4"
            keyCache.add(url, cipher, "video/mp4")

            val file = DecryptedMediaFiles.fileFor(url)!!

            assertContentEquals(plaintext, file.readBytes())
            assertTrue(file.name.endsWith(".mp4"), file.name)
            // The second ask is the same file, not a second download.
            assertSame(file, DecryptedMediaFiles.fileFor(url))
            assertEquals(1, requests)
            file.delete()
        }

    @Test
    fun aUrlWithNoKeyIsLeftToStream() =
        runTest {
            assertNull(DecryptedMediaFiles.fileFor("https://blossom.example/public.mp4"))
            assertEquals(0, requests)
        }

    @Test
    fun aFailedDownloadIsAnIOException() =
        runTest {
            val url = "https://blossom.example/missing.mp4"
            keyCache.add(url, cipher, "video/mp4")
            assertFailsWith<IOException> { DecryptedMediaFiles.fileFor(url) }
        }
}
