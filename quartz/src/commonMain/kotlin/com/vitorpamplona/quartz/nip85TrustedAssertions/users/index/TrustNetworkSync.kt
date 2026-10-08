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
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.NegentropyLocalIndex
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch

/**
 * What a cold download has collected so far: its cards (entries and tombstones), saved so a
 * download that is cut off (the process killed, the relay gone) resumes instead of starting over.
 * Never a network: most of it is missing, and filtering with it would reject everyone else.
 */
class TrustNetworkPartial(
    val header: TrustNetworkHeader,
    val index: TrustNetworkIndex,
    val ids: TrustNetworkIds,
) {
    val cards: Int get() = index.size + ids.tombstones
}

/** Receives a [TrustNetworkPartial] every [TRUST_NETWORK_CHECKPOINT_EVERY] cards of a cold download. */
fun interface TrustNetworkCheckpoint {
    fun save(partial: TrustNetworkPartial)
}

/**
 * Cards between two checkpoints of a cold download. Each one sorts what has arrived (a second or
 * two at 150k on a phone), so a handful per download; at a phone's ~550 cards/s, an interruption
 * loses under a minute.
 */
const val TRUST_NETWORK_CHECKPOINT_EVERY = 25_000

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
    /** Why the relay walk ended, for logs. Not for decisions: use [complete] and [unchanged]. */
    val detail: String?,
    /** The relay had nothing new: [index] and [ids] are the ones the update was given. */
    val unchanged: Boolean = false,
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
private const val FETCH_BY_ID_CONCURRENCY = 4

/** Cards per negentropy window: the local side never materialises more than this at once. */
private const val NEGENTROPY_WINDOW = 50_000

/**
 * Cards a reconcile may fail to get (the relay lists them but will not serve them, or their
 * signature fails) and still count as complete: one bad card must not freeze the network. They
 * are asked for again by the next reconcile.
 */
private fun missingAllowed(need: Int) = maxOf(16, need / 200)

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
    /** What an earlier, cut-off download of this provider collected: only the rest is fetched. */
    resumeFrom: TrustNetworkPartial? = null,
    /** Saves what has arrived every [checkpointEvery] cards. */
    checkpoint: TrustNetworkCheckpoint? = null,
    checkpointEvery: Int = TRUST_NETWORK_CHECKPOINT_EVERY,
    // Last, so callers can pass it as a trailing lambda.
    progress: TrustNetworkProgress? = null,
): TrustNetworkSyncResult {
    val startedAt = TimeUtils.now()
    val filter = trustNetworkFilter(provider)
    val expected = countOrNull(relay, filter)

    // The relay's count only hints the size: growing is cheap, a huge upfront allocation is not.
    val builder = TrustNetworkBuilder(provider, initialCapacity = (expected ?: 4096).coerceIn(1024, 262_144))
    val resume = resumeFrom?.takeIf { it.header.provider == provider && it.cards > 0 }
    resume?.let { builder.addAll(it.index, it.ids) }
    progress?.onProgress(builder.cardCount, expected)
    val invalid = AtomicInt(0)

    val save: ((Int) -> Unit)? =
        checkpoint?.let { sink ->
            { accepted ->
                if (accepted % checkpointEvery == 0) {
                    val (index, ids) = builder.build()
                    sink.save(TrustNetworkPartial(TrustNetworkHeader(provider, relay.url, syncCursor = 0, lastFullCheck = 0, lastUpdate = 0), index, ids))
                }
            }
        }

    var complete: Boolean
    var detail: String
    // Resuming: reconcile what the cut-off download had with the relay, so only the rest is
    // fetched and a card the relay replaced or dropped since then goes. A relay without NIP-77
    // gets the whole walk again; the builder merges the cards it already had.
    val resumed = resume?.let { reconcileIds(relay, filter, it.ids.negentropyIndex()) }
    if (resumed != null) {
        Log.d(TAG) { "Resuming a download of ${builder.cardCount} cards: need ${resumed.need.size}, gone ${resumed.have.size}" }
        val reconciled = applyDiff(builder, relay, resumed, invalid, progress, seeded = 0, expected = expected, onAccepted = save)
        complete = reconciled.complete
        detail = "resumed from ${resume.cards}: need ${reconciled.need}, gone ${reconciled.gone}"
    } else {
        val paged = verifying(builder, invalid, expected, progress, seeded = 0, onAccepted = save) { submit -> fetchAllPages(relay, listOf(filter), IDLE_MS) { submit(it) } }
        complete = paged.drained
        detail = "${paged.end}${paged.message?.let { ": $it" } ?: ""}"
        if (!complete && paged.end == PagedFetchResult.End.UNPAGEABLE) {
            Log.d(TAG) { "Paging stalled on a shared timestamp at ${builder.cardCount} cards; switching to negentropy" }
            try {
                val local = builder.idsAndTimes()
                val synced =
                    verifying(builder, invalid, expected, progress, seeded = 0, onAccepted = save) { submit ->
                        negentropySync(relay, filter, localEntries = local, idleTimeoutMs = IDLE_MS) { submit(it) }
                    }
                // By-id batches that time out are dropped without an error: only a full count is complete.
                complete = synced.downloaded >= synced.needCount
                detail = "negentropy ${synced.downloaded} of ${synced.needCount}"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Negentropy fallback failed on ${relay.url}", e)
                detail = "negentropy failed: ${e.message}"
            }
        }
    }

    // A provider still publishing its first run (right after sign-up) leaves the relay with more
    // cards than the walk saw: everyone not yet published would count as outside the network.
    // Recount, and call the download incomplete when clearly more arrived than it got; the next
    // try reads the finished batch. A few cards (a re-rank landing mid-download) are left to the
    // next update, whose counts will differ.
    if (complete) {
        val after = countOrNull(relay, filter)
        val got = builder.cardCount + invalid.load()
        if (after != null && after - got > missingAllowed(after)) {
            complete = false
            detail = "the provider is still publishing: $got cards downloaded, $after on the relay now"
        }
    }

    val now = TimeUtils.now()
    val (index, ids) = builder.build()
    val cursor = cursorAfter(0, builder.newestCreatedAt, startedAt)
    return TrustNetworkSyncResult(
        header = TrustNetworkHeader(provider, relay.url, syncCursor = cursor, lastFullCheck = now, lastUpdate = now, heldAtCursor = ids.countSince(cursor), heldAfterCursor = ids.countSince(cursor + 1)),
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
 * Pass [knownNews] when the caller already asked [trustNetworkNews], so the counts are not repeated.
 */
@OptIn(ExperimentalAtomicApi::class)
suspend fun INostrClient.updateTrustNetwork(
    header: TrustNetworkHeader,
    index: TrustNetworkIndex,
    ids: TrustNetworkIds,
    relay: NormalizedRelayUrl,
    progress: TrustNetworkProgress? = null,
    knownNews: TrustNetworkNews? = null,
): TrustNetworkSyncResult {
    val startedAt = TimeUtils.now()
    val provider = header.provider
    val newer = header.syncCursor + 1
    val news =
        knownNews ?: trustNetworkNews(
            header,
            relay,
            held = header.heldAtCursor ?: ids.countSince(header.syncCursor),
            heldAfter = header.heldAfterCursor ?: ids.countSince(header.syncCursor + 1),
        )
    if (!news.any) {
        return TrustNetworkSyncResult(header.copy(lastUpdate = startedAt), index, ids, complete = true, invalid = 0, received = 0, detail = "nothing new", unchanged = true)
    }
    val newDeletions = news.deletions

    val windowStart = (header.syncCursor - TRUST_NETWORK_UPDATE_OVERLAP_SECS).coerceAtLeast(0)
    val diff = reconcileIds(relay, trustNetworkFilter(provider, windowStart), ids.negentropyIndex())

    // The counts said something changed but the ids say nothing did (a relay without NIP-45
    // always says so): no index is built, nothing is rewritten, no feed rebuilds.
    if (diff != null && diff.isEmpty && newDeletions == 0) {
        return TrustNetworkSyncResult(header.copy(lastUpdate = TimeUtils.now()).withHeldCounts(ids), index, ids, complete = true, invalid = 0, received = 0, detail = "need 0, gone 0", unchanged = true)
    }

    val builder = TrustNetworkBuilder(provider, initialCapacity = index.size + ids.tombstones + (diff?.need?.size ?: 1024))
    builder.addAll(index, ids)
    val before = builder.cardCount
    val invalid = AtomicInt(0)

    val reconciled = diff?.let { applyDiff(builder, relay, it, invalid, progress, seeded = before) }

    val complete: Boolean
    val detail: String
    if (reconciled != null) {
        val deletions =
            if (newDeletions == 0) {
                null
            } else {
                verifying(builder, invalid, null, progress, seeded = before) { submit -> fetchAllPages(relay, listOf(trustNetworkDeletionFilter(provider, newer)), IDLE_MS) { submit(it) } }
            }
        complete = reconciled.complete && (deletions == null || deletions.drained)
        detail = "need ${reconciled.need}, gone ${reconciled.gone}" + (deletions?.let { ", deletions ${it.downloaded}" } ?: "")
    } else {
        val filters = listOf(trustNetworkFilter(provider, newer), trustNetworkDeletionFilter(provider, newer))
        val paged = verifying(builder, invalid, null, progress, seeded = before) { submit -> fetchAllPages(relay, filters, IDLE_MS) { submit(it) } }
        complete = paged.drained
        detail = "${paged.end}${paged.message?.let { ": $it" } ?: ""}"
    }

    // The counts said something changed, but nothing arrived or went (a relay without NIP-45
    // always says so): keep the same index, so nothing is rewritten and no feed rebuilds.
    if (complete && builder.cardCount == before && !builder.hasRemovals) {
        return TrustNetworkSyncResult(header.copy(lastUpdate = TimeUtils.now()).withHeldCounts(ids), index, ids, complete = true, invalid = invalid.load(), received = 0, detail = detail, unchanged = true)
    }

    val (newIndex, newIds) = builder.build()
    val cursor = cursorAfter(header.syncCursor, builder.newestCreatedAt, startedAt)
    return TrustNetworkSyncResult(
        header = header.copy(syncCursor = cursor, lastUpdate = TimeUtils.now()).withHeldCounts(newIds),
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
    val startedAt = TimeUtils.now()
    val diff = reconcileIds(relay, trustNetworkFilter(header.provider), ids.negentropyIndex()) ?: return null
    if (diff.isEmpty) {
        val now = TimeUtils.now()
        return TrustNetworkSyncResult(header.copy(lastFullCheck = now, lastUpdate = now).withHeldCounts(ids), index, ids, complete = true, invalid = 0, received = 0, detail = "need 0, gone 0", unchanged = true)
    }

    val builder = TrustNetworkBuilder(header.provider, initialCapacity = index.size + ids.tombstones + diff.need.size)
    builder.addAll(index, ids)
    val before = builder.cardCount
    val invalid = AtomicInt(0)
    val reconciled = applyDiff(builder, relay, diff, invalid, progress, seeded = before)

    val (newIndex, newIds) = builder.build()
    val now = TimeUtils.now()
    val cursor = cursorAfter(header.syncCursor, builder.newestCreatedAt, startedAt)
    return TrustNetworkSyncResult(
        header = header.copy(syncCursor = cursor, lastFullCheck = now, lastUpdate = now).withHeldCounts(newIds),
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

/** What a NIP-77 reconcile found: ids the relay has and we lack, ids only we hold. */
private class IdDiff(
    val need: List<HexKey>,
    val have: List<HexKey>,
) {
    val isEmpty: Boolean get() = need.isEmpty() && have.isEmpty()
}

/** NIP-77 diff of [filter] against [local]. Null when the relay cannot reconcile. */
private suspend fun INostrClient.reconcileIds(
    relay: NormalizedRelayUrl,
    filter: Filter,
    local: NegentropyLocalIndex,
): IdDiff? {
    val need = ArrayList<HexKey>()
    val have = ArrayList<HexKey>()
    try {
        negentropyReconcile(
            relay = relay,
            filter = filter,
            localIndex = local,
            targetWindow = NEGENTROPY_WINDOW,
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
    return IdDiff(need, have)
}

/**
 * Applies [diff] to [builder]: fetches (and verifies) the cards it needs and drops the ones the
 * relay no longer has. Complete unless more than [missingAllowed] cards could not be had.
 */
@OptIn(ExperimentalAtomicApi::class)
private suspend fun INostrClient.applyDiff(
    builder: TrustNetworkBuilder,
    relay: NormalizedRelayUrl,
    diff: IdDiff,
    invalid: AtomicInt,
    progress: TrustNetworkProgress?,
    seeded: Int,
    expected: Int? = diff.need.size,
    onAccepted: ((Int) -> Unit)? = null,
): Reconciled {
    val missing = AtomicInt(0)
    val invalidBefore = invalid.load()
    if (diff.need.isNotEmpty()) {
        verifying(builder, invalid, expected, progress, seeded, onAccepted) { submit ->
            // A few batches in flight at once: a provider recompute can need every card.
            val slots = Semaphore(FETCH_BY_ID_CONCURRENCY)
            coroutineScope {
                for (batch in diff.need.chunked(FETCH_BY_ID_BATCH)) {
                    launch {
                        slots.withPermit { missing.addAndFetch(fetchByIds(relay, batch, submit)) }
                    }
                }
            }
        }
    }
    // A card whose signature failed counts as missing: the relay did not give us a valid one.
    val unobtained = missing.load() + (invalid.load() - invalidBefore)
    // The cards only we hold are mostly ones the relay replaced with a newer version, and the diff
    // does not say which replacement goes with which. When every needed card arrived, the rest are
    // gone for good. When some did not, dropping them would take out the people whose new card
    // failed, so keep them all: each one that was replaced loses to its newer card in the build,
    // and the truly gone are dropped by the next reconcile that gets everything.
    val gone =
        if (unobtained == 0) {
            builder.removeEventIds(diff.have)
            diff.have.size
        } else {
            Log.w(TAG) { "$unobtained of ${diff.need.size} cards could not be had from ${relay.url}; keeping the ${diff.have.size} it no longer lists until a reconcile gets them all" }
            0
        }
    return Reconciled(unobtained <= missingAllowed(diff.need.size), diff.need.size, gone)
}

/**
 * Fetches [ids] from [relay] and hands each event to [submit]. A relay whose max_limit is below
 * the batch answers part of it, so this asks again for the rest until it stops making progress.
 * Returns how many ids never came.
 */
private suspend fun INostrClient.fetchByIds(
    relay: NormalizedRelayUrl,
    ids: List<HexKey>,
    submit: (Event) -> Unit,
): Int {
    var missing = ids.toHashSet()
    while (missing.isNotEmpty()) {
        val events = fetchAll(relay, Filter(ids = missing.toList()), IDLE_MS)
        var progress = false
        for (event in events) {
            // Only what was asked for: a relay answering with other events must not loop forever.
            if (missing.remove(event.id)) {
                progress = true
                submit(event)
            }
        }
        if (!progress) return missing.size
    }
    return 0
}

/**
 * The sync cursor after a run: the newest `created_at` applied, but never past when the run
 * started. A card signed with a clock running ahead must not push the cursor into the future,
 * where it would hide every later card from the "anything new?" count.
 */
private fun cursorAfter(
    previous: Long,
    newest: Long,
    startedAt: Long,
) = maxOf(previous, minOf(newest, startedAt))

/** What the relay's counts say changed since [TrustNetworkHeader.syncCursor]. */
class TrustNetworkNews(
    /** Cards at or after the cursor differ from the ones held, or the relay could not count. */
    val cards: Boolean,
    /** Deletions newer than the cursor, or null when the relay could not count. */
    val deletions: Int?,
) {
    val any: Boolean get() = cards || deletions != 0
}

/**
 * Two NIP-45 COUNTs of a few bytes each: the relay's cards at or after the cursor against the
 * [held] count, and deletions after it. Counting the cursor's own second catches a batch that
 * was still arriving at the last sync (providers sign a whole run with one `created_at`).
 */
suspend fun INostrClient.trustNetworkNews(
    header: TrustNetworkHeader,
    relay: NormalizedRelayUrl,
    held: Int? = header.heldAtCursor,
    heldAfter: Int? = header.heldAfterCursor,
): TrustNetworkNews {
    val relayCards = countOrNull(relay, trustNetworkFilter(header.provider, header.syncCursor))
    // A replacement in the cursor's batch keeps the count above equal (the relay drops the old
    // card as it stores the new one), but the new card is always newer than the old: counting
    // past the cursor catches it.
    val relayNewer = countOrNull(relay, trustNetworkFilter(header.provider, header.syncCursor + 1))
    val deletions = countOrNull(relay, trustNetworkDeletionFilter(header.provider, header.syncCursor + 1))
    val cards = held == null || relayCards == null || relayCards != held || heldAfter == null || relayNewer == null || relayNewer != heldAfter
    return TrustNetworkNews(cards = cards, deletions = deletions)
}

/** [TrustNetworkHeader.heldAtCursor] and [TrustNetworkHeader.heldAfterCursor] from [ids], the id column saved with this header. */
private fun TrustNetworkHeader.withHeldCounts(ids: TrustNetworkIds) = copy(heldAtCursor = ids.countSince(syncCursor), heldAfterCursor = ids.countSince(syncCursor + 1))

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
    /** Cards the builder held before this sync: progress counts only what the sync adds. */
    seeded: Int,
    /** Called with the running count after each accepted card, on the same single writer as the builder. */
    onAccepted: ((Int) -> Unit)? = null,
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
                        if (n % 1000 == 0) progress?.onProgress(builder.cardCount - seeded, expected)
                        onAccepted?.invoke(n)
                    }
                },
            )
        try {
            fetch { event -> verifier.submit(event, Unit) }
        } finally {
            verifier.close()
            verifier.join()
            progress?.onProgress(builder.cardCount - seeded, expected)
        }
    }
