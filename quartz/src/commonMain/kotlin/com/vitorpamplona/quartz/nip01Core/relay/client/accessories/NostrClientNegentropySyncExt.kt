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
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.ClosedMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.NoticeMessage
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
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.decrementAndFetch
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.coroutines.coroutineContext
import kotlin.math.min

/**
 * Outcome of a successful [negentropySync] run.
 *
 * @property needCount  ids the relay had that we lacked — the diff downloaded.
 *   With the default empty `localEntries` this is the relay's full matched set;
 *   pass the local set to reconcile incrementally and download only the diff.
 * @property haveCount  ids we had that the relay lacked. `0` unless `localEntries`
 *   is supplied (the downloader ignores this direction); kept so the result
 *   mirrors a full NIP-77 reconcile.
 * @property downloaded distinct events actually delivered through `onEvent`.
 * @property windows    number of `created_at` windows the matched set was split
 *   into (`1` when the relay reconciled the whole filter in one shot).
 * @property peerCap    the relay's own `max_sync_events`, when a refusal during
 *   this sync stated one. Worth persisting per relay: it is the number that
 *   sizes the first window of the NEXT sync, and it is not discoverable any
 *   other way.
 */
class NegentropySyncResult(
    val needCount: Int,
    val haveCount: Int,
    val downloaded: Int,
    val windows: Int,
    val peerCap: Long? = null,
)

/**
 * Downloads every event a single [relay] holds matching [filter], delivering each
 * one (deduped by id) through [onEvent]. A high-level wrapper over NIP-77
 * negentropy that hides the parts that make the raw protocol painful to use:
 *
 *  1. Reconciles the relay's matched set against [localEntries] (empty by default,
 *     which downloads the full matched set; pass the local ids to fetch only the
 *     diff), **streaming** the ids the relay has straight into the download
 *     pipeline as each NIP-77 round arrives — the full id list is never materialised.
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
 * @param onEvent           called once per distinct event, serially, from the single
 *   delivery consumer coroutine (not the relay reader thread) — so it never overlaps
 *   itself and the [maxEvents] cap is exact.
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
    localEntries: List<IdAndTime> = emptyList(),
    localIndex: NegentropyLocalIndex? = null,
    targetWindow: Int = 0,
    onUnreconcilableWindow: (suspend (Filter) -> Unit)? = null,
    onProgress: ((needSoFar: Int, downloaded: Int) -> Unit)? = null,
    onEvent: suspend (Event) -> Unit,
): NegentropySyncResult {
    val need = AtomicInt(0)
    val windows = AtomicInt(0)
    var downloaded = 0
    var peerCap: Long? = null

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
                            local = localIndex ?: NegentropyLocalIndex.of(localEntries),
                            targetWindow = targetWindow,
                            onUnreconcilableWindow = onUnreconcilableWindow,
                            onWindow = { windows.incrementAndFetch() },
                            onPeerCap = { peerCap = it },
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
        peerCap = peerCap,
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
    localEntries: List<IdAndTime> = emptyList(),
    localIndex: NegentropyLocalIndex? = null,
    targetWindow: Int = 0,
    onUnreconcilableWindow: (suspend (Filter) -> Unit)? = null,
    onProgress: ((needSoFar: Int, downloaded: Int) -> Unit)? = null,
    onEvent: suspend (Event) -> Unit,
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
        localEntries = localEntries,
        localIndex = localIndex,
        targetWindow = targetWindow,
        onUnreconcilableWindow = onUnreconcilableWindow,
        onProgress = onProgress,
        onEvent = onEvent,
    )

/**
 * Result of [negentropySyncOrFetch].
 *
 * @property downloaded   distinct events delivered through `onEvent` (across whichever
 *   path ran).
 * @property pagedFallback `true` if ANY part of the range came from
 *   [fetchAllPages] rather than a reconcile — either the whole filter (the
 *   relay could not reconcile at all) or the individual windows counted by
 *   [pagedWindows]. Deliberately conservative: a caller recording what it has
 *   covered must not book a paged walk as a completed reconcile, and one
 *   un-reconcilable second in the range is enough to make that claim untrue.
 * @property negentropy    the negentropy outcome when it succeeded; `null` on fallback.
 * @property fallbackCause why negentropy was abandoned for the WHOLE filter;
 *   `null` when it was not — including when individual windows were paged, which
 *   have no single cause between them.
 * @property pagedWindows  how many individual `created_at` windows were paged
 *   inside an otherwise-successful negentropy sync — seconds so dense the relay
 *   would not reconcile them at any window size. `0` for almost every sync;
 *   non-zero means part of the range came over REQ and is subject to a paged
 *   walk's limits rather than a reconcile's guarantees.
 */
class NegentropyOrFetchResult(
    val downloaded: Int,
    val pagedFallback: Boolean,
    val negentropy: NegentropySyncResult?,
    val fallbackCause: NegentropySyncException?,
    val pagedWindows: Int = 0,
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
 * **Memory:** unlike bare [negentropySync] (which streams in memory bounded by the
 * pipeline depth), this holds a set of every delivered id for the whole run — needed
 * to dedup the paging phase against what negentropy already delivered — so peak heap
 * is O(delivered ids) (~100 B each). Fine for bounded/filtered syncs; for an
 * open-ended bulk mirror of a multi-million-event set prefer [negentropySync] (or
 * [negentropyReconcile]) directly and handle the fallback yourself, to keep the
 * streaming memory bound.
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
    localEntries: List<IdAndTime> = emptyList(),
    localIndex: NegentropyLocalIndex? = null,
    targetWindow: Int = 0,
    onProgress: ((needSoFar: Int, downloaded: Int) -> Unit)? = null,
    onEvent: suspend (Event) -> Unit,
): NegentropyOrFetchResult {
    val seen = HashSet<HexKey>()
    var delivered = 0
    var pagedWindows = 0

    // Shared dedup + cap across both phases. Returns true if the event was new and
    // delivered. Both phases run sequentially, so no concurrent access.
    suspend fun accept(event: Event): Boolean {
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
                localEntries = localEntries,
                localIndex = localIndex,
                targetWindow = targetWindow,
                // One second the relay will not reconcile at any size costs
                // that second, not the sync. Without this the exception below
                // catches it and re-pages the WHOLE filter — every window that
                // already reconciled cleanly walked again over REQ, which on a
                // large corpus is the entire cost negentropy was there to save.
                onUnreconcilableWindow = { window ->
                    pagedWindows++
                    val pageTimeoutMs = if (idleTimeoutMs > 0) idleTimeoutMs else DEFAULT_DOWNLOAD_IDLE_MS
                    fetchAllPages(relay, listOf(window), pageTimeoutMs) { event ->
                        if (accept(event)) onProgress?.invoke(delivered, delivered)
                    }
                },
                onProgress = onProgress,
            ) { accept(it) }
        NegentropyOrFetchResult(
            delivered,
            // Any paged window makes this not a clean reconcile — see the
            // property doc: under-reporting it would let a caller record
            // coverage it never compared.
            pagedFallback = pagedWindows > 0,
            negentropy = result,
            fallbackCause = null,
            pagedWindows = pagedWindows,
        )
    } catch (e: NegentropySyncException) {
        // Negentropy couldn't enumerate the set — page the whole filter instead,
        // skipping anything the negentropy attempt already delivered. fetchAllPages
        // has no "no timeout" mode, so a disabled watchdog maps to a finite page bound.
        val pageFilter = if (maxEvents > 0) filter.copy(limit = maxEvents) else filter
        val pageTimeoutMs = if (idleTimeoutMs > 0) idleTimeoutMs else DEFAULT_DOWNLOAD_IDLE_MS
        fetchAllPages(relay, listOf(pageFilter), pageTimeoutMs) { event ->
            if (accept(event)) onProgress?.invoke(delivered, delivered)
        }
        NegentropyOrFetchResult(
            delivered,
            pagedFallback = true,
            negentropy = null,
            fallbackCause = e,
            pagedWindows = pagedWindows,
        )
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
    localEntries: List<IdAndTime> = emptyList(),
    localIndex: NegentropyLocalIndex? = null,
    targetWindow: Int = 0,
    onProgress: ((needSoFar: Int, downloaded: Int) -> Unit)? = null,
    onEvent: suspend (Event) -> Unit,
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
        localEntries = localEntries,
        localIndex = localIndex,
        targetWindow = targetWindow,
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
    local: NegentropyLocalIndex,
    targetWindow: Int,
    onWindow: () -> Unit,
    onNeed: (Int) -> Unit,
    onPeerCap: ((Long) -> Unit)?,
    onUnreconcilableWindow: (suspend (Filter) -> Unit)?,
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
        local = local,
        idleTimeoutMs = idleTimeoutMs,
        batchSize = fetchBatch,
        reconcileConcurrency = reconcileConcurrency,
        targetWindow = targetWindow,
        onWindow = onWindow,
        onNeed = onNeed,
        onHave = {},
        onPeerCap = onPeerCap,
        onUnreconcilableWindow = onUnreconcilableWindow,
        sendNeedBatch = { batch -> idBatches.send(batch) },
        sendHaveBatch = null,
    )

    idBatches.close()
    workers.joinAll()
}

/**
 * The shared window engine behind [negentropySync] and [negentropyReconcile]:
 * reconciles [filter] against [local], splitting into `created_at` windows,
 * with up to [reconcileConcurrency] windows reconciling at once from a shared
 * work queue. Each window reconciles against that window's slice of [local], so
 * both sides always compare the same slice of the timeline.
 *
 * Two independent things split a window, and the same queue absorbs both:
 *
 *  - **The relay refuses it** (strfry's `max_sync_events`). Known only after a
 *    round trip, and the only signal available about THEIR size.
 *  - **We hold more than [targetWindow] in it**, per [NegentropyLocalIndex.count],
 *    which is known before the round trip and is what bounds the entries this
 *    engine asks [local] to materialise. Off when [targetWindow] is `0` (the
 *    default), which is the pre-existing behaviour: one window until refused.
 *
 * Neither side can see the other's size, so [targetWindow] adapts within the
 * sync: a refusal shrinks it — straight to the relay's own cap when the refusal
 * states one ([NegErrMessage.statedCap]), halved when it does not — and windows
 * that reconcile in one piece grow it back toward, never past, the caller's
 * number.
 *
 * Throws [NegentropySyncException] for any window negentropy cannot reconcile
 * (a minimal window still over the cap with no [onUnreconcilableWindow] to hand
 * it to, or an unavailable/erroring relay); the failure cancels the whole scope.
 */
@OptIn(ExperimentalAtomicApi::class)
internal suspend fun reconcileWindows(
    // one or more connections to the SAME relay; reconciler i runs its NEG
    // sessions on clients[i % size], so concurrent windows spread across
    // connections (server-side snapshot builds are paced per connection)
    clients: List<INostrClient>,
    relay: NormalizedRelayUrl,
    filter: Filter,
    local: NegentropyLocalIndex,
    idleTimeoutMs: Long,
    batchSize: Int,
    reconcileConcurrency: Int,
    targetWindow: Int = 0,
    onWindow: () -> Unit,
    onNeed: (Int) -> Unit,
    onHave: (Int) -> Unit,
    onPeerCap: ((Long) -> Unit)? = null,
    // Given a minimal window the relay will not reconcile at any size, instead
    // of throwing. The caller drains it however it can (paging it over REQ) and
    // the sweep carries on with the rest of the filter.
    onUnreconcilableWindow: (suspend (Filter) -> Unit)? = null,
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

    // Total windows ever created (never decremented). A genuine overflow shrinks the
    // window until it fits, so it converges after a handful of splits; a
    // non-shrinking error mislabeled as overflow (e.g. a rate limit) would instead
    // split forever toward 1-second leaves and blow up `pending`. This cap is the
    // wording-independent backstop [isOverflow]'s narrowing relies on: cross it and
    // we fail over to paging instead of storming the relay. Legitimate splits are in
    // the tens–hundreds, so the cap is orders of magnitude above any real sync.
    val totalWindows = AtomicInt(1)

    // The largest window this sync will ask for, in events. Shrinks on a
    // refusal, recovers toward the caller's number on clean windows, and is
    // read only where a local count exists to compare it against — with
    // targetWindow at 0 nothing below this line does anything.
    val budget = AtomicInt(targetWindow)

    // Splits a window in two and queues both halves. Returns false when the
    // window is already minimal — `created_at` is in seconds, so that is the
    // floor, not a tuning choice.
    fun splitInto(
        pendingWindow: Filter,
        lo: Long,
        hi: Long,
    ): Boolean {
        if (hi - lo <= MIN_WINDOW_SECONDS) return false
        val mid = lo + (hi - lo) / 2
        remaining.incrementAndFetch()
        // The lower child gets the finite midpoint; the upper child KEEPS this
        // window's original `until` (which may be null = unbounded). Replacing
        // null with `now()` here would drop every event dated after now()
        // (clock skew) once any split happens, while the un-split path would
        // have included them.
        pending.trySend(pendingWindow.copy(since = lo, until = mid))
        pending.trySend(pendingWindow.copy(since = mid + 1, until = pendingWindow.until))
        return true
    }

    val reconcilers =
        List(reconcileConcurrency.coerceAtLeast(1)) { reconcilerIndex ->
            launch {
                val client = clients[reconcilerIndex % clients.size]
                for (window in pending) {
                    coroutineContext.ensureActive()

                    val lo = window.since ?: 0L
                    val hi = window.until ?: TimeUtils.now()

                    // Our own side, before the round trip. Deliberately NOT
                    // counted against MAX_WINDOWS: that backstop guards against
                    // an overflow loop that never converges, while this split is
                    // driven by a number that provably halves with the range.
                    val ceiling = budget.load()
                    if (ceiling > 0 && hi - lo > MIN_WINDOW_SECONDS) {
                        val mine = local.count(window)
                        if (mine != null && mine > ceiling) {
                            splitInto(window, lo, hi)
                            continue
                        }
                    }

                    val outcome =
                        client.reconcileStreaming(
                            relay = relay,
                            filter = window,
                            localEntries = local.entriesFor(window),
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
                            // A window that fitted is evidence the budget can
                            // recover — gently, and never past what the caller
                            // asked for, so a sync that met one dense stretch
                            // does not stay small for the rest of the timeline.
                            if (targetWindow > 0) {
                                val now = budget.load()
                                if (now < targetWindow) {
                                    budget.store(minOf(targetWindow, (now * BUDGET_GROWTH).toInt().coerceAtLeast(now + 1)))
                                }
                            }
                            if (remaining.decrementAndFetch() == 0) pending.close()
                        }

                        is ReconcileOutcome.Overflow -> {
                            // What they will take, when they said so: one step
                            // instead of a halving ladder, for this sync and —
                            // via onPeerCap — for whatever the caller persists.
                            outcome.cap?.let { cap ->
                                onPeerCap?.invoke(cap)
                                if (targetWindow > 0) {
                                    val fitted = (cap * CAP_MARGIN).toInt().coerceAtLeast(1)
                                    if (fitted < budget.load()) budget.store(fitted)
                                }
                            }
                            if (outcome.cap == null && targetWindow > 0) {
                                // No number to go on: halve and find out.
                                budget.store((budget.load() / 2).coerceAtLeast(1))
                            }
                            if (hi - lo <= MIN_WINDOW_SECONDS) {
                                // A minimal window that still overflows:
                                // negentropy genuinely can't enumerate this
                                // slice. Hand it to the caller if it has a way
                                // to drain it, otherwise surface it — paging is
                                // the caller's call either way.
                                val fallback = onUnreconcilableWindow
                                if (fallback != null) {
                                    fallback(window)
                                    onWindow()
                                    if (remaining.decrementAndFetch() == 0) pending.close()
                                    continue
                                }
                                throw NegentropySyncException(
                                    relay = relay,
                                    window = window,
                                    reason = NegentropySyncException.Reason.OVER_MAX_SYNC_EVENTS,
                                    detail = "created_at window [$lo, $hi] still exceeds the relay's max_sync_events",
                                    cap = outcome.cap,
                                )
                            }
                            if (totalWindows.addAndFetch(2) > MAX_WINDOWS) {
                                // The split isn't converging — almost always a
                                // non-shrinking error (rate limit, quota) misread as
                                // overflow. Bail to paging rather than storm the relay.
                                throw NegentropySyncException(
                                    relay = relay,
                                    window = window,
                                    reason = NegentropySyncException.Reason.UNAVAILABLE,
                                    detail = "created_at window split exceeded $MAX_WINDOWS windows without converging; the relay likely rejects negentropy with an overflow-looking error",
                                )
                            }
                            splitInto(window, lo, hi)
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

private sealed interface ReconcileOutcome {
    /** Reconciliation completed; every id was streamed to the downloader. */
    object Complete : ReconcileOutcome

    /**
     * Relay rejected the set as too large (strfry `max_sync_events`).
     * [cap] is the relay's own limit when the refusal stated one.
     */
    class Overflow(
        val cap: Long?,
    ) : ReconcileOutcome

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
 * @property peerCap   the relay's own `max_sync_events`, when a refusal during
 *   this reconcile stated one.
 */
class NegentropyReconcileResult(
    val needCount: Int,
    val haveCount: Int,
    val windows: Int,
    val peerCap: Long? = null,
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
    localIndex: NegentropyLocalIndex? = null,
    targetWindow: Int = 0,
    batchSize: Int = 500,
    idleTimeoutMs: Long = 120_000L,
    reconcileConcurrency: Int = 1,
    onUnreconcilableWindow: (suspend (Filter) -> Unit)? = null,
    onHaveIds: (suspend (List<HexKey>) -> Unit)? = null,
    onNeedIds: suspend (List<HexKey>) -> Unit,
): NegentropyReconcileResult {
    val need = AtomicInt(0)
    val have = AtomicInt(0)
    val windows = AtomicInt(0)
    var peerCap: Long? = null

    // Same connection-pinning trick as negentropySync: a NEG-OPEN is not a REQ,
    // so without a live subscription the pool would consider the relay unwanted
    // and disconnect it mid-reconcile.
    val keepAliveSubId = newSubId()
    subscribe(keepAliveSubId, mapOf(relay to listOf(Filter(ids = listOf(KEEP_ALIVE_ID)))), null)
    try {
        reconcileWindows(
            clients = listOf(this),
            relay = relay,
            filter = filter,
            local = localIndex ?: NegentropyLocalIndex.of(localEntries),
            idleTimeoutMs = idleTimeoutMs,
            batchSize = batchSize,
            reconcileConcurrency = reconcileConcurrency,
            targetWindow = targetWindow,
            onWindow = { windows.incrementAndFetch() },
            onNeed = { need.addAndFetch(it) },
            onHave = { have.addAndFetch(it) },
            onPeerCap = { peerCap = it },
            onUnreconcilableWindow = onUnreconcilableWindow,
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
        peerCap = peerCap,
    )
}

suspend fun INostrClient.negentropyReconcile(
    relay: String,
    filter: Filter,
    localEntries: List<IdAndTime> = emptyList(),
    localIndex: NegentropyLocalIndex? = null,
    targetWindow: Int = 0,
    batchSize: Int = 500,
    idleTimeoutMs: Long = 120_000L,
    reconcileConcurrency: Int = 1,
    onUnreconcilableWindow: (suspend (Filter) -> Unit)? = null,
    onHaveIds: (suspend (List<HexKey>) -> Unit)? = null,
    onNeedIds: suspend (List<HexKey>) -> Unit,
): NegentropyReconcileResult =
    negentropyReconcile(
        relay = RelayUrlNormalizer.normalize(relay),
        filter = filter,
        localEntries = localEntries,
        localIndex = localIndex,
        targetWindow = targetWindow,
        batchSize = batchSize,
        idleTimeoutMs = idleTimeoutMs,
        reconcileConcurrency = reconcileConcurrency,
        onUnreconcilableWindow = onUnreconcilableWindow,
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

    // Idle watchdog. Bumped on connect and on the messages that represent real
    // progress on this connection — this session's own NEG frames and the download
    // REQs' events/EOSEs (a connection-level listener sees them all) — so any
    // progress anywhere in the pipeline pushes the reconcile deadline out. It is
    // deliberately NOT bumped by NOTICE/CLOSED error chatter: a relay that keeps
    // refusing our subscriptions would otherwise reset the watchdog forever, which
    // is exactly how a rejected sync escaped the idle timeout.
    val clock = IdleClock()

    // Have we received a single valid NEG frame for our subId yet? A relay that
    // advertises NIP-77 but refuses it at runtime answers our NEG-OPEN with a
    // connection-level NOTICE (which carries no subId) rather than a subId-addressed
    // NEG-ERR. Before the first NEG frame arrives, such a NOTICE is the answer to
    // our NEG-OPEN and must be treated as terminal. Only touched from the relay's
    // single reader coroutine.
    var sawNegFrame = false

    val listener =
        object : RelayConnectionListener {
            override fun onConnected(
                relay: IRelayClient,
                pingMillis: Int,
                compressed: Boolean,
            ) {
                if (relay.url == targetUrl) clock.bump()
            }

            override suspend fun onIncomingMessage(
                relay: IRelayClient,
                msgStr: String,
                msg: Message,
            ) {
                if (relay.url != targetUrl) return
                when (msg) {
                    is NegMsgMessage -> {
                        clock.bump()
                        if (msg.subId == subId) {
                            sawNegFrame = true
                            incoming.trySend(NegFrame.Msg(msg.message))
                        }
                    }

                    is NegErrMessage -> {
                        clock.bump()
                        if (msg.subId == subId) {
                            sawNegFrame = true
                            incoming.trySend(NegFrame.Err(msg.reason, msg.statedCap))
                        }
                    }

                    is ClosedMessage ->
                        // A CLOSED addressed to our negentropy subscription is a
                        // terminal rejection of the NEG-OPEN (some relays answer a
                        // refused negentropy session this way instead of NEG-ERR).
                        if (msg.subId == subId) incoming.trySend(NegFrame.Err("closed: ${msg.message}"))

                    is NoticeMessage ->
                        // NIP-77 says a relay SHOULD reject with NEG-ERR, but relays
                        // that advertise NIP-77 yet refuse it at runtime answer with a
                        // connection-level NOTICE instead (strfry: "ERROR: bad msg:
                        // negentropy disabled"; purplepag.es: "failed to parse
                        // envelope: unknown envelope label"). A NOTICE has no subId, so
                        // we bind it to this session by phase + wording: before the
                        // first valid NEG frame, a negentropy-looking NOTICE is the
                        // answer to our NEG-OPEN. Surface it as terminal so the caller
                        // fails over (paging) instead of hanging until the socket drops.
                        if (!sawNegFrame && isNegentropyRejectionNotice(msg.message)) {
                            incoming.trySend(NegFrame.Err("notice: ${msg.message}"))
                        }

                    else ->
                        // EVENT/EOSE from the download REQs (and any other framing on
                        // this connection) = real progress; keep the reconcile alive.
                        clock.bump()
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
                    return if (isOverflow(frame.reason)) {
                        ReconcileOutcome.Overflow(frame.cap)
                    } else {
                        ReconcileOutcome.Failed(frame.reason)
                    }

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
        // The relay's own max_sync_events, when the refusal stated one.
        val cap: Long? = null,
    ) : NegFrame
}

/**
 * strfry sends `["NEG-ERR", subId, "blocked: query matches too many records (N > M)"]`
 * (and, older, `"too many query results"`) when a NEG-OPEN matches more than
 * `relay__negentropy__maxSyncEvents`. Match that, plus equivalent "result set too
 * large" wording from other relays, so it triggers the window split rather than
 * aborting.
 *
 * This MUST stay narrow, and specifically must key on the *result-set-size* meaning:
 * only a genuine set-too-large signal may be treated as overflow, because overflow
 * triggers `created_at` window-splitting. Two ways a too-lax matcher goes wrong:
 *  - A hard refusal (negentropy disabled, `auth-required`, a ban) that happens to
 *    contain a matched word would split, re-open, be refused again, and fan out
 *    across the whole `created_at` range instead of failing over to paging.
 *  - A *rate/quota* error — `"too many requests"`, `"too many concurrent
 *    subscriptions"` — is especially dangerous: it does not shrink as the window
 *    shrinks, so every split re-triggers it and the splitter walks toward 1-second
 *    leaves, queueing up to ~2^31 windows (an OOM + relay-hammering storm) before
 *    any window is small enough to give up on. That is why the bare `"too many"` /
 *    `"too large"` substrings were replaced with result-set-qualified phrases:
 *    `"too many requests"` no longer looks like overflow, so it fails over to paging.
 *
 * [reconcileWindows] also caps the total window count as a wording-independent
 * backstop, so a novel overflow-looking-but-not-shrinking error can never storm.
 */
internal fun isOverflow(reason: String): Boolean = NegErrMessage.isOverflow(reason)

/**
 * A relay that advertises NIP-77 but refuses it at runtime signals the refusal with
 * a connection-level `NOTICE` (which carries no subId) rather than a subId-addressed
 * `NEG-ERR`. Observed against public relays that all list NIP-77 in NIP-11:
 *   - strfry with negentropy off: `"ERROR: bad msg: negentropy disabled"`
 *   - purplepag.es (no NEG envelope): `"failed to parse envelope: unknown envelope label"`
 *
 * We only treat a NOTICE as our negentropy rejection when it plausibly refers to the
 * NEG exchange (this matcher) AND it arrives before this session's first valid NEG
 * frame — so an unrelated NOTICE on a healthy relay mid-reconcile can never abort an
 * otherwise-progressing sync. This is only a *fast path*: it is deliberately narrow
 * (a false positive fails the window over to paging), and anything it misses is still
 * caught by the idle watchdog, which — since NOTICE/CLOSED no longer bump the clock —
 * fires once a refusing relay goes silent after its notice. So prefer under-matching
 * here. Both matched phrases are ones a relay that actually speaks NIP-77 would never
 * emit for a well-formed client (quartz only sends valid frames): "negentropy" names
 * the feature; "unknown envelope" is the parse failure of a relay that never
 * implemented the NEG-OPEN envelope. Broad substrings like a bare "envelope" or the
 * echoed command names are excluded — an unrelated parse/rate NOTICE could carry them.
 */
internal fun isNegentropyRejectionNotice(reason: String): Boolean =
    reason.contains("negentropy", ignoreCase = true) ||
        reason.contains("unknown envelope", ignoreCase = true)

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
            override suspend fun onEvent(
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

/**
 * Hard cap on total `created_at` windows a single reconcile may split into, a
 * wording-independent backstop against a non-shrinking error (rate limit, quota)
 * being mistaken for a set-too-large overflow and splitting forever. A real sync
 * against a huge relay converges in tens–hundreds of windows, so this is a wide
 * margin; crossing it fails the sync over to paging instead of storming the relay.
 */
private const val MAX_WINDOWS = 100_000

/**
 * How much of a relay's stated `max_sync_events` a window actually aims for.
 * The margin absorbs what the relay gains between stating that number and
 * answering the next NEG-OPEN — asking for exactly the cap would be refused
 * again by anything still being written to.
 */
private const val CAP_MARGIN = 0.8

/**
 * How fast a shrunk window grows back toward the caller's target, per window
 * that reconciled in one piece. Multiplicative and gentle on purpose: too small
 * costs an extra round trip, too big costs a refused NEG-OPEN plus the snapshot
 * scan the relay did before refusing it.
 */
private const val BUDGET_GROWTH = 1.25

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
internal const val DEFAULT_DOWNLOAD_IDLE_MS = 60_000L
