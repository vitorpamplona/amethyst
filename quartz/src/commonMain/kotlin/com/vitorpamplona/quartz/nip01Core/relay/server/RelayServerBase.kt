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

import com.vitorpamplona.quartz.nip01Core.relay.server.policies.LimitsPolicy
import com.vitorpamplona.quartz.nip77Negentropy.NegentropySettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.coroutines.CoroutineContext

/**
 * Shared engine behind [NostrServer] and [EventSourceServer]. Owns the
 * per-connection coroutine [scope], the [ConnectionRegistry] (and the
 * [activeConnections] gauge / [RelayServerListener] dispatch it drives), the
 * policy composition, and the connect/serve plumbing — so the two concrete
 * servers differ only in the [backend] they wrap and what their [close] tears
 * down beyond the connections.
 *
 * @param policyBuilder Builds a fresh base policy per connection.
 * @param parentContext Parent coroutine context for all subscriptions.
 * @param negentropySettings NIP-77 tuning handed to each session.
 * @param listener Connection-lifecycle observer.
 * @param limits Operational limits; when non-null a [LimitsPolicy] is prepended
 *   to every connection's policy and the value is exposed for NIP-11 advertising.
 */
abstract class RelayServerBase(
    private val policyBuilder: () -> IRelayPolicy,
    parentContext: CoroutineContext,
    private val negentropySettings: NegentropySettings,
    listener: RelayServerListener,
    val limits: RelayLimits?,
) : AutoCloseable {
    /** Scope for all subscriptions. */
    protected val scope = CoroutineScope(parentContext + SupervisorJob())

    private val connections = ConnectionRegistry(listener)

    /** The data plane every connection on this server talks to. */
    protected abstract val backend: SessionBackend

    /** Number of connections currently registered with this server. */
    val activeConnections: Long get() = connections.active

    /**
     * Builds the per-connection policy, prepending a [LimitsPolicy] when
     * [limits] is set so requests are clamped/rejected before the application
     * policy runs.
     */
    private fun buildPolicy(): IRelayPolicy {
        val base = policyBuilder()
        return if (limits != null) LimitsPolicy(limits) + base else base
    }

    /**
     * Registers a new client connection.
     *
     * @param send Callback the server uses to send JSON messages to this client.
     *             Implementations must be safe to call from any coroutine.
     */
    fun connect(send: (String) -> Unit): RelaySession =
        connections.register(
            RelaySession(
                policy = buildPolicy(),
                store = backend,
                scope = scope,
                onSend = send,
                onClose = { connections.unregister(it.id) },
                negentropySettings = negentropySettings,
            ),
        )

    /**
     * Registers a new client connection and serves it for the duration of
     * [incoming]. The session is automatically closed when [incoming] returns.
     *
     * @param send Callback the server uses to send JSON messages to this client.
     * @param incoming Suspend block that yields raw JSON strings from the client
     *                 (e.g., reading WebSocket text frames in a loop).
     */
    suspend fun serve(
        send: (String) -> Unit,
        incoming: suspend (RelaySession) -> Unit,
    ) {
        val session = connect(send)
        try {
            incoming(session)
        } finally {
            session.close()
        }
    }

    /** Cancels every open connection's subscriptions and clears the registry. */
    protected fun closeConnections() = connections.closeAll()

    /**
     * Shuts the server down: cancels all subscriptions and the server [scope].
     * Subclasses override to tear down extra resources (e.g. an event store),
     * calling [closeConnections] and cancelling [scope] in the order they need.
     */
    override fun close() {
        closeConnections()
        scope.cancel()
    }
}
