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
package com.vitorpamplona.amethyst.service.relayClient.diagnostics

import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.ClosedMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EoseMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EventMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.NoticeMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.AuthCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

/**
 * Debug-only cold-start census: what every relay in the pool actually did, and why the ones
 * that failed, failed.
 *
 * The existing instrumentation cannot answer this. [com.vitorpamplona.quartz.nip01Core.relay.client.stats.RelayStats]
 * has counters but no dump path and no failure taxonomy; `RelaySpeedLogger` counts events per
 * second but not connection outcomes; `RelayLogger` prints one line per event, which at the
 * few-hundred-relay cardinality of the outbox model is thousands of lines to grep rather than
 * a table to read.
 *
 * What this adds: connection outcome bucketed **by cause**, REQ/EOSE/CLOSED accounting per
 * relay, and time-to-first-EOSE — so a boot can be read as "N relays served us, M were
 * refused for reason R, K were never reachable".
 *
 * Attach only in debug builds; it holds one small record per relay for the process lifetime.
 */
class BootRelayDiagnostics(
    val client: INostrClient,
    val dumpAtSeconds: List<Long> = listOf(20, 45, 90),
) {
    companion object {
        const val TAG = "BootRelayDiag"

        /**
         * Buckets a connection failure by what actually went wrong. The distinction that
         * matters most here is *ours vs theirs*: a SOCKS refusal is our Tor proxy declining
         * to open a stream and says nothing about the relay, but it reaches the relay client
         * through the same path as a genuine relay failure and is charged to the relay.
         */
        fun classify(error: String): String =
            when {
                error.contains("SOCKS", ignoreCase = true) -> "tor-socks"
                error.contains("127.0.0.1") -> "tor-proxy-down"
                error.contains("UnknownHostException") -> "dns"
                error.contains("SSLHandshakeException") || error.contains("SSLPeerUnverified") -> "tls"
                error.contains("SocketTimeoutException") -> "timeout"
                error.contains("Server Misconfigured") -> "http-" + (Regex("Response: (\\d+)").find(error)?.groupValues?.get(1) ?: "?")
                error.contains("ConnectException") -> "refused"
                error.contains("Connection reset") -> "reset"
                else -> "other"
            }
    }

    class RelayRecord {
        val tentatives = AtomicInteger()
        val opens = AtomicInteger()
        val disconnects = AtomicInteger()
        val reqsSent = AtomicInteger()
        val authsSent = AtomicInteger()
        val events = AtomicInteger()
        val eoses = AtomicInteger()
        val notices = AtomicInteger()

        /** failure cause -> count, see [classify]. */
        val failures = ConcurrentHashMap<String, AtomicInteger>()

        /** CLOSED machine-readable prefix (or "unprefixed") -> count. */
        val closed = ConcurrentHashMap<String, AtomicInteger>()

        val firstOpenAtMs = AtomicLong(0)
        val firstEoseAtMs = AtomicLong(0)

        fun bump(
            map: ConcurrentHashMap<String, AtomicInteger>,
            key: String,
        ) = map.computeIfAbsent(key) { AtomicInteger() }.incrementAndGet()
    }

    private val records = ConcurrentHashMap<NormalizedRelayUrl, RelayRecord>()
    private val startedAtMs = System.currentTimeMillis()

    private fun record(url: NormalizedRelayUrl) = records.computeIfAbsent(url) { RelayRecord() }

    private fun elapsed() = System.currentTimeMillis() - startedAtMs

    private val listener =
        object : RelayConnectionListener {
            override fun onConnecting(relay: IRelayClient) {
                record(relay.url).tentatives.incrementAndGet()
            }

            override fun onConnected(
                relay: IRelayClient,
                pingMillis: Int,
                compressed: Boolean,
            ) {
                val r = record(relay.url)
                r.opens.incrementAndGet()
                r.firstOpenAtMs.compareAndSet(0, elapsed())
            }

            override fun onCannotConnect(
                relay: IRelayClient,
                errorMessage: String,
            ) {
                val r = record(relay.url)
                r.bump(r.failures, classify(errorMessage))
            }

            override fun onDisconnected(relay: IRelayClient) {
                record(relay.url).disconnects.incrementAndGet()
            }

            override fun onSent(
                relay: IRelayClient,
                cmdStr: String,
                cmd: Command,
                success: Boolean,
            ) {
                val r = record(relay.url)
                when (cmd) {
                    is ReqCmd -> r.reqsSent.incrementAndGet()
                    is AuthCmd -> r.authsSent.incrementAndGet()
                    else -> Unit
                }
            }

            override fun onIncomingMessage(
                relay: IRelayClient,
                msgStr: String,
                msg: Message,
            ) {
                val r = record(relay.url)
                when (msg) {
                    is EventMessage -> r.events.incrementAndGet()
                    is EoseMessage -> {
                        r.eoses.incrementAndGet()
                        r.firstEoseAtMs.compareAndSet(0, elapsed())
                    }
                    is NoticeMessage -> r.notices.incrementAndGet()
                    is ClosedMessage -> r.bump(r.closed, prefixOf(msg.message))
                    else -> Unit
                }
            }
        }

    /** First token of a NIP-01 machine-readable CLOSED/OK message, or "unprefixed". */
    private fun prefixOf(message: String?): String {
        val text = message?.trim().orEmpty()
        if (text.isEmpty()) return "empty"
        val head = text.substringBefore(':', "")
        return if (head.isNotEmpty() && head.length < 20 && !head.contains(' ')) head else "unprefixed"
    }

    init {
        client.addConnectionListener(listener)
        thread(isDaemon = true, name = TAG) {
            var last = 0L
            dumpAtSeconds.forEach { at ->
                Thread.sleep((at - last) * 1000)
                last = at
                dump(at)
            }
        }
    }

    fun detach() = client.removeConnectionListener(listener)

    /**
     * One line per relay plus a rollup. Kept to a single Log.w per line so the whole census
     * survives logcat's per-tag rate limiting on a busy boot.
     */
    fun dump(atSeconds: Long) {
        val snapshot = records.toMap()

        val served = snapshot.filter { it.value.events.get() > 0 }
        val opened = snapshot.filter { it.value.opens.get() > 0 }
        val neverOpened = snapshot.filter { it.value.opens.get() == 0 }

        val causeTotals = mutableMapOf<String, Int>()
        val closedTotals = mutableMapOf<String, Int>()
        snapshot.values.forEach { r ->
            r.failures.forEach { (k, v) -> causeTotals[k] = (causeTotals[k] ?: 0) + v.get() }
            r.closed.forEach { (k, v) -> closedTotals[k] = (closedTotals[k] ?: 0) + v.get() }
        }

        Log.w(TAG, "===== boot census @${atSeconds}s =====")
        Log.w(
            TAG,
            "pool=${snapshot.size} opened=${opened.size} served_events=${served.size} never_opened=${neverOpened.size} " +
                "dials=${snapshot.values.sumOf { it.tentatives.get() }} " +
                "events=${snapshot.values.sumOf { it.events.get() }} " +
                "reqs=${snapshot.values.sumOf { it.reqsSent.get() }} " +
                "auths=${snapshot.values.sumOf { it.authsSent.get() }}",
        )
        Log.w(TAG, "failures_by_cause=" + causeTotals.entries.sortedByDescending { it.value }.joinToString { "${it.key}:${it.value}" })
        Log.w(TAG, "closed_by_prefix=" + closedTotals.entries.sortedByDescending { it.value }.joinToString { "${it.key}:${it.value}" })

        // Relays that cost us dials and gave nothing back, worst first: the wasted-effort list.
        Log.w(TAG, "--- top wasted dials (no events received) ---")
        snapshot
            .filter { it.value.events.get() == 0 }
            .entries
            .sortedByDescending { it.value.tentatives.get() }
            .take(25)
            .forEach { (url, r) ->
                Log.w(
                    TAG,
                    "WASTE ${url.url} dials=${r.tentatives.get()} opens=${r.opens.get()} " +
                        "fail=[${r.failures.entries.joinToString { "${it.key}:${it.value.get()}" }}] " +
                        "closed=[${r.closed.entries.joinToString { "${it.key}:${it.value.get()}" }}] " +
                        "reqs=${r.reqsSent.get()} eose=${r.eoses.get()}",
                )
            }

        // The relays actually carrying the boot, so a suppression change can be checked for
        // coverage loss rather than just CLOSED reduction.
        Log.w(TAG, "--- top event providers ---")
        served.entries
            .sortedByDescending { it.value.events.get() }
            .take(20)
            .forEach { (url, r) ->
                Log.w(
                    TAG,
                    "SERVE ${url.url} events=${r.events.get()} reqs=${r.reqsSent.get()} eose=${r.eoses.get()} " +
                        "openMs=${r.firstOpenAtMs.get()} eoseMs=${r.firstEoseAtMs.get()} dials=${r.tentatives.get()}",
                )
            }
        Log.w(TAG, "===== end census @${atSeconds}s =====")
    }
}
