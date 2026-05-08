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

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.EventCmd
import com.vitorpamplona.quartz.nip01Core.relay.server.PolicyResult

/**
 * Operator-controlled author allow/deny list. Mirrors nostr-rs-relay's
 * `[authorization].pubkey_whitelist` / `pubkey_blacklist`.
 *
 *  - [allow] non-empty: only events from listed pubkeys are accepted.
 *    This is the "private relay" mode.
 *  - [deny] non-empty: events from listed pubkeys are rejected.
 *  - Empty lists are no-op pass-through.
 *  - When both are set, allow is checked first.
 *
 * Pubkeys are matched case-insensitively (lowercased on entry).
 */
class PubkeyAllowDenyPolicy(
    allow: Set<HexKey> = emptySet(),
    deny: Set<HexKey> = emptySet(),
) : PassThroughPolicy() {
    private val allow = allow.mapTo(HashSet()) { it.lowercase() }
    private val deny = deny.mapTo(HashSet()) { it.lowercase() }

    override fun accept(cmd: EventCmd): PolicyResult<EventCmd> {
        val pk = cmd.event.pubKey.lowercase()
        if (allow.isNotEmpty() && pk !in allow) {
            return PolicyResult.Rejected("blocked: pubkey not on allow list")
        }
        if (pk in deny) {
            return PolicyResult.Rejected("blocked: pubkey is denied")
        }
        return PolicyResult.Accepted(cmd)
    }
}
