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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.service.Nip11CachedRetriever
import com.vitorpamplona.amethyst.service.relays.RelayPool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SearchRelayListViewModel : ViewModel() {
    private lateinit var account: Account

    private val _relays = MutableStateFlow<List<BasicRelaySetupInfo>>(emptyList())
    val relays = _relays.asStateFlow()

    fun load(account: Account) {
        this.account = account
        clear()
        loadRelayDocuments()
    }

    fun create() {
        viewModelScope.launch(Dispatchers.IO) {
            account.saveSearchRelayList(_relays.value.map { it.url })
            clear()
        }
    }

    fun loadRelayDocuments() {
        viewModelScope.launch(Dispatchers.IO) {
            _relays.value.forEach { item ->
                Nip11CachedRetriever.loadRelayInfo(
                    dirtyUrl = item.url,
                    onInfo = {
                        togglePaidRelay(item, it.limitation?.payment_required ?: false)
                    },
                    onError = { url, errorCode, exceptionMessage -> },
                )
            }
        }
    }

    fun clear() {
        _relays.update {
            val relayList = account.getSearchRelayList()?.relays() ?: emptyList()

            relayList.map { relayUrl ->
                val liveRelay = RelayPool.getRelay(relayUrl)
                val errorCounter = liveRelay?.errorCounter ?: 0
                val eventDownloadCounter = liveRelay?.eventDownloadCounterInBytes ?: 0
                val eventUploadCounter = liveRelay?.eventUploadCounterInBytes ?: 0
                val spamCounter = liveRelay?.spamCounter ?: 0

                BasicRelaySetupInfo(
                    relayUrl,
                    errorCounter,
                    eventDownloadCounter,
                    eventUploadCounter,
                    spamCounter,
                )
            }.distinctBy { it.url }.sortedBy { it.downloadCountInBytes }.reversed()
        }
    }

    fun addRelay(relay: BasicRelaySetupInfo) {
        if (relays.value.any { it.url == relay.url }) return

        _relays.update { it.plus(relay) }
    }

    fun deleteRelay(relay: BasicRelaySetupInfo) {
        _relays.update { it.minus(relay) }
    }

    fun deleteAll() {
        _relays.update { relays -> emptyList() }
    }

    fun togglePaidRelay(
        relay: BasicRelaySetupInfo,
        paid: Boolean,
    ) {
        _relays.update { it.updated(relay, relay.copy(paidRelay = paid)) }
    }
}
