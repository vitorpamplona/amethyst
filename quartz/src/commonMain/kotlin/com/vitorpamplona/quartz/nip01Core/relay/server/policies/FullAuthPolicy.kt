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
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.AuthMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.AuthCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CountCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.EventCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.server.backend.RequestContext
import com.vitorpamplona.quartz.nip40Expiration.isExpired
import com.vitorpamplona.quartz.nip42RelayAuth.RelayAuthEvent
import com.vitorpamplona.quartz.utils.RandomInstance
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Requires authentication for all EVENT, REQ, and COUNT commands.
 * Replicates the previous `requireAuth = true` behavior.
 *
 * Implements the full NIP-42 challenge/verify handshake: [onConnect] sends the
 * [challenge] and [accept] (AuthCmd) validates the returned event (expiration,
 * freshness, challenge match, relay match). This policy runs the auth *logic*
 * but does not *own* the authenticated-identity store: the engine-owned
 * connection [scope] holds it. [accept] does NOT mutate state, and
 * [onAuthenticated] only votes `true` (after [authorize]) — the engine performs
 * the single recording into the scope once the whole chain has approved. Gating
 * decisions read [scope].`authenticatedUsers`. A rejected AUTH simply never
 * reaches [onAuthenticated], so there is no rollback to reason about.
 *
 * To bridge to an external auth system, override [authorize] (a `suspend` hook)
 * and do the post-verification I/O there — e.g. exchange the verified event for
 * a backend session token. Throwing from it rejects the login (`OK false`) and
 * the pubkey is never recorded.
 */
open class FullAuthPolicy(
    val relay: NormalizedRelayUrl,
) : IRelayPolicy {
    /** The challenge string sent to this client for NIP-42 authentication. */
    val challenge: String = RandomInstance.randomChars(32)

    /**
     * The engine-owned connection scope, captured at [onConnect]. Read-only
     * here: this policy reads [RequestContext.authenticatedUsers] to gate, while
     * the engine is the only writer. Held safely because a [FullAuthPolicy] is
     * built fresh per connection.
     */
    private lateinit var scope: RequestContext

    /**
     * The pubkeys authenticated on this connection, read from the engine-owned
     * scope. Exposed to subclasses so they can gate or rewrite on the caller's
     * identity (restricted content, caller-relative filters) — the same set the
     * data plane sees via [RequestContext.authenticatedUsers].
     */
    protected val authenticatedUsers: Set<HexKey> get() = scope.authenticatedUsers

    /** Returns true if at least one pubkey has authenticated on this connection. */
    fun isAuthenticated(): Boolean = authenticatedUsers.isNotEmpty()

    override fun onConnect(
        scope: RequestContext,
        send: (Message) -> Unit,
    ) {
        this.scope = scope
        send(AuthMessage(challenge))
    }

    override fun accept(cmd: AuthCmd): PolicyResult<AuthCmd> {
        val event = cmd.event

        if (event.isExpired()) {
            return PolicyResult.Rejected("invalid: auth event expired")
        }

        if (!TimeUtils.withinTenMinutes(event.createdAt)) {
            return PolicyResult.Rejected("invalid: created_at is too far from the current time")
        }

        if (event.challenge() != challenge) {
            return PolicyResult.Rejected("invalid: challenge does not match")
        }

        if (event.relay() != relay) {
            return PolicyResult.Rejected("invalid: relay url does not match")
        }

        return PolicyResult.Accepted(cmd)
    }

    /**
     * Votes to record the authentication. The engine calls this only after
     * [accept] and the whole policy chain have approved the AUTH. It runs
     * [authorize] first (which may throw to reject — the engine then records
     * nothing) and returns `true` so the engine records `event.pubKey` into the
     * connection scope. `final`: override [authorize], not this.
     */
    final override suspend fun onAuthenticated(event: RelayAuthEvent): Boolean {
        authorize(event)
        return true
    }

    /**
     * Hook for external authorization once the NIP-42 proof checks out — e.g.
     * exchange [event] for a backend session token. Throw to reject the login
     * (the AUTH becomes `OK false` and `event.pubKey` is not recorded). Runs
     * before the pubkey is committed. The default does nothing.
     */
    open suspend fun authorize(event: RelayAuthEvent) {}

    override fun accept(cmd: EventCmd): PolicyResult<EventCmd> =
        if (isAuthenticated()) {
            PolicyResult.Accepted(cmd)
        } else {
            PolicyResult.Rejected("auth-required: this relay requires authentication")
        }

    override fun accept(cmd: ReqCmd) =
        if (isAuthenticated()) {
            PolicyResult.Accepted(cmd)
        } else {
            PolicyResult.Rejected("auth-required: this relay requires authentication")
        }

    override fun accept(cmd: CountCmd) =
        if (isAuthenticated()) {
            PolicyResult.Accepted(cmd)
        } else {
            PolicyResult.Rejected("auth-required: this relay requires authentication")
        }

    override fun canSendToSession(event: Event) = true
}
