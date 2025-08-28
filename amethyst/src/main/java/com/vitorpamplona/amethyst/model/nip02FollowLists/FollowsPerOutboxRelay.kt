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
import com.vitorpamplona.amethyst.model.Constants
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.NoteState
import com.vitorpamplona.amethyst.model.nip51Lists.blockedRelays.BlockedRelayListState
import com.vitorpamplona.amethyst.model.nip51Lists.proxyRelays.ProxyRelayListState
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.utils.mapOfSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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
import kotlin.collections.ifEmpty

class FollowsPerOutboxRelay(
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

    fun combineAllRelayListFlows(flows: List<StateFlow<NoteState>>): Flow<Map<NormalizedRelayUrl, Set<HexKey>>> =
        combine(flows) { relayListNotes: Array<NoteState> ->
            mapOfSet {
                relayListNotes.forEach { noteState ->
                    noteState.note.author?.pubkeyHex?.let { authorHex ->
                        val outboxRelayList =
                            getNIP65RelayList(authorHex)?.writeRelaysNorm()
                                ?: LocalCache.relayHints.hintsForKey(authorHex).ifEmpty { null }
                                ?: Constants.eventFinderRelays
                        outboxRelayList.forEach { relay ->
                            add(relay, authorHex)
                        }
                    }
                }
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    val outboxPerRelayFlow: StateFlow<Map<NormalizedRelayUrl, Set<HexKey>>> =
        kind3Follows.flow
            .transformLatest {
                emitAll(combineAllRelayListFlows(allRelayListFlows(it.authors)))
            }.onStart {
                emit(
                    mapOfSet {
                        kind3Follows.flow.value.authors.map { authorHex ->
                            val outboxRelayList =
                                getNIP65RelayList(authorHex)?.writeRelaysNorm()
                                    ?: LocalCache.relayHints.hintsForKey(authorHex).ifEmpty { null }
                                    ?: Constants.eventFinderRelays
                            outboxRelayList.forEach { relay ->
                                add(relay, authorHex)
                            }
                        }
                    },
                )
            }.distinctUntilChanged()
            .flowOn(Dispatchers.Default)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptyMap(),
            )

    val outboxPerRelayMinusBlockedFlow: StateFlow<Map<NormalizedRelayUrl, Set<HexKey>>> =
        combine(outboxPerRelayFlow, blockedRelayList.flow) { followList, blockedRelays ->
            followList.minus(blockedRelays)
        }.onStart {
            emit(outboxPerRelayFlow.value.minus(blockedRelayList.flow.value.toSet()))
        }.flowOn(Dispatchers.Default)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptyMap(),
            )

    @OptIn(ExperimentalCoroutinesApi::class)
    val flow: StateFlow<Map<NormalizedRelayUrl, Set<HexKey>>> =
        proxyRelayList.flow
            .flatMapLatest { proxyRelays ->
                if (proxyRelays.isEmpty()) {
                    outboxPerRelayMinusBlockedFlow
                } else {
                    kind3Follows.flow.map { follows ->
                        proxyRelays.associateWith { follows.authors }
                    }
                }
            }.onStart {
                if (proxyRelayList.flow.value.isEmpty()) {
                    emit(outboxPerRelayMinusBlockedFlow.value)
                } else {
                    emit(proxyRelayList.flow.value.associateWith { kind3Follows.flow.value.authors })
                }
            }.flowOn(Dispatchers.Default)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptyMap(),
            )
}
