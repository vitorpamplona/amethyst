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
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.NoticeMessage
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.BasicOkHttpWebSocket
import com.vitorpamplona.quartz.nip77Negentropy.NegErrMessage
import com.vitorpamplona.quartz.nip77Negentropy.NegMsgMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.fail

/**
 * Broad live validation of the NIP-77 stall fix: run `negentropySyncOrFetch` against
 * as many public relays as we can reach and assert NONE hangs — every relay must
 * either reconcile via negentropy or fall over to paging, and the suspend fun must
 * return without an external wall-clock rescue.
 *
 * Gated (opens live sockets to ~35 relays):
 *   NEG_MULTI=1 ./gradlew :quartz:jvmTest --tests "*.NegentropyMultiRelayLiveTest" --info
 *
 * The wall-clock [HARD_TIMEOUT_MS] is only a safety net set well above [IDLE_MS]:
 * with the fix a refusing relay trips the idle watchdog (or the notice/closed
 * fast-path) and returns; a relay that only returns because the wall clock fired is
 * the bug this guards against and fails the test.
 */
class NegentropyMultiRelayLiveTest {
    companion object {
        const val MAX_EVENTS = 100
        const val IDLE_MS = 20_000L
        const val HARD_TIMEOUT_MS = 90_000L
        const val PARALLELISM = 6

        val RELAYS =
            listOf(
                "wss://relay.damus.io",
                "wss://nos.lol",
                "wss://relay.primal.net",
                "wss://relay.nostr.band",
                "wss://nostr.wine",
                "wss://purplepag.es",
                "wss://relay.ditto.pub",
                "wss://nostr.mom",
                "wss://relay.nostr.bg",
                "wss://offchain.pub",
                "wss://nostr.oxtr.dev",
                "wss://relay.nostrplebs.com",
                "wss://nostr21.com",
                "wss://relay.mostr.pub",
                "wss://nostr.bitcoiner.social",
                "wss://relay.nostrcheck.me",
                "wss://nostr-pub.wellorder.net",
                "wss://relay.snort.social",
                "wss://relayable.org",
                "wss://nostr.land",
                "wss://relay.momostr.pink",
                "wss://relay.0xchat.com",
                "wss://nostr.fmt.wiz.biz",
                "wss://wot.utxo.one",
                "wss://nostr.data.haus",
                "wss://eden.nostr.land",
                "wss://atlas.nostr.land",
                "wss://relay.nostr.com.au",
                "wss://relay.wellorder.net",
                "wss://relay.fountain.fm",
            )
    }

    private class RelayProbe {
        val negMsgs = AtomicInteger(0)
        val negErrs = AtomicInteger(0)

        @Volatile var firstNegErr: String? = null

        @Volatile var firstNotice: String? = null
    }

    private class Outcome(
        val relay: String,
        val label: String,
        val downloaded: Int,
        val ms: Long,
        val detail: String,
    )

    @Test
    fun everyReachableRelayReturnsNoHang() {
        if (System.getenv("NEG_MULTI") == null && System.getProperty("negMulti") == null) {
            println("NegentropyMultiRelayLiveTest skipped. Set NEG_MULTI=1 to run against live relays.")
            return
        }

        val httpClient =
            OkHttpClient
                .Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .pingInterval(30, TimeUnit.SECONDS)
                .build()

        val outcomes =
            runBlocking {
                val gate = Semaphore(PARALLELISM)
                RELAYS
                    .map { relay ->
                        async(Dispatchers.IO) {
                            gate.withPermit { probeRelay(relay, httpClient) }
                        }
                    }.awaitAll()
            }

        println("\n================ NIP-77 multi-relay results ================")
        println("%-26s %-14s %10s %8s  %s".format("relay", "outcome", "downloaded", "ms", "detail"))
        outcomes.sortedBy { it.label }.forEach {
            println(
                "%-26s %-14s %10d %8d  %s".format(
                    it.relay.removePrefix("wss://"),
                    it.label,
                    it.downloaded,
                    it.ms,
                    it.detail,
                ),
            )
        }

        val byLabel = outcomes.groupingBy { it.label }.eachCount()
        println("\nsummary: $byLabel")

        val hangs = outcomes.filter { it.label == "HANG" }
        if (hangs.isNotEmpty()) {
            fail("These relays HUNG (no return within ${HARD_TIMEOUT_MS}ms) — the stall is not fixed for them:\n" + hangs.joinToString("\n") { "  ${it.relay}: ${it.detail}" })
        }
    }

    private suspend fun probeRelay(
        relay: String,
        httpClient: OkHttpClient,
    ): Outcome {
        val probe = RelayProbe()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val client = NostrClient(BasicOkHttpWebSocket.Builder { httpClient }, scope)

        val listener =
            object : RelayConnectionListener {
                override suspend fun onIncomingMessage(
                    relay: IRelayClient,
                    msgStr: String,
                    msg: Message,
                ) {
                    when (msg) {
                        is NegMsgMessage -> probe.negMsgs.incrementAndGet()
                        is NegErrMessage -> {
                            probe.negErrs.incrementAndGet()
                            if (probe.firstNegErr == null) probe.firstNegErr = msg.reason
                        }
                        is NoticeMessage -> if (probe.firstNotice == null) probe.firstNotice = msg.message
                        else -> Unit
                    }
                }
            }
        client.addConnectionListener(listener)

        val started = System.currentTimeMillis()
        return try {
            val downloaded = AtomicInteger(0)
            val result =
                withTimeoutOrNull(HARD_TIMEOUT_MS) {
                    client.negentropySyncOrFetch(
                        relay = relay,
                        filter = Filter(kinds = listOf(0)),
                        maxEvents = MAX_EVENTS,
                        idleTimeoutMs = IDLE_MS,
                        localEntries = emptyList(),
                        onEvent = { downloaded.incrementAndGet() },
                    )
                }
            val ms = System.currentTimeMillis() - started
            val wire =
                "negMsg=${probe.negMsgs.get()} negErr=${probe.negErrs.get()}" +
                    (probe.firstNegErr?.let { " err=\"${it.take(48)}\"" } ?: "") +
                    (probe.firstNotice?.let { " notice=\"${it.take(48)}\"" } ?: "")

            when {
                result == null ->
                    Outcome(relay, "HANG", downloaded.get(), ms, "no return; $wire")
                result.pagedFallback ->
                    Outcome(relay, "OK-PAGED", result.downloaded, ms, "fell back to paging; $wire")
                result.downloaded > 0 ->
                    Outcome(relay, "OK-NEG", result.downloaded, ms, "native negentropy; $wire")
                else ->
                    Outcome(relay, "OK-EMPTY", result.downloaded, ms, "returned, 0 events; $wire")
            }
        } catch (e: Throwable) {
            val ms = System.currentTimeMillis() - started
            Outcome(relay, "ERROR", 0, ms, "${e::class.simpleName}: ${e.message?.take(60)}")
        } finally {
            client.close()
        }
    }
}
