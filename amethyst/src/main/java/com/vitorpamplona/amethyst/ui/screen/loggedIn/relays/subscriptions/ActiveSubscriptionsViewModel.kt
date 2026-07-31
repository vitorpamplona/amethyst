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
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.IFeedTopNavPerRelayFilter
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
    /**
     * The top-nav selection behind a filter that names no entity — what a discovery filter is
     * searching *within*. Null for filters that name a real entity, which speaks for itself.
     */
    val scope: IFeedTopNavPerRelayFilter?,
    val detail: String?,
    val relays: List<NormalizedRelayUrl>,
    val filterCount: Int,
)

/**
 * What makes two filters the same row.
 *
 * [scopeKey] is the scope's **type**, not its contents: an author-based selection carries a
 * different slice of the follow list to every relay, so keying on contents would shatter "People you
 * follow" into one row per relay — the opposite of what this screen is for. Selections whose
 * contents are the same everywhere (a hashtag, a geohash) render theirs from the retained instance.
 */
private data class EntityKey(
    val entityId: HexKey?,
    val scopeKey: String?,
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
) {
    /** The largest purpose, so every card can draw its share against a common scale. */
    val busiestPurposeFilters: Int = accounts.flatMap { it.purposes }.maxOfOrNull { it.filterCount } ?: 0
}

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
            val byAccount = mutableMapOf<HexKey?, MutableMap<SubPurpose, MutableMap<EntityKey, MutableList<NormalizedRelayUrl>>>>()
            val detailOf = mutableMapOf<Pair<SubPurpose, EntityKey>, String?>()
            val scopeOf = mutableMapOf<Pair<SubPurpose, EntityKey>, IFeedTopNavPerRelayFilter>()
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
                    // Discovery filters name no entity — they go looking for things rather than
                    // serving known ones — but they do carry the selection they search within, which
                    // is what keeps them from collapsing into one nameless row per purpose.
                    val scopeKey = explained.scope?.let { it::class.simpleName }
                    // A batched filter serves several entities at once — relay-group state is one #d
                    // filter per host relay carrying every joined group on it — so it contributes a
                    // row to each of them rather than collapsing to "All".
                    val entities: List<HexKey?> = explained.entityIds?.takeIf { it.isNotEmpty() } ?: listOf(null)
                    entities.forEach { entityId ->
                        val key = EntityKey(entityId, scopeKey)
                        byAccount
                            .getOrPut(explained.accountPubKey) { mutableMapOf() }
                            .getOrPut(explained.purpose) { mutableMapOf() }
                            .getOrPut(key) { mutableListOf() }
                            .add(relay)
                        detailOf[explained.purpose to key] = explained.purposeDetail
                        explained.scope?.let { scopeOf.getOrPut(explained.purpose to key) { it } }
                    }
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
                                            .map { (key, relays) ->
                                                SubscriptionEntityRow(
                                                    entityId = key.entityId,
                                                    scope = scopeOf[purpose to key],
                                                    detail = detailOf[purpose to key],
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
