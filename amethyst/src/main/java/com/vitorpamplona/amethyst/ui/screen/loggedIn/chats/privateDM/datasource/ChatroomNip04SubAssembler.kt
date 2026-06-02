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
import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.WindowLoadTracker
import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.trackingListener
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.account.nip59GiftWraps.AccountGiftWrapsEoseManager
import com.vitorpamplona.amethyst.service.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.client.subscriptions.Subscription
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.flow.StateFlow

/**
 * Always-on **live tail** for one conversation's NIP-04 DMs (kind 4). A fixed one-week floor, no
 * upper bound, never widens — older history is loaded by [ChatroomNip04HistorySubAssembler] in
 * bounded slices that follow the gift-wrap history window.
 */
class ChatroomNip04SubAssembler(
    client: INostrClient,
    allKeys: () -> Set<ChatroomQueryState>,
) : PerUserAndFollowListEoseManager<ChatroomQueryState, String>(client, allKeys) {
    private val windowLoad = WindowLoadTracker("convo.nip04.live")
    val loadingMore: StateFlow<Boolean> = windowLoad.loading

    override fun updateFilter(
        key: ChatroomQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter>? =
        if (key.account.isWriteable()) {
            val sinceTime = TimeUtils.now() - AccountGiftWrapsEoseManager.LIVE_TAIL_SECONDS
            val filters = filterNip04DMs(key.room.users, key.account, sinceTime)
            windowLoad.setExpectedRelays(filters?.mapTo(mutableSetOf()) { it.relay } ?: emptySet())
            DmRelayLog.log("convo.nip04.live", key.account)
            Log.d("DMPagination") { "[convo.nip04.live] REQ since=$sinceTime (7d, no until) on ${filters?.size ?: 0} relay-filter(s): ${filters?.map { it.relay.url }?.distinct()}" }
            filters
        } else {
            windowLoad.setExpectedRelays(emptySet())
            emptyList()
        }

    override fun user(key: ChatroomQueryState) = key.account.userProfile()

    override fun list(key: ChatroomQueryState) = key.listId

    override fun newSub(key: ChatroomQueryState): Subscription {
        windowLoad.startLoading(key.account.scope)
        return requestNewSubscription(
            windowLoad.trackingListener { relay, filters -> newEose(key, relay, TimeUtils.now(), filters) },
        )
    }
}
