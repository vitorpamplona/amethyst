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

import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.MachineReadablePrefix
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
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
 */
class LimitsPolicy(
    private val limits: RelayLimits,
) : PassThroughPolicy() {
    override fun acceptMessage(message: String): String? {
        val max = limits.maxMessageLength ?: return null
        return if (message.length > max) MachineReadablePrefix.INVALID.format("message too large (max $max)") else null
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

    override fun accept(cmd: EventCmd): PolicyResult<EventCmd> {
        val event = cmd.event
        limits.maxContentLength?.let {
            if (event.content.length > it) return reject("content too large (max $it)")
        }
        limits.maxEventTags?.let {
            if (event.tags.size > it) return reject("too many tags (max $it)")
        }
        limits.createdAtLowerLimit?.let {
            if (event.createdAt < it) return reject("created_at is before the relay's lower limit")
        }
        limits.createdAtUpperLimit?.let {
            if (event.createdAt > it) return reject("created_at is after the relay's upper limit")
        }
        return PolicyResult.Accepted(cmd)
    }

    override fun accept(cmd: ReqCmd): PolicyResult<ReqCmd> {
        rejectSubId<ReqCmd>(cmd.subId)?.let { return it }
        rejectFilterCount<ReqCmd>(cmd.filters)?.let { return it }
        val clamped = clampLimits(cmd.filters)
        return PolicyResult.Accepted(if (clamped === cmd.filters) cmd else ReqCmd(cmd.subId, clamped))
    }

    override fun accept(cmd: CountCmd): PolicyResult<CountCmd> {
        rejectSubId<CountCmd>(cmd.queryId)?.let { return it }
        rejectFilterCount<CountCmd>(cmd.filters)?.let { return it }
        val clamped = clampLimits(cmd.filters)
        return PolicyResult.Accepted(if (clamped === cmd.filters) cmd else CountCmd(cmd.queryId, clamped))
    }

    private fun <T : Command> reject(message: String): PolicyResult<T> = PolicyResult.Rejected(MachineReadablePrefix.INVALID.format(message))

    private fun <T : Command> rejectSubId(subId: String): PolicyResult<T>? {
        val max = limits.maxSubidLength ?: return null
        return if (subId.length > max) reject("subscription id too long (max $max)") else null
    }

    private fun <T : Command> rejectFilterCount(filters: List<Filter>): PolicyResult<T>? {
        val max = limits.maxFilters ?: return null
        return if (filters.size > max) reject("too many filters (max $max)") else null
    }

    /** Returns the same list reference when nothing changes, so callers can skip rebuilding the command. */
    private fun clampLimits(filters: List<Filter>): List<Filter> {
        if (limits.maxLimit == null && limits.defaultLimit == null) return filters
        var changed = false
        val out =
            filters.map { f ->
                val target = targetLimit(f.limit)
                if (target != f.limit) {
                    changed = true
                    f.copy(limit = target)
                } else {
                    f
                }
            }
        return if (changed) out else filters
    }

    private fun targetLimit(current: Int?): Int? =
        when {
            current != null && limits.maxLimit != null && current > limits.maxLimit -> limits.maxLimit
            current == null && limits.defaultLimit != null -> limits.defaultLimit
            else -> current
        }
}
