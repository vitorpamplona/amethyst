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
import kotlin.test.Test
import kotlin.test.assertEquals

class PrivacyRouterTest {
    private val clearnet = "wss://relay.damus.io/"
    private val onion = "wss://abc123.onion/"
    private val i2p = "wss://abc.i2p/"
    private val localhost = "ws://127.0.0.1:8080/"

    private fun settings(
        torOn: Boolean = false,
        i2pOn: Boolean = false,
        preferred: PrivacyTransport = PrivacyTransport.DIRECT,
        tor: TorSettings = TorSettings(torType = if (torOn) TorType.INTERNAL else TorType.OFF),
        i2p: I2pSettings = I2pSettings(i2pType = if (i2pOn) I2pType.EXTERNAL else I2pType.OFF),
    ) = PrivacySettings(
        tor = tor,
        i2p = i2p,
        preferredClearnetTransport = preferred,
    )

    @Test
    fun localhost_alwaysDirect() {
        assertEquals(
            PrivacyRoute.Direct,
            PrivacyRouter.route(localhost, FeatureRole.IMAGE, settings(torOn = true, i2pOn = true, preferred = PrivacyTransport.TOR)),
        )
    }

    @Test
    fun onion_pinsToTor_whenAvailable() {
        assertEquals(PrivacyRoute.Tor, PrivacyRouter.route(onion, FeatureRole.IMAGE, settings(torOn = true)))
    }

    @Test
    fun onion_blocksWhenTorOff() {
        assertEquals(
            PrivacyRoute.Blocked(BlockReason.ONION_REQUIRES_TOR),
            PrivacyRouter.route(onion, FeatureRole.IMAGE, settings(torOn = false, i2pOn = true, preferred = PrivacyTransport.I2P)),
        )
    }

    @Test
    fun i2p_pinsToI2p_whenAvailable() {
        assertEquals(PrivacyRoute.I2p, PrivacyRouter.route(i2p, FeatureRole.IMAGE, settings(i2pOn = true)))
    }

    @Test
    fun i2p_blocksWhenI2pOff() {
        assertEquals(
            PrivacyRoute.Blocked(BlockReason.I2P_REQUIRES_I2P),
            PrivacyRouter.route(i2p, FeatureRole.IMAGE, settings(torOn = true, i2pOn = false, preferred = PrivacyTransport.TOR)),
        )
    }

    @Test
    fun b32_i2p_address_pinsToI2p() {
        val b32 = "wss://abcdefghijklmnopqrstuvwxyz234567.b32.i2p/"
        assertEquals(PrivacyRoute.I2p, PrivacyRouter.route(b32, FeatureRole.IMAGE, settings(i2pOn = true)))
    }

    @Test
    fun clearnet_directWhenNoPreferred() {
        assertEquals(
            PrivacyRoute.Direct,
            PrivacyRouter.route(
                clearnet,
                FeatureRole.IMAGE,
                settings(torOn = true, i2pOn = true, preferred = PrivacyTransport.DIRECT, tor = TorSettings(torType = TorType.INTERNAL, imagesViaTor = true)),
            ),
        )
    }

    @Test
    fun clearnet_torRoutes_whenPreferredAndFeatureOn() {
        val s =
            settings(
                torOn = true,
                preferred = PrivacyTransport.TOR,
                tor = TorSettings(torType = TorType.INTERNAL, imagesViaTor = true),
            )
        assertEquals(PrivacyRoute.Tor, PrivacyRouter.route(clearnet, FeatureRole.IMAGE, s))
    }

    @Test
    fun clearnet_torDirect_whenPreferredButFeatureOff() {
        val s =
            settings(
                torOn = true,
                preferred = PrivacyTransport.TOR,
                tor = TorSettings(torType = TorType.INTERNAL, imagesViaTor = false),
            )
        assertEquals(PrivacyRoute.Direct, PrivacyRouter.route(clearnet, FeatureRole.IMAGE, s))
    }

    @Test
    fun clearnet_i2pRoutes_whenPreferredAndFeatureOn() {
        val s =
            settings(
                i2pOn = true,
                preferred = PrivacyTransport.I2P,
                i2p = I2pSettings(i2pType = I2pType.EXTERNAL, videosViaI2p = true),
            )
        assertEquals(PrivacyRoute.I2p, PrivacyRouter.route(clearnet, FeatureRole.VIDEO, s))
    }

    @Test
    fun clearnet_i2pDirect_whenPreferredButFeatureOff() {
        val s =
            settings(
                i2pOn = true,
                preferred = PrivacyTransport.I2P,
                i2p = I2pSettings(i2pType = I2pType.EXTERNAL, videosViaI2p = false),
            )
        assertEquals(PrivacyRoute.Direct, PrivacyRouter.route(clearnet, FeatureRole.VIDEO, s))
    }

    @Test
    fun clearnet_preferredButDaemonOff_isDirect() {
        // User picked TOR but the daemon is off — go Direct instead of erroring.
        val s =
            settings(
                torOn = false,
                preferred = PrivacyTransport.TOR,
                tor = TorSettings(torType = TorType.OFF, imagesViaTor = true),
            )
        assertEquals(PrivacyRoute.Direct, PrivacyRouter.route(clearnet, FeatureRole.IMAGE, s))
    }

    @Test
    fun clearnet_i2pTogglesIgnored_whenTorIsPreferred() {
        // I2P is the only daemon running but TOR is the preferred clearnet transport,
        // so I2P toggles must not take effect — fall through to Direct.
        val s =
            settings(
                torOn = false,
                i2pOn = true,
                preferred = PrivacyTransport.TOR,
                i2p = I2pSettings(i2pType = I2pType.EXTERNAL, imagesViaI2p = true),
            )
        assertEquals(PrivacyRoute.Direct, PrivacyRouter.route(clearnet, FeatureRole.IMAGE, s))
    }

    @Test
    fun bothDaemonsRunning_clearnetUsesOnlyPreferred() {
        // Both daemons up, both have images toggled on, preferred is I2P → I2P wins.
        val s =
            settings(
                torOn = true,
                i2pOn = true,
                preferred = PrivacyTransport.I2P,
                tor = TorSettings(torType = TorType.INTERNAL, imagesViaTor = true),
                i2p = I2pSettings(i2pType = I2pType.EXTERNAL, imagesViaI2p = true),
            )
        assertEquals(PrivacyRoute.I2p, PrivacyRouter.route(clearnet, FeatureRole.IMAGE, s))
    }

    @Test
    fun hiddenServiceIgnoresClearnetPreference() {
        // .onion still pins to Tor even when I2P is the preferred clearnet transport.
        val s = settings(torOn = true, i2pOn = true, preferred = PrivacyTransport.I2P)
        assertEquals(PrivacyRoute.Tor, PrivacyRouter.route(onion, FeatureRole.IMAGE, s))
    }
}
