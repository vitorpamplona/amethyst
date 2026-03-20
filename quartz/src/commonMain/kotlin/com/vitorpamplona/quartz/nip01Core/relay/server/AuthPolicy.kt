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
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter

/**
 * Policy that controls authentication requirements for relay commands.
 *
 * Each trigger point receives the set of pubkeys that have authenticated on the
 * current session, allowing the policy to make per-user decisions. Implementations
 * can range from fully open (no auth) to fine-grained per-kind or per-user rules.
 */
interface AuthPolicy {
    /**
     * Evaluates whether an incoming EVENT command should be accepted.
     *
     * @param event The event the client wants to publish.
     * @param authedPubkeys Pubkeys authenticated on this session (empty if none).
     * @return [Accepted] to store the event, or [Rejected] with a reason string.
     */
    fun acceptEvent(
        event: Event,
        authedPubkeys: Set<HexKey>,
    ): PolicyResult

    /**
     * Evaluates a REQ command, optionally rewriting the filter list.
     *
     * The policy may narrow filters to match what the authenticated user is
     * allowed to see (e.g., restrict to their own DMs), or reject the
     * subscription entirely.
     *
     * @param filters The filters from the REQ command.
     * @param authedPubkeys Pubkeys authenticated on this session.
     * @return [Accepted] with an optional replacement filter list, or [Rejected].
     */
    fun acceptReq(
        filters: List<Filter>,
        authedPubkeys: Set<HexKey>,
    ): ReqPolicyResult

    /**
     * Evaluates a COUNT command.
     *
     * @param filters The filters from the COUNT command.
     * @param authedPubkeys Pubkeys authenticated on this session.
     * @return [Accepted] to allow counting, or [Rejected] with a reason.
     */
    fun acceptCount(
        filters: List<Filter>,
        authedPubkeys: Set<HexKey>,
    ): PolicyResult

    /**
     * Filters a live event before it is forwarded to a subscriber.
     *
     * Called for each event that matches a subscription's filters. Return
     * true to deliver the event, false to suppress it for this session.
     *
     * @param event The event about to be sent.
     * @param authedPubkeys Pubkeys authenticated on this session.
     */
    fun canSendToSession(
        event: Event,
        authedPubkeys: Set<HexKey>,
    ): Boolean = true
}

sealed interface PolicyResult {
    data object Accepted : PolicyResult

    data class Rejected(
        val reason: String,
    ) : PolicyResult
}

sealed interface ReqPolicyResult {
    data class Accepted(
        val filters: List<Filter>? = null,
    ) : ReqPolicyResult

    data class Rejected(
        val reason: String,
    ) : ReqPolicyResult
}

/**
 * Allows all commands without authentication. This is the default policy.
 */
class OpenPolicy : AuthPolicy {
    override fun acceptEvent(
        event: Event,
        authedPubkeys: Set<HexKey>,
    ) = PolicyResult.Accepted

    override fun acceptReq(
        filters: List<Filter>,
        authedPubkeys: Set<HexKey>,
    ) = ReqPolicyResult.Accepted()

    override fun acceptCount(
        filters: List<Filter>,
        authedPubkeys: Set<HexKey>,
    ) = PolicyResult.Accepted

    override fun canSendToSession(
        event: Event,
        authedPubkeys: Set<HexKey>,
    ) = true
}

/**
 * Requires authentication for all EVENT, REQ, and COUNT commands.
 * Replicates the previous `requireAuth = true` behavior.
 */
class RequireAuthPolicy : AuthPolicy {
    override fun acceptEvent(
        event: Event,
        authedPubkeys: Set<HexKey>,
    ) = if (authedPubkeys.isNotEmpty()) {
        PolicyResult.Accepted
    } else {
        PolicyResult.Rejected("auth-required: this relay requires authentication")
    }

    override fun acceptReq(
        filters: List<Filter>,
        authedPubkeys: Set<HexKey>,
    ) = if (authedPubkeys.isNotEmpty()) {
        ReqPolicyResult.Accepted()
    } else {
        ReqPolicyResult.Rejected("auth-required: this relay requires authentication")
    }

    override fun acceptCount(
        filters: List<Filter>,
        authedPubkeys: Set<HexKey>,
    ) = if (authedPubkeys.isNotEmpty()) {
        PolicyResult.Accepted
    } else {
        PolicyResult.Rejected("auth-required: this relay requires authentication")
    }

    override fun canSendToSession(
        event: Event,
        authedPubkeys: Set<HexKey>,
    ) = authedPubkeys.isNotEmpty()
}
