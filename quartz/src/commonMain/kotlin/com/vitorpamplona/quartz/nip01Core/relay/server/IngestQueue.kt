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
package com.vitorpamplona.quartz.nip01Core.relay.server

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.store.IEventStore
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/**
 * Group-commit writer for incoming EVENT publishes.
 *
 * Submissions from any number of [RelaySession]s land in [incoming],
 * a single drain coroutine pulls one item to start a batch then
 * greedily drains everything else already queued (up to [maxBatch]),
 * and forwards the whole batch through [IEventStore.batchInsert] —
 * one writer-mutex acquisition + one BEGIN / COMMIT for the lot.
 *
 * Why this lives here: the SQLite event store enforces a single-writer
 * mutex (matching SQLite's own file-level rule), so per-event
 * `useWriter` calls serialise no matter how many publishers we have.
 * Group commit collapses N mutex round-trips into one and lets
 * BEGIN / WAL-append / COMMIT amortise across the batch.
 *
 * OK semantics (NIP-01):
 *  - The OK frame carries the event id, so clients pair replies by
 *    id, not by arrival order. We dispatch each [Submission.onComplete]
 *    as its row resolves; reordering across connections (and on the
 *    same connection) is allowed.
 *  - `OK true` means "accepted by this relay," not "fsynced." Since
 *    the underlying SQLite pool runs `synchronous = OFF` and WAL,
 *    a successful row inside the open transaction is the strongest
 *    guarantee we provide; replying after the batch's COMMIT (which
 *    is what happens in the current implementation) just adds the
 *    in-memory commit cost and keeps the write-failure path simple.
 *
 * Per-row error isolation lives in the store layer (SAVEPOINT in
 * [com.vitorpamplona.quartz.nip01Core.store.sqlite.SQLiteEventStore]).
 * A duplicate, expired event, etc. is a Rejected outcome for that
 * row only.
 *
 * Backpressure: [incoming] is bounded at [capacity]. A flood of
 * EVENTs that outpaces the writer suspends [submit] callers (i.e.
 * the per-connection ingest path) until the writer drains. This
 * propagates back through the WebSocket pump so a slow disk
 * eventually slows the publisher rather than ballooning JVM memory.
 */
class IngestQueue(
    private val store: IEventStore,
    parentContext: CoroutineContext,
    private val maxBatch: Int = DEFAULT_MAX_BATCH,
    capacity: Int = DEFAULT_CAPACITY,
) : AutoCloseable {
    /**
     * One outstanding ingest request: the event to insert plus the
     * callback the writer fires once the row's outcome is known.
     */
    class Submission(
        val event: Event,
        val onComplete: (IEventStore.InsertOutcome) -> Unit,
    )

    private val incoming = Channel<Submission>(capacity)
    private val scope = CoroutineScope(parentContext + SupervisorJob())

    /**
     * Lazily-launched drain coroutine. We don't start it in `init`
     * because eagerly launching from a server's lazy
     * `LiveEventStore` allocates a Default-dispatcher slot at server
     * construction time — visible to other tests sharing the same
     * `Dispatchers.Default` pool, where it can perturb scheduling
     * for unrelated REQ/EOSE timing. Starting on first `submit`
     * keeps relays that never see an EVENT (read-only sessions,
     * negentropy-only) from paying for the writer at all.
     */
    @Volatile
    private var writerStarted = false
    private val startLock = Any()

    /**
     * Hand off [event] for insertion. [onComplete] is invoked once
     * with the per-row outcome from the writer batch — exactly once,
     * unless the queue closes mid-flight.
     *
     * Suspends only when [incoming] is full (writer fell behind by
     * [DEFAULT_CAPACITY] events). Otherwise this returns as fast as a
     * channel `send`, freeing the WebSocket pump to read the next
     * frame — that's where the per-connection pipeline win comes
     * from.
     */
    suspend fun submit(
        event: Event,
        onComplete: (IEventStore.InsertOutcome) -> Unit,
    ) {
        ensureWriterStarted()
        incoming.send(Submission(event, onComplete))
    }

    private fun ensureWriterStarted() {
        if (writerStarted) return
        synchronized(startLock) {
            if (writerStarted) return
            scope.launch { drainLoop() }
            writerStarted = true
        }
    }

    private suspend fun drainLoop() {
        val batch = ArrayList<Submission>(maxBatch)
        val events = ArrayList<Event>(maxBatch)
        try {
            while (true) {
                // Block for the first item — anything else would be a
                // hot loop. Once we have one, drain greedily without
                // blocking so back-to-back publishes coalesce into a
                // single transaction.
                batch.add(incoming.receive())
                while (batch.size < maxBatch) {
                    val next = incoming.tryReceive().getOrNull() ?: break
                    batch.add(next)
                }
                events.clear()
                for (sub in batch) events.add(sub.event)

                val outcomes =
                    try {
                        store.batchInsert(events)
                    } catch (e: Throwable) {
                        Log.w("IngestQueue") { "batchInsert failed for ${batch.size} events: ${e.message}" }
                        val reason = e.message ?: e::class.simpleName ?: "insert failed"
                        List(batch.size) { IEventStore.InsertOutcome.Rejected(reason) }
                    }

                for (i in batch.indices) {
                    val sub = batch[i]
                    val outcome =
                        outcomes.getOrNull(i)
                            ?: IEventStore.InsertOutcome.Rejected("internal error: missing outcome")
                    try {
                        sub.onComplete(outcome)
                    } catch (e: Throwable) {
                        // A misbehaving callback must not poison the
                        // writer loop; the outcome is delivered
                        // best-effort.
                        Log.w("IngestQueue") { "onComplete threw: ${e.message}" }
                    }
                }
                batch.clear()
            }
        } catch (_: kotlinx.coroutines.channels.ClosedReceiveChannelException) {
            // Normal shutdown via close().
        }
    }

    /**
     * Stop accepting new submissions and cancel the writer.
     * In-flight submissions whose batch hadn't started yet may never
     * receive their callback — the WebSocket on the other side is
     * also being torn down in that case, so the OK reply has nowhere
     * to go anyway.
     */
    override fun close() {
        incoming.close()
        scope.cancel()
    }

    companion object {
        /**
         * Cap per batch. Sized to keep per-batch latency low (each
         * transaction holds the SQLite writer mutex; over-large
         * batches starve other writers and hurt p99 publish latency).
         * 64 events at ~0.2 ms per insert ≈ 13 ms held — well under
         * a perceptible UI tick.
         */
        const val DEFAULT_MAX_BATCH: Int = 64

        /**
         * In-flight cap before [submit] suspends. With ~5–10× group
         * commit speed-up over the single-event path, one batch
         * cycle is on the order of milliseconds, so a 1024-deep
         * queue tolerates short bursts (a publisher dumping a
         * thousand notes) without blocking the WS pump.
         */
        const val DEFAULT_CAPACITY: Int = 1024
    }
}
