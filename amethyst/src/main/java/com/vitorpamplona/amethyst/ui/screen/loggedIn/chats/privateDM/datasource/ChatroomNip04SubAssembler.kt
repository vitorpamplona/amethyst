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
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.account.nip59GiftWraps.AccountGiftWrapsEoseManager
import com.vitorpamplona.amethyst.service.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.client.subscriptions.Subscription
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * Loads one conversation's NIP-04 DMs (kind 4). Like the rooms-list loader, it follows the account
 * gift-wrap window's [AccountGiftWrapsEoseManager.windowSince] floor so a thread shows both DM
 * protocols to the same depth. [reload] re-issues at the current floor; [loadingMore] reports when
 * this protocol has covered it, which the conversation screen joins with the gift-wrap loader's flag
 * to decide how deep the thread is safe to reveal.
 */
class ChatroomNip04SubAssembler(
    client: INostrClient,
    allKeys: () -> Set<ChatroomQueryState>,
    private val giftWraps: AccountGiftWrapsEoseManager,
) : PerUserAndFollowListEoseManager<ChatroomQueryState, String>(client, allKeys) {
    private val windowLoad = WindowLoadTracker("convo.nip04")
    val loadingMore: StateFlow<Boolean> = windowLoad.loading

    // Account scope for the watchdog. Volatile: written on IO (newSub), read on UI (reload).
    @Volatile
    private var scope: CoroutineScope? = null

    override fun updateFilter(
        key: ChatroomQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter>? =
        if (key.account.isWriteable()) {
            val windowSince = giftWraps.windowSince(user(key))
            val filters = filterNip04DMs(key.room.users, key.account, windowSince)
            windowLoad.setExpectedRelays(filters?.mapTo(mutableSetOf()) { it.relay } ?: emptySet())
            val daysAgo = (TimeUtils.now() - windowSince) / TimeUtils.ONE_DAY
            Log.d("DMPagination") { "[convo.nip04] REQ since=$windowSince (${daysAgo}d) on ${filters?.size ?: 0} relay-filter(s)" }
            filters
        } else {
            windowLoad.setExpectedRelays(emptySet())
            emptyList()
        }

    /** Re-issues at the (now-wider) shared gift-wrap floor and tracks the load. */
    fun reload() {
        Log.d("DMPagination") { "[convo.nip04] reload" }
        scope?.let { windowLoad.startLoading(it) }
        invalidateFilters()
    }

    override fun user(key: ChatroomQueryState) = key.account.userProfile()

    override fun list(key: ChatroomQueryState) = key.listId

    override fun newSub(key: ChatroomQueryState): Subscription {
        scope = key.account.scope
        windowLoad.startLoading(key.account.scope)
        return requestNewSubscription(
            windowLoad.trackingListener { relay, filters -> newEose(key, relay, TimeUtils.now(), filters) },
        )
    }
}
