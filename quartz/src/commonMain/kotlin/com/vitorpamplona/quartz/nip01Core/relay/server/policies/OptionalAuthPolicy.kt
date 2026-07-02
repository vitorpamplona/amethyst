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

import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CountCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.EventCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

/**
 * Runs the full NIP-42 challenge/verify handshake but never *requires* it.
 *
 * Like [FullAuthPolicy], [onConnect] emits the AUTH challenge and [accept]
 * (AuthCmd) validates a returned event (expiration, freshness, challenge match,
 * relay match), recording verified pubkeys into the engine-owned connection
 * scope. Unlike [FullAuthPolicy], EVENT, REQ, and COUNT are **always accepted** —
 * a client that ignores the challenge and never authenticates keeps working.
 *
 * Use this when you want the relay to *advertise* authentication (so clients that
 * do support NIP-42 can identify themselves, and downstream policies can gate or
 * rewrite on the caller's identity via [authenticatedUsers]) without locking out
 * clients that don't. It is the middle ground between [EmptyPolicy] (no challenge
 * at all) and [FullAuthPolicy] (challenge required for every command).
 *
 * Because it subclasses [FullAuthPolicy], the [authorize] hook and the
 * per-connection [authenticatedUsers] set behave identically; only the gating on
 * EVENT/REQ/COUNT is relaxed. Subclasses can still tighten specific commands
 * (e.g. require auth for kind 4 only) by overriding the relevant [accept] and
 * reading [authenticatedUsers].
 */
open class OptionalAuthPolicy(
    relay: NormalizedRelayUrl,
) : FullAuthPolicy(relay) {
    override fun accept(cmd: EventCmd): PolicyResult<EventCmd> = PolicyResult.Accepted(cmd)

    override fun accept(cmd: ReqCmd): PolicyResult<ReqCmd> = PolicyResult.Accepted(cmd)

    override fun accept(cmd: CountCmd): PolicyResult<CountCmd> = PolicyResult.Accepted(cmd)
}
