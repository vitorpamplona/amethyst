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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM.datasource

import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.DmRelayLog
import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.PerUserAndFollowListEoseManager
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
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey
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
 * Loads older NIP-04 DMs (kind 4) for one conversation by `until`+`limit` paging, per relay, scoped to
 * the two participants. Same gap-proof model as the gift-wrap history (a relay is done on an empty
 * page + EOSE; [exhausted] once a round advances no relay), keyed per conversation. Idle until
 * [loadMore].
 */
class ChatroomNip04HistorySubAssembler(
    client: INostrClient,
    allKeys: () -> Set<ChatroomQueryState>,
) : PerUserAndFollowListEoseManager<ChatroomQueryState, String>(client, allKeys) {
    // Keyed by (account, conversation) so each thread paginates independently — and so the same
    // correspondent opened from two logged-in accounts doesn't share a cursor. ChatroomKey is a data
    // class over the participant set, so it's a collision-free key (unlike its 32-bit hashCode/listId).
    private data class ConvoKey(
        val account: HexKey,
        val room: ChatroomKey,
    )

    private fun convoKey(key: ChatroomQueryState) = ConvoKey(user(key).pubkeyHex, key.room)

    private val pager = UntilLimitPager<ConvoKey>()
    private val started = ConcurrentHashMap.newKeySet<ConvoKey>()
    private val askedRelays = ConcurrentHashMap<ConvoKey, Set<NormalizedRelayUrl>>()

    private val windowLoad = WindowLoadTracker("convo.nip04.history", tracksReqSends = true, onAbandoned = ::onRelaysAbandoned)
    val loadingMore: StateFlow<Boolean> = windowLoad.loading

    private val _exhausted = MutableStateFlow(false)
    val exhausted: StateFlow<Boolean> = _exhausted.asStateFlow()

    private val _relayCount = MutableStateFlow(0)
    val relayCount: StateFlow<Int> = _relayCount.asStateFlow()

    private val _reachedBack = MutableStateFlow<Long?>(null)
    val reachedBack: StateFlow<Long?> = _reachedBack.asStateFlow()

    // Shared across accounts/conversations (singleton coordinator): repoint the display flows to the
    // conversation now on screen instead of leaking the previous one's state. Cursors live in [pager].
    @Volatile
    private var activeConvo: ConvoKey? = null
    private val exhaustedByConvo = ConcurrentHashMap<ConvoKey, Boolean>()

    // No-progress guard (see the gift-wrap history manager's twin): skip re-issuing an identical round
    // that brought nothing; cleared by onEose.
    @Volatile
    private var lastAskedActive: Set<NormalizedRelayUrl> = emptySet()

    @Volatile
    private var lastRoundEventCount = -1

    @Volatile
    private var scope: CoroutineScope? = null

    @Volatile
    private var roundJob: Job? = null

    @Volatile
    private var autoLoadAll = false

    private fun startUntil() = TimeUtils.now() - AccountGiftWrapsEoseManager.LIVE_TAIL_SECONDS

    override fun user(key: ChatroomQueryState) = key.account.userProfile()

    override fun list(key: ChatroomQueryState) = key.listId

    override fun updateFilter(
        key: ChatroomQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter>? {
        val pk = convoKey(key)
        val relays = nip04DMRelays(key.room.users, key.account)
        if (!key.account.isWriteable() || pk !in started || relays == null) {
            windowLoad.setExpectedRelays(emptySet())
            return emptyList()
        }
        val active = pager.activeRelays(pk, relays.all).toSet()
        askedRelays[pk] = active
        windowLoad.setExpectedRelays(active)
        if (active.isEmpty()) return emptyList()
        DmRelayLog.log("convo.nip04.history", key.account)
        Log.d("DMPagination") { "[convo.nip04.history] REQ ${active.size} relay(s), limit=$PAGE_LIMIT fromMe(outbox)=${relays.fromMeRelays.keys.intersect(active).map { it.url }} toMe(inbox)=${relays.toMeRelays.keys.intersect(active).map { it.url }}" }
        val activeRelays =
            Nip04DmRelays(
                toMeRelays = relays.toMeRelays.filterKeys { it in active },
                fromMeRelays = relays.fromMeRelays.filterKeys { it in active },
            )
        return filterNip04DMsHistory(key.account, activeRelays, PAGE_LIMIT) { relay ->
            pager.untilFor(pk, relay, startUntil())
        }
    }

    /** Requests the next backward page for every open conversation that still has older history. */
    fun loadMore() {
        if (_exhausted.value) return
        // Gather the active (not-finished) relays per open conversation first, so the no-progress guard
        // is checked before any beginRound (which would otherwise reset round tallies prematurely).
        val perKeyActive = mutableListOf<Pair<ConvoKey, List<NormalizedRelayUrl>>>()
        val activeUnion = mutableSetOf<NormalizedRelayUrl>()
        allKeys().forEach { key ->
            val relays = nip04DMRelays(key.room.users, key.account) ?: return@forEach
            val pk = convoKey(key)
            started.add(pk)
            val active = pager.activeRelays(pk, relays.all)
            if (active.isNotEmpty()) {
                perKeyActive.add(pk to active)
                activeUnion.addAll(active)
            }
        }
        if (perKeyActive.isEmpty()) {
            activeConvo?.let { exhaustedByConvo[it] = true }
            _exhausted.value = true
            return
        }
        if (activeUnion == lastAskedActive && lastRoundEventCount == 0) {
            // The same relays just returned nothing two rounds running — they're unreadable: a
            // correspondent's auth-walled relay that only ever CLOSEs (ditto: "all authors must be
            // authenticated"), or one perpetually stuck reconnecting. Give up on them so the
            // conversation reports as finished instead of lingering forever on a "N relays" count
            // with no progress bar. (My own reachable relays empty-EOSE to `done` and never land here.)
            Log.d("DMPagination") { "[convo.nip04.history] giving up — no progress on the same relays ${activeUnion.map { it.url }}" }
            perKeyActive.forEach { (pk, active) -> active.forEach { pager.giveUp(pk, it) } }
            markExhaustedIfAllDone()
            return
        }
        lastAskedActive = activeUnion
        var totalRelays = 0
        var deepest: Long? = null
        perKeyActive.forEach { (pk, active) ->
            pager.beginRound(pk, active)
            totalRelays += active.size
            pager.deepestUntil(pk, active, startUntil())?.let { d ->
                deepest = deepest?.let { minOf(it, d) } ?: d
            }
        }
        _relayCount.value = totalRelays
        _reachedBack.value = deepest
        Log.d("DMPagination") { "[convo.nip04.history] loadMore" }
        scope?.let {
            ensureRoundCollector(it)
            windowLoad.startLoading(it)
        }
        invalidateFilters()
    }

    /** Pages to the end: each completed round auto-issues the next until exhausted. */
    fun loadEverything() {
        if (_exhausted.value) return
        autoLoadAll = true
        loadMore()
    }

    private fun ensureRoundCollector(scope: CoroutineScope) {
        if (roundJob?.isActive == true) return
        roundJob =
            scope.launch {
                var wasLoading = false
                windowLoad.loading.collect { loading ->
                    if (!loading && wasLoading) {
                        val count = started.sumOf { pk -> pager.roundEventCount(pk, askedRelays[pk] ?: emptySet()) }
                        lastRoundEventCount = count
                        // Exhausted ONLY when every open conversation's relays have all returned an
                        // empty page + EOSE; a CLOSED / unanswered relay isn't finished, so keep loading.
                        val keys = allKeys()
                        val exhaustedNow =
                            keys.isNotEmpty() &&
                                keys.none { key ->
                                    val relays = nip04DMRelays(key.room.users, key.account)
                                    relays != null && pager.activeRelays(convoKey(key), relays.all).isNotEmpty()
                                }
                        activeConvo?.let { exhaustedByConvo[it] = exhaustedNow }
                        _exhausted.value = exhaustedNow
                        _reachedBack.value = started.mapNotNull { pk -> pager.deepestUntil(pk, askedRelays[pk] ?: emptySet(), startUntil()) }.minOrNull()
                        Log.d("DMPagination") { "[convo.nip04.history] round done: $count event(s), exhausted=$exhaustedNow" }
                        if (autoLoadAll && !exhaustedNow) loadMore()
                    }
                    wasLoading = loading
                }
            }
    }

    override fun newSub(key: ChatroomQueryState): Subscription {
        scope = key.account.scope
        val pk = convoKey(key)
        if (activeConvo != pk) {
            activeConvo = pk
            // A different conversation (or account) is on screen: repoint the display flows to it.
            _exhausted.value = exhaustedByConvo[pk] ?: false
            _relayCount.value = 0
            _reachedBack.value = null
            lastAskedActive = emptySet()
            lastRoundEventCount = -1
        }
        return requestNewSubscription(historyListener(key))
    }

    // A relay accepted the REQ but never answered (auth-walled / dead): drop it from every open
    // conversation's pager so it stops blocking the relay count and exhaustion on the next round. May
    // complete exhaustion right away if it was the last relay still holding a thread open.
    private fun onRelaysAbandoned(relays: Set<NormalizedRelayUrl>) {
        var gaveUp = false
        started.forEach { pk ->
            val asked = askedRelays[pk] ?: return@forEach
            relays.forEach { if (it in asked && pager.giveUp(pk, it)) gaveUp = true }
        }
        if (gaveUp) markExhaustedIfAllDone()
    }

    // Flips to exhausted only once every open conversation's relays have all returned an empty page +
    // EOSE. Sets true only — false transitions belong to loadMore / the round collector.
    private fun markExhaustedIfAllDone() {
        val keys = allKeys()
        val allDone =
            keys.isNotEmpty() &&
                keys.none { key ->
                    val relays = nip04DMRelays(key.room.users, key.account)
                    relays != null && pager.activeRelays(convoKey(key), relays.all).isNotEmpty()
                }
        if (allDone) {
            activeConvo?.let { exhaustedByConvo[it] = true }
            _exhausted.value = true
        }
    }

    private fun historyListener(key: ChatroomQueryState): SubscriptionListener {
        val pk = convoKey(key)
        return object : SubscriptionListener {
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
                pager.onEvent(pk, relay, event.createdAt)
            }

            override fun onEose(
                relay: NormalizedRelayUrl,
                forFilters: List<Filter>?,
            ) {
                pager.onEose(pk, relay)
                windowLoad.onRelaySettled(relay)
                newEose(key, relay, TimeUtils.now(), forFilters)
                lastRoundEventCount = -1
                markExhaustedIfAllDone()
            }

            override fun onClosed(
                message: String,
                relay: NormalizedRelayUrl,
                forFilters: List<Filter>?,
            ) {
                windowLoad.onRelaySettled(relay)
                // A relay (e.g. the correspondent's) may demand auth we can't satisfy and CLOSE every
                // round; once the pager gives up on it, it stops blocking this thread's exhaustion.
                if (pager.onClosed(pk, relay)) markExhaustedIfAllDone()
            }

            override fun onCannotConnect(
                relay: NormalizedRelayUrl,
                message: String,
                forFilters: List<Filter>?,
            ) {
                windowLoad.onRelaySettled(relay)
            }
        }
    }

    companion object {
        private const val PAGE_LIMIT = 10000
    }
}
