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
package com.vitorpamplona.relaybench.bench

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.vitorpamplona.relaybench.NostrSocket
import com.vitorpamplona.relaybench.Percentiles
import com.vitorpamplona.relaybench.Scenario
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient

/**
 * Read-path metrics for one [Scenario]:
 *
 * - **sequential**: R fresh REQ→EOSE rounds on a single connection —
 *   time-to-first-event and time-to-EOSE percentiles, plus the derived
 *   events/second a single client experiences.
 * - **concurrent**: the same REQ issued from C connections at once, each
 *   running R rounds — aggregate events/second, i.e. how the relay holds
 *   up when a popular filter is hot.
 *
 * The number of events each round returns is recorded so the harness can
 * flag relays that disagree about the result set (a correctness smell that
 * would invalidate the speed comparison).
 */
object QueryBenchmark {
    private val mapper = jacksonObjectMapper()

    data class ScenarioResult(
        val scenario: Scenario,
        val eventsPerRound: Int,
        val timeToFirst: Percentiles,
        val timeToEose: Percentiles,
        val sequentialEventsPerSec: Double,
        val concurrentEventsPerSec: Double,
        val concurrentConnections: Int,
    )

    suspend fun run(
        wsUrl: String,
        scenario: Scenario,
        warmupRounds: Int,
        rounds: Int,
        concurrency: Int,
        concurrentRounds: Int,
        http: OkHttpClient,
    ): ScenarioResult {
        // --- sequential ---
        val socket = NostrSocket.connect(http, wsUrl)
        val firstNanos = ArrayList<Long>(rounds)
        val eoseNanos = ArrayList<Long>(rounds)
        var eventsPerRound = 0
        try {
            repeat(warmupRounds + rounds) { round ->
                val r = oneRound(socket, scenario, "q$round")
                if (round >= warmupRounds) {
                    firstNanos += r.firstEventNanos
                    eoseNanos += r.eoseNanos
                    eventsPerRound = r.events
                }
            }
        } finally {
            socket.disconnect()
        }
        val totalSeqSec = eoseNanos.sum() / 1_000_000_000.0
        val seqEps = if (totalSeqSec > 0) eventsPerRound * rounds / totalSeqSec else 0.0

        // --- concurrent ---
        var concEps = 0.0
        if (concurrency > 1) {
            val start = System.nanoTime()
            val counts =
                withContext(Dispatchers.IO) {
                    (0 until concurrency)
                        .map { c ->
                            async {
                                val s = NostrSocket.connect(http, wsUrl)
                                try {
                                    var total = 0
                                    repeat(concurrentRounds) { round ->
                                        total += oneRound(s, scenario, "c$c-$round").events
                                    }
                                    total
                                } finally {
                                    s.disconnect()
                                }
                            }
                        }.awaitAll()
                }
            val wallSec = (System.nanoTime() - start) / 1_000_000_000.0
            concEps = if (wallSec > 0) counts.sum() / wallSec else 0.0
        }

        return ScenarioResult(
            scenario = scenario,
            eventsPerRound = eventsPerRound,
            timeToFirst = Percentiles.ofNanos(firstNanos),
            timeToEose = Percentiles.ofNanos(eoseNanos),
            sequentialEventsPerSec = seqEps,
            concurrentEventsPerSec = concEps,
            concurrentConnections = concurrency,
        )
    }

    private class Round(
        val events: Int,
        val firstEventNanos: Long,
        val eoseNanos: Long,
    )

    private suspend fun oneRound(
        socket: NostrSocket,
        scenario: Scenario,
        subId: String,
    ): Round =
        withTimeout(60_000) {
            val start = System.nanoTime()
            socket.req(subId, scenario.filterJson)
            var events = 0
            var firstAt = 0L
            for (raw in socket.incoming) {
                // Cheap dispatch on the frame prefix; EVENT frames don't
                // need full JSON parsing just to be counted.
                if (raw.startsWith("[\"EVENT\"")) {
                    if (firstAt == 0L) firstAt = System.nanoTime()
                    events++
                    continue
                }
                val node = runCatching { mapper.readTree(raw) }.getOrNull() ?: continue
                val type = node[0]?.asText()
                if ((type == "EOSE" || type == "CLOSED") && node[1]?.asText() == subId) break
            }
            val eoseAt = System.nanoTime()
            socket.close(subId)
            Round(
                events = events,
                firstEventNanos = (if (firstAt == 0L) eoseAt else firstAt) - start,
                eoseNanos = eoseAt - start,
            )
        }
}
