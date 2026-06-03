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

import com.vitorpamplona.quartz.nip01Core.relay.server.policies.EmptyPolicy
import com.vitorpamplona.quartz.nip77Negentropy.NegentropySettings
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.CoroutineContext

/**
 * A transport-agnostic relay engine for relays that answer REQs from a
 * [ReqResponder] instead of an event store — search relays, redirectors that
 * forward to an HTTP backend, and relays that emit computed/projected data.
 *
 * This is the storage-free sibling of [NostrServer]. It owns the full NIP-01
 * wire protocol — challenge/auth, command parsing, the [IRelayPolicy], EVENT/
 * EOSE/CLOSED framing, and subscription lifecycle — so callers only provide a
 * per-connection `send` callback and feed it raw JSON frames, exactly like
 * [NostrServer]. EVENT publishes are rejected (there is nothing to store) and
 * negentropy is disabled, per [ReqResponderBackend] / [SessionBackend].
 *
 * ```
 * val server = ReqResponderServer(
 *     responder = SearchResponder(searchApi),
 *     policyBuilder = { FullAuthPolicy(relay) }, // optional NIP-42 gating
 * )
 *
 * // per WebSocket connection:
 * server.serve(send = { json -> launch { socket.send(json) } }) { session ->
 *     for (frame in incoming) session.receive(frame.text)
 * }
 * ```
 *
 * Both this class and [RelaySession] implement [AutoCloseable].
 *
 * @param responder Produces the events that answer each REQ.
 * @param policyBuilder Builds a fresh [IRelayPolicy] per connection. Defaults to
 *   [EmptyPolicy] (accept REQ/COUNT, no signature verification — appropriate for
 *   a read-only relay). Pass a [com.vitorpamplona.quartz.nip01Core.relay.server.policies.FullAuthPolicy]
 *   to gate access behind NIP-42.
 * @param parentContext Parent coroutine context for all subscriptions.
 * @param negentropySettings NIP-77 tuning. Negentropy is effectively a no-op
 *   here (the snapshot is empty) but the setting is plumbed for symmetry.
 * @param listener Observability hook fired as connections open and close,
 *   keyed by [RelaySession.id]. Defaults to a no-op.
 * @param limits Operational limits enforced on every connection (per-command
 *   via a composed [LimitsPolicy], plus the session-level message-size and
 *   subscription caps) and advertised via [RelayLimits.toNip11Limitation].
 *   Null disables limit enforcement.
 */
class ReqResponderServer(
    responder: ReqResponder,
    policyBuilder: () -> IRelayPolicy = { EmptyPolicy },
    parentContext: CoroutineContext = SupervisorJob(),
    negentropySettings: NegentropySettings = NegentropySettings.Default,
    listener: RelayServerListener = RelayServerListener.None,
    limits: RelayLimits? = null,
) : RelayServerBase(policyBuilder, parentContext, negentropySettings, listener, limits) {
    override val backend: SessionBackend = ReqResponderBackend(responder)
}
