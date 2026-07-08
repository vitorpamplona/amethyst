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
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.NegentropySyncException
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAll
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.negentropyReconcile
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.negentropyReconcileIds
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.store.IdAndTime
import com.vitorpamplona.quartz.nip01Core.store.deletionsCovering
import com.vitorpamplona.quartz.nip09Deletions.DeletionEvent
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
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
 * Deletion propagation (on by default; disable with `--no-sync-deletions`) is a
 * **second pass over the residual**, not per-event work in the content pass — so it
 * costs the same whether the database is tiny or huge. After the content settle, a
 * re-reconcile's leftover diff is (barring races) exactly the events a deletion kept
 * from converging:
 *
 *   - a residual **need** (relay has it, we still lack it after `--down` tried to
 *     download) = we deleted it → publish OUR covering deletion up so the relay drops it;
 *   - a residual **have** (we have it, relay still lacks it after `--up` tried to upload)
 *     = the relay deleted it → pull the relay's covering kind-5 down and apply it locally.
 *
 * Coverage is any way a deletion reaches an event ([deletionsCovering]): a NIP-09 kind-5
 * by id (`e`) or address (`a`, cutoff-checked), or a NIP-62 vanish targeting this relay
 * (up direction only — a pulled vanish is not auto-applied, its blast radius being the
 * whole account). The residual is small (only real deletion mismatches), so only it is
 * fetched — never the whole need set. The loop repeats until a round resolves nothing.
 * So `amy sync` (default `--down`) makes the relay honor your deletions; `--up` makes
 * your store honor the relay's; `--up --down` converges both ways.
 *
 * Content is pipelined with the reconcile: need-id batches feed [DOWNLOAD_WORKERS]
 * concurrent by-id REQ drains and have-ids feed a single uploader. Thin assembly only:
 * the windowing, streaming, and back-pressure live in quartz (`negentropyReconcile`);
 * this file only routes ids to `Context.drain` / `Context.publish`.
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

    /**
     * Cap on deletion-settle rounds. Each round resolves the residual it can and
     * re-reconciles; a healthy sync converges in 1–2 (round N sends/applies, round
     * N+1 confirms empty). The cap only bounds pathological non-convergence (e.g. a
     * relay that refuses a deletion), which the "resolved nothing → stop" check
     * normally catches first.
     */
    private const val MAX_DELETION_ROUNDS = 4

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
            val localEvents = ctx.store.query<Event>(filter)
            val localById = localEvents.associateBy { it.id }
            val localEntries = localEvents.map { IdAndTime(it.createdAt, it.id) }

            val downloaded = AtomicInteger(0)
            val uploaded = AtomicInteger(0)

            // ── Pass 1: content settle — download needs, upload haves. No deletion
            // logic, so a plain sync costs exactly what it always did.
            val result =
                try {
                    coroutineScope {
                        // needIds = relay has, we lack; haveIds = we have, relay lacks.
                        val needBatches = Channel<List<HexKey>>(DOWNLOAD_WORKERS * 2)
                        val haveBatches = Channel<List<HexKey>>(Channel.UNLIMITED)

                        val downloaders =
                            List(DOWNLOAD_WORKERS) {
                                launch {
                                    for (batch in needBatches) {
                                        // drain verifies + stores; anything we deleted is
                                        // rejected by our own tombstone and stays a "need".
                                        downloaded.addAndGet(ctx.drain(mapOf(relay to listOf(Filter(ids = batch))), timeoutMs).size)
                                    }
                                }
                            }
                        val uploader =
                            launch {
                                for (batch in haveBatches) {
                                    for (id in batch) {
                                        val ev = localById[id] ?: continue
                                        if (ctx.publish(ev, setOf(relay)).values.any { it }) uploaded.incrementAndGet()
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

            // ── Pass 2+: deletion settle. After the content pass, a re-reconcile's
            // residual is (barring races) exactly the events a deletion kept from moving:
            //  - a residual NEED (relay has it, we still lack it after trying to download)
            //    = we deleted it → publish OUR covering deletion up so the relay drops it;
            //  - a residual HAVE (we have it, relay still lacks it after trying to upload)
            //    = the relay deleted it → pull the relay's covering kind-5 down and apply.
            // The residual is tiny (only real deletion mismatches), so this is cheap no
            // matter how large the database is — we only fetch metadata for the residual,
            // never the whole need set. Loop until a round resolves nothing (converged) or
            // we hit the round cap. Best-effort: a failed reconcile here never fails the
            // command — the content sync already succeeded.
            var deletionsUp = 0
            var deletionsDown = 0
            var deletionRounds = 0
            if (syncDeletions && (down || up)) {
                val sentUp = HashSet<HexKey>()
                val appliedDown = HashSet<HexKey>()
                try {
                    while (deletionRounds < MAX_DELETION_ROUNDS) {
                        deletionRounds++
                        val diff =
                            ctx.client.negentropyReconcileIds(
                                relay = relay,
                                filter = filter,
                                localEntries = ctx.store.snapshotIdsForNegentropy(listOf(filter)),
                                batchSize = ID_CHUNK,
                                idleTimeoutMs = timeoutMs,
                                reconcileConcurrency = RECONCILE_CONCURRENCY,
                            )
                        var resolved = 0

                        // residual needs → send our deletions up (bounded: --down settled
                        // every need we don't have a deletion for).
                        if (down) {
                            for (chunk in diff.needIds.chunked(ID_CHUNK)) {
                                val events = ctx.client.fetchAll(relay, Filter(ids = chunk), timeoutMs)
                                for (del in ctx.store.deletionsCovering(events, relay)) {
                                    if (sentUp.add(del.id) && ctx.publish(del, setOf(relay)).values.any { it }) {
                                        deletionsUp++
                                        resolved++
                                    }
                                }
                            }
                        }

                        // residual haves → apply the relay's deletions locally (bounded:
                        // --up settled every have the relay didn't delete). Only precise
                        // kind-5 deletions are pulled down; a kind-62 vanish is NOT
                        // auto-applied (its blast radius is the whole account).
                        if (up) {
                            for (chunk in diff.haveIds.chunked(ID_CHUNK)) {
                                val ourEvents = ctx.store.query<Event>(Filter(ids = chunk))
                                val relayDeletions = deletionsCovering(ourEvents, relay) { f -> ctx.client.fetchAll(relay, f, timeoutMs) }
                                for (del in relayDeletions.filterIsInstance<DeletionEvent>()) {
                                    if (appliedDown.add(del.id) && ctx.verifyAndStore(del)) {
                                        deletionsDown++
                                        resolved++
                                    }
                                }
                            }
                        }

                        if (resolved == 0) break
                    }
                } catch (e: NegentropySyncException) {
                    // content already synced; deletion convergence is best-effort.
                }
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
                    "deletions_sent_up" to deletionsUp,
                    "deletions_applied_down" to deletionsDown,
                    "deletion_rounds" to deletionRounds,
                ),
            )
            return 0
        }
    }
}
