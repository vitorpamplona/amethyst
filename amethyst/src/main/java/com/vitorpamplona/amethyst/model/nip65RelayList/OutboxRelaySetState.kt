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
package com.vitorpamplona.amethyst.model.nip65RelayList

import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.NoteState
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
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest

class OutboxRelaySetState(
    usersToLoad: MutableStateFlow<Set<HexKey>>,
    val cache: LocalCache,
    scope: CoroutineScope,
) {
    fun getNIP65RelayListAddress(pubkey: HexKey) = AdvertisedRelayListEvent.Companion.createAddress(pubkey)

    fun getNIP65RelayListNote(pubkey: HexKey): AddressableNote = cache.getOrCreateAddressableNote(getNIP65RelayListAddress(pubkey))

    fun getNIP65RelayListFlow(pubkey: HexKey): StateFlow<NoteState> = getNIP65RelayListNote(pubkey).flow().metadata.stateFlow

    fun getNIP65RelayList(pubkey: HexKey): AdvertisedRelayListEvent? = getNIP65RelayListNote(pubkey).event as? AdvertisedRelayListEvent

    fun allRelayListFlows(followList: Set<HexKey>): List<StateFlow<NoteState>> = followList.map { getNIP65RelayListFlow(it) }

    fun combineAllFlows(flows: List<StateFlow<NoteState>>): Flow<Set<NormalizedRelayUrl>> =
        combine(flows) { relayListNotes: Array<NoteState> ->
            relayListNotes.mapNotNull {
                (it.note.event as? AdvertisedRelayListEvent)?.writeRelaysNorm()
            }
        }.map {
            it.flatten().toSet()
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    val flow: StateFlow<Set<NormalizedRelayUrl>> =
        usersToLoad.transformLatest { followList ->
            val flows: List<StateFlow<NoteState>> = allRelayListFlows(followList)
            val relayListFlows = combineAllFlows(flows)
            emitAll(relayListFlows)
        }.onStart {
            emit(
                usersToLoad.value.mapNotNull {
                    getNIP65RelayList(it)?.writeRelaysNorm()
                }.flatten().toSet(),
            )
        }.flowOn(Dispatchers.Default)
            .stateIn(
                scope,
                SharingStarted.Companion.Eagerly,
                emptySet(),
            )

    @OptIn(ExperimentalCoroutinesApi::class)
    val flowSet: StateFlow<Set<String>> =
        flow.map { relayList ->
            relayList.map { it.url }.toSet()
        }.onStart {
            emit(
                usersToLoad.value.mapNotNull {
                    getNIP65RelayList(it)?.writeRelaysNorm()?.map { it.url }?.toSet()
                }.flatten().toSet(),
            )
        }.flowOn(Dispatchers.Default)
            .stateIn(
                scope,
                SharingStarted.Companion.Eagerly,
                emptySet(),
            )
}
