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
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.negentropyReconcile
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.store.IdAndTime
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
        val filter = RawEventSupport.buildFilter(args)

        Context.openOrAnonymous(dataDir).use { ctx ->
            ctx.prepare()
            val localEvents = ctx.store.query<Event>(filter)
            val localById = localEvents.associateBy { it.id }
            val localEntries = localEvents.map { IdAndTime(it.createdAt, it.id) }

            val downloaded = AtomicInteger(0)
            val uploaded = AtomicInteger(0)

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
                                        if (ack.values.any { it }) uploaded.incrementAndGet()
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

            Output.emit(
                mapOf(
                    "relay" to relay.url,
                    "local_events" to localEvents.size,
                    "windows" to result.windows,
                    "need" to result.needCount,
                    "have" to result.haveCount,
                    "downloaded" to downloaded.get(),
                    "uploaded" to uploaded.get(),
                ),
            )
            return 0
        }
    }
}
