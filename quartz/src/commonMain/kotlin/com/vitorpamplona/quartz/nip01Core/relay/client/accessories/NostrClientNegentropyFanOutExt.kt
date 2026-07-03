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
package com.vitorpamplona.quartz.nip01Core.relay.client.accessories

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.single.newSubId
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.store.IdAndTime
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.coroutines.coroutineContext

/**
 * [negentropySync] scaled past the single-connection ceiling: ONE reconcile
 * (on `clients[0]`) feeding by-id download batches to EVERY client in
 * [clients] — each client being its own socket to the same [relay].
 *
 * Why this shape: a relay serves a single connection only so fast — the
 * production by-id matrix measured ~5.5k events/s per connection (saturating
 * at ~8 in-flight REQs) but ~15k events/s across 4 connections; see
 * `quartz/plans/2026-07-02-nostrclient-receiver-perf.md`. Reconciliation is
 * cheap and stays on one connection; the downloads are what need the fan-out.
 *
 * Mechanics:
 *  - `clients[0]` runs [negentropyReconcile] (windows, overflow splits,
 *    optional [localEntries] diffing — all identical semantics), streaming
 *    need-id batches into a bounded queue;
 *  - every client runs [reqsPerClient] download workers pulling batches off
 *    that shared queue ([fetchByIds]: one REQ per batch, collected to EOSE);
 *  - events funnel through a bounded channel to a single consumer, so
 *    [onEvent] runs single-threaded and [maxEvents] is exact; the bounded
 *    stages back-pressure all the way to the reconcile.
 *
 * Reconcile windows round-robin across the clients too (a single connection
 * produced need-ids at only ~9k/s on a 2.6M corpus and starved the download
 * workers). Subscription budget per connection is the caller's job (relays
 * cap concurrent subs; strfry defaults to 20): each client holds at peak
 * `reqsPerClient + ceil(reconcileConcurrency / clients.size) + 1`.
 *
 * Passing a single-element [clients] degrades gracefully to (pipelined)
 * [negentropySync] behavior. Duplicate clients in the list would just share
 * a connection — use distinct instances.
 *
 * @throws NegentropySyncException when a window cannot be reconciled (same
 *   contract as [negentropySync]); the whole fan-out is cancelled.
 */
@OptIn(ExperimentalAtomicApi::class)
suspend fun negentropySyncFanOut(
    clients: List<INostrClient>,
    relay: NormalizedRelayUrl,
    filter: Filter,
    localEntries: List<IdAndTime> = emptyList(),
    maxEvents: Int = 0,
    reqsPerClient: Int = 10,
    fetchBatch: Int = 250,
    idleTimeoutMs: Long = 120_000L,
    reconcileConcurrency: Int = 2,
    onProgress: ((needSoFar: Int, downloaded: Int) -> Unit)? = null,
    onEvent: (Event) -> Unit,
): NegentropyFanOutResult {
    require(clients.isNotEmpty()) { "at least one client is required" }

    val need = AtomicInt(0)
    val have = AtomicInt(0)
    val windows = AtomicInt(0)
    val used = AtomicInt(0)
    var downloaded = 0

    // Pin every client's connection open for the whole run: between two
    // batches (or two reconcile windows) a client briefly has no live
    // subscription and its pool would otherwise drop the relay.
    val keepAlives =
        clients.map { client ->
            val subId = newSubId()
            client.subscribe(subId, mapOf(relay to listOf(Filter(ids = listOf(KEEP_ALIVE_ID)))), null)
            client to subId
        }

    try {
        coroutineScope {
            // Reconcile → download handoff. Bounded so download saturation
            // back-pressures the reconcile instead of piling up ids.
            val idBatches = Channel<List<HexKey>>(clients.size * reqsPerClient * 2)

            // Download → consumer funnel; single consumer keeps onEvent
            // single-threaded and the maxEvents cap exact.
            val events = Channel<Event>(DELIVERY_BUFFER)

            val workers =
                clients.flatMap { client ->
                    List(reqsPerClient.coerceAtLeast(1)) {
                        launch {
                            var servedAny = false
                            for (batch in idBatches) {
                                coroutineContext.ensureActive()
                                if (!servedAny) {
                                    servedAny = true
                                    used.incrementAndFetch()
                                }
                                for (event in client.fetchByIds(relay, batch, idleTimeoutMs)) {
                                    events.send(event)
                                }
                            }
                        }
                    }
                }

            val producer =
                launch {
                    try {
                        val sorted =
                            if (localEntries.size > 1) localEntries.sortedBy { it.createdAt } else localEntries
                        // Windows reconcile round-robin ACROSS the clients so
                        // server-side snapshot builds parallelize per connection
                        // (a single connection produced ids at only ~9k/s and
                        // starved the download workers).
                        reconcileWindows(
                            clients = clients,
                            relay = relay,
                            filter = filter,
                            localEntries = sorted,
                            idleTimeoutMs = idleTimeoutMs,
                            batchSize = fetchBatch,
                            reconcileConcurrency = reconcileConcurrency,
                            onWindow = { windows.incrementAndFetch() },
                            onNeed = {
                                need.addAndFetch(it)
                            },
                            onHave = { have.addAndFetch(it) },
                            sendNeedBatch = { batch -> idBatches.send(batch) },
                            sendHaveBatch = if (localEntries.isEmpty()) null else { _ -> },
                        )
                    } finally {
                        idBatches.close()
                    }
                }

            val closer =
                launch {
                    producer.join()
                    workers.joinAll()
                    events.close()
                }

            for (event in events) {
                downloaded++
                onEvent(event)
                onProgress?.invoke(need.load(), downloaded)
                if (maxEvents in 1..downloaded) break
            }

            // Cap reached (or producer done): stop everything still running.
            producer.cancel()
            workers.forEach { it.cancel() }
            closer.cancel()
        }
    } finally {
        keepAlives.forEach { (client, subId) -> client.unsubscribe(subId) }
    }

    return NegentropyFanOutResult(
        needCount = need.load(),
        haveCount = have.load(),
        downloaded = downloaded,
        windows = windows.load(),
        connections = used.load(),
    )
}

/** Bounded buffer between download workers and the single delivery consumer. */
private const val DELIVERY_BUFFER = 256
