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
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.store.IEventStore
import com.vitorpamplona.quartz.nip01Core.store.verifyAndInsert
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
import kotlin.coroutines.cancellation.CancellationException

/**
 * Two-pass NIP-77 sync of ANY filter set between a relay and a local [IEventStore],
 * with a paged fallback — the reusable engine behind `amy sync` and the GrapeRank
 * outbox updater, generalized so any caller can reconcile arbitrary
 * `relay -> filters` work against their store.
 *
 * A **group** is one `(relay, filter)`: [syncGroup] runs the full two-pass sync for it.
 *
 *  1. **Content pass** — [negentropyReconcile] diffs the relay's matched set for the
 *     filter against the store's ids, then:
 *      - [Config.down] downloads the residual **needs** (relay has, store lacks) by id
 *        and verifies+inserts them into [store];
 *      - [Config.up] uploads the residual **haves** (store has, relay lacks) as EVENTs.
 *  2. **Deletion pass** — [negentropySettleDeletions] over what the content pass could
 *     not converge ([Config.syncDeletions]). Its **applyDown** direction is the
 *     "download the deletion when our upload was rejected" case: an event pushed up
 *     that the relay keeps rejecting (it deleted it) is a residual *have*, so the
 *     relay's covering kind:5 is pulled down and applied and [store] drops the
 *     retracted event. **sendUp** publishes the store's covering deletions for records
 *     deleted locally that the relay still serves.
 *
 * If the content pass can't reconcile ([NegentropySyncException] — no NIP-77, an
 * over-cap minimal window, a mid-sync disconnect) and [Config.pageFallback] is on, the
 * group pages the same filter ([fetchAllPages]) into the store instead; only the
 * negentropy-only deletion settle is skipped there. Every group is best-effort — a
 * failure lands in [GroupResult.error], it never throws — so one bad relay can't abort
 * a multi-relay [sync].
 *
 * [sync] runs many groups: relays go up to [Config.concurrency] at once, and a single
 * relay's filters run sequentially (so one relay never opens more than one group's worth
 * of negentropy sessions at a time — keeping under its subscription budget). Progress is
 * emitted through [log].
 */
@OptIn(ExperimentalAtomicApi::class)
class NegentropyStoreSync(
    private val client: INostrClient,
    private val store: IEventStore,
    private val config: Config = Config(),
    private val log: (String) -> Unit = {},
) {
    /**
     * @param down download records the relay has that the store lacks.
     * @param up upload records the store has that the relay lacks (also arms the
     *   deletion **applyDown** path — a rejected upload pulls the relay's kind:5 down).
     * @param syncDeletions run the deletion settle over the reconcile residual.
     * @param pageFallback page the filter when negentropy can't reconcile the relay.
     * @param idChunk ids per reconcile chunk and per by-id fetch.
     * @param downloadWorkers concurrent by-id download fetches per group.
     * @param reconcileConcurrency overlapped `created_at`-window reconciles after an over-cap split.
     * @param maxDeletionRounds hard cap on deletion-settle rounds (converges in 1–2).
     * @param concurrency relays synced at once by [sync] (a relay's own filters stay sequential).
     * @param idleTimeoutMs idle watchdog for reconciles / fetches / pages.
     * @param publishTimeoutSecs OK-confirmation wait per uploaded event.
     */
    class Config(
        val down: Boolean = true,
        val up: Boolean = false,
        val syncDeletions: Boolean = true,
        val pageFallback: Boolean = true,
        val idChunk: Int = 500,
        val downloadWorkers: Int = 4,
        val reconcileConcurrency: Int = 2,
        val maxDeletionRounds: Int = 4,
        val concurrency: Int = 4,
        val idleTimeoutMs: Long = 30_000L,
        val publishTimeoutSecs: Long = 15,
    )

    /** Outcome of one `(relay, filter)` group. `error` is null on success. */
    class GroupResult(
        val relay: NormalizedRelayUrl,
        val filter: Filter,
        val need: Int,
        val have: Int,
        val downloaded: Int,
        val uploaded: Int,
        val deletionsSentUp: Int,
        val deletionsAppliedDown: Int,
        /** True when negentropy couldn't reconcile and the filter was paged instead. */
        val pagedFallback: Boolean,
        val error: String?,
    )

    /**
     * Sync every `(relay, filter)` in [filtersByRelay]: relays run up to
     * [Config.concurrency] at once; each relay's filters run sequentially. Returns one
     * [GroupResult] per relay+filter (relay order preserved, filters in list order).
     */
    suspend fun sync(filtersByRelay: Map<NormalizedRelayUrl, List<Filter>>): List<GroupResult> {
        if (filtersByRelay.isEmpty()) return emptyList()
        val gate = Semaphore(config.concurrency.coerceAtLeast(1))
        return coroutineScope {
            filtersByRelay.entries
                .map { (relay, filters) ->
                    async { gate.withPermit { filters.map { syncGroupSafely(relay, it) } } }
                }.awaitAll()
                .flatten()
        }
    }

    /**
     * [syncGroup] with a best-effort guard so an unexpected failure in one group
     * (store I/O, a relay throwing outside the NIP-77 path, …) is recorded rather than
     * cancelling every other relay in a [sync]. Cancellation is propagated, not caught.
     */
    private suspend fun syncGroupSafely(
        relay: NormalizedRelayUrl,
        filter: Filter,
    ): GroupResult =
        try {
            syncGroup(relay, filter)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("[store-sync] ${relay.url}: group failed: ${e::class.simpleName}: ${e.message}")
            GroupResult(relay, filter, 0, 0, 0, 0, 0, 0, pagedFallback = false, error = "${e::class.simpleName}: ${e.message}")
        }

    /** Content pass + deletion settle (+ page fallback) for one relay + one filter. */
    suspend fun syncGroup(
        relay: NormalizedRelayUrl,
        filter: Filter,
    ): GroupResult {
        // Only the id+created_at snapshot is needed to reconcile — never the decoded
        // events (~40 B/entry vs ~1 KB), which matters when a relay hosts a large
        // matched set. The events the reconcile decides to UP-publish (the small
        // residual haves) are fetched by id on demand in the uploader below.
        val localEntries = store.snapshotIdsForNegentropy(listOf(filter))

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
                                // Fetch just the residual haves from the store (not the
                                // whole matched set) and publish them up.
                                for (ev in store.query<Event>(Filter(ids = batch))) {
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
                // Negentropy couldn't reconcile — page the same filter so the records
                // still refresh. Deletion settle is negentropy-only, so it is skipped.
                var pageError: String? = e.message ?: "negentropy sync failed"
                if (config.pageFallback && config.down) {
                    pageError =
                        try {
                            downloaded.addAndFetch(pageDownload(relay, filter))
                            null
                        } catch (pe: CancellationException) {
                            throw pe
                        } catch (pe: Exception) {
                            "negentropy: ${e.message}; page fallback: ${pe::class.simpleName}: ${pe.message}"
                        }
                }
                log("[store-sync] ${relay.url}: paged fallback, ${downloaded.load()} stored${pageError?.let { " (error: $it)" } ?: ""}")
                return GroupResult(relay, filter, 0, 0, downloaded.load(), uploaded.load(), 0, 0, pagedFallback = true, error = pageError)
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

        log(
            "[store-sync] ${relay.url}: down ${downloaded.load()}, up ${uploaded.load()}, " +
                "del↑ ${deletions?.sentUp ?: 0}, del↓ ${deletions?.appliedDown ?: 0}",
        )
        return GroupResult(
            relay = relay,
            filter = filter,
            need = reconcileResult.needCount,
            have = reconcileResult.haveCount,
            downloaded = downloaded.load(),
            uploaded = uploaded.load(),
            deletionsSentUp = deletions?.sentUp ?: 0,
            deletionsAppliedDown = deletions?.appliedDown ?: 0,
            pagedFallback = false,
            error = null,
        )
    }

    /**
     * Paged fallback: walk [relay] past its per-REQ cap for [filter], verifying and
     * inserting each event into [store]. [fetchAllPages]'s `onEvent` can't suspend, so
     * events funnel through a channel to a single inserter. Returns how many were newly stored.
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
}
