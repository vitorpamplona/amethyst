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
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.NegentropyStoreSync
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.store.IEventStore
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip56Reports.ReportEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent

/**
 * Store-driven, outbox-model refresh of the record kinds a [GrapeRank] score is a
 * function of — the profiles (kind:0), follows (kind:3), outbox relay lists
 * (kind:10002), and reports (kind:1984) of every author already known to the
 * local [store].
 *
 * Where [GrapeRankDataCrawler] discovers the graph by walking follows outward from
 * an observer, this refreshes what is *already* known: it reads every kind:10002 in
 * the store, inverts them into a `write-relay -> authors` map (the outbox model — an
 * author's events live on the relays they write to), fans those into one filter per
 * `(write relay, author chunk)`, and hands them to [NegentropyStoreSync] — the generic
 * two-pass sync engine. So each relay is asked only for the authors it hosts, and each
 * author is reconciled only against their own relays. Run it periodically to keep a
 * scored network current without paying a full from-scratch crawl.
 *
 * The engine does the work per `(relay, filter)` group: a bidirectional NIP-77
 * reconcile against [store] ([Config.down] / [Config.up]), a deletion settle over the
 * residual ([Config.syncDeletions] — its applyDown direction downloads the relay's
 * kind:5 when an uploaded record was rejected because the author retracted it), and a
 * paged-download fallback when a relay can't reconcile ([Config.pageFallback]). This
 * class only builds the outbox filter set and folds the engine's per-group results back
 * up per relay.
 *
 * Transport-agnostic within quartz: it takes an [INostrClient] and an [IEventStore].
 * Progress is emitted through [log]; a headless caller routes it to stderr, a UI ignores it.
 */
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
     * @param relayConcurrency write relays synced at once.
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
    ) {
        /** Project the shared engine knobs onto a [NegentropyStoreSync.Config]. */
        internal fun toEngineConfig() =
            NegentropyStoreSync.Config(
                down = down,
                up = up,
                syncDeletions = syncDeletions,
                pageFallback = pageFallback,
                idChunk = idChunk,
                downloadWorkers = downloadWorkers,
                reconcileConcurrency = reconcileConcurrency,
                maxDeletionRounds = maxDeletionRounds,
                concurrency = relayConcurrency,
                idleTimeoutMs = idleTimeoutMs,
                publishTimeoutSecs = publishTimeoutSecs,
            )
    }

    /** Per-write-relay outcome of an [update] (folded from the engine's group results). */
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
        /** null when every chunk of this relay succeeded; the first failure otherwise. */
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

    /**
     * Run the full outbox-model refresh: [writeRelayGroups] then hand every group
     * hosting at least [Config.minAuthors] authors (largest first, chunked to
     * [Config.authorChunk]) to [NegentropyStoreSync], folding its per-group results back
     * up per relay. Best-effort — a relay that fails is recorded in [RelayResult.error]
     * and never aborts the run.
     */
    suspend fun update(): Result {
        val latest = loadLatestRelayLists()
        val groups = groupByWriteRelay(latest)
        val authorsWithOutbox = groups.values.flatMapTo(HashSet()) { it }.size

        // Plan: relays with enough authors, largest first so the heaviest groups start
        // while engine permits are free.
        val plan =
            groups.entries
                .filter { it.value.size >= config.minAuthors }
                .sortedByDescending { it.value.size }
                .map { it.key to it.value }

        // Fan each relay's authors into one filter per authorChunk-sized slice.
        val authorChunk = config.authorChunk.coerceAtLeast(1)
        val filtersByRelay =
            plan.associate { (relay, authors) ->
                relay to authors.toList().chunked(authorChunk).map { Filter(kinds = config.kinds, authors = it) }
            }

        val groupResults = NegentropyStoreSync(client, store, config.toEngineConfig(), log).sync(filtersByRelay)
        val byRelay = groupResults.groupBy { it.relay }

        // Fold each relay's chunk results back into one RelayResult (plan order preserved).
        val perRelay =
            plan.map { (relay, authors) ->
                val chunks = byRelay[relay].orEmpty()
                RelayResult(
                    relay = relay,
                    authors = authors.size,
                    need = chunks.sumOf { it.need },
                    have = chunks.sumOf { it.have },
                    downloaded = chunks.sumOf { it.downloaded },
                    uploaded = chunks.sumOf { it.uploaded },
                    deletionsSentUp = chunks.sumOf { it.deletionsSentUp },
                    deletionsAppliedDown = chunks.sumOf { it.deletionsAppliedDown },
                    pagedFallback = chunks.any { it.pagedFallback },
                    error = chunks.firstNotNullOfOrNull { it.error },
                )
            }

        return Result(
            relayListsInStore = latest.size,
            authorsWithOutbox = authorsWithOutbox,
            relays = perRelay.size,
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
