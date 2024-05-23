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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DMRelayListViewModel : ViewModel() {
    private lateinit var account: Account

    private val _relays = MutableStateFlow<List<DMRelaySetupInfo>>(emptyList())
    val relays = _relays.asStateFlow()

    fun load(account: Account) {
        this.account = account
        clear()
        loadRelayDocuments()
    }

    fun create() {
        viewModelScope.launch(Dispatchers.IO) {
            account.saveDMRelayList(_relays.value.map { it.url })
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

    @Immutable
    data class DMRelaySetupInfo(
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
        _relays.update {
            val relayList = account.getDMRelayList()?.relays() ?: emptyList()

            relayList.map { relayUrl ->
                val liveRelay = RelayPool.getRelay(relayUrl)
                val errorCounter = liveRelay?.errorCounter ?: 0
                val eventDownloadCounter = liveRelay?.eventDownloadCounterInBytes ?: 0
                val eventUploadCounter = liveRelay?.eventUploadCounterInBytes ?: 0
                val spamCounter = liveRelay?.spamCounter ?: 0

                DMRelaySetupInfo(
                    relayUrl,
                    errorCounter,
                    eventDownloadCounter,
                    eventUploadCounter,
                    spamCounter,
                )
            }.distinctBy { it.url }.sortedBy { it.downloadCountInBytes }.reversed()
        }
    }

    fun addRelay(relay: DMRelaySetupInfo) {
        if (relays.value.any { it.url == relay.url }) return

        _relays.update { it.plus(relay) }
    }

    fun deleteRelay(relay: DMRelaySetupInfo) {
        _relays.update { it.minus(relay) }
    }

    fun deleteAll() {
        _relays.update { relays -> emptyList() }
    }

    fun togglePaidRelay(
        relay: DMRelaySetupInfo,
        paid: Boolean,
    ) {
        _relays.update { it.updated(relay, relay.copy(paidRelay = paid)) }
    }
}
