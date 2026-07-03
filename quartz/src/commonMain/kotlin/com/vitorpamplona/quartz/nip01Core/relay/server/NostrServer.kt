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

import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.relay.server.backend.IngestQueue
import com.vitorpamplona.quartz.nip01Core.relay.server.backend.LiveEventStore
import com.vitorpamplona.quartz.nip01Core.relay.server.backend.SessionBackend
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.IRelayPolicy
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.RelayLimits
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.VerifyPolicy
import com.vitorpamplona.quartz.nip01Core.store.IEventStore
import com.vitorpamplona.quartz.nip77Negentropy.NegentropySettings
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/**
 * Storage-backed Nostr relay server: a [RelayServerBase] whose [backend] is a
 * [LiveEventStore] over [store] (historical replay + live tail after EOSE),
 * fed through a group-commit [IngestQueue].
 *
 * @param store The [IEventStore] backing this relay.
 * @param policyBuilder Controls requirements for relay commands.
 * @param parallelVerify When `true`, Schnorr verification runs in
 *   parallel inside the [IngestQueue] (one async per event, dispatched
 *   on `Dispatchers.Default`) rather than serially on the WS pump
 *   coroutine inside [VerifyPolicy]. Callers that flip this on should
 *   *omit* `VerifyPolicy` from their [policyBuilder] chain to avoid
 *   double-verifying.
 * @param negentropySettings NIP-77 server-side tuning (frame cap,
 *   snapshot cap, per-connection session cap). Defaults to strfry-
 *   parity values; see [NegentropySettings].
 * @param listener Observability hook fired as connections open and close,
 *   keyed by [RelaySession.id]. Defaults to a no-op.
 * @param limits Operational limits enforced on every connection (per-command
 *   via a composed [com.vitorpamplona.quartz.nip01Core.relay.server.policies.LimitsPolicy],
 *   plus the session-level message-size and subscription caps) and advertised
 *   via [RelayLimits.toNip11Limitation]. Null disables limit enforcement.
 */
class NostrServer(
    private val store: IEventStore,
    policyBuilder: () -> IRelayPolicy = { VerifyPolicy },
    parentContext: CoroutineContext = SupervisorJob(),
    parallelVerify: Boolean = false,
    negentropySettings: NegentropySettings = NegentropySettings.Default,
    listener: RelayServerListener = RelayServerListener.None,
    limits: RelayLimits? = null,
) : RelayServerBase(policyBuilder, parentContext, negentropySettings, listener, limits) {
    /**
     * Wakes the deferred-FTS catch-up worker. Conflated: N batch commits
     * while the worker is mid-drain collapse into one more pass.
     */
    private val ftsCatchUpPokes = Channel<Unit>(Channel.CONFLATED)

    /**
     * Group-commit writer shared across every connected session.
     * Sessions hand off EVENT publishes here instead of awaiting
     * [IEventStore.insert] inline; the queue coalesces back-to-back
     * publishes into a single SQLite transaction. See [IngestQueue]
     * for the OK ordering and durability semantics.
     */
    private val ingest =
        IngestQueue(
            store = store,
            parentContext = parentContext,
            verify = if (parallelVerify) ({ it.verify() }) else null,
            onBatchCommitted =
                if (store.needsFtsCatchUp) {
                    { ftsCatchUpPokes.trySend(Unit) }
                } else {
                    null
                },
        )

    override val backend: SessionBackend = LiveEventStore(store, ingest)

    init {
        // Deferred-FTS catch-up worker: tokenizes in the gaps between
        // publish batches. Each catch-up batch is its own write
        // transaction, so a publish burst arriving mid-drain interleaves
        // at batch granularity instead of stalling. Search REQs don't
        // depend on this worker's pace — LiveEventStore drains the
        // backlog synchronously before serving any search filter.
        if (store.needsFtsCatchUp) {
            // Drain any backlog left over from a previous run before the
            // first publish arrives.
            ftsCatchUpPokes.trySend(Unit)
            scope.launch {
                for (poke in ftsCatchUpPokes) {
                    // Yield to publish traffic: while the ingest queue has
                    // backlog, don't compete for the writer connection —
                    // the burst's final batch commit pokes again, and the
                    // pre-search drain covers correctness regardless.
                    while (!ingest.hasBacklog()) {
                        val done =
                            try {
                                store.ftsCatchUp()
                            } catch (e: Throwable) {
                                if (e is CancellationException) throw e
                                // A shutdown can close the store between this
                                // worker's cancellation and its last batch (the
                                // mutex fast path skips the cancellation check),
                                // and an uncaught throw here poisons unrelated
                                // runTest tests via the global handler. Nothing
                                // depends on this pass — the pre-search drain
                                // covers correctness — so log and stop.
                                Log.w("NostrServer") { "FTS catch-up batch failed: ${e.message}" }
                                break
                            }
                        if (done) break
                    }
                }
            }
        }
    }

    /**
     * Shuts down the server, cancelling all subscriptions and closing the store.
     */
    override fun close() {
        closeConnections()
        ingest.close()
        scope.cancel()
        store.close()
    }

    /**
     * Shuts down the server, cancelling all subscriptions and closing the store.
     */
    @Deprecated("Use close() instead", replaceWith = ReplaceWith("close()"))
    fun shutdown() = close()
}
