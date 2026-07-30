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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.subscriptions

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "Why do I have this many subscriptions right now?"
 *
 * Pivots on **purpose**, not on relay. The relay-shaped view cannot answer the question: a probe
 * holding 670 filters across 168 relays looks like one unremarkable chip repeated on 168 rows,
 * whereas here it is a single line that reads `Notifications · 670 filters · 168 relays` and is
 * immediately obviously the largest thing running.
 *
 * Grouped by account first because several accounts are normally logged in and they do not share
 * relay sets — a total that mixes them cannot be acted on.
 *
 * Everything is a **snapshot**, polled rather than observed: `activeRequests` is a plain map read
 * off the relay pool with no change feed, and subscriptions churn constantly (every EOSE advances a
 * `since`). Polling on a visible screen is honest and cheap; a push feed would mean instrumenting
 * the pool for a diagnostic screen.
 */
@Immutable
data class SubscriptionEntityRow(
    /** Null when the filter named no entity — "the rest of this purpose", not a real entity. */
    val entityId: HexKey?,
    val detail: String?,
    val relays: List<NormalizedRelayUrl>,
    val filterCount: Int,
)

@Immutable
data class SubscriptionPurposeRow(
    val purpose: SubPurpose,
    val filterCount: Int,
    val relays: List<NormalizedRelayUrl>,
    val entities: List<SubscriptionEntityRow>,
)

@Immutable
data class SubscriptionAccountRow(
    /** Null groups everything not yet attributed to an account. Shown last, never hidden. */
    val accountPubKey: HexKey?,
    val filterCount: Int,
    val relays: List<NormalizedRelayUrl>,
    val purposes: List<SubscriptionPurposeRow>,
)

@Immutable
data class ActiveSubscriptionsState(
    val accounts: List<SubscriptionAccountRow> = emptyList(),
    val totalFilters: Int = 0,
    val totalRelays: Int = 0,
    /** Filters in flight that carry no purpose — assemblers not yet migrated. Honesty, not a bug. */
    val untaggedFilters: Int = 0,
)

class ActiveSubscriptionsViewModel : ViewModel() {
    private val _state = MutableStateFlow(ActiveSubscriptionsState())
    val state: StateFlow<ActiveSubscriptionsState> = _state.asStateFlow()

    /** Polls while the screen is on. [REFRESH_MS] is slow enough to be free, fast enough to feel live. */
    fun startPolling() {
        viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                _state.value = snapshot()
                kotlinx.coroutines.delay(REFRESH_MS)
            }
        }
    }

    private suspend fun snapshot(): ActiveSubscriptionsState =
        withContext(Dispatchers.Default) {
            val client = Amethyst.instance.client

            // account -> purpose -> entity -> relays / count
            val byAccount = mutableMapOf<HexKey?, MutableMap<SubPurpose, MutableMap<HexKey?, MutableList<NormalizedRelayUrl>>>>()
            val detailOf = mutableMapOf<Pair<SubPurpose, HexKey?>, String?>()
            var total = 0
            var untagged = 0
            val allRelays = mutableSetOf<NormalizedRelayUrl>()

            client.connectedRelaysFlow().value.forEach { relay ->
                client.activeRequests(relay).values.flatten().forEach { filter ->
                    total++
                    val explained = filter as? ExplainedFilter
                    if (explained == null) {
                        untagged++
                        return@forEach
                    }
                    allRelays.add(relay)
                    byAccount
                        .getOrPut(explained.accountPubKey) { mutableMapOf() }
                        .getOrPut(explained.purpose) { mutableMapOf() }
                        .getOrPut(explained.entityId) { mutableListOf() }
                        .add(relay)
                    detailOf[explained.purpose to explained.entityId] = explained.purposeDetail
                }
            }

            val accounts =
                byAccount
                    .map { (account, purposes) ->
                        val purposeRows =
                            purposes
                                .map { (purpose, entities) ->
                                    val entityRows =
                                        entities
                                            .map { (entityId, relays) ->
                                                SubscriptionEntityRow(
                                                    entityId = entityId,
                                                    detail = detailOf[purpose to entityId],
                                                    relays = relays.distinct().sortedBy { it.url },
                                                    filterCount = relays.size,
                                                )
                                            }.sortedByDescending { it.filterCount }
                                    SubscriptionPurposeRow(
                                        purpose = purpose,
                                        filterCount = entityRows.sumOf { it.filterCount },
                                        relays = entityRows.flatMap { it.relays }.distinct(),
                                        entities = entityRows,
                                    )
                                }.sortedByDescending { it.filterCount }
                        SubscriptionAccountRow(
                            accountPubKey = account,
                            filterCount = purposeRows.sumOf { it.filterCount },
                            relays = purposeRows.flatMap { it.relays }.distinct(),
                            purposes = purposeRows,
                        )
                    }
                    // unattributed group last, so it reads as a remainder rather than a headline
                    .sortedWith(compareBy<SubscriptionAccountRow> { it.accountPubKey == null }.thenByDescending { it.filterCount })

            ActiveSubscriptionsState(
                accounts = accounts,
                totalFilters = total,
                totalRelays = allRelays.size,
                untaggedFilters = untagged,
            )
        }

    companion object {
        const val REFRESH_MS = 2_000L
    }
}
