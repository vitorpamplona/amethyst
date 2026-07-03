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

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.ClosedMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.CountMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EoseMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EventMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.MachineReadablePrefix
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.NoticeMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.OkMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.AuthCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CloseCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CountCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.EventCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.server.backend.RequestContext
import com.vitorpamplona.quartz.nip01Core.relay.server.backend.SessionBackend
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.IRelayPolicy
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.PolicyResult
import com.vitorpamplona.quartz.nip01Core.store.IEventStore
import com.vitorpamplona.quartz.nip01Core.store.RawEvent
import com.vitorpamplona.quartz.nip77Negentropy.NegCloseCmd
import com.vitorpamplona.quartz.nip77Negentropy.NegMsgCmd
import com.vitorpamplona.quartz.nip77Negentropy.NegOpenCmd
import com.vitorpamplona.quartz.nip77Negentropy.NegentropySettings
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.cache.LargeCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.launch
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Represents an active session between a Nostr client and the relay.
 * Each one of these is a connection that can hold many subscriptions
 */
@OptIn(ExperimentalAtomicApi::class)
class RelaySession(
    private val store: SessionBackend,
    val policy: IRelayPolicy,
    private val scope: CoroutineScope,
    private val onSend: (String) -> Unit,
    private val onClose: (RelaySession) -> Unit,
    negentropySettings: NegentropySettings = NegentropySettings.Default,
    /**
     * Stable, process-unique identifier for this connection. Used by the
     * server classes to key their connection registry and by
     * [RelayServerListener] callbacks so observers can correlate the
     * open/close of the same connection. Defaults to a fresh monotonic id.
     */
    val id: Long = nextConnectionId(),
) : AutoCloseable {
    /**
     * One active REQ. Launched subscriptions cancel their coroutine;
     * inline subscriptions (the bounded small-REQ fast path, which never
     * had a coroutine) close their live-tail registration directly.
     */
    private fun interface ActiveSubscription {
        fun cancel()
    }

    private val subscriptions = LargeCache<String, ActiveSubscription>()

    /**
     * The authenticated-identity store for this connection. The engine is the
     * only writer (committed in [handleAuth] on a successful NIP-42 AUTH); the
     * policy and the data plane read it through [requestContext].
     */
    private val authenticatedUsers = mutableSetOf<HexKey>()

    /**
     * The per-connection scope. Handed to the [policy] at connect (so gating
     * policies can read the authenticated users) and to the [store] on every
     * REQ/COUNT (so a source can see who is asking). [RequestContext.authenticatedUsers]
     * is a live view of [authenticatedUsers], so a REQ after a NIP-42 AUTH sees
     * the freshly recorded pubkey(s).
     */
    val requestContext: RequestContext =
        object : RequestContext {
            override val connectionId = id
            override val policy = this@RelaySession.policy
            override val authenticatedUsers: Set<HexKey> get() = this@RelaySession.authenticatedUsers
        }

    /** NIP-77 negentropy state for this connection. */
    private val negentropy = NegSessionRegistry(store, ::send, negentropySettings)

    private fun addSubscription(
        subId: String,
        sub: ActiveSubscription,
    ) = subscriptions.put(subId, sub)

    private fun cancelSubscription(subId: String): Boolean =
        subscriptions.remove(subId)?.let {
            it.cancel()
            true
        } ?: false

    fun cancelAllSubscriptions() {
        subscriptions.forEach { _, sub -> sub.cancel() }
        subscriptions.clear()
        negentropy.clear()
    }

    fun send(message: Message) {
        try {
            onSend(OptimizedJsonMapper.toJson(message))
        } catch (e: Exception) {
            Log.w("ClientSession") { "Failed to send to ${e.message}" }
        }
    }

    /** [send] for frames that are already wire-format JSON (the raw REQ path). */
    private fun sendRaw(json: String) {
        try {
            onSend(json)
        } catch (e: Exception) {
            Log.w("ClientSession") { "Failed to send to ${e.message}" }
        }
    }

    override fun close() {
        cancelAllSubscriptions()
        onClose(this)
    }

    /**
     * Processes a raw JSON message from a client.
     *
     * Parses the message as a NIP-01 command and dispatches it.
     */
    suspend fun receive(command: String) {
        policy.acceptMessage(command)?.let { reason ->
            send(NoticeMessage(reason))
            return
        }

        val cmd =
            try {
                OptimizedJsonMapper.fromJsonToCommand(command)
            } catch (_: Exception) {
                send(NoticeMessage("error: could not parse message"))
                return
            }

        if (!cmd.isValid()) {
            send(NoticeMessage("error: invalid command"))
            return
        }

        when (cmd) {
            is AuthCmd -> handleAuth(cmd)
            is EventCmd -> handleEvent(cmd)
            is ReqCmd -> handleReq(cmd)
            is CloseCmd -> handleClose(cmd)
            is CountCmd -> handleCount(cmd)
            is NegOpenCmd -> negentropy.open(cmd, policy)
            is NegMsgCmd -> negentropy.msg(cmd)
            is NegCloseCmd -> negentropy.close(cmd)
            else -> send(NoticeMessage("error: unsupported command ${cmd.label()}"))
        }
    }

    private suspend fun handleEvent(cmd: EventCmd) {
        val result = policy.accept(cmd)
        if (result is PolicyResult.Rejected) {
            send(OkMessage(cmd.event.id, false, result.reason))
            return
        }

        // Fire-and-forget: hand the event to the group-commit writer
        // and continue reading from the WebSocket without waiting on
        // SQLite. The OK frame is sent from the writer's callback,
        // possibly out of arrival order — NIP-01 pairs OKs to events
        // by id, so reordering is fine.
        try {
            store.submit(cmd.event) { outcome ->
                when (outcome) {
                    IEventStore.InsertOutcome.Accepted -> {
                        send(OkMessage(cmd.event.id, true, ""))
                    }

                    is IEventStore.InsertOutcome.Rejected -> {
                        send(OkMessage(cmd.event.id, false, outcome.reason))
                    }
                }
            }
        } catch (_: ClosedSendChannelException) {
            // Server is shutting down — the queue is closed. Reply
            // with a transient failure so a client re-trying against
            // the next instance gets a sane signal; the WS itself is
            // about to be torn down by the server-stop path.
            send(OkMessage(cmd.event.id, false, "error: relay shutting down"))
        }
    }

    private suspend fun handleCount(cmd: CountCmd) {
        val result = policy.accept(cmd)
        if (result is PolicyResult.Rejected) {
            send(ClosedMessage(cmd.queryId, result.reason))
            return
        }

        // Policy may rewrite filters to match the user's access level.
        val filters = (result as PolicyResult.Accepted).cmd.filters

        val countResult =
            try {
                store.countResult(requestContext, filters)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                send(ClosedMessage.of(cmd.queryId, MachineReadablePrefix.ERROR, e.message ?: "count failed"))
                return
            }

        send(CountMessage(cmd.queryId, countResult))
    }

    // -- NIP-42: AUTH ---------------------------------------------------------
    private suspend fun handleAuth(cmd: AuthCmd) {
        val result = policy.accept(cmd)
        if (result is PolicyResult.Rejected) {
            send(OkMessage(cmd.event.id, false, result.reason))
            return
        }

        // The whole policy chain validated the AUTH. onAuthenticated runs any
        // post-verification I/O (e.g. exchanging the verified event for a
        // backend token) and votes on whether to record the identity. A throw
        // here cleanly fails the login — the engine records nothing.
        val record =
            try {
                policy.onAuthenticated(cmd.event)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                send(OkMessage.rejected(cmd.event.id, MachineReadablePrefix.ERROR, e.message ?: "authentication failed"))
                return
            }

        // Single, engine-side commit into the connection scope — after the full
        // chain approved and a verifying policy voted to record.
        if (record) authenticatedUsers.add(cmd.event.pubKey)

        send(OkMessage(cmd.event.id, true, ""))
    }

    // -- NIP-01: REQ ----------------------------------------------------------

    /**
     * A REQ qualifies for the inline fast path when its replay is
     * provably bounded: every filter carries a `limit`, or an `ids` list
     * (ids are unique keys, so the result can't exceed the list), and
     * the bounds sum to at most [INLINE_REPLAY_MAX_ROWS]. Inline replays
     * run on this connection's receive coroutine — no per-REQ launch, no
     * dispatcher handoffs (profiled at ~2/3 of a ~20-row REQ's
     * time-to-EOSE) — at the price of delaying the connection's NEXT
     * command until EOSE, which the row cap keeps in the
     * single-digit-milliseconds range. Unbounded REQs keep the launched
     * path so a CLOSE can always interrupt a genuinely giant replay.
     */
    private fun isInlineEligible(filters: List<Filter>): Boolean {
        var total = 0
        for (f in filters) {
            val bound = f.limit ?: f.ids?.size ?: return false
            total += bound
            if (total > INLINE_REPLAY_MAX_ROWS) return false
        }
        return true
    }

    private suspend fun handleReq(cmd: ReqCmd) {
        // Ask the policy whether a *new* subscription may open (e.g. a
        // max_subscriptions cap). A re-REQ on an existing id replaces it
        // 1-for-1 and doesn't grow the count, so it's exempt. Checked before
        // the cancel so the existing subscription isn't dropped only to then
        // reject its replacement.
        if (!subscriptions.containsKey(cmd.subId)) {
            policy.acceptSubscription(cmd.subId, subscriptions.size())?.let { reason ->
                send(ClosedMessage(cmd.subId, reason))
                return
            }
        }

        // Cancel any existing subscription with the same id (NIP-01 spec).
        cancelSubscription(cmd.subId)

        val result = policy.accept(cmd)
        if (result is PolicyResult.Rejected) {
            send(ClosedMessage(cmd.subId, result.reason))
            return
        }

        // Policy may rewrite filters to match the user's access level.
        val filters = (result as PolicyResult.Accepted).cmd.filters

        // The `["EVENT","<subId>",` prefix is built once per
        // subscription, not per row (zero-decode paths only).
        fun framePrefix() =
            buildString {
                append("[\"EVENT\",")
                RawEvent.appendJsonQuoted(this, cmd.subId)
                append(',')
            }

        fun sendStored(
            framePrefix: String,
            raw: RawEvent,
        ) = sendRaw(
            buildString(framePrefix.length + raw.jsonTags.length + raw.content.length + 256) {
                append(framePrefix)
                raw.appendJsonObjectTo(this)
                append(']')
            },
        )

        // Small-REQ fast path: a provably bounded replay runs inline on
        // this receive coroutine — no launch, no dispatcher handoffs —
        // and only the live-tail handle is retained. See
        // [SessionBackend.queryRawInline] for the contract.
        if (!policy.filtersOutgoingEvents && isInlineEligible(filters)) {
            val handle =
                try {
                    val prefix = framePrefix()
                    store.queryRawInline(
                        ctx = requestContext,
                        filters = filters,
                        onEachStored = { raw -> sendStored(prefix, raw) },
                        onEachLive = { event -> send(EventMessage(cmd.subId, event)) },
                        onEose = { send(EoseMessage(cmd.subId)) },
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    send(ClosedMessage.of(cmd.subId, MachineReadablePrefix.ERROR, e.message ?: "query failed"))
                    return
                }
            if (handle != null) {
                addSubscription(cmd.subId) { handle.close() }
                return
            }
            // Backend without an inline path: fall through to launch.
        }

        val job =
            scope.launch {
                try {
                    if (policy.filtersOutgoingEvents) {
                        // Screened path: every event is materialized so the
                        // policy can veto it per session.
                        store.query(
                            ctx = requestContext,
                            filters = filters,
                            onEach = { event ->
                                if (policy.canSendToSession(event)) {
                                    send(EventMessage(cmd.subId, event))
                                }
                            },
                            onEose = { send(EoseMessage(cmd.subId)) },
                        )
                    } else {
                        // Zero-decode path: the stored replay splices raw
                        // storage strings straight into wire frames — no tags
                        // parse, no Event materialization, no re-serialize.
                        val prefix = framePrefix()
                        store.queryRaw(
                            ctx = requestContext,
                            filters = filters,
                            onEachStored = { raw -> sendStored(prefix, raw) },
                            onEachLive = { event -> send(EventMessage(cmd.subId, event)) },
                            onEose = { send(EoseMessage(cmd.subId)) },
                        )
                    }
                } catch (e: CancellationException) {
                    // Subscription was closed – this is expected.
                    throw e
                } catch (e: Exception) {
                    // A backend failure (e.g. an event source's network I/O)
                    // ends the subscription with a machine-readable CLOSED
                    // rather than silently dropping the coroutine.
                    send(ClosedMessage.of(cmd.subId, MachineReadablePrefix.ERROR, e.message ?: "query failed"))
                }
            }

        addSubscription(cmd.subId) { job.cancel() }
    }

    // -- NIP-01: CLOSE --------------------------------------------------------
    private fun handleClose(cmd: CloseCmd) {
        val cancelled = cancelSubscription(cmd.subId)
        if (!cancelled) {
            send(ClosedMessage(cmd.subId, "error: no such subscription"))
        }
    }

    init {
        policy.onConnect(requestContext, ::send)
    }

    companion object {
        private val connectionIdSeq = AtomicLong(0L)

        /** Allocates the next process-unique connection id. */
        fun nextConnectionId(): Long = connectionIdSeq.fetchAndAdd(1L)

        /**
         * Ceiling on the summed per-filter bounds (limit or ids count)
         * for the inline REQ fast path. Sized so the worst-case inline
         * replay stays in single-digit milliseconds (500-row replays
         * measure ~5–8 ms of storage+frame work) — small enough that
         * delaying the connection's next command is unnoticeable, large
         * enough to cover the limit≤500 feed/notifications/archive
         * shapes real clients hammer relays with.
         */
        const val INLINE_REPLAY_MAX_ROWS = 512
    }
}
