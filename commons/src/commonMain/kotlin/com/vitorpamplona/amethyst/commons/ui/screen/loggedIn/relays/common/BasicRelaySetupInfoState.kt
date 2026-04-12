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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.count
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Platform-agnostic abstract base for relay setup ViewModels.
 *
 * Manages the relay list state (add/delete/move/clear) and count results.
 * Platform-specific operations (relay info loading, signer access, tor evaluation)
 * are provided via abstract methods that each platform implements.
 */
abstract class BasicRelaySetupInfoState : ViewModel() {
    private val _relays = MutableStateFlow<List<BasicRelaySetupInfo>>(emptyList())
    val relays = _relays.asStateFlow()

    private val _countResults = MutableStateFlow<Map<NormalizedRelayUrl, RelayCountResult>>(emptyMap())
    val countResults = _countResults.asStateFlow()

    var hasModified = false

    // --- Abstract: relay list source/destination ---

    abstract fun getRelayList(): List<NormalizedRelayUrl>?

    abstract suspend fun saveRelayList(urlList: List<NormalizedRelayUrl>)

    // --- Abstract: platform-specific hooks ---

    /** Launch a block that requires signer access (e.g. signing events). */
    abstract fun launchSigner(block: suspend () -> Unit)

    /** Load NIP-11 relay information and call [onPaid] with the result. */
    abstract suspend fun loadRelayInfo(
        relay: NormalizedRelayUrl,
        onPaid: (Boolean) -> Unit,
    )

    /** Build a [BasicRelaySetupInfo] for the given relay URL. */
    abstract fun buildRelaySetupInfo(normalized: NormalizedRelayUrl): BasicRelaySetupInfo

    /** Provide the [INostrClient] for relay count queries. */
    abstract fun getClient(): INostrClient

    // --- Open: count filters for relay event counts ---

    open fun countFilters(relayUrl: NormalizedRelayUrl): List<CountFilter> = emptyList()

    // --- Core state management ---

    fun create() {
        if (hasModified) {
            launchSigner {
                saveRelayList(_relays.value.map { it.relay })
                clear()
            }
        }
    }

    fun load() {
        clear()
        loadRelayDocuments()
        loadCounts()
    }

    fun loadRelayDocuments() {
        viewModelScope.launch(Dispatchers.IO) {
            _relays.value.forEach { item ->
                loadRelayInfo(item.relay) { paid ->
                    togglePaidRelay(item, paid)
                }
            }
        }
    }

    private fun loadCounts() {
        _countResults.value = emptyMap()

        val client = getClient()
        val relayList = _relays.value
        if (relayList.isEmpty()) return

        relayList.forEach { item ->
            val filters = countFilters(item.relay)
            if (filters.isEmpty()) return@forEach

            filters.forEach { countFilter ->
                viewModelScope.launch(Dispatchers.IO) {
                    val result = client.count(item.relay, countFilter.filter)
                    if (result != null) {
                        _countResults.update { currentMap ->
                            val current = currentMap[item.relay] ?: RelayCountResult()
                            val entries = current.counts.toMutableList()
                            val newEntry =
                                RelayCountResult.CountEntry(
                                    label = countFilter.label,
                                    count = result.count,
                                    approximate = result.approximate,
                                )
                            val existing = entries.indexOfFirst { it.label == countFilter.label }
                            if (existing >= 0) entries[existing] = newEntry else entries.add(newEntry)
                            currentMap + (item.relay to RelayCountResult(entries))
                        }
                    }
                }
            }
        }
    }

    open fun relayListBuilder(): List<BasicRelaySetupInfo> {
        val relayList = getRelayList() ?: emptyList()

        return relayList
            .map { buildRelaySetupInfo(it) }
            .distinctBy { it.relay }
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

    fun moveRelay(
        from: Int,
        to: Int,
    ) {
        _relays.update { list ->
            list.toMutableList().apply {
                add(to, removeAt(from))
            }
        }
        hasModified = true
    }

    fun deleteAll() {
        _relays.update { emptyList() }
        hasModified = true
    }

    fun togglePaidRelay(
        relay: BasicRelaySetupInfo,
        paid: Boolean,
    ) {
        _relays.update { it.replace(relay, relay.copy(paidRelay = paid)) }
    }
}
