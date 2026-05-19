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
package com.vitorpamplona.amethyst.commons.privacy

import com.vitorpamplona.amethyst.commons.i2p.I2pRelayEvaluation
import com.vitorpamplona.amethyst.commons.tor.TorRelayEvaluation
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.isI2p
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.isOnion

// Per-relay transport pick. Onion always wins for Tor, I2P always wins for I2P
// (matching PrivacyRouter's hostname pin). For non-hidden hosts, prefer I2P if
// both transports want the relay — arbitrary tiebreak documented here so the
// behavior is testable.
class PrivacyRelayEvaluation(
    private val tor: TorRelayEvaluation,
    private val i2p: I2pRelayEvaluation,
) {
    fun transport(relay: NormalizedRelayUrl): PrivacyTransport {
        if (relay.isOnion()) return if (tor.useTor(relay)) PrivacyTransport.TOR else PrivacyTransport.DIRECT
        if (relay.isI2p()) return if (i2p.useI2p(relay)) PrivacyTransport.I2P else PrivacyTransport.DIRECT
        return when {
            i2p.useI2p(relay) -> PrivacyTransport.I2P
            tor.useTor(relay) -> PrivacyTransport.TOR
            else -> PrivacyTransport.DIRECT
        }
    }
}
