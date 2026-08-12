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
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAllPages
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.BasicOkHttpWebSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import java.time.Duration
import kotlin.test.Test

/**
 * The live half of the paging termination guards, against the relay that found
 * them. [NostrClientFetchAllPagesDrainTest] scripts this behaviour, so it pins our
 * INTERPRETATION of a relay; only dialling one can say whether the interpretation
 * matches anything real.
 *
 * ## What it walks, and why that relay
 *
 * purplepag.es holds twelve `kind 10002` events stamped `created_at = 0` and treats
 * `until <= 0` as *no* `until`, answering with its five hundred NEWEST events. A
 * cursor walk therefore reaches zero, gets a page it never asked for, delivers none
 * of it, and — before the guards — stepped one second lower and asked again. Measured
 * against the live relay: ~5.5 pages a second, 500 events fetched and discarded on
 * each, an EOSE on *every* page, `until` marching one second further negative every
 * time, for as long as the process ran. A cold walk pulled 1,490,010 real events in
 * ~10.8 minutes and then never returned.
 *
 * The ceiling is set just above the epoch-stamped events rather than `now` on
 * purpose: this relay serves ~2,300 kind 0/10002 events a second and holds years of
 * them, so starting at the top would spend a quarter of an hour on history that is
 * not what this measures. One page from [TRAP_CEILING] already carries them.
 *
 * ## Reading it
 *
 * `lowest until` is the whole tell. A walk that ends leaves it at a real timestamp; a
 * walk that cannot end leaves it below zero. With the guards in place the expected
 * report is `UNPAGEABLE` with the cursor never going under `0`.
 *
 * OFF by default and not a gate: it dials the public internet, so it is neither
 * hermetic nor reproducible, and a relay being down is not a code regression. It
 * asserts nothing for that reason — it REPORTS, and a human reads it.
 *
 * ```
 * ./gradlew :quartz:jvmTest --tests "*.CursorTerminationProbe" -PprodRelayBench=1 -i
 * ```
 */
class CursorTerminationProbe {
    @Test
    fun reportWhetherAPagedWalkTerminates() {
        if (System.getenv("PROD_RELAY_BENCH") == null && System.getProperty("prodRelayBench") == null) {
            println("reportWhetherAPagedWalkTerminates skipped. Run with -PprodRelayBench=1 to enable.")
            return
        }
        val okhttp =
            OkHttpClient
                .Builder()
                .connectTimeout(Duration.ofSeconds(20))
                .pingInterval(Duration.ofSeconds(120))
                .build()
        val scope = CoroutineScope(SupervisorJob())
        val client = NostrClient(BasicOkHttpWebSocket.Builder { okhttp }, scope)

        println("=".repeat(78))
        println("Does a paged walk TERMINATE? kinds [0, 10002], from $TRAP_CEILING down")
        println("=".repeat(78))
        try {
            for (url in RELAYS) {
                val relay = RelayUrlNormalizer.normalize(url)
                var events = 0
                var pages = 0
                var lowest = Long.MAX_VALUE
                val startedAt = System.currentTimeMillis()
                val outcome =
                    runCatching {
                        runBlocking {
                            // A hard ceiling, which `fetchAllPages` deliberately does
                            // not have: its own doc says a walk is bounded by a
                            // `limit` or by cancelling the caller, and this is the
                            // caller cancelling. Without it a relay with no guard
                            // hangs the probe — which is exactly what it is here to
                            // detect, so it must be detected rather than suffered.
                            withTimeoutOrNull(TERMINATION_MS) {
                                client.fetchAllPages(
                                    relay,
                                    listOf(Filter(kinds = listOf(0, 10002), until = TRAP_CEILING)),
                                    idleTimeoutMs = 20_000L,
                                    onNewPage = { until ->
                                        pages++
                                        if (until < lowest) lowest = until
                                    },
                                ) { events++ }
                            }
                        }
                    }
                val took = System.currentTimeMillis() - startedAt
                val verdict =
                    outcome.fold(
                        onSuccess = { r ->
                            when (r) {
                                null -> "NEVER ENDED in ${TERMINATION_MS / 1000}s — THE GUARD IS NOT WORKING"
                                else -> "${r.end} (${r.downloaded} event(s), drained=${r.drained})"
                            }
                        },
                        onFailure = { "threw ${it::class.simpleName}: ${it.message}" },
                    )
                val reached = if (lowest == Long.MAX_VALUE) "no page after the first" else "$lowest"
                println("  %-26s %-52s".format(url.removePrefix("wss://"), verdict))
                println("  %-26s   %d page(s), %d event(s), %dms, lowest until=%s".format("", pages, events, took, reached))
            }
        } finally {
            runCatching { client.disconnect() }
            scope.cancel()
        }
        println("=".repeat(78))
    }

    companion object {
        /**
         * purplepag.es is the one that found this. The other four are controls: they
         * hold nothing at all below `1.5e9`, so they drain in a single page and prove
         * the guards did not change an ordinary walk.
         */
        private val RELAYS =
            listOf(
                "wss://purplepag.es",
                "wss://user.kindpag.es",
                "wss://directory.yabu.me",
                "wss://profiles.nostr1.com",
                "wss://indexer.coracle.social",
            )

        /** Just above the `created_at = 0` events, so one page reaches the cursor that matters. */
        private const val TRAP_CEILING = 1_600_000_000L

        /**
         * Not an idle timeout — the relay answers, with an EOSE, the entire time.
         * This is how long a walk gets to prove it can END.
         */
        private const val TERMINATION_MS = 45_000L
    }
}
