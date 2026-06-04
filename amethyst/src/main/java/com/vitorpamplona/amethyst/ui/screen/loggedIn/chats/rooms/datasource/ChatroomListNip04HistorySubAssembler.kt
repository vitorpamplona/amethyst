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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Loads older NIP-04 DMs (kind 4) for the rooms list by `until`+`limit` paging, **per relay,
 * independently** — the same model as the per-conversation loader
 * ([com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM.datasource.ChatroomNip04HistorySubAssembler])
 * and the gift-wrap history loader
 * ([com.vitorpamplona.amethyst.service.relayClient.reqCommand.account.nip59GiftWraps.AccountGiftWrapsHistoryEoseManager]),
 * but account-wide across the home (outbox, *from me*) + DM (inbox, *to me*) relays.
 *
 * Idle until [loadMore]. A single [loadMore] kicks off every relay that still has older history, and
 * from then on each relay drives its own pages off its own cursor, continuing the instant it EOSEs a
 * non-empty page ([onEose] → `pager.beginRound([relay])` + `invalidateFilters`; the subscription layer
 * diffs per relay, so re-issuing only re-REQs the relay whose cursor moved). A relay is *done* on an
 * empty page + EOSE; one that won't answer (auth CLOSE, unreachable, silent) is marked *stalled* but
 * kept open. The whole history is [exhausted] once the window settles — every relay done or stalled —
 * via the [WindowLoadTracker]'s silence + connect-grace backstops.
 */
class ChatroomListNip04HistorySubAssembler(
    client: INostrClient,
    allKeys: () -> Set<ChatroomListState>,
) : PerUserEoseManager<ChatroomListState>(client, allKeys) {
    private val pager = UntilLimitPager<HexKey>()
    private val started = ConcurrentHashMap.newKeySet<HexKey>()
    private val accounts = ConcurrentHashMap<HexKey, Account>()

    // Relays currently not advancing for a user (auth CLOSE / unreachable / silent). Tracked only for the
    // logs; these relays are NOT given up — they keep their subscription and keep trying to catch up.
    private val stalledRelays = ConcurrentHashMap<HexKey, MutableSet<NormalizedRelayUrl>>()

    // Shared across accounts (singleton coordinator): repoint the display flows to the active account on
    // switch instead of leaking the previous one's state. Cursors live in [pager].
    @Volatile
    private var activeUser: HexKey? = null
    private val exhaustedByUser = ConcurrentHashMap<HexKey, Boolean>()

    private val windowLoad = WindowLoadTracker("rooms.nip04.history", tracksReqSends = true, onAbandoned = ::onRelaysStalled)

    // Exposed instead of windowLoad.loading directly: that flow starts `true` (it assumes a load is in
    // flight from construction). Wired straight through, its `true` would wedge the scroll-driven loader
    // — whose gate is `!loading` — so the first loadMore could never fire. This starts false and only
    // goes true once paging actually begins (mirrored from windowLoad by the done collector).
    private val _loadingMore = MutableStateFlow(false)
    val loadingMore: StateFlow<Boolean> = _loadingMore.asStateFlow()

    private val _exhausted = MutableStateFlow(false)
    val exhausted: StateFlow<Boolean> = _exhausted.asStateFlow()

    private val _relayCount = MutableStateFlow(0)
    val relayCount: StateFlow<Int> = _relayCount.asStateFlow()

    private val _reachedBack = MutableStateFlow<Long?>(null)
    val reachedBack: StateFlow<Long?> = _reachedBack.asStateFlow()

    @Volatile
    private var scope: CoroutineScope? = null

    @Volatile
    private var doneJob: Job? = null

    // The user whose window is in flight, read by the done collector when it settles.
    @Volatile
    private var windowUser: User? = null

    // Whether a paging window is currently running. Tracked ourselves rather than read from
    // windowLoad.loading (which starts `true` before any window exists), so the first loadMore actually
    // starts the window instead of mistaking the construction-time `true` for an in-flight one.
    @Volatile
    private var windowActive = false

    // The history floor (live-tail boundary) pinned for the current window. startUntil() is `now − 1w`,
    // which drifts forward in real time — if it were recomputed per assembly, an un-advanced relay's
    // filter (until = floor) would change every time ANY relay's EOSE triggers invalidateFilters,
    // re-REQing relays that haven't moved. Pinning it per window keeps those filters stable so only a
    // relay whose cursor genuinely advanced is re-REQed.
    @Volatile
    private var windowFloor = 0L

    private fun startUntil() = TimeUtils.now() - AccountGiftWrapsEoseManager.LIVE_TAIL_SECONDS

    private fun floor() = windowFloor.takeIf { it != 0L } ?: startUntil()

    override fun user(key: ChatroomListState) = key.account.userProfile()

    override fun updateFilter(
        key: ChatroomListState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter>? {
        val user = user(key)
        if (!key.account.isWriteable() || user.pubkeyHex !in started) return emptyList()

        // Every relay that still has older history to ask for, each at its own cursor. A relay whose
        // cursor advanced since the last assembly re-REQs its next page; one still mid-page keeps its open
        // REQ; a done relay drops out (its REQ closes). This is what lets relays run independently.
        val homeRelays = key.account.homeRelays.flow.value
        val dmRelays = key.account.dmRelays.flow.value
        val active = pager.activeRelays(user.pubkeyHex, (homeRelays + dmRelays).toSet()).toSet()
        if (active.isEmpty()) return emptyList()
        DmRelayLog.log("rooms.nip04.history", key.account)
        Log.d("DMPagination") { "[rooms.nip04.history] REQ ${active.size} relay(s), limit=$PAGE_LIMIT fromMe(outbox)=${homeRelays.filter { it in active }.map { it.url }} toMe(inbox)=${dmRelays.filter { it in active }.map { it.url }}" }
        return homeRelays.filter { it in active }.map {
            filterNip04DMsFromMe(user, it, since = null, until = pager.untilFor(user.pubkeyHex, it, floor()), limit = PAGE_LIMIT)
        } +
            dmRelays.filter { it in active }.map {
                filterNip04DMsToMe(user, it, since = null, until = pager.untilFor(user.pubkeyHex, it, floor()), limit = PAGE_LIMIT)
            }
    }

    /** Starts (or resumes) per-relay paging of the NIP-04 history. Idempotent: safe to call again. */
    fun loadMore(user: User) {
        val account = accounts[user.pubkeyHex] ?: return
        started.add(user.pubkeyHex)
        val all = (account.homeRelays.flow.value + account.dmRelays.flow.value).toSet()
        if (all.isEmpty()) return
        if (pager.activeRelays(user.pubkeyHex, all).isEmpty()) {
            // Everything already paged to the bottom.
            exhaustedByUser[user.pubkeyHex] = true
            _exhausted.value = true
            return
        }
        _exhausted.value = false
        DmRelayLog.log("rooms.nip04.history", account)
        windowUser = user
        scope?.let {
            ensureDoneCollector(it)
            // One window spanning the whole per-relay pagination: it settles a relay only on that relay's
            // empty-EOSE (done) or when it goes silent/stalled, never on a mid-history page, so the
            // spinner tracks "is anything still advancing" rather than any single page. Start it only if
            // none is running — a re-entrant loadMore (the scroll loader re-firing mid-pagination) must
            // not reset the window and forget the relays that already finished.
            if (!windowActive) {
                windowActive = true
                windowFloor = startUntil()
                // Populate the relay count BEFORE raising the spinner, so the status card never renders a
                // "loading from 0 relays" frame between loadingMore flipping true and the first progress.
                updateStatus(user)
                _loadingMore.value = true
                windowLoad.startLoading(it)
            }
            windowLoad.setExpectedRelays(all)
        }
        updateStatus(user)
        Log.d("DMPagination") { "[rooms.nip04.history] paging ${all.size} relay(s) independently: ${all.map { it.url }}" }
        invalidateFilters()
    }

    // Mirrors the window's loading state into [_loadingMore] and, when it settles (every relay done or
    // stalled), flips [exhausted] and clears [windowActive] so the next loadMore can start a fresh window.
    private fun ensureDoneCollector(scope: CoroutineScope) {
        if (doneJob?.isActive == true) return
        doneJob =
            scope.launch {
                var wasLoading = false
                windowLoad.loading.collect { loading ->
                    _loadingMore.value = loading && windowActive
                    if (!loading && wasLoading) {
                        windowActive = false
                        _loadingMore.value = false
                        windowUser?.let { user ->
                            exhaustedByUser[user.pubkeyHex] = true
                            if (activeUser == user.pubkeyHex) _exhausted.value = true
                            updateStatus(user)
                            logSettleSummary(user)
                        }
                    }
                    wasLoading = loading
                }
            }
    }

    // WindowLoadTracker reports relays that accepted a REQ then went silent, or never got their REQ out.
    // We do NOT give up on them (they may simply be slow and need to catch up) — we just record them as
    // stalled for the logs and let them keep their open subscription.
    private fun onRelaysStalled(relays: Set<NormalizedRelayUrl>) {
        started.forEach { pk -> relays.forEach { markStalled(pk, it, "no response (silence/connect timeout)") } }
    }

    // Records [relay] as not currently advancing for [pk] and logs it once (the first time it stalls in
    // this window). The relay is kept — it kept its subscription and keeps trying to catch up.
    private fun markStalled(
        pk: HexKey,
        relay: NormalizedRelayUrl,
        reason: String,
    ) {
        val firstTime = stalledRelays.getOrPut(pk) { ConcurrentHashMap.newKeySet() }.add(relay)
        if (firstTime) Log.d("DMPagination") { "[rooms.nip04.history] ${relay.url} stalled — $reason (kept open, still trying)" }
    }

    private fun updateStatus(user: User) {
        val account = accounts[user.pubkeyHex]
        val all = account?.let { (it.homeRelays.flow.value + it.dmRelays.flow.value).toSet() } ?: emptySet()
        // "Asking N relays" on the status card: the ones still being paged (done relays have dropped out).
        _relayCount.value = pager.activeRelays(user.pubkeyHex, all).size
        // Over ALL relays, not just the still-active ones: a relay that finished keeps its deep cursor, so
        // "reached back to X" stays monotonic instead of jumping back to a newer date when the deepest
        // relay drops out of the active set.
        _reachedBack.value = pager.deepestUntil(user.pubkeyHex, all, floor())
    }

    // A one-line breakdown of where each relay landed when the window settles — the snapshot to reach for
    // when history didn't load tomorrow: who reached the bottom vs. who is still being retried.
    private fun logSettleSummary(user: User) {
        val account = accounts[user.pubkeyHex] ?: return
        val all = (account.homeRelays.flow.value + account.dmRelays.flow.value).toSet()
        val done = all.filter { pager.isDone(user.pubkeyHex, it) }.map { it.url }
        val stillTrying = all.filterNot { pager.isDone(user.pubkeyHex, it) }.map { it.url }
        Log.d("DMPagination") { "[rooms.nip04.history] settled — done=$done still-trying=$stillTrying" }
    }

    override fun newSub(key: ChatroomListState): Subscription {
        val user = user(key)
        scope = key.account.scope
        accounts[user.pubkeyHex] = key.account
        if (activeUser != user.pubkeyHex) {
            activeUser = user.pubkeyHex
            // Account switched: repoint the shared display flows to this account's own state.
            _exhausted.value = exhaustedByUser[user.pubkeyHex] ?: false
            _relayCount.value = 0
            _reachedBack.value = null
        }
        return requestNewSubscription(historyListener(user, key))
    }

    private fun historyListener(
        user: User,
        key: ChatroomListState,
    ): SubscriptionListener =
        object : SubscriptionListener {
            override fun onSubscriptionStarted(
                relay: String,
                forFilters: List<Filter>,
            ) {
                windowLoad.onReqSent(relay)
            }

            override fun onEvent(
                event: Event,
                isLive: Boolean,
                relay: NormalizedRelayUrl,
                forFilters: List<Filter>?,
            ) {
                windowLoad.onRelayEvent(relay)
                pager.onEvent(user.pubkeyHex, relay, event.createdAt)
                stalledRelays[user.pubkeyHex]?.remove(relay)
            }

            override fun onEose(
                relay: NormalizedRelayUrl,
                forFilters: List<Filter>?,
            ) {
                stalledRelays[user.pubkeyHex]?.remove(relay)
                pager.onEose(user.pubkeyHex, relay)
                if (pager.isDone(user.pubkeyHex, relay)) {
                    // Reached the bottom on this relay: settle it for the spinner, nothing more to ask.
                    windowLoad.onRelaySettled(relay)
                    Log.d("DMPagination") { "[rooms.nip04.history] ${relay.url} reached the bottom (done)" }
                } else {
                    // This page had events: reset only this relay's tally and let it continue to its next
                    // page immediately, independent of every other relay.
                    pager.beginRound(user.pubkeyHex, listOf(relay))
                }
                newEose(key, relay, TimeUtils.now(), forFilters)
                updateStatus(user)
                invalidateFilters()
            }

            override fun onClosed(
                message: String,
                relay: NormalizedRelayUrl,
                forFilters: List<Filter>?,
            ) {
                // A relay may demand auth we can't satisfy and CLOSE. It's stalled, not done — keep its
                // subscription so the pool can re-auth and it can catch up — but don't let it hold the
                // spinner.
                windowLoad.onRelaySettled(relay)
                markStalled(user.pubkeyHex, relay, "CLOSED: $message")
            }

            override fun onCannotConnect(
                relay: NormalizedRelayUrl,
                message: String,
                forFilters: List<Filter>?,
            ) {
                windowLoad.onRelaySettled(relay)
                markStalled(user.pubkeyHex, relay, "cannot connect: $message")
            }
        }

    companion object {
        private const val PAGE_LIMIT = 10000
    }
}
