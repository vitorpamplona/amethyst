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
package com.vitorpamplona.amethyst.desktop.napplet

import com.sun.net.httpserver.HttpServer
import okhttp3.OkHttpClient
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PaperArtCacheTest {
    private lateinit var dir: File
    private lateinit var server: HttpServer
    private val hits = AtomicInteger()
    private val client = OkHttpClient()
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3)

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("paper-art").toFile()
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/photo.jpg") { exchange ->
            hits.incrementAndGet()
            exchange.sendResponseHeaders(200, jpeg.size.toLong())
            exchange.responseBody.use { it.write(jpeg) }
        }
        server.createContext("/missing.jpg") { exchange ->
            hits.incrementAndGet()
            exchange.sendResponseHeaders(404, -1)
            exchange.close()
        }
        server.start()
    }

    @AfterTest
    fun tearDown() {
        server.stop(0)
        dir.deleteRecursively()
    }

    private fun url(path: String) = "http://127.0.0.1:${server.address.port}$path"

    @Test
    fun aPictureIsDownloadedOnceAndThenServedFromDisk() {
        val cache = PaperArtCache(dir)
        assertContentEquals(jpeg, cache.load(url("/photo.jpg"), client))
        // A new cache on the same folder: what the next session, or the next start, sees.
        assertContentEquals(jpeg, PaperArtCache(dir).load(url("/photo.jpg"), client))
        assertEquals(1, hits.get())
    }

    @Test
    fun aFailedDownloadIsNotKept() {
        val cache = PaperArtCache(dir)
        assertNull(cache.load(url("/missing.jpg"), client))
        assertNull(cache.load(url("/missing.jpg"), client))
        assertEquals(2, hits.get())
    }

    @Test
    fun trimmingDropsWhatWasUsedLongestAgo() {
        val cache = PaperArtCache(dir, maxBytes = 15)
        cache.put("https://a.example/old.jpg", ByteArray(10))
        dir.listFiles()!!.single().setLastModified(System.currentTimeMillis() - 10 * 60 * 60 * 1000)
        cache.put("https://a.example/new.jpg", ByteArray(10))
        cache.trim()
        assertNull(cache.get("https://a.example/old.jpg"))
        assertEquals(10, cache.get("https://a.example/new.jpg")?.size)
    }

    @Test
    fun picturesAreNamedByTheirBytes() {
        assertEquals("image/jpeg", PaperArtCache.mimeOf(jpeg))
        assertEquals("image/png", PaperArtCache.mimeOf(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A)))
        assertEquals("image/webp", PaperArtCache.mimeOf("RIFF\u0000\u0000\u0000\u0000WEBPVP8 ".toByteArray(Charsets.ISO_8859_1)))
        assertEquals("image/svg+xml", PaperArtCache.mimeOf("<?xml version=\"1.0\"?><svg xmlns=\"http://www.w3.org/2000/svg\"/>".toByteArray()))
        assertEquals("application/octet-stream", PaperArtCache.mimeOf(byteArrayOf(1, 2, 3)))
    }
}
