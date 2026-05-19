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
package com.vitorpamplona.amethyst.commons.i2p

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.isI2p
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.isLocalHost

class I2pRelayEvaluation(
    val i2pSettings: I2pRelaySettings,
    val trustedRelayList: Set<NormalizedRelayUrl>,
    val dmRelayList: Set<NormalizedRelayUrl>,
) {
    fun useI2p(relay: NormalizedRelayUrl): Boolean =
        if (i2pSettings.i2pType == I2pType.OFF) {
            false
        } else {
            if (relay.isLocalHost()) {
                false
            } else if (relay.isI2p()) {
                i2pSettings.i2pRelaysViaI2p
            } else if (relay in dmRelayList) {
                i2pSettings.dmRelaysViaI2p
            } else if (relay in trustedRelayList) {
                i2pSettings.trustedRelaysViaI2p
            } else {
                i2pSettings.newRelaysViaI2p
            }
        }
}
