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
package com.vitorpamplona.amethyst.model.torState

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.tor.TorType
import com.vitorpamplona.amethyst.service.okhttp.DualHttpClientManager
import com.vitorpamplona.amethyst.ui.tor.TorSettingsFlow
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import okhttp3.OkHttpClient

@Stable
class TorRelayState(
    val okHttpClient: DualHttpClientManager,
    val torSettingsFlow: TorSettingsFlow,
    val scope: CoroutineScope,
) {
    val dmRelays = MutableStateFlow<Set<NormalizedRelayUrl>>(emptySet())
    val trustedRelays = MutableStateFlow<Set<NormalizedRelayUrl>>(emptySet())

    /**
     * Relays known to be used for money operations from persistent configuration: NIP-47 wallet
     * relays and saved CLINK Debits service relays. Fed by [AccountsTorStateConnector] across all
     * logged-in accounts. These follow the money-operations Tor preference (see [TorRelayEvaluation]).
     */
    val moneyOpRelays = MutableStateFlow<Set<NormalizedRelayUrl>>(emptySet())

    /**
     * Money-operation relays registered for the lifetime of a single ad-hoc round-trip whose relay
     * isn't a saved wallet — e.g. paying someone's CLINK offer (`noffer`) pointer. Reference-counted
     * so overlapping payments that share a relay don't unregister it while another is still in flight.
     */
    private val adHocMoneyOpCounts = MutableStateFlow<Map<NormalizedRelayUrl, Int>>(emptyMap())

    private fun currentMoneyOpRelays(): Set<NormalizedRelayUrl> = moneyOpRelays.value + adHocMoneyOpCounts.value.keys

    /**
     * Marks [relays] as money-operation relays until a matching [unregisterMoneyOpRelays] call.
     * Used by the CLINK offer/debit payers so a one-off payment relay honors the money-operations
     * Tor preference instead of being treated as a generic "new" relay.
     */
    fun registerMoneyOpRelays(relays: Set<NormalizedRelayUrl>) {
        if (relays.isEmpty()) return
        adHocMoneyOpCounts.update { current ->
            current.toMutableMap().apply {
                relays.forEach { this[it] = (this[it] ?: 0) + 1 }
            }
        }
    }

    fun unregisterMoneyOpRelays(relays: Set<NormalizedRelayUrl>) {
        if (relays.isEmpty()) return
        adHocMoneyOpCounts.update { current ->
            current.toMutableMap().apply {
                relays.forEach {
                    val next = (this[it] ?: 0) - 1
                    if (next <= 0) remove(it) else this[it] = next
                }
            }
        }
    }

    private fun currentSettings() =
        TorRelaySettings(
            torType = torSettingsFlow.torType.value,
            onionRelaysViaTor = torSettingsFlow.onionRelaysViaTor.value,
            dmRelaysViaTor = torSettingsFlow.dmRelaysViaTor.value,
            newRelaysViaTor = torSettingsFlow.newRelaysViaTor.value,
            trustedRelaysViaTor = torSettingsFlow.trustedRelaysViaTor.value,
            moneyOperationsViaTor = torSettingsFlow.moneyOperationsViaTor.value,
        )

    val torSettings =
        combine(
            torSettingsFlow.torType,
            torSettingsFlow.onionRelaysViaTor,
            torSettingsFlow.dmRelaysViaTor,
            torSettingsFlow.trustedRelaysViaTor,
            torSettingsFlow.newRelaysViaTor,
        ) {
            torType: TorType,
            onionRelaysViaTor: Boolean,
            dmRelaysViaTor: Boolean,
            trustedRelaysViaTor: Boolean,
            newRelaysViaTor: Boolean,
            ->
            TorRelaySettings(
                torType = torType,
                onionRelaysViaTor = onionRelaysViaTor,
                dmRelaysViaTor = dmRelaysViaTor,
                newRelaysViaTor = newRelaysViaTor,
                trustedRelaysViaTor = trustedRelaysViaTor,
            )
        }.combine(torSettingsFlow.moneyOperationsViaTor) { settings, moneyOperationsViaTor ->
            settings.copy(moneyOperationsViaTor = moneyOperationsViaTor)
        }.onStart {
            emit(currentSettings())
        }.flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                currentSettings(),
            )

    val flow =
        combineTransform(
            torSettings,
            trustedRelays,
            dmRelays,
            moneyOpRelays,
            adHocMoneyOpCounts,
        ) {
            torSettings: TorRelaySettings,
            trustedRelayList: Set<NormalizedRelayUrl>,
            dmRelayList: Set<NormalizedRelayUrl>,
            moneyOpRelayList: Set<NormalizedRelayUrl>,
            adHocMoneyOps: Map<NormalizedRelayUrl, Int>,
            ->
            emit(
                TorRelayEvaluation(
                    torSettings = torSettings,
                    trustedRelayList = trustedRelayList,
                    dmRelayList = dmRelayList,
                    moneyOpRelayList = moneyOpRelayList + adHocMoneyOps.keys,
                ),
            )
        }.onStart {
            emit(
                TorRelayEvaluation(
                    torSettings = torSettings.value,
                    trustedRelayList = trustedRelays.value,
                    dmRelayList = dmRelays.value,
                    moneyOpRelayList = currentMoneyOpRelays(),
                ),
            )
        }.flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                TorRelayEvaluation(
                    torSettings = torSettings.value,
                    trustedRelayList = trustedRelays.value,
                    dmRelayList = dmRelays.value,
                    moneyOpRelayList = currentMoneyOpRelays(),
                ),
            )

    /**
     * Resolves the Tor preference for [relay] from live source values rather than the cached [flow]
     * snapshot. This makes ad-hoc money-op registration ([registerMoneyOpRelays]) take effect on the
     * very next connection attempt, with no dependency on the combine pipeline having propagated yet.
     */
    fun shouldUseTorForRelay(relay: NormalizedRelayUrl) =
        TorRelayEvaluation(
            torSettings = currentSettings(),
            trustedRelayList = trustedRelays.value,
            dmRelayList = dmRelays.value,
            moneyOpRelayList = currentMoneyOpRelays(),
        ).useTor(relay)

    fun okHttpClientForRelay(url: NormalizedRelayUrl): OkHttpClient = okHttpClient.getHttpClient(shouldUseTorForRelay(url))
}
