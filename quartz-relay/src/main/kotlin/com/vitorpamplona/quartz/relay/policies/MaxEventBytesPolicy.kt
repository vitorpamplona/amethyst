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

import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.EventCmd
import com.vitorpamplona.quartz.nip01Core.relay.server.PolicyResult

/**
 * Rejects events whose canonical JSON byte size exceeds [maxBytes].
 * Mirrors nostr-rs-relay's `[limits].max_event_bytes`.
 *
 * Note: this measures the size of the SERVER-side re-serialised event
 * (via [OptimizedJsonMapper.toJson]), which is byte-equivalent to the
 * canonical NIP-01 form a well-behaved client would have sent. It does
 * NOT enforce `[limits].max_ws_message_bytes` — that one belongs at the
 * WebSocket frame layer (Ktor `WebSockets { maxFrameSize = ... }` for
 * [com.vitorpamplona.quartz.relay.LocalRelayServer]) because the policy
 * layer never sees the raw frame. Both limits are enforced together
 * when the operator sets them in the config.
 */
class MaxEventBytesPolicy(
    val maxBytes: Int,
) : PassThroughPolicy() {
    init {
        require(maxBytes > 0) { "maxBytes must be > 0, got $maxBytes" }
    }

    override fun accept(cmd: EventCmd): PolicyResult<EventCmd> {
        val size = OptimizedJsonMapper.toJson(cmd.event).length
        return if (size > maxBytes) {
            PolicyResult.Rejected("invalid: event size $size exceeds limit of $maxBytes bytes")
        } else {
            PolicyResult.Accepted(cmd)
        }
    }
}
