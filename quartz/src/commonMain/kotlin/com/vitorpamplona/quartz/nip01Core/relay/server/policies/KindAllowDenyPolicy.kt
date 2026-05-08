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

import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.EventCmd
import com.vitorpamplona.quartz.nip01Core.relay.server.PolicyResult

/**
 * Operator-controlled kind allow/deny list. Mirrors nostr-rs-relay's
 * `[authorization].kind_whitelist` / `kind_blacklist`.
 *
 *  - When [allow] is non-empty, only events whose kind is in [allow]
 *    are accepted; everything else is rejected.
 *  - When [deny] is non-empty, events whose kind is in [deny] are
 *    rejected.
 *  - Both lists may be empty (no-op pass-through).
 *  - When both are set, allow is checked first (deny inside allow is
 *    still denied, matching nostr-rs-relay's precedence).
 */
class KindAllowDenyPolicy(
    val allow: Set<Int> = emptySet(),
    val deny: Set<Int> = emptySet(),
) : PassThroughPolicy() {
    override fun accept(cmd: EventCmd): PolicyResult<EventCmd> {
        val k = cmd.event.kind
        if (allow.isNotEmpty() && k !in allow) {
            return PolicyResult.Rejected("blocked: kind $k not allowed")
        }
        if (k in deny) {
            return PolicyResult.Rejected("blocked: kind $k denied")
        }
        return PolicyResult.Accepted(cmd)
    }
}
