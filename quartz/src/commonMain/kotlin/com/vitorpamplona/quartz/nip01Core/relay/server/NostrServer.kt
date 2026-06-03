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
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.LimitsPolicy
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.VerifyPolicy
import com.vitorpamplona.quartz.nip01Core.store.IEventStore
import com.vitorpamplona.quartz.nip77Negentropy.NegentropySettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.coroutines.CoroutineContext

/**
 * Represents a Nostr relay server that manages client connections, event storage, and verification.
 *
 * This class acts as the central coordinator for a relay server, handling the lifecycle of [RelaySession]s
 * and providing access to the underlying event store.
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
 *   via a composed [LimitsPolicy], plus the session-level message-size and
 *   subscription caps) and advertised via [RelayLimits.toNip11Limitation].
 *   Null disables limit enforcement.
 */
class NostrServer(
    private val store: IEventStore,
    private val policyBuilder: () -> IRelayPolicy = { VerifyPolicy },
    private val parentContext: CoroutineContext = SupervisorJob(),
    parallelVerify: Boolean = false,
    private val negentropySettings: NegentropySettings = NegentropySettings.Default,
    listener: RelayServerListener = RelayServerListener.None,
    val limits: RelayLimits? = null,
) : AutoCloseable {
    /** Scope for all subscriptions. */
    private val scope = CoroutineScope(parentContext + SupervisorJob())

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
        )

    private val subStore = LiveEventStore(store, ingest)

    private val connections = ConnectionRegistry(listener)

    /** Number of connections currently registered with this server. */
    val activeConnections: Long get() = connections.active

    /**
     * Builds the per-connection policy, prepending a [LimitsPolicy] when
     * [limits] declares per-command caps so requests are clamped/rejected
     * before the application policy runs.
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
                store = subStore,
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
     * @param send   Callback the server uses to send JSON messages to this client.
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

    /**
     * Shuts down the server, cancelling all subscriptions and closing the store.
     */
    override fun close() {
        connections.closeAll()
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
