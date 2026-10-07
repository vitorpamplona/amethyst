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

/** How far back each small update re-asks: absorbs batches that share a timestamp and late relay arrivals. */
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
 * Applies what [header]'s provider published since the last sync: new and changed cards, and
 * removals (rank-0 cards and kind-5 deletions). Re-asks [TRUST_NETWORK_UPDATE_OVERLAP_SECS]
 * before the cursor, which is harmless: re-applying a card is idempotent.
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
    val since = (header.syncCursor - TRUST_NETWORK_UPDATE_OVERLAP_SECS).coerceAtLeast(0)
    val builder = TrustNetworkBuilder(header.provider, initialCapacity = index.size + 1024)
    builder.addAll(index, ids)
    val before = builder.cardCount
    val invalid = AtomicInt(0)

    val filters = listOf(trustNetworkFilter(header.provider, since), trustNetworkDeletionFilter(header.provider, since))
    val paged = verifying(builder, invalid, null, progress) { submit -> fetchAllPages(relay, filters, IDLE_MS) { submit(it) } }

    val (newIndex, newIds) = builder.build()
    val cursor = maxOf(header.syncCursor, builder.newestCreatedAt)
    return TrustNetworkSyncResult(
        header = header.copy(syncCursor = cursor, lastUpdate = TimeUtils.now()),
        index = newIndex,
        ids = newIds,
        complete = paged.drained,
        invalid = invalid.load(),
        received = builder.cardCount - before,
        detail = "${paged.end}${paged.message?.let { ": $it" } ?: ""}",
    )
}

/**
 * The weekly full check: reconciles the local id set with the relay over NIP-77, downloads the
 * cards we are missing and drops the ones the relay no longer has. Catches whatever the small
 * updates missed (a relay outage, a deletion outside the overlap window).
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
    val filter = trustNetworkFilter(header.provider)
    val builder = TrustNetworkBuilder(header.provider, initialCapacity = index.size + 1024)
    builder.addAll(index, ids)
    val before = builder.cardCount
    val local = builder.idsAndTimes()

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
        Log.w(TAG, "Full check could not reconcile with ${relay.url}", e)
        return null
    }

    val invalid = AtomicInt(0)
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

    val (newIndex, newIds) = builder.build()
    val now = TimeUtils.now()
    return TrustNetworkSyncResult(
        header = header.copy(syncCursor = maxOf(header.syncCursor, builder.newestCreatedAt), lastFullCheck = now, lastUpdate = now),
        index = newIndex,
        ids = newIds,
        complete = complete,
        invalid = invalid.load(),
        received = builder.cardCount - before + have.size,
        detail = "need ${need.size}, gone ${have.size}",
    )
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
