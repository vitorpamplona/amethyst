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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.nip65

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.service.Nip11CachedRetriever
import com.vitorpamplona.amethyst.service.replace
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.common.BasicRelaySetupInfo
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.common.relaySetupInfoBuilder
import com.vitorpamplona.quartz.nip65RelayList.tags.AdvertisedRelayInfo
import com.vitorpamplona.quartz.nip65RelayList.tags.AdvertisedRelayType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Stable
class Nip65RelayListViewModel : ViewModel() {
    private lateinit var accountViewModel: AccountViewModel
    private lateinit var account: Account

    private val _homeRelays = MutableStateFlow<List<BasicRelaySetupInfo>>(emptyList())
    val homeRelays = _homeRelays.asStateFlow()

    private val _notificationRelays = MutableStateFlow<List<BasicRelaySetupInfo>>(emptyList())
    val notificationRelays = _notificationRelays.asStateFlow()

    var hasModified = false

    fun init(accountViewModel: AccountViewModel) {
        this.accountViewModel = accountViewModel
        this.account = accountViewModel.account
    }

    fun load() {
        clear()
        loadRelayDocuments()
    }

    fun create() {
        if (hasModified) {
            accountViewModel.runIOCatching {
                val writes = _homeRelays.value.map { it.relay }.toSet()
                val reads = _notificationRelays.value.map { it.relay }.toSet()

                val urls = writes.union(reads)

                account.sendNip65RelayList(
                    urls.map {
                        val type =
                            if (writes.contains(it) && reads.contains(it)) {
                                AdvertisedRelayType.BOTH
                            } else if (writes.contains(it)) {
                                AdvertisedRelayType.WRITE
                            } else {
                                AdvertisedRelayType.READ
                            }

                        AdvertisedRelayInfo(it, type)
                    },
                )
                clear()
            }
        }
    }

    fun loadRelayDocuments() {
        viewModelScope.launch(Dispatchers.IO) {
            _homeRelays.value.forEach { item ->
                Nip11CachedRetriever.loadRelayInfo(
                    relay = item.relay,
                    okHttpClient = { Amethyst.instance.okHttpClients.getHttpClient(account.torRelayState.shouldUseTorForClean(item.relay)) },
                    onInfo = {
                        toggleHomePaidRelay(item, it.limitation?.payment_required ?: false)
                    },
                    onError = { url, errorCode, exceptionMessage -> },
                )
            }

            _notificationRelays.value.forEach { item ->
                Nip11CachedRetriever.loadRelayInfo(
                    relay = item.relay,
                    okHttpClient = { Amethyst.instance.okHttpClients.getHttpClient(account.torRelayState.shouldUseTorForClean(item.relay)) },
                    onInfo = {
                        toggleNotifPaidRelay(item, it.limitation?.payment_required ?: false)
                    },
                    onError = { url, errorCode, exceptionMessage -> },
                )
            }
        }
    }

    fun clear() {
        hasModified = false
        _homeRelays.update {
            val relayList = account.nip65RelayList.getNIP65RelayList()?.writeRelaysNorm() ?: emptyList()

            relayList
                .map { relaySetupInfoBuilder(it) }
                .distinctBy { it.relay }
                .sortedBy { it.relayStat.receivedBytes }
                .reversed()
        }

        _notificationRelays.update {
            val relayList = account.nip65RelayList.getNIP65RelayList()?.readRelaysNorm() ?: emptyList()

            relayList
                .map { relaySetupInfoBuilder(it) }
                .distinctBy { it.relay }
                .sortedBy { it.relayStat.receivedBytes }
                .reversed()
        }
    }

    fun addHomeRelay(relay: BasicRelaySetupInfo) {
        if (_homeRelays.value.any { it.relay == relay.relay }) return

        _homeRelays.update { it.plus(relay) }
        hasModified = true
    }

    fun deleteHomeRelay(relay: BasicRelaySetupInfo) {
        _homeRelays.update { it.minus(relay) }
        hasModified = true
    }

    fun deleteHomeAll() {
        _homeRelays.update { relays -> emptyList() }
        hasModified = true
    }

    fun toggleHomePaidRelay(
        relay: BasicRelaySetupInfo,
        paid: Boolean,
    ) {
        _homeRelays.update { it.replace(relay, relay.copy(paidRelay = paid)) }
    }

    fun addNotifRelay(relay: BasicRelaySetupInfo) {
        if (_notificationRelays.value.any { it.relay == relay.relay }) return

        _notificationRelays.update { it.plus(relay) }
        hasModified = true
    }

    fun deleteNotifRelay(relay: BasicRelaySetupInfo) {
        _notificationRelays.update { it.minus(relay) }
        hasModified = true
    }

    fun deleteNotifAll() {
        _notificationRelays.update { relays -> emptyList() }
        hasModified = true
    }

    fun toggleNotifPaidRelay(
        relay: BasicRelaySetupInfo,
        paid: Boolean,
    ) {
        _notificationRelays.update { it.replace(relay, relay.copy(paidRelay = paid)) }
    }
}
