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

import com.vitorpamplona.amethyst.commons.i2p.I2pSettings
import com.vitorpamplona.amethyst.commons.i2p.I2pType
import com.vitorpamplona.amethyst.commons.tor.TorSettings
import com.vitorpamplona.amethyst.commons.tor.TorType

// Aggregate of all privacy configuration. Both Tor and I2P daemons can run
// side-by-side so hidden services on each network stay reachable, but only one
// transport carries clearnet at a time: preferredClearnetTransport picks it.
// The per-feature booleans on each TorSettings / I2pSettings only take effect
// for the preferred transport.
data class PrivacySettings(
    val tor: TorSettings = TorSettings(),
    val i2p: I2pSettings = I2pSettings(),
    val preferredClearnetTransport: PrivacyTransport = PrivacyTransport.DIRECT,
) {
    val torAvailable: Boolean get() = tor.torType != TorType.OFF
    val i2pAvailable: Boolean get() = i2p.i2pType != I2pType.OFF
}
