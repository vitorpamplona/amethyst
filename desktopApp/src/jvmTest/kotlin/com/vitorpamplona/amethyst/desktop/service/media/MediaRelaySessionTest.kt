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
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A retired engine's downloads stop when its session is revoked, not when the engine is disposed. */
class MediaRelaySessionTest {
    private val chunk = ByteArray(64 * 1024)
    private val total = 64L * 1024 * 1024
    private val served = AtomicLong()
    private val file = ByteArray(3 * 1024 * 1024) { (it % 251).toByte() }
    private val ranges = Collections.synchronizedList(mutableListOf<String?>())
    private lateinit var upstream: HttpServer
    private lateinit var base: String

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
                // A host that answers ranges, 64 KiB every 10 ms.
                createContext("/clip.mp4") { exchange ->
                    val range = exchange.requestHeaders.getFirst("Range")
                    ranges += range
                    exchange.responseHeaders.add("Content-Type", "video/mp4")
                    exchange.responseHeaders.add("Accept-Ranges", "bytes")
                    val from = range?.removePrefix("bytes=")?.substringBefore('-')?.toInt() ?: 0
                    val to = range?.substringAfter('-')?.takeIf { it.isNotEmpty() }?.toInt() ?: (file.size - 1)
                    try {
                        if (range != null) exchange.responseHeaders.add("Content-Range", "bytes $from-$to/${file.size}")
                        exchange.sendResponseHeaders(if (range != null) 206 else 200, (to - from + 1).toLong())
                        var at = from
                        while (at <= to) {
                            val n = minOf(64 * 1024, to - at + 1)
                            exchange.responseBody.write(file, at, n)
                            exchange.responseBody.flush()
                            at += n
                            Thread.sleep(10)
                        }
                    } catch (_: Exception) {
                        // The relay hung up.
                    } finally {
                        exchange.close()
                    }
                }
                // A slow host: 64 KiB every 20 ms.
                createContext("/video.mp4") { exchange ->
                    exchange.responseHeaders.add("Content-Type", "video/mp4")
                    try {
                        exchange.sendResponseHeaders(200, total)
                        var sent = 0L
                        while (sent < total) {
                            exchange.responseBody.write(chunk)
                            exchange.responseBody.flush()
                            sent += chunk.size
                            served.addAndGet(chunk.size.toLong())
                            Thread.sleep(20)
                        }
                    } catch (_: Exception) {
                        // The relay hung up.
                    } finally {
                        exchange.close()
                    }
                }
                executor = Executors.newCachedThreadPool()
                start()
            }
        base = "http://127.0.0.1:${upstream.address.port}"
        val client = OkHttpClient()
        MediaHttp.install({ client }, EncryptionKeyCache(), viaTor = { true })
    }

    @AfterTest
    fun stopUpstream() {
        upstream.stop(0)
    }

    @Test
    fun aBackgroundStreamGivesWayWhileTheWatchedVideoStartsAndResumesWhereItStopped() {
        val original = "$base/clip.mp4?run=${System.nanoTime()}"
        val watched = MediaRelay.newSession()
        val background = MediaRelay.newSession()
        MediaRelay.markPictured(background)
        try {
            loopback
                .newCall(
                    Request
                        .Builder()
                        .url(MediaRelay.streamingUrl(original, background)!!)
                        .header("Range", "bytes=0-")
                        .build(),
                ).execute()
                .use { response ->
                    assertEquals(206, response.code)
                    val input = response.body.byteStream()
                    val head = input.readNBytes(256 * 1024)
                    MediaRelay.setForeground(watched, starting = true)
                    // Held: nothing more arrives for a while.
                    Thread.sleep(500)
                    val heldAt = ranges.size
                    MediaRelay.setForeground(watched, starting = false)
                    val rest = input.readNBytes(Int.MAX_VALUE)
                    assertContentEquals(file, head + rest)
                    // The rest came on a new range request from where the first one was dropped.
                    assertEquals(heldAt + 1, ranges.size)
                    assertTrue(ranges.last()!!.startsWith("bytes="))
                    assertTrue(
                        ranges
                            .last()!!
                            .removePrefix("bytes=")
                            .substringBefore('-')
                            .toInt() >= 256 * 1024,
                    )
                }
        } finally {
            MediaRelay.setForeground(null, starting = false)
        }
    }

    @Test
    fun aStreamWhoseCardHasNoPictureYetDoesNotGiveWay() {
        val original = "$base/clip.mp4?run=${System.nanoTime()}"
        val watched = MediaRelay.newSession()
        val background = MediaRelay.newSession()
        try {
            loopback
                .newCall(
                    Request
                        .Builder()
                        .url(MediaRelay.streamingUrl(original, background)!!)
                        .header("Range", "bytes=0-")
                        .build(),
                ).execute()
                .use { response ->
                    val input = response.body.byteStream()
                    val head = input.readNBytes(256 * 1024)
                    MediaRelay.setForeground(watched, starting = true)
                    val started = System.currentTimeMillis()
                    val rest = input.readNBytes(Int.MAX_VALUE)
                    // Read straight through while the watched video was still starting.
                    assertTrue(System.currentTimeMillis() - started < 5_000)
                    assertContentEquals(file, head + rest)
                    assertEquals(1, ranges.size)
                }
        } finally {
            MediaRelay.setForeground(null, starting = false)
        }
    }

    @Test
    fun revokingASessionStopsItsDownloadAndRefusesItsNextRequest() {
        val original = "$base/video.mp4?run=${System.nanoTime()}"
        val session = MediaRelay.newSession()
        val url = MediaRelay.streamingUrl(original, session)!!
        loopback.newCall(Request.Builder().url(url).build()).execute().use { response ->
            assertEquals(200, response.code)
            val input = response.body.byteStream()
            input.readNBytes(256 * 1024)
            MediaRelay.revoke(session)
            val cutAt = served.get()
            val ended =
                try {
                    input.readNBytes(Int.MAX_VALUE)
                    true
                } catch (_: IOException) {
                    true
                }
            assertTrue(ended)
            Thread.sleep(300)
            // Nothing much flowed after the revoke: the upstream call was cancelled.
            assertTrue(served.get() - cutAt < 4L * 1024 * 1024, "kept downloading ${served.get() - cutAt} bytes")
        }
        loopback.newCall(Request.Builder().url(url).build()).execute().use { again ->
            assertEquals(410, again.code)
        }
    }
}
