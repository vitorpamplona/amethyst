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
package com.vitorpamplona.amethyst.cli.commands

import com.vitorpamplona.amethyst.cli.Args
import com.vitorpamplona.amethyst.cli.Context
import com.vitorpamplona.amethyst.cli.DataDir
import com.vitorpamplona.amethyst.cli.Output
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.DELETION_PROPAGATION_KINDS
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.NegentropySyncException
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.deletionSideChannelFilter
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.excludesDeletionKinds
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.negentropyPropagateDeletions
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.negentropyReconcile
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.store.IdAndTime
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * `amy sync --relay URL [filter flags] [--down] [--up] [--timeout SECS]`
 *
 * NIP-77 Negentropy set-reconciliation between the local event store and a
 * relay (nak's `sync`, adapted to amy's local-store model). The protocol
 * itself is quartz's [negentropyReconcile]: it pins the relay with a
 * keep-alive subscription, splits the filter by `created_at` window whenever
 * the relay caps the set (strfry `max_sync_events`), and streams the two
 * directions of the diff as each round completes. This command closes the
 * loop on that stream:
 *
 *   --down  (default)  download events the relay has and we lack (REQ by id)
 *   --up               upload events we have and the relay lacks (EVENT)
 *
 * Pass both for a full bidirectional sync. The filter flags are the same as
 * `fetch`/`subscribe`; an empty filter reconciles the whole store.
 *
 * Deletions ride a side-channel, and run in three phases so both sides fully
 * reflect each other. NIP-77 reconciles by id over the content filter, so a
 * scoped sync (`--kind 1`) would never carry the kind-5/62 that deletes one of
 * those notes — the deletion would be stuck on whichever side issued it.
 *
 *   1. Whenever the filter pins `kinds` and excludes 5/62, reconcile
 *      `{kinds:[5,62], authors:<same>}` **both directions regardless of
 *      --up/--down** ([negentropyPropagateDeletions]) — FIRST, before content,
 *      so every deletion is applied on both sides before the content diff is
 *      taken. Local deletions are pushed up (a kind-62 vanish only to a relay it
 *      targets); the relay's deletions are pulled down and applied locally.
 *   2. Content reconcile, over a local snapshot taken AFTER phase 1 so it never
 *      re-offers (and cannot resurrect on the relay) an event just deleted.
 *   3. Backstop: if the relay rejected a content push — usually because it holds
 *      a deletion we lack — pull that author's kind-5/62 and apply it locally so
 *      we stop re-offering the dead event.
 *
 * Pass `--no-sync-deletions` to opt out of all three.
 *
 * Both directions are pipelined with the reconcile: need-id batches feed
 * [DOWNLOAD_WORKERS] concurrent by-id REQ drains and have-ids feed a single
 * uploader, so downloads and uploads overlap the remaining reconcile rounds
 * instead of waiting for the full diff. Every downloaded event funnels
 * through `Context.drain`'s verify-and-store path, unchanged.
 *
 * Thin assembly only: the windowing, streaming, and back-pressure live in
 * quartz (`negentropyReconcile`); this file only routes ids to
 * `Context.drain` / `Context.publish`.
 */
object SyncCommand {
    private const val ID_CHUNK = 500

    /**
     * Concurrent by-id download REQs. With [RECONCILE_CONCURRENCY] NEG
     * sessions and the keep-alive, peak concurrent subscriptions on the
     * relay are DOWNLOAD_WORKERS + RECONCILE_CONCURRENCY + 1 = 7 — well
     * under the common NIP-11 `max_subscriptions` floor of 20.
     */
    private const val DOWNLOAD_WORKERS = 4

    /** Overlapped `created_at`-window reconciles after an over-cap split. */
    private const val RECONCILE_CONCURRENCY = 2

    suspend fun run(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val relayUrl =
            args.flag("relay")
                ?: return Output.error("bad_args", "sync requires --relay URL")
        val relay =
            RelayUrlNormalizer.normalizeOrNull(relayUrl)
                ?: return Output.error("bad_args", "invalid relay url: $relayUrl")
        val timeoutMs = (args.flag("timeout")?.toLongOrNull() ?: 30L) * 1000
        // Default direction is download; --up adds upload.
        val up = args.bool("up")
        val down = args.bool("down") || !up
        val syncDeletions = !args.bool("no-sync-deletions")
        val filter = RawEventSupport.buildFilter(args)

        Context.open(dataDir).use { ctx ->
            ctx.prepare()

            val deletionsDown = AtomicInteger(0)
            val deletionsUp = AtomicInteger(0)

            // ── Phase 1: deletions first, both directions ────────────────────
            // Propagate kind 5/62 before content so both stores have applied every
            // deletion by the time content is diffed. Ordering is load-bearing: a
            // deletion pulled down here removes a local event, so the content
            // snapshot MUST be taken AFTER this phase — a snapshot taken before
            // would still list the just-deleted event and re-offer it up, which the
            // relay would either reject or (if it also lacks the deletion) resurrect.
            // No-op (returns null) when the content filter already covers 5/62.
            val deletionResult =
                if (syncDeletions && filter.excludesDeletionKinds()) {
                    try {
                        val localDeletions = ctx.store.query<Event>(filter.deletionSideChannelFilter())
                        ctx.client.negentropyPropagateDeletions(
                            relay = relay,
                            contentFilter = filter,
                            localDeletions = localDeletions,
                            idleTimeoutMs = timeoutMs,
                            download = { batch ->
                                deletionsDown.addAndGet(ctx.drain(mapOf(relay to listOf(Filter(ids = batch))), timeoutMs).size)
                            },
                            upload = { event ->
                                if (ctx.publish(event, setOf(relay)).values.any { it }) deletionsUp.incrementAndGet()
                            },
                        )
                    } catch (e: NegentropySyncException) {
                        return Output.error("sync_error", e.message ?: "deletion sync failed")
                    }
                } else {
                    null
                }

            // ── Phase 2: content ─────────────────────────────────────────────
            // Snapshot the local set AFTER the deletion phase so it reflects any
            // deletion just applied — never re-offering an event we just deleted.
            val localEvents = ctx.store.query<Event>(filter)
            val localById = localEvents.associateBy { it.id }
            val localEntries = localEvents.map { IdAndTime(it.createdAt, it.id) }

            val downloaded = AtomicInteger(0)
            val uploaded = AtomicInteger(0)
            // Authors whose content push the relay rejected — a rejection usually
            // means the relay holds a deletion we lack (Phase 3 reconciles them).
            val blockedAuthors = ConcurrentHashMap.newKeySet<HexKey>()

            val result =
                try {
                    coroutineScope {
                        // needIds = relay has, we lack; haveIds = we have, relay lacks.
                        // Bounded so a slow download back-pressures the reconcile
                        // rounds instead of piling ids up in memory.
                        val needBatches = Channel<List<HexKey>>(DOWNLOAD_WORKERS * 2)
                        // Unbounded is fine here: have-ids reference events we already
                        // hold locally, so memory is bounded by the local set.
                        val haveBatches = Channel<List<HexKey>>(Channel.UNLIMITED)

                        val downloaders =
                            List(DOWNLOAD_WORKERS) {
                                launch {
                                    for (batch in needBatches) {
                                        val got = ctx.drain(mapOf(relay to listOf(Filter(ids = batch))), timeoutMs)
                                        downloaded.addAndGet(got.size)
                                    }
                                }
                            }
                        val uploader =
                            launch {
                                for (batch in haveBatches) {
                                    for (id in batch) {
                                        val ev = localById[id] ?: continue
                                        val ack = ctx.publish(ev, setOf(relay))
                                        if (ack.values.any { it }) {
                                            uploaded.incrementAndGet()
                                        } else if (syncDeletions) {
                                            // Relay refused it — most often because it holds a
                                            // deletion for this id that we lack. Remember the
                                            // author so Phase 3 can pull that deletion down.
                                            blockedAuthors.add(ev.pubKey)
                                        }
                                    }
                                }
                            }

                        val reconcile =
                            try {
                                ctx.client.negentropyReconcile(
                                    relay = relay,
                                    filter = filter,
                                    localEntries = localEntries,
                                    batchSize = ID_CHUNK,
                                    idleTimeoutMs = timeoutMs,
                                    reconcileConcurrency = RECONCILE_CONCURRENCY,
                                    onHaveIds = if (up) { batch -> haveBatches.send(batch) } else null,
                                    onNeedIds = { batch -> if (down) needBatches.send(batch) },
                                )
                            } finally {
                                needBatches.close()
                                haveBatches.close()
                            }

                        downloaders.joinAll()
                        uploader.join()
                        reconcile
                    }
                } catch (e: NegentropySyncException) {
                    return Output.error("sync_error", e.message ?: "negentropy sync failed")
                }

            // ── Phase 3: reject-reaction backstop ────────────────────────────
            // A content push the relay blocked usually means the relay deleted that
            // id and holds the deletion we lack. Pull that author's deletions and
            // ingest them locally so we stop re-offering the dead event. Cheap —
            // only fires on an actual rejection, and verify-by-fetch: only a real
            // kind-5/62 that the store accepts has any effect. Rarely triggers once
            // Phase 1 ran, but it is the net when deletions are otherwise skipped.
            if (blockedAuthors.isNotEmpty()) {
                ctx.drain(
                    mapOf(relay to listOf(Filter(kinds = DELETION_PROPAGATION_KINDS, authors = blockedAuthors.toList()))),
                    timeoutMs,
                )
            }

            Output.emit(
                mapOf(
                    "relay" to relay.url,
                    "local_events" to localEvents.size,
                    "windows" to result.windows,
                    "need" to result.needCount,
                    "have" to result.haveCount,
                    "downloaded" to downloaded.get(),
                    "uploaded" to uploaded.get(),
                    "deletions_need" to (deletionResult?.needCount ?: 0),
                    "deletions_have" to (deletionResult?.haveCount ?: 0),
                    "deletions_downloaded" to deletionsDown.get(),
                    "deletions_uploaded" to deletionsUp.get(),
                ),
            )
            return 0
        }
    }
}
