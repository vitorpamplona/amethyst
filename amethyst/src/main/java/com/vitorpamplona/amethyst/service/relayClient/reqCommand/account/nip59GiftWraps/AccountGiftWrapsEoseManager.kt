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
package com.vitorpamplona.amethyst.service.relayClient.reqCommand.account.nip59GiftWraps

import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.PerUserEoseManager
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.account.AccountQueryState
import com.vitorpamplona.amethyst.service.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.subscriptions.Subscription
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AccountGiftWrapsEoseManager(
    client: INostrClient,
    allKeys: () -> Set<AccountQueryState>,
) : PerUserEoseManager<AccountQueryState>(client, allKeys) {
    companion object {
        const val TARGET_CHATROOMS = 50
        const val BATCH_SIZE = 100
    }

    override fun user(key: AccountQueryState) = key.account.userProfile()

    // Pagination state
    private var paginationCursor: Long? = null
    private var paginationStartTime: Long = TimeUtils.now()
    private var initialLoadComplete = false
    private var oldestEventInBatch: Long? = null
    private var eventsInBatch = 0
    private var batchEoseReceived = false

    private val _hasMore = MutableStateFlow(true)
    val hasMore = _hasMore.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore = _isLoadingMore.asStateFlow()

    private var currentKey: AccountQueryState? = null

    override fun updateFilter(
        key: AccountQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter> {
        if (!key.account.isWriteable()) return emptyList()

        currentKey = key
        val pubkey = user(key).pubkeyHex

        if (initialLoadComplete && !_isLoadingMore.value) {
            // Live mode: normal since-based tracking
            return key.account.dmRelays.flow.value.flatMap { relay ->
                filterGiftWrapsToPubkey(
                    relay = relay,
                    pubkey = pubkey,
                    since = since?.get(relay)?.time,
                )
            }
        }

        // Pagination mode (initial load or load-more)
        if (batchEoseReceived) {
            val roomCount =
                key.account.chatroomList.rooms
                    .size()

            if (roomCount >= TARGET_CHATROOMS || eventsInBatch == 0) {
                // Pagination phase done
                if (!initialLoadComplete) {
                    initialLoadComplete = true
                }
                _hasMore.value = eventsInBatch > 0
                _isLoadingMore.value = false
                batchEoseReceived = false

                // Switch to live mode
                return key.account.dmRelays.flow.value.flatMap { relay ->
                    filterGiftWrapsToPubkey(
                        relay = relay,
                        pubkey = pubkey,
                        since = paginationStartTime.minus(TimeUtils.twoDays()),
                    )
                }
            } else {
                // Need more: advance cursor
                paginationCursor = oldestEventInBatch?.minus(1)
            }
        }

        // Reset batch tracking for new page
        batchEoseReceived = false
        eventsInBatch = 0
        oldestEventInBatch = null

        return key.account.dmRelays.flow.value.flatMap { relay ->
            filterGiftWrapsToPubkeyPaginated(
                relay = relay,
                pubkey = pubkey,
                until = paginationCursor,
                limit = BATCH_SIZE,
            )
        }
    }

    val userJobMap = mutableMapOf<User, List<Job>>()

    @OptIn(FlowPreview::class)
    override fun newSub(key: AccountQueryState): Subscription {
        val user = user(key)
        userJobMap[user]?.forEach { it.cancel() }
        userJobMap[user] =
            listOf(
                key.account.scope.launch(Dispatchers.IO) {
                    key.account.dmRelays.flow.collectLatest {
                        invalidateFilters()
                    }
                },
            )

        currentKey = key
        paginationStartTime = TimeUtils.now()
        initialLoadComplete = false
        paginationCursor = null
        batchEoseReceived = false
        eventsInBatch = 0
        oldestEventInBatch = null
        _hasMore.value = true
        _isLoadingMore.value = false

        return requestNewSubscription(
            object : SubscriptionListener {
                override fun onEose(
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    if (initialLoadComplete && !_isLoadingMore.value) {
                        newEose(key, relay, TimeUtils.now(), forFilters)
                    } else {
                        batchEoseReceived = true
                        invalidateFilters()
                    }
                }

                override fun onEvent(
                    event: com.vitorpamplona.quartz.nip01Core.core.Event,
                    isLive: Boolean,
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    if (!initialLoadComplete || _isLoadingMore.value) {
                        eventsInBatch++
                        val eventTime = event.createdAt
                        val oldest = oldestEventInBatch
                        if (oldest == null || eventTime < oldest) {
                            oldestEventInBatch = eventTime
                        }
                    }
                    if (isLive) {
                        newEose(key, relay, TimeUtils.now(), forFilters)
                    }
                }
            },
        )
    }

    override fun endSub(
        key: User,
        subId: String,
    ) {
        super.endSub(key, subId)
        userJobMap[key]?.forEach { it.cancel() }
    }

    fun loadMore() {
        if (!initialLoadComplete || !_hasMore.value || _isLoadingMore.value) return
        _isLoadingMore.value = true
        batchEoseReceived = false
        eventsInBatch = 0
        oldestEventInBatch = null
        invalidateFilters()
    }
}
