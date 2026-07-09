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
package com.vitorpamplona.quartz.experimental.graperank

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.NegentropySyncException
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAll
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAllPages
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.negentropyReconcile
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.negentropySettleDeletions
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.publishAndConfirm
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.store.IEventStore
import com.vitorpamplona.quartz.nip01Core.store.IdAndTime
import com.vitorpamplona.quartz.nip01Core.store.verifyAndInsert
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip56Reports.ReportEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Store-driven, outbox-model refresh of the record kinds a [GrapeRank] score is a
 * function of — the profiles (kind:0), follows (kind:3), outbox relay lists
 * (kind:10002), and reports (kind:1984) of every author already known to the
 * local [store].
 *
 * Where [GrapeRankDataCrawler] discovers the graph by walking follows outward from
 * an observer, this refreshes what is *already* known: it reads every kind:10002 in
 * the store, inverts them into a `write-relay -> authors` map (the outbox model — an
 * author's events live on the relays they write to), then runs one NIP-77 negentropy
 * reconcile per write relay scoped to exactly the authors who publish there. So each
 * relay is asked only for the authors it hosts, and each author is reconciled only
 * against their own relays. Run it periodically to keep a scored network current
 * without paying a full from-scratch crawl.
 *
 * Each per-relay group is bidirectional by default ([Config.down] / [Config.up]):
 *  - **down** downloads records the relay has and the store lacks (by-id fetch,
 *    verified + inserted into [store]);
 *  - **up** uploads records the store has and the relay lacks.
 *
 * After the content pass each group settles deletions over the reconcile residual
 * ([negentropySettleDeletions], [Config.syncDeletions]). Its **applyDown** direction
 * is the "download the deletion when our upload was rejected" case: an event pushed up
 * that the relay keeps rejecting (it deleted it) surfaces as a residual *have*, so the
 * relay's covering kind:5 is pulled down and applied locally and the store drops the
 * retracted record. The **sendUp** direction publishes the store's covering deletions
 * for records deleted locally that the relay still serves. Cheap: it works off the
 * small residual, not the whole set.
 *
 * If negentropy can't reconcile a relay (no NIP-77 support, an over-cap minimal window,
 * a mid-sync disconnect, …) and [Config.pageFallback] is on, the group falls back to a
 * full paged download ([fetchAllPages]) of the same authors+kinds so those records are
 * still refreshed — only the negentropy-only deletion settle is skipped there.
 *
 * Transport-agnostic within quartz: it takes an [INostrClient] and an [IEventStore].
 * Progress is emitted through [log]; a headless caller routes it to stderr, a UI ignores it.
 */
@OptIn(ExperimentalAtomicApi::class)
class GrapeRankUpdater(
    private val client: INostrClient,
    private val store: IEventStore,
    private val config: Config = Config(),
    private val log: (String) -> Unit = {},
) {
    /**
     * @param kinds the record kinds refreshed per author (default: the WoT set
     *   0 / 3 / 10002 / 1984).
     * @param down download records the relay has that the store lacks.
     * @param up upload records the store has that the relay lacks (also arms the
     *   deletion **applyDown** path — a rejected upload pulls the relay's kind:5 down).
     * @param syncDeletions run the deletion settle over the reconcile residual.
     * @param pageFallback page the filter when negentropy can't reconcile a relay.
     * @param idChunk ids per reconcile chunk and per by-id fetch.
     * @param downloadWorkers concurrent by-id download fetches per group.
     * @param reconcileConcurrency overlapped `created_at`-window reconciles after an over-cap split.
     * @param maxDeletionRounds hard cap on deletion-settle rounds (converges in 1–2).
     * @param relayConcurrency write relays reconciled at once.
     * @param authorChunk authors per reconcile filter (a relay with more is split into several).
     * @param minAuthors skip relays hosting fewer than this many of the store's authors.
     * @param idleTimeoutMs idle watchdog for reconciles / fetches / pages.
     * @param publishTimeoutSecs OK-confirmation wait per uploaded event.
     */
    class Config(
        val kinds: List<Int> = DEFAULT_KINDS,
        val down: Boolean = true,
        val up: Boolean = true,
        val syncDeletions: Boolean = true,
        val pageFallback: Boolean = true,
        val idChunk: Int = 500,
        val downloadWorkers: Int = 4,
        val reconcileConcurrency: Int = 2,
        val maxDeletionRounds: Int = 4,
        val relayConcurrency: Int = 4,
        val authorChunk: Int = 500,
        val minAuthors: Int = 1,
        val idleTimeoutMs: Long = 30_000L,
        val publishTimeoutSecs: Long = 15,
    )

    /** Per-write-relay outcome of an [update]. */
    class RelayResult(
        val relay: NormalizedRelayUrl,
        val authors: Int,
        val need: Int,
        val have: Int,
        val downloaded: Int,
        val uploaded: Int,
        val deletionsSentUp: Int,
        val deletionsAppliedDown: Int,
        val pagedFallback: Boolean,
        /** null on success; the negentropy (and any page-fallback) failure otherwise. */
        val error: String?,
    )

    /** Aggregate outcome of an [update], plus the per-relay breakdown. */
    class Result(
        val relayListsInStore: Int,
        val authorsWithOutbox: Int,
        val relays: Int,
        val relaysOk: Int,
        val relaysFailed: Int,
        val relaysPagedFallback: Int,
        val downloaded: Int,
        val uploaded: Int,
        val deletionsSentUp: Int,
        val deletionsAppliedDown: Int,
        val perRelay: List<RelayResult>,
    )

    /**
     * Group the store's authors by their kind:10002 write relays (the outbox model).
     * The latest kind:10002 per author wins; an author with no write-marked relays
     * contributes nothing (there is nowhere to reconcile them). Public so callers can
     * inspect the plan (relay count, largest groups) before running [update].
     */
    suspend fun writeRelayGroups(): Map<NormalizedRelayUrl, Set<HexKey>> = groupByWriteRelay(loadLatestRelayLists())

    /** Latest kind:10002 per author from the store (replaceable — newest createdAt wins). */
    private suspend fun loadLatestRelayLists(): Map<HexKey, AdvertisedRelayListEvent> {
        val latest = HashMap<HexKey, AdvertisedRelayListEvent>()
        for (event in store.query<Event>(Filter(kinds = listOf(AdvertisedRelayListEvent.KIND)))) {
            if (event !is AdvertisedRelayListEvent) continue
            val prev = latest[event.pubKey]
            if (prev == null || event.createdAt > prev.createdAt) latest[event.pubKey] = event
        }
        return latest
    }

    /** Invert the per-author relay lists into `write-relay -> authors`. */
    private fun groupByWriteRelay(latest: Map<HexKey, AdvertisedRelayListEvent>): Map<NormalizedRelayUrl, Set<HexKey>> {
        val relayToAuthors = HashMap<NormalizedRelayUrl, MutableSet<HexKey>>()
        for ((author, list) in latest) {
            val writes = list.writeRelaysNorm() ?: continue
            for (relay in writes) relayToAuthors.getOrPut(relay) { HashSet() }.add(author)
        }
        return relayToAuthors
    }

    /**
     * Run the full outbox-model refresh: [writeRelayGroups] then one per-relay sync
     * for each group hosting at least [Config.minAuthors] authors, up to
     * [Config.relayConcurrency] relays at once (largest groups first). Best-effort —
     * a relay that fails is recorded in its [RelayResult.error] and never aborts the run.
     */
    suspend fun update(): Result {
        val latest = loadLatestRelayLists()
        val groups = groupByWriteRelay(latest)
        val authorsWithOutbox = groups.values.flatMapTo(HashSet()) { it }.size

        val plan =
            groups.entries
                .filter { it.value.size >= config.minAuthors }
                .sortedByDescending { it.value.size }

        val perRelay =
            if (plan.isEmpty()) {
                emptyList()
            } else {
                val gate = Semaphore(config.relayConcurrency.coerceAtLeast(1))
                coroutineScope {
                    plan
                        .map { (relay, authors) ->
                            async { gate.withPermit { syncRelay(relay, authors) } }
                        }.awaitAll()
                }
            }

        return Result(
            relayListsInStore = latest.size,
            authorsWithOutbox = authorsWithOutbox,
            relays = plan.size,
            relaysOk = perRelay.count { it.error == null },
            relaysFailed = perRelay.count { it.error != null },
            relaysPagedFallback = perRelay.count { it.pagedFallback },
            downloaded = perRelay.sumOf { it.downloaded },
            uploaded = perRelay.sumOf { it.uploaded },
            deletionsSentUp = perRelay.sumOf { it.deletionsSentUp },
            deletionsAppliedDown = perRelay.sumOf { it.deletionsAppliedDown },
            perRelay = perRelay,
        )
    }

    /** Sync one write relay by folding each [Config.authorChunk]-sized author slice. */
    private suspend fun syncRelay(
        relay: NormalizedRelayUrl,
        authors: Set<HexKey>,
    ): RelayResult {
        var downloaded = 0
        var uploaded = 0
        var delUp = 0
        var delDown = 0
        var need = 0
        var have = 0
        var paged = false
        var error: String? = null

        for (chunk in authors.toList().chunked(config.authorChunk.coerceAtLeast(1))) {
            val filter = Filter(kinds = config.kinds, authors = chunk)
            val res = syncGroup(relay, filter)
            downloaded += res.downloaded
            uploaded += res.uploaded
            delUp += res.deletionsSentUp
            delDown += res.deletionsAppliedDown
            need += res.need
            have += res.have
            if (res.pagedFallback) paged = true
            if (res.error != null) error = res.error
        }

        log(
            "[graperank update] ${relay.url}: ${authors.size} authors, " +
                "down $downloaded, up $uploaded, del↑ $delUp, del↓ $delDown" +
                (if (paged) " (paged fallback)" else "") +
                (if (error != null) " (error: $error)" else ""),
        )
        return RelayResult(relay, authors.size, need, have, downloaded, uploaded, delUp, delDown, paged, error)
    }

    /** One relay + one author chunk. Mirrors the two-pass content+deletion sync. */
    private suspend fun syncGroup(
        relay: NormalizedRelayUrl,
        filter: Filter,
    ): GroupResult {
        val localEvents = store.query<Event>(filter)
        val localById = localEvents.associateBy { it.id }
        val localEntries = localEvents.map { IdAndTime(it.createdAt, it.id) }

        val downloaded = AtomicInt(0)
        val uploaded = AtomicInt(0)

        val reconcileResult =
            try {
                coroutineScope {
                    // needIds = relay has, store lacks; haveIds = store has, relay lacks.
                    val needBatches = Channel<List<HexKey>>(config.downloadWorkers * 2)
                    val haveBatches = Channel<List<HexKey>>(Channel.UNLIMITED)

                    val downloaders =
                        List(config.downloadWorkers.coerceAtLeast(1)) {
                            launch {
                                for (batch in needBatches) {
                                    for (event in client.fetchAll(relay, Filter(ids = batch), config.idleTimeoutMs)) {
                                        if (store.verifyAndInsert(event)) downloaded.addAndFetch(1)
                                    }
                                }
                            }
                        }
                    val uploader =
                        launch {
                            for (batch in haveBatches) {
                                for (id in batch) {
                                    val ev = localById[id] ?: continue
                                    if (client.publishAndConfirm(ev, setOf(relay), config.publishTimeoutSecs)) uploaded.addAndFetch(1)
                                }
                            }
                        }

                    val result =
                        try {
                            client.negentropyReconcile(
                                relay = relay,
                                filter = filter,
                                localEntries = localEntries,
                                batchSize = config.idChunk,
                                idleTimeoutMs = config.idleTimeoutMs,
                                reconcileConcurrency = config.reconcileConcurrency,
                                onHaveIds = if (config.up) { batch -> haveBatches.send(batch) } else null,
                                onNeedIds = { batch -> if (config.down) needBatches.send(batch) },
                            )
                        } finally {
                            needBatches.close()
                            haveBatches.close()
                        }

                    downloaders.joinAll()
                    uploader.join()
                    result
                }
            } catch (e: NegentropySyncException) {
                // Negentropy couldn't reconcile — page the same authors+kinds so the
                // records still refresh. Deletion settle is negentropy-only, so skipped.
                var pageError: String? = e.message ?: "negentropy sync failed"
                if (config.pageFallback && config.down) {
                    pageError =
                        try {
                            downloaded.addAndFetch(pageDownload(relay, filter))
                            null
                        } catch (pe: Exception) {
                            "negentropy: ${e.message}; page fallback: ${pe::class.simpleName}: ${pe.message}"
                        }
                }
                return GroupResult(downloaded.load(), uploaded.load(), 0, 0, 0, 0, pagedFallback = true, error = pageError)
            }

        val deletions =
            if (config.syncDeletions) {
                client.negentropySettleDeletions(
                    relay = relay,
                    filter = filter,
                    store = store,
                    sendUp = config.down,
                    applyDown = config.up,
                    batchSize = config.idChunk,
                    idleTimeoutMs = config.idleTimeoutMs,
                    maxRounds = config.maxDeletionRounds,
                    reconcileConcurrency = config.reconcileConcurrency,
                )
            } else {
                null
            }

        return GroupResult(
            downloaded = downloaded.load(),
            uploaded = uploaded.load(),
            deletionsSentUp = deletions?.sentUp ?: 0,
            deletionsAppliedDown = deletions?.appliedDown ?: 0,
            need = reconcileResult.needCount,
            have = reconcileResult.haveCount,
            pagedFallback = false,
            error = null,
        )
    }

    /**
     * Paged fallback: walk [relay] past its per-REQ cap for [filter], verifying and
     * inserting each event into [store]. [fetchAllPages]'s `onEvent` can't suspend, so
     * events funnel through a bounded channel to a single inserter. Returns how many
     * were newly stored.
     */
    private suspend fun pageDownload(
        relay: NormalizedRelayUrl,
        filter: Filter,
    ): Int {
        val stored = AtomicInt(0)
        val events = Channel<Event>(Channel.UNLIMITED)
        coroutineScope {
            val inserter =
                launch {
                    for (event in events) {
                        if (store.verifyAndInsert(event)) stored.addAndFetch(1)
                    }
                }
            try {
                client.fetchAllPages(relay, listOf(filter), config.idleTimeoutMs) { event -> events.trySend(event) }
            } finally {
                events.close()
            }
            inserter.join()
        }
        return stored.load()
    }

    private class GroupResult(
        val downloaded: Int,
        val uploaded: Int,
        val deletionsSentUp: Int,
        val deletionsAppliedDown: Int,
        val need: Int,
        val have: Int,
        val pagedFallback: Boolean,
        val error: String?,
    )

    companion object {
        /** The record kinds a GrapeRank score is a function of. */
        val DEFAULT_KINDS =
            listOf(
                MetadataEvent.KIND, // 0     — profiles
                ContactListEvent.KIND, // 3     — follows
                AdvertisedRelayListEvent.KIND, // 10002 — outbox relay lists
                ReportEvent.KIND, // 1984  — reports
            )
    }
}
