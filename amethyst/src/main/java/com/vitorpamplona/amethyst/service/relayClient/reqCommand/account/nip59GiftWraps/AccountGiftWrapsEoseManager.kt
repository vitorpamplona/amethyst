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
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.account.AccountQueryState
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class AccountGiftWrapsEoseManager(
    client: INostrClient,
    allKeys: () -> Set<AccountQueryState>,
) : PerUserEoseManager<AccountQueryState>(client, allKeys) {
    override fun user(key: AccountQueryState) = key.account.userProfile()

    // How far back in time gift wraps are requested, per account. Boot opens a small
    // window so the messages list is usable before the whole DM history is fetched and
    // decrypted; the rooms screen widens it via [loadMore] to fill the screen and to
    // prefetch as the user scrolls. The step grows geometrically so a sparse history
    // (or confirming there is nothing older) converges in a handful of requests; it is
    // kept in lockstep with the NIP-04 window so both DM protocols advance together.
    // Concurrent: windowFor is reached from the UI thread (loadMore / loadEverything) and from
    // Dispatchers.IO (updateFilter, via the bundled invalidation), so a plain HashMap would race.
    private val windows = ConcurrentHashMap<HexKey, TimeWindowPagination>()

    private fun windowFor(user: User) =
        windows.computeIfAbsent(user.pubkeyHex) {
            TimeWindowPagination(growthFactor = WINDOW_GROWTH_FACTOR).also {
                Log.d(TAG) { "opening initial gift-wrap window for pubkey=${user.pubkeyHex.take(8)}… since=${it.since} (${daysAgo(it.since)}d back)" }
            }
        }

    // A window is "loading" until every dmRelay it was sent to has answered (EOSE / live event),
    // or a timeout fires — not on the first EOSE, which a fast empty relay can trip prematurely.
    private val windowLoad = WindowLoadTracker()
    val loadingMore: StateFlow<Boolean> = windowLoad.loading

    // True once the window has reached the maximum lookback: there is no older history to fetch,
    // so the rooms screen can stop the auto-fill loop and show the real empty state.
    private val _exhausted = MutableStateFlow(false)
    val exhausted: StateFlow<Boolean> = _exhausted.asStateFlow()

    // The account scope to run the window-load watchdog on, captured when the subscription opens.
    // Volatile: written on Dispatchers.IO (newSub), read on the UI thread (loadMore/loadEverything).
    @Volatile
    private var scope: CoroutineScope? = null

    override fun updateFilter(
        key: AccountQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter> {
        // Only loads DMs if the account is writeable
        return if (key.account.isWriteable()) {
            val relays = key.account.dmRelays.flow.value
            windowLoad.setExpectedRelays(relays.toSet())
            val windowSince = windowFor(user(key)).since
            Log.d(TAG) {
                "updateFilter: pubkey=${user(key).pubkeyHex.take(8)}… requesting kind:1059 " +
                    "since=$windowSince (${daysAgo(windowSince)}d window) on ${relays.size} dmRelay(s): ${relays.map { it.url }}"
            }
            relays.flatMap { relay ->
                filterGiftWrapsToPubkey(
                    relay = relay,
                    pubkey = user(key).pubkeyHex,
                    since = windowSince,
                )
            }
        } else {
            windowLoad.setExpectedRelays(emptySet())
            Log.d(TAG) { "updateFilter: pubkey=${user(key).pubkeyHex.take(8)}… account not writeable, skipping" }
            emptyList()
        }
    }

    /**
     * Widens the gift-wrap time window for [user] one step back and re-issues the
     * subscription so older conversations stream in. Called by the rooms screen to
     * fill the screen and to prefetch older history as the user scrolls. No-op once
     * the window is [exhausted]. Kept in lockstep with the NIP-04 window.
     */
    fun loadMore(user: User) {
        val window = windowFor(user)
        if (window.isExhausted()) return
        val before = window.since
        window.loadMore()
        _exhausted.value = window.isExhausted()
        Log.d(TAG) {
            "loadMore: pubkey=${user.pubkeyHex.take(8)}… widening window since $before -> ${window.since} " +
                "(${daysAgo(window.since)}d back, was ${daysAgo(before)}d, exhausted=${_exhausted.value}), re-issuing subscription"
        }
        scope?.let { windowLoad.startLoading(it) }
        invalidateFilters()
    }

    /**
     * Jumps the gift-wrap window straight to the maximum lookback so a single REQ pulls the entire
     * history (the pre-windowing behavior), and marks it [exhausted] so auto-fill stops.
     */
    fun loadEverything(user: User) {
        val window = windowFor(user)
        if (window.isExhausted()) return
        window.loadAll()
        _exhausted.value = true
        Log.d(TAG) { "loadEverything: pubkey=${user.pubkeyHex.take(8)}… loading full history since ${window.since}" }
        scope?.let { windowLoad.startLoading(it) }
        invalidateFilters()
    }

    override fun newEose(
        key: AccountQueryState,
        relay: NormalizedRelayUrl,
        time: Long,
        filters: List<Filter>?,
    ) {
        windowLoad.onRelayResponded(relay)
        super.newEose(key, relay, time, filters)
    }

    private fun daysAgo(epochSeconds: Long) = (TimeUtils.now() - epochSeconds) / TimeUtils.ONE_DAY

    val userJobMap = mutableMapOf<User, List<Job>>()

    // Cold-boot instrumentation: when the subscription opened (ms), how many gift
    // wraps have arrived since, and whether we've already logged the first EOSE.
    // Concurrent because the listener callbacks below run on the relay reader threads
    // (several relays delivering events at once during the cold-boot flood).
    private val bootStartMs = ConcurrentHashMap<HexKey, Long>()
    private val bootEventCount = ConcurrentHashMap<HexKey, Int>()
    private val bootEoseLogged = ConcurrentHashMap.newKeySet<HexKey>()

    @OptIn(FlowPreview::class)
    override fun newSub(key: AccountQueryState): Subscription {
        val user = user(key)
        scope = key.account.scope
        userJobMap[user]?.forEach { it.cancel() }
        userJobMap[user] =
            listOf(
                key.account.scope.launch(Dispatchers.IO) {
                    key.account.dmRelays.flow.collectLatest {
                        invalidateFilters()
                    }
                },
            )

        // Reset and start the cold-boot timer for this subscription.
        val pubkey = user.pubkeyHex
        bootStartMs[pubkey] = System.currentTimeMillis()
        bootEventCount[pubkey] = 0
        bootEoseLogged.remove(pubkey)
        windowLoad.startLoading(key.account.scope)
        Log.d(TAG) { "cold boot: pubkey=${pubkey.take(8)}… opening gift-wrap subscription, starting to load messages" }

        // Custom listener so we can tell a real EOSE (load finished) apart from live
        // events; the base class routes both into newEose, which can't distinguish them.
        return requestNewSubscription(
            object : SubscriptionListener {
                override fun onEose(
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    if (bootEoseLogged.add(pubkey)) {
                        val elapsed = System.currentTimeMillis() - (bootStartMs[pubkey] ?: System.currentTimeMillis())
                        val count = bootEventCount[pubkey] ?: 0
                        Log.d(TAG) {
                            "cold boot: pubkey=${pubkey.take(8)}… initial load complete — first EOSE from ${relay.url} " +
                                "after ${elapsed}ms, $count gift wrap(s) received so far"
                        }
                    }
                    newEose(key, relay, TimeUtils.now(), forFilters)
                }

                override fun onEvent(
                    event: Event,
                    isLive: Boolean,
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    // Every event (stored backfill included) keeps the window-load watchdog alive,
                    // so a relay mid-flood is never mistaken for a finished window.
                    windowLoad.onActivity()
                    if (pubkey !in bootEoseLogged) {
                        bootEventCount.merge(pubkey, 1, Int::plus)
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
        bootStartMs.remove(key.pubkeyHex)
        bootEventCount.remove(key.pubkeyHex)
        bootEoseLogged.remove(key.pubkeyHex)
    }

    companion object {
        // Shared log tag for the DM time-window pagination. Filter logcat by this
        // tag to watch the boot window and scroll-driven backfill in real time.
        private const val TAG = "DMPagination"

        // The window doubles each widen so a sparse history (or confirming there is nothing
        // older) reaches the 10-year backstop in ~10 requests instead of crawling weekly.
        // Must match the NIP-04 window so both DM protocols advance in lockstep.
        const val WINDOW_GROWTH_FACTOR = 2L
    }
}
