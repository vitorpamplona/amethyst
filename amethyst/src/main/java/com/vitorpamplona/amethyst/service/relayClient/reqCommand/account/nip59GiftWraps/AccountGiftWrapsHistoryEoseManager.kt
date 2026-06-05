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
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.account.AccountQueryState
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
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.flow.StateFlow
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
 * The per-relay cursor / stall / exhaustion bookkeeping lives in the shared [BackwardRelayPager]; this
 * class only builds the gift-wrap REQ filters and forwards relay callbacks into the pager. A relay is
 * *done* once it answers an empty page; one that won't answer (auth CLOSE, unreachable, or silent) is
 * flagged *stalled* but kept. [exhausted] flips once every relay is either done or stalled.
 */
class AccountGiftWrapsHistoryEoseManager(
    client: INostrClient,
    allKeys: () -> Set<AccountQueryState>,
) : PerUserEoseManager<AccountQueryState>(client, allKeys) {
    override fun user(key: AccountQueryState) = key.account.userProfile()

    // The account behind each user pubkey, captured on subscribe so the pager's relaysFor lookup and the
    // advance() API can read the DM relay list (and the account scope) without the key.
    private val accounts = ConcurrentHashMap<HexKey, Account>()

    // Per-relay demand-driven paging, keyed by account pubkey so switching accounts preserves progress.
    private val pager =
        BackwardRelayPager<HexKey>("giftwrap.history") { pk ->
            accounts[pk]?.dmRelays?.flow?.value
        }

    val loadingMore: StateFlow<Boolean> = pager.loadingMore
    val exhausted: StateFlow<Boolean> = pager.exhausted
    val relayCount: StateFlow<Int> = pager.relayCount
    val stalledCount: StateFlow<Int> = pager.stalledCount
    val reachedBack: StateFlow<Long?> = pager.reachedBack
    val relayProgress: StateFlow<Map<NormalizedRelayUrl, RelayPagingProgress>> = pager.relayProgress

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
            Log.d(TAG) { "[giftwrap.history] REQ ${relay.url} until ${daysAgo(until)}d, limit=${pager.pageLimit}" }
            filterGiftWrapsToPubkey(relay = relay, pubkey = user.pubkeyHex, since = null, until = until, limit = pager.pageLimit)
        }
    }

    /** Steps a single [relay] to its next, older page. Driven by that relay's on-screen window-limit marker. */
    fun advance(
        user: User,
        relay: NormalizedRelayUrl,
    ) {
        val account = accounts[user.pubkeyHex] ?: return
        if (pager.advance(user.pubkeyHex, relay, account.scope)) invalidateFilters()
    }

    /** Steps every not-done, not-in-flight relay one page. For the empty/initial boundary (nothing to scroll). */
    fun advanceAll(user: User) {
        val account = accounts[user.pubkeyHex] ?: return
        if (pager.advanceAll(user.pubkeyHex, account.scope)) {
            Log.d(TAG) { "[giftwrap.history] advanceAll (empty-feed bootstrap)" }
            invalidateFilters()
        }
    }

    override fun newSub(key: AccountQueryState): Subscription {
        val user = user(key)
        accounts[user.pubkeyHex] = key.account
        // Repoint the shared display flows to this account and populate the per-relay markers (all relays
        // at the floor, not done) so the UI can render their sentinels and pull the first page on view.
        pager.activate(user.pubkeyHex)
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
                pager.onEvent(user.pubkeyHex, relay, event.createdAt)
            }

            override fun onEose(
                relay: NormalizedRelayUrl,
                forFilters: List<Filter>?,
            ) {
                if (pager.onEose(user.pubkeyHex, relay)) {
                    Log.d(TAG) { "[giftwrap.history] ${relay.url} reached the bottom (done)" }
                }
                // No auto-advance: the relay parks here until its marker asks for the next page.
                newEose(key, relay, TimeUtils.now(), forFilters)
            }

            override fun onClosed(
                message: String,
                relay: NormalizedRelayUrl,
                forFilters: List<Filter>?,
            ) {
                pager.onClosed(user.pubkeyHex, relay, message)
            }

            override fun onCannotConnect(
                relay: NormalizedRelayUrl,
                message: String,
                forFilters: List<Filter>?,
            ) {
                pager.onCannotConnect(user.pubkeyHex, relay, message)
            }
        }

    companion object {
        private const val TAG = "DMPagination"
    }
}
