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

import com.vitorpamplona.amethyst.model.AccountSettings
import com.vitorpamplona.amethyst.model.nip17Dms.DmRelayListState
import com.vitorpamplona.amethyst.model.serverList.TrustedRelayListsState
import com.vitorpamplona.amethyst.ui.tor.TorType
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

class TorRelayState(
    val trustedRelayState: TrustedRelayListsState,
    val dmRelayState: DmRelayListState,
    val settings: AccountSettings,
    val scope: CoroutineScope,
) {
    val torSettings =
        combine(
            settings.torSettings.torType,
            settings.torSettings.onionRelaysViaTor,
            settings.torSettings.dmRelaysViaTor,
            settings.torSettings.trustedRelaysViaTor,
            settings.torSettings.newRelaysViaTor,
        ) {
                torType: TorType,
                onionRelaysViaTor: Boolean,
                dmRelaysViaTor: Boolean,
                trustedRelaysViaTor: Boolean,
                newRelaysViaTor: Boolean,
            ->
            TorRelaySettings(torType, onionRelaysViaTor, dmRelaysViaTor, trustedRelaysViaTor, newRelaysViaTor)
        }.onStart {
            emit(
                TorRelaySettings(
                    settings.torSettings.torType.value,
                    settings.torSettings.onionRelaysViaTor.value,
                    settings.torSettings.dmRelaysViaTor.value,
                    settings.torSettings.trustedRelaysViaTor.value,
                    settings.torSettings.newRelaysViaTor.value,
                ),
            )
        }
            .flowOn(Dispatchers.Default)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                TorRelaySettings(
                    settings.torSettings.torType.value,
                    settings.torSettings.onionRelaysViaTor.value,
                    settings.torSettings.dmRelaysViaTor.value,
                    settings.torSettings.trustedRelaysViaTor.value,
                    settings.torSettings.newRelaysViaTor.value,
                ),
            )

    val flow =
        combineTransform(
            torSettings,
            trustedRelayState.flow,
            dmRelayState.flow,
        ) { torSettings: TorRelaySettings, trustedRelayList: Set<NormalizedRelayUrl>, dmRelayList: Set<NormalizedRelayUrl> ->
            emit(TorRelayEvaluation(torSettings, trustedRelayList, dmRelayList))
        }.onStart {
            emit(
                TorRelayEvaluation(
                    torSettings.value,
                    trustedRelayState.flow.value,
                    dmRelayState.flow.value,
                ),
            )
        }.flowOn(Dispatchers.Default)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                TorRelayEvaluation(
                    torSettings.value,
                    trustedRelayState.flow.value,
                    dmRelayState.flow.value,
                ),
            )
}
