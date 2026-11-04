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
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * Chromium reads an Ogg file by opening it whole, then asking for its last pages to learn its
 * length before it reads on: the whole-file response sits unread meanwhile. The tail must still
 * arrive, or the file never plays.
 */
class MediaRelayConcurrentRangeTest {
    private val file = ByteArray(4_671_074) { (it % 251).toByte() }
    private lateinit var upstream: HttpServer
    private lateinit var base: String
    private val client = OkHttpClient()

    // How Chromium's requests reach the relay (ChromiumMediaSession's loopback client).
    private val loopback =
        OkHttpClient
            .Builder()
            .proxy(Proxy.NO_PROXY)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

    @BeforeTest
    fun startUpstream() {
        upstream =
            HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).apply {
                createContext("/audio/voice.ogg") { exchange ->
                    val range = exchange.requestHeaders.getFirst("Range")
                    exchange.responseHeaders.add("Content-Type", "audio/ogg")
                    exchange.responseHeaders.add("Accept-Ranges", "bytes")
                    val from = range?.removePrefix("bytes=")?.substringBefore('-')?.toInt() ?: 0
                    val to = range?.substringAfter('-')?.takeIf { it.isNotEmpty() }?.toInt() ?: (file.size - 1)
                    try {
                        if (range != null) {
                            exchange.responseHeaders.add("Content-Range", "bytes $from-$to/${file.size}")
                            exchange.sendResponseHeaders(206, (to - from + 1).toLong())
                        } else {
                            exchange.sendResponseHeaders(200, file.size.toLong())
                        }
                        exchange.responseBody.write(file, from, to - from + 1)
                    } catch (_: Exception) {
                        // The relay hung up.
                    } finally {
                        exchange.close()
                    }
                }
                executor =
                    java.util.concurrent.Executors
                        .newCachedThreadPool()
                start()
            }
        base = "http://127.0.0.1:${upstream.address.port}"
        MediaHttp.install({ client }, EncryptionKeyCache(), viaTor = { false })
    }

    @AfterTest
    fun stopUpstream() {
        upstream.stop(0)
    }

    @Test
    fun theTailArrivesWhileTheWholeFileSitsUnread() {
        val original = "$base/audio/voice.ogg?run=${System.nanoTime()}"
        val whole =
            loopback
                .newCall(
                    Request
                        .Builder()
                        .url(MediaRelay.relayUrlFor(original)!!)
                        .header("Range", "bytes=0-")
                        .build(),
                ).execute()
        try {
            assertEquals(206, whole.code)
            val tailStart = 4_587_520
            loopback
                .newCall(
                    Request
                        .Builder()
                        .url(MediaRelay.relayUrlFor(original)!!)
                        .header("Range", "bytes=$tailStart-")
                        .build(),
                ).execute()
                .use { tail ->
                    assertEquals(206, tail.code)
                    assertContentEquals(file.copyOfRange(tailStart, file.size), tail.body.bytes())
                }
        } finally {
            whole.close()
        }
    }
}
