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

/** How far back one relay has paged a conversation, for the per-relay progress markers. */
data class RelayPagingProgress(
    // The oldest createdAt this relay has loaded down to (its `until` cursor). The marker sits here and
    // slides down (older) as the relay pages further back.
    val reachedUntil: Long,
    // The relay answered an empty page: it has nothing older, it has reached the bottom of its window.
    val done: Boolean,
    // The relay isn't answering right now (auth-walled CLOSE / unreachable / slow). It is NOT abandoned
    // — its subscription stays open and it keeps trying to catch up — but it isn't currently advancing.
    val stalled: Boolean,
)

/**
 * Loads older NIP-04 DMs (kind 4) for one conversation by `until`+`limit` paging — **per relay,
 * independently**. There are no lock-step rounds: every relay drives its own pages off its own cursor,
 * continuing the instant it EOSEs (the subscription layer diffs per relay, so re-issuing only re-REQs
 * the relay whose cursor moved; the others' in-flight REQs are untouched). Fast relays race to the
 * bottom of the conversation in a few back-to-back pages while slow / auth-walled relays catch up at
 * their own pace in the background — none are abandoned, so they all converge on the same window.
 *
 * A relay is *done* once it answers an empty page (nothing older). A relay that won't answer (auth
 * CLOSE, unreachable, silent) is marked *stalled* for the markers but keeps its subscription open and
 * keeps trying. The [loadingMore] spinner reflects whether anything is still actively advancing; it
 * clears once every relay is either done or stalled, without waiting on the slow ones beyond that.
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

    // Relays currently not advancing for a conversation (auth CLOSE / unreachable / silent). Tracked for
    // the progress markers; these relays are NOT given up — they keep their subscription and keep trying.
    private val stalledRelays = ConcurrentHashMap<ConvoKey, MutableSet<NormalizedRelayUrl>>()

    private val windowLoad = WindowLoadTracker("convo.nip04.history", tracksReqSends = true, onAbandoned = ::onRelaysStalled)
    val loadingMore: StateFlow<Boolean> = windowLoad.loading

    private val _exhausted = MutableStateFlow(false)
    val exhausted: StateFlow<Boolean> = _exhausted.asStateFlow()

    private val _relayCount = MutableStateFlow(0)
    val relayCount: StateFlow<Int> = _relayCount.asStateFlow()

    private val _reachedBack = MutableStateFlow<Long?>(null)
    val reachedBack: StateFlow<Long?> = _reachedBack.asStateFlow()

    // Per-relay paging progress for the conversation on screen — the data the in-stream markers render.
    private val _relayProgress = MutableStateFlow<Map<NormalizedRelayUrl, RelayPagingProgress>>(emptyMap())
    val relayProgress: StateFlow<Map<NormalizedRelayUrl, RelayPagingProgress>> = _relayProgress.asStateFlow()

    // Shared across accounts/conversations (singleton coordinator): repoint the display flows to the
    // conversation now on screen instead of leaking the previous one's state. Cursors live in [pager].
    @Volatile
    private var activeConvo: ConvoKey? = null
    private val exhaustedByConvo = ConcurrentHashMap<ConvoKey, Boolean>()

    @Volatile
    private var scope: CoroutineScope? = null

    @Volatile
    private var doneJob: Job? = null

    private fun startUntil() = TimeUtils.now() - AccountGiftWrapsEoseManager.LIVE_TAIL_SECONDS

    override fun user(key: ChatroomQueryState) = key.account.userProfile()

    override fun list(key: ChatroomQueryState) = key.listId

    override fun updateFilter(
        key: ChatroomQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter>? {
        val pk = convoKey(key)
        val relays = nip04DMRelays(key.room.users, key.account)
        if (!key.account.isWriteable() || pk !in started || relays == null) return emptyList()

        // Every relay that still has older history to ask for, each at its own cursor. A relay whose
        // cursor advanced since the last assembly re-REQs its next page; one still mid-page keeps its
        // open REQ; a done relay drops out (its REQ closes). This is what lets relays run independently.
        val active = pager.activeRelays(pk, relays.all).toSet()
        if (active.isEmpty()) return emptyList()

        val scoped =
            Nip04DmRelays(
                toMeRelays = relays.toMeRelays.filterKeys { it in active },
                fromMeRelays = relays.fromMeRelays.filterKeys { it in active },
            )
        return filterNip04DMsHistory(key.account, scoped, PAGE_LIMIT) { relay ->
            pager.untilFor(pk, relay, startUntil())
        }
    }

    /** Starts (or resumes) per-relay paging for every open conversation. Idempotent: safe to call again. */
    fun loadMore() {
        val fullRelays = mutableSetOf<NormalizedRelayUrl>()
        var anyActive = false
        allKeys().forEach { key ->
            val relays = nip04DMRelays(key.room.users, key.account) ?: return@forEach
            started.add(convoKey(key))
            fullRelays.addAll(relays.all)
            if (pager.activeRelays(convoKey(key), relays.all).isNotEmpty()) anyActive = true
            DmRelayLog.log("convo.nip04.history", key.account)
        }
        if (fullRelays.isEmpty()) return
        if (!anyActive) {
            // Everything already paged to the bottom.
            activeConvo?.let { exhaustedByConvo[it] = true }
            _exhausted.value = true
            return
        }
        _exhausted.value = false
        scope?.let {
            ensureDoneCollector(it)
            // One window spanning the whole per-relay pagination: it settles a relay only on that relay's
            // empty-EOSE (done) or when it goes silent/stalled, never on a mid-history page, so the
            // spinner tracks "is anything still advancing" rather than any single round.
            if (!windowLoad.loading.value) windowLoad.startLoading(it)
            windowLoad.setExpectedRelays(fullRelays)
        }
        publishProgress()
        Log.d("DMPagination") { "[convo.nip04.history] paging ${fullRelays.size} relay(s) independently: ${fullRelays.map { it.url }}" }
        invalidateFilters()
    }

    // Flips [exhausted] when the window settles (every relay done or stalled) and back to false when a
    // fresh page starts. Tied to the spinner so "nothing is advancing" and "caught up" stay consistent.
    private fun ensureDoneCollector(scope: CoroutineScope) {
        if (doneJob?.isActive == true) return
        doneJob =
            scope.launch {
                var wasLoading = false
                windowLoad.loading.collect { loading ->
                    if (!loading && wasLoading) {
                        activeConvo?.let { exhaustedByConvo[it] = true }
                        _exhausted.value = true
                        publishProgress()
                        logSettleSummary()
                    }
                    wasLoading = loading
                }
            }
    }

    // WindowLoadTracker reports relays that accepted a REQ then went silent, or never got their REQ out.
    // We do NOT give up on them (they may simply be slow and need to catch up) — we just record them as
    // stalled for the markers and let them keep their open subscription.
    private fun onRelaysStalled(relays: Set<NormalizedRelayUrl>) {
        started.forEach { pk -> relays.forEach { markStalled(pk, it, "no response (silence/connect timeout)") } }
        publishProgress()
    }

    // Records [relay] as not currently advancing for [pk] and logs it once (the first time it stalls in
    // this window). The relay is kept — it kept its subscription and keeps trying to catch up.
    private fun markStalled(
        pk: ConvoKey,
        relay: NormalizedRelayUrl,
        reason: String,
    ) {
        val firstTime = stalledRelays.getOrPut(pk) { ConcurrentHashMap.newKeySet() }.add(relay)
        if (firstTime) Log.d("DMPagination") { "[convo.nip04.history] ${relay.url} stalled — $reason (kept open, still trying)" }
    }

    private fun relaysFor(pk: ConvoKey): Nip04DmRelays? = allKeys().firstOrNull { convoKey(it) == pk }?.let { nip04DMRelays(it.room.users, it.account) }

    private fun publishProgress() {
        val pk = activeConvo ?: return
        val relays = relaysFor(pk) ?: return
        val stalled = stalledRelays[pk] ?: emptySet()
        val start = startUntil()
        _relayProgress.value =
            relays.all.associateWith { relay ->
                RelayPagingProgress(
                    reachedUntil = pager.untilFor(pk, relay, start),
                    done = pager.isDone(pk, relay),
                    stalled = relay in stalled && !pager.isDone(pk, relay),
                )
            }
        // "Asking N relays" on the status card: the ones still being paged (done relays have dropped out).
        _relayCount.value = pager.activeRelays(pk, relays.all).size
        _reachedBack.value = pager.deepestUntil(pk, relays.all, start)
    }

    // A one-line breakdown of where each relay landed when the window settles — the snapshot to reach for
    // when a conversation didn't load tomorrow: who reached the bottom vs. who is still being retried.
    private fun logSettleSummary() {
        val pk = activeConvo ?: return
        val relays = relaysFor(pk) ?: return
        val done = relays.all.filter { pager.isDone(pk, it) }.map { it.url }
        val stillTrying = relays.all.filterNot { pager.isDone(pk, it) }.map { it.url }
        Log.d("DMPagination") { "[convo.nip04.history] settled — done=$done still-trying=$stillTrying" }
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
            _relayProgress.value = emptyMap()
        }
        return requestNewSubscription(historyListener(key))
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
                stalledRelays[pk]?.remove(relay)
            }

            override fun onEose(
                relay: NormalizedRelayUrl,
                forFilters: List<Filter>?,
            ) {
                stalledRelays[pk]?.remove(relay)
                pager.onEose(pk, relay)
                if (pager.isDone(pk, relay)) {
                    // Reached the bottom on this relay: settle it for the spinner, nothing more to ask.
                    windowLoad.onRelaySettled(relay)
                    Log.d("DMPagination") { "[convo.nip04.history] ${relay.url} reached the bottom (done)" }
                } else {
                    // This page had events: reset only this relay's tally and let it continue to its
                    // next page immediately, independent of every other relay.
                    pager.beginRound(pk, listOf(relay))
                }
                newEose(key, relay, TimeUtils.now(), forFilters)
                publishProgress()
                invalidateFilters()
            }

            override fun onClosed(
                message: String,
                relay: NormalizedRelayUrl,
                forFilters: List<Filter>?,
            ) {
                // A relay (e.g. the correspondent's) may demand auth we can't satisfy and CLOSE. It's
                // stalled, not done — keep its subscription so the pool can re-auth and it can catch up —
                // but don't let it hold the spinner.
                windowLoad.onRelaySettled(relay)
                markStalled(pk, relay, "CLOSED: $message")
                publishProgress()
            }

            override fun onCannotConnect(
                relay: NormalizedRelayUrl,
                message: String,
                forFilters: List<Filter>?,
            ) {
                windowLoad.onRelaySettled(relay)
                markStalled(pk, relay, "cannot connect: $message")
                publishProgress()
            }
        }
    }

    companion object {
        private const val PAGE_LIMIT = 10000
    }
}
