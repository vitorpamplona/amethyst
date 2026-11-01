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
import okhttp3.Request
import java.io.File
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import java.nio.file.Files
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VideoIndexPrefetchTest {
    // An MP4 laid out as a streaming one is: ftyp, then a 300 KiB moov, then the media.
    private val moovSize = 300 * 1024
    private val file = mp4(box("ftyp", 24), box("moov", moovSize), box("mdat", 2 * 1024 * 1024))
    private val moovEnd = 24L + moovSize

    // An MP4 with its index after the media, as phones record them: ftyp, mdat, moov.
    private val tailFile by lazy { box("ftyp", 24) + box("mdat", 200 * 1024) + container("moov", trak("soun", "mp4a") + trak("vide", "av01")) }

    private lateinit var upstream: HttpServer
    private lateinit var base: String
    private val ranges = Collections.synchronizedList(mutableListOf<String?>())

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
                createContext("/") { exchange ->
                    val file = if (exchange.requestURI.path == "/tail.mp4") tailFile else file
                    val range = exchange.requestHeaders.getFirst("Range")
                    ranges += range
                    exchange.responseHeaders.add("Content-Type", "video/mp4")
                    exchange.responseHeaders.add("Accept-Ranges", "bytes")
                    val from = range?.removePrefix("bytes=")?.substringBefore('-')?.toInt() ?: 0
                    // A host slow to answer for the media past the index.
                    if (exchange.requestURI.path == "/slow.mp4" && from >= moovEnd) Thread.sleep(3_000)
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
        MediaHttp.install({ client }, EncryptionKeyCache(), viaTor = { false })
    }

    @AfterTest
    fun stopUpstream() {
        upstream.stop(0)
    }

    @Test
    fun findsTheIndexAtTheFrontOfAStreamingMp4() {
        assertEquals(moovEnd, VideoIndexPrefetch.endOfLeadingIndex(file.copyOf(64 * 1024)))
    }

    @Test
    fun readsAnIndexWithA64BitSize() {
        val large =
            ByteArray(16).also { b ->
                put32(b, 0, 1)
                "moov".toByteArray().copyInto(b, 4)
                put64(b, 8, 1L shl 33)
            }
        val head = box("ftyp", 24) + large
        assertEquals(24L + (1L shl 33), VideoIndexPrefetch.endOfLeadingIndex(head))
    }

    @Test
    fun leavesAnIndexAtTheEndToTheEngine() {
        val head = (box("ftyp", 24) + box("mdat", 1024) + box("moov", 1024))
        assertNull(VideoIndexPrefetch.endOfLeadingIndex(head))
    }

    @Test
    fun leavesAnythingButAnMp4Alone() {
        // A WebM starts with an EBML header.
        assertNull(VideoIndexPrefetch.endOfLeadingIndex(byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte(), 0, 0, 0, 0, 0, 0)))
        assertNull(VideoIndexPrefetch.endOfLeadingIndex(ByteArray(4)))
    }

    @Test
    fun readsTheCodecFromAnIndexAfterTheMedia() {
        val original = "$base/tail.mp4?run=${System.nanoTime()}"
        assertEquals(24L + 200 * 1024, VideoIndexPrefetch.startOfTrailingIndex(tailFile.copyOf(64 * 1024)))
        runBlocking { VideoIndexPrefetch.prefetch(original) }
        assertEquals("av01", VideoIndexPrefetch.videoCodecOf(original))
        // The probe, then the index alone: none of the media between them.
        assertEquals(listOf("bytes=0-65535", "bytes=${24 + 200 * 1024}-${tailFile.size - 1}"), ranges.toList())
    }

    @Test
    fun namesTheCodecOfTheVideoTrackNotTheSoundTrack() {
        val file = box("ftyp", 24) + container("moov", container("mvhd", ByteArray(20)) + trak("soun", "mp4a") + trak("vide", "av01"))
        assertEquals("av01", VideoIndexPrefetch.videoCodec(file))
        assertNull(VideoIndexPrefetch.videoCodec(box("ftyp", 24) + container("moov", trak("soun", "mp4a"))))
    }

    @Test
    fun theEngineGetsTheIndexFromDiskAndOnlyTheMediaFromTheHost() {
        val original = "$base/episode.mp4?run=${System.nanoTime()}"
        runBlocking { VideoIndexPrefetch.prefetch(original) }
        assertEquals(listOf("bytes=0-65535", "bytes=65536-${moovEnd - 1}"), ranges.toList())
        ranges.clear()

        // The engine opens the whole file from its start.
        loopback
            .newCall(
                Request
                    .Builder()
                    .url(MediaRelay.relayUrlFor(original)!!)
                    .header("Range", "bytes=0-")
                    .build(),
            ).execute()
            .use { whole ->
                assertEquals(206, whole.code)
                assertEquals("bytes 0-${file.size - 1}/${file.size}", whole.header("Content-Range"))
                assertContentEquals(file, whole.body.bytes())
            }
        assertEquals(listOf("bytes=$moovEnd-${file.size - 1}"), ranges.toList())
    }

    @Test
    fun theIndexOnDiskGoesOutBeforeASlowHostAnswersForTheRest() {
        val original = "$base/slow.mp4?run=${System.nanoTime()}"
        runBlocking { VideoIndexPrefetch.prefetch(original) }
        // The JDK's client, as the engines read: OkHttp's holds a body's last bytes until more arrive.
        val connection = URI(MediaRelay.relayUrlFor(original)!!).toURL().openConnection(Proxy.NO_PROXY) as HttpURLConnection
        try {
            connection.setRequestProperty("Range", "bytes=0-")
            val started = System.currentTimeMillis()
            val input = connection.inputStream
            val index = input.readNBytes(moovEnd.toInt())
            val took = System.currentTimeMillis() - started
            assertContentEquals(file.copyOf(moovEnd.toInt()), index)
            // The host takes 3 s to answer for what follows the index.
            assertTrue(took < 1_500, "the index on disk waited $took ms for the host")
            assertContentEquals(file.copyOfRange(moovEnd.toInt(), file.size), input.readAllBytes())
        } finally {
            connection.disconnect()
        }
    }

    @Test
    fun ofABigFileOnlyTheHeadIsKept() {
        val dir = Files.createTempDirectory("vdc").toFile()
        try {
            val total = 1_000_000_000L
            val entry = VideoDiskCache.Entry(File(dir, "a.data"), File(dir, "a.meta"), total, "video/mp4")
            val limit = 1024L
            val writer = VideoDiskCache.Writer(entry, 0, limit)
            val chunk = ByteArray(700) { it.toByte() }
            writer.write(chunk, chunk.size)
            writer.write(chunk, chunk.size)
            writer.close()
            assertTrue(entry.covers(0, limit - 1))
            assertFalse(entry.covers(0, limit))
            assertEquals(limit, File(dir, "a.data").length())
            assertEquals(limit, entry.heldThrough(0))
            assertContentEquals((chunk + chunk).copyOf(limit.toInt()), File(dir, "a.data").readBytes())
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun box(
        type: String,
        size: Int,
    ): ByteArray =
        ByteArray(size).also { b ->
            put32(b, 0, size.toLong())
            type.toByteArray().copyInto(b, 4)
            for (i in 8 until size) b[i] = (i % 251).toByte()
        }

    private fun trak(
        handler: String,
        codec: String,
    ): ByteArray {
        val hdlr = container("hdlr", ByteArray(8) + handler.toByteArray() + ByteArray(12))
        val stsd = container("stsd", ByteArray(4) + byteArrayOf(0, 0, 0, 1) + container(codec, ByteArray(16)))
        return container("trak", container("mdia", hdlr + container("minf", container("stbl", stsd))))
    }

    private fun container(
        type: String,
        body: ByteArray,
    ): ByteArray =
        ByteArray(8).also { b ->
            put32(b, 0, 8L + body.size)
            type.toByteArray().copyInto(b, 4)
        } + body

    private fun mp4(vararg boxes: ByteArray): ByteArray = boxes.reduce { a, b -> a + b }

    private fun put32(
        b: ByteArray,
        at: Int,
        v: Long,
    ) {
        for (k in 0 until 4) b[at + k] = (v shr (8 * (3 - k))).toByte()
    }

    private fun put64(
        b: ByteArray,
        at: Int,
        v: Long,
    ) {
        for (k in 0 until 8) b[at + k] = (v shr (8 * (7 - k))).toByte()
    }
}
