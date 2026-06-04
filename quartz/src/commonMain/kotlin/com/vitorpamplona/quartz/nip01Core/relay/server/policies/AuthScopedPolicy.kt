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

/**
 * Opt-in mixin for the policies that actually track NIP-42 authentication, so
 * the auth concept stays out of the universal [IRelayPolicy]. A relay that does
 * no auth never implements this; only [FullAuthPolicy] (and [PolicyStack], which
 * unions any auth-tracking members) do.
 *
 * The engine surfaces it to the data plane through
 * [com.vitorpamplona.quartz.nip01Core.relay.server.backend.RequestContext.authenticatedUsers],
 * which downcasts the connection's policy to this interface — so a source sees
 * the authenticated pubkey(s) without the base policy interface knowing about
 * NIP-42.
 */
interface AuthScopedPolicy {
    /** The pubkeys that have authenticated on this connection via NIP-42. */
    val authenticatedUsers: Set<HexKey>
}
