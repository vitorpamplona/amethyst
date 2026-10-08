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
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.URI
import java.util.Collections
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MediaRelayTest {
    private val video = ByteArray(100_000) { (it % 251).toByte() }
    private lateinit var upstream: HttpServer
    private lateinit var base: String

    // Every request the app's client made: the relay must fetch through it, never around it.
    private val fetched = Collections.synchronizedList(mutableListOf<String>())
    private val client =
        OkHttpClient
            .Builder()
            .addInterceptor(
                Interceptor { chain ->
                    fetched += chain.request().url.toString()
                    chain.proceed(chain.request())
                },
            ).build()

    @BeforeTest
    fun startUpstream() {
        upstream =
            HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).apply {
                createContext("/media/video.mp4") { exchange ->
                    val range = exchange.requestHeaders.getFirst("Range")
                    exchange.responseHeaders.add("Content-Type", "video/mp4")
                    exchange.responseHeaders.add("Accept-Ranges", "bytes")
                    if (range != null) {
                        val (from, to) = range.removePrefix("bytes=").split('-').map { it.toInt() }
                        exchange.responseHeaders.add("Content-Range", "bytes $from-$to/${video.size}")
                        exchange.sendResponseHeaders(206, (to - from + 1).toLong())
                        exchange.responseBody.write(video, from, to - from + 1)
                    } else {
                        exchange.sendResponseHeaders(200, video.size.toLong())
                        exchange.responseBody.write(video)
                    }
                    exchange.close()
                }
                createContext("/live/elsewhere.m3u8") { exchange ->
                    val bytes = "#EXTM3U\n#EXTINF:4.0,\nrtmp://example.com/live/seg1\n".toByteArray()
                    exchange.responseHeaders.add("Content-Type", "application/vnd.apple.mpegurl")
                    exchange.sendResponseHeaders(200, bytes.size.toLong())
                    exchange.responseBody.write(bytes)
                    exchange.close()
                }
                createContext("/live/index.m3u8") { exchange ->
                    val playlist =
                        "#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"keys/k1\"\n#EXTINF:4.0,\nseg1.ts\n#EXTINF:4.0,\n$base/elsewhere/seg2.ts\n"
                    val bytes = playlist.toByteArray()
                    exchange.responseHeaders.add("Content-Type", "application/vnd.apple.mpegurl")
                    exchange.sendResponseHeaders(200, bytes.size.toLong())
                    exchange.responseBody.write(bytes)
                    exchange.close()
                }
                start()
            }
        base = "http://127.0.0.1:${upstream.address.port}"
        MediaHttp.install({ client }, EncryptionKeyCache(), viaTor = { true })
    }

    @AfterTest
    fun stopUpstream() {
        upstream.stop(0)
        MediaHttp.install({ client }, EncryptionKeyCache(), viaTor = { false })
    }

    private fun get(
        url: String,
        range: String? = null,
    ): Triple<Int, Map<String, List<String>>, ByteArray> {
        val connection = URI(url).toURL().openConnection(Proxy.NO_PROXY) as HttpURLConnection
        range?.let { connection.setRequestProperty("Range", it) }
        val code = connection.responseCode
        val body = (if (code < 400) connection.inputStream else connection.errorStream)?.use { it.readBytes() } ?: ByteArray(0)
        return Triple(code, connection.headerFields.filterKeys { it != null }, body)
    }

    @Test
    fun aTorVideoStreamsThroughTheAppsClientWithItsRanges() {
        val original = "$base/media/video.mp4?x=1"
        val relayed = MediaRelay.streamingUrl(original)!!
        assertTrue(relayed.startsWith("http://127.0.0.1:"), relayed)

        val (code, headers, body) = get(relayed, range = "bytes=1000-1999")

        assertEquals(206, code)
        assertContentEquals(video.copyOfRange(1000, 2000), body)
        // Header names are case-insensitive (the JDK server sends "Content-range").
        val contentRange =
            headers.entries
                .firstOrNull { it.key.equals("Content-Range", ignoreCase = true) }
                ?.value
                ?.single()
        assertEquals("bytes 1000-1999/${video.size}", contentRange)
        assertEquals(listOf(original), fetched)
    }

    @Test
    fun aVideoNotOverTorIsLeftToTheEngine() {
        MediaHttp.install({ client }, EncryptionKeyCache(), viaTor = { false })
        val original = "$base/media/video.mp4"
        assertEquals(original, MediaRelay.streamingUrl(original))
    }

    @Test
    fun everyPlaylistUriComesBackThroughTheRelay() {
        val (code, _, body) = get(MediaRelay.streamingUrl("$base/live/index.m3u8")!!)
        assertEquals(200, code)

        val lines = body.decodeToString().lines()
        val uris = lines.filter { it.isNotBlank() && !it.startsWith("#") } + lines.mapNotNull { Regex("URI=\"([^\"]+)\"").find(it)?.groupValues?.get(1) }
        assertEquals(3, uris.size, body.decodeToString())
        assertTrue(uris.all { it.startsWith("http://127.0.0.1:") && !it.contains("127.0.0.1:${upstream.address.port}/") }, uris.toString())

        // A rewritten segment address leads back to the original, resolved against the playlist.
        val segment = uris.first { it.contains("seg1.ts") }
        val path = segment.toHttpUrl().encodedPath.split('/', limit = 3)[2]
        assertEquals("$base/live/seg1.ts", MediaRelay.originalFor(path))
    }

    @Test
    fun theRelayAnswersOnlyUnderItsSecret() {
        val relayed = MediaRelay.streamingUrl("$base/media/video.mp4")!!
        val port = relayed.toHttpUrl().port
        val (code, _, _) = get("http://127.0.0.1:$port/not-the-secret/http/127.0.0.1/media/video.mp4")
        assertEquals(404, code)
        assertTrue(fetched.isEmpty())
    }

    @Test
    fun aUrlTheServersUriParserWouldRejectStillStreams() {
        // OkHttp keeps '|' and '{' raw in a query; java.net.URI, which the relay's server parses
        // request lines with, rejects them, so the original must not appear in the relay path.
        // (The test's upstream is a JDK server too and rejects them in turn, so only the fetch is
        // checked: it happening at all means the relay took the request.)
        val original = "$base/media/video.mp4?x=a|b&y={c}"
        get(MediaRelay.streamingUrl(original)!!)

        assertEquals(listOf(original.toHttpUrl().toString()), fetched)
    }

    @Test
    fun aTorVideoTheRelayCannotCarryIsRefusedRatherThanSentDirect() {
        assertEquals(null, MediaRelay.streamingUrl("rtmp://example.com/live/stream"))
    }

    @Test
    fun aPlaylistNamingANonHttpUriIsRefused() {
        val (code, _, _) = get(MediaRelay.streamingUrl("$base/live/elsewhere.m3u8")!!)
        assertEquals(502, code)
    }

    @Test
    fun aRequestForAnotherHostIsRefused() {
        // What a web page reaching the relay by DNS rebinding would send.
        val relayed = MediaRelay.streamingUrl("$base/media/video.mp4")!!.toHttpUrl()
        val status =
            Socket(Proxy.NO_PROXY).use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", relayed.port))
                socket.getOutputStream().write(
                    "GET ${relayed.encodedPath} HTTP/1.1\r\nHost: attacker.example:${relayed.port}\r\nConnection: close\r\n\r\n".toByteArray(),
                )
                socket.getInputStream().bufferedReader().readLine()
            }
        assertTrue(status.contains(" 403"), status)
        assertTrue(fetched.isEmpty())
    }
}
