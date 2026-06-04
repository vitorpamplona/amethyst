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
import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.PerRelayLoadTracker
import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.PerUserEoseManager
import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.RelayPagingProgress
import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.UntilLimitPager
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Loads the account's NIP-17 gift-wrap **history** — everything older than the one-week live tail
 * ([AccountGiftWrapsEoseManager]) — by **`until`+`limit` paging, per relay, on demand**.
 *
 * There is no proactive walk: each relay advances exactly one page when the UI calls [advance] for it,
 * and then **parks** at its window limit. The on-screen window-limit markers are the drivers — a relay
 * pages only while its marker is visible, and keeps paging (page after page) as long as it stays visible
 * (see the rooms-list / conversation feed views). So a spam-dense relay never floods: the user has to
 * scroll through its messages to pull more, and nothing is fetched while its marker is off screen.
 *
 * A relay is *done* once it answers an empty page; one that won't answer (auth CLOSE, unreachable, or
 * silent past the load tracker's window) is flagged *stalled* but kept. [exhausted] flips once every
 * relay is either done or stalled — nothing more is reachable right now.
 */
class AccountGiftWrapsHistoryEoseManager(
    client: INostrClient,
    allKeys: () -> Set<AccountQueryState>,
) : PerUserEoseManager<AccountQueryState>(client, allKeys) {
    override fun user(key: AccountQueryState) = key.account.userProfile()

    // Per-relay demand-driven cursors, keyed by account pubkey so switching accounts preserves progress.
    private val pager = UntilLimitPager<HexKey>()

    // The account behind each user pubkey, captured on subscribe so the UI-thread API can read the DM
    // relay list without the key.
    private val accounts = ConcurrentHashMap<HexKey, Account>()

    // Relays not currently advancing for a user (auth CLOSE / unreachable / silent). Kept (not given up)
    // and surfaced as stalled in the markers; they resume if the user re-advances them.
    private val stalledRelays = ConcurrentHashMap<HexKey, MutableSet<NormalizedRelayUrl>>()

    // Shared across accounts (singleton coordinator): repoint the display flows to the active account on
    // switch instead of leaking the previous one's state. Cursors live in [pager].
    @Volatile
    private var activeUser: HexKey? = null
    private val exhaustedByUser = ConcurrentHashMap<HexKey, Boolean>()

    private val loadTracker = PerRelayLoadTracker("giftwrap.history", onSilenced = ::onRelaysSilenced)
    val loadingMore: StateFlow<Boolean> = loadTracker.loading

    private val _exhausted = MutableStateFlow(false)
    val exhausted: StateFlow<Boolean> = _exhausted.asStateFlow()

    // Relays currently fetching a page (for the "asking N relays" status line).
    private val _relayCount = MutableStateFlow(0)
    val relayCount: StateFlow<Int> = _relayCount.asStateFlow()

    private val _reachedBack = MutableStateFlow<Long?>(null)
    val reachedBack: StateFlow<Long?> = _reachedBack.asStateFlow()

    // Per-relay window limits — where each relay has paged to, done/stalled — the data the on-screen
    // markers render and drive their advance from.
    private val _relayProgress = MutableStateFlow<Map<NormalizedRelayUrl, RelayPagingProgress>>(emptyMap())
    val relayProgress: StateFlow<Map<NormalizedRelayUrl, RelayPagingProgress>> = _relayProgress.asStateFlow()

    // History starts just below the live tail's one-week floor and pages backward from there. Pinned per
    // account for the session: it must NOT drift forward on every recompute, or an un-delivered relay's
    // marker (which sits at this floor) would keep changing and re-trigger its on-screen sentinel. The
    // live tail covers everything newer than the floor.
    private val pinnedFloor = ConcurrentHashMap<HexKey, Long>()

    private fun startUntil(pk: HexKey) = pinnedFloor.getOrPut(pk) { TimeUtils.now() - AccountGiftWrapsEoseManager.LIVE_TAIL_SECONDS }

    private fun daysAgo(epochSeconds: Long) = (TimeUtils.now() - epochSeconds) / TimeUtils.ONE_DAY

    override fun updateFilter(
        key: AccountQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter> {
        val user = user(key)
        if (!key.account.isWriteable()) return emptyList()
        // Only relays that have been advanced (armed) and aren't done carry a REQ. A relay that finished a
        // page keeps the same `until` here, so re-assembly (triggered when ANOTHER relay advances) doesn't
        // re-REQ it — it stays parked until the UI advances it again.
        val relays = key.account.dmRelays.flow.value
        val armed = pager.armedRelays(user.pubkeyHex, relays)
        if (armed.isEmpty()) return emptyList()
        DmRelayLog.log("giftwrap.history", key.account)
        return armed.flatMap { relay ->
            val until = pager.requestedUntilFor(user.pubkeyHex, relay) ?: return@flatMap emptyList()
            Log.d(TAG) { "[giftwrap.history] REQ ${relay.url} until ${daysAgo(until)}d, limit=$PAGE_LIMIT" }
            filterGiftWrapsToPubkey(relay = relay, pubkey = user.pubkeyHex, since = null, until = until, limit = PAGE_LIMIT)
        }
    }

    /** Steps a single [relay] to its next, older page. Driven by that relay's on-screen window-limit marker. */
    fun advance(
        user: User,
        relay: NormalizedRelayUrl,
    ) {
        if (arm(user, relay)) {
            _exhausted.value = false
            updateStatus(user)
            invalidateFilters()
        }
    }

    /** Steps every not-done, not-in-flight relay one page. For the empty/initial boundary (nothing to scroll). */
    fun advanceAll(user: User) {
        val account = accounts[user.pubkeyHex] ?: return
        var any = false
        account.dmRelays.flow.value
            .forEach { if (arm(user, it)) any = true }
        if (any) {
            Log.d(TAG) { "[giftwrap.history] advanceAll (empty-feed bootstrap)" }
            _exhausted.value = false
            updateStatus(user)
            invalidateFilters()
        }
    }

    // Moves one relay's cursor to its next page and marks it in-flight. Returns false if it can't advance
    // (unknown relay, already fetching, or already done). Does NOT invalidate — the caller batches that.
    private fun arm(
        user: User,
        relay: NormalizedRelayUrl,
    ): Boolean {
        val account = accounts[user.pubkeyHex] ?: return false
        if (relay !in account.dmRelays.flow.value) return false
        if (loadTracker.isInFlight(relay)) return false
        if (!pager.advance(user.pubkeyHex, relay, startUntil(user.pubkeyHex))) return false
        stalledRelays[user.pubkeyHex]?.remove(relay)
        loadTracker.bind(account.scope)
        loadTracker.onAdvance(relay)
        return true
    }

    private fun onRelaysSilenced(relays: Set<NormalizedRelayUrl>) {
        val pk = activeUser ?: return
        relays.forEach { markStalled(pk, it, "no response (silence timeout)") }
        accounts[pk]?.userProfile()?.let {
            updateStatus(it)
            recomputeExhausted(it)
        }
    }

    private fun markStalled(
        pk: HexKey,
        relay: NormalizedRelayUrl,
        reason: String,
    ) {
        val firstTime = stalledRelays.getOrPut(pk) { ConcurrentHashMap.newKeySet() }.add(relay)
        if (firstTime) Log.d(TAG) { "[giftwrap.history] ${relay.url} stalled — $reason (kept, advance to retry)" }
    }

    private fun updateStatus(user: User) {
        // The display flows are singletons shown for the foreground account; a background account's late
        // EOSE must not overwrite them (its cursors still advance in the pager).
        if (activeUser != user.pubkeyHex) return
        val relays = accounts[user.pubkeyHex]?.dmRelays?.flow?.value ?: emptySet()
        _relayCount.value = loadTracker.count()
        val start = startUntil(user.pubkeyHex)
        _reachedBack.value = pager.deepestReached(user.pubkeyHex, relays, start)
        val stalled = stalledRelays[user.pubkeyHex] ?: emptySet()
        _relayProgress.value =
            relays.associateWith { relay ->
                RelayPagingProgress(
                    reachedUntil = pager.reachedUntilFor(user.pubkeyHex, relay, start),
                    done = pager.isDone(user.pubkeyHex, relay),
                    stalled = relay in stalled && !pager.isDone(user.pubkeyHex, relay),
                )
            }
    }

    // Exhausted once every relay is either done (empty page) or stalled (unreachable) — nothing more is
    // reachable right now. A merely parked relay (more to load, just not advancing) keeps this false.
    private fun recomputeExhausted(user: User) {
        val relays = accounts[user.pubkeyHex]?.dmRelays?.flow?.value ?: return
        if (relays.isEmpty()) return
        val stalled = stalledRelays[user.pubkeyHex] ?: emptySet()
        val pending = relays.any { !pager.isDone(user.pubkeyHex, it) && it !in stalled }
        val ex = !pending
        exhaustedByUser[user.pubkeyHex] = ex
        if (activeUser == user.pubkeyHex) _exhausted.value = ex
    }

    override fun newSub(key: AccountQueryState): Subscription {
        val user = user(key)
        accounts[user.pubkeyHex] = key.account
        loadTracker.bind(key.account.scope)
        if (activeUser != user.pubkeyHex) {
            activeUser = user.pubkeyHex
            // Account switched: repoint the shared display flows to this account's own state.
            loadTracker.reset()
            _exhausted.value = exhaustedByUser[user.pubkeyHex] ?: false
            _relayCount.value = 0
            _reachedBack.value = null
            _relayProgress.value = emptyMap()
        }
        // Populate the per-relay markers (all relays at the floor, not done) so the UI can render their
        // window-limit sentinels and pull the first page when they come into view.
        updateStatus(user)
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
                loadTracker.onActivity()
                pager.onEvent(user.pubkeyHex, relay, event.createdAt)
                stalledRelays[user.pubkeyHex]?.remove(relay)
            }

            override fun onEose(
                relay: NormalizedRelayUrl,
                forFilters: List<Filter>?,
            ) {
                stalledRelays[user.pubkeyHex]?.remove(relay)
                pager.onEose(user.pubkeyHex, relay)
                loadTracker.onSettled(relay)
                if (pager.isDone(user.pubkeyHex, relay)) {
                    Log.d(TAG) { "[giftwrap.history] ${relay.url} reached the bottom (done)" }
                }
                // No auto-advance: the relay parks here until its marker asks for the next page.
                newEose(key, relay, TimeUtils.now(), forFilters)
                updateStatus(user)
                recomputeExhausted(user)
            }

            override fun onClosed(
                message: String,
                relay: NormalizedRelayUrl,
                forFilters: List<Filter>?,
            ) {
                loadTracker.onSettled(relay)
                markStalled(user.pubkeyHex, relay, "CLOSED: $message")
                updateStatus(user)
                recomputeExhausted(user)
            }

            override fun onCannotConnect(
                relay: NormalizedRelayUrl,
                message: String,
                forFilters: List<Filter>?,
            ) {
                loadTracker.onSettled(relay)
                markStalled(user.pubkeyHex, relay, "cannot connect: $message")
                updateStatus(user)
                recomputeExhausted(user)
            }
        }

    companion object {
        private const val TAG = "DMPagination"

        // Asked of every relay per page. Large on purpose: we want a whole band in one page where the
        // relay allows it. A relay returning fewer is treated as its own cap, NOT as "nothing more" —
        // only an empty page + EOSE ends a relay.
        private const val PAGE_LIMIT = 10000
    }
}
