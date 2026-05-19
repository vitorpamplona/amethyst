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
package com.vitorpamplona.amethyst.model.privacyOptions

import com.vitorpamplona.amethyst.commons.privacy.FeatureRole
import com.vitorpamplona.amethyst.commons.privacy.PrivacyRoute
import com.vitorpamplona.amethyst.commons.privacy.PrivacyRouter
import com.vitorpamplona.amethyst.commons.privacy.PrivacySettings
import com.vitorpamplona.amethyst.model.preferences.PrivacySharedPreferences
import com.vitorpamplona.amethyst.ui.i2p.I2pSettingsFlow
import com.vitorpamplona.amethyst.ui.tor.TorSettingsFlow

// Read-side facade over PrivacyRouter that snapshot-reads from the three live
// pref flows (Tor, I2P, preferred clearnet transport). Exists so callers don't
// have to know how to assemble a PrivacySettings every time — and so the daemon
// work and HTTP routing rewrite that come next have one stable seam to consult.
class PrivacyRoutingFlow(
    val tor: TorSettingsFlow,
    val i2p: I2pSettingsFlow,
    val privacy: PrivacySharedPreferences,
) {
    fun routeFor(
        role: FeatureRole,
        url: String,
    ): PrivacyRoute = PrivacyRouter.route(url, role, snapshot())

    fun snapshot(): PrivacySettings =
        PrivacySettings(
            tor = tor.toSettings(),
            i2p = i2p.toSettings(),
            preferredClearnetTransport = privacy.preferredClearnetTransport.value,
        )
}
