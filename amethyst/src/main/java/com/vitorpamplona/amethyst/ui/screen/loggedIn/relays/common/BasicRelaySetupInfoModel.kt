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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.service.replace
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.IRelayClientListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.client.single.newSubId
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.CountMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

abstract class BasicRelaySetupInfoModel : ViewModel(), IRelayClientListener {
    lateinit var accountViewModel: AccountViewModel
    lateinit var account: Account

    private val _relays = MutableStateFlow<List<BasicRelaySetupInfo>>(emptyList())
    val relays = _relays.asStateFlow()

    private val _countResults = MutableStateFlow<Map<NormalizedRelayUrl, RelayCountResult>>(emptyMap())
    val countResults = _countResults.asStateFlow()

    private val subIdToRelay = mutableMapOf<String, Pair<NormalizedRelayUrl, Int>>()
    private val relayQueryInfos = mutableMapOf<NormalizedRelayUrl, List<CountQueryInfo>>()

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

    abstract fun getRelayList(): List<NormalizedRelayUrl>?

    abstract suspend fun saveRelayList(urlList: List<NormalizedRelayUrl>)

    open fun countFilters(relayUrl: NormalizedRelayUrl): List<CountFilter> = emptyList()

    fun create() {
        if (hasModified) {
            accountViewModel.launchSigner {
                saveRelayList(_relays.value.map { it.relay })
                clear()
            }
        }
    }

    fun loadRelayDocuments() {
        viewModelScope.launch(Dispatchers.IO) {
            _relays.value.forEach { item ->
                Amethyst.instance.nip11Cache.loadRelayInfo(
                    relay = item.relay,
                    onInfo = {
                        togglePaidRelay(item, it.limitation?.payment_required ?: false)
                    },
                    onError = { url, errorCode, exceptionMessage -> },
                )
            }
        }
    }

    private fun loadCounts() {
        val client = Amethyst.instance.client
        cleanupCounts()

        val relayList = _relays.value
        if (relayList.isEmpty()) return

        val hasFilters = relayList.any { countFilters(it.relay).isNotEmpty() }
        if (!hasFilters) return

        client.subscribe(this)

        relayList.forEach { item ->
            val filters = countFilters(item.relay)
            if (filters.isEmpty()) return@forEach

            val queryInfos = mutableListOf<CountQueryInfo>()

            filters.forEachIndexed { index, countFilter ->
                val subId = newSubId()
                subIdToRelay[subId] = Pair(item.relay, index)
                queryInfos.add(CountQueryInfo(subId, countFilter.label, index))

                client.queryCount(
                    subId = subId,
                    filters = mapOf(item.relay to listOf(countFilter.filter)),
                )
            }

            relayQueryInfos[item.relay] = queryInfos
        }
    }

    override fun onIncomingMessage(
        relay: IRelayClient,
        msgStr: String,
        msg: Message,
    ) {
        if (msg is CountMessage) {
            val (relayUrl, filterIndex) = subIdToRelay[msg.queryId] ?: return
            val queryInfos = relayQueryInfos[relayUrl] ?: return
            val queryInfo = queryInfos.find { it.filterIndex == filterIndex } ?: return

            _countResults.update { currentMap ->
                val currentResult = currentMap[relayUrl] ?: RelayCountResult()
                val updatedEntries = currentResult.counts.toMutableList()

                val existingIndex = updatedEntries.indexOfFirst { it.label == queryInfo.label }
                val newEntry =
                    RelayCountResult.CountEntry(
                        label = queryInfo.label,
                        count = msg.result.count,
                        approximate = msg.result.approximate,
                    )

                if (existingIndex >= 0) {
                    updatedEntries[existingIndex] = newEntry
                } else {
                    updatedEntries.add(newEntry)
                }

                currentMap + (relayUrl to RelayCountResult(updatedEntries))
            }
        }
    }

    private fun cleanupCounts() {
        val client = Amethyst.instance.client
        subIdToRelay.keys.forEach { subId ->
            client.close(subId)
        }
        subIdToRelay.clear()
        relayQueryInfos.clear()
        _countResults.value = emptyMap()
        client.unsubscribe(this)
    }

    open fun relayListBuilder(): List<BasicRelaySetupInfo> {
        val relayList = getRelayList() ?: emptyList()

        return relayList
            .map {
                relaySetupInfoBuilder(
                    normalized = it,
                    forcesTor =
                        Amethyst.instance.torEvaluatorFlow.flow.value
                            .useTor(it),
                )
            }.distinctBy { it.relay }
            .sortedBy { it.relayStat.receivedBytes }
            .reversed()
    }

    fun clear() {
        _relays.update { relayListBuilder() }
    }

    fun addRelay(relay: BasicRelaySetupInfo) {
        if (relays.value.any { it.relay == relay.relay }) return

        _relays.update { it.plus(relay) }
        hasModified = true
    }

    fun deleteRelay(relay: BasicRelaySetupInfo) {
        _relays.update { it.minus(relay) }
        hasModified = true
    }

    fun deleteAll() {
        _relays.update { relays -> emptyList() }
        hasModified = true
    }

    fun togglePaidRelay(
        relay: BasicRelaySetupInfo,
        paid: Boolean,
    ) {
        _relays.update { it.replace(relay, relay.copy(paidRelay = paid)) }
    }

    override fun onCleared() {
        cleanupCounts()
        super.onCleared()
    }

    private data class CountQueryInfo(
        val subId: String,
        val label: String,
        val filterIndex: Int,
    )
}
