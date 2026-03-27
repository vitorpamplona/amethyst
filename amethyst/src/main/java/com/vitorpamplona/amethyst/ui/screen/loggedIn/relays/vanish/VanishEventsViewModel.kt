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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.vanish

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.downloadFirstEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.query
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip62RequestToVanish.RequestToVanishEvent
import com.vitorpamplona.quartz.nip62RequestToVanish.tags.RelayTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Stable
data class VanishEventItem(
    val event: RequestToVanishEvent,
    val relays: List<String>,
    val isAllRelays: Boolean,
)

enum class ComplianceStatus {
    UNTESTED,
    TESTING,
    COMPLIANT,
    NON_COMPLIANT,
    ERROR,
}

class VanishEventsViewModel : ViewModel() {
    lateinit var account: Account

    private val _vanishEvents = MutableStateFlow<List<VanishEventItem>>(emptyList())
    val vanishEvents = _vanishEvents.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _complianceResults = MutableStateFlow<Map<String, ComplianceStatus>>(emptyMap())
    val complianceResults = _complianceResults.asStateFlow()

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _vanishEvents.value = emptyList()
            _complianceResults.value = emptyMap()

            val connectedRelays = account.client.connectedRelaysFlow().value
            if (connectedRelays.isEmpty()) {
                _isLoading.value = false
                return@launch
            }

            val filter =
                Filter(
                    kinds = listOf(RequestToVanishEvent.KIND),
                    authors = listOf(account.pubKey),
                    limit = 100,
                )

            val filtersPerRelay = connectedRelays.associateWith { listOf(filter) }

            val events =
                account.client.query(filters = filtersPerRelay).mapNotNull { event ->
                    if (event is RequestToVanishEvent) {
                        val relayTags = event.vanishFromRelays()
                        val isAll = relayTags.contains(RelayTag.EVERYWHERE)
                        VanishEventItem(
                            event = event,
                            relays = relayTags,
                            isAllRelays = isAll,
                        )
                    } else {
                        null
                    }
                }
            _vanishEvents.value = events
            _isLoading.value = false
        }
    }

    fun testCompliance(
        relayUrl: String,
        vanishDate: Long,
    ) {
        val key = "$relayUrl:$vanishDate"
        _complianceResults.value = _complianceResults.value + (key to ComplianceStatus.TESTING)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val foundEvent =
                    account.client.downloadFirstEvent(
                        relay = relayUrl,
                        filter =
                            Filter(
                                authors = listOf(account.pubKey),
                                until = vanishDate - 1,
                                limit = 1,
                            ),
                    )

                _complianceResults.value += (
                    key to
                        if (foundEvent != null) {
                            ComplianceStatus.NON_COMPLIANT
                        } else {
                            ComplianceStatus.COMPLIANT
                        }
                )
            } catch (_: Exception) {
                _complianceResults.value += (key to ComplianceStatus.ERROR)
            }
        }
    }
}
