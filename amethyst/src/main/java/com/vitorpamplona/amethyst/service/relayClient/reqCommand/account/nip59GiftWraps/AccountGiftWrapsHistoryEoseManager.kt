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

import com.vitorpamplona.amethyst.commons.relayClient.nip17Dm.filterGiftWrapsToPubkey
import com.vitorpamplona.amethyst.commons.relayClient.pagination.TimeWindowPagination
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.PerUserEoseManager
import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.WindowLoadTracker
import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.trackingListener
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.account.AccountQueryState
import com.vitorpamplona.amethyst.service.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.client.subscriptions.Subscription
import com.vitorpamplona.quartz.utils.Log
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
import java.util.concurrent.atomic.AtomicInteger

/**
 * Loads the account's NIP-17 gift-wrap **history** — everything older than the one-week live tail
 * ([AccountGiftWrapsEoseManager]) — in bounded, one-shot `[since, until]` slices.
 *
 * It is idle until the screens call [loadMore]: each call advances the floor one geometric step and
 * requests **only the new band** `[newFloor, previousFloor]`, never the whole window. Consecutive
 * slices are disjoint, so re-issuing the (advanced) filter on a reconnect does not re-stream older
 * slices — those already live in the cache. The 2-day NIP-17 margin (applied to the slice `since` by
 * [filterGiftWrapsToPubkey]) overlaps adjacent slices so a randomized outer timestamp can't open a gap.
 */
class AccountGiftWrapsHistoryEoseManager(
    client: INostrClient,
    allKeys: () -> Set<AccountQueryState>,
) : PerUserEoseManager<AccountQueryState>(client, allKeys) {
    override fun user(key: AccountQueryState) = key.account.userProfile()

    // The window's [TimeWindowPagination.since] is the oldest floor reached so far; it starts at the
    // live-tail floor (now − 1 week) and moves back one geometric step per loadMore.
    private val windows = ConcurrentHashMap<HexKey, TimeWindowPagination>()

    // The current slice to request per user, or absent until the first loadMore (manager stays idle).
    private val slices = ConcurrentHashMap<HexKey, Slice>()

    private data class Slice(
        val since: Long,
        val until: Long,
    )

    private fun windowFor(user: User) = windows.computeIfAbsent(user.pubkeyHex) { TimeWindowPagination(growthFactor = WINDOW_GROWTH_FACTOR) }

    /** The current history slice for [user] (logical, un-margined bounds), or null while idle. */
    fun currentSlice(user: User): Pair<Long, Long>? = slices[user.pubkeyHex]?.let { it.since to it.until }

    private val windowLoad = WindowLoadTracker("giftwrap.history")
    val loadingMore: StateFlow<Boolean> = windowLoad.loading

    // True once the floor reached the maximum lookback: nothing older to fetch.
    private val _exhausted = MutableStateFlow(false)
    val exhausted: StateFlow<Boolean> = _exhausted.asStateFlow()

    // Rooms-list auto-fill stall mark: the number of distinct private rooms shown the last time the
    // list auto-widened. The list stops widening once a step adds no new room (widening only pulls
    // older MESSAGES, which for a few busy correspondents can be thousands of events without a single
    // new room). Kept here, beside the window it guards, so the stall survives leaving and reopening
    // the Messages screen — a fresh UI-local counter would re-widen on every open.
    @Volatile
    var autoFillPrivateRoomMark: Int = Int.MIN_VALUE

    // Account scope for the window-load watchdog. Volatile: written on IO (newSub), read on UI (loadMore).
    @Volatile
    private var scope: CoroutineScope? = null

    override fun updateFilter(
        key: AccountQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter> {
        val slice = slices[user(key).pubkeyHex]
        if (!key.account.isWriteable() || slice == null) {
            windowLoad.setExpectedRelays(emptySet())
            return emptyList()
        }
        val relays = key.account.dmRelays.flow.value
        windowLoad.setExpectedRelays(relays.toSet())
        Log.d(TAG) { "[giftwrap.history] REQ slice [${daysAgo(slice.since)}d, ${daysAgo(slice.until)}d] on ${relays.size} relay(s)" }
        return relays.flatMap { relay ->
            filterGiftWrapsToPubkey(relay = relay, pubkey = user(key).pubkeyHex, since = slice.since, until = slice.until)
        }
    }

    private fun daysAgo(epochSeconds: Long) = (TimeUtils.now() - epochSeconds) / TimeUtils.ONE_DAY

    /** Widens the floor one geometric step back and requests only the new slice. No-op if exhausted. */
    fun loadMore(user: User) {
        val window = windowFor(user)
        if (window.isExhausted()) {
            Log.d(TAG) { "[giftwrap.history] loadMore ignored — already exhausted" }
            return
        }
        val until = window.since
        window.loadMore()
        slices[user.pubkeyHex] = Slice(since = window.since, until = until)
        _exhausted.value = window.isExhausted()
        Log.d(TAG) { "[giftwrap.history] loadMore slice [${daysAgo(window.since)}d, ${daysAgo(until)}d] (exhausted=${_exhausted.value})" }
        scope?.let { beginWindowLoad(user, it) }
        invalidateFilters()
    }

    /** Requests the entire remaining past in one slice `[maxLookback, currentFloor]`. */
    fun loadEverything(user: User) {
        val window = windowFor(user)
        if (window.isExhausted()) return
        val until = window.since
        window.loadAll()
        slices[user.pubkeyHex] = Slice(since = window.since, until = until)
        _exhausted.value = true
        Log.d(TAG) { "[giftwrap.history] loadEverything — slice [${daysAgo(window.since)}d, ${daysAgo(until)}d]" }
        scope?.let { beginWindowLoad(user, it) }
        invalidateFilters()
    }

    // Per-load instrumentation: how many gift wraps a slice pulled, and how many fell outside the band
    // the REQ actually asked for ([loadSince], the margined floor) — a non-zero out-of-band share means
    // a relay ignored the bounds. Volatile/atomic: the event hook runs on relay IO threads while the
    // summary collector reads on the account scope.
    @Volatile
    private var loadSince = 0L
    private val eventsThisLoad = AtomicInteger(0)
    private val outOfWindowThisLoad = AtomicInteger(0)

    @Volatile
    private var summaryJob: Job? = null

    private fun countEvent(createdAt: Long) {
        eventsThisLoad.incrementAndGet()
        if (createdAt < loadSince) outOfWindowThisLoad.incrementAndGet()
    }

    private fun beginWindowLoad(
        user: User,
        scope: CoroutineScope,
    ) {
        loadSince = (slices[user.pubkeyHex]?.since ?: windowFor(user).since) - TimeUtils.twoDays()
        eventsThisLoad.set(0)
        outOfWindowThisLoad.set(0)
        ensureSummaryLogger(scope)
        windowLoad.startLoading(scope)
    }

    private fun ensureSummaryLogger(scope: CoroutineScope) {
        if (summaryJob?.isActive == true) return
        summaryJob =
            scope.launch {
                var wasLoading = false
                windowLoad.loading.collect { loading ->
                    if (!loading && wasLoading) {
                        val total = eventsThisLoad.get()
                        val outOfWindow = outOfWindowThisLoad.get()
                        Log.d(TAG) {
                            "[giftwrap.history] load summary: $total event(s), $outOfWindow before floor " +
                                "(since=$loadSince, ${daysAgo(loadSince)}d back)"
                        }
                    }
                    wasLoading = loading
                }
            }
    }

    private val userJobMap = mutableMapOf<User, List<Job>>()

    @OptIn(FlowPreview::class)
    override fun newSub(key: AccountQueryState): Subscription {
        val user = user(key)
        scope = key.account.scope
        userJobMap[user]?.forEach { it.cancel() }
        userJobMap[user] =
            listOf(
                key.account.scope.launch(Dispatchers.IO) {
                    key.account.dmRelays.flow
                        .collectLatest { invalidateFilters() }
                },
            )

        return requestNewSubscription(
            windowLoad.trackingListener(
                onEachEvent = { event -> countEvent(event.createdAt) },
            ) { relay, filters -> newEose(key, relay, TimeUtils.now(), filters) },
        )
    }

    override fun endSub(
        key: User,
        subId: String,
    ) {
        super.endSub(key, subId)
        userJobMap[key]?.forEach { it.cancel() }
    }

    companion object {
        private const val TAG = "DMPagination"

        // The slice doubles each widen so a sparse history (or confirming nothing older exists)
        // reaches the 10-year backstop in ~10 requests instead of crawling a week at a time.
        private const val WINDOW_GROWTH_FACTOR = 2L
    }
}
