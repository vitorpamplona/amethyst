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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.datasource

import com.vitorpamplona.amethyst.commons.relayClient.pagination.TimeWindowPagination
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.PerUserEoseManager
import com.vitorpamplona.amethyst.service.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.client.subscriptions.Subscription
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DMsFromUserFilterSubAssembler(
    client: INostrClient,
    allKeys: () -> Set<ChatroomListState>,
) : PerUserEoseManager<ChatroomListState>(client, allKeys) {
    // Same moving time window as the gift-wrap (NIP-17) loader, so the merged rooms list is
    // bounded uniformly across both DM protocols. Without this, NIP-04 loaded all history while
    // NIP-17 only loaded the recent window, so scroll-to-end (which widens the windows) landed
    // new NIP-17 rooms in the middle of the NIP-04 tail instead of extending the list end.
    private val windows = mutableMapOf<HexKey, TimeWindowPagination>()

    private fun windowFor(user: User) = windows.getOrPut(user.pubkeyHex) { TimeWindowPagination() }

    private val _loadingMore = MutableStateFlow(false)
    val loadingMore: StateFlow<Boolean> = _loadingMore.asStateFlow()

    override fun updateFilter(
        key: ChatroomListState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter>? =
        if (key.account.isWriteable()) {
            val windowSince = windowFor(user(key)).since
            key.account.homeRelays.flow.value.map {
                filterNip04DMsFromMe(key.account.userProfile(), it, windowSince)
            } +
                key.account.dmRelays.flow.value.map {
                    filterNip04DMsToMe(key.account.userProfile(), it, windowSince)
                }
        } else {
            emptyList()
        }

    /** Widens the NIP-04 time window for [user] one step back. Kept in lockstep with the gift-wrap window. */
    fun loadMore(user: User) {
        windowFor(user).loadMore()
        _loadingMore.value = true
        invalidateFilters()
    }

    override fun newEose(
        key: ChatroomListState,
        relay: NormalizedRelayUrl,
        time: Long,
        filters: List<Filter>?,
    ) {
        if (_loadingMore.value) _loadingMore.value = false
        super.newEose(key, relay, time, filters)
    }

    override fun user(key: ChatroomListState) = key.account.userProfile()

    val userJobMap = mutableMapOf<User, List<Job>>()

    @OptIn(FlowPreview::class)
    override fun newSub(key: ChatroomListState): Subscription {
        val user = user(key)
        userJobMap[user]?.forEach { it.cancel() }
        userJobMap[user] =
            listOf(
                key.account.scope.launch(Dispatchers.IO) {
                    key.account.homeRelays.flow.collectLatest {
                        invalidateFilters()
                    }
                },
                key.account.scope.launch(Dispatchers.IO) {
                    key.account.dmRelays.flow.collectLatest {
                        invalidateFilters()
                    }
                },
            )

        return super.newSub(key)
    }

    override fun endSub(
        key: User,
        subId: String,
    ) {
        super.endSub(key, subId)
        userJobMap[key]?.forEach { it.cancel() }
    }
}
