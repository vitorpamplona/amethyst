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

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.IRelayClientListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.client.single.newSubId
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.CountMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip04Dm.messages.PrivateDmEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Immutable
data class RelayCountResult(
    val counts: List<CountEntry> = emptyList(),
) {
    @Immutable
    data class CountEntry(
        val label: String,
        val count: Int,
        val approximate: Boolean = false,
    )
}

class RelayEventCountViewModel : ViewModel(), IRelayClientListener {
    private val client: INostrClient get() = Amethyst.instance.client

    private val _counts = MutableStateFlow<Map<NormalizedRelayUrl, RelayCountResult>>(emptyMap())
    val counts = _counts.asStateFlow()

    // Maps subId -> (relay, filterIndex) so we can route count results
    private val subIdToRelay = mutableMapOf<String, Pair<NormalizedRelayUrl, Int>>()
    // Maps relay -> list of (subId, label) to track active queries
    private val relayQueries = mutableMapOf<NormalizedRelayUrl, MutableList<QueryInfo>>()

    private data class QueryInfo(
        val subId: String,
        val label: String,
        val filterIndex: Int,
    )

    fun queryCountsForRelays(queries: Map<NormalizedRelayUrl, List<CountFilter>>) {
        cleanup()
        client.subscribe(this)

        queries.forEach { (relay, filters) ->
            val queryInfos = mutableListOf<QueryInfo>()

            filters.forEachIndexed { index, countFilter ->
                val subId = newSubId()
                subIdToRelay[subId] = Pair(relay, index)
                queryInfos.add(QueryInfo(subId, countFilter.label, index))

                client.queryCount(
                    subId = subId,
                    filters = mapOf(relay to listOf(countFilter.filter)),
                )
            }

            relayQueries[relay] = queryInfos
        }
    }

    override fun onIncomingMessage(
        relay: IRelayClient,
        msgStr: String,
        msg: Message,
    ) {
        if (msg is CountMessage) {
            val (relayUrl, filterIndex) = subIdToRelay[msg.queryId] ?: return
            val queryInfos = relayQueries[relayUrl] ?: return
            val queryInfo = queryInfos.find { it.filterIndex == filterIndex } ?: return

            _counts.update { currentMap ->
                val currentResult = currentMap[relayUrl] ?: RelayCountResult()
                val updatedEntries = currentResult.counts.toMutableList()

                // Replace or add the entry for this filter
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

    private fun cleanup() {
        // Close existing queries
        subIdToRelay.keys.forEach { subId ->
            client.close(subId)
        }
        subIdToRelay.clear()
        relayQueries.clear()
        _counts.value = emptyMap()
        client.unsubscribe(this)
    }

    override fun onCleared() {
        cleanup()
        super.onCleared()
    }

    companion object {
        fun authorCountFilters(
            userPubKey: HexKey,
            relays: List<NormalizedRelayUrl>,
        ): Map<NormalizedRelayUrl, List<CountFilter>> =
            relays.associateWith {
                listOf(
                    CountFilter(
                        label = "events",
                        filter = Filter(authors = listOf(userPubKey)),
                    ),
                )
            }

        fun pTagCountFilters(
            userPubKey: HexKey,
            relays: List<NormalizedRelayUrl>,
        ): Map<NormalizedRelayUrl, List<CountFilter>> =
            relays.associateWith {
                listOf(
                    CountFilter(
                        label = "events",
                        filter = Filter(tags = mapOf("p" to listOf(userPubKey))),
                    ),
                )
            }

        fun dmCountFilters(
            userPubKey: HexKey,
            relays: List<NormalizedRelayUrl>,
        ): Map<NormalizedRelayUrl, List<CountFilter>> =
            relays.associateWith {
                listOf(
                    CountFilter(
                        label = "events",
                        filter =
                            Filter(
                                kinds = listOf(GiftWrapEvent.KIND, PrivateDmEvent.KIND),
                                tags = mapOf("p" to listOf(userPubKey)),
                            ),
                    ),
                )
            }

        fun totalCountFilters(relays: List<NormalizedRelayUrl>): Map<NormalizedRelayUrl, List<CountFilter>> =
            relays.associateWith {
                listOf(
                    CountFilter(
                        label = "events",
                        filter = Filter(kinds = null),
                    ),
                )
            }

        fun indexerCountFilters(relays: List<NormalizedRelayUrl>): Map<NormalizedRelayUrl, List<CountFilter>> =
            relays.associateWith {
                listOf(
                    CountFilter(
                        label = "kind 0",
                        filter = Filter(kinds = listOf(0)),
                    ),
                    CountFilter(
                        label = "kind 10002",
                        filter = Filter(kinds = listOf(10002)),
                    ),
                )
            }
    }
}

data class CountFilter(
    val label: String,
    val filter: Filter,
)
