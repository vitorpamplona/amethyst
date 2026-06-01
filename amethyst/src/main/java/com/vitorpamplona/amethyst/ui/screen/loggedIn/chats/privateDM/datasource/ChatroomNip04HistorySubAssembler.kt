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

import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.PerUserAndFollowListEoseManager
import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.WindowLoadTracker
import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.trackingListener
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.account.nip59GiftWraps.AccountGiftWrapsHistoryEoseManager
import com.vitorpamplona.amethyst.service.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.client.subscriptions.Subscription
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * Loads older NIP-04 DMs (kind 4) for one conversation, following the gift-wrap history's current
 * bounded slice ([AccountGiftWrapsHistoryEoseManager.currentSlice]) so a thread shows both DM
 * protocols to the same depth. NIP-04 timestamps are exact, so the slice needs no margin. [reload]
 * re-issues at the now-advanced slice; idle until the first slice is opened.
 */
class ChatroomNip04HistorySubAssembler(
    client: INostrClient,
    allKeys: () -> Set<ChatroomQueryState>,
    private val giftWrapsHistory: AccountGiftWrapsHistoryEoseManager,
) : PerUserAndFollowListEoseManager<ChatroomQueryState, String>(client, allKeys) {
    private val windowLoad = WindowLoadTracker("convo.nip04.history")
    val loadingMore: StateFlow<Boolean> = windowLoad.loading

    // Account scope for the watchdog. Volatile: written on IO (newSub), read on UI (reload).
    @Volatile
    private var scope: CoroutineScope? = null

    override fun updateFilter(
        key: ChatroomQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter>? {
        val slice = giftWrapsHistory.currentSlice(user(key))
        if (!key.account.isWriteable() || slice == null) {
            windowLoad.setExpectedRelays(emptySet())
            return emptyList()
        }
        val (sliceSince, sliceUntil) = slice
        val filters = filterNip04DMs(key.room.users, key.account, sliceSince, sliceUntil)
        windowLoad.setExpectedRelays(filters?.mapTo(mutableSetOf()) { it.relay } ?: emptySet())
        Log.d("DMPagination") { "[convo.nip04.history] REQ slice since=$sliceSince until=$sliceUntil on ${filters?.size ?: 0} relay-filter(s)" }
        return filters
    }

    /** Re-issues at the gift-wrap history's now-advanced slice and tracks the load. */
    fun reload() {
        Log.d("DMPagination") { "[convo.nip04.history] reload" }
        scope?.let { windowLoad.startLoading(it) }
        invalidateFilters()
    }

    override fun user(key: ChatroomQueryState) = key.account.userProfile()

    override fun list(key: ChatroomQueryState) = key.listId

    override fun newSub(key: ChatroomQueryState): Subscription {
        scope = key.account.scope
        return requestNewSubscription(
            windowLoad.trackingListener { relay, filters -> newEose(key, relay, TimeUtils.now(), filters) },
        )
    }
}
