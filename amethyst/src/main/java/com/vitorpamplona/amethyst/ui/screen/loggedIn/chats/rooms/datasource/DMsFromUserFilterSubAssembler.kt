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
import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.WindowLoadTracker
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.account.nip59GiftWraps.AccountGiftWrapsEoseManager
import com.vitorpamplona.amethyst.service.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.subscriptions.Subscription
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class DMsFromUserFilterSubAssembler(
    client: INostrClient,
    allKeys: () -> Set<ChatroomListState>,
) : PerUserEoseManager<ChatroomListState>(client, allKeys) {
    // Same moving time window as the gift-wrap (NIP-17) loader, so the merged rooms list is
    // bounded uniformly across both DM protocols. Without this, NIP-04 loaded all history while
    // NIP-17 only loaded the recent window, so widening (which fills the screen / prefetches)
    // landed new NIP-17 rooms in the middle of the NIP-04 tail instead of extending the list end.
    // Same growth factor as the gift-wrap window keeps both advancing in lockstep.
    // Concurrent: windowFor is reached from the UI thread (loadMore / loadEverything) and from
    // Dispatchers.IO (updateFilter, via the bundled invalidation), so a plain HashMap would race.
    private val windows = ConcurrentHashMap<HexKey, TimeWindowPagination>()

    private fun windowFor(user: User) =
        windows.computeIfAbsent(user.pubkeyHex) {
            TimeWindowPagination(growthFactor = AccountGiftWrapsEoseManager.WINDOW_GROWTH_FACTOR)
        }

    // A window is "loading" until every relay it was sent to has answered (EOSE / live event),
    // or a timeout fires — not on the first EOSE, which a fast empty relay can trip prematurely.
    private val windowLoad = WindowLoadTracker()
    val loadingMore: StateFlow<Boolean> = windowLoad.loading

    // True once the window has reached the maximum lookback: no older history to fetch.
    private val _exhausted = MutableStateFlow(false)
    val exhausted: StateFlow<Boolean> = _exhausted.asStateFlow()

    // The account scope to run the window-load watchdog on, captured when the subscription opens.
    // Volatile: written on Dispatchers.IO (newSub), read on the UI thread (loadMore/loadEverything).
    @Volatile
    private var scope: CoroutineScope? = null

    override fun updateFilter(
        key: ChatroomListState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter>? =
        if (key.account.isWriteable()) {
            val homeRelays = key.account.homeRelays.flow.value
            val dmRelays = key.account.dmRelays.flow.value
            windowLoad.setExpectedRelays((homeRelays + dmRelays).toSet())
            val windowSince = windowFor(user(key)).since
            homeRelays.map {
                filterNip04DMsFromMe(key.account.userProfile(), it, windowSince)
            } +
                dmRelays.map {
                    filterNip04DMsToMe(key.account.userProfile(), it, windowSince)
                }
        } else {
            windowLoad.setExpectedRelays(emptySet())
            emptyList()
        }

    /**
     * Widens the NIP-04 time window for [user] one step back, kept in lockstep with the
     * gift-wrap window. No-op once the window is [exhausted].
     */
    fun loadMore(user: User) {
        val window = windowFor(user)
        if (window.isExhausted()) return
        window.loadMore()
        _exhausted.value = window.isExhausted()
        scope?.let { windowLoad.startLoading(it) }
        invalidateFilters()
    }

    /**
     * Jumps the NIP-04 window straight to the maximum lookback so a single REQ pulls the entire
     * history (the pre-windowing behavior), and marks it [exhausted] so auto-fill stops.
     */
    fun loadEverything(user: User) {
        val window = windowFor(user)
        if (window.isExhausted()) return
        window.loadAll()
        _exhausted.value = true
        scope?.let { windowLoad.startLoading(it) }
        invalidateFilters()
    }

    override fun newEose(
        key: ChatroomListState,
        relay: NormalizedRelayUrl,
        time: Long,
        filters: List<Filter>?,
    ) {
        windowLoad.onRelayResponded(relay)
        super.newEose(key, relay, time, filters)
    }

    override fun user(key: ChatroomListState) = key.account.userProfile()

    val userJobMap = mutableMapOf<User, List<Job>>()

    @OptIn(FlowPreview::class)
    override fun newSub(key: ChatroomListState): Subscription {
        val user = user(key)
        scope = key.account.scope
        windowLoad.startLoading(key.account.scope)
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

        // Custom listener (vs super.newSub) so every event — stored backfill included — keeps the
        // window-load watchdog alive; otherwise a NIP-04 flood would look "done" mid-stream.
        return requestNewSubscription(
            object : SubscriptionListener {
                override fun onEose(
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    newEose(key, relay, TimeUtils.now(), forFilters)
                }

                override fun onEvent(
                    event: Event,
                    isLive: Boolean,
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    windowLoad.onActivity()
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
}
