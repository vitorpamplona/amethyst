/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.amethyst.ui.actions.relays

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.RelayBriefInfoCache
import com.vitorpamplona.amethyst.service.Nip11CachedRetriever
import com.vitorpamplona.amethyst.service.relays.RelayPool
import com.vitorpamplona.quartz.events.AdvertisedRelayListEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class Nip65RelayListViewModel : ViewModel() {
    private lateinit var account: Account

    private val _homeRelays = MutableStateFlow<List<Nip65RelaySetupInfo>>(emptyList())
    val homeRelays = _homeRelays.asStateFlow()

    private val _notificationRelays = MutableStateFlow<List<Nip65RelaySetupInfo>>(emptyList())
    val notificationRelays = _notificationRelays.asStateFlow()

    fun load(account: Account) {
        this.account = account
        clear()
        loadRelayDocuments()
    }

    fun create() {
        viewModelScope.launch(Dispatchers.IO) {
            val writes = _homeRelays.value.map { it.url }.toSet()
            val reads = _notificationRelays.value.map { it.url }.toSet()

            val urls = writes.union(reads)

            account.sendNip65RelayList(
                urls.map {
                    val type =
                        if (writes.contains(it) && reads.contains(it)) {
                            AdvertisedRelayListEvent.AdvertisedRelayType.BOTH
                        } else if (writes.contains(it)) {
                            AdvertisedRelayListEvent.AdvertisedRelayType.WRITE
                        } else {
                            AdvertisedRelayListEvent.AdvertisedRelayType.READ
                        }
                    AdvertisedRelayListEvent.AdvertisedRelayInfo(it, type)
                },
            )
            clear()
        }
    }

    fun loadRelayDocuments() {
        viewModelScope.launch(Dispatchers.IO) {
            _homeRelays.value.forEach { item ->
                Nip11CachedRetriever.loadRelayInfo(
                    dirtyUrl = item.url,
                    onInfo = {
                        toggleHomePaidRelay(item, it.limitation?.payment_required ?: false)
                    },
                    onError = { url, errorCode, exceptionMessage -> },
                )
            }

            _notificationRelays.value.forEach { item ->
                Nip11CachedRetriever.loadRelayInfo(
                    dirtyUrl = item.url,
                    onInfo = {
                        toggleNotifPaidRelay(item, it.limitation?.payment_required ?: false)
                    },
                    onError = { url, errorCode, exceptionMessage -> },
                )
            }
        }
    }

    @Immutable
    data class Nip65RelaySetupInfo(
        val url: String,
        val errorCount: Int = 0,
        val downloadCountInBytes: Int = 0,
        val uploadCountInBytes: Int = 0,
        val spamCount: Int = 0,
        val paidRelay: Boolean = false,
    ) {
        val briefInfo: RelayBriefInfoCache.RelayBriefInfo = RelayBriefInfoCache.RelayBriefInfo(url)
    }

    fun clear() {
        _homeRelays.update {
            val relayList = account.getNIP65RelayList()?.relays() ?: emptyList()

            relayList.filter { it.type == AdvertisedRelayListEvent.AdvertisedRelayType.BOTH || it.type == AdvertisedRelayListEvent.AdvertisedRelayType.WRITE }.map { relayUrl ->
                val liveRelay = RelayPool.getRelay(relayUrl.relayUrl)
                val errorCounter = liveRelay?.errorCounter ?: 0
                val eventDownloadCounter = liveRelay?.eventDownloadCounterInBytes ?: 0
                val eventUploadCounter = liveRelay?.eventUploadCounterInBytes ?: 0
                val spamCounter = liveRelay?.spamCounter ?: 0

                Nip65RelaySetupInfo(
                    relayUrl.relayUrl,
                    errorCounter,
                    eventDownloadCounter,
                    eventUploadCounter,
                    spamCounter,
                )
            }.distinctBy { it.url }.sortedBy { it.downloadCountInBytes }.reversed()
        }

        _notificationRelays.update {
            val relayList = account.getNIP65RelayList()?.relays() ?: emptyList()

            relayList.filter { it.type == AdvertisedRelayListEvent.AdvertisedRelayType.BOTH || it.type == AdvertisedRelayListEvent.AdvertisedRelayType.READ }.map { relayUrl ->
                val liveRelay = RelayPool.getRelay(relayUrl.relayUrl)
                val errorCounter = liveRelay?.errorCounter ?: 0
                val eventDownloadCounter = liveRelay?.eventDownloadCounterInBytes ?: 0
                val eventUploadCounter = liveRelay?.eventUploadCounterInBytes ?: 0
                val spamCounter = liveRelay?.spamCounter ?: 0

                Nip65RelaySetupInfo(
                    relayUrl.relayUrl,
                    errorCounter,
                    eventDownloadCounter,
                    eventUploadCounter,
                    spamCounter,
                )
            }.distinctBy { it.url }.sortedBy { it.downloadCountInBytes }.reversed()
        }
    }

    fun addHomeRelay(relay: Nip65RelaySetupInfo) {
        if (_homeRelays.value.any { it.url == relay.url }) return

        _homeRelays.update { it.plus(relay) }
    }

    fun deleteHomeRelay(relay: Nip65RelaySetupInfo) {
        _homeRelays.update { it.minus(relay) }
    }

    fun deleteHomeAll() {
        _homeRelays.update { relays -> emptyList() }
    }

    fun toggleHomePaidRelay(
        relay: Nip65RelaySetupInfo,
        paid: Boolean,
    ) {
        _homeRelays.update { it.updated(relay, relay.copy(paidRelay = paid)) }
    }

    fun addNotifRelay(relay: Nip65RelaySetupInfo) {
        if (_notificationRelays.value.any { it.url == relay.url }) return

        _notificationRelays.update { it.plus(relay) }
    }

    fun deleteNotifRelay(relay: Nip65RelaySetupInfo) {
        _notificationRelays.update { it.minus(relay) }
    }

    fun deleteNotifAll() {
        _notificationRelays.update { relays -> emptyList() }
    }

    fun toggleNotifPaidRelay(
        relay: Nip65RelaySetupInfo,
        paid: Boolean,
    ) {
        _notificationRelays.update { it.updated(relay, relay.copy(paidRelay = paid)) }
    }
}
