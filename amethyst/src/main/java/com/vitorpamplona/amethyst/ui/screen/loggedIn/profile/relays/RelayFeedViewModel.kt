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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.relays

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.ui.feeds.InvalidatableContent
import com.vitorpamplona.amethyst.model.RelayInfo
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip17Dm.settings.ChatMessageRelayListEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch

@Stable
class RelayFeedViewModel :
    ViewModel(),
    InvalidatableContent {
    val order =
        compareByDescending<RelayInfo> { it.lastEvent }
            .thenByDescending { it.counter }
            .thenBy { it.url.url }

    var currentUser: MutableStateFlow<User?> = MutableStateFlow(null)

    fun convert(
        relays: Set<NormalizedRelayUrl>?,
        user: User?,
    ): List<RelayInfo> {
        if (relays == null || user == null) return emptyList()
        return relays
            .map { relay ->
                user.relaysBeingUsed[relay] ?: RelayInfo(relay, 0, 0)
            }.sortedWith(order)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val nip65OutboxFlow =
        currentUser
            .transformLatest { user ->
                if (user != null) {
                    emitAll(
                        combine(
                            user.nip65RelayListNote
                                .flow()
                                .metadata.stateFlow,
                            user.flow().usedRelays.stateFlow,
                        ) { nip65, userState ->
                            val relays = (nip65.note.event as? AdvertisedRelayListEvent)?.writeRelaysNorm()?.toSet() ?: emptySet()
                            convert(relays, userState.user)
                        },
                    )
                } else {
                    emit(emptyList<RelayInfo>())
                }
            }.onStart {
                emit(convert((currentUser.value?.nip65RelayListNote?.event as? AdvertisedRelayListEvent)?.writeRelaysNorm()?.toSet(), currentUser.value))
            }.flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val nip65InboxFlow =
        currentUser
            .transformLatest { user ->
                if (user != null) {
                    emitAll(
                        combine(
                            user.nip65RelayListNote
                                .flow()
                                .metadata.stateFlow,
                            user.flow().usedRelays.stateFlow,
                        ) { nip65, userState ->
                            val relays = (nip65.note.event as? AdvertisedRelayListEvent)?.readRelaysNorm()?.toSet() ?: emptySet()
                            convert(relays, userState.user)
                        },
                    )
                } else {
                    emit(emptyList<RelayInfo>())
                }
            }.onStart {
                emit(convert((currentUser.value?.nip65RelayListNote?.event as? AdvertisedRelayListEvent)?.readRelaysNorm()?.toSet(), currentUser.value))
            }.flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val dmInboxFlow =
        currentUser
            .transformLatest { user ->
                if (user != null) {
                    emitAll(
                        combine(
                            user.dmRelayListNote
                                .flow()
                                .metadata.stateFlow,
                            user.flow().usedRelays.stateFlow,
                        ) { nip65, userState ->
                            val relays = (nip65.note.event as? ChatMessageRelayListEvent)?.relays()?.toSet() ?: emptySet()
                            convert(relays, userState.user)
                        },
                    )
                } else {
                    emit(emptyList<RelayInfo>())
                }
            }.onStart {
                emit(convert((currentUser.value?.nip65RelayListNote?.event as? ChatMessageRelayListEvent)?.relays()?.toSet(), currentUser.value))
            }.flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    override val isRefreshing: MutableState<Boolean> = mutableStateOf(false)

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            refreshSuspended()
        }
    }

    suspend fun refreshSuspended() {
        try {
            isRefreshing.value = true

            delay(1000)
        } finally {
            isRefreshing.value = false
        }
    }

    @OptIn(FlowPreview::class)
    fun subscribeTo(user: User) {
        if (currentUser != user) {
            currentUser.tryEmit(user)
        }
    }

    fun unsubscribeTo(user: User) {
        if (currentUser == user) {
            currentUser.tryEmit(null)
            invalidateData()
        }
    }

    override fun invalidateData(ignoreIfDoing: Boolean) {
        currentUser.tryEmit(currentUser.value)
    }

    override fun onCleared() {
        Log.d("Init", "OnCleared: ${this.javaClass.simpleName}")
        super.onCleared()
    }
}
