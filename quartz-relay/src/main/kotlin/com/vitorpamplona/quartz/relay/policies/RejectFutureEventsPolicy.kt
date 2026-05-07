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
package com.vitorpamplona.quartz.relay.policies

import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.EventCmd
import com.vitorpamplona.quartz.nip01Core.relay.server.PolicyResult
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Rejects events whose `created_at` is more than [maxFutureSeconds]
 * seconds in the future relative to the relay's clock. Mirrors
 * nostr-rs-relay's `[options].reject_future_seconds`.
 *
 * This catches both clock-skew accidents and intentional far-future
 * timestamps used to push events to the top of newest-first feeds.
 *
 * The current time is read from [TimeUtils.now] (epoch seconds), the
 * same source the [com.vitorpamplona.quartz.nip40Expiration.isExpired]
 * check uses, so the relay's "future" and "expired" decisions agree.
 */
class RejectFutureEventsPolicy(
    val maxFutureSeconds: Int,
    private val now: () -> Long = { TimeUtils.now() },
) : PassThroughPolicy() {
    init {
        require(maxFutureSeconds >= 0) { "maxFutureSeconds must be >= 0, got $maxFutureSeconds" }
    }

    override fun accept(cmd: EventCmd): PolicyResult<EventCmd> {
        val skew = cmd.event.createdAt - now()
        return if (skew > maxFutureSeconds) {
            PolicyResult.Rejected("invalid: created_at is $skew seconds in the future (max $maxFutureSeconds)")
        } else {
            PolicyResult.Accepted(cmd)
        }
    }
}
