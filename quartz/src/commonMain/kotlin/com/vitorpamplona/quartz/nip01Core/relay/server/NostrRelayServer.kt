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
import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.ClosedMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.CountMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.CountResult
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EoseMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EventMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.NoticeMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.OkMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CloseCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CountCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.EventCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.CoroutineContext

/**
 * Nostr relay server implementing NIP-01 and NIP-45.
 *
 * This class manages per-connection subscriptions as coroutines. Each
 * subscription ([REQ]) launches a child coroutine that first replays stored
 * events matching the filters, sends EOSE, and then streams live events.
 * Closing a subscription ([CLOSE]) immediately cancels its coroutine.
 *
 * The server is transport-agnostic: callers feed incoming JSON via
 * [processMessage] and receive outgoing JSON via the [send] callback
 * provided to [connect]. This allows use with any WebSocket library.
 *
 * @param store The [EventStore] backing this relay.
 * @param eventVerifier Validates incoming events. Defaults to cryptographic
 *                      verification (id + signature). Override for testing.
 */
class NostrRelayServer(
    val store: EventStore,
    parentContext: CoroutineContext = SupervisorJob(),
    private val eventVerifier: (Event) -> Boolean = { it.verify() },
) {
    private val scope = CoroutineScope(parentContext + SupervisorJob())

    /** Active client sessions keyed by an opaque connection id. */
    private val sessions = HashMap<String, ClientSession>()
    private val sessionsMutex = Mutex()

    /**
     * Registers a new client connection.
     *
     * @param connectionId Unique id for this connection (e.g. WebSocket session id).
     * @param send Callback the server uses to send JSON messages to this client.
     *             Implementations must be safe to call from any coroutine.
     */
    suspend fun connect(
        connectionId: String,
        send: suspend (String) -> Unit,
    ) {
        sessionsMutex.withLock {
            sessions[connectionId] = ClientSession(connectionId, send)
        }
    }

    /**
     * Removes a client connection and cancels all its subscriptions.
     */
    suspend fun disconnect(connectionId: String) {
        sessionsMutex
            .withLock {
                sessions.remove(connectionId)
            }?.cancelAll()
    }

    /**
     * Processes a raw JSON message from a client.
     *
     * Parses the message as a NIP-01 command and dispatches it.
     */
    suspend fun processMessage(
        connectionId: String,
        message: String,
    ) {
        val session =
            sessionsMutex.withLock { sessions[connectionId] }
                ?: return

        val command =
            try {
                OptimizedJsonMapper.fromJsonToCommand(message)
            } catch (e: Exception) {
                Log.w(TAG, "Invalid message from $connectionId: ${e.message}")
                session.send(NoticeMessage("error: could not parse message"))
                return
            }

        if (!command.isValid()) {
            session.send(NoticeMessage("error: invalid command"))
            return
        }

        when (command) {
            is EventCmd -> handleEvent(session, command)
            is ReqCmd -> handleReq(session, command)
            is CloseCmd -> handleClose(session, command)
            is CountCmd -> handleCount(session, command)
            else -> session.send(NoticeMessage("error: unsupported command ${command.label()}"))
        }
    }

    // -- NIP-01: EVENT --------------------------------------------------------

    private suspend fun handleEvent(
        session: ClientSession,
        cmd: EventCmd,
    ) {
        val event = cmd.event

        if (!eventVerifier(event)) {
            session.send(OkMessage(event.id, false, "invalid: bad signature or id"))
            return
        }

        val accepted = store.store(event)
        if (accepted) {
            session.send(OkMessage(event.id, true, ""))
        } else {
            session.send(OkMessage(event.id, false, "duplicate: already have this event"))
        }
    }

    // -- NIP-01: REQ ----------------------------------------------------------

    private suspend fun handleReq(
        session: ClientSession,
        cmd: ReqCmd,
    ) {
        // Cancel any existing subscription with the same id (NIP-01 spec).
        session.cancelSubscription(cmd.subId)

        val job =
            scope.launch {
                try {
                    // 1. Replay stored events matching filters.
                    for (filter in cmd.filters) {
                        val events = store.query(filter)
                        for (event in events) {
                            session.send(EventMessage(cmd.subId, event))
                        }
                    }

                    // 2. Signal end of stored events.
                    session.send(EoseMessage(cmd.subId))

                    // 3. Stream live events until cancelled.
                    store.newEvents.collect { event ->
                        if (cmd.filters.any { it.match(event) }) {
                            session.send(EventMessage(cmd.subId, event))
                        }
                    }
                } catch (_: kotlinx.coroutines.CancellationException) {
                    // Subscription was closed – this is expected.
                }
            }

        session.addSubscription(cmd.subId, job)
    }

    // -- NIP-01: CLOSE --------------------------------------------------------

    private suspend fun handleClose(
        session: ClientSession,
        cmd: CloseCmd,
    ) {
        val cancelled = session.cancelSubscription(cmd.subId)
        if (!cancelled) {
            session.send(ClosedMessage(cmd.subId, "error: no such subscription"))
        }
    }

    // -- NIP-45: COUNT --------------------------------------------------------

    private suspend fun handleCount(
        session: ClientSession,
        cmd: CountCmd,
    ) {
        var total = 0
        for (filter in cmd.filters) {
            total += store.count(filter)
        }
        session.send(CountMessage(cmd.queryId, CountResult(total)))
    }

    /**
     * Shuts down the server, cancelling all subscriptions and sessions.
     */
    suspend fun shutdown() {
        sessionsMutex.withLock {
            sessions.values.forEach { it.cancelAll() }
            sessions.clear()
        }
        scope.cancel()
    }

    companion object {
        private const val TAG = "NostrRelayServer"
    }
}

/**
 * Represents a single connected client with its active subscriptions.
 */
internal class ClientSession(
    val connectionId: String,
    private val sendCallback: suspend (String) -> Unit,
) {
    private val subscriptions = HashMap<String, Job>()
    private val mutex = Mutex()

    suspend fun send(message: com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message) {
        try {
            sendCallback(OptimizedJsonMapper.toJson(message))
        } catch (e: Exception) {
            Log.w("ClientSession", "Failed to send to $connectionId: ${e.message}")
        }
    }

    suspend fun addSubscription(
        subId: String,
        job: Job,
    ) {
        mutex.withLock {
            subscriptions[subId] = job
        }
    }

    suspend fun cancelSubscription(subId: String): Boolean =
        mutex.withLock {
            subscriptions.remove(subId)?.let {
                it.cancel()
                true
            } ?: false
        }

    suspend fun cancelAll() {
        mutex.withLock {
            subscriptions.values.forEach { it.cancel() }
            subscriptions.clear()
        }
    }
}
