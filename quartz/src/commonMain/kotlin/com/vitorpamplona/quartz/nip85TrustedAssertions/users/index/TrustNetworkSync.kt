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

/** Receives a [TrustNetworkPartial] as a cold download grows: first at [TRUST_NETWORK_CHECKPOINT_EVERY] cards, then each time it grows by half. */
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

/** How far before the sync cursor each update counts: catches a batch still arriving at the last sync, and late relay arrivals. */
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
 * `max_limit` (providers publish in batches that share a `created_at`), paging steps past that
 * second and loses its tail, or cannot page at all (`UNPAGEABLE`). Either way the relay's count
 * comes out higher than what arrived, and the sync switches to NIP-77 negentropy, passing what it
 * already has so only the remainder is downloaded.
 *
 * Every signature is checked ([ParallelEventVerifier], all cores, bounded backlog so the socket
 * slows instead of the heap growing). Events are never held: each verified card goes straight
 * into a [TrustNetworkBuilder].
 *
 * No kind 5 is downloaded: the relay serves only the cards it still holds, so a card retracted
 * before the download is simply not in it. Fetching the provider's deletions anyway cost more
 * than the cards (scores.brainstorm.world, measured: 341,569 kind 5s, 380 MB, against 151,831
 * cards in 70 MB) and removed none of them. A relay that keeps a deleted card anyway is the
 * reconciles' to correct: they drop whatever it stops serving.
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
    /** Saves what has arrived: first at [checkpointEvery] cards, then each time the download grows by half. */
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

    // Each checkpoint sorts and writes everything so far, so they are spaced geometrically: at
    // [checkpointEvery], then whenever the download has grown by half. A fixed step would cost
    // O(n²) over a million cards, on the coroutine that drains the socket.
    // Counted on the builder, not per phase: each phase (the walk, a negentropy fill, a resume's
    // fetch) counts its own cards from 0, and against a checkpoint taken in an earlier phase a
    // fill never reached the next one.
    var lastCheckpoint = builder.cardCount
    val save: ((Int) -> Unit)? =
        checkpoint?.let { sink ->
            { _ ->
                val cards = builder.cardCount
                if (cards - lastCheckpoint >= maxOf(checkpointEvery, lastCheckpoint / 2)) {
                    lastCheckpoint = cards
                    val (index, ids) = builder.build()
                    sink.save(TrustNetworkPartial(TrustNetworkHeader(provider, relay.url, syncCursor = 0, lastFullCheck = 0, lastUpdate = 0), index, ids))
                }
            }
        }

    var complete: Boolean
    var detail: String
    var triedNegentropy = false

    // Fetches what the relay has and the builder lacks. Only a full count is complete: by-id
    // batches that time out are dropped without an error.
    suspend fun fillByNegentropy(): Boolean {
        triedNegentropy = true
        return try {
            val local = builder.idsAndTimes()
            val synced =
                verifying(builder, invalid, expected, progress, seeded = 0, onAccepted = save) { submit ->
                    negentropySync(relay, filter, localEntries = local, idleTimeoutMs = IDLE_MS) { submit(it) }
                }
            detail = "negentropy ${synced.downloaded} of ${synced.needCount}"
            synced.downloaded >= synced.needCount
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Negentropy fallback failed on ${relay.url}", e)
            detail = "negentropy failed: ${e.message}"
            false
        }
    }

    // Resuming: reconcile what the cut-off download had with the relay, so only the rest is
    // fetched and a card the relay replaced or dropped since then goes. A relay without NIP-77
    // gets the whole walk again; the builder merges the cards it already had.
    val resumed = resume?.let { reconcileIds(relay, filter, it.ids.negentropyIndex()) }
    if (resumed != null) {
        Log.d(TAG) { "Resuming a download of ${builder.cardCount} cards: need ${resumed.need.size}, gone ${resumed.have.size}" }
        val reconciled = applyDiff(builder, relay, resumed, invalid, progress, seeded = 0, seededIds = resume.ids, expected = expected, onAccepted = save)
        complete = reconciled.complete
        detail = "resumed from ${resume.cards}: need ${reconciled.need}, gone ${reconciled.gone}"
        triedNegentropy = true
    } else {
        val paged = verifying(builder, invalid, expected, progress, seeded = 0, onAccepted = save) { submit -> fetchAllPages(relay, listOf(filter), IDLE_MS) { submit(it) } }
        complete = paged.drained
        detail = "${paged.end}${paged.message?.let { ": $it" } ?: ""}"
        if (!complete && paged.end == PagedFetchResult.End.UNPAGEABLE) {
            Log.d(TAG) { "Paging stalled on a shared timestamp at ${builder.cardCount} cards; switching to negentropy" }
            complete = fillByNegentropy()
        }
    }

    // What the relay holds against what arrived: distinct cards, counting the versions and
    // removals kept as tombstones and the cards that failed verification. Not the builder's raw
    // count, which holds a resume's seeded cards and cards delivered twice.
    var built = builder.build()

    fun held() = built.first.size + built.second.tombstones + invalid.load()

    // Short of the relay's count: either a second denser than the relay's page (paging stepped
    // past its tail and still drained) or a provider still publishing its first run (right after
    // sign-up), whose unpublished people would count as outside the network. Negentropy fetches
    // exactly what is missing; if the count is still short, the download is incomplete and the
    // next try reads the finished batch. A few cards (a re-rank landing mid-download) are left to
    // the next update, whose counts will differ.
    if (complete) {
        var after = countOrNull(relay, filter)
        if (after != null && after - held() > missingAllowed(after) && !triedNegentropy) {
            Log.d(TAG) { "The walk got ${held()} of $after cards; fetching the rest by negentropy" }
            complete = fillByNegentropy()
            built = builder.build()
            after = countOrNull(relay, filter)
        }
        if (complete && after != null && after - held() > missingAllowed(after)) {
            complete = false
            detail = "the provider is still publishing: ${held()} cards downloaded, $after on the relay now"
        }
    }

    val now = TimeUtils.now()
    val (index, ids) = built
    val cursor = cursorAfter(0, builder.newestCreatedAt, startedAt)
    return TrustNetworkSyncResult(
        header = TrustNetworkHeader(provider, relay.url, syncCursor = cursor, lastFullCheck = now, lastUpdate = now).withHeldCounts(ids),
        index = index,
        ids = ids,
        complete = complete,
        invalid = invalid.load(),
        received = builder.cardCount,
        detail = detail,
    )
}

/**
 * Applies what [header]'s provider changed since the last sync, without re-downloading what
 * the index already holds. Providers publish in batches that share a `created_at` (one
 * Brainstorm run signed 155k cards within a few seconds), so re-asking a time window would
 * re-download the whole batch every time.
 *
 *  1. **Is there anything new?** NIP-45 COUNTs ([trustNetworkNews]): the relay's cards in the
 *     update window, newer than the cursor, and in all, each against the cards (entries and
 *     tombstones) held for the same range. A few bytes each. All equal means nothing changed,
 *     and the update ends there, so it is cheap enough to run on every app open.
 *  2. **What changed:** a NIP-77 reconcile of every card the provider published against every
 *     id held. Only missing cards are fetched; cards the relay no longer serves (a deletion it
 *     applied, a version it replaced) are dropped. Cheap even over the whole set
 *     (scores.brainstorm.world, measured: 151k cards reconciled in ~40 KB), and it learns of
 *     retractions without a single kind 5: a provider's deletions can outweigh its cards many
 *     times over (341k kind 5s, 380 MB, against 151k cards).
 *
 * Relays without NIP-77 get a plain fetch of the cards and deletions strictly newer than the
 * cursor instead: there, a kind 5 is the only way to learn of a retraction. Late arrivals at the
 * cursor's second are then left to the weekly [reconcileTrustNetwork].
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
            held = header.heldAtCursor ?: ids.countSince(header.windowStart()),
            heldAfter = header.heldAfterCursor ?: ids.countSince(header.syncCursor + 1),
            heldTotal = header.heldTotal ?: ids.total,
        )
    if (!news.any) {
        return TrustNetworkSyncResult(header.copy(lastUpdate = startedAt), index, ids, complete = true, invalid = 0, received = 0, detail = "nothing new", unchanged = true)
    }

    val diff = reconcileIds(relay, trustNetworkFilter(provider), ids.negentropyIndex())

    // The counts said something changed but the ids say nothing did (a relay without NIP-45
    // always says so): no index is built, nothing is rewritten, no feed rebuilds.
    if (diff != null && diff.isEmpty) {
        return TrustNetworkSyncResult(header.copy(lastUpdate = TimeUtils.now()).withHeldCounts(ids), index, ids, complete = true, invalid = 0, received = 0, detail = "need 0, gone 0", unchanged = true)
    }

    val builder = TrustNetworkBuilder(provider, initialCapacity = index.size + ids.tombstones + (diff?.need?.size ?: 1024))
    builder.addAll(index, ids)
    val before = builder.cardCount
    val invalid = AtomicInt(0)

    val reconciled = diff?.let { applyDiff(builder, relay, it, invalid, progress, seeded = before, seededIds = ids) }

    val complete: Boolean
    val detail: String
    if (reconciled != null) {
        complete = reconciled.complete
        detail = "need ${reconciled.need}, gone ${reconciled.gone}"
    } else {
        val filters = listOf(trustNetworkFilter(provider, newer), trustNetworkDeletionFilter(provider, newer))
        val paged = verifying(builder, invalid, null, progress, seeded = before) { submit -> fetchAllPages(relay, filters, IDLE_MS) { submit(it) } }
        complete = paged.drained
        detail = "${paged.end}${paged.message?.let { ": $it" } ?: ""}"
    }

    // The counts said something changed, but nothing arrived or went (a relay without NIP-45
    // always says so): keep the same index, so nothing is rewritten and no feed rebuilds. A kind 5
    // that named nothing here still moves the cursor past it, or the next update finds it again.
    if (complete && builder.cardCount == before && !builder.hasRemovals) {
        val cursor = cursorAfter(header.syncCursor, builder.newestCreatedAt, startedAt)
        return TrustNetworkSyncResult(header.copy(syncCursor = cursor, lastUpdate = TimeUtils.now()).withHeldCounts(ids), index, ids, complete = true, invalid = invalid.load(), received = 0, detail = detail, unchanged = true)
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
    val reconciled = applyDiff(builder, relay, diff, invalid, progress, seeded = before, seededIds = ids)

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
    /** The ids column the builder was seeded with: tells its tombstones apart. */
    seededIds: TrustNetworkIds,
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
            // Tombstones still go: they are rank 0 and never the member of their subject, so
            // dropping one cannot put anyone in. Kept, the ones a newer card replaced would pile
            // up for as long as one card stays unobtainable.
            val tombstones = seededIds.tombstoneIdSet()
            val goneTombstones = diff.have.filter { it in tombstones }
            builder.removeEventIds(goneTombstones)
            Log.w(TAG) { "$unobtained of ${diff.need.size} cards could not be had from ${relay.url}; keeping the ${diff.have.size - goneTombstones.size} cards it no longer lists until a reconcile gets them all" }
            goneTombstones.size
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

/** What the relay's counts say changed since the last sync. */
class TrustNetworkNews(
    /** The relay's cards differ from the ones held, or the relay could not count them. */
    val cards: Boolean,
) {
    val any: Boolean get() = cards
}

/**
 * Three NIP-45 COUNTs of a few bytes each, each against what is held for the same range: the
 * relay's cards in the update window (from [TRUST_NETWORK_UPDATE_OVERLAP_SECS] before the cursor)
 * against [held], its cards newer than the cursor against [heldAfter], and all its cards against
 * [heldTotal]. Counting the window catches a batch that was still arriving at the last sync
 * (providers sign a whole run with one `created_at`); the total catches a card the relay stopped
 * serving (a deletion it applied), which no count above the cursor sees.
 */
suspend fun INostrClient.trustNetworkNews(
    header: TrustNetworkHeader,
    relay: NormalizedRelayUrl,
    held: Int? = header.heldAtCursor,
    heldAfter: Int? = header.heldAfterCursor,
    heldTotal: Int? = header.heldTotal,
): TrustNetworkNews {
    val relayCards = countOrNull(relay, trustNetworkFilter(header.provider, header.windowStart()))
    // A replacement in the cursor's batch keeps the count above equal (the relay drops the old
    // card as it stores the new one), but the new card is always newer than the old: counting
    // past the cursor catches it.
    val relayNewer = countOrNull(relay, trustNetworkFilter(header.provider, header.syncCursor + 1))
    val relayTotal = countOrNull(relay, trustNetworkFilter(header.provider))
    val cards =
        held == null || relayCards == null || relayCards != held ||
            heldAfter == null || relayNewer == null || relayNewer != heldAfter ||
            heldTotal == null || relayTotal == null || relayTotal != heldTotal
    return TrustNetworkNews(cards = cards)
}

/**
 * Where an update starts looking: [TRUST_NETWORK_UPDATE_OVERLAP_SECS] before the cursor. The
 * cursor is the newest card or kind 5 applied, so the rest of a batch still arriving at the last
 * sync can sit a little below it (a kind 5 signed after the batch began moves the cursor past it).
 */
private fun TrustNetworkHeader.windowStart() = (syncCursor - TRUST_NETWORK_UPDATE_OVERLAP_SECS).coerceAtLeast(0)

/** The held counts ([TrustNetworkHeader.heldAtCursor], [TrustNetworkHeader.heldAfterCursor], [TrustNetworkHeader.heldTotal]) from [ids], the id column saved with this header. */
private fun TrustNetworkHeader.withHeldCounts(ids: TrustNetworkIds) = copy(heldAtCursor = ids.countSince(windowStart()), heldAfterCursor = ids.countSince(syncCursor + 1), heldTotal = ids.total)

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
