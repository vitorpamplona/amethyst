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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [TorRelayState] moved to commonMain so iOS routes relays with the same code. The decision table
 * itself is [TorRelayEvaluationTest]'s; these pin what the state adds on top: the live read in
 * [TorRelayState.shouldUseTorForRelay], the reference-counted ad-hoc sets and the published flow.
 */
class TorRelayStateTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @AfterTest
    fun tearDown() = scope.cancel()

    private val relay = NormalizedRelayUrl("wss://pay.example.com/")
    private val onion = NormalizedRelayUrl("ws://abcdefghijklmnop.onion/")

    // The default preset's relay half: new relays and DMs through Tor, money operations direct.
    private val settings =
        TorSettingsFlow(
            torType = MutableStateFlow(TorType.INTERNAL),
            newRelaysViaTor = MutableStateFlow(true),
            moneyOperationsViaTor = MutableStateFlow(false),
            trustedRelaysViaTor = MutableStateFlow(false),
        )

    private val state = TorRelayState(settings, scope)

    @Test
    fun anUnclassifiedRelayFollowsTheNewRelaySwitch() {
        assertTrue(state.shouldUseTorForRelay(relay))
        settings.newRelaysViaTor.value = false
        assertFalse(state.shouldUseTorForRelay(relay), "settings are read live, not from the cached flow")
        settings.torType.value = TorType.OFF
        settings.onionRelaysViaTor.value = true
        assertFalse(state.shouldUseTorForRelay(onion), "OFF never routes, not even .onion")
    }

    @Test
    fun adHocMoneyRelaysAreReferenceCounted() {
        state.registerMoneyOpRelays(setOf(relay))
        state.registerMoneyOpRelays(setOf(relay))
        assertFalse(state.shouldUseTorForRelay(relay), "a registered payment relay follows moneyOperationsViaTor at once")

        state.unregisterMoneyOpRelays(setOf(relay))
        assertFalse(state.shouldUseTorForRelay(relay), "a second payment still holds the relay")

        state.unregisterMoneyOpRelays(setOf(relay))
        assertTrue(state.shouldUseTorForRelay(relay), "released, it is a new relay again")
    }

    @Test
    fun loginSignerRelaysAreReferenceCounted() {
        state.registerSignerRelays(setOf(relay))
        assertFalse(state.shouldUseTorForRelay(relay), "a NIP-46 login relay follows the trusted switch")

        settings.trustedRelaysViaTor.value = true
        assertTrue(state.shouldUseTorForRelay(relay))

        settings.trustedRelaysViaTor.value = false
        state.unregisterSignerRelays(setOf(relay))
        assertTrue(state.shouldUseTorForRelay(relay))
    }

    @Test
    fun savedSetsClassifyTheirRelays() {
        state.moneyOpRelays.value = setOf(relay)
        assertFalse(state.shouldUseTorForRelay(relay))
        state.moneyOpRelays.value = emptySet()
        state.trustedRelays.value = setOf(relay)
        assertFalse(state.shouldUseTorForRelay(relay))
        state.trustedRelays.value = emptySet()
        state.dmRelays.value = setOf(relay)
        settings.dmRelaysViaTor.value = false
        assertFalse(state.shouldUseTorForRelay(relay))
    }

    @Test
    fun theFlowPublishesSettingsAndClassification() =
        runTest {
            state.trustedRelays.value = setOf(relay)
            settings.moneyOperationsViaTor.value = true

            val evaluation =
                withContext(Dispatchers.Default) {
                    withTimeout(5_000) {
                        state.flow.first { it.classification.trusted == setOf(relay) && it.torSettings.moneyOperationsViaTor }
                    }
                }

            assertEquals(TorType.INTERNAL, evaluation.torSettings.torType)
            assertFalse(evaluation.useTor(relay), "trusted relays stay direct under trustedRelaysViaTor=false")
        }
}
