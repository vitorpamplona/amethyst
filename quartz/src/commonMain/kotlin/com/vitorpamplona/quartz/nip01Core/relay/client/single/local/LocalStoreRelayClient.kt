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
package com.vitorpamplona.quartz.nip01Core.relay.client.single.local

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.CountMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.CountResult
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EoseMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EventMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.OkMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CloseCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CountCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.EventCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.store.IEventStore
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.cancellation.CancellationException

/**
 * An [IRelayClient] that answers from a local [IEventStore] instead of a
 * websocket. Drop it into a [com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayPool]
 * (via a custom [com.vitorpamplona.quartz.nip01Core.relay.client.single.RelayBuilder])
 * and the on-device database becomes just another relay in the one
 * [com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient]: callers
 * `subscribe`/`count`/`publish` to [url] and the commands are served straight
 * from SQLite — no socket, no JSON round-trip, no relay-server engine.
 *
 * Command handling:
 *  - `REQ`  → stream every stored match as an `EVENT`, then `EOSE`.
 *  - `COUNT`→ `store.count(filters)` as a `COUNT` reply.
 *  - `EVENT`→ insert into the store, reply `OK true/false`.
 *  - `CLOSE`→ cancel the in-flight query for that subscription, if any.
 *  - `AUTH` and anything else → ignored (a local store needs no auth).
 *
 * **Snapshot semantics (no live tail).** A `REQ` replays what is in the store
 * *now* and closes with `EOSE`; events written *after* the scan are not pushed
 * to an already-open subscription — a re-`REQ` picks them up. That is the right
 * shape for a read cache (instant EOSE-backed load), and it avoids reimplementing
 * the server-side replay+index dedup of
 * [com.vitorpamplona.quartz.nip01Core.relay.server.backend.LiveEventStore].
 * Live tailing would layer on
 * [com.vitorpamplona.quartz.nip01Core.store.ObservableEventStore.changes].
 *
 * The relay is always "connected": [connect] reports up immediately and the
 * pool's connect → `onConnected` → `syncFilters` path delivers the active
 * filters once, exactly as it would for a freshly-dialled socket relay.
 */
@OptIn(ExperimentalAtomicApi::class)
class LocalStoreRelayClient(
    override val url: NormalizedRelayUrl,
    private val store: IEventStore,
    private val listener: RelayConnectionListener,
    private val scope: CoroutineScope,
) : IRelayClient {
    @Volatile
    private var connected = false

    /** Live query jobs per subscription id, so CLOSE / disconnect can cancel a long scan. */
    private val jobs = AtomicReference<Map<String, Job>>(emptyMap())

    override fun isConnected(): Boolean = connected

    // In-process: there is no socket to go stale, so a reconnect is never needed.
    override fun needsToReconnect(): Boolean = false

    override fun connect() {
        if (connected) return
        listener.onConnecting(this)
        connected = true
        // Mirrors a socket's onOpen: the pool marks us connected and replays
        // the active REQ/COUNT/EVENT state to us via syncFilters.
        listener.onConnected(this, pingMillis = 0, compressed = false)
    }

    override fun connectAndSyncFiltersIfDisconnected(ignoreRetryDelays: Boolean) {
        if (!connected) connect()
    }

    override fun sendOrConnectAndSync(cmd: Command) {
        if (connected) {
            handle(cmd)
        } else {
            // Don't serve yet: connecting triggers syncFilters, which re-sends
            // the active filters once. Serving here too would double-deliver.
            connect()
        }
    }

    override fun sendIfConnected(cmd: Command) {
        if (connected) handle(cmd)
    }

    override fun disconnect() {
        if (!connected) return
        connected = false
        jobs.exchange(emptyMap()).values.forEach(Job::cancel)
        listener.onDisconnected(this)
    }

    private fun handle(cmd: Command) {
        when (cmd) {
            is ReqCmd -> serveReq(cmd)
            is CountCmd -> serveCount(cmd)
            is EventCmd -> serveEvent(cmd)
            is CloseCmd -> cancelJob(cmd.subId)
            else -> Unit // AUTH etc. — nothing to do for a local store.
        }
        // Tell the pool the command was accepted so it advances its per-relay
        // bookkeeping (won't resend this filter). No bytes left the device.
        listener.onSent(this, cmd.label(), cmd, success = true)
    }

    private fun serveReq(cmd: ReqCmd) {
        val job =
            scope.launch {
                try {
                    store.query<Event>(cmd.filters) { event ->
                        emit(EventMessage(cmd.subId, event))
                    }
                    emit(EoseMessage(cmd.subId))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("LocalStoreRelayClient") { "REQ ${cmd.subId} failed: ${e.message}" }
                }
            }
        putJob(cmd.subId, job)
    }

    private fun serveCount(cmd: CountCmd) {
        scope.launch {
            try {
                emit(CountMessage(cmd.queryId, CountResult(store.count(cmd.filters))))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("LocalStoreRelayClient") { "COUNT ${cmd.queryId} failed: ${e.message}" }
            }
        }
    }

    private fun serveEvent(cmd: EventCmd) {
        scope.launch {
            val ok =
                try {
                    when (val outcome = store.batchInsert(listOf(cmd.event)).first()) {
                        is IEventStore.InsertOutcome.Accepted -> OkMessage(cmd.event.id, true, "")
                        is IEventStore.InsertOutcome.Rejected -> OkMessage(cmd.event.id, false, outcome.reason)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    OkMessage(cmd.event.id, false, "error: ${e.message ?: e::class.simpleName}")
                }
            emit(ok)
        }
    }

    private fun emit(msg: Message) {
        // In-process: nothing is serialised. msgStr is only read by stats/logging
        // listeners, and "" keeps the relay's byte-accounting at a truthful zero.
        listener.onIncomingMessage(this, "", msg)
    }

    private fun putJob(
        subId: String,
        job: Job,
    ) {
        while (true) {
            val cur = jobs.load()
            val prev = cur[subId]
            if (jobs.compareAndSet(cur, cur + (subId to job))) {
                prev?.cancel()
                return
            }
        }
    }

    private fun cancelJob(subId: String) {
        while (true) {
            val cur = jobs.load()
            val prev = cur[subId] ?: return
            if (jobs.compareAndSet(cur, cur - subId)) {
                prev.cancel()
                return
            }
        }
    }
}
