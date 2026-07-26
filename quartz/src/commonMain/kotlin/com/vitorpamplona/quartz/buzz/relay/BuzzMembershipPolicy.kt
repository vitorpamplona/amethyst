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
package com.vitorpamplona.quartz.buzz.relay

import com.vitorpamplona.quartz.buzz.oaOwnerAttestation.tags.AuthTag
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CountCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.EventCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.FullAuthPolicy
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.PolicyResult
import com.vitorpamplona.quartz.nip42RelayAuth.RelayAuthEvent

/**
 * A **private, agent-authorized workspace** relay policy — the thin server half that lets
 * `geode` (via `amy serve --buzz`) host a Buzz-style agent channel without Block's Rust relay
 * + Postgres/Redis/MinIO stack. Built on quartz's own relay-server code.
 *
 * It extends [FullAuthPolicy], so it runs the full NIP-42 handshake and then layers Buzz's
 * two authorization rules on top:
 *
 * 1. **Membership.** Only a [members] key (the team) may publish or read. A completed NIP-42
 *    auth alone is not enough — auth proves identity; membership is the authorization.
 * 2. **NIP-OA virtual membership.** An un-enrolled *agent* key is granted membership **for its
 *    connection** when its NIP-42 auth event carries an owner-signed `auth` tag
 *    ([com.vitorpamplona.quartz.buzz.oaOwnerAttestation.OwnerAttestation]) whose owner is a
 *    member and whose signature authorizes that agent — the same primitive Block's relay uses
 *    to make agents first-class without enrolling every key. The grant lives only as long as
 *    the connection (a fresh policy is built per connection), so revoking the owner's
 *    membership or dropping the socket revokes the agent.
 *
 * What this deliberately does NOT do (out of scope for a job channel; needs a relay that
 * *emits* signed events, which a policy cannot): relay-signed NIP-29 metadata (39000-39003),
 * relay-assigned DM UUIDs, and workflow execution (46xxx). See
 * `cli/plans/2026-07-25-buzz-agent-support-channel.md`.
 *
 * [allowedKinds], when non-null, further restricts every author to those kinds (e.g. lock a
 * channel down to the Buzz job + chat + reaction kinds); null accepts any kind.
 */
open class BuzzMembershipPolicy(
    relay: NormalizedRelayUrl,
    private val members: Set<HexKey>,
    private val allowedKinds: Set<Int>? = null,
) : FullAuthPolicy(relay) {
    /** Agent keys granted virtual membership on THIS connection via a valid NIP-OA `auth` tag. */
    private val authorizedAgents = mutableSetOf<HexKey>()

    /**
     * Runs after the NIP-42 proof checks out (see [FullAuthPolicy.authorize]). If the auth event
     * carries an owner-signed attestation authorizing this agent, and the owner is a member,
     * remember the agent as a member for this connection. We never throw here — a missing or
     * invalid attestation just means the key is authenticated but not (yet) authorized, which the
     * membership gate below handles.
     */
    override suspend fun authorize(event: RelayAuthEvent) {
        val attestation = event.tags.firstNotNullOfOrNull(AuthTag::parse) ?: return
        if (attestation.ownerPubKey in members && attestation.verify(event.pubKey)) {
            authorizedAgents.add(event.pubKey)
        }
    }

    private fun isMember(pubKey: HexKey): Boolean = pubKey in members || pubKey in authorizedAgents

    /**
     * The read gate as a rejection reason, or null to allow. Crucially, an *unauthenticated*
     * connection is told `auth-required` (not `restricted`) so the client runs the NIP-42
     * handshake and retries — only an authenticated non-member is `restricted`.
     */
    private fun readGate(): String? =
        when {
            authenticatedUsers.isEmpty() -> "auth-required: authenticate before reading this workspace"
            authenticatedUsers.any(::isMember) -> null
            else -> "restricted: not a workspace member"
        }

    override fun accept(cmd: EventCmd): PolicyResult<EventCmd> {
        val author = cmd.event.pubKey
        if (author !in authenticatedUsers) {
            return PolicyResult.Rejected("auth-required: authenticate before publishing")
        }
        if (!isMember(author)) {
            return PolicyResult.Rejected("restricted: not a workspace member")
        }
        allowedKinds?.let {
            if (cmd.event.kind !in it) return PolicyResult.Rejected("restricted: kind ${cmd.event.kind} is not accepted on this workspace")
        }
        return PolicyResult.Accepted(cmd)
    }

    override fun accept(cmd: ReqCmd): PolicyResult<ReqCmd> = readGate()?.let { PolicyResult.Rejected(it) } ?: PolicyResult.Accepted(cmd)

    override fun accept(cmd: CountCmd): PolicyResult<CountCmd> = readGate()?.let { PolicyResult.Rejected(it) } ?: PolicyResult.Accepted(cmd)
}
