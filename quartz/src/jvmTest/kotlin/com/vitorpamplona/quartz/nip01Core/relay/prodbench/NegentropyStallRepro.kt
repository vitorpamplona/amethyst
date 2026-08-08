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
package com.vitorpamplona.quartz.nip01Core.relay.prodbench

import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.negentropySyncOrFetch
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocket
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocketListener
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebsocketBuilder
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.BasicOkHttpWebSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test

/**
 * Isolated repro for the client-side NIP-77 negentropy stall against
 * primal.net / purplepag.es (works against relay.ditto.pub).
 *
 * Gated: no-ops unless NEG_STALL_REPRO is set.
 *
 *   NEG_STALL_REPRO=1 ./gradlew :quartz:jvmTest --tests "*.NegentropyStallRepro" --info
 */
class NegentropyStallRepro {
    companion object {
        const val WALL_CLOCK_MS = 60_000L
    }

    /** Live, line-flushed log so progress is visible even under Gradle stdout capture. */
    private val logFile = System.getenv("NEG_STALL_LOG")?.let { java.io.File(it) }

    private fun log(line: String) {
        println(line)
        logFile?.appendText(line + "\n")
    }

    /** A tag for a raw nostr frame so the log reads at a glance. */
    private fun tag(frame: String): String {
        val head = frame.take(12)
        return when {
            head.contains("NEG-OPEN") -> "NEG-OPEN"
            head.contains("NEG-MSG") -> "NEG-MSG"
            head.contains("NEG-ERR") -> "NEG-ERR"
            head.contains("NEG-CLOSE") -> "NEG-CLOSE"
            head.contains("\"REQ\"") || head.startsWith("[\"REQ\"") -> "REQ"
            head.contains("\"EVENT\"") || head.startsWith("[\"EVENT\"") -> "EVENT"
            head.contains("\"EOSE\"") -> "EOSE"
            head.contains("\"CLOSE\"") -> "CLOSE"
            head.contains("\"CLOSED\"") -> "CLOSED"
            head.contains("\"NOTICE\"") -> "NOTICE"
            head.contains("\"COUNT\"") -> "COUNT"
            else -> "?"
        }
    }

    /**
     * Wraps a [WebsocketBuilder] to log every frame in both directions and
     * tally per-type counts, so we can see the exact sequence and where it
     * stops making progress.
     */
    private class LoggingBuilder(
        val delegate: WebsocketBuilder,
        val sentCounts: ConcurrentHashMap<String, AtomicInteger>,
        val recvCounts: ConcurrentHashMap<String, AtomicInteger>,
        val negMsgBytesIn: AtomicLong,
        val negMsgBytesOut: AtomicLong,
        val verbose: Boolean,
        val tagger: (String) -> String,
        val log: (String) -> Unit,
    ) : WebsocketBuilder {
        override fun build(
            url: NormalizedRelayUrl,
            out: WebSocketListener,
        ): WebSocket {
            val loggingOut =
                object : WebSocketListener {
                    override fun onOpen(
                        pingMillis: Int,
                        compression: Boolean,
                    ) {
                        log("  [<-open] ${url.url} ping=${pingMillis}ms deflate=$compression")
                        out.onOpen(pingMillis, compression)
                    }

                    override suspend fun onMessage(text: String) {
                        val t = tagger(text)
                        recvCounts.getOrPut(t) { AtomicInteger() }.incrementAndGet()
                        if (t == "NEG-MSG") negMsgBytesIn.addAndGet(text.length.toLong())
                        if (verbose && t != "EVENT") {
                            log("  [<-$t] len=${text.length} ${text.take(80)}")
                        }
                        out.onMessage(text)
                    }

                    override fun onClosed(
                        code: Int,
                        reason: String,
                    ) {
                        log("  [<-closed] ${url.url} code=$code reason=$reason")
                        out.onClosed(code, reason)
                    }

                    override fun onFailure(
                        t: Throwable,
                        code: Int?,
                        response: String?,
                    ) {
                        log("  [<-failure] ${url.url} code=$code msg=$response err=${t.message}")
                        out.onFailure(t, code, response)
                    }
                }

            val socket = delegate.build(url, loggingOut)
            return object : WebSocket {
                override fun needsReconnect() = socket.needsReconnect()

                override fun connect() = socket.connect()

                override fun disconnect() = socket.disconnect()

                override fun send(msg: String): Boolean {
                    val t = tagger(msg)
                    sentCounts.getOrPut(t) { AtomicInteger() }.incrementAndGet()
                    if (t == "NEG-MSG") negMsgBytesOut.addAndGet(msg.length.toLong())
                    if (verbose && t != "EVENT") {
                        log("  [->$t] len=${msg.length} ${msg.take(80)}")
                    }
                    return socket.send(msg)
                }
            }
        }
    }

    private fun run(
        label: String,
        relay: String,
        filter: Filter,
        httpClient: OkHttpClient,
        verbose: Boolean,
    ) {
        log("\n=== $label -> $relay  filter=$filter ===")

        val sent = ConcurrentHashMap<String, AtomicInteger>()
        val recv = ConcurrentHashMap<String, AtomicInteger>()
        val negIn = AtomicLong(0)
        val negOut = AtomicLong(0)

        val builder =
            LoggingBuilder(
                BasicOkHttpWebSocket.Builder { httpClient },
                sent,
                recv,
                negIn,
                negOut,
                verbose,
                ::tag,
                ::log,
            )

        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val client = NostrClient(builder, scope)

        var lastProgress = -1L
        val events = AtomicInteger(0)
        val startMs = System.currentTimeMillis()

        runBlocking {
            val result =
                withTimeoutOrNull(WALL_CLOCK_MS) {
                    client.negentropySyncOrFetch(
                        relay = relay,
                        filter = filter,
                        localEntries = emptyList(),
                        onProgress = { need, downloaded ->
                            // Throttle to one line/sec so an endless reconcile
                            // doesn't flood the log.
                            val now = System.currentTimeMillis()
                            if (now - lastProgress > 1000) {
                                lastProgress = now
                                log("  [progress] need=$need downloaded=$downloaded  (recv NEG-MSG=${recv["NEG-MSG"]?.get() ?: 0})")
                            }
                        },
                        onEvent = { events.incrementAndGet() },
                    )
                }

            val took = System.currentTimeMillis() - startMs
            if (result == null) {
                log("  RESULT: *** STALLED *** (externally timed out after ${took}ms)")
            } else {
                log("  RESULT: returned in ${took}ms  downloaded=${result.downloaded} pagedFallback=${result.pagedFallback}")
            }
        }

        log("  events delivered:  ${events.get()}")
        log("  frames SENT:       ${sent.entries.sortedBy { it.key }.joinToString { "${it.key}=${it.value.get()}" }}")
        log("  frames RECV:       ${recv.entries.sortedBy { it.key }.joinToString { "${it.key}=${it.value.get()}" }}")
        log("  NEG-MSG bytes:     out=${negOut.get()} in=${negIn.get()}")

        client.close()
    }

    @Test
    fun reproduce() {
        if (System.getenv("NEG_STALL_REPRO") == null && System.getProperty("negStallRepro") == null) {
            println("NegentropyStallRepro skipped. Set NEG_STALL_REPRO=1 to run against live relays.")
            return
        }

        val httpClient =
            OkHttpClient
                .Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .pingInterval(30, TimeUnit.SECONDS)
                .build()

        val verbose = System.getenv("NEG_STALL_VERBOSE") != null

        // Control: known-good relay.
        run("DITTO (control, expected to work)", "wss://relay.ditto.pub", Filter(kinds = listOf(0)), httpClient, verbose)

        // The two stalls.
        run("PRIMAL (expected to stall)", "wss://relay.primal.net", Filter(kinds = listOf(0)), httpClient, verbose)
        run("PURPLEPAGES (expected to stall)", "wss://purplepag.es", Filter(kinds = listOf(0)), httpClient, verbose)
    }
}
