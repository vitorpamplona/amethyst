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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.service.Nip11CachedRetriever
import com.vitorpamplona.amethyst.service.replace
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

abstract class BasicRelaySetupInfoModel : ViewModel() {
    lateinit var account: Account

    private val _relays = MutableStateFlow<List<BasicRelaySetupInfo>>(emptyList())
    val relays = _relays.asStateFlow()

    var hasModified = false

    fun init(account: Account) {
        this.account = account
    }

    fun load() {
        clear()
        loadRelayDocuments()
    }

    abstract fun getRelayList(): List<NormalizedRelayUrl>?

    abstract fun saveRelayList(urlList: List<NormalizedRelayUrl>)

    fun create() {
        if (hasModified) {
            viewModelScope.launch(Dispatchers.IO) {
                saveRelayList(_relays.value.map { it.relay })
                clear()
            }
        }
    }

    fun loadRelayDocuments() {
        viewModelScope.launch(Dispatchers.IO) {
            _relays.value.forEach { item ->
                Nip11CachedRetriever.loadRelayInfo(
                    relay = item.relay,
                    okHttpClient = {
                        Amethyst.instance.okHttpClients.getHttpClient(account.shouldUseTorForClean(item.relay))
                    },
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
            val relayList = getRelayList() ?: emptyList()

            relayList
                .map {
                    relaySetupInfoBuilder(
                        normalized = it,
                        forcesTor =
                            account.torRelayState.flow.value
                                .useTor(it),
                    )
                }.distinctBy { it.relay }
                .sortedBy { it.relayStat.receivedBytes }
                .reversed()
        }
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
}
