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
package com.vitorpamplona.amethyst.commons.tor

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TorRelayEvaluationTest {
    private val clearnetRelay = NormalizedRelayUrl("wss://relay.damus.io/")
    private val onionRelay = NormalizedRelayUrl("wss://abc123.onion/")
    private val localhostRelay = NormalizedRelayUrl("ws://127.0.0.1:8080/")
    private val localhostNameRelay = NormalizedRelayUrl("ws://localhost:8080/")
    private val localNetworkRelay = NormalizedRelayUrl("ws://192.168.1.100:8080/")
    private val dmRelay = NormalizedRelayUrl("wss://dm.relay.com/")
    private val trustedRelay = NormalizedRelayUrl("wss://trusted.relay.com/")

    private fun buildEvaluation(
        torType: TorType = TorType.INTERNAL,
        onionViaTor: Boolean = true,
        dmViaTor: Boolean = true,
        newViaTor: Boolean = true,
        trustedViaTor: Boolean = false,
        dmRelays: Set<NormalizedRelayUrl> = setOf(dmRelay),
        trustedRelays: Set<NormalizedRelayUrl> = setOf(trustedRelay),
    ) = TorRelayEvaluation(
        torSettings =
            TorRelaySettings(
                torType = torType,
                onionRelaysViaTor = onionViaTor,
                dmRelaysViaTor = dmViaTor,
                newRelaysViaTor = newViaTor,
                trustedRelaysViaTor = trustedViaTor,
            ),
        trustedRelayList = trustedRelays,
        dmRelayList = dmRelays,
    )

    // --- Tor OFF ---
    @Test
    fun torOff_alwaysFalse() {
        val eval = buildEvaluation(torType = TorType.OFF)
        assertFalse(eval.useTor(clearnetRelay))
        assertFalse(eval.useTor(onionRelay))
        assertFalse(eval.useTor(dmRelay))
    }

    // --- Localhost bypass ---
    @Test
    fun localhost_alwaysFalse() {
        val eval = buildEvaluation()
        assertFalse(eval.useTor(localhostRelay))
        assertFalse(eval.useTor(localhostNameRelay))
        assertFalse(eval.useTor(localNetworkRelay))
    }

    // --- .onion ---
    @Test
    fun onion_enabled_returnsTrue() {
        val eval = buildEvaluation(onionViaTor = true)
        assertTrue(eval.useTor(onionRelay))
    }

    @Test
    fun onion_disabled_returnsFalse() {
        val eval = buildEvaluation(onionViaTor = false)
        assertFalse(eval.useTor(onionRelay))
    }

    // --- DM relays ---
    @Test
    fun dm_enabled_returnsTrue() = assertTrue(buildEvaluation(dmViaTor = true).useTor(dmRelay))

    @Test
    fun dm_disabled_returnsFalse() = assertFalse(buildEvaluation(dmViaTor = false).useTor(dmRelay))

    // --- Trusted relays ---
    @Test
    fun trusted_enabled_returnsTrue() = assertTrue(buildEvaluation(trustedViaTor = true).useTor(trustedRelay))

    @Test
    fun trusted_disabled_returnsFalse() = assertFalse(buildEvaluation(trustedViaTor = false).useTor(trustedRelay))

    // --- New/unknown relays ---
    @Test
    fun unknown_enabled_returnsTrue() = assertTrue(buildEvaluation(newViaTor = true).useTor(clearnetRelay))

    @Test
    fun unknown_disabled_returnsFalse() = assertFalse(buildEvaluation(newViaTor = false).useTor(clearnetRelay))

    // --- Priority ---
    @Test
    fun onionInDmList_treatedAsOnion() {
        val onionDm = NormalizedRelayUrl("wss://dmrelay.onion/")
        val eval = buildEvaluation(onionViaTor = false, dmViaTor = true, dmRelays = setOf(onionDm))
        assertFalse(eval.useTor(onionDm)) // onion check first, disabled
    }

    @Test
    fun relayInBothDmAndTrusted_dmTakesPrecedence() {
        val both = NormalizedRelayUrl("wss://both.relay.com/")
        val eval = buildEvaluation(dmViaTor = true, trustedViaTor = false, dmRelays = setOf(both), trustedRelays = setOf(both))
        assertTrue(eval.useTor(both))
    }

    @Test
    fun relayInBothDmAndTrusted_dmDisabled_noFallToTrusted() {
        val both = NormalizedRelayUrl("wss://both.relay.com/")
        val eval = buildEvaluation(dmViaTor = false, trustedViaTor = true, dmRelays = setOf(both), trustedRelays = setOf(both))
        assertFalse(eval.useTor(both))
    }

    // --- Empty lists ---
    @Test
    fun emptyLists_allAreNew() {
        val eval = buildEvaluation(newViaTor = true, dmRelays = emptySet(), trustedRelays = emptySet())
        assertTrue(eval.useTor(clearnetRelay))
        assertTrue(eval.useTor(dmRelay)) // not in list, treated as new
    }

    // --- External mode ---
    @Test
    fun torExternal_routesLikeInternal() {
        val eval = buildEvaluation(torType = TorType.EXTERNAL, newViaTor = true)
        assertTrue(eval.useTor(clearnetRelay))
        assertFalse(eval.useTor(localhostRelay))
    }
}
