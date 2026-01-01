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

import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User


import com.vitorpamplona.amethyst.model.accountsCache.AccountCacheState
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest

class AccountsTorStateConnector(
    accountsCache: AccountCacheState,
    torEvaluatorFlow: TorRelayState,
    scope: CoroutineScope,
) {
    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val allDmRelayFlows: Flow<Set<NormalizedRelayUrl>> =
        accountsCache.accounts
            .debounce(200)
            .transformLatest { snapshot ->
                val dmFlows = snapshot.map { it.value.dmRelayList.flow }

                val dmFlowReady =
                    dmFlows.ifEmpty {
                        listOf(MutableStateFlow(emptySet()))
                    }

                if (dmFlowReady.isEmpty()) {
                    emit(emptySet())
                } else {
                    emitAll(
                        combine(dmFlowReady) {
                            val dmRelays = mutableSetOf<NormalizedRelayUrl>()
                            it.forEach {
                                dmRelays.addAll(it)
                            }
                            dmRelays.toSet()
                        },
                    )
                }
            }.onEach {
                torEvaluatorFlow.dmRelays.tryEmit(it)
            }.stateIn(
                scope,
                SharingStarted.Eagerly,
                emptySet(),
            )

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val allTrustedRelaysFlow: Flow<Set<NormalizedRelayUrl>> =
        accountsCache.accounts
            .debounce(200)
            .transformLatest { snapshot ->
                val trustedRelayFlows = snapshot.map { it.value.trustedRelays.flow }

                val trustedRelayFlowReady =
                    trustedRelayFlows.ifEmpty {
                        listOf(MutableStateFlow(emptySet()))
                    }

                if (trustedRelayFlowReady.isEmpty()) {
                    emit(emptySet())
                } else {
                    emitAll(
                        combine(trustedRelayFlowReady) {
                            val trustedRelays = mutableSetOf<NormalizedRelayUrl>()
                            it.forEach {
                                trustedRelays.addAll(it)
                            }
                            trustedRelays.toSet()
                        },
                    )
                }
            }.onEach {
                torEvaluatorFlow.trustedRelays.tryEmit(it)
            }.stateIn(
                scope,
                SharingStarted.Eagerly,
                emptySet(),
            )
}
