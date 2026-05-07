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
package com.vitorpamplona.quartz.relay.admin

import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.EventCmd
import com.vitorpamplona.quartz.nip01Core.relay.server.PolicyResult
import com.vitorpamplona.quartz.relay.policies.PassThroughPolicy

/**
 * Reads the live [BanStore] on every EVENT and rejects events that
 * violate any of: banned-event-id, banned-pubkey, missing from a
 * non-empty allow list, or kind disallowed / not in the kind allow
 * list.
 *
 * This is the runtime-mutable counterpart of the static
 * [com.vitorpamplona.quartz.relay.policies.KindAllowDenyPolicy] +
 * [com.vitorpamplona.quartz.relay.policies.PubkeyAllowDenyPolicy] —
 * both sets compose: the event must clear both layers. NIP-86 admin
 * RPC mutations land here; the static policies stay frozen at
 * boot-time config values.
 */
class DynamicBanPolicy(
    val banStore: BanStore,
) : PassThroughPolicy() {
    override fun accept(cmd: EventCmd): PolicyResult<EventCmd> {
        val ev = cmd.event
        if (banStore.isBannedEvent(ev.id)) {
            return PolicyResult.Rejected("blocked: event id is banned")
        }
        if (banStore.isBanned(ev.pubKey)) {
            return PolicyResult.Rejected("blocked: pubkey is banned")
        }
        if (banStore.hasAllowList() && !banStore.isAllowedPubkey(ev.pubKey)) {
            return PolicyResult.Rejected("blocked: pubkey is not on the allow list")
        }
        if (!banStore.isKindAllowed(ev.kind)) {
            return PolicyResult.Rejected("blocked: kind ${ev.kind} not allowed")
        }
        return PolicyResult.Accepted(cmd)
    }
}
