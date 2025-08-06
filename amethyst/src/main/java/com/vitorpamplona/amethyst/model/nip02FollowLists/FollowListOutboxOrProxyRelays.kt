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
package com.vitorpamplona.amethyst.model.nip02FollowLists

import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.NoteState
import com.vitorpamplona.amethyst.model.nip51Lists.blockedRelays.BlockedRelayListState
import com.vitorpamplona.amethyst.model.nip51Lists.proxyRelays.ProxyRelayListState
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlin.collections.flatten

class FollowListOutboxOrProxyRelays(
    kind3Follows: FollowListState,
    blockedRelayList: BlockedRelayListState,
    proxyRelayList: ProxyRelayListState,
    val cache: LocalCache,
    scope: CoroutineScope,
) {
    fun getNIP65RelayListAddress(pubkey: HexKey) = AdvertisedRelayListEvent.createAddress(pubkey)

    fun getNIP65RelayListNote(pubkey: HexKey): AddressableNote = cache.getOrCreateAddressableNote(getNIP65RelayListAddress(pubkey))

    fun getNIP65RelayListFlow(pubkey: HexKey): StateFlow<NoteState> = getNIP65RelayListNote(pubkey).flow().metadata.stateFlow

    fun getNIP65RelayList(pubkey: HexKey): AdvertisedRelayListEvent? = getNIP65RelayListNote(pubkey).event as? AdvertisedRelayListEvent

    fun allRelayListFlows(followList: Set<HexKey>): List<StateFlow<NoteState>> = followList.map { getNIP65RelayListFlow(it) }

    fun combineAllFlows(flows: List<StateFlow<NoteState>>): Flow<Set<NormalizedRelayUrl>> =
        combine(flows) { relayListNotes: Array<NoteState> ->
            relayListNotes
                .mapNotNull {
                    (it.note.event as? AdvertisedRelayListEvent)?.writeRelaysNorm()
                }.flatten()
                .toSet()
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    val outboxRelayFlow: StateFlow<Set<NormalizedRelayUrl>> =
        kind3Follows.flow
            .transformLatest {
                emitAll(combineAllFlows(allRelayListFlows(it.authors)))
            }.onStart {
                emit(
                    kind3Follows.flow.value.authors
                        .mapNotNull {
                            getNIP65RelayList(it)?.writeRelaysNorm()
                        }.flatten()
                        .toSet(),
                )
            }.distinctUntilChanged()
            .flowOn(Dispatchers.Default)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptySet(),
            )

    val outboxRelayMinusBlockedFlow: StateFlow<Set<NormalizedRelayUrl>> =
        combine(outboxRelayFlow, blockedRelayList.flow) { followList, blockedRelays ->
            followList.minus(blockedRelays)
        }.onStart {
            emit(outboxRelayFlow.value.minus(blockedRelayList.flow.value.toSet()))
        }.flowOn(Dispatchers.Default)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptySet(),
            )

    @OptIn(ExperimentalCoroutinesApi::class)
    val flow: StateFlow<Set<NormalizedRelayUrl>> =
        proxyRelayList.flow
            .flatMapLatest { proxyRelays ->
                if (proxyRelays.isEmpty()) {
                    outboxRelayMinusBlockedFlow
                } else {
                    MutableStateFlow(proxyRelays)
                }
            }.onStart {
                emit(
                    proxyRelayList.flow.value.ifEmpty {
                        outboxRelayMinusBlockedFlow.value
                    },
                )
            }.flowOn(Dispatchers.Default)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptySet(),
            )

    @OptIn(ExperimentalCoroutinesApi::class)
    val flowSet: StateFlow<Set<String>> =
        flow
            .map { relayList ->
                relayList.map { it.url }.toSet()
            }.onStart {
                emit(flow.value.map { it.url }.toSet())
            }.flowOn(Dispatchers.Default)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptySet(),
            )
}
