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
import com.vitorpamplona.quartz.nip01Core.relay.server.backend.SessionBackend
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.IRelayPolicy
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.PolicyResult
import com.vitorpamplona.quartz.nip01Core.store.IEventStore
import com.vitorpamplona.quartz.nip77Negentropy.NegCloseCmd
import com.vitorpamplona.quartz.nip77Negentropy.NegMsgCmd
import com.vitorpamplona.quartz.nip77Negentropy.NegOpenCmd
import com.vitorpamplona.quartz.nip77Negentropy.NegentropySettings
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.cache.LargeCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
    private val subscriptions = LargeCache<String, Job>()

    /** NIP-77 negentropy state for this connection. */
    private val negentropy = NegSessionRegistry(store, ::send, negentropySettings)

    private fun addSubscription(
        subId: String,
        job: Job,
    ) = subscriptions.put(subId, job)

    private fun cancelSubscription(subId: String): Boolean =
        subscriptions.remove(subId)?.let {
            it.cancel()
            true
        } ?: false

    fun cancelAllSubscriptions() {
        subscriptions.forEach { _, job -> job.cancel() }
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
                store.countResult(filters)
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
        // backend token) AND is where a policy commits the authentication, so a
        // throw here cleanly fails the login — nothing was committed to undo.
        try {
            policy.onAuthenticated(cmd.event.pubKey, cmd.event)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            send(OkMessage.rejected(cmd.event.id, MachineReadablePrefix.ERROR, e.message ?: "authentication failed"))
            return
        }

        send(OkMessage(cmd.event.id, true, ""))
    }

    // -- NIP-01: REQ ----------------------------------------------------------
    private fun handleReq(cmd: ReqCmd) {
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

        val job =
            scope.launch {
                try {
                    store.query(
                        filters = filters,
                        onEach = { event ->
                            if (policy.canSendToSession(event)) {
                                send(EventMessage(cmd.subId, event))
                            }
                        },
                        onEose = { send(EoseMessage(cmd.subId)) },
                    )
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

        addSubscription(cmd.subId, job)
    }

    // -- NIP-01: CLOSE --------------------------------------------------------
    private fun handleClose(cmd: CloseCmd) {
        val cancelled = cancelSubscription(cmd.subId)
        if (!cancelled) {
            send(ClosedMessage(cmd.subId, "error: no such subscription"))
        }
    }

    init {
        policy.onConnect(::send)
    }

    companion object {
        private val connectionIdSeq = AtomicLong(0L)

        /** Allocates the next process-unique connection id. */
        fun nextConnectionId(): Long = connectionIdSeq.fetchAndAdd(1L)
    }
}
