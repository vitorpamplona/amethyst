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
package com.vitorpamplona.quartz.nip01Core.relay.server.backend

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.AuthScopedPolicy
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.IRelayPolicy

/**
 * The per-connection context handed to an [EventSource] (and a [SessionBackend])
 * on every REQ/COUNT. It tells the data plane *who* is asking, so a single
 * shared source can tailor its answer to the caller without smuggling state in
 * through a side channel.
 *
 * This is what makes NIP-42 useful on a non-storage relay: the engine already
 * knows the authenticated pubkey(s) (the connection's [IRelayPolicy] records
 * them), and [RequestContext] is the path that carries them to the code that
 * actually produces the events. With it, the same `EventSource` instance can
 * serve:
 *
 *  - **caller-relative results** — trust/relevance scored from
 *    [authenticatedUsers]'s perspective ("for-you" feeds, follow-aware search);
 *  - **restricted content** — a pubkey's DMs/private events returned only to
 *    that authenticated pubkey;
 *  - **paid / allow-listed** sets that depend on the authenticated identity;
 *  - **per-connection** quotas/tenancy keyed by [connectionId].
 *
 * For per-connection application state beyond the pubkey (e.g. a backend session
 * token minted in [com.vitorpamplona.quartz.nip01Core.relay.server.policies.FullAuthPolicy.authorize]),
 * downcast [policy] to your own [IRelayPolicy] subclass and read a typed field —
 * the policy instance is itself per-connection, so it is the natural typed bag.
 */
interface RequestContext {
    /**
     * Stable, process-unique id of the connection this request arrived on
     * (the owning [com.vitorpamplona.quartz.nip01Core.relay.server.RelaySession.id]).
     * Use it to key per-connection state a shared source keeps on the side.
     */
    val connectionId: Long

    /** The connection's policy, for typed access to per-connection state. */
    val policy: IRelayPolicy

    /**
     * The pubkeys that have authenticated on this connection via NIP-42, read
     * live from [policy]. Empty when the connection is unauthenticated or the
     * policy does not track auth (i.e. is not an [AuthScopedPolicy]). This
     * accessor encapsulates that downcast so a source needn't repeat it.
     */
    val authenticatedUsers: Set<HexKey> get() = (policy as? AuthScopedPolicy)?.authenticatedUsers ?: emptySet()
}
