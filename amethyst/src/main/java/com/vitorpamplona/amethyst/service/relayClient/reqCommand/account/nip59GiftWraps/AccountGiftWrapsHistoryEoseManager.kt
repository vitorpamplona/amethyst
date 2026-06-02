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
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.PerUserEoseManager
import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.UntilLimitPager
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Loads the account's NIP-17 gift-wrap **history** — everything older than the one-week live tail
 * ([AccountGiftWrapsEoseManager]) — by **`until`+`limit` paging, per relay**.
 *
 * Idle until a screen calls [loadMore]. Each round asks every not-yet-empty relay for [PAGE_LIMIT]
 * gift wraps older than its own cursor (no `since`, so gaps are skipped). When a relay answers an
 * empty page with EOSE it is done; otherwise its cursor advances below the oldest wrap it sent. The
 * limit is **not** trusted as a stop signal (a relay may cap results on its own) — only an empty page
 * is. The whole history is [exhausted] once a full round advances no relay at all (every relay
 * empty-EOSE'd or only answered CLOSED), which is the gap-proof "nothing more is reachable" signal the
 * old time-slice model couldn't produce.
 */
class AccountGiftWrapsHistoryEoseManager(
    client: INostrClient,
    allKeys: () -> Set<AccountQueryState>,
) : PerUserEoseManager<AccountQueryState>(client, allKeys) {
    override fun user(key: AccountQueryState) = key.account.userProfile()

    private val pager = UntilLimitPager<HexKey>()

    // Users that have requested history at least once (else the manager stays idle, issuing no REQ).
    private val started = ConcurrentHashMap.newKeySet<HexKey>()

    // The relays the in-flight round asked, per user — used to tally the round on completion.
    private val askedRelays = ConcurrentHashMap<HexKey, Set<NormalizedRelayUrl>>()

    // The account behind each user pubkey, captured on subscribe so [loadMore] (UI thread) can read
    // the DM relay list without the key.
    private val accounts = ConcurrentHashMap<HexKey, Account>()

    // This manager is shared across logged-in accounts (one singleton coordinator), so the single
    // display flows below must follow whichever account is currently active. Per-account paging
    // cursors live in [pager], so switching away and back preserves progress; [exhaustedByUser] lets
    // the display flow repoint accurately on switch instead of leaking the previous account's state.
    @Volatile
    private var activeUser: HexKey? = null
    private val exhaustedByUser = ConcurrentHashMap<HexKey, Boolean>()

    private val windowLoad = WindowLoadTracker("giftwrap.history")
    val loadingMore: StateFlow<Boolean> = windowLoad.loading

    // True once a full round advanced no relay — nothing older is reachable.
    private val _exhausted = MutableStateFlow(false)
    val exhausted: StateFlow<Boolean> = _exhausted.asStateFlow()

    // Status surfaced to the loading card: how many relays the current page is asking, and the oldest
    // point paging has reached (epoch seconds, the deepest cursor).
    private val _relayCount = MutableStateFlow(0)
    val relayCount: StateFlow<Int> = _relayCount.asStateFlow()

    private val _reachedBack = MutableStateFlow<Long?>(null)
    val reachedBack: StateFlow<Long?> = _reachedBack.asStateFlow()

    // Rooms-list auto-fill stall mark: the number of THIS protocol's distinct rooms shown the last
    // time the list auto-widened it. The list stops widening once a step adds no new room of this
    // protocol (widening only pulls older MESSAGES, which for a few busy correspondents can be
    // thousands of events without a single new room). Kept here so the stall survives leaving and
    // reopening the Messages screen.
    @Volatile
    var autoFillRoomMark: Int = Int.MIN_VALUE

    // Account scope for the watchdog / round collector. Volatile: written on IO (newSub), read on UI.
    @Volatile
    private var scope: CoroutineScope? = null

    @Volatile
    private var roundJob: Job? = null

    // The user whose round is in flight, read by the round collector on completion.
    @Volatile
    private var lastRoundUser: User? = null

    // "Load entire history" mode: keep paging to the end without waiting for more scrolling.
    @Volatile
    private var autoLoadAll = false

    // History starts just below the live tail's one-week floor and pages backward from there.
    private fun startUntil() = TimeUtils.now() - AccountGiftWrapsEoseManager.LIVE_TAIL_SECONDS

    private fun daysAgo(epochSeconds: Long) = (TimeUtils.now() - epochSeconds) / TimeUtils.ONE_DAY

    override fun updateFilter(
        key: AccountQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter> {
        val user = user(key)
        if (!key.account.isWriteable() || user.pubkeyHex !in started) {
            windowLoad.setExpectedRelays(emptySet())
            return emptyList()
        }
        val relays = key.account.dmRelays.flow.value
        val active = pager.activeRelays(user.pubkeyHex, relays).toSet()
        askedRelays[user.pubkeyHex] = active
        windowLoad.setExpectedRelays(active)
        if (active.isEmpty()) return emptyList()
        Log.d(TAG) { "[giftwrap.history] REQ ${active.size} relay(s), limit=$PAGE_LIMIT (until ${daysAgo(pager.untilFor(user.pubkeyHex, active.first(), startUntil()))}d…)" }
        return active.flatMap { relay ->
            filterGiftWrapsToPubkey(
                relay = relay,
                pubkey = user.pubkeyHex,
                since = null,
                until = pager.untilFor(user.pubkeyHex, relay, startUntil()),
                limit = PAGE_LIMIT,
            )
        }
    }

    /** Requests the next backward page from every relay that still has older history. No-op if exhausted. */
    fun loadMore(user: User) {
        if (_exhausted.value) {
            Log.d(TAG) { "[giftwrap.history] loadMore ignored — exhausted" }
            return
        }
        val account = accounts[user.pubkeyHex] ?: return
        started.add(user.pubkeyHex)
        val active = pager.activeRelays(user.pubkeyHex, account.dmRelays.flow.value)
        if (active.isEmpty()) {
            exhaustedByUser[user.pubkeyHex] = true
            _exhausted.value = true
            return
        }
        pager.beginRound(user.pubkeyHex, active)
        lastRoundUser = user
        _relayCount.value = active.size
        _reachedBack.value = pager.deepestUntil(user.pubkeyHex, active, startUntil())
        Log.d(TAG) { "[giftwrap.history] loadMore → ${active.size} active relay(s)" }
        scope?.let {
            ensureRoundCollector(it)
            windowLoad.startLoading(it)
        }
        invalidateFilters()
    }

    /** Pages to the very end: each completed round auto-issues the next until the history is exhausted. */
    fun loadEverything(user: User) {
        if (_exhausted.value) return
        autoLoadAll = true
        Log.d(TAG) { "[giftwrap.history] loadEverything — paging to the end" }
        loadMore(user)
    }

    // Emits the round tally and the exhausted decision when the in-flight load settles. A round that
    // received no events means no relay had anything older → exhausted; otherwise (and in load-all
    // mode) keep paging.
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
                            exhaustedByUser[user.pubkeyHex] = count == 0
                            _exhausted.value = count == 0
                            _reachedBack.value = pager.deepestUntil(user.pubkeyHex, asked, startUntil())
                            Log.d(TAG) { "[giftwrap.history] round done: $count event(s), exhausted=${count == 0}" }
                            if (autoLoadAll && count > 0) loadMore(user)
                        }
                    }
                    wasLoading = loading
                }
            }
    }

    override fun newSub(key: AccountQueryState): Subscription {
        val user = user(key)
        scope = key.account.scope
        accounts[user.pubkeyHex] = key.account
        if (activeUser != user.pubkeyHex) {
            activeUser = user.pubkeyHex
            // Account switched: repoint the shared display flows to this account's own state.
            _exhausted.value = exhaustedByUser[user.pubkeyHex] ?: false
            _relayCount.value = 0
            _reachedBack.value = null
            autoFillRoomMark = Int.MIN_VALUE
        }
        return requestNewSubscription(historyListener(user, key))
    }

    private fun historyListener(
        user: User,
        key: AccountQueryState,
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
            }

            override fun onClosed(
                message: String,
                relay: NormalizedRelayUrl,
                forFilters: List<Filter>?,
            ) {
                // CLOSED (e.g. auth-required) is not "empty": don't mark the relay done — it may answer
                // after the auth handshake. It just settles the load so the spinner can clear.
                windowLoad.onRelaySettled(relay)
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
        private const val TAG = "DMPagination"

        // Asked of every relay per page. Large on purpose: we want a whole band in one round where the
        // relay allows it. A relay returning fewer is treated as its own cap, NOT as "nothing more" —
        // only an empty page + EOSE ends a relay.
        private const val PAGE_LIMIT = 10000
    }
}
