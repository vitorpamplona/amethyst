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
import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.PerRelayLoadTracker
import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.PerUserEoseManager
import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.RelayPagingProgress
import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.UntilLimitPager
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Loads older NIP-04 DMs (kind 4) for the rooms list by **`until`+`limit` paging, per relay, on
 * demand** — the same model as the gift-wrap history loader
 * ([com.vitorpamplona.amethyst.service.relayClient.reqCommand.account.nip59GiftWraps.AccountGiftWrapsHistoryEoseManager]),
 * across the account's home (outbox, *from me*) + DM (inbox, *to me*) relays. Each relay advances one
 * page when its on-screen window-limit marker asks ([advance]); otherwise it parks. Nothing is walked
 * proactively.
 */
class ChatroomListNip04HistorySubAssembler(
    client: INostrClient,
    allKeys: () -> Set<ChatroomListState>,
) : PerUserEoseManager<ChatroomListState>(client, allKeys) {
    private val pager = UntilLimitPager<HexKey>()
    private val accounts = ConcurrentHashMap<HexKey, Account>()
    private val stalledRelays = ConcurrentHashMap<HexKey, MutableSet<NormalizedRelayUrl>>()

    @Volatile
    private var activeUser: HexKey? = null
    private val exhaustedByUser = ConcurrentHashMap<HexKey, Boolean>()

    private val loadTracker = PerRelayLoadTracker("rooms.nip04.history", onSilenced = ::onRelaysSilenced)
    val loadingMore: StateFlow<Boolean> = loadTracker.loading

    private val _exhausted = MutableStateFlow(false)
    val exhausted: StateFlow<Boolean> = _exhausted.asStateFlow()

    private val _relayCount = MutableStateFlow(0)
    val relayCount: StateFlow<Int> = _relayCount.asStateFlow()

    // Not-done relays that can't be reached right now — shown as "waiting on N relays" on the paused card.
    private val _stalledCount = MutableStateFlow(0)
    val stalledCount: StateFlow<Int> = _stalledCount.asStateFlow()

    private val _reachedBack = MutableStateFlow<Long?>(null)
    val reachedBack: StateFlow<Long?> = _reachedBack.asStateFlow()

    private val _relayProgress = MutableStateFlow<Map<NormalizedRelayUrl, RelayPagingProgress>>(emptyMap())
    val relayProgress: StateFlow<Map<NormalizedRelayUrl, RelayPagingProgress>> = _relayProgress.asStateFlow()

    // Pinned per account for the session — must not drift forward, or an un-delivered relay's marker
    // would keep moving and re-trigger its sentinel. See AccountGiftWrapsHistoryEoseManager.
    private val pinnedFloor = ConcurrentHashMap<HexKey, Long>()

    private fun startUntil(pk: HexKey) = pinnedFloor.getOrPut(pk) { TimeUtils.now() - AccountGiftWrapsEoseManager.LIVE_TAIL_SECONDS }

    override fun user(key: ChatroomListState) = key.account.userProfile()

    private fun allRelays(account: Account) = (account.homeRelays.flow.value + account.dmRelays.flow.value).toSet()

    override fun updateFilter(
        key: ChatroomListState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter>? {
        val user = user(key)
        if (!key.account.isWriteable()) return emptyList()
        val homeRelays = key.account.homeRelays.flow.value
        val dmRelays = key.account.dmRelays.flow.value
        val armed = pager.armedRelays(user.pubkeyHex, (homeRelays + dmRelays).toSet())
        if (armed.isEmpty()) return emptyList()
        DmRelayLog.log("rooms.nip04.history", key.account)
        return armed.flatMap { relay ->
            val until = pager.requestedUntilFor(user.pubkeyHex, relay) ?: return@flatMap emptyList()
            buildList {
                if (relay in homeRelays) add(filterNip04DMsFromMe(user, relay, since = null, until = until, limit = PAGE_LIMIT))
                if (relay in dmRelays) add(filterNip04DMsToMe(user, relay, since = null, until = until, limit = PAGE_LIMIT))
            }
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
        allRelays(account).forEach { if (arm(user, it)) any = true }
        if (any) {
            Log.d("DMPagination") { "[rooms.nip04.history] advanceAll (empty-feed bootstrap)" }
            _exhausted.value = false
            updateStatus(user)
            invalidateFilters()
        }
    }

    private fun arm(
        user: User,
        relay: NormalizedRelayUrl,
    ): Boolean {
        val account = accounts[user.pubkeyHex] ?: return false
        if (relay !in allRelays(account)) return false
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
        if (firstTime) Log.d("DMPagination") { "[rooms.nip04.history] ${relay.url} stalled — $reason (kept, advance to retry)" }
    }

    private fun updateStatus(user: User) {
        // The display flows are singletons shown for the foreground account; a background account's late
        // EOSE must not overwrite them (its cursors still advance in the pager).
        if (activeUser != user.pubkeyHex) return
        val account = accounts[user.pubkeyHex]
        val relays = account?.let { allRelays(it) } ?: emptySet()
        _relayCount.value = loadTracker.count()
        val start = startUntil(user.pubkeyHex)
        _reachedBack.value = pager.deepestReached(user.pubkeyHex, relays, start)
        val stalled = stalledRelays[user.pubkeyHex] ?: emptySet()
        _stalledCount.value = relays.count { it in stalled && !pager.isDone(user.pubkeyHex, it) }
        _relayProgress.value =
            relays.associateWith { relay ->
                RelayPagingProgress(
                    reachedUntil = pager.reachedUntilFor(user.pubkeyHex, relay, start),
                    done = pager.isDone(user.pubkeyHex, relay),
                    stalled = relay in stalled && !pager.isDone(user.pubkeyHex, relay),
                )
            }
    }

    private fun recomputeExhausted(user: User) {
        val account = accounts[user.pubkeyHex] ?: return
        val relays = allRelays(account)
        if (relays.isEmpty()) return
        val stalled = stalledRelays[user.pubkeyHex] ?: emptySet()
        val pending = relays.any { !pager.isDone(user.pubkeyHex, it) && it !in stalled }
        val ex = !pending
        val was = exhaustedByUser[user.pubkeyHex] ?: false
        exhaustedByUser[user.pubkeyHex] = ex
        if (ex && !was) {
            val done = relays.filter { pager.isDone(user.pubkeyHex, it) }.map { it.url }
            val stuck = relays.filter { it in stalled && !pager.isDone(user.pubkeyHex, it) }.map { it.url }
            Log.d("DMPagination") { "[rooms.nip04.history] window settled (nothing more reachable) — done=$done stalled=$stuck" }
        }
        if (activeUser == user.pubkeyHex) _exhausted.value = ex
    }

    override fun newSub(key: ChatroomListState): Subscription {
        val user = user(key)
        accounts[user.pubkeyHex] = key.account
        loadTracker.bind(key.account.scope)
        if (activeUser != user.pubkeyHex) {
            activeUser = user.pubkeyHex
            loadTracker.reset()
            _exhausted.value = exhaustedByUser[user.pubkeyHex] ?: false
            _relayCount.value = 0
            _stalledCount.value = 0
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
        key: ChatroomListState,
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
                    Log.d("DMPagination") { "[rooms.nip04.history] ${relay.url} reached the bottom (done)" }
                }
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
        private const val PAGE_LIMIT = 10000
    }
}
