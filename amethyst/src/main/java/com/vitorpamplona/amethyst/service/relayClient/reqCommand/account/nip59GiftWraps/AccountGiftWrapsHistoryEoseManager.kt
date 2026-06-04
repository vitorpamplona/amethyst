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
import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.DmRelayLog
import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.PerUserEoseManager
import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.RelayPagingProgress
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
 * ([AccountGiftWrapsEoseManager]) — by **`until`+`limit` paging, per relay, independently**.
 *
 * There are no lock-step rounds: a single [loadMore] kicks off every relay that still has older
 * history, and from then on each relay drives its own pages off its own cursor, continuing the instant
 * it EOSEs a non-empty page ([onEose] → `pager.beginRound([relay])` + `invalidateFilters`; the
 * subscription layer diffs per relay, so re-issuing only re-REQs the relay whose cursor moved). Fast
 * relays race to the bottom of the history in back-to-back pages while slow / auth-walled relays catch
 * up at their own pace in the background — none holds the others back. This mirrors the per-conversation
 * NIP-04 loader ([com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM.datasource.ChatroomNip04HistorySubAssembler]).
 *
 * A relay is *done* once it answers an empty page (nothing older). A relay that won't answer (auth
 * CLOSE, unreachable, silent) is marked *stalled* for the logs but keeps its subscription open and keeps
 * trying. The [loadingMore] spinner reflects whether anything is still actively advancing across one
 * window spanning the whole walk; it clears — and [exhausted] flips — once every relay is either done or
 * stalled (the [WindowLoadTracker] settles silent / unreachable relays via its silence + connect-grace
 * backstops), without waiting on the slow ones beyond that.
 */
class AccountGiftWrapsHistoryEoseManager(
    client: INostrClient,
    allKeys: () -> Set<AccountQueryState>,
) : PerUserEoseManager<AccountQueryState>(client, allKeys) {
    override fun user(key: AccountQueryState) = key.account.userProfile()

    // Per-relay cursors, keyed by account pubkey so switching accounts preserves each one's progress.
    private val pager = UntilLimitPager<HexKey>()

    // Users that have requested history at least once (else the manager stays idle, issuing no REQ).
    private val started = ConcurrentHashMap.newKeySet<HexKey>()

    // The account behind each user pubkey, captured on subscribe so [loadMore] (UI thread) can read the
    // DM relay list without the key.
    private val accounts = ConcurrentHashMap<HexKey, Account>()

    // Relays currently not advancing for a user (auth CLOSE / unreachable / silent). Tracked only for the
    // logs; these relays are NOT given up — they keep their subscription and keep trying to catch up.
    private val stalledRelays = ConcurrentHashMap<HexKey, MutableSet<NormalizedRelayUrl>>()

    // This manager is shared across logged-in accounts (one singleton coordinator), so the single
    // display flows below must follow whichever account is currently active. Per-account paging cursors
    // live in [pager], so switching away and back preserves progress; [exhaustedByUser] lets the display
    // flow repoint accurately on switch instead of leaking the previous account's state.
    @Volatile
    private var activeUser: HexKey? = null
    private val exhaustedByUser = ConcurrentHashMap<HexKey, Boolean>()

    private val windowLoad = WindowLoadTracker("giftwrap.history", tracksReqSends = true, onAbandoned = ::onRelaysStalled)

    // Exposed instead of windowLoad.loading directly: that flow starts `true` (it assumes a load is in
    // flight from construction). Wired straight through, its `true` would wedge the scroll-driven loader
    // — whose gate is `!loading` — so the first loadMore could never fire. This starts false and only
    // goes true once paging actually begins (mirrored from windowLoad by the done collector).
    private val _loadingMore = MutableStateFlow(false)
    val loadingMore: StateFlow<Boolean> = _loadingMore.asStateFlow()

    // True once the window settles (every relay done or stalled) — nothing more is reachable right now.
    private val _exhausted = MutableStateFlow(false)
    val exhausted: StateFlow<Boolean> = _exhausted.asStateFlow()

    // Status surfaced to the loading card: how many relays are still being paged, and the oldest point
    // paging has reached (epoch seconds, the deepest cursor).
    private val _relayCount = MutableStateFlow(0)
    val relayCount: StateFlow<Int> = _relayCount.asStateFlow()

    private val _reachedBack = MutableStateFlow<Long?>(null)
    val reachedBack: StateFlow<Long?> = _reachedBack.asStateFlow()

    // Per-relay paging progress for the active account — the data the in-stream markers render, same as
    // the per-conversation NIP-04 loader. Account-wide (gift wraps can't be filtered per room), so a
    // marker shows how far back THIS relay has paged the account's gift-wrap history.
    private val _relayProgress = MutableStateFlow<Map<NormalizedRelayUrl, RelayPagingProgress>>(emptyMap())
    val relayProgress: StateFlow<Map<NormalizedRelayUrl, RelayPagingProgress>> = _relayProgress.asStateFlow()

    // Account scope for the done collector. Volatile: written on IO (newSub), read on UI.
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

    // History starts just below the live tail's one-week floor and pages backward from there.
    private fun startUntil() = TimeUtils.now() - AccountGiftWrapsEoseManager.LIVE_TAIL_SECONDS

    private fun floor() = windowFloor.takeIf { it != 0L } ?: startUntil()

    private fun daysAgo(epochSeconds: Long) = (TimeUtils.now() - epochSeconds) / TimeUtils.ONE_DAY

    override fun updateFilter(
        key: AccountQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter> {
        val user = user(key)
        if (!key.account.isWriteable() || user.pubkeyHex !in started) return emptyList()

        // Every relay that still has older history to ask for, each at its own cursor. A relay whose
        // cursor advanced since the last assembly re-REQs its next page; one still mid-page keeps its open
        // REQ; a done relay drops out (its REQ closes). This is what lets relays run independently.
        val relays = key.account.dmRelays.flow.value
        val active = pager.activeRelays(user.pubkeyHex, relays).toSet()
        if (active.isEmpty()) return emptyList()
        DmRelayLog.log("giftwrap.history", key.account)
        Log.d(TAG) { "[giftwrap.history] REQ ${active.size} relay(s) ${active.map { it.url }}, limit=$PAGE_LIMIT (until ${daysAgo(pager.untilFor(user.pubkeyHex, active.first(), floor()))}d…)" }
        return active.flatMap { relay ->
            filterGiftWrapsToPubkey(
                relay = relay,
                pubkey = user.pubkeyHex,
                since = null,
                until = pager.untilFor(user.pubkeyHex, relay, floor()),
                limit = PAGE_LIMIT,
            )
        }
    }

    /** Starts (or resumes) per-relay paging of the gift-wrap history. Idempotent: safe to call again. */
    fun loadMore(user: User) {
        val account = accounts[user.pubkeyHex] ?: return
        started.add(user.pubkeyHex)
        val allRelays = account.dmRelays.flow.value
        if (allRelays.isEmpty()) return
        if (pager.activeRelays(user.pubkeyHex, allRelays).isEmpty()) {
            // Everything already paged to the bottom.
            exhaustedByUser[user.pubkeyHex] = true
            _exhausted.value = true
            return
        }
        _exhausted.value = false
        DmRelayLog.log("giftwrap.history", account)
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
            windowLoad.setExpectedRelays(allRelays.toSet())
        }
        updateStatus(user)
        Log.d(TAG) { "[giftwrap.history] paging ${allRelays.size} relay(s) independently: ${allRelays.map { it.url }}" }
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
        windowUser?.let { updateStatus(it) }
    }

    // Records [relay] as not currently advancing for [pk] and logs it once (the first time it stalls in
    // this window). The relay is kept — it kept its subscription and keeps trying to catch up.
    private fun markStalled(
        pk: HexKey,
        relay: NormalizedRelayUrl,
        reason: String,
    ) {
        val firstTime = stalledRelays.getOrPut(pk) { ConcurrentHashMap.newKeySet() }.add(relay)
        if (firstTime) Log.d(TAG) { "[giftwrap.history] ${relay.url} stalled — $reason (kept open, still trying)" }
    }

    private fun updateStatus(user: User) {
        val relays = accounts[user.pubkeyHex]?.dmRelays?.flow?.value ?: emptySet()
        // "Asking N relays" on the status card: the ones still being paged (done relays have dropped out).
        _relayCount.value = pager.activeRelays(user.pubkeyHex, relays).size
        // Over ALL relays, not just the still-active ones: a relay that finished keeps its deep cursor, so
        // "reached back to X" stays monotonic instead of jumping back to a newer date when the deepest
        // relay drops out of the active set.
        val start = floor()
        _reachedBack.value = pager.deepestUntil(user.pubkeyHex, relays, start)
        // Per-relay markers: where each relay's cursor sits and whether it's done / stalled.
        val stalled = stalledRelays[user.pubkeyHex] ?: emptySet()
        _relayProgress.value =
            relays.associateWith { relay ->
                RelayPagingProgress(
                    reachedUntil = pager.untilFor(user.pubkeyHex, relay, start),
                    done = pager.isDone(user.pubkeyHex, relay),
                    stalled = relay in stalled && !pager.isDone(user.pubkeyHex, relay),
                )
            }
    }

    // A one-line breakdown of where each relay landed when the window settles — the snapshot to reach for
    // when history didn't load tomorrow: who reached the bottom vs. who is still being retried.
    private fun logSettleSummary(user: User) {
        val relays = accounts[user.pubkeyHex]?.dmRelays?.flow?.value ?: return
        val done = relays.filter { pager.isDone(user.pubkeyHex, it) }.map { it.url }
        val stillTrying = relays.filterNot { pager.isDone(user.pubkeyHex, it) }.map { it.url }
        Log.d(TAG) { "[giftwrap.history] settled — done=$done still-trying=$stillTrying" }
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
            _relayProgress.value = emptyMap()
        }
        return requestNewSubscription(historyListener(user, key))
    }

    private fun historyListener(
        user: User,
        key: AccountQueryState,
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
                    Log.d(TAG) { "[giftwrap.history] ${relay.url} reached the bottom (done)" }
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
                // A relay (e.g. an author's) may demand auth we can't satisfy and CLOSE. It's stalled, not
                // done — keep its subscription so the pool can re-auth and it can catch up — but don't let
                // it hold the spinner.
                windowLoad.onRelaySettled(relay)
                markStalled(user.pubkeyHex, relay, "CLOSED: $message")
                updateStatus(user)
            }

            override fun onCannotConnect(
                relay: NormalizedRelayUrl,
                message: String,
                forFilters: List<Filter>?,
            ) {
                windowLoad.onRelaySettled(relay)
                markStalled(user.pubkeyHex, relay, "cannot connect: $message")
                updateStatus(user)
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
