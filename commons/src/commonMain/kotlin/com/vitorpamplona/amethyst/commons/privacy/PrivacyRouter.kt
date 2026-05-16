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

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.HiddenServiceKind
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer

// Decides which PrivacyTransport carries a request. Hidden-service hostnames
// hard-pin (onion → Tor, i2p → I2P) regardless of the per-feature picker, since
// no other transport can reach them. If the pinned or chosen transport is OFF,
// downgrade to DIRECT — the caller decides whether to make the request anyway.
object PrivacyRouter {
    fun route(
        url: String,
        role: FeatureRole,
        settings: PrivacySettings,
    ): PrivacyTransport =
        when (RelayUrlNormalizer.classifyHidden(url)) {
            HiddenServiceKind.LOCALHOST -> PrivacyTransport.DIRECT
            HiddenServiceKind.ONION -> if (settings.torAvailable) PrivacyTransport.TOR else PrivacyTransport.DIRECT
            HiddenServiceKind.I2P -> if (settings.i2pAvailable) PrivacyTransport.I2P else PrivacyTransport.DIRECT
            HiddenServiceKind.CLEARNET -> resolve(settings.features.choiceFor(role), settings)
        }

    private fun resolve(
        choice: TransportChoice,
        settings: PrivacySettings,
    ): PrivacyTransport =
        when (choice) {
            TransportChoice.DIRECT -> PrivacyTransport.DIRECT
            TransportChoice.TOR -> if (settings.torAvailable) PrivacyTransport.TOR else PrivacyTransport.DIRECT
            TransportChoice.I2P -> if (settings.i2pAvailable) PrivacyTransport.I2P else PrivacyTransport.DIRECT
        }
}
