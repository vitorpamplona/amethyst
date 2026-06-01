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

import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.PerUserEoseManager
import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.WindowLoadTracker
import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.trackingListener
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.account.nip59GiftWraps.AccountGiftWrapsEoseManager
import com.vitorpamplona.amethyst.service.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.client.subscriptions.Subscription
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Loads the account's NIP-04 DMs (kind 4) for the rooms list. It does not own a time window: it
 * follows the gift-wrap window's [AccountGiftWrapsEoseManager.windowSince] floor, so both DM
 * protocols are requested to the same depth. [reload] re-issues at the current floor (called after
 * the gift-wrap window widens); [loadingMore] reports when this protocol has covered that floor.
 */
class ChatroomListNip04SubAssembler(
    client: INostrClient,
    allKeys: () -> Set<ChatroomListState>,
    private val giftWraps: AccountGiftWrapsEoseManager,
) : PerUserEoseManager<ChatroomListState>(client, allKeys) {
    private val windowLoad = WindowLoadTracker()
    val loadingMore: StateFlow<Boolean> = windowLoad.loading

    // Account scope for the watchdog. Volatile: written on IO (newSub), read on UI (reload).
    @Volatile
    private var scope: CoroutineScope? = null

    override fun updateFilter(
        key: ChatroomListState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter>? =
        if (key.account.isWriteable()) {
            val homeRelays = key.account.homeRelays.flow.value
            val dmRelays = key.account.dmRelays.flow.value
            windowLoad.setExpectedRelays((homeRelays + dmRelays).toSet())
            val windowSince = giftWraps.windowSince(user(key))
            homeRelays.map { filterNip04DMsFromMe(key.account.userProfile(), it, windowSince) } +
                dmRelays.map { filterNip04DMsToMe(key.account.userProfile(), it, windowSince) }
        } else {
            windowLoad.setExpectedRelays(emptySet())
            emptyList()
        }

    /** Re-issues at the (now-wider) shared gift-wrap floor and tracks the load. */
    fun reload() {
        scope?.let { windowLoad.startLoading(it) }
        invalidateFilters()
    }

    override fun user(key: ChatroomListState) = key.account.userProfile()

    private val userJobMap = mutableMapOf<User, List<Job>>()

    @OptIn(FlowPreview::class)
    override fun newSub(key: ChatroomListState): Subscription {
        val user = user(key)
        scope = key.account.scope
        windowLoad.startLoading(key.account.scope)
        userJobMap[user]?.forEach { it.cancel() }
        userJobMap[user] =
            listOf(
                key.account.scope.launch(Dispatchers.IO) {
                    key.account.homeRelays.flow
                        .collectLatest { invalidateFilters() }
                },
                key.account.scope.launch(Dispatchers.IO) {
                    key.account.dmRelays.flow
                        .collectLatest { invalidateFilters() }
                },
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
}
