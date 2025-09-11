/**
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

import com.vitorpamplona.amethyst.service.okhttp.DualHttpClientManager
import com.vitorpamplona.amethyst.ui.tor.TorSettingsFlow
import com.vitorpamplona.amethyst.ui.tor.TorType
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
import okhttp3.OkHttpClient

class TorRelayState(
    val okHttpClient: DualHttpClientManager,
    val torSettingsFlow: TorSettingsFlow,
    val scope: CoroutineScope,
) {
    val dmRelays = MutableStateFlow<Set<NormalizedRelayUrl>>(emptySet())
    val trustedRelays = MutableStateFlow<Set<NormalizedRelayUrl>>(emptySet())

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
                trustedRelaysViaTor = trustedRelaysViaTor,
                newRelaysViaTor = newRelaysViaTor,
            )
        }.onStart {
            emit(
                TorRelaySettings(
                    torType = torSettingsFlow.torType.value,
                    onionRelaysViaTor = torSettingsFlow.onionRelaysViaTor.value,
                    dmRelaysViaTor = torSettingsFlow.dmRelaysViaTor.value,
                    trustedRelaysViaTor = torSettingsFlow.trustedRelaysViaTor.value,
                    newRelaysViaTor = torSettingsFlow.newRelaysViaTor.value,
                ),
            )
        }.flowOn(Dispatchers.Default)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                TorRelaySettings(
                    torType = torSettingsFlow.torType.value,
                    onionRelaysViaTor = torSettingsFlow.onionRelaysViaTor.value,
                    dmRelaysViaTor = torSettingsFlow.dmRelaysViaTor.value,
                    trustedRelaysViaTor = torSettingsFlow.trustedRelaysViaTor.value,
                    newRelaysViaTor = torSettingsFlow.newRelaysViaTor.value,
                ),
            )

    val flow =
        combineTransform(
            torSettings,
            trustedRelays,
            dmRelays,
        ) { torSettings: TorRelaySettings, trustedRelayList: Set<NormalizedRelayUrl>, dmRelayList: Set<NormalizedRelayUrl> ->
            emit(
                TorRelayEvaluation(
                    torSettings = torSettings,
                    trustedRelayList = trustedRelayList,
                    dmRelayList = dmRelayList,
                ),
            )
        }.onStart {
            emit(
                TorRelayEvaluation(
                    torSettings = torSettings.value,
                    trustedRelayList = trustedRelays.value,
                    dmRelayList = dmRelays.value,
                ),
            )
        }.flowOn(Dispatchers.Default)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                TorRelayEvaluation(
                    torSettings = torSettings.value,
                    trustedRelayList = trustedRelays.value,
                    dmRelayList = dmRelays.value,
                ),
            )

    fun shouldUseTorForRelay(relay: NormalizedRelayUrl) = flow.value.useTor(relay)

    fun okHttpClientForRelay(url: NormalizedRelayUrl): OkHttpClient = okHttpClient.getHttpClient(shouldUseTorForRelay(url))
}
