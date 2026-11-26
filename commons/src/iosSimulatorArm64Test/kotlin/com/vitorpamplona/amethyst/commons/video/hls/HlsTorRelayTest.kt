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
package com.vitorpamplona.amethyst.commons.video.hls

import com.vitorpamplona.amethyst.commons.service.http.DualKtorClients
import com.vitorpamplona.amethyst.commons.service.http.FakeSocks5
import com.vitorpamplona.amethyst.commons.service.http.LoopbackProbe
import com.vitorpamplona.amethyst.commons.service.http.RoleBasedKtorClients
import com.vitorpamplona.amethyst.commons.service.http.SocksConnect
import com.vitorpamplona.amethyst.commons.service.http.darwinHttpClient
import com.vitorpamplona.amethyst.commons.tor.TorSettings
import com.vitorpamplona.amethyst.commons.tor.TorSettingsFlow
import com.vitorpamplona.amethyst.commons.tor.TorType
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.readUTF8Line
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.currentItem
import platform.AVFoundation.error
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.AVFoundation.status
import platform.Foundation.NSDate
import platform.Foundation.NSRunLoop
import platform.Foundation.NSURL
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.Foundation.runUntilDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * The B12e end-to-end test (plans/2026-11-25-ios-tor.md): an HLS stream played through
 * [LoopbackMediaRelay], the way the iOS player opens one that must go through Tor. A direct
 * NSURLSession client stands in for AVPlayer and reads only what the relay hands it; the relay
 * fetches over the app's Tor client ([RoleBasedKtorClients.ktorClientForTorVideoStream] over
 * [DualKtorClients] and [darwinHttpClient]) through a fake SOCKS5 proxy, which tunnels to a fake
 * HLS origin on 127.0.0.1.
 *
 * The CDN hosts are under `.invalid`, which never resolves, so a file that reached the origin can
 * only have gone through the proxy, and each CONNECT must name its host (ATYP 0x03, so Tor does
 * the DNS). The playlists cover what the rewriting must get right: relative, root-relative and
 * absolute URIs on a second host, a rendition served as `text/plain` without `.m3u8`, a key, an
 * init map, a byte-range segment, a live playlist that changes on every reload, and a redirect.
 */
class HlsTorRelayTest {
    private val everythingThroughTor =
        TorSettings(
            torType = TorType.INTERNAL,
            onionRelaysViaTor = true,
            videosViaTor = true,
        )

    private val cdn = "cdn.hls-test.invalid"
    private val edge = "edge.hls-test.invalid"

    private var liveReloads = 0
    private val relays = mutableListOf<LoopbackMediaRelay>()

    private fun origin(hit: FakeHlsServer.Hit): FakeHlsServer.Answer? =
        when (hit.host to hit.path.substringBefore('?')) {
            cdn to "/live/master.m3u8" -> {
                FakeHlsServer.Answer(
                    """
                    #EXTM3U
                    #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="a",NAME="en",DEFAULT=YES,URI="audio/en?format=hls"
                    #EXT-X-STREAM-INF:BANDWIDTH=800000,AUDIO="a"
                    low/index.m3u8
                    #EXT-X-STREAM-INF:BANDWIDTH=2000000
                    http://$edge/hd/index.m3u8
                    """.trimIndent(),
                    "application/vnd.apple.mpegurl",
                )
            }

            // A playlist only its first bytes give away.
            cdn to "/live/audio/en" -> {
                FakeHlsServer.Answer("#EXTM3U\n#EXT-X-TARGETDURATION:2\n#EXTINF:2,\n/live/audio/a1.aac\n", "text/plain")
            }

            cdn to "/live/low/index.m3u8" -> {
                val sequence = 100 + liveReloads++
                FakeHlsServer.Answer(
                    """
                    #EXTM3U
                    #EXT-X-TARGETDURATION:2
                    #EXT-X-MEDIA-SEQUENCE:$sequence
                    #EXT-X-KEY:METHOD=AES-128,URI="../keys/k.bin"
                    #EXT-X-MAP:URI="init.mp4"
                    #EXTINF:2,
                    #EXT-X-BYTERANGE:4@2
                    seg$sequence.ts
                    #EXTINF:2,
                    http://$edge/abs/seg${sequence + 1}.ts
                    """.trimIndent(),
                    "application/x-mpegURL",
                )
            }

            // What the AVPlayer test opens: a media playlist whose segment is relative.
            cdn to "/av/low.m3u8" -> FakeHlsServer.Answer("#EXTM3U\n#EXT-X-TARGETDURATION:2\n#EXTINF:2,\nlow1.ts\n#EXT-X-ENDLIST\n", "application/vnd.apple.mpegurl")

            cdn to "/big.ts" -> FakeHlsServer.Answer(ByteArray(8 * 1024 * 1024), "video/mp2t")

            cdn to "/live/keys/k.bin" -> FakeHlsServer.Answer(ByteArray(16) { it.toByte() })
            cdn to "/live/low/init.mp4" -> FakeHlsServer.Answer("INIT".encodeToByteArray(), "video/mp4")
            cdn to "/live/audio/a1.aac" -> FakeHlsServer.Answer("AUDIO".encodeToByteArray(), "audio/aac")
            cdn to "/redirect.m3u8" -> FakeHlsServer.Answer(ByteArray(0), status = 302, headers = listOf("Location" to "http://$edge/moved/index.m3u8"))
            cdn to "/drm.m3u8" -> FakeHlsServer.Answer("#EXTM3U\n#EXT-X-KEY:METHOD=SAMPLE-AES,URI=\"skd://key\"\n#EXTINF:2,\ns.ts\n", "application/vnd.apple.mpegurl")
            edge to "/hd/index.m3u8" -> FakeHlsServer.Answer("#EXTM3U\n#EXTINF:2,\nhd1.ts\n#EXT-X-ENDLIST\n", "application/vnd.apple.mpegurl")
            edge to "/moved/index.m3u8" -> FakeHlsServer.Answer("#EXTM3U\n#EXTINF:2,\nrel.ts\n", "application/vnd.apple.mpegurl")
            else -> if (hit.path.endsWith(".ts")) FakeHlsServer.Answer("0123456789".encodeToByteArray(), "video/mp2t") else null
        }

    @Test
    fun everyPlaylistSegmentAndKeyGoesThroughTheProxyByName() =
        withServers { hls, socks, scope ->
            val relay = torRelay(MutableStateFlow(socks.port), scope)
            val player = darwinHttpClient(null)
            val stream = assertNotNull(relay.open("http://$cdn/live/master.m3u8"))
            val relayPrefix = "http://127.0.0.1:${relay.port()}/"
            assertTrue(stream.url.startsWith(relayPrefix), stream.url)

            // The master playlist: every URI is a relay address now, relative or on another host.
            val master = player.get(stream.url)
            assertEquals(200, master.status.value)
            assertEquals(HlsPlaylistRewriter.PLAYLIST_CONTENT_TYPE, master.headers[HttpHeaders.ContentType])
            val masterUris = uris(master.bodyAsText())
            assertEquals(3, masterUris.size, "audio rendition and two variants")
            masterUris.forEach { assertTrue(it.startsWith(relayPrefix), "the player is only ever handed the relay: $it") }
            val (audio, low, hd) = masterUris

            // The rendition sent as text/plain from a path without .m3u8 is rewritten too.
            val audioUris = uris(player.get(audio).bodyAsText())
            assertEquals(1, audioUris.size)
            assertEquals("AUDIO", player.get(audioUris[0]).bodyAsText())

            // A live playlist: each reload is fetched again, not served from anything kept.
            val first = player.get(low).bodyAsText()
            val second = player.get(low).bodyAsText()
            assertTrue("#EXT-X-MEDIA-SEQUENCE:100" in first, first)
            assertTrue("#EXT-X-MEDIA-SEQUENCE:101" in second, second)
            val (key, map, segment, absoluteSegment) = uris(second)

            assertEquals(ByteArray(16) { it.toByte() }.toList(), player.get(key).bodyAsBytes().toList(), "the AES key")
            assertEquals("INIT", player.get(map).bodyAsText())

            // #EXT-X-BYTERANGE: the player asks for the range, the relay passes it up and the 206 back.
            val ranged = player.get(segment) { header(HttpHeaders.Range, "bytes=2-5") }
            assertEquals(206, ranged.status.value)
            assertEquals("bytes 2-5/10", ranged.headers[HttpHeaders.ContentRange])
            assertEquals("2345", ranged.bodyAsText())
            assertEquals("0123456789", player.get(absoluteSegment).bodyAsText())

            val hdSegment = uris(player.get(hd).bodyAsText()).single()
            assertEquals("0123456789", player.get(hdSegment).bodyAsText())

            // A redirected playlist's relative URIs resolve against where it moved.
            val moved = assertNotNull(relay.open("http://$cdn/redirect.m3u8"))
            val rel = uris(player.get(moved.url).bodyAsText()).single()
            assertEquals("0123456789", player.get(rel).bodyAsText())

            val expected =
                listOf(
                    cdn to "/live/master.m3u8",
                    cdn to "/live/audio/en?format=hls",
                    cdn to "/live/audio/a1.aac",
                    cdn to "/live/low/index.m3u8",
                    cdn to "/live/keys/k.bin",
                    cdn to "/live/low/init.mp4",
                    cdn to "/live/low/seg101.ts",
                    edge to "/abs/seg102.ts",
                    edge to "/hd/index.m3u8",
                    edge to "/hd/hd1.ts",
                    cdn to "/redirect.m3u8",
                    edge to "/moved/index.m3u8",
                    edge to "/moved/rel.ts",
                )
            val hits = hls.hits().map { it.host to it.path }
            expected.forEach { assertTrue(it in hits, "the origin saw $it; it saw $hits") }
            assertEquals(2, hits.count { it == cdn to "/live/low/index.m3u8" }, "both live reloads reached the origin")
            assertEquals("bytes=2-5", hls.hits().single { it.path == "/live/low/seg101.ts" }.range)

            // Every connection to the origin came through the proxy, by name.
            val connects = socks.connects()
            assertEquals(hls.hits().size, connects.size, "one tunnel per request: $connects")
            assertEquals(setOf(SocksConnect(FakeSocks5.DOMAIN_NAME, cdn, 80), SocksConnect(FakeSocks5.DOMAIN_NAME, edge, 80)), connects.toSet())
            println("B12e relay: ${hits.size} origin requests, all through SOCKS by name: ${connects.toSet()}")
        }

    @Test
    fun avPlayerAsksTheRelayForThePlaylistAndItsSegment() =
        withServers { hls, socks, scope ->
            val relay = torRelay(MutableStateFlow(socks.port), scope)
            val stream = assertNotNull(relay.open("http://$cdn/av/low.m3u8"))
            val player = AVPlayer(playerItem = AVPlayerItem(asset = AVURLAsset(uRL = assertNotNull(NSURL.URLWithString(stream.url)), options = null)))
            player.play()
            try {
                // AVFoundation reads the playlist through the relay, then asks the relay for the
                // segment the rewritten playlist names (the bytes are no real video: asking is the
                // point). Its own requests go to 127.0.0.1 only: the origin's hosts never resolve.
                // A master playlist is not tried here: in this test process AVFoundation refuses
                // one (-11868, "cannot be played on the device") even straight from the origin.
                val deadline = TimeSource.Monotonic.markNow() + AV_PLAYER_WAIT
                while (hls.hits().none { it.path.endsWith(".ts") } && deadline.hasNotPassedNow()) {
                    NSRunLoop.mainRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(0.05))
                }
                val hits = hls.hits().map { it.host to it.path }
                println("B12e AVPlayer: origin saw $hits, SOCKS ${socks.connects().toSet()}, item ${player.currentItem?.status} ${player.currentItem?.error}")
                assertTrue(cdn to "/av/low.m3u8" in hits, "the playlist: $hits")
                assertTrue(cdn to "/av/low1.ts" in hits, "the segment, resolved against the playlist: $hits")
                socks.connects().forEach { assertEquals(FakeSocks5.DOMAIN_NAME, it.addressType, "$it") }
                assertEquals(hls.hits().size, socks.connects().size, "every request AVPlayer made reached the origin through the proxy")
            } finally {
                player.pause()
                player.replaceCurrentItemWithPlayerItem(null)
            }
        }

    @Test
    fun withoutTorNothingIsFetchedAndTheFailureIsKept() =
        withServers { hls, socks, scope ->
            // Tor required, no port yet: the Tor route is the client that refuses without sending.
            val relay = torRelay(MutableStateFlow(null), scope)
            val stream = assertNotNull(relay.open("http://$cdn/live/master.m3u8"))
            val answer = darwinHttpClient(null).get(stream.url)
            assertEquals(502, answer.status.value)
            assertIs<RelayFailure.TorUnavailable>(relay.failureOf(stream.stream))
            delay(SETTLE_MS)
            assertEquals(emptyList(), hls.hits(), "nothing reached the origin")
            assertEquals(emptyList(), socks.connects(), "nothing reached the proxy")
        }

    @Test
    fun failuresAreErrorsNeverRedirectsToTheOriginal() =
        withServers { _, socks, scope ->
            val relay = torRelay(MutableStateFlow(socks.port), scope)
            val player = darwinHttpClient(null)

            val missing = assertNotNull(relay.open("http://$cdn/missing.m3u8"))
            assertEquals(404, player.get(missing.url).status.value)
            assertEquals(RelayFailure.Http("http://$cdn/missing.m3u8", 404), relay.failureOf(missing.stream))

            // A FairPlay key the player would fetch itself: the playlist is refused whole.
            val drm = assertNotNull(relay.open("http://$cdn/drm.m3u8"))
            val refused = player.get(drm.url)
            assertEquals(502, refused.status.value)
            assertTrue(refused.headers[HttpHeaders.Location] == null)
            assertIs<RelayFailure.Unroutable>(relay.failureOf(drm.stream))
        }

    @Test
    fun aPlayerHangingUpMidSegmentIsNoFailureOfTheStream() =
        withServers { hls, socks, scope ->
            val relay = torRelay(MutableStateFlow(socks.port), scope)
            val stream = assertNotNull(relay.open("http://$cdn/big.ts"))
            val port = assertNotNull(relay.port())
            // AVPlayer drops a segment's connection on every seek: read the head, then hang up.
            assertEquals(200, rawStatus(port, stream.url.removePrefix("http://127.0.0.1:$port"), host = "127.0.0.1:$port"))
            val deadline = TimeSource.Monotonic.markNow() + 5.seconds
            while (hls.hits().isEmpty() && deadline.hasNotPassedNow()) delay(20)
            delay(SETTLE_MS * 2)
            assertEquals(null, relay.failureOf(stream.stream), "a hang-up is not the stream's failure")
        }

    @Test
    fun answersOnlyItsOwnPathsAndHost() =
        withServers { hls, socks, scope ->
            val relay = torRelay(MutableStateFlow(socks.port), scope)
            val stream = assertNotNull(relay.open("http://$cdn/live/master.m3u8"))
            val port = assertNotNull(relay.port())
            val path = stream.url.removePrefix("http://127.0.0.1:$port")

            assertEquals(200, rawStatus(port, path, host = "127.0.0.1:$port"))
            // DNS rebinding: a page's request arrives with the page's own host name.
            assertEquals(403, rawStatus(port, path, host = "attacker.example:$port"))
            // Not under the secret: not an open proxy.
            assertEquals(404, rawStatus(port, "/0123/abc/aHR0cDovL2E/x", host = "127.0.0.1:$port"))
            assertEquals(1, hls.hits().size, "only the good request was relayed")
            assertEquals(null, relay.open("file:///etc/hosts"), "only http(s) is relayed")
        }

    @Test
    fun aReclaimedListenerComesBackOnTheSamePortAndOldAddressesKeepWorking() =
        withServers { hls, socks, scope ->
            val relay = torRelay(MutableStateFlow(socks.port), scope)
            val stream = assertNotNull(relay.open("http://$cdn/live/master.m3u8"))
            val port = assertNotNull(relay.port())
            val path = stream.url.removePrefix("http://127.0.0.1:$port")
            assertEquals(200, rawStatus(port, path, host = "127.0.0.1:$port"))

            relay.recheck()
            assertEquals(port, relay.port(), "a listener that still accepts is left alone")

            // As iOS reclaims a suspended app's listening socket.
            relay.closeListeningSocketForTest()
            assertFalse(LoopbackProbe.isTcpListening(port), "nothing accepts on the port")

            relay.recheck()
            assertEquals(port, relay.port(), "listening again on the same port")
            assertEquals(200, rawStatus(port, path, host = "127.0.0.1:$port"), "an address handed out before still plays")
            assertEquals(2, hls.hits().size)
        }

    private fun torRelay(
        port: MutableStateFlow<Int?>,
        scope: CoroutineScope,
    ): LoopbackMediaRelay {
        val clients = DualKtorClients(port, MutableStateFlow(false), scope, ::darwinHttpClient)
        val roles = RoleBasedKtorClients(clients, TorSettingsFlow.build(everythingThroughTor))
        return LoopbackMediaRelay(roles::ktorClientForTorVideoStream).also { relays += it }
    }

    /** The URIs a playlist names: its URI lines and URI attributes, in order. */
    private fun uris(playlist: String): List<String> =
        playlist.lines().flatMap { line ->
            when {
                line.isBlank() -> emptyList()
                line.startsWith("#") -> Regex("""URI="([^"]*)"""").findAll(line).map { it.groupValues[1] }.toList()
                else -> listOf(line.trim())
            }
        }

    /** A request with the given `Host`, which NSURLSession would not let a test set. */
    private suspend fun rawStatus(
        port: Int,
        path: String,
        host: String,
    ): Int {
        val selector = SelectorManager(Dispatchers.IO)
        try {
            val socket = aSocket(selector).tcp().connect("127.0.0.1", port)
            try {
                val output = socket.openWriteChannel(autoFlush = true)
                val input = socket.openReadChannel()
                output.writeFully("GET $path HTTP/1.1\r\nHost: $host\r\nConnection: close\r\n\r\n".encodeToByteArray())
                return input
                    .readUTF8Line()
                    ?.split(' ')
                    ?.getOrNull(1)
                    ?.toIntOrNull() ?: -1
            } finally {
                socket.close()
            }
        } finally {
            selector.close()
        }
    }

    private fun withServers(block: suspend (FakeHlsServer, FakeSocks5, CoroutineScope) -> Unit) =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val hls = FakeHlsServer.start(scope, routes = ::origin)
            val socks = FakeSocks5.start(scope, upstreamPort = hls.port)
            try {
                withTimeout(TEST_TIMEOUT_MS) { block(hls, socks, scope) }
            } finally {
                relays.forEach { it.close() }
                socks.close()
                hls.close()
                scope.cancel()
            }
        }

    companion object {
        const val TEST_TIMEOUT_MS = 120_000L
        val AV_PLAYER_WAIT = 30.seconds

        /** Long enough for a stray dial to reach the origin, were there one. */
        const val SETTLE_MS = 500L
    }
}
