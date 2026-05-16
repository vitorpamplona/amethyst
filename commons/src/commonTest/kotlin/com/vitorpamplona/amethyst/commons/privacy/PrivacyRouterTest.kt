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
        features: FeatureTransportChoices = FeatureTransportChoices(),
    ) = PrivacySettings(
        tor = TorSettings(torType = if (torOn) TorType.INTERNAL else TorType.OFF),
        i2p = I2pSettings(i2pType = if (i2pOn) I2pType.INTERNAL else I2pType.OFF),
        features = features,
    )

    @Test
    fun localhost_alwaysDirect() {
        assertEquals(PrivacyTransport.DIRECT, PrivacyRouter.route(localhost, FeatureRole.IMAGE, settings(torOn = true, i2pOn = true)))
    }

    @Test
    fun onion_pinsToTor_whenAvailable() {
        assertEquals(PrivacyTransport.TOR, PrivacyRouter.route(onion, FeatureRole.IMAGE, settings(torOn = true)))
    }

    @Test
    fun onion_downgradesToDirect_whenTorOff() {
        assertEquals(PrivacyTransport.DIRECT, PrivacyRouter.route(onion, FeatureRole.IMAGE, settings(torOn = false, i2pOn = true)))
    }

    @Test
    fun i2p_pinsToI2p_whenAvailable() {
        assertEquals(PrivacyTransport.I2P, PrivacyRouter.route(i2p, FeatureRole.IMAGE, settings(i2pOn = true)))
    }

    @Test
    fun i2p_downgradesToDirect_whenI2pOff() {
        assertEquals(PrivacyTransport.DIRECT, PrivacyRouter.route(i2p, FeatureRole.IMAGE, settings(torOn = true, i2pOn = false)))
    }

    @Test
    fun i2p_ignoresFeatureChoice() {
        // Per-feature picker is irrelevant for hidden-service hosts.
        val features = FeatureTransportChoices(image = TransportChoice.TOR)
        assertEquals(PrivacyTransport.I2P, PrivacyRouter.route(i2p, FeatureRole.IMAGE, settings(torOn = true, i2pOn = true, features = features)))
    }

    @Test
    fun clearnet_followsFeatureChoice_tor() {
        val features = FeatureTransportChoices(image = TransportChoice.TOR)
        assertEquals(PrivacyTransport.TOR, PrivacyRouter.route(clearnet, FeatureRole.IMAGE, settings(torOn = true, features = features)))
    }

    @Test
    fun clearnet_followsFeatureChoice_i2p() {
        val features = FeatureTransportChoices(video = TransportChoice.I2P)
        assertEquals(PrivacyTransport.I2P, PrivacyRouter.route(clearnet, FeatureRole.VIDEO, settings(i2pOn = true, features = features)))
    }

    @Test
    fun clearnet_choiceWithoutBackingTransport_downgradesToDirect() {
        val features = FeatureTransportChoices(image = TransportChoice.TOR)
        assertEquals(PrivacyTransport.DIRECT, PrivacyRouter.route(clearnet, FeatureRole.IMAGE, settings(torOn = false, features = features)))
    }

    @Test
    fun clearnet_defaultChoice_isDirect() {
        assertEquals(PrivacyTransport.DIRECT, PrivacyRouter.route(clearnet, FeatureRole.NIP05, settings(torOn = true, i2pOn = true)))
    }

    @Test
    fun perFeatureRouting_routesIndependently() {
        val features =
            FeatureTransportChoices(
                image = TransportChoice.TOR,
                video = TransportChoice.I2P,
                urlPreview = TransportChoice.DIRECT,
            )
        val s = settings(torOn = true, i2pOn = true, features = features)
        assertEquals(PrivacyTransport.TOR, PrivacyRouter.route(clearnet, FeatureRole.IMAGE, s))
        assertEquals(PrivacyTransport.I2P, PrivacyRouter.route(clearnet, FeatureRole.VIDEO, s))
        assertEquals(PrivacyTransport.DIRECT, PrivacyRouter.route(clearnet, FeatureRole.URL_PREVIEW, s))
    }

    @Test
    fun b32_i2p_address_pinsToI2p() {
        val b32 = "wss://abcdefghijklmnopqrstuvwxyz234567.b32.i2p/"
        assertEquals(PrivacyTransport.I2P, PrivacyRouter.route(b32, FeatureRole.IMAGE, settings(i2pOn = true)))
    }
}
