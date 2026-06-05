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
import com.vitorpamplona.amethyst.service.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.paging.BackwardRelayPager
import com.vitorpamplona.quartz.nip01Core.relay.client.paging.RelayPagingProgress
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.subscriptions.Subscription
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.flow.StateFlow

/**
 * Loads older NIP-04 DMs (kind 4) for one conversation by **`until`+`limit` paging, per relay, on
 * demand**. Each relay advances exactly one page when the conversation's on-screen window-limit marker
 * for that relay asks ([advance]); otherwise it parks. Nothing is walked proactively — a relay pages
 * only while its marker is visible and keeps paging while it stays visible.
 *
 * The per-relay cursor / stall / exhaustion bookkeeping lives in the shared [BackwardRelayPager]; this
 * class only builds the (per-relay scoped) NIP-04 REQ filters and forwards relay callbacks into it. A
 * relay is *done* once it answers an empty page; one that won't answer (auth CLOSE, unreachable, or
 * silent) is flagged *stalled* but kept. [exhausted] flips once every relay is either done or stalled.
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

    // The conversation's relay set for a key, resolved via the outbox model (per-relay scoped).
    private fun relaysFor(pk: ConvoKey): Collection<NormalizedRelayUrl>? = allKeys().firstOrNull { convoKey(it) == pk }?.let { nip04DMRelays(it.room.users, it.account)?.all }

    private val pager = BackwardRelayPager<ConvoKey>("convo.nip04.history", relaysFor = ::relaysFor)

    val loadingMore: StateFlow<Boolean> = pager.loadingMore
    val exhausted: StateFlow<Boolean> = pager.exhausted
    val relayCount: StateFlow<Int> = pager.relayCount
    val stalledCount: StateFlow<Int> = pager.stalledCount
    val reachedBack: StateFlow<Long?> = pager.reachedBack
    val relayProgress: StateFlow<Map<NormalizedRelayUrl, RelayPagingProgress>> = pager.relayProgress

    override fun user(key: ChatroomQueryState) = key.account.userProfile()

    override fun list(key: ChatroomQueryState) = key.listId

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
        return filterNip04DMsHistory(key.account, scoped, pager.pageLimit) { relay ->
            pager.requestedUntilFor(pk, relay)
        }
    }

    /** Steps a single [relay] to its next, older page for the open conversation(s). Driven by its marker. */
    fun advance(relay: NormalizedRelayUrl) {
        var any = false
        allKeys().forEach { if (pager.advance(convoKey(it), relay, it.account.scope)) any = true }
        if (any) invalidateFilters()
    }

    /** Steps every not-done, not-in-flight relay one page. For a thread too short to scroll. */
    fun advanceAll() {
        var any = false
        allKeys().forEach { if (pager.advanceAll(convoKey(it), it.account.scope)) any = true }
        if (any) {
            Log.d("DMPagination") { "[convo.nip04.history] advanceAll (empty-thread bootstrap)" }
            invalidateFilters()
        }
    }

    override fun newSub(key: ChatroomQueryState): Subscription {
        // Repoint the shared display flows to this conversation and populate the per-relay markers (all
        // relays at the floor, not done) so the UI can render their sentinels and pull the first page.
        pager.activate(convoKey(key))
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
                pager.onEvent(pk, relay, event.createdAt)
            }

            override fun onEose(
                relay: NormalizedRelayUrl,
                forFilters: List<Filter>?,
            ) {
                if (pager.onEose(pk, relay)) {
                    Log.d("DMPagination") { "[convo.nip04.history] ${relay.url} reached the bottom (done)" }
                }
                newEose(key, relay, TimeUtils.now(), forFilters)
            }

            override fun onClosed(
                message: String,
                relay: NormalizedRelayUrl,
                forFilters: List<Filter>?,
            ) {
                pager.onClosed(pk, relay, message)
            }

            override fun onCannotConnect(
                relay: NormalizedRelayUrl,
                message: String,
                forFilters: List<Filter>?,
            ) {
                pager.onCannotConnect(pk, relay, message)
            }
        }
    }
}
