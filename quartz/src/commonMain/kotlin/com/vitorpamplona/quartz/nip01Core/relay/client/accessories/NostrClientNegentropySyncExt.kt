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
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.newSubId
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip77Negentropy.INegentropyListener
import com.vitorpamplona.quartz.nip77Negentropy.NegentropyManager
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.coroutineContext
import kotlin.math.min

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
 *  1. Reconciles the relay's matched set against an empty local set via a
 *     [NegentropyManager], accumulating the ids the relay has (`needIds`) across
 *     rounds until completion.
 *  2. Downloads those ids through at most [maxConcurrentReqs] concurrent `REQ`
 *     subscriptions of [fetchBatch] ids each, refilling as each `EOSE` arrives,
 *     so a huge set never opens thousands of subs at once.
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
 * tags, …) to download a slice instead of everything. The accessory keeps memory
 * bounded by reconciling and downloading one window at a time and by capping the
 * delivered set at [maxEvents].
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
 * @param timeoutMs         max wait for a single reconcile round or download page's `EOSE`.
 * @param onProgress        optional `(needSoFar, downloaded)` ticks as work proceeds.
 * @param onEvent           called once per distinct event, on the relay reader thread.
 */
suspend fun INostrClient.negentropySync(
    relay: NormalizedRelayUrl,
    filter: Filter,
    maxEvents: Int = 0,
    maxConcurrentReqs: Int = 8,
    fetchBatch: Int = 500,
    timeoutMs: Long = 30_000L,
    onProgress: ((needSoFar: Int, downloaded: Int) -> Unit)? = null,
    onEvent: (Event) -> Unit,
): NegentropySyncResult {
    var need = 0
    var windows = 0

    var downloaded = 0
    val seen = HashSet<HexKey>()

    coroutineScope {
        // Single funnel for every delivered event, so dedup + the maxEvents cap +
        // onEvent run on one coroutine even though the relay reader threads produce
        // concurrently.
        val events = Channel<Event>(Channel.UNLIMITED)

        val producer =
            launch {
                try {
                    syncWindow(
                        relay = relay,
                        filter = filter,
                        timeoutMs = timeoutMs,
                        fetchBatch = fetchBatch,
                        maxConcurrentReqs = maxConcurrentReqs,
                        onWindow = { windows++ },
                        // Only accumulate here; progress is reported from the single
                        // consumer loop below so the user callback is never invoked
                        // from two coroutines at once.
                        onNeed = { need += it },
                        deliver = { events.trySend(it) },
                    )
                } finally {
                    events.close()
                }
            }

        for (event in events) {
            if (seen.add(event.id)) {
                downloaded++
                onEvent(event)
                onProgress?.invoke(need, downloaded)
                if (maxEvents in 1..downloaded) break
            }
        }

        // If we broke out early (cap reached) the producer may still be working —
        // stop it. If the producer finished normally this is a no-op.
        producer.cancel()
    }

    return NegentropySyncResult(
        needCount = need,
        haveCount = 0,
        downloaded = downloaded,
        windows = windows,
    )
}

suspend fun INostrClient.negentropySync(
    relay: String,
    filter: Filter,
    maxEvents: Int = 0,
    maxConcurrentReqs: Int = 8,
    fetchBatch: Int = 500,
    timeoutMs: Long = 30_000L,
    onProgress: ((needSoFar: Int, downloaded: Int) -> Unit)? = null,
    onEvent: (Event) -> Unit,
): NegentropySyncResult =
    negentropySync(
        relay = RelayUrlNormalizer.normalize(relay),
        filter = filter,
        maxEvents = maxEvents,
        maxConcurrentReqs = maxConcurrentReqs,
        fetchBatch = fetchBatch,
        timeoutMs = timeoutMs,
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
    timeoutMs: Long = 30_000L,
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
                timeoutMs = timeoutMs,
                onProgress = onProgress,
            ) { accept(it) }
        NegentropyOrFetchResult(delivered, pagedFallback = false, negentropy = result, fallbackCause = null)
    } catch (e: NegentropySyncException) {
        // Negentropy couldn't enumerate the set — page the whole filter instead,
        // skipping anything the negentropy attempt already delivered.
        val pageFilter = if (maxEvents > 0) filter.copy(limit = maxEvents) else filter
        fetchAllPages(relay, listOf(pageFilter), timeoutMs) { event ->
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
    timeoutMs: Long = 30_000L,
    onProgress: ((needSoFar: Int, downloaded: Int) -> Unit)? = null,
    onEvent: (Event) -> Unit,
): NegentropyOrFetchResult =
    negentropySyncOrFetch(
        relay = RelayUrlNormalizer.normalize(relay),
        filter = filter,
        maxEvents = maxEvents,
        maxConcurrentReqs = maxConcurrentReqs,
        fetchBatch = fetchBatch,
        timeoutMs = timeoutMs,
        onProgress = onProgress,
        onEvent = onEvent,
    )

/**
 * Recursively reconciles [filter] for [relay], splitting by `created_at` windows
 * whenever the relay rejects the set as too large, and downloading the ids of each
 * window as it resolves. Runs on a single coroutine; [deliver] funnels events out.
 *
 * Throws [NegentropySyncException] for any window negentropy cannot reconcile (a
 * minimal window still over the cap, or an unavailable/erroring relay).
 */
private suspend fun INostrClient.syncWindow(
    relay: NormalizedRelayUrl,
    filter: Filter,
    timeoutMs: Long,
    fetchBatch: Int,
    maxConcurrentReqs: Int,
    onWindow: () -> Unit,
    onNeed: (Int) -> Unit,
    deliver: (Event) -> Unit,
) {
    coroutineContext.ensureActive()

    when (val outcome = reconcileWindow(relay, filter, timeoutMs)) {
        is ReconcileOutcome.Ids -> {
            onWindow()
            onNeed(outcome.needIds.size)
            downloadIds(relay, outcome.needIds, fetchBatch, maxConcurrentReqs, timeoutMs, deliver)
        }

        is ReconcileOutcome.Overflow -> {
            val lo = filter.since ?: 0L
            val hi = filter.until ?: TimeUtils.now()
            if (hi - lo <= MIN_WINDOW_SECONDS) {
                // A minimal window that still overflows: negentropy genuinely can't
                // enumerate this slice. Surface it — paging is the caller's call.
                throw NegentropySyncException(
                    relay = relay,
                    window = filter,
                    reason = NegentropySyncException.Reason.OVER_MAX_SYNC_EVENTS,
                    detail = "created_at window [$lo, $hi] still exceeds the relay's max_sync_events",
                )
            } else {
                val mid = lo + (hi - lo) / 2
                syncWindow(relay, filter.copy(since = lo, until = mid), timeoutMs, fetchBatch, maxConcurrentReqs, onWindow, onNeed, deliver)
                syncWindow(relay, filter.copy(since = mid + 1, until = hi), timeoutMs, fetchBatch, maxConcurrentReqs, onWindow, onNeed, deliver)
            }
        }

        is ReconcileOutcome.Failed ->
            throw NegentropySyncException(
                relay = relay,
                window = filter,
                reason = NegentropySyncException.Reason.UNAVAILABLE,
                detail = outcome.detail,
            )
    }
}

private sealed interface ReconcileOutcome {
    /** Reconciliation completed; [needIds] are the ids the relay has that we lack. */
    class Ids(
        val needIds: List<HexKey>,
    ) : ReconcileOutcome

    /** Relay rejected the set as too large (strfry `max_sync_events`). */
    object Overflow : ReconcileOutcome

    /** Reconciliation could not complete; [detail] says why. */
    class Failed(
        val detail: String,
    ) : ReconcileOutcome
}

/**
 * Drives one NIP-77 reconciliation of [filter] against an EMPTY local set, so the
 * resulting `needIds` are every id the relay holds for that filter. Registers a
 * [NegentropyManager], sends `NEG-OPEN`, and walks the rounds until completion,
 * error, or [timeoutMs]. Always closes the session and removes the listener.
 */
private suspend fun INostrClient.reconcileWindow(
    relay: NormalizedRelayUrl,
    filter: Filter,
    timeoutMs: Long,
): ReconcileOutcome {
    val relayClient = getOrCreateRelay(relay)
    val subId = newSubId()
    val signals = Channel<NegSignal>(Channel.UNLIMITED)
    val needIds = ArrayList<HexKey>()

    val listener =
        object : INegentropyListener {
            override fun onHaveIds(
                relay: NormalizedRelayUrl,
                subId: String,
                haveIds: List<String>,
            ) {
                // Empty local set: there is nothing the relay can lack. Ignore.
            }

            override fun onNeedIds(
                relay: NormalizedRelayUrl,
                subId: String,
                needIds: List<String>,
            ) {
                signals.trySend(NegSignal.Need(needIds))
            }

            override fun onComplete(
                relay: NormalizedRelayUrl,
                subId: String,
            ) {
                signals.trySend(NegSignal.Complete)
            }

            override fun onError(
                relay: NormalizedRelayUrl,
                subId: String,
                reason: String,
            ) {
                signals.trySend(NegSignal.Error(reason))
            }
        }

    val manager = NegentropyManager(listener)
    addConnectionListener(manager)
    try {
        // NEG-OPEN is a one-shot command. Unlike a REQ — which the client replays
        // from its active-request state every time a relay (re)connects — a dropped
        // NEG-OPEN is never resent. `sendOrConnectAndSync` on a cold relay only
        // kicks off the connect and silently drops the command, so we must connect
        // and wait until the relay is ready before opening the session.
        relayClient.connect()
        val connected =
            withTimeoutOrNull(timeoutMs) {
                connectedRelaysFlow().first { relay in it }
            }
        if (connected == null) return ReconcileOutcome.Failed("could not connect within ${timeoutMs}ms")

        manager.startSync(relayClient, subId, filter, localEvents = emptyList())

        val outcome =
            withTimeoutOrNull(timeoutMs) {
                while (true) {
                    when (val signal = signals.receive()) {
                        is NegSignal.Need -> needIds.addAll(signal.ids)
                        is NegSignal.Complete -> return@withTimeoutOrNull ReconcileOutcome.Ids(needIds)
                        is NegSignal.Error ->
                            return@withTimeoutOrNull if (isOverflow(signal.reason)) {
                                ReconcileOutcome.Overflow
                            } else {
                                ReconcileOutcome.Failed(signal.reason)
                            }
                    }
                }
                @Suppress("UNREACHABLE_CODE")
                ReconcileOutcome.Failed("unreachable")
            }

        return outcome ?: ReconcileOutcome.Failed("reconcile timed out after ${timeoutMs}ms")
    } finally {
        manager.closeSync(relayClient, subId)
        removeConnectionListener(manager)
        signals.close()
    }
}

private sealed interface NegSignal {
    class Need(
        val ids: List<HexKey>,
    ) : NegSignal

    object Complete : NegSignal

    class Error(
        val reason: String,
    ) : NegSignal
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
 * Downloads [ids] from [relay] through at most [maxConcurrentReqs] concurrent
 * `REQ`s of [fetchBatch] ids each. A fixed worker pool drains a batch queue, so at
 * most [maxConcurrentReqs] subscriptions are ever open at once, each refilled as
 * its `EOSE` arrives.
 */
private suspend fun INostrClient.downloadIds(
    relay: NormalizedRelayUrl,
    ids: List<HexKey>,
    fetchBatch: Int,
    maxConcurrentReqs: Int,
    timeoutMs: Long,
    deliver: (Event) -> Unit,
) {
    if (ids.isEmpty()) return

    val batches = Channel<List<HexKey>>(Channel.UNLIMITED)
    val chunks = ids.chunked(fetchBatch)
    chunks.forEach { batches.trySend(it) }
    batches.close()

    val workers = min(maxConcurrentReqs.coerceAtLeast(1), chunks.size)

    coroutineScope {
        repeat(workers) {
            launch {
                for (batch in batches) {
                    coroutineContext.ensureActive()
                    fetchByIds(relay, batch, timeoutMs, deliver)
                }
            }
        }
    }
}

/** One `REQ` for [batch] ids; delivers each event, returns on `EOSE`/close/timeout. */
private suspend fun INostrClient.fetchByIds(
    relay: NormalizedRelayUrl,
    batch: List<HexKey>,
    timeoutMs: Long,
    deliver: (Event) -> Unit,
) {
    val subId = newSubId()
    val done = Channel<Unit>(Channel.CONFLATED)

    val listener =
        object : SubscriptionListener {
            override fun onEvent(
                event: Event,
                isLive: Boolean,
                relay: NormalizedRelayUrl,
                forFilters: List<Filter>?,
            ) {
                deliver(event)
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
        withTimeoutOrNull(timeoutMs) {
            done.receive()
        }
    } finally {
        unsubscribe(subId)
        done.close()
    }
}

/** Seconds: a window this small that still overflows is paged instead of split. */
private const val MIN_WINDOW_SECONDS = 1L
