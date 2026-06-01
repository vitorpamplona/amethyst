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
 * Always-on loader for the account's NIP-17 gift wraps (kind 1059). It owns the single DM time
 * window: boot opens a small window so the messages list is usable before the whole history is
 * fetched and decrypted, and the screens widen it ([loadMore] / [loadEverything]) as the user
 * scrolls. The NIP-04 loaders follow this window's [windowSince] so both DM protocols stay aligned.
 */
class AccountGiftWrapsEoseManager(
    client: INostrClient,
    allKeys: () -> Set<AccountQueryState>,
) : PerUserEoseManager<AccountQueryState>(client, allKeys) {
    override fun user(key: AccountQueryState) = key.account.userProfile()

    // Per-account window floor. Concurrent: windowFor is reached from the UI thread (loadMore /
    // loadEverything) and from Dispatchers.IO (updateFilter), so a plain HashMap would race.
    private val windows = ConcurrentHashMap<HexKey, TimeWindowPagination>()

    private fun windowFor(user: User) =
        windows.computeIfAbsent(user.pubkeyHex) {
            TimeWindowPagination(growthFactor = WINDOW_GROWTH_FACTOR).also {
                Log.d(TAG) { "[giftwrap] open window since=${it.since} (${daysAgo(it.since)}d back)" }
            }
        }

    /** The current lower bound (epoch seconds) of this account's gift-wrap window. */
    fun windowSince(user: User): Long = windowFor(user).since

    private fun daysAgo(epochSeconds: Long) = (TimeUtils.now() - epochSeconds) / TimeUtils.ONE_DAY

    // A window load is in flight until every dmRelay it was sent to has answered, the event stream
    // goes quiet, or a cap fires — never on just the first EOSE (a fast empty relay would trip it).
    private val windowLoad = WindowLoadTracker("giftwrap")
    val loadingMore: StateFlow<Boolean> = windowLoad.loading

    // True once the window reached the maximum lookback: nothing older to fetch.
    private val _exhausted = MutableStateFlow(false)
    val exhausted: StateFlow<Boolean> = _exhausted.asStateFlow()

    // Account scope for the window-load watchdog. Volatile: written on IO (newSub), read on UI (loadMore).
    @Volatile
    private var scope: CoroutineScope? = null

    // Per-load instrumentation. Each gift-wrap event the relays push during a load is counted, and
    // those whose (outer, randomized) created_at falls before the floor the REQ actually asked for are
    // counted separately — a relay ignoring `since` re-streams the whole history every widen, so a
    // total that keeps growing (and a non-zero out-of-window share) is the fingerprint of "getting all
    // events over and over again". [loadSince] is the *margined* floor (window.since − 2 days, matching
    // the wire REQ from filterGiftWrapsToPubkey), so the deliberate 2-day randomization band reads as
    // in-window and only a relay that under-shoots that floor is flagged. Volatile/atomic because the
    // event hook runs on the relay IO threads while the summary collector reads on the account scope.
    @Volatile
    private var loadSince = 0L
    private val eventsThisLoad = AtomicInteger(0)
    private val outOfWindowThisLoad = AtomicInteger(0)

    // The single tracker is shared across every account, so the summary collector is launched once
    // (not per newSub) — otherwise a second logged-in account would double every summary line.
    @Volatile
    private var summaryJob: Job? = null

    private fun countEvent(createdAt: Long) {
        eventsThisLoad.incrementAndGet()
        if (createdAt < loadSince) outOfWindowThisLoad.incrementAndGet()
    }

    /**
     * Starts a window load and resets the per-load counters in the same breath (synchronously, before
     * [WindowLoadTracker.startLoading] raises `loading`, so no in-flight event is counted against the
     * wrong load). The summary is emitted by a single collector that logs on each load's falling edge.
     */
    private fun beginWindowLoad(
        user: User,
        scope: CoroutineScope,
    ) {
        loadSince = windowFor(user).since - TimeUtils.twoDays()
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
                            "[giftwrap] load summary: $total event(s), $outOfWindow before floor " +
                                "(since=$loadSince, ${daysAgo(loadSince)}d back)"
                        }
                    }
                    wasLoading = loading
                }
            }
    }

    override fun updateFilter(
        key: AccountQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter> {
        if (!key.account.isWriteable()) {
            windowLoad.setExpectedRelays(emptySet())
            return emptyList()
        }
        val relays = key.account.dmRelays.flow.value
        windowLoad.setExpectedRelays(relays.toSet())
        val windowSince = windowFor(user(key)).since
        Log.d(TAG) { "[giftwrap] REQ since=$windowSince (${daysAgo(windowSince)}d) on ${relays.size} relay(s)" }
        return relays.flatMap { relay ->
            filterGiftWrapsToPubkey(relay = relay, pubkey = user(key).pubkeyHex, since = windowSince)
        }
    }

    /** Widens the window one (geometric) step back and re-issues the subscription. No-op if exhausted. */
    fun loadMore(user: User) {
        val window = windowFor(user)
        if (window.isExhausted()) {
            Log.d(TAG) { "[giftwrap] loadMore ignored — already exhausted" }
            return
        }
        val before = window.since
        window.loadMore()
        _exhausted.value = window.isExhausted()
        Log.d(TAG) { "[giftwrap] loadMore ${daysAgo(before)}d -> ${daysAgo(window.since)}d back (exhausted=${_exhausted.value})" }
        scope?.let { beginWindowLoad(user, it) }
        invalidateFilters()
    }

    /** Jumps the window to the maximum lookback so a single REQ pulls the entire history. */
    fun loadEverything(user: User) {
        val window = windowFor(user)
        if (window.isExhausted()) return
        window.loadAll()
        _exhausted.value = true
        Log.d(TAG) { "[giftwrap] loadEverything — full history (${daysAgo(window.since)}d back)" }
        scope?.let { beginWindowLoad(user, it) }
        invalidateFilters()
    }

    private val userJobMap = mutableMapOf<User, List<Job>>()

    @OptIn(FlowPreview::class)
    override fun newSub(key: AccountQueryState): Subscription {
        val user = user(key)
        scope = key.account.scope
        beginWindowLoad(user, key.account.scope)
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

        // The window doubles each widen so a sparse history (or confirming nothing older exists)
        // reaches the 10-year backstop in ~10 requests instead of crawling a week at a time.
        private const val WINDOW_GROWTH_FACTOR = 2L
    }
}
