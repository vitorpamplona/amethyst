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
package com.vitorpamplona.amethyst.commons.model.buzz

import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * Who this client takes for an agent in a Buzz DM, the input Quartz's
 * [com.vitorpamplona.quartz.buzz.dm.BuzzDmRules] needs: agents neither count toward a DM's people
 * cap nor fork it. The relay decides from its own records; this reads what we have cached.
 */
object BuzzDmAgents {
    /**
     * Best local guess at whether the relay counts [pubkey] as an agent: it has a NIP-OA owner in
     * its profile, or a Buzz agent profile (kind 10100). A miss only means the cap is applied as if
     * they were a person.
     */
    fun isLikelyAgent(pubkey: HexKey): Boolean =
        LocalCache
            .getUserIfExists(pubkey)
            ?.metadataOrNull()
            ?.flow
            ?.value
            ?.nipOaOwner != null || BuzzIdentityNames.hasCachedAgentProfile(pubkey)
}
