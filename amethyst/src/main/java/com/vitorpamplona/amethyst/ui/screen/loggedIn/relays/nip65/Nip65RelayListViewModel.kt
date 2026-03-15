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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.nip65

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.service.replace
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.common.BasicRelaySetupInfo
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.common.RelayCountResult
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.common.relaySetupInfoBuilder
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.IRelayClientListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.client.single.newSubId
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.CountMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip65RelayList.tags.AdvertisedRelayInfo
import com.vitorpamplona.quartz.nip65RelayList.tags.AdvertisedRelayType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Stable
class Nip65RelayListViewModel : ViewModel(), IRelayClientListener {
    private lateinit var accountViewModel: AccountViewModel
    private lateinit var account: Account

    private val _homeRelays = MutableStateFlow<List<BasicRelaySetupInfo>>(emptyList())
    val homeRelays = _homeRelays.asStateFlow()

    private val _notificationRelays = MutableStateFlow<List<BasicRelaySetupInfo>>(emptyList())
    val notificationRelays = _notificationRelays.asStateFlow()

    private val _homeCountResults = MutableStateFlow<Map<NormalizedRelayUrl, RelayCountResult>>(emptyMap())
    val homeCountResults = _homeCountResults.asStateFlow()

    private val _notifCountResults = MutableStateFlow<Map<NormalizedRelayUrl, RelayCountResult>>(emptyMap())
    val notifCountResults = _notifCountResults.asStateFlow()

    private val subIdToRelay = mutableMapOf<String, Pair<NormalizedRelayUrl, Boolean>>()

    var hasModified = false

    fun init(accountViewModel: AccountViewModel) {
        this.accountViewModel = accountViewModel
        this.account = accountViewModel.account
    }

    fun load() {
        clear()
        loadRelayDocuments()
        loadCounts()
    }

    fun create() {
        if (hasModified) {
            accountViewModel.launchSigner {
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
                Amethyst.instance.nip11Cache.loadRelayInfo(
                    relay = item.relay,
                    onInfo = {
                        toggleHomePaidRelay(item, it.limitation?.payment_required ?: false)
                    },
                    onError = { url, errorCode, exceptionMessage -> },
                )
            }

            _notificationRelays.value.forEach { item ->
                Amethyst.instance.nip11Cache.loadRelayInfo(
                    relay = item.relay,
                    onInfo = {
                        toggleNotifPaidRelay(item, it.limitation?.payment_required ?: false)
                    },
                    onError = { url, errorCode, exceptionMessage -> },
                )
            }
        }
    }

    private fun loadCounts() {
        val client = Amethyst.instance.client
        cleanupCounts()

        val homeList = _homeRelays.value
        val notifList = _notificationRelays.value

        if (homeList.isEmpty() && notifList.isEmpty()) return

        client.subscribe(this)

        homeList.forEach { item ->
            val subId = newSubId()
            subIdToRelay[subId] = Pair(item.relay, true)
            client.queryCount(
                subId = subId,
                filters = mapOf(item.relay to listOf(Filter(authors = listOf(account.pubKey)))),
            )
        }

        notifList.forEach { item ->
            val subId = newSubId()
            subIdToRelay[subId] = Pair(item.relay, false)
            client.queryCount(
                subId = subId,
                filters = mapOf(item.relay to listOf(Filter(tags = mapOf("p" to listOf(account.pubKey))))),
            )
        }
    }

    override fun onIncomingMessage(
        relay: IRelayClient,
        msgStr: String,
        msg: Message,
    ) {
        if (msg is CountMessage) {
            val (relayUrl, isHome) = subIdToRelay[msg.queryId] ?: return

            val newResult =
                RelayCountResult(
                    listOf(
                        RelayCountResult.CountEntry(
                            label = "events",
                            count = msg.result.count,
                            approximate = msg.result.approximate,
                        ),
                    ),
                )

            if (isHome) {
                _homeCountResults.update { it + (relayUrl to newResult) }
            } else {
                _notifCountResults.update { it + (relayUrl to newResult) }
            }
        }
    }

    private fun cleanupCounts() {
        val client = Amethyst.instance.client
        subIdToRelay.keys.forEach { subId ->
            client.close(subId)
        }
        subIdToRelay.clear()
        _homeCountResults.value = emptyMap()
        _notifCountResults.value = emptyMap()
        client.unsubscribe(this)
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

    override fun onCleared() {
        cleanupCounts()
        super.onCleared()
    }
}
