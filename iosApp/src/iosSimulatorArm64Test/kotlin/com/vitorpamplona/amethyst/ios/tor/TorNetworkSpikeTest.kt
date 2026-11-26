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
package com.vitorpamplona.amethyst.ios.tor

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.ProxyBuilder
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.engine.darwin.DarwinClientEngineConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import platform.Foundation.setValue
import platform.Network.nw_endpoint_create_host
import platform.Network.nw_proxy_config_create_socksv5
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.TimeSource

/**
 * The B12a routing spike (plans/2026-11-25-ios-tor.md §4): Arti on the simulator, NSURLSession
 * (Ktor Darwin) pointed at its SOCKS port, and what actually goes through it.
 *
 * Needs the Tor network, so it is excluded unless `-Pamethyst.arti.network=true`. Only reads:
 * check.torproject.org, the Tor Project's onion site, and one REQ to a public relay read up to
 * its EOSE. Nothing is published. Every result is printed with a `B12A` prefix; the assertions
 * at the end cover only what B12 depends on (Tor answers, the relay socket goes through the proxy,
 * nothing falls back to a direct connection).
 */
@OptIn(ExperimentalForeignApi::class)
class TorNetworkSpikeTest {
    private val checkUrl = "https://check.torproject.org/api/ip"

    // Several, because any one may time out at the exit or refuse Tor exits (relay.damus.io
    // answered the upgrade with an HTTP error, -1011, on 2026-11-25).
    private val clearRelays =
        listOf("wss://nos.lol", "wss://relay.primal.net", "wss://nostr.mom", "wss://offchain.pub", "wss://relay.damus.io")

    // From the list at github.com/0xtrr/onion-service-nostr-relays (nostr.oxtr.dev, bitcoiner.social).
    private val onionRelays =
        listOf(
            "ws://oxtrdevav64z64yb7x6rjg4ntzqjhedm5b5zjqulugknhzr46ny2qbad.onion",
            "ws://bitcoinr6de5lkvx4tpwdmzrdfdpla5sya2afwpcabjup2xpi5dulbad.onion",
        )

    // www.torproject.org's onion service, per onion.torproject.org.
    private val onionSite = "http://2gzyxa5ihm7nsggfxnu52rck2vv4rvmdlkiu3zzui5du4xyclen53wid.onion/"

    private fun report(line: String) = println("B12A $line")

    private fun HttpClientConfig<DarwinClientEngineConfig>.common() {
        install(WebSockets)
        install(HttpTimeout) {
            requestTimeoutMillis = 90_000
            connectTimeoutMillis = 60_000
            socketTimeoutMillis = 60_000
        }
        expectSuccess = false
    }

    /** Ktor's own SOCKS path: `connectionProxyDictionary` on the session configuration. */
    private fun dictionaryProxyClient(
        host: String,
        port: Int,
    ) = HttpClient(Darwin) {
        engine { proxy = ProxyBuilder.socks(host, port) }
        common()
    }

    /**
     * iOS 17's `proxyConfigurations` (failover off by default). Not in the Kotlin/Native Foundation
     * klib, so it is set by key-value coding with an `nw_proxy_config_t` from Network.framework.
     */
    private fun nwProxyClient(port: Int) =
        HttpClient(Darwin) {
            engine {
                configureSession {
                    val endpoint = nw_endpoint_create_host("localhost", port.toString())
                    val proxy = nw_proxy_config_create_socksv5(endpoint)
                    setValue(listOf(proxy), forKey = "proxyConfigurations")
                }
            }
            common()
        }

    private fun directClient() =
        HttpClient(Darwin) {
            common()
        }

    /** "IsTor=true", "IsTor=false", or the failure. Never the IP itself. */
    private suspend fun isTor(client: HttpClient): String =
        try {
            val body = client.get(checkUrl).bodyAsText()
            Regex("\"IsTor\"\\s*:\\s*(true|false)").find(body)?.let { "IsTor=${it.groupValues[1]}" }
                ?: "unexpected body ${body.take(80)}"
        } catch (e: Throwable) {
            "FAILED ${e::class.simpleName}: ${e.message?.take(160)}"
        }

    private suspend fun httpGet(
        client: HttpClient,
        url: String,
    ): String =
        try {
            val response = client.get(url)
            "HTTP ${response.status.value}, ${response.bodyAsText().length} chars"
        } catch (e: Throwable) {
            "FAILED ${e::class.simpleName}: ${e.message?.take(200)}"
        }

    /** Opens the relay, sends one REQ, reads to EOSE. Returns "EOSE after n events" or the failure. */
    private suspend fun relayEose(
        client: HttpClient,
        url: String,
    ): String =
        try {
            withTimeout(90_000) {
                var outcome = "closed before EOSE"
                client.webSocket(url) {
                    send(Frame.Text("""["REQ","b12a",{"kinds":[1],"limit":2}]"""))
                    var events = 0
                    for (frame in incoming) {
                        val text = (frame as? Frame.Text)?.readText() ?: continue
                        when {
                            text.startsWith("[\"EVENT\"") -> events++
                            text.startsWith("[\"EOSE\"") -> {
                                outcome = "EOSE after $events events"
                                break
                            }
                            text.startsWith("[\"CLOSED\"") || text.startsWith("[\"NOTICE\"") -> {
                                outcome = "relay answered ${text.take(120)}"
                                break
                            }
                        }
                    }
                    send(Frame.Text("""["CLOSE","b12a"]"""))
                }
                outcome
            }
        } catch (e: Throwable) {
            "FAILED ${e::class.simpleName}: ${e.message?.take(200)}"
        }

    /** Runs [block] and says how many SOCKS connections it opened. */
    private suspend fun <T> viaSocks(block: suspend () -> T): Pair<T, Long> {
        val before = ArtiNative.socksAcceptedCount()
        val result = block()
        return result to (ArtiNative.socksAcceptedCount() - before)
    }

    @Test
    fun spike() =
        runBlocking(Dispatchers.Default) {
            val log = LogCollector()
            ArtiNative.setLogCallback(log::add)
            ArtiNative.destroy()
            report("before Arti: ${processStats()}")

            // ---- Bootstrap (cold: a fresh data dir) ----
            val dir = freshArtiDir("spike")
            val cold = TimeSource.Monotonic.markNow()
            check(ArtiNative.initialize(dir) == 0) { "initialize failed: ${log.lines.takeLast(5)}" }
            val port = startSocksOnFreePort()
            check(port > 0) { "no free SOCKS port" }
            report("initialize+SOCKS ${cold.elapsedNow().inWholeMilliseconds}ms, port $port")
            while (ArtiNative.isBootstrapped() != 1 && cold.elapsedNow().inWholeSeconds < 180) delay(500)
            val coldMs = cold.elapsedNow().inWholeMilliseconds
            report("[5] cold bootstrap: ready=${ArtiNative.isBootstrapped()} after ${coldMs}ms, progress ${ArtiNative.bootstrapProgressPermille()}/1000")
            report("[5] after bootstrap: ${processStats()}")

            val dictLocalhost = dictionaryProxyClient("localhost", port)
            val direct = directClient()

            // ---- 1. HTTPS through connectionProxyDictionary ----
            val (check1, socks1) = viaSocks { isTor(dictLocalhost) }
            report("[1] check.torproject.org via SOCKS localhost:$port -> $check1 (SOCKS connections +$socks1)")
            val (check1b, socks1b) = viaSocks { dictionaryProxyClient("127.0.0.1", port).use { isTor(it) } }
            report("[1] same via 127.0.0.1:$port -> $check1b (+$socks1b)")
            report("[1] direct client -> ${isTor(direct)}")

            // ---- 2. wss:// relay through the same proxied client ----
            var check2 = ""
            var socks2 = 0L
            var wssRelay = clearRelays.first()
            relays@ for (relay in clearRelays) {
                repeat(2) {
                    val (outcome, opened) = viaSocks { relayEose(dictLocalhost, relay) }
                    report("[2] $relay via SOCKS -> $outcome (SOCKS connections +$opened)")
                    if (outcome.startsWith("EOSE")) {
                        check2 = outcome
                        socks2 = opened
                        wssRelay = relay
                        break@relays
                    }
                }
            }
            report("[2] ${clearRelays.first()} direct -> ${relayEose(direct, clearRelays.first())}")

            // ---- 3. .onion over http:// and ws://, and what ATS does here ----
            val (onionHttp, socks3a) = viaSocks { httpGet(dictLocalhost, onionSite) }
            report("[3] $onionSite via SOCKS -> $onionHttp (+$socks3a)")
            for (relay in onionRelays) {
                val (outcome, opened) = viaSocks { relayEose(dictLocalhost, relay) }
                report("[3] $relay via SOCKS -> $outcome (+$opened)")
                if (outcome.startsWith("EOSE")) break
            }
            report("[3] ATS probe, clearnet http:// direct: http://example.com/ -> ${httpGet(direct, "http://example.com/")}")

            // ---- 4. SOCKS stopped mid-run: does anything go direct? ----
            ArtiNative.stopSocksProxy()
            report("[4] SOCKS stopped")
            val afterStopFresh = dictionaryProxyClient("localhost", port).use { isTor(it) }
            report("[4] new dictionary-proxy client -> $afterStopFresh")
            val afterStopOld = isTor(dictLocalhost)
            report("[4] the client that was already connected, same host -> $afterStopOld")
            val afterStopOldNewHost = httpGet(dictLocalhost, "https://www.torproject.org/")
            report("[4] the client that was already connected, new host www.torproject.org -> $afterStopOldNewHost")
            val afterStopWs = dictionaryProxyClient("localhost", port).use { relayEose(it, wssRelay) }
            report("[4] new dictionary-proxy client, wss relay -> $afterStopWs")
            val neverListened = dictionaryProxyClient("localhost", port + 100).use { isTor(it) }
            report("[4] proxy port that never had a listener (${port + 100}) -> $neverListened")

            // ---- 4b. The same through iOS 17's proxyConfigurations ----
            val nwStopped = nwProxyClient(port).use { isTor(it) }
            report("[4b] proxyConfigurations client, SOCKS stopped -> $nwStopped")
            check(ArtiNative.startSocksProxy(port) == 0) { "SOCKS restart failed" }
            val (nwHttp, socksNwHttp) = viaSocks { nwProxyClient(port).use { isTor(it) } }
            report("[4b] proxyConfigurations client, SOCKS up -> $nwHttp (+$socksNwHttp)")
            val (nwWs, socksNwWs) = viaSocks { nwProxyClient(port).use { relayEose(it, wssRelay) } }
            report("[4b] proxyConfigurations client, wss relay -> $nwWs (+$socksNwWs)")
            ArtiNative.stopSocksProxy()
            val nwStoppedAgain = nwProxyClient(port).use { isTor(it) }
            report("[4b] proxyConfigurations client after a second stop -> $nwStoppedAgain")

            // ---- 5. Memory after traffic, warm restart ----
            report("[5] after the checks: ${processStats()}")
            dictLocalhost.close()
            direct.close()
            ArtiNative.destroy()
            val warm = TimeSource.Monotonic.markNow()
            ArtiNative.initialize(dir)
            while (ArtiNative.isBootstrapped() != 1 && warm.elapsedNow().inWholeSeconds < 120) delay(250)
            report("[5] warm bootstrap (same data dir, after destroy): ready=${ArtiNative.isBootstrapped()} after ${warm.elapsedNow().inWholeMilliseconds}ms")
            ArtiNative.destroy()
            report("[5] after destroy: ${processStats()}")
            report("arti log: ${log.lines.size} lines, errors: ${log.lines.filter { it.startsWith("ERROR") }.take(8)}")
            ArtiNative.setLogCallback(null)

            fun noLeak(outcome: String) = outcome.startsWith("FAILED") || outcome == "IsTor=true"
            assertTrue(check1 == "IsTor=true", "[1] $check1")
            assertTrue(socks1 > 0, "[1] did not use the SOCKS port")
            assertTrue(check2.startsWith("EOSE") && socks2 > 0, "[2] relay through SOCKS: $check2 (+$socks2)")
            assertTrue(noLeak(afterStopFresh), "[4] new client after stop went direct: $afterStopFresh")
            assertTrue(noLeak(afterStopOld), "[4] old client after stop went direct: $afterStopOld")
            assertTrue(afterStopOldNewHost.startsWith("FAILED"), "[4] old client, new host after stop: $afterStopOldNewHost")
            assertTrue(afterStopWs.startsWith("FAILED"), "[4] relay after stop: $afterStopWs")
            assertTrue(noLeak(neverListened), "[4] dead port went direct: $neverListened")
            assertTrue(noLeak(nwStopped) && noLeak(nwStoppedAgain), "[4b] $nwStopped / $nwStoppedAgain")
        }
}
