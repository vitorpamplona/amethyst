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
import com.vitorpamplona.amethyst.commons.tor.TorSettings
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.HiddenServiceKind
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer

// Decides how a request is routed.
//
// Hidden-service hostnames hard-pin to their matching network — .onion needs
// Tor, .i2p needs I2P — and fail closed (Blocked) when that daemon is OFF
// rather than leak the request over the clearnet.
//
// For clearnet hostnames there is exactly one active privacy transport at a
// time (PrivacySettings.preferredClearnetTransport). Per-feature toggles on
// that transport's own settings (TorSettings.*ViaTor or I2pSettings.*ViaI2p)
// decide whether this particular request is routed through it; otherwise the
// request goes Direct.
object PrivacyRouter {
    fun route(
        url: String,
        role: FeatureRole,
        settings: PrivacySettings,
    ): PrivacyRoute =
        when (RelayUrlNormalizer.classifyHidden(url)) {
            HiddenServiceKind.LOCALHOST -> PrivacyRoute.Direct
            HiddenServiceKind.ONION ->
                if (settings.torAvailable) PrivacyRoute.Tor else PrivacyRoute.Blocked(BlockReason.ONION_REQUIRES_TOR)
            HiddenServiceKind.I2P ->
                if (settings.i2pAvailable) PrivacyRoute.I2p else PrivacyRoute.Blocked(BlockReason.I2P_REQUIRES_I2P)
            HiddenServiceKind.CLEARNET -> clearnet(role, settings)
        }

    private fun clearnet(
        role: FeatureRole,
        settings: PrivacySettings,
    ): PrivacyRoute =
        when (settings.preferredClearnetTransport) {
            PrivacyTransport.TOR ->
                if (settings.torAvailable && torEnables(role, settings.tor)) PrivacyRoute.Tor else PrivacyRoute.Direct
            PrivacyTransport.I2P ->
                if (settings.i2pAvailable && i2pEnables(role, settings.i2p)) PrivacyRoute.I2p else PrivacyRoute.Direct
            PrivacyTransport.DIRECT -> PrivacyRoute.Direct
        }

    private fun torEnables(
        role: FeatureRole,
        tor: TorSettings,
    ): Boolean =
        when (role) {
            FeatureRole.IMAGE -> tor.imagesViaTor
            FeatureRole.VIDEO -> tor.videosViaTor
            FeatureRole.URL_PREVIEW -> tor.urlPreviewsViaTor
            FeatureRole.PROFILE_PIC -> tor.profilePicsViaTor
            FeatureRole.NIP05 -> tor.nip05VerificationsViaTor
            FeatureRole.MONEY -> tor.moneyOperationsViaTor
            FeatureRole.UPLOAD -> tor.mediaUploadsViaTor
        }

    private fun i2pEnables(
        role: FeatureRole,
        i2p: I2pSettings,
    ): Boolean =
        when (role) {
            FeatureRole.IMAGE -> i2p.imagesViaI2p
            FeatureRole.VIDEO -> i2p.videosViaI2p
            FeatureRole.URL_PREVIEW -> i2p.urlPreviewsViaI2p
            FeatureRole.PROFILE_PIC -> i2p.profilePicsViaI2p
            FeatureRole.NIP05 -> i2p.nip05VerificationsViaI2p
            FeatureRole.MONEY -> i2p.moneyOperationsViaI2p
            FeatureRole.UPLOAD -> i2p.mediaUploadsViaI2p
        }
}
