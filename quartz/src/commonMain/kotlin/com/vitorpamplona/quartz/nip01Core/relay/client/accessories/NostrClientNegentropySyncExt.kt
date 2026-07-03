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
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.client.single.newSubId
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.store.IdAndTime
import com.vitorpamplona.quartz.nip77Negentropy.NegErrMessage
import com.vitorpamplona.quartz.nip77Negentropy.NegMsgMessage
import com.vitorpamplona.quartz.nip77Negentropy.NegentropySession
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.decrementAndFetch
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.coroutines.coroutineContext
import kotlin.math.min
import kotlin.time.TimeSource

/**
 * Outcome of a successful [negentropySync] run.
 *
 * @property needCount  ids the relay had that we lacked (i.e. everything that
 *   matched [Filter] on the relay — this sync always reconciles against an empty
 *   local set, so it downloads the full matched set).
 * @property haveCount  ids we had that the relay lacked. Always `0` here because
 *   the local set is empty; kept so the result mirrors a full NIP-77 reconcile.
 * @property downloaded distinct events actually delivered through `onEvent`.
 * @property windows    number of `created_at` windows the matched set was split
 *   into (`1` when the relay reconciled the whole filter in one shot).
 */
class NegentropySyncResult(
    val needCount: Int,
    val haveCount: Int,
    val downloaded: Int,
    val windows: Int,
)

/**
 * Downloads every event a single [relay] holds matching [filter], delivering each
 * one (deduped by id) through [onEvent]. A high-level wrapper over NIP-77
 * negentropy that hides the parts that make the raw protocol painful to use:
 *
 *  1. Reconciles the relay's matched set against an empty local set, **streaming**
 *     the ids the relay has straight into the download pipeline as each NIP-77
 *     round arrives — the full id list is never materialised.
 *  2. Downloads those ids through at most [maxConcurrentReqs] concurrent `REQ`
 *     subscriptions of [fetchBatch] ids each, refilling as each `EOSE` arrives. The
 *     reconciliation, the id queue and event delivery are all back-pressured, so a
 *     slow consumer throttles the whole chain and **peak memory is bounded by the
 *     pipeline depth, not by the window size** — a multi-million-event window
 *     streams through in roughly constant memory. No id/event dedup set is held:
 *     NIP-77 yields a distinct id set, so each event is requested (and returned)
 *     exactly once.
 *  3. Handles the relay-side cap on negentropy (strfry's `max_sync_events`,
 *     observed as `NEG-ERR … "blocked: too many query results"`): the [filter] is
 *     split by `created_at` windows and each window reconciled on its own, a
 *     window that still overflows being halved and retried.
 *
 * This method is negentropy-only. It does NOT silently fall back to plain paging:
 * if a window genuinely cannot be reconciled — a minimal `created_at` window still
 * over the relay's cap, or a relay that does not speak NIP-77 / drops the session /
 * times out — it throws [NegentropySyncException] so the caller chooses what to do.
 * For the common "try negentropy, else page" shape, use [negentropySyncOrFetch].
 *
 * Scope is controlled entirely by [filter] — narrow it (kinds, authors, `since`,
 * tags, …) to download a slice instead of everything. [maxEvents] additionally caps
 * the delivered set.
 *
 * Coroutine-cancellable: on completion, cancel, reaching [maxEvents], or a thrown
 * [NegentropySyncException], all `REQ` subscriptions are unsubscribed and the
 * negentropy session is closed and its listener removed, so nothing leaks.
 *
 * @throws NegentropySyncException when a window cannot be reconciled via NIP-77.
 *
 * @param relay             the relay to sync from.
 * @param filter            what to download. A single filter (NEG-OPEN is single-filter).
 * @param maxEvents         stop after delivering this many distinct events. `0` = unlimited.
 * @param maxConcurrentReqs upper bound on simultaneously-open download `REQ`s. Keep
 *   it at or below the relay's per-connection subscription cap.
 * @param fetchBatch        ids per download `REQ`.
 * @param idleTimeoutMs     the idle watchdog: the maximum time the relay may go
 *   **completely silent** before the sync gives up. It is NOT a per-round deadline —
 *   it **resets on every message the relay sends** (each NIP-77 round, every download
 *   `EOSE`/event) and on connect. So a genuinely slow but progressing sync runs for as
 *   long as it needs: only true silence trips it. This matters because the relay
 *   builds its whole negentropy snapshot before the FIRST round responds — O(matched
 *   set), a minute or more for a multi-million-event filter — and that first wait is a
 *   real silence, so keep this comfortably above the largest expected first-round build.
 *   A dead/half-open socket does NOT depend on this: the WebSocket keep-alive detects
 *   it and the disconnect is turned into a clean abort. Pass `0` to disable the
 *   watchdog entirely and run until the socket drops (download batches keep a finite
 *   internal idle bound regardless, so a single stuck batch can't hang the pipeline).
 * @param reconcileConcurrency how many `created_at` windows are reconciled at once
 *   after an over-cap split (each holds one NEG session on the connection). `1`
 *   reproduces the old strictly-sequential window walk. Raising it overlaps the
 *   reconcile round-trips of one window with another's — the reconcile cadence is
 *   what starves the download workers on large sets.
 * @param idBufferBatches   depth (in batches of [fetchBatch] ids) of the buffer
 *   between reconciliation and the download workers. Deeper keeps the workers fed
 *   across a window's round-trip gaps; memory is bounded by
 *   `idBufferBatches * fetchBatch` ids.
 *
 *   **Subscription budget is the caller's job:** at peak this method holds
 *   `maxConcurrentReqs + reconcileConcurrency + 1` (keep-alive) subscriptions on
 *   the connection. Relays cap concurrent subscriptions per connection (NIP-11
 *   `limitation.max_subscriptions`; e.g. strfry defaults to 20) and exceeding the
 *   cap can wedge the connection, not just fail the extra REQ — size the two knobs
 *   to fit the target relay.
 * @param onProgress        optional `(needSoFar, downloaded)` ticks as work proceeds.
 * @param onEvent           called once per distinct event, on the relay reader thread.
 */
@OptIn(ExperimentalAtomicApi::class)
suspend fun INostrClient.negentropySync(
    relay: NormalizedRelayUrl,
    filter: Filter,
    maxEvents: Int = 0,
    maxConcurrentReqs: Int = 8,
    fetchBatch: Int = 500,
    idleTimeoutMs: Long = 120_000L,
    reconcileConcurrency: Int = 1,
    idBufferBatches: Int = maxConcurrentReqs * 4,
    onProgress: ((needSoFar: Int, downloaded: Int) -> Unit)? = null,
    onEvent: (Event) -> Unit,
): NegentropySyncResult {
    val need = AtomicInt(0)
    val windows = AtomicInt(0)
    var downloaded = 0

    // Pin the relay in the pool's "desired" set for the whole sync. A NEG-OPEN is not
    // a REQ, so during a reconcile round (before that window's first download REQ
    // exists) the relay would otherwise look unwanted and the pool would disconnect
    // it — fatal mid-sync, and frequent when many small windows each have such a gap.
    // A never-matching keep-alive subscription holds the connection open without
    // delivering anything.
    val keepAliveSubId = newSubId()
    subscribe(keepAliveSubId, mapOf(relay to listOf(Filter(ids = listOf(KEEP_ALIVE_ID)))), null)
    try {
        coroutineScope {
            // Bounded funnel: every delivered event passes through this one consumer
            // (so onEvent + the maxEvents cap run single-threaded) and the bound
            // back-pressures the download workers when the consumer can't keep up.
            val events = Channel<Event>(DELIVERY_BUFFER)

            val producer =
                launch {
                    try {
                        syncPipeline(
                            relay = relay,
                            filter = filter,
                            idleTimeoutMs = idleTimeoutMs,
                            fetchBatch = fetchBatch,
                            maxConcurrentReqs = maxConcurrentReqs,
                            reconcileConcurrency = reconcileConcurrency,
                            idBufferBatches = idBufferBatches,
                            onWindow = { windows.incrementAndFetch() },
                            // Only accumulate here; progress is reported from the
                            // single consumer loop below so the user callback is never
                            // invoked from two coroutines at once.
                            onNeed = { need.addAndFetch(it) },
                            deliver = { events.send(it) },
                        )
                    } finally {
                        events.close()
                    }
                }

            for (event in events) {
                downloaded++
                onEvent(event)
                onProgress?.invoke(need.load(), downloaded)
                if (maxEvents in 1..downloaded) break
            }

            // If we broke out early (cap reached) the producer may still be working —
            // stop it. If the producer finished normally this is a no-op.
            producer.cancel()
        }
    } finally {
        unsubscribe(keepAliveSubId)
    }

    return NegentropySyncResult(
        needCount = need.load(),
        haveCount = 0,
        downloaded = downloaded,
        windows = windows.load(),
    )
}

suspend fun INostrClient.negentropySync(
    relay: String,
    filter: Filter,
    maxEvents: Int = 0,
    maxConcurrentReqs: Int = 8,
    fetchBatch: Int = 500,
    idleTimeoutMs: Long = 120_000L,
    reconcileConcurrency: Int = 1,
    idBufferBatches: Int = maxConcurrentReqs * 4,
    onProgress: ((needSoFar: Int, downloaded: Int) -> Unit)? = null,
    onEvent: (Event) -> Unit,
): NegentropySyncResult =
    negentropySync(
        relay = RelayUrlNormalizer.normalize(relay),
        filter = filter,
        maxEvents = maxEvents,
        maxConcurrentReqs = maxConcurrentReqs,
        fetchBatch = fetchBatch,
        idleTimeoutMs = idleTimeoutMs,
        reconcileConcurrency = reconcileConcurrency,
        idBufferBatches = idBufferBatches,
        onProgress = onProgress,
        onEvent = onEvent,
    )

/**
 * Result of [negentropySyncOrFetch].
 *
 * @property downloaded   distinct events delivered through `onEvent` (across whichever
 *   path ran).
 * @property pagedFallback `true` if negentropy could not reconcile and the events
 *   came from [fetchAllPages] instead.
 * @property negentropy    the negentropy outcome when it succeeded; `null` on fallback.
 * @property fallbackCause why negentropy was abandoned; `null` when it succeeded.
 */
class NegentropyOrFetchResult(
    val downloaded: Int,
    val pagedFallback: Boolean,
    val negentropy: NegentropySyncResult?,
    val fallbackCause: NegentropySyncException?,
)

/**
 * "Try negentropy, else page." Runs [negentropySync] and, if it throws
 * [NegentropySyncException] (relay can't reconcile the set — no NIP-77 support, an
 * over-cap minimal window, a disconnect, …), transparently falls back to
 * [fetchAllPages] over the same [filter].
 *
 * This is the convenience combinator for the common case where you just want the
 * events and don't care which transport delivered them. Events are deduped by id
 * across both phases, so anything the negentropy attempt already delivered before
 * failing is not delivered again by the paging phase. [maxEvents] is honored across
 * both phases.
 *
 * Use [negentropySync] directly if you want to decide the fallback yourself (try
 * another relay, narrow the filter, abort, …) instead of always paging.
 */
suspend fun INostrClient.negentropySyncOrFetch(
    relay: NormalizedRelayUrl,
    filter: Filter,
    maxEvents: Int = 0,
    maxConcurrentReqs: Int = 8,
    fetchBatch: Int = 500,
    idleTimeoutMs: Long = 120_000L,
    reconcileConcurrency: Int = 1,
    idBufferBatches: Int = maxConcurrentReqs * 4,
    onProgress: ((needSoFar: Int, downloaded: Int) -> Unit)? = null,
    onEvent: (Event) -> Unit,
): NegentropyOrFetchResult {
    val seen = HashSet<HexKey>()
    var delivered = 0

    // Shared dedup + cap across both phases. Returns true if the event was new and
    // delivered. Both phases run sequentially, so no concurrent access.
    fun accept(event: Event): Boolean {
        if ((maxEvents <= 0 || delivered < maxEvents) && seen.add(event.id)) {
            delivered++
            onEvent(event)
            return true
        }
        return false
    }

    return try {
        val result =
            negentropySync(
                relay = relay,
                filter = filter,
                maxEvents = maxEvents,
                maxConcurrentReqs = maxConcurrentReqs,
                fetchBatch = fetchBatch,
                idleTimeoutMs = idleTimeoutMs,
                reconcileConcurrency = reconcileConcurrency,
                idBufferBatches = idBufferBatches,
                onProgress = onProgress,
            ) { accept(it) }
        NegentropyOrFetchResult(delivered, pagedFallback = false, negentropy = result, fallbackCause = null)
    } catch (e: NegentropySyncException) {
        // Negentropy couldn't enumerate the set — page the whole filter instead,
        // skipping anything the negentropy attempt already delivered. fetchAllPages
        // has no "no timeout" mode, so a disabled watchdog maps to a finite page bound.
        val pageFilter = if (maxEvents > 0) filter.copy(limit = maxEvents) else filter
        val pageTimeoutMs = if (idleTimeoutMs > 0) idleTimeoutMs else DEFAULT_DOWNLOAD_IDLE_MS
        fetchAllPages(relay, listOf(pageFilter), pageTimeoutMs) { event ->
            if (accept(event)) onProgress?.invoke(delivered, delivered)
        }
        NegentropyOrFetchResult(delivered, pagedFallback = true, negentropy = null, fallbackCause = e)
    }
}

suspend fun INostrClient.negentropySyncOrFetch(
    relay: String,
    filter: Filter,
    maxEvents: Int = 0,
    maxConcurrentReqs: Int = 8,
    fetchBatch: Int = 500,
    idleTimeoutMs: Long = 120_000L,
    reconcileConcurrency: Int = 1,
    idBufferBatches: Int = maxConcurrentReqs * 4,
    onProgress: ((needSoFar: Int, downloaded: Int) -> Unit)? = null,
    onEvent: (Event) -> Unit,
): NegentropyOrFetchResult =
    negentropySyncOrFetch(
        relay = RelayUrlNormalizer.normalize(relay),
        filter = filter,
        maxEvents = maxEvents,
        maxConcurrentReqs = maxConcurrentReqs,
        fetchBatch = fetchBatch,
        idleTimeoutMs = idleTimeoutMs,
        reconcileConcurrency = reconcileConcurrency,
        idBufferBatches = idBufferBatches,
        onProgress = onProgress,
        onEvent = onEvent,
    )

/**
 * The whole-sync pipeline: a single pool of [maxConcurrentReqs] download workers
 * fed by up to [reconcileConcurrency] concurrent window reconciliations through a
 * bounded buffer of [idBufferBatches] id batches.
 *
 * Why this shape (measured on a 2.6M-event production set — see
 * `quartz/plans/2026-07-02-nostrclient-receiver-perf.md`):
 *
 *  - The workers are GLOBAL, not per-window: with the old per-window pool the
 *    next window's reconcile round-trips only started after the previous
 *    window's last download joined, so the connection idled between windows.
 *  - Overflow-split windows go into a shared work queue processed by
 *    [reconcileConcurrency] reconcilers, overlapping their NEG round-trips.
 *    Each active reconcile holds one NEG session on the connection.
 *  - The id buffer decouples the bursty reconcile id stream from the steady
 *    download stream; it stays bounded so a slow consumer still back-pressures
 *    the relay (memory is O(idBufferBatches × fetchBatch) ids).
 *
 * Throws [NegentropySyncException] for any window negentropy cannot reconcile (a
 * minimal window still over the cap, or an unavailable/erroring relay); the
 * failure cancels the whole pipeline.
 */
@OptIn(ExperimentalAtomicApi::class)
private suspend fun INostrClient.syncPipeline(
    relay: NormalizedRelayUrl,
    filter: Filter,
    idleTimeoutMs: Long,
    fetchBatch: Int,
    maxConcurrentReqs: Int,
    reconcileConcurrency: Int,
    idBufferBatches: Int,
    onWindow: () -> Unit,
    onNeed: (Int) -> Unit,
    deliver: suspend (Event) -> Unit,
) = coroutineScope {
    val idBatches = Channel<List<HexKey>>(idBufferBatches.coerceAtLeast(1))

    val workers =
        List(maxConcurrentReqs.coerceAtLeast(1)) {
            launch {
                for (batch in idBatches) {
                    coroutineContext.ensureActive()
                    for (event in fetchByIds(relay, batch, idleTimeoutMs)) {
                        deliver(event)
                    }
                }
            }
        }

    reconcileWindows(
        clients = listOf(this@syncPipeline),
        relay = relay,
        filter = filter,
        localEntries = emptyList(),
        idleTimeoutMs = idleTimeoutMs,
        batchSize = fetchBatch,
        reconcileConcurrency = reconcileConcurrency,
        onWindow = onWindow,
        onNeed = onNeed,
        onHave = {},
        sendNeedBatch = { batch -> idBatches.send(batch) },
        sendHaveBatch = null,
    )

    idBatches.close()
    workers.joinAll()
}

/**
 * The shared window engine behind [negentropySync] and [negentropyReconcile]:
 * reconciles [filter] against [localEntries], splitting into `created_at`
 * windows whenever the relay rejects the set as too large, with up to
 * [reconcileConcurrency] windows reconciling at once from a shared work
 * queue. Each window's local subset is sliced out of [localEntries] (which
 * MUST be sorted by `createdAt`) so both sides always reconcile the same
 * slice of the timeline.
 *
 * Throws [NegentropySyncException] for any window negentropy cannot reconcile
 * (a minimal window still over the cap, or an unavailable/erroring relay); the
 * failure cancels the whole scope.
 */
@OptIn(ExperimentalAtomicApi::class)
internal suspend fun reconcileWindows(
    // one or more connections to the SAME relay; reconciler i runs its NEG
    // sessions on clients[i % size], so concurrent windows spread across
    // connections (server-side snapshot builds are paced per connection)
    clients: List<INostrClient>,
    relay: NormalizedRelayUrl,
    filter: Filter,
    localEntries: List<IdAndTime>,
    idleTimeoutMs: Long,
    batchSize: Int,
    reconcileConcurrency: Int,
    onWindow: () -> Unit,
    onNeed: (Int) -> Unit,
    onHave: (Int) -> Unit,
    sendNeedBatch: suspend (List<HexKey>) -> Unit,
    sendHaveBatch: (suspend (List<HexKey>) -> Unit)?,
) = coroutineScope {
    // Windows waiting for (or under) reconciliation. UNLIMITED so a reconciler
    // re-queueing an overflow split never suspends while holding queue capacity
    // (the split fan-out is tiny: two Filters per overflow).
    val pending = Channel<Filter>(Channel.UNLIMITED)

    // Queued-or-running windows. An overflow replaces one window with two
    // (net +1); a completion is -1; the queue closes when it hits zero.
    val remaining = AtomicInt(1)
    pending.send(filter)

    val reconcilers =
        List(reconcileConcurrency.coerceAtLeast(1)) { reconcilerIndex ->
            launch {
                val client = clients[reconcilerIndex % clients.size]
                for (window in pending) {
                    coroutineContext.ensureActive()

                    val outcome =
                        client.reconcileStreaming(
                            relay = relay,
                            filter = window,
                            localEntries = entriesForWindow(localEntries, window.since, window.until),
                            idleTimeoutMs = idleTimeoutMs,
                            fetchBatch = batchSize,
                            onNeed = onNeed,
                            onHave = onHave,
                            sendNeedBatch = sendNeedBatch,
                            sendHaveBatch = sendHaveBatch,
                        )

                    when (outcome) {
                        is ReconcileOutcome.Complete -> {
                            onWindow()
                            if (remaining.decrementAndFetch() == 0) pending.close()
                        }

                        is ReconcileOutcome.Overflow -> {
                            val lo = window.since ?: 0L
                            val hi = window.until ?: TimeUtils.now()
                            if (hi - lo <= MIN_WINDOW_SECONDS) {
                                // A minimal window that still overflows: negentropy
                                // genuinely can't enumerate this slice. Surface it —
                                // paging is the caller's call.
                                throw NegentropySyncException(
                                    relay = relay,
                                    window = window,
                                    reason = NegentropySyncException.Reason.OVER_MAX_SYNC_EVENTS,
                                    detail = "created_at window [$lo, $hi] still exceeds the relay's max_sync_events",
                                )
                            }
                            val mid = lo + (hi - lo) / 2
                            remaining.incrementAndFetch()
                            pending.send(window.copy(since = lo, until = mid))
                            pending.send(window.copy(since = mid + 1, until = hi))
                        }

                        is ReconcileOutcome.Failed ->
                            throw NegentropySyncException(
                                relay = relay,
                                window = window,
                                reason = NegentropySyncException.Reason.UNAVAILABLE,
                                detail = outcome.detail,
                            )
                    }
                }
            }
        }

    reconcilers.joinAll()
}

/**
 * The `createdAt`-range slice of [sorted] (ascending by `createdAt`) that
 * belongs to the window `[since, until]` (both inclusive, NIP-01 semantics).
 * Binary-searched so window splits stay O(log n) over multi-million local sets.
 */
private fun entriesForWindow(
    sorted: List<IdAndTime>,
    since: Long?,
    until: Long?,
): List<IdAndTime> {
    if (sorted.isEmpty() || (since == null && until == null)) return sorted

    val lo = since ?: 0L
    val hi = until ?: Long.MAX_VALUE

    // first index with createdAt >= lo
    var start = 0
    var e = sorted.size
    while (start < e) {
        val mid = (start + e) ushr 1
        if (sorted[mid].createdAt < lo) start = mid + 1 else e = mid
    }

    // first index with createdAt > hi
    var end = start
    e = sorted.size
    while (end < e) {
        val mid = (end + e) ushr 1
        if (sorted[mid].createdAt <= hi) end = mid + 1 else e = mid
    }

    return if (start >= end) emptyList() else sorted.subList(start, end)
}

private sealed interface ReconcileOutcome {
    /** Reconciliation completed; every id was streamed to the downloader. */
    object Complete : ReconcileOutcome

    /** Relay rejected the set as too large (strfry `max_sync_events`). */
    object Overflow : ReconcileOutcome

    /** Reconciliation could not complete; [detail] says why. */
    class Failed(
        val detail: String,
    ) : ReconcileOutcome
}

/**
 * Outcome of a [negentropyReconcile] run.
 *
 * @property needCount ids the relay has that the local set lacks (streamed to `onNeedIds`).
 * @property haveCount ids the local set has that the relay lacks (streamed to `onHaveIds`).
 * @property windows   number of `created_at` windows the reconcile split into.
 */
class NegentropyReconcileResult(
    val needCount: Int,
    val haveCount: Int,
    val windows: Int,
)

/**
 * Pure NIP-77 reconciliation — no downloads, no uploads. Diffs the relay's
 * matched set for [filter] against [localEntries] and streams the two
 * directions of the diff to the caller, who decides what to do with them:
 *
 *  - **need ids** (`onNeedIds`): the relay has them, the local set doesn't —
 *    fetch them however fits (own REQ fan-out across several connections or
 *    clients, batching, prioritization, …). The by-id fetch matrix in
 *    `quartz/plans/2026-07-02-nostrclient-receiver-perf.md` is the map for
 *    that fan-out.
 *  - **have ids** (`onHaveIds`): the local set has them, the relay doesn't —
 *    publish the corresponding events to push the relay up to date.
 *
 * Compared to [negentropySync], which couples the reconcile to a built-in
 * by-id downloader on the same connection, this is the composable half:
 * `negentropyReconcile` + caller-side loading is how to sync faster than one
 * connection allows.
 *
 * Ids are streamed in chunks of [batchSize] as reconcile rounds arrive — the
 * full id set is never materialized here (accumulate them yourself or use
 * [negentropyReconcileIds] when the set is known to be small). Both callbacks
 * suspend the reconcile round that produced them, so a slow consumer
 * back-pressures the relay. With [reconcileConcurrency] > 1 the callbacks are
 * invoked from that many coroutines concurrently.
 *
 * Relay-side overflow (strfry `max_sync_events`) is handled by `created_at`
 * window splitting, like [negentropySync]. [localEntries] may be in any order;
 * each window reconciles against the matching `createdAt` slice. The caller
 * is responsible for [localEntries] actually being the local events matching
 * [filter] — entries outside the filter would show up as false "have" ids.
 *
 * Holds `reconcileConcurrency` NEG sessions plus one keep-alive subscription
 * on the connection; budget that against the relay's
 * `limitation.max_subscriptions`.
 *
 * @throws NegentropySyncException when a window cannot be reconciled via NIP-77.
 */
@OptIn(ExperimentalAtomicApi::class)
suspend fun INostrClient.negentropyReconcile(
    relay: NormalizedRelayUrl,
    filter: Filter,
    localEntries: List<IdAndTime> = emptyList(),
    batchSize: Int = 500,
    idleTimeoutMs: Long = 120_000L,
    reconcileConcurrency: Int = 1,
    onHaveIds: (suspend (List<HexKey>) -> Unit)? = null,
    onNeedIds: suspend (List<HexKey>) -> Unit,
): NegentropyReconcileResult {
    val need = AtomicInt(0)
    val have = AtomicInt(0)
    val windows = AtomicInt(0)

    // Same connection-pinning trick as negentropySync: a NEG-OPEN is not a REQ,
    // so without a live subscription the pool would consider the relay unwanted
    // and disconnect it mid-reconcile.
    val keepAliveSubId = newSubId()
    subscribe(keepAliveSubId, mapOf(relay to listOf(Filter(ids = listOf(KEEP_ALIVE_ID)))), null)
    try {
        val sorted =
            if (localEntries.size > 1) {
                localEntries.sortedBy { it.createdAt }
            } else {
                localEntries
            }

        reconcileWindows(
            clients = listOf(this),
            relay = relay,
            filter = filter,
            localEntries = sorted,
            idleTimeoutMs = idleTimeoutMs,
            batchSize = batchSize,
            reconcileConcurrency = reconcileConcurrency,
            onWindow = { windows.incrementAndFetch() },
            onNeed = { need.addAndFetch(it) },
            onHave = { have.addAndFetch(it) },
            sendNeedBatch = onNeedIds,
            sendHaveBatch = onHaveIds,
        )
    } finally {
        unsubscribe(keepAliveSubId)
    }

    return NegentropyReconcileResult(
        needCount = need.load(),
        haveCount = have.load(),
        windows = windows.load(),
    )
}

suspend fun INostrClient.negentropyReconcile(
    relay: String,
    filter: Filter,
    localEntries: List<IdAndTime> = emptyList(),
    batchSize: Int = 500,
    idleTimeoutMs: Long = 120_000L,
    reconcileConcurrency: Int = 1,
    onHaveIds: (suspend (List<HexKey>) -> Unit)? = null,
    onNeedIds: suspend (List<HexKey>) -> Unit,
): NegentropyReconcileResult =
    negentropyReconcile(
        relay = RelayUrlNormalizer.normalize(relay),
        filter = filter,
        localEntries = localEntries,
        batchSize = batchSize,
        idleTimeoutMs = idleTimeoutMs,
        reconcileConcurrency = reconcileConcurrency,
        onHaveIds = onHaveIds,
        onNeedIds = onNeedIds,
    )

/**
 * The full id diff from a [negentropyReconcile] run, materialized.
 *
 * @property needIds relay has them, the local set doesn't — download these.
 * @property haveIds local set has them, the relay doesn't — publish these.
 * @property windows number of `created_at` windows the reconcile split into.
 */
class NegentropyIdDiff(
    val needIds: List<HexKey>,
    val haveIds: List<HexKey>,
    val windows: Int,
)

/**
 * Convenience over [negentropyReconcile] that accumulates both directions of
 * the diff and returns them as lists.
 *
 * Materializes the FULL diff in memory: at ~100 B per id string a
 * million-id diff is ~100 MB of heap. Fine for bounded sets (per-author
 * sync, recent windows); for open-ended bulk syncs prefer the streaming
 * [negentropyReconcile] and consume batches as they arrive.
 */
suspend fun INostrClient.negentropyReconcileIds(
    relay: NormalizedRelayUrl,
    filter: Filter,
    localEntries: List<IdAndTime> = emptyList(),
    batchSize: Int = 500,
    idleTimeoutMs: Long = 120_000L,
    reconcileConcurrency: Int = 1,
): NegentropyIdDiff {
    val lock = Mutex()
    val needIds = ArrayList<HexKey>()
    val haveIds = ArrayList<HexKey>()

    val result =
        negentropyReconcile(
            relay = relay,
            filter = filter,
            localEntries = localEntries,
            batchSize = batchSize,
            idleTimeoutMs = idleTimeoutMs,
            reconcileConcurrency = reconcileConcurrency,
            onHaveIds = { batch -> lock.withLock { haveIds.addAll(batch) } },
            onNeedIds = { batch -> lock.withLock { needIds.addAll(batch) } },
        )

    return NegentropyIdDiff(needIds, haveIds, result.windows)
}

suspend fun INostrClient.negentropyReconcileIds(
    relay: String,
    filter: Filter,
    localEntries: List<IdAndTime> = emptyList(),
    batchSize: Int = 500,
    idleTimeoutMs: Long = 120_000L,
    reconcileConcurrency: Int = 1,
): NegentropyIdDiff =
    negentropyReconcileIds(
        relay = RelayUrlNormalizer.normalize(relay),
        filter = filter,
        localEntries = localEntries,
        batchSize = batchSize,
        idleTimeoutMs = idleTimeoutMs,
        reconcileConcurrency = reconcileConcurrency,
    )

/**
 * Drives one NIP-77 reconciliation of [filter] against [localEntries], sending
 * `NEG-OPEN` and walking the rounds itself (rather than via [NegentropyManager]) so
 * it can apply back-pressure: each round's `needIds` are handed to [sendNeedBatch] —
 * which suspends while the download queue is full — *before* the next round is
 * acked, so the relay's id stream is paced to the downloader and never piles up.
 * When [sendHaveBatch] is non-null the ids the relay LACKS (we have them locally)
 * are streamed through it the same way.
 *
 * The ids are streamed, not returned; the result is only the terminal outcome.
 * Always sends `NEG-CLOSE` and removes the listener on the way out.
 */
private suspend fun INostrClient.reconcileStreaming(
    relay: NormalizedRelayUrl,
    filter: Filter,
    localEntries: List<IdAndTime>,
    idleTimeoutMs: Long,
    fetchBatch: Int,
    onNeed: (Int) -> Unit,
    onHave: (Int) -> Unit,
    sendNeedBatch: suspend (List<HexKey>) -> Unit,
    sendHaveBatch: (suspend (List<HexKey>) -> Unit)?,
): ReconcileOutcome {
    val targetUrl = relay
    val relayClient = getOrCreateRelay(relay)
    val subId = newSubId()
    val session = NegentropySession(subId, filter, localEntries = localEntries)

    // Reader-thread → driver hand-off. Holds at most one frame: the relay only sends
    // the next one once we ack, and we ack only after this round's ids are queued.
    val incoming = Channel<NegFrame>(Channel.UNLIMITED)

    // Idle watchdog. Bumped on connect and on EVERY message this relay sends —
    // including the download REQs' events, since this is a connection-level listener
    // that sees all of them — so any progress anywhere in the pipeline pushes the
    // reconcile deadline out. Only true silence trips it.
    val clock = IdleClock()

    val listener =
        object : RelayConnectionListener {
            override fun onConnected(
                relay: IRelayClient,
                pingMillis: Int,
                compressed: Boolean,
            ) {
                if (relay.url == targetUrl) clock.bump()
            }

            override fun onIncomingMessage(
                relay: IRelayClient,
                msgStr: String,
                msg: Message,
            ) {
                if (relay.url == targetUrl) clock.bump()
                when (msg) {
                    is NegMsgMessage -> if (msg.subId == subId) incoming.trySend(NegFrame.Msg(msg.message))
                    is NegErrMessage -> if (msg.subId == subId) incoming.trySend(NegFrame.Err(msg.reason))
                    else -> Unit
                }
            }

            override fun onDisconnected(relay: IRelayClient) {
                if (relay.url == targetUrl) incoming.trySend(NegFrame.Err("closed: relay disconnected"))
            }
        }

    addConnectionListener(listener)
    try {
        // NEG-OPEN is a one-shot command. Unlike a REQ — which the client replays
        // from its active-request state every time a relay (re)connects — a dropped
        // NEG-OPEN is never resent, so we must connect and wait until the relay is
        // ready before sending it. The connect itself keeps a finite bound even when
        // the watchdog is disabled, so an unreachable relay can't hang here forever.
        relayClient.connect()
        val connectBound = if (idleTimeoutMs > 0) idleTimeoutMs else DEFAULT_CONNECT_TIMEOUT_MS
        val connected =
            withTimeoutOrNull(connectBound) {
                connectedRelaysFlow().first { targetUrl in it }
            }
        if (connected == null) return ReconcileOutcome.Failed("could not connect within ${connectBound}ms")

        relayClient.sendIfConnected(session.open())

        while (true) {
            // Wait for the relay's next frame, giving up only after idleTimeoutMs of
            // total silence (the wait resets whenever the relay sends anything —
            // another round, or an event on a download REQ). A disconnect arrives as
            // an Err frame, so a dead socket ends this promptly regardless.
            val frame =
                incoming.receiveWithinIdle(clock, idleTimeoutMs)
                    ?: return ReconcileOutcome.Failed(
                        if (idleTimeoutMs > 0) {
                            "relay went silent for ${idleTimeoutMs}ms mid-reconcile"
                        } else {
                            "connection closed before reconcile completed"
                        },
                    )

            when (frame) {
                is NegFrame.Err ->
                    return if (isOverflow(frame.reason)) ReconcileOutcome.Overflow else ReconcileOutcome.Failed(frame.reason)

                is NegFrame.Msg -> {
                    val result = session.processMessage(frame.payload)
                    val needIds = result.needIds
                    if (needIds.isNotEmpty()) {
                        onNeed(needIds.size)
                        var i = 0
                        while (i < needIds.size) {
                            val end = min(i + fetchBatch, needIds.size)
                            // Copy each batch so the frame's full id list can be freed
                            // as soon as it is chunked; suspends under back-pressure.
                            sendNeedBatch(ArrayList(needIds.subList(i, end)))
                            i = end
                        }
                    }
                    val haveIds = result.haveIds
                    if (haveIds.isNotEmpty() && sendHaveBatch != null) {
                        onHave(haveIds.size)
                        var i = 0
                        while (i < haveIds.size) {
                            val end = min(i + fetchBatch, haveIds.size)
                            sendHaveBatch(ArrayList(haveIds.subList(i, end)))
                            i = end
                        }
                    }
                    val next = result.nextCmd
                    if (next != null) {
                        relayClient.sendIfConnected(next)
                    } else {
                        return ReconcileOutcome.Complete
                    }
                }
            }
        }
    } finally {
        relayClient.sendIfConnected(session.close())
        removeConnectionListener(listener)
        incoming.close()
    }
}

private sealed interface NegFrame {
    class Msg(
        val payload: String,
    ) : NegFrame

    class Err(
        val reason: String,
    ) : NegFrame
}

/**
 * strfry sends `["NEG-ERR", subId, "blocked: too many query results"]` when a
 * NEG-OPEN matches more than `relay__negentropy__maxSyncEvents`. Match that
 * verbatim, plus a looser contains-check so equivalent wording from other relays
 * still triggers the window split rather than aborting.
 */
private fun isOverflow(reason: String): Boolean =
    reason == "blocked: too many query results" ||
        reason.contains("too many", ignoreCase = true) ||
        reason.startsWith("blocked", ignoreCase = true)

/**
 * One `REQ` for [batch] ids; collects the matching events and returns them on
 * `EOSE`/close/timeout. All events for a single relay arrive on its one reader
 * thread, so collecting here needs no synchronisation.
 *
 * Events are deduped *within this batch* (a [HashSet] bounded by the batch size, so
 * still O(pipeline) memory). A REQ-by-ids should return each id once, but the client
 * may re-send the REQ on a reconnect/filter-sync mid-flight, which makes the relay
 * replay the batch; without this the same event would be delivered twice. We rely on
 * NIP-77 yielding a distinct id set across batches, so no global dedup is needed.
 */
internal suspend fun INostrClient.fetchByIds(
    relay: NormalizedRelayUrl,
    batch: List<HexKey>,
    idleTimeoutMs: Long,
): List<Event> {
    val subId = newSubId()
    val done = Channel<Unit>(Channel.CONFLATED)
    val collected = ArrayList<Event>(batch.size)
    val seen = HashSet<HexKey>(batch.size)

    // Per-batch idle clock: each event resets it, so a batch that keeps streaming is
    // never cut off, but a batch that stalls (relay stops mid-flight) unblocks after
    // the idle bound instead of hanging a worker. A download batch always keeps a
    // finite bound even when the caller disabled the whole-sync watchdog.
    val clock = IdleClock()
    val batchIdleMs = if (idleTimeoutMs > 0) idleTimeoutMs else DEFAULT_DOWNLOAD_IDLE_MS

    val listener =
        object : SubscriptionListener {
            override fun onEvent(
                event: Event,
                isLive: Boolean,
                relay: NormalizedRelayUrl,
                forFilters: List<Filter>?,
            ) {
                clock.bump()
                if (seen.add(event.id)) collected.add(event)
            }

            override fun onEose(
                relay: NormalizedRelayUrl,
                forFilters: List<Filter>?,
            ) {
                done.trySend(Unit)
            }

            override fun onClosed(
                message: String,
                relay: NormalizedRelayUrl,
                forFilters: List<Filter>?,
            ) {
                done.trySend(Unit)
            }

            override fun onCannotConnect(
                relay: NormalizedRelayUrl,
                message: String,
                forFilters: List<Filter>?,
            ) {
                done.trySend(Unit)
            }
        }

    try {
        subscribe(subId, mapOf(relay to listOf(Filter(ids = batch))), listener)
        done.receiveWithinIdle(clock, batchIdleMs)
    } finally {
        unsubscribe(subId)
        done.close()
    }
    return collected
}

/** Seconds: a window this small that still overflows can't be split further. */
private const val MIN_WINDOW_SECONDS = 1L

/** Bounded buffer between the download workers and the single delivery consumer. */
private const val DELIVERY_BUFFER = 256

/**
 * A 32-byte id that no real event can have (all `f`s), used only to hold a
 * never-matching keep-alive subscription that keeps the relay connected for the
 * duration of a sync. Synthetic/real event ids are SHA-256 digests, so this never
 * collides with an actual event.
 */
internal val KEEP_ALIVE_ID = "f".repeat(64)

/**
 * Finite fallback bounds (ms) for the two waits that must stay bounded even when the
 * whole-sync idle watchdog is disabled (`idleTimeoutMs = 0`): the initial connect,
 * and each individual download batch. Keeping these finite means an unreachable relay
 * or a single stuck batch can never hang the pipeline, while the reconcile rounds
 * still honor "run until the socket drops".
 */
private const val DEFAULT_CONNECT_TIMEOUT_MS = 30_000L
private const val DEFAULT_DOWNLOAD_IDLE_MS = 60_000L

/**
 * Monotonic "last activity" marker for the idle watchdog. [bump] on every sign of
 * life from the relay; [elapsedMs] reports the silence since the last bump.
 *
 * [bump] is on the per-event hot path (the connection listener bumps for every
 * message the relay sends — millions during a large download), so it must not
 * allocate: a single [start] mark is taken once (unboxed field) and each bump only
 * writes a `Long` of nanos-since-start into a `@Volatile` field. Reader threads
 * write, the driver coroutine reads — visibility is all we need, so a plain volatile
 * Long beats boxing a `ValueTimeMark` into an `AtomicReference` on every event.
 */
private class IdleClock {
    private val start = TimeSource.Monotonic.markNow()

    @Volatile
    private var lastNanos = 0L

    fun bump() {
        lastNanos = start.elapsedNow().inWholeNanoseconds
    }

    fun elapsedMs(): Long = (start.elapsedNow().inWholeNanoseconds - lastNanos) / 1_000_000
}

/**
 * Receives the next item, giving up (returning `null`) only after [idleMs] elapse with
 * no activity on [clock]. Because [clock] is bumped by *any* relay message — not just
 * items on this channel — unrelated progress (e.g. download events arriving during a
 * reconcile wait) keeps pushing the deadline out. [idleMs] `<= 0` disables the
 * watchdog: it waits until an item arrives (a disconnect is delivered as an item, so
 * a dead socket still unblocks it).
 */
private suspend fun <T> Channel<T>.receiveWithinIdle(
    clock: IdleClock,
    idleMs: Long,
): T? {
    if (idleMs <= 0) return receive()
    while (true) {
        val remaining = idleMs - clock.elapsedMs()
        if (remaining <= 0) return null
        val item = withTimeoutOrNull(remaining) { receive() }
        if (item != null) return item
        // Timed out with nothing on this channel. If other activity bumped the clock
        // meanwhile, the next `remaining` is positive and we wait again; otherwise it
        // is <= 0 on the next iteration and we give up.
    }
}
