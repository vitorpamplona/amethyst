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
package com.vitorpamplona.quartz.nip85TrustedAssertions.users.index

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.PagedFetchResult
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.ParallelEventVerifier
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.count
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAll
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAllPages
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.negentropyReconcile
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.negentropySync
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.store.IdAndTime
import com.vitorpamplona.quartz.nip09Deletions.DeletionRequestEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.UserAssertionEvent
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch

/** Progress of a sync: cards verified so far, and the relay's NIP-45 count when it gave one. */
fun interface TrustNetworkProgress {
    fun onProgress(
        verified: Int,
        expected: Int?,
    )
}

/**
 * The result of a sync. [complete] is false when the relay stopped answering (or refused)
 * before the whole set arrived: callers must not treat a partial cold sync as the network.
 */
class TrustNetworkSyncResult(
    val header: TrustNetworkHeader,
    val index: TrustNetworkIndex,
    val ids: TrustNetworkIds,
    val complete: Boolean,
    /** Events dropped because their signature did not verify. */
    val invalid: Int,
    /** Cards and deletions accepted by this run (for an update, the delta size). */
    val received: Int,
    /** Why the relay walk ended, for logs and the UI. */
    val detail: String?,
)

/** Every kind 30382 card the [provider] has published. */
fun trustNetworkFilter(
    provider: HexKey,
    since: Long? = null,
) = Filter(kinds = listOf(UserAssertionEvent.KIND), authors = listOf(provider), since = since)

/** The [provider]'s deletions, which is how cards are retracted. */
fun trustNetworkDeletionFilter(
    provider: HexKey,
    since: Long,
) = Filter(kinds = listOf(DeletionRequestEvent.KIND), authors = listOf(provider), since = since)

/** How far before the sync cursor each update reconciles: catches a batch still arriving at the last sync, and late relay arrivals. */
const val TRUST_NETWORK_UPDATE_OVERLAP_SECS = 3600L

private const val TAG = "TrustNetworkSync"
private const val IDLE_MS = 60_000L
private const val FETCH_BY_ID_BATCH = 500

/**
 * Downloads, verifies and indexes every card [provider] has published on [relay]: the cold
 * sync of a new trust network.
 *
 * Pages backwards with [fetchAllPages]. When one second holds more cards than the relay's
 * `max_limit` (providers publish in batches that share a `created_at`), paging cannot advance
 * and reports `UNPAGEABLE`; the sync then switches to NIP-77 negentropy, passing what it
 * already has so only the remainder is downloaded.
 *
 * Every signature is checked ([ParallelEventVerifier], all cores, bounded backlog so the socket
 * slows instead of the heap growing). Events are never held: each verified card goes straight
 * into a [TrustNetworkBuilder].
 *
 * Use a dedicated client: an app client that files everything it receives into a cache would
 * receive these hundreds of thousands of cards too.
 */
@OptIn(ExperimentalAtomicApi::class)
suspend fun INostrClient.downloadTrustNetwork(
    provider: HexKey,
    relay: NormalizedRelayUrl,
    progress: TrustNetworkProgress? = null,
): TrustNetworkSyncResult {
    val filter = trustNetworkFilter(provider)
    val expected = countOrNull(relay, filter)
    progress?.onProgress(0, expected)

    val builder = TrustNetworkBuilder(provider, initialCapacity = (expected ?: 4096).coerceIn(1024, 1_000_000))
    val invalid = AtomicInt(0)

    val paged = verifying(builder, invalid, expected, progress) { submit -> fetchAllPages(relay, listOf(filter), IDLE_MS) { submit(it) } }

    var complete = paged.drained
    var detail = "${paged.end}${paged.message?.let { ": $it" } ?: ""}"

    if (!complete && paged.end == PagedFetchResult.End.UNPAGEABLE) {
        Log.d(TAG) { "Paging stalled on a shared timestamp at ${builder.cardCount} cards; switching to negentropy" }
        try {
            val local = builder.idsAndTimes()
            verifying(builder, invalid, expected, progress) { submit ->
                negentropySync(relay, filter, localEntries = local, idleTimeoutMs = IDLE_MS) { submit(it) }
            }
            complete = true
            detail = "negentropy"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Negentropy fallback failed on ${relay.url}", e)
            detail = "negentropy failed: ${e.message}"
        }
    }

    val now = TimeUtils.now()
    val (index, ids) = builder.build()
    return TrustNetworkSyncResult(
        header = TrustNetworkHeader(provider, relay.url, syncCursor = builder.newestCreatedAt, lastFullCheck = now, lastUpdate = now),
        index = index,
        ids = ids,
        complete = complete,
        invalid = invalid.load(),
        received = builder.cardCount,
        detail = detail,
    )
}

/**
 * Applies what [header]'s provider published since the last sync, without re-downloading what
 * the index already holds. Providers publish in batches that share a `created_at` (one
 * Brainstorm run signed 155k cards within a few seconds), so re-asking a time window would
 * re-download the whole batch every time.
 *
 *  1. **Is there anything new?** NIP-45 COUNTs: the relay's cards at or after the sync cursor,
 *     compared with the cards (entries and tombstones) held for that window, and deletions
 *     newer than the cursor. Two round trips of a few bytes each. Equal and zero means nothing
 *     changed, and the update ends there, so it is cheap enough to run on every app open.
 *     Counting the cursor's own second catches a batch that was still arriving at the last
 *     sync (providers sign a whole run with one `created_at`).
 *  2. **Cards:** a NIP-77 reconcile of the window from [TRUST_NETWORK_UPDATE_OVERLAP_SECS]
 *     before the cursor on, against the ids already held for that window. Only missing cards
 *     are fetched; cards the relay no longer has are dropped. The overlap catches a batch that
 *     was still arriving during the last sync (it shares the cursor's second).
 *  3. **Deletions:** kind 5s strictly newer than the cursor.
 *
 * Relays without NIP-77 get a plain fetch of everything strictly newer than the cursor instead;
 * late arrivals at the cursor's second are then left to the weekly [reconcileTrustNetwork].
 *
 * Needs the current [ids] column (not only the index) because the merged index rewrites it.
 */
@OptIn(ExperimentalAtomicApi::class)
suspend fun INostrClient.updateTrustNetwork(
    header: TrustNetworkHeader,
    index: TrustNetworkIndex,
    ids: TrustNetworkIds,
    relay: NormalizedRelayUrl,
    progress: TrustNetworkProgress? = null,
): TrustNetworkSyncResult {
    val provider = header.provider
    val newer = header.syncCursor + 1
    val relayCards = countOrNull(relay, trustNetworkFilter(provider, header.syncCursor))
    val newDeletions = countOrNull(relay, trustNetworkDeletionFilter(provider, newer))
    if (relayCards != null && relayCards == ids.countSince(header.syncCursor) && newDeletions == 0) {
        return TrustNetworkSyncResult(header.copy(lastUpdate = TimeUtils.now()), index, ids, complete = true, invalid = 0, received = 0, detail = "nothing new")
    }

    val builder = TrustNetworkBuilder(provider, initialCapacity = index.size + 1024)
    builder.addAll(index, ids)
    val before = builder.cardCount
    val invalid = AtomicInt(0)

    val windowStart = (header.syncCursor - TRUST_NETWORK_UPDATE_OVERLAP_SECS).coerceAtLeast(0)
    val reconciled = reconcileInto(builder, relay, trustNetworkFilter(provider, windowStart), ids.entriesSince(windowStart), invalid, progress)

    val complete: Boolean
    val detail: String
    if (reconciled != null) {
        val deletions =
            if (newDeletions == 0) {
                null
            } else {
                verifying(builder, invalid, null, progress) { submit -> fetchAllPages(relay, listOf(trustNetworkDeletionFilter(provider, newer)), IDLE_MS) { submit(it) } }
            }
        complete = reconciled.complete && (deletions == null || deletions.drained)
        detail = "need ${reconciled.need}, gone ${reconciled.gone}" + (deletions?.let { ", deletions ${it.downloaded}" } ?: "")
    } else {
        val filters = listOf(trustNetworkFilter(provider, newer), trustNetworkDeletionFilter(provider, newer))
        val paged = verifying(builder, invalid, null, progress) { submit -> fetchAllPages(relay, filters, IDLE_MS) { submit(it) } }
        complete = paged.drained
        detail = "${paged.end}${paged.message?.let { ": $it" } ?: ""}"
    }

    val (newIndex, newIds) = builder.build()
    return TrustNetworkSyncResult(
        header = header.copy(syncCursor = maxOf(header.syncCursor, builder.newestCreatedAt), lastUpdate = TimeUtils.now()),
        index = newIndex,
        ids = newIds,
        complete = complete,
        invalid = invalid.load(),
        received = builder.cardCount - before + (reconciled?.gone ?: 0),
        detail = detail,
    )
}

/**
 * The weekly full check: reconciles the local id set with the relay over NIP-77, downloads the
 * cards we are missing and drops the ones the relay no longer has. Catches whatever the small
 * updates missed (a relay outage, a deletion the update did not see).
 *
 * Returns null when the relay cannot reconcile (no NIP-77, or it refused); the caller then
 * falls back to a cold [downloadTrustNetwork].
 */
@OptIn(ExperimentalAtomicApi::class)
suspend fun INostrClient.reconcileTrustNetwork(
    header: TrustNetworkHeader,
    index: TrustNetworkIndex,
    ids: TrustNetworkIds,
    relay: NormalizedRelayUrl,
    progress: TrustNetworkProgress? = null,
): TrustNetworkSyncResult? {
    val builder = TrustNetworkBuilder(header.provider, initialCapacity = index.size + 1024)
    builder.addAll(index, ids)
    val before = builder.cardCount
    val invalid = AtomicInt(0)

    val reconciled = reconcileInto(builder, relay, trustNetworkFilter(header.provider), ids.entriesSince(), invalid, progress) ?: return null

    val (newIndex, newIds) = builder.build()
    val now = TimeUtils.now()
    return TrustNetworkSyncResult(
        header = header.copy(syncCursor = maxOf(header.syncCursor, builder.newestCreatedAt), lastFullCheck = now, lastUpdate = now),
        index = newIndex,
        ids = newIds,
        complete = reconciled.complete,
        invalid = invalid.load(),
        received = builder.cardCount - before + reconciled.gone,
        detail = "need ${reconciled.need}, gone ${reconciled.gone}",
    )
}

private class Reconciled(
    val complete: Boolean,
    val need: Int,
    val gone: Int,
)

/**
 * NIP-77 reconcile of [filter] against [local]: fetches (and verifies into [builder]) the cards
 * the relay has and we lack, and drops from [builder] the ones only we have. Null when the
 * relay cannot reconcile.
 */
@OptIn(ExperimentalAtomicApi::class)
private suspend fun INostrClient.reconcileInto(
    builder: TrustNetworkBuilder,
    relay: NormalizedRelayUrl,
    filter: Filter,
    local: List<IdAndTime>,
    invalid: AtomicInt,
    progress: TrustNetworkProgress?,
): Reconciled? {
    val need = ArrayList<HexKey>()
    val have = ArrayList<HexKey>()
    try {
        negentropyReconcile(
            relay = relay,
            filter = filter,
            localEntries = local,
            idleTimeoutMs = IDLE_MS,
            onHaveIds = { batch -> have.addAll(batch) },
            onNeedIds = { batch -> need.addAll(batch) },
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Could not reconcile the trust network with ${relay.url}", e)
        return null
    }

    var complete = true
    if (need.isNotEmpty()) {
        verifying(builder, invalid, need.size, progress) { submit ->
            for (batch in need.chunked(FETCH_BY_ID_BATCH)) {
                val events = fetchAll(relay, Filter(ids = batch), IDLE_MS)
                if (events.size < batch.size) complete = false
                for (event in events) submit(event)
            }
        }
    }
    builder.removeEventIds(have)
    return Reconciled(complete, need.size, have.size)
}

private suspend fun INostrClient.countOrNull(
    relay: NormalizedRelayUrl,
    filter: Filter,
): Int? =
    try {
        count(relay, filter)?.count
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

/**
 * Runs [fetch] with a [ParallelEventVerifier] in front of [builder]: [fetch] submits raw events,
 * only verified ones reach the builder (from the verifier's single drain coroutine, so the
 * builder stays single-writer). Returns once every submitted event has been processed.
 */
@OptIn(ExperimentalAtomicApi::class)
private suspend fun <T> verifying(
    builder: TrustNetworkBuilder,
    invalid: AtomicInt,
    expected: Int?,
    progress: TrustNetworkProgress?,
    fetch: suspend (submit: (Event) -> Unit) -> T,
): T =
    coroutineScope {
        val accepted = AtomicInt(0)
        val verifier =
            ParallelEventVerifier<Unit>(
                scope = this,
                onInvalid = { _, _ -> invalid.incrementAndFetch() },
                onVerified = { event, _ ->
                    if (builder.add(event)) {
                        val n = accepted.incrementAndFetch()
                        if (n % 1000 == 0) progress?.onProgress(builder.cardCount, expected)
                    }
                },
            )
        try {
            fetch { event -> verifier.submit(event, Unit) }
        } finally {
            verifier.close()
            verifier.join()
            progress?.onProgress(builder.cardCount, expected)
        }
    }
