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

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.DmRelayLog
import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.PerUserEoseManager
import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.UntilLimitPager
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
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Loads older NIP-04 DMs (kind 4) for the rooms list by `until`+`limit` paging, per relay — the same
 * gap-proof model as [com.vitorpamplona.amethyst.service.relayClient.reqCommand.account.nip59GiftWraps.AccountGiftWrapsHistoryEoseManager],
 * but for kind 4 (exact timestamps, no margin) across the account's home + DM relays. Idle until
 * [loadMore]; a relay is done on an empty page + EOSE; the whole history is [exhausted] once a round
 * advances no relay.
 */
class ChatroomListNip04HistorySubAssembler(
    client: INostrClient,
    allKeys: () -> Set<ChatroomListState>,
) : PerUserEoseManager<ChatroomListState>(client, allKeys) {
    private val pager = UntilLimitPager<HexKey>()
    private val started = ConcurrentHashMap.newKeySet<HexKey>()
    private val askedRelays = ConcurrentHashMap<HexKey, Set<NormalizedRelayUrl>>()
    private val accounts = ConcurrentHashMap<HexKey, Account>()

    // Shared across accounts (singleton coordinator): repoint the display flows to the active account
    // on switch instead of leaking the previous one's exhausted/mark state. Cursors live in [pager].
    @Volatile
    private var activeUser: HexKey? = null
    private val exhaustedByUser = ConcurrentHashMap<HexKey, Boolean>()

    // No-progress guard: skip re-issuing an identical round that brought nothing (see the gift-wrap
    // history manager's twin); cleared by onEose.
    @Volatile
    private var lastAskedActive: Set<NormalizedRelayUrl> = emptySet()

    @Volatile
    private var lastRoundEventCount = -1

    private val windowLoad = WindowLoadTracker("rooms.nip04.history")
    val loadingMore: StateFlow<Boolean> = windowLoad.loading

    private val _exhausted = MutableStateFlow(false)
    val exhausted: StateFlow<Boolean> = _exhausted.asStateFlow()

    private val _relayCount = MutableStateFlow(0)
    val relayCount: StateFlow<Int> = _relayCount.asStateFlow()

    private val _reachedBack = MutableStateFlow<Long?>(null)
    val reachedBack: StateFlow<Long?> = _reachedBack.asStateFlow()

    @Volatile
    private var scope: CoroutineScope? = null

    @Volatile
    private var roundJob: Job? = null

    // Backoff retry after a no-progress, not-exhausted round (relays failed to answer cleanly rather
    // than empty-EOSE'ing), so a transient connect-storm failure recovers instead of stalling forever.
    @Volatile
    private var retryJob: Job? = null

    @Volatile
    private var lastRoundUser: User? = null

    @Volatile
    private var autoLoadAll = false

    private fun startUntil() = TimeUtils.now() - AccountGiftWrapsEoseManager.LIVE_TAIL_SECONDS

    override fun user(key: ChatroomListState) = key.account.userProfile()

    override fun updateFilter(
        key: ChatroomListState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter>? {
        val user = user(key)
        if (!key.account.isWriteable() || user.pubkeyHex !in started) {
            windowLoad.setExpectedRelays(emptySet())
            return emptyList()
        }
        val homeRelays = key.account.homeRelays.flow.value
        val dmRelays = key.account.dmRelays.flow.value
        val active = pager.activeRelays(user.pubkeyHex, (homeRelays + dmRelays).toSet()).toSet()
        askedRelays[user.pubkeyHex] = active
        windowLoad.setExpectedRelays(active)
        if (active.isEmpty()) return emptyList()
        DmRelayLog.log("rooms.nip04.history", key.account)
        Log.d("DMPagination") { "[rooms.nip04.history] REQ ${active.size} relay(s), limit=$PAGE_LIMIT fromMe(outbox)=${homeRelays.filter { it in active }.map { it.url }} toMe(inbox)=${dmRelays.filter { it in active }.map { it.url }}" }
        return homeRelays.filter { it in active }.map {
            filterNip04DMsFromMe(user, it, since = null, until = pager.untilFor(user.pubkeyHex, it, startUntil()), limit = PAGE_LIMIT)
        } +
            dmRelays.filter { it in active }.map {
                filterNip04DMsToMe(user, it, since = null, until = pager.untilFor(user.pubkeyHex, it, startUntil()), limit = PAGE_LIMIT)
            }
    }

    /** Requests the next backward page from every relay that still has older NIP-04 history. */
    fun loadMore(user: User) {
        if (_exhausted.value) return
        val account = accounts[user.pubkeyHex] ?: return
        started.add(user.pubkeyHex)
        val all = (account.homeRelays.flow.value + account.dmRelays.flow.value).toSet()
        val active = pager.activeRelays(user.pubkeyHex, all)
        if (active.isEmpty()) {
            exhaustedByUser[user.pubkeyHex] = true
            _exhausted.value = true
            return
        }
        val activeSet = active.toSet()
        if (activeSet == lastAskedActive && lastRoundEventCount == 0) {
            Log.d("DMPagination") { "[rooms.nip04.history] loadMore skipped — no progress on the same relays" }
            return
        }
        lastAskedActive = activeSet
        pager.beginRound(user.pubkeyHex, active)
        lastRoundUser = user
        _relayCount.value = active.size
        _reachedBack.value = pager.deepestUntil(user.pubkeyHex, active, startUntil())
        Log.d("DMPagination") { "[rooms.nip04.history] loadMore → ${active.size} active relay(s)" }
        scope?.let {
            ensureRoundCollector(it)
            windowLoad.startLoading(it)
        }
        invalidateFilters()
    }

    /** Pages to the end: each completed round auto-issues the next until exhausted. */
    fun loadEverything(user: User) {
        if (_exhausted.value) return
        autoLoadAll = true
        loadMore(user)
    }

    private fun ensureRoundCollector(scope: CoroutineScope) {
        if (roundJob?.isActive == true) return
        roundJob =
            scope.launch {
                var wasLoading = false
                windowLoad.loading.collect { loading ->
                    if (!loading && wasLoading) {
                        val user = lastRoundUser
                        if (user != null) {
                            val asked = askedRelays[user.pubkeyHex] ?: emptySet()
                            val count = pager.roundEventCount(user.pubkeyHex, asked)
                            lastRoundEventCount = count
                            val account = accounts[user.pubkeyHex]
                            val allRelays = account?.let { (it.homeRelays.flow.value + it.dmRelays.flow.value).toSet() } ?: emptySet()
                            // Exhausted ONLY when every relay returned an empty page + EOSE; CLOSED /
                            // unanswered relays are not finished, so keep loading them.
                            val exhaustedNow = allRelays.isNotEmpty() && pager.activeRelays(user.pubkeyHex, allRelays).isEmpty()
                            exhaustedByUser[user.pubkeyHex] = exhaustedNow
                            _exhausted.value = exhaustedNow
                            _reachedBack.value = pager.deepestUntil(user.pubkeyHex, asked, startUntil())
                            Log.d("DMPagination") { "[rooms.nip04.history] round done: $count event(s), exhausted=$exhaustedNow" }
                            if (autoLoadAll && !exhaustedNow) {
                                loadMore(user)
                            } else if (!exhaustedNow && count == 0) {
                                // No progress and not exhausted: relays failed to answer cleanly rather
                                // than empty-EOSE'ing. Retry after a backoff so a transient failure
                                // recovers, paced so a rate-limited relay isn't hammered.
                                retryJob?.cancel()
                                retryJob =
                                    scope.launch {
                                        delay(NO_PROGRESS_RETRY_MS)
                                        if (!_exhausted.value && !windowLoad.loading.value) {
                                            lastAskedActive = emptySet()
                                            Log.d("DMPagination") { "[rooms.nip04.history] retry after no-progress round" }
                                            loadMore(user)
                                        }
                                    }
                            }
                        }
                    }
                    wasLoading = loading
                }
            }
    }

    override fun newSub(key: ChatroomListState): Subscription {
        val user = user(key)
        scope = key.account.scope
        accounts[user.pubkeyHex] = key.account
        if (activeUser != user.pubkeyHex) {
            activeUser = user.pubkeyHex
            _exhausted.value = exhaustedByUser[user.pubkeyHex] ?: false
            _relayCount.value = 0
            _reachedBack.value = null
            lastAskedActive = emptySet()
            lastRoundEventCount = -1
        }
        return requestNewSubscription(historyListener(user, key))
    }

    // Flips to exhausted only once every relay has returned an empty page + EOSE. Sets true only.
    private fun markExhaustedIfAllDone(user: User) {
        val account = accounts[user.pubkeyHex] ?: return
        val allRelays = (account.homeRelays.flow.value + account.dmRelays.flow.value).toSet()
        if (allRelays.isNotEmpty() && pager.activeRelays(user.pubkeyHex, allRelays).isEmpty()) {
            exhaustedByUser[user.pubkeyHex] = true
            if (activeUser == user.pubkeyHex) _exhausted.value = true
        }
    }

    private fun historyListener(
        user: User,
        key: ChatroomListState,
    ): SubscriptionListener =
        object : SubscriptionListener {
            override fun onEvent(
                event: Event,
                isLive: Boolean,
                relay: NormalizedRelayUrl,
                forFilters: List<Filter>?,
            ) {
                windowLoad.onRelayEvent(relay)
                pager.onEvent(user.pubkeyHex, relay, event.createdAt)
            }

            override fun onEose(
                relay: NormalizedRelayUrl,
                forFilters: List<Filter>?,
            ) {
                pager.onEose(user.pubkeyHex, relay)
                windowLoad.onRelaySettled(relay)
                newEose(key, relay, TimeUtils.now(), forFilters)
                lastRoundEventCount = -1
                markExhaustedIfAllDone(user)
            }

            override fun onClosed(
                message: String,
                relay: NormalizedRelayUrl,
                forFilters: List<Filter>?,
            ) {
                windowLoad.onRelaySettled(relay)
                if (pager.onClosed(user.pubkeyHex, relay)) markExhaustedIfAllDone(user)
            }

            override fun onCannotConnect(
                relay: NormalizedRelayUrl,
                message: String,
                forFilters: List<Filter>?,
            ) {
                windowLoad.onRelaySettled(relay)
            }
        }

    companion object {
        private const val PAGE_LIMIT = 10000

        // Backoff before retrying a no-progress, not-exhausted round (transient relay failure).
        private const val NO_PROGRESS_RETRY_MS = 5_000L
    }
}
