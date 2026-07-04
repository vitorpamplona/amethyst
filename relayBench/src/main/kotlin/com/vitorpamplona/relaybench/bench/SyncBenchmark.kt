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
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.NoticeMessage
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip77Negentropy.NegErrMessage
import com.vitorpamplona.quartz.nip77Negentropy.NegMsgMessage
import com.vitorpamplona.quartz.nip77Negentropy.NegentropySession
import com.vitorpamplona.relaybench.NostrSocket
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient

/**
 * NIP-77 negentropy sync between two relays, driven the way `strfry sync`
 * drives it: this harness plays the role of the syncing agent that holds
 * one relay's dataset locally and reconciles it against the other relay.
 *
 * Per pair, both relays get an 80% slice of the corpus with 60% overlap;
 * then we measure, in order:
 *
 *  1. **reconcile** against each relay as the NEG server (time, NEG-MSG
 *     round trips, wire bytes) — this is the interesting server-side
 *     data structure work;
 *  2. **delta transfer** — REQ the missing events off one side and
 *     publish them to the other, both directions, until every OK lands
 *     (plain NIP-01 follow-up, dominated by ingest speed);
 *  3. **re-reconcile with identical sets** — the steady-state "nothing to
 *     do" sync cost that a periodic mirror would pay.
 *
 * Reconciliation runs over the *effective* corpus — replaceable kinds
 * collapsed to their newest version — because that's what a relay stores;
 * superseded versions would otherwise show up as phantom diffs and the
 * pair would never converge.
 */
object SyncBenchmark {
    private val mapper = jacksonObjectMapper()

    data class ReconcileStats(
        val ms: Double,
        val rounds: Int,
        val wireBytes: Long,
        val needCount: Int,
        val haveCount: Int,
        val error: String?,
    )

    data class PairResult(
        val serverA: String,
        val serverB: String,
        val syncableEvents: Int,
        val initialReconcile: Map<String, ReconcileStats>,
        val transferMs: Double,
        val transferredToA: Int,
        val transferredToB: Int,
        val identicalReconcile: Map<String, ReconcileStats>,
        /**
         * A second identical-set reconcile fired immediately after the
         * first with no writes in between — the periodic-mirror
         * heartbeat ("anything new?" × N peers). Relays that keep a
         * live/cached reconciliation structure answer from it here;
         * relays that rebuild per NEG-OPEN pay the full cost again.
         */
        val repeatReconcile: Map<String, ReconcileStats> = emptyMap(),
        val converged: Boolean,
        val error: String? = null,
    )

    /** Newest version per replaceable/addressable key; everything else untouched. */
    fun effectiveEvents(events: List<Event>): List<Event> {
        val byKey = LinkedHashMap<String, Event>(events.size)
        for (e in events) {
            val key =
                when {
                    e.kind == 0 || e.kind == 3 || e.kind in 10000..19999 -> "${e.kind}:${e.pubKey}"
                    e.kind in 30000..39999 -> {
                        val d = e.tags.firstOrNull { it.size >= 2 && it[0] == "d" }?.get(1) ?: ""
                        "${e.kind}:${e.pubKey}:$d"
                    }
                    else -> e.id
                }
            val prev = byKey[key]
            if (prev == null || e.createdAt > prev.createdAt || (e.createdAt == prev.createdAt && e.id < prev.id)) {
                byKey[key] = e
            }
        }
        return byKey.values.toList()
    }

    class NegotiationResult(
        val haveIds: Set<HexKey>,
        val needIds: Set<HexKey>,
        val rounds: Int,
        val wireBytes: Long,
        val ms: Double,
        val error: String?,
    )

    /** One full NEG-OPEN → NEG-MSG* → NEG-CLOSE negotiation. */
    suspend fun reconcile(
        wsUrl: String,
        localEvents: List<Event>,
        http: OkHttpClient,
        maxRounds: Int = 128,
    ): NegotiationResult {
        val socket = NostrSocket.connect(http, wsUrl)
        val haveIds = mutableSetOf<HexKey>()
        val needIds = mutableSetOf<HexKey>()
        var rounds = 0
        var bytes = 0L
        val start = System.nanoTime()

        fun elapsedMs() = (System.nanoTime() - start) / 1_000_000.0

        try {
            val session = NegentropySession.fromEvents("bench-sync", Filter(), localEvents, frameSizeLimit = 0)
            val open = OptimizedJsonMapper.toJson(session.open())
            bytes += open.length
            check(socket.send(open)) { "send NEG-OPEN failed" }

            while (rounds < maxRounds) {
                val raw = withTimeout(60_000) { socket.incoming.receive() }
                if (raw.startsWith("[\"EVENT\"") || raw.startsWith("[\"OK\"")) continue
                bytes += raw.length
                rounds++
                when (val msg = OptimizedJsonMapper.fromJsonToMessage(raw)) {
                    is NegErrMessage -> return NegotiationResult(haveIds, needIds, rounds, bytes, elapsedMs(), "NEG-ERR: ${msg.reason}")
                    is NoticeMessage -> return NegotiationResult(haveIds, needIds, rounds, bytes, elapsedMs(), "NOTICE: ${msg.message}")
                    is NegMsgMessage -> {
                        val r = session.processMessage(msg.message)
                        haveIds += r.haveIds
                        needIds += r.needIds
                        if (r.isComplete()) {
                            socket.send("""["NEG-CLOSE","bench-sync"]""")
                            return NegotiationResult(haveIds, needIds, rounds, bytes, elapsedMs(), null)
                        }
                        val next = OptimizedJsonMapper.toJson(r.nextCmd!!)
                        bytes += next.length
                        check(socket.send(next)) { "send NEG-MSG failed" }
                    }
                    else -> {} // stray frame from another subsystem; ignore
                }
            }
            return NegotiationResult(haveIds, needIds, rounds, bytes, elapsedMs(), "no convergence in $maxRounds rounds")
        } finally {
            socket.disconnect()
        }
    }

    /** REQ [ids] off [fromUrl] in ≤100-id batches; returns the events. */
    suspend fun fetchByIds(
        fromUrl: String,
        ids: Collection<HexKey>,
        http: OkHttpClient,
    ): List<Event> {
        if (ids.isEmpty()) return emptyList()
        val socket = NostrSocket.connect(http, fromUrl)
        val fetched = ArrayList<Event>(ids.size)
        try {
            for ((batchIdx, batch) in ids.chunked(100).withIndex()) {
                withTimeout(60_000) {
                    val subId = "fetch-$batchIdx"
                    socket.req(subId, Filter(ids = batch).toJson())
                    for (raw in socket.incoming) {
                        val node = runCatching { mapper.readTree(raw) }.getOrNull() ?: continue
                        if (node[1]?.asText() != subId) continue
                        when (node[0]?.asText()) {
                            "EVENT" ->
                                runCatching { OptimizedJsonMapper.fromJson(node[2].toString()) }
                                    .getOrNull()
                                    ?.let { fetched.add(it) }
                            "EOSE", "CLOSED" -> {
                                socket.close(subId)
                                return@withTimeout
                            }
                        }
                    }
                }
            }
        } finally {
            socket.disconnect()
        }
        return fetched
    }

    /**
     * Full pair benchmark. [urlA]/[urlB] must already hold [sliceA]/[sliceB]
     * (both slices of [effective], which must be replaceable-collapsed).
     */
    suspend fun runPair(
        nameA: String,
        urlA: String,
        nameB: String,
        urlB: String,
        effective: List<Event>,
        sliceA: List<Event>,
        sliceB: List<Event>,
        window: Int,
        http: OkHttpClient,
        log: (String) -> Unit,
    ): PairResult {
        val idsA = sliceA.mapTo(HashSet()) { it.id }
        val idsB = sliceB.mapTo(HashSet()) { it.id }

        // 1. Initial reconcile against each side as server.
        log("    reconciling (local=$nameA's ${sliceA.size} events) against $nameB…")
        val againstB = reconcile(urlB, sliceA, http)
        log("      ${againstB.rounds} rounds, need=${againstB.needIds.size} have=${againstB.haveIds.size} in ${"%.0f".format(againstB.ms)} ms")
        log("    reconciling (local=$nameB's ${sliceB.size} events) against $nameA…")
        val againstA = reconcile(urlA, sliceB, http)
        log("      ${againstA.rounds} rounds, need=${againstA.needIds.size} have=${againstA.haveIds.size} in ${"%.0f".format(againstA.ms)} ms")

        val expectedOnlyB = idsB.count { it !in idsA }
        val expectedOnlyA = idsA.count { it !in idsB }
        val diffCorrect =
            againstB.error == null &&
                againstA.error == null &&
                againstB.needIds.size == expectedOnlyB &&
                againstB.haveIds.size == expectedOnlyA &&
                againstA.needIds.size == expectedOnlyA &&
                againstA.haveIds.size == expectedOnlyB

        // 2. Delta transfer: close the diff in both directions.
        val transferStart = System.nanoTime()
        val byId = effective.associateBy { it.id }
        val toA = fetchByIds(urlB, againstB.needIds, http)
        val toB = againstB.haveIds.mapNotNull { byId[it] }
        coroutineScope {
            listOf(
                async { if (toA.isNotEmpty()) IngestBenchmark.throughput(urlA, toA, 1, window, http) },
                async { if (toB.isNotEmpty()) IngestBenchmark.throughput(urlB, toB, 1, window, http) },
            ).awaitAll()
        }
        val transferMs = (System.nanoTime() - transferStart) / 1_000_000.0
        log("    delta transfer: ${toA.size} → $nameA, ${toB.size} → $nameB in ${"%.0f".format(transferMs)} ms")

        // 3. Steady-state: reconcile identical sets.
        log("    re-reconciling identical sets…")
        val identicalA = reconcile(urlA, effective, http)
        val identicalB = reconcile(urlB, effective, http)

        // Diagnostic: when a side didn't converge to `effective`, name the
        // events it's missing (present in the reference, absent from the
        // relay) by kind — that identifies the ingest-semantics mismatch
        // (NIP-09 deletion / NIP-40 expiration / validation) rather than
        // leaving a bare "did not converge". `haveIds` are ids the initiator
        // (holding `effective`) has that the server lacks.
        fun reportMissing(
            serverName: String,
            r: NegotiationResult,
        ) {
            val missing = r.haveIds.mapNotNull { byId[it] }
            if (missing.isEmpty()) return
            val byKind =
                missing
                    .groupingBy { it.kind }
                    .eachCount()
                    .entries
                    .sortedByDescending { it.value }
            log("    ⚠ $serverName is missing ${r.haveIds.size} events the reference set has:")
            log("      by kind: " + byKind.joinToString(" ") { "k${it.key}:${it.value}" })
            missing.take(5).forEach { e ->
                val tagKeys =
                    e.tags
                        .mapNotNull { it.getOrNull(0) }
                        .distinct()
                        .joinToString(",")
                log("      e.g. kind=${e.kind} created_at=${e.createdAt} id=${e.id.take(12)}… tags=[$tagKeys]")
            }
        }
        reportMissing(nameA, identicalA)
        reportMissing(nameB, identicalB)

        // 4. Heartbeat: same reconcile again, immediately, no writes in
        // between — measures whether the server keeps its reconciliation
        // structure warm across NEG-OPENs.
        log("    repeating identical-set reconcile (warm)…")
        val repeatA = reconcile(urlA, effective, http)
        val repeatB = reconcile(urlB, effective, http)
        val converged =
            diffCorrect &&
                identicalA.error == null &&
                identicalB.error == null &&
                identicalA.needIds.isEmpty() &&
                identicalA.haveIds.isEmpty() &&
                identicalB.needIds.isEmpty() &&
                identicalB.haveIds.isEmpty()

        fun stats(r: NegotiationResult) = ReconcileStats(r.ms, r.rounds, r.wireBytes, r.needIds.size, r.haveIds.size, r.error)

        return PairResult(
            serverA = nameA,
            serverB = nameB,
            syncableEvents = effective.size,
            initialReconcile = mapOf(nameA to stats(againstA), nameB to stats(againstB)),
            transferMs = transferMs,
            transferredToA = toA.size,
            transferredToB = toB.size,
            identicalReconcile = mapOf(nameA to stats(identicalA), nameB to stats(identicalB)),
            repeatReconcile = mapOf(nameA to stats(repeatA), nameB to stats(repeatB)),
            converged = converged,
        )
    }
}
