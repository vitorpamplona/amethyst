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
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.CountResult
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EoseMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EventMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.NoticeMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.OkMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.AuthCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CloseCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CountCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.EventCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import com.vitorpamplona.quartz.nip77Negentropy.NegCloseCmd
import com.vitorpamplona.quartz.nip77Negentropy.NegErrMessage
import com.vitorpamplona.quartz.nip77Negentropy.NegMsgCmd
import com.vitorpamplona.quartz.nip77Negentropy.NegOpenCmd
import com.vitorpamplona.quartz.nip77Negentropy.NegentropyServerSession
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.cache.LargeCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Represents an active session between a Nostr client and the relay.
 * Each one of these is a connection that can hold many subscriptions
 */
class RelaySession(
    private val store: LiveEventStore,
    val policy: IRelayPolicy,
    private val scope: CoroutineScope,
    private val onSend: (String) -> Unit,
    private val onClose: (RelaySession) -> Unit,
) : AutoCloseable {
    private val subscriptions = LargeCache<String, Job>()

    /**
     * NIP-77 negentropy reconciliation sessions, keyed by NEG-OPEN
     * subId. Plain hash map here (not [LargeCache]) because it's
     * mutated only from the single-threaded `receive()` path —
     * RelaySession.receive is serialised by the WebSocket handler.
     */
    private val negSessions = HashMap<String, NegentropyServerSession>()

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
        negSessions.clear()
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
            is NegOpenCmd -> handleNegOpen(cmd)
            is NegMsgCmd -> handleNegMsg(cmd)
            is NegCloseCmd -> handleNegClose(cmd)
            else -> send(NoticeMessage("error: unsupported command ${cmd.label()}"))
        }
    }

    private suspend fun handleEvent(cmd: EventCmd) {
        val result = policy.accept(cmd)
        if (result is PolicyResult.Rejected) {
            send(OkMessage(cmd.event.id, false, result.reason))
            return
        }

        try {
            store.insert(cmd.event)
            send(OkMessage(cmd.event.id, true, ""))
        } catch (e: Exception) {
            send(OkMessage(cmd.event.id, false, e.message ?: e::class.simpleName ?: "unkown error"))
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

        val total = store.count(filters)

        send(CountMessage(cmd.queryId, CountResult(total)))
    }

    // -- NIP-42: AUTH ---------------------------------------------------------
    private fun handleAuth(cmd: AuthCmd) {
        val result = policy.accept(cmd)
        if (result is PolicyResult.Rejected) {
            send(OkMessage(cmd.event.id, false, result.reason))
            return
        }

        send(OkMessage(cmd.event.id, true, ""))
    }

    // -- NIP-01: REQ ----------------------------------------------------------
    private fun handleReq(cmd: ReqCmd) {
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
                } catch (_: kotlinx.coroutines.CancellationException) {
                    // Subscription was closed – this is expected.
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

    // -- NIP-77: NEG-OPEN -----------------------------------------------------

    /**
     * Open a negentropy reconciliation session. The relay snapshots its
     * matching events at this instant — concurrent inserts during the
     * sync are not surfaced; clients re-open if they want fresh state.
     *
     * Access control reuses the REQ policy hook: a relay that requires
     * AUTH or has kind/pubkey allow-deny lists applies the same rules
     * to NEG-OPEN as it does to subscription REQs.
     */
    private suspend fun handleNegOpen(cmd: NegOpenCmd) {
        // Run the same access controls as REQ would.
        val asReq = ReqCmd(cmd.subId, listOf(cmd.filter))
        val gate = policy.accept(asReq)
        if (gate is PolicyResult.Rejected) {
            send(NegErrMessage(cmd.subId, gate.reason))
            return
        }
        val filters = (gate as PolicyResult.Accepted).cmd.filters

        // Drop any prior session at this subId (NIP-77: same-subId
        // OPEN replaces).
        negSessions.remove(cmd.subId)

        val events =
            if (filters.size == 1) {
                store.snapshotQuery(filters[0])
            } else {
                // Multiple filters: union the snapshots and dedupe by id.
                val seen = HashSet<String>()
                val merged = mutableListOf<com.vitorpamplona.quartz.nip01Core.core.Event>()
                for (f in filters) {
                    for (e in store.snapshotQuery(f)) {
                        if (seen.add(e.id)) merged += e
                    }
                }
                merged
            }

        val neg = NegentropyServerSession(cmd.subId, events)
        negSessions[cmd.subId] = neg

        try {
            val response = neg.processMessage(cmd.initialMessage)
            if (response != null) send(response)
        } catch (e: Exception) {
            negSessions.remove(cmd.subId)
            send(NegErrMessage(cmd.subId, "error: ${e.message ?: e::class.simpleName}"))
        }
    }

    // -- NIP-77: NEG-MSG ------------------------------------------------------
    private fun handleNegMsg(cmd: NegMsgCmd) {
        val neg = negSessions[cmd.subId]
        if (neg == null) {
            send(NegErrMessage(cmd.subId, "error: no negentropy session for ${cmd.subId}"))
            return
        }
        try {
            val response = neg.processMessage(cmd.message)
            if (response != null) send(response)
        } catch (e: Exception) {
            negSessions.remove(cmd.subId)
            send(NegErrMessage(cmd.subId, "error: ${e.message ?: e::class.simpleName}"))
        }
    }

    // -- NIP-77: NEG-CLOSE ----------------------------------------------------
    private fun handleNegClose(cmd: NegCloseCmd) {
        // Spec: clients send NEG-CLOSE to free server-side state.
        // Silent no-op if the session is unknown — there's no authoritative
        // error response in NIP-77 for an unknown close.
        negSessions.remove(cmd.subId)
    }

    init {
        policy.onConnect(::send)
    }
}
