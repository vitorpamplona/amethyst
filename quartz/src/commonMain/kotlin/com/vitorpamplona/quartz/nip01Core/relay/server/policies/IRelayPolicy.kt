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
package com.vitorpamplona.quartz.nip01Core.relay.server.policies

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.AuthCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CountCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.EventCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import com.vitorpamplona.quartz.nip01Core.relay.server.backend.RequestContext
import com.vitorpamplona.quartz.nip42RelayAuth.RelayAuthEvent

/**
 * Defines custom behavior for this relay.
 */
interface IRelayPolicy {
    /**
     * Called once when the connection opens. [scope] is the engine-owned,
     * read-only connection scope (id + authenticated users) — a per-connection
     * policy may retain it to make later auth-aware decisions; shared singleton
     * policies must ignore it and stay stateless. [send] pushes a message to the
     * client (e.g. a NIP-42 AUTH challenge).
     */
    fun onConnect(
        scope: RequestContext,
        send: (Message) -> Unit,
    )

    /**
     * Evaluates whether an incoming EVENT command should be accepted.
     *
     * @param cmd The event the client wants to publish.
     * @return [PolicyResult.Accepted] to store the event, or [PolicyResult.Rejected] with a reason string.
     */
    fun accept(cmd: EventCmd): PolicyResult<EventCmd>

    /**
     * Evaluates a REQ command, optionally rewriting the filter list.
     *
     * @param cmd The filters from the REQ command.
     * @return [PolicyResult.Accepted] with an optional replacement filter list, or [PolicyResult.Rejected].
     */
    fun accept(cmd: ReqCmd): PolicyResult<ReqCmd>

    /**
     * Evaluates a COUNT command, optionally rewriting the filter list.
     *
     * @param cmd The filters from the COUNT command.
     * @return [PolicyResult.Accepted] to allow counting, or [PolicyResult.Rejected] with a reason.
     */
    fun accept(cmd: CountCmd): PolicyResult<CountCmd>

    /**
     * Evaluates whether an incoming AUTH command should be accepted.
     *
     * @param cmd The event the client wants to auth.
     * @return [PolicyResult.Accepted] to log in, or [PolicyResult.Rejected] with a reason string.
     */
    fun accept(cmd: AuthCmd): PolicyResult<AuthCmd>

    /**
     * Called once an AUTH command has been [accept]ed by this policy *and* the
     * rest of the policy chain, before the success `OK` is sent. Run any
     * post-verification side effects that need network or disk I/O here — e.g.
     * exchanging the verified NIP-42 event for a backend session token — without
     * leaking that logic into the transport layer.
     *
     * The engine — not the policy — owns the authenticated-identity store. The
     * return value is this policy's vote on whether [pubKey] should be recorded
     * as authenticated on the connection: return `true` only if this policy
     * actually verified the identity. The default returns `false`, so a policy
     * that does not authenticate (e.g. a pass-through or a blind-accept) never
     * causes an unverified pubkey to be recorded.
     *
     * Because it runs only after the whole chain approved the AUTH, throwing
     * here cleanly fails the login: the AUTH becomes `OK false` and the engine
     * records nothing.
     *
     * @param pubKey The pubkey being authenticated.
     * @param event The verified NIP-42 auth event.
     * @return `true` to have the engine record [pubKey] as authenticated.
     */
    suspend fun onAuthenticated(
        pubKey: HexKey,
        event: RelayAuthEvent,
    ): Boolean = false

    /**
     * Inspects a raw inbound message before it is parsed. Return a reason
     * string to reject it (the engine sends it as a `NOTICE`), or null to let
     * it through. This is the only hook that sees the unparsed frame, so guards
     * that must run before JSON parsing live here — e.g. a `max_message_length`
     * cap (see [com.vitorpamplona.quartz.nip01Core.relay.server.policies.LimitsPolicy]).
     *
     * Called on every inbound message, so keep it cheap. Default: accept.
     */
    fun acceptMessage(message: String): String? = null

    /**
     * Decides whether a new subscription may open on this connection, given how
     * many are already open ([openSubscriptions]). Called only for a genuinely
     * new subscription id — a REQ that replaces an existing id does not grow the
     * count. Return a reason string to reject it (the engine sends a `CLOSED`),
     * or null to allow it. This is where a `max_subscriptions` cap lives, since
     * a policy otherwise can't see the per-connection subscription count.
     *
     * Default: accept.
     */
    fun acceptSubscription(
        subId: String,
        openSubscriptions: Int,
    ): String? = null

    /**
     * Filters a live event before it is forwarded to a subscriber.
     *
     * Called for each event that matches a subscription's filters. Return
     * true to deliver the event, false to suppress it for this session.
     *
     * @param event The event about to be sent.
     */
    fun canSendToSession(event: Event): Boolean = true

    operator fun plus(other: IRelayPolicy): IRelayPolicy = PolicyStack(this, other)
}

sealed interface PolicyResult<T : Command> {
    class Accepted<T : Command>(
        val cmd: T,
    ) : PolicyResult<T>

    class Rejected<T : Command>(
        val reason: String,
    ) : PolicyResult<T>
}
