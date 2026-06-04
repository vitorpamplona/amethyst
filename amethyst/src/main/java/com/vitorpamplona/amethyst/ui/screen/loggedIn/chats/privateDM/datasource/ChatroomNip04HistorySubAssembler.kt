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
import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.PerRelayLoadTracker
import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.PerUserAndFollowListEoseManager
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
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Loads older NIP-04 DMs (kind 4) for one conversation by **`until`+`limit` paging, per relay, on
 * demand**. Each relay advances exactly one page when the conversation's on-screen window-limit marker
 * for that relay asks ([advance]); otherwise it parks. Nothing is walked proactively — a relay pages
 * only while its marker is visible and keeps paging while it stays visible.
 *
 * A relay is *done* once it answers an empty page; one that won't answer (auth CLOSE, unreachable, or
 * silent past the load tracker's window) is flagged *stalled* but kept. [exhausted] flips once every
 * relay is either done or stalled.
 */
class ChatroomNip04HistorySubAssembler(
    client: INostrClient,
    allKeys: () -> Set<ChatroomQueryState>,
) : PerUserAndFollowListEoseManager<ChatroomQueryState, String>(client, allKeys) {
    // Keyed by (account, conversation) so each thread paginates independently — and so the same
    // correspondent opened from two logged-in accounts doesn't share a cursor.
    private data class ConvoKey(
        val account: HexKey,
        val room: ChatroomKey,
    )

    private fun convoKey(key: ChatroomQueryState) = ConvoKey(user(key).pubkeyHex, key.room)

    private val pager = UntilLimitPager<ConvoKey>()

    private val stalledRelays = ConcurrentHashMap<ConvoKey, MutableSet<NormalizedRelayUrl>>()

    private val loadTracker = PerRelayLoadTracker("convo.nip04.history", onSilenced = ::onRelaysSilenced)
    val loadingMore: StateFlow<Boolean> = loadTracker.loading

    private val _exhausted = MutableStateFlow(false)
    val exhausted: StateFlow<Boolean> = _exhausted.asStateFlow()

    private val _relayCount = MutableStateFlow(0)
    val relayCount: StateFlow<Int> = _relayCount.asStateFlow()

    private val _reachedBack = MutableStateFlow<Long?>(null)
    val reachedBack: StateFlow<Long?> = _reachedBack.asStateFlow()

    private val _relayProgress = MutableStateFlow<Map<NormalizedRelayUrl, RelayPagingProgress>>(emptyMap())
    val relayProgress: StateFlow<Map<NormalizedRelayUrl, RelayPagingProgress>> = _relayProgress.asStateFlow()

    // Shared across accounts/conversations (singleton coordinator): repoint the display flows to the
    // conversation now on screen. Cursors live in [pager].
    @Volatile
    private var activeConvo: ConvoKey? = null
    private val exhaustedByConvo = ConcurrentHashMap<ConvoKey, Boolean>()

    private fun startUntil() = TimeUtils.now() - AccountGiftWrapsEoseManager.LIVE_TAIL_SECONDS

    override fun user(key: ChatroomQueryState) = key.account.userProfile()

    override fun list(key: ChatroomQueryState) = key.listId

    private fun relaysFor(pk: ConvoKey): Nip04DmRelays? = allKeys().firstOrNull { convoKey(it) == pk }?.let { nip04DMRelays(it.room.users, it.account) }

    override fun updateFilter(
        key: ChatroomQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter>? {
        val pk = convoKey(key)
        val relays = nip04DMRelays(key.room.users, key.account)
        if (!key.account.isWriteable() || relays == null) return emptyList()

        // Only armed (advanced, not done) relays carry a REQ, each at its own requested cursor. A parked
        // relay keeps the same filter here, so re-assembly (another relay advancing) doesn't re-REQ it.
        val armed = pager.armedRelays(pk, relays.all).toSet()
        if (armed.isEmpty()) return emptyList()
        DmRelayLog.log("convo.nip04.history", key.account)
        val scoped =
            Nip04DmRelays(
                toMeRelays = relays.toMeRelays.filterKeys { it in armed },
                fromMeRelays = relays.fromMeRelays.filterKeys { it in armed },
            )
        return filterNip04DMsHistory(key.account, scoped, PAGE_LIMIT) { relay ->
            pager.requestedUntilFor(pk, relay)
        }
    }

    /** Steps a single [relay] to its next, older page for the open conversation(s). Driven by its marker. */
    fun advance(relay: NormalizedRelayUrl) {
        var any = false
        allKeys().forEach { if (arm(it, relay)) any = true }
        if (any) {
            _exhausted.value = false
            updateStatus()
            invalidateFilters()
        }
    }

    /** Steps every not-done, not-in-flight relay one page. For a thread too short to scroll. */
    fun advanceAll() {
        var any = false
        allKeys().forEach { key ->
            val relays = nip04DMRelays(key.room.users, key.account) ?: return@forEach
            relays.all.forEach { if (arm(key, it)) any = true }
        }
        if (any) {
            _exhausted.value = false
            updateStatus()
            invalidateFilters()
        }
    }

    private fun arm(
        key: ChatroomQueryState,
        relay: NormalizedRelayUrl,
    ): Boolean {
        val relays = nip04DMRelays(key.room.users, key.account) ?: return false
        if (relay !in relays.all) return false
        val pk = convoKey(key)
        if (loadTracker.isInFlight(relay)) return false
        if (!pager.advance(pk, relay, startUntil())) return false
        stalledRelays[pk]?.remove(relay)
        loadTracker.bind(key.account.scope)
        loadTracker.onAdvance(relay)
        return true
    }

    private fun onRelaysSilenced(relays: Set<NormalizedRelayUrl>) {
        val pk = activeConvo ?: return
        relays.forEach { markStalled(pk, it, "no response (silence timeout)") }
        updateStatus()
        recomputeExhausted()
    }

    private fun markStalled(
        pk: ConvoKey,
        relay: NormalizedRelayUrl,
        reason: String,
    ) {
        val firstTime = stalledRelays.getOrPut(pk) { ConcurrentHashMap.newKeySet() }.add(relay)
        if (firstTime) Log.d("DMPagination") { "[convo.nip04.history] ${relay.url} stalled — $reason (kept, advance to retry)" }
    }

    private fun updateStatus() {
        val pk = activeConvo ?: return
        val relays = relaysFor(pk) ?: return
        _relayCount.value = loadTracker.count()
        val start = startUntil()
        _reachedBack.value = pager.deepestReached(pk, relays.all, start)
        val stalled = stalledRelays[pk] ?: emptySet()
        _relayProgress.value =
            relays.all.associateWith { relay ->
                RelayPagingProgress(
                    reachedUntil = pager.reachedUntilFor(pk, relay, start),
                    done = pager.isDone(pk, relay),
                    stalled = relay in stalled && !pager.isDone(pk, relay),
                )
            }
    }

    private fun recomputeExhausted() {
        val pk = activeConvo ?: return
        val relays = relaysFor(pk) ?: return
        if (relays.all.isEmpty()) return
        val stalled = stalledRelays[pk] ?: emptySet()
        val pending = relays.all.any { !pager.isDone(pk, it) && it !in stalled }
        val ex = !pending
        exhaustedByConvo[pk] = ex
        if (activeConvo == pk) _exhausted.value = ex
    }

    override fun newSub(key: ChatroomQueryState): Subscription {
        val pk = convoKey(key)
        loadTracker.bind(key.account.scope)
        if (activeConvo != pk) {
            activeConvo = pk
            loadTracker.reset()
            _exhausted.value = exhaustedByConvo[pk] ?: false
            _relayCount.value = 0
            _reachedBack.value = null
            _relayProgress.value = emptyMap()
        }
        // Populate the per-relay markers (all relays at the floor, not done) so the UI can render their
        // window-limit sentinels and pull the first page when they come into view.
        updateStatus()
        return requestNewSubscription(historyListener(key))
    }

    private fun historyListener(key: ChatroomQueryState): SubscriptionListener {
        val pk = convoKey(key)
        return object : SubscriptionListener {
            override fun onEvent(
                event: Event,
                isLive: Boolean,
                relay: NormalizedRelayUrl,
                forFilters: List<Filter>?,
            ) {
                loadTracker.onActivity()
                pager.onEvent(pk, relay, event.createdAt)
                stalledRelays[pk]?.remove(relay)
            }

            override fun onEose(
                relay: NormalizedRelayUrl,
                forFilters: List<Filter>?,
            ) {
                stalledRelays[pk]?.remove(relay)
                pager.onEose(pk, relay)
                loadTracker.onSettled(relay)
                if (pager.isDone(pk, relay)) {
                    Log.d("DMPagination") { "[convo.nip04.history] ${relay.url} reached the bottom (done)" }
                }
                newEose(key, relay, TimeUtils.now(), forFilters)
                updateStatus()
                recomputeExhausted()
            }

            override fun onClosed(
                message: String,
                relay: NormalizedRelayUrl,
                forFilters: List<Filter>?,
            ) {
                loadTracker.onSettled(relay)
                markStalled(pk, relay, "CLOSED: $message")
                updateStatus()
                recomputeExhausted()
            }

            override fun onCannotConnect(
                relay: NormalizedRelayUrl,
                message: String,
                forFilters: List<Filter>?,
            ) {
                loadTracker.onSettled(relay)
                markStalled(pk, relay, "cannot connect: $message")
                updateStatus()
                recomputeExhausted()
            }
        }
    }

    companion object {
        private const val PAGE_LIMIT = 10000
    }
}
