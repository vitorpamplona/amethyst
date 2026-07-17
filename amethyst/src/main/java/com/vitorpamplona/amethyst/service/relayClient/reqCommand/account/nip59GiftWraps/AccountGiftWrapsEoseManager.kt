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

import com.vitorpamplona.amethyst.commons.model.chats.ChatFeedType
import com.vitorpamplona.amethyst.commons.model.privateChats.DmHistoryTuning
import com.vitorpamplona.amethyst.commons.relayClient.nip17Dm.filterGiftWrapsToPubkey
import com.vitorpamplona.amethyst.commons.relayClient.paging.WindowLoadTracker
import com.vitorpamplona.amethyst.commons.relayClient.paging.trackingListener
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.DmRelayLog
import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.PerUserEoseManager
import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.launchChatFeedToggleObserver
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.account.AccountQueryState
import com.vitorpamplona.amethyst.service.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.client.subscriptions.Subscription
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Always-on **live tail** for the account's NIP-17 gift wraps (kind 1059). It keeps a fixed
 * one-week floor with no upper bound, so the messages list is usable on boot and new incoming
 * messages always stream in. It deliberately never widens: pulling older history is the job of
 * [AccountGiftWrapsHistoryEoseManager], which fetches the past in bounded, one-shot slices so a
 * widen never re-streams what this tail already holds.
 */
class AccountGiftWrapsEoseManager(
    client: INostrClient,
    allKeys: () -> Set<AccountQueryState>,
) : PerUserEoseManager<AccountQueryState>(client, allKeys) {
    override fun user(key: AccountQueryState) = key.account.userProfile()

    // The initial-load tracker drives the boot spinner: it stays true until every DM relay has
    // settled (EOSE / CLOSED / cannot-connect) on the one-week tail.
    private val windowLoad = WindowLoadTracker("giftwrap.live")
    val loadingMore: StateFlow<Boolean> = windowLoad.loading

    override fun updateFilter(
        key: AccountQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter> {
        if (!key.account.isWriteable() || !key.account.settings.isChatFeedEnabled(ChatFeedType.NIP17)) {
            windowLoad.setExpectedRelays(emptySet())
            return emptyList()
        }
        val relays = key.account.dmRelays.flow.value
        windowLoad.setExpectedRelays(relays.toSet())
        val sinceTime = DmHistoryTuning.recentBoundary()
        DmRelayLog.log("giftwrap.live", key.account)
        Log.d(TAG) { "[giftwrap.live] REQ since=$sinceTime (no until) on ${relays.size} relay(s): ${relays.map { it.url }}" }
        return relays.flatMap { relay ->
            filterGiftWrapsToPubkey(relay = relay, pubkey = user(key).pubkeyHex, since = sinceTime)
        }
    }

    private val userJobMap = mutableMapOf<User, List<Job>>()

    @OptIn(FlowPreview::class)
    override fun newSub(key: AccountQueryState): Subscription {
        val user = user(key)
        windowLoad.startLoading(key.account.scope)
        userJobMap[user]?.forEach { it.cancel() }
        userJobMap[user] =
            listOf(
                key.account.scope.launch(Dispatchers.IO) {
                    key.account.dmRelays.flow
                        .collectLatest { invalidateFilters() }
                },
                key.account.scope.launchChatFeedToggleObserver(key.account, ChatFeedType.NIP17) { invalidateFilters() },
            )

        return requestNewSubscription(
            windowLoad.trackingListener { relay, filters -> newEose(key, relay, TimeUtils.now(), filters) },
        )
    }

    override fun endSub(
        key: User,
        subId: String,
    ) {
        super.endSub(key, subId)
        userJobMap[key]?.forEach { it.cancel() }
    }

    companion object {
        private const val TAG = "DMPagination"
    }
}
