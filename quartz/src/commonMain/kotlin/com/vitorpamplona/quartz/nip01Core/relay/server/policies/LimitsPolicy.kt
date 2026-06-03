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
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.MachineReadablePrefix
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CountCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.EventCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.server.PolicyResult
import com.vitorpamplona.quartz.nip01Core.relay.server.RelayLimits

/**
 * Enforces the per-command fields of a [RelayLimits]: rejects EVENTs whose
 * content/tags/created_at fall outside the configured bounds, rejects REQ/COUNT
 * with too many filters or an over-long subscription id, and clamps each
 * filter's `limit` to [RelayLimits.maxLimit] (substituting
 * [RelayLimits.defaultLimit] when the client gives none).
 *
 * Compose it ahead of your own policy so requests are clamped/rejected before
 * the application logic runs — `LimitsPolicy(limits) + myPolicy`. The server
 * classes do this automatically when you pass them `limits`. The session-level
 * caps ([RelayLimits.maxMessageLength], [RelayLimits.maxSubscriptions]) are
 * enforced through the [acceptMessage] / [acceptSubscription] policy hooks, so
 * everything limit-related composes uniformly across a [PolicyStack].
 *
 * Each check is expressed as a "rejection reason or null" helper; the command
 * `accept` overloads just wrap a non-null reason in [PolicyResult.Rejected].
 */
class LimitsPolicy(
    private val limits: RelayLimits,
) : PassThroughPolicy() {
    override fun acceptMessage(message: String): String? {
        val max = limits.maxMessageLength ?: return null
        return if (message.length > max) invalid("message too large (max $max)") else null
    }

    override fun acceptSubscription(
        subId: String,
        openSubscriptions: Int,
    ): String? {
        val max = limits.maxSubscriptions ?: return null
        return if (openSubscriptions >= max) {
            MachineReadablePrefix.RATE_LIMITED.format("too many concurrent subscriptions (max $max)")
        } else {
            null
        }
    }

    override fun accept(cmd: EventCmd): PolicyResult<EventCmd> = eventRejection(cmd.event)?.let { PolicyResult.Rejected(it) } ?: PolicyResult.Accepted(cmd)

    override fun accept(cmd: ReqCmd): PolicyResult<ReqCmd> {
        subscriptionRejection(cmd.subId, cmd.filters)?.let { return PolicyResult.Rejected(it) }
        val clamped = clampLimits(cmd.filters)
        return PolicyResult.Accepted(if (clamped === cmd.filters) cmd else ReqCmd(cmd.subId, clamped))
    }

    override fun accept(cmd: CountCmd): PolicyResult<CountCmd> {
        subscriptionRejection(cmd.queryId, cmd.filters)?.let { return PolicyResult.Rejected(it) }
        val clamped = clampLimits(cmd.filters)
        return PolicyResult.Accepted(if (clamped === cmd.filters) cmd else CountCmd(cmd.queryId, clamped))
    }

    /** The reason an EVENT violates a limit, or null when it's within bounds. */
    private fun eventRejection(event: Event): String? {
        limits.maxContentLength?.let { if (event.content.length > it) return invalid("content too large (max $it)") }
        limits.maxEventTags?.let { if (event.tags.size > it) return invalid("too many tags (max $it)") }
        limits.createdAtLowerLimit?.let { if (event.createdAt < it) return invalid("created_at is before the relay's lower limit") }
        limits.createdAtUpperLimit?.let { if (event.createdAt > it) return invalid("created_at is after the relay's upper limit") }
        return null
    }

    /** The reason a REQ/COUNT violates a limit, or null when it's within bounds. */
    private fun subscriptionRejection(
        subId: String,
        filters: List<Filter>,
    ): String? {
        limits.maxSubidLength?.let { if (subId.length > it) return invalid("subscription id too long (max $it)") }
        limits.maxFilters?.let { if (filters.size > it) return invalid("too many filters (max $it)") }
        return null
    }

    private fun invalid(message: String): String = MachineReadablePrefix.INVALID.format(message)

    /** Returns the same list reference (no allocation) when no filter needs clamping. */
    private fun clampLimits(filters: List<Filter>): List<Filter> {
        if (limits.maxLimit == null && limits.defaultLimit == null) return filters
        if (filters.none { targetLimit(it.limit) != it.limit }) return filters
        return filters.map { it.copy(limit = targetLimit(it.limit)) }
    }

    private fun targetLimit(current: Int?): Int? =
        when {
            current != null && limits.maxLimit != null && current > limits.maxLimit -> limits.maxLimit
            current == null && limits.defaultLimit != null -> limits.defaultLimit
            else -> current
        }
}
