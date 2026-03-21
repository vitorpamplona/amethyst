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
import com.vitorpamplona.quartz.nip01Core.relay.server.IRelayPolicy
import com.vitorpamplona.quartz.nip01Core.relay.server.PolicyResult
import com.vitorpamplona.quartz.nip40Expiration.isExpired
import com.vitorpamplona.quartz.utils.RandomInstance
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Requires authentication for all EVENT, REQ, and COUNT commands.
 * Replicates the previous `requireAuth = true` behavior.
 */
open class FullAuthPolicy(
    val relay: NormalizedRelayUrl,
) : IRelayPolicy {
    /** The challenge string sent to this client for NIP-42 authentication. */
    val challenge: String = RandomInstance.randomChars(32)

    /** Set of pubkeys that have successfully authenticated on this session. */
    val authenticatedUsers = mutableSetOf<HexKey>()

    /** Returns true if at least one pubkey has authenticated. */
    fun isAuthenticated(): Boolean = authenticatedUsers.isNotEmpty()

    override fun onConnect(send: (Message) -> Unit) {
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

        authenticatedUsers.add(event.pubKey)

        return PolicyResult.Accepted(cmd)
    }

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
