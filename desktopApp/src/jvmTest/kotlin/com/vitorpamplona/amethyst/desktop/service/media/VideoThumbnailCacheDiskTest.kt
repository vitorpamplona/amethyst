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

import com.sun.net.httpserver.HttpServer
import com.vitorpamplona.amethyst.commons.service.http.EncryptionKeyCache
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The poster of a feed video downloads the start of the video; those bytes must stay in
 * [VideoDiskCache], so pressing play reads them from disk instead of downloading them again.
 */
class VideoThumbnailCacheDiskTest {
    // Not a decodable video: the poster fails, but the download it made is what is under test.
    private val file = Random(42).nextBytes(6 * 1024 * 1024)
    private val chunk = 4 * 1024 * 1024

    private lateinit var upstream: HttpServer
    private lateinit var base: String

    @BeforeTest
    fun startUpstream() {
        upstream =
            HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).apply {
                createContext("/") { exchange ->
                    val range = exchange.requestHeaders.getFirst("Range")
                    exchange.responseHeaders.add("Content-Type", "video/mp4")
                    exchange.responseHeaders.add("Accept-Ranges", "bytes")
                    val from = range?.removePrefix("bytes=")?.substringBefore('-')?.toInt() ?: 0
                    val to =
                        range
                            ?.substringAfter('-')
                            ?.takeIf { it.isNotEmpty() }
                            ?.toInt()
                            ?.coerceAtMost(file.size - 1) ?: (file.size - 1)
                    try {
                        if (range != null) {
                            exchange.responseHeaders.add("Content-Range", "bytes $from-$to/${file.size}")
                            exchange.sendResponseHeaders(206, (to - from + 1).toLong())
                        } else {
                            exchange.sendResponseHeaders(200, file.size.toLong())
                        }
                        exchange.responseBody.write(file, from, to - from + 1)
                    } catch (_: IOException) {
                        // The client hung up.
                    } finally {
                        exchange.close()
                    }
                }
                executor = Executors.newCachedThreadPool()
                start()
            }
        base = "http://127.0.0.1:${upstream.address.port}"
        val client = OkHttpClient.Builder().proxy(Proxy.NO_PROXY).build()
        MediaHttp.install({ client }, EncryptionKeyCache(), viaTor = { false })
    }

    @AfterTest
    fun stopUpstream() {
        upstream.stop(0)
    }

    @Test
    fun thePostersDownloadStaysInTheVideoDiskCache() {
        val url = "$base/${UUID.randomUUID()}.mp4"
        assertNull(VideoDiskCache.heldStart(url))

        runBlocking { VideoThumbnailCache.getThumbnail(url) }

        val (held, total) = assertNotNull(VideoDiskCache.heldStart(url), "the poster's download was thrown away")
        assertEquals(chunk.toLong(), held)
        assertEquals(file.size.toLong(), total)
        assertContentEquals(file.copyOf(chunk), VideoDiskCache.readStart(url, chunk))
    }
}
