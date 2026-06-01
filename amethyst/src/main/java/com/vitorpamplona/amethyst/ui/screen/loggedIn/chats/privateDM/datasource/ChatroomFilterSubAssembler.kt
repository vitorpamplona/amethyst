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
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.account.nip59GiftWraps.AccountGiftWrapsEoseManager
import com.vitorpamplona.amethyst.service.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.subscriptions.Subscription
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

class ChatroomFilterSubAssembler(
    client: INostrClient,
    allKeys: () -> Set<ChatroomQueryState>,
    // The account-wide gift-wrap window is the single source of truth for how far back DMs are
    // requested; NIP-04 here follows its floor so a thread shows both protocols to the same depth.
    private val giftWraps: AccountGiftWrapsEoseManager,
) : PerUserAndFollowListEoseManager<ChatroomQueryState, String>(client, allKeys) {
    // A NIP-04 load is "in flight" until every relay it was sent to has answered (or a timeout).
    // The conversation screen reads this alongside the gift-wrap loader's flag to know when BOTH
    // protocols have fully covered the current floor, so it never reveals a half-loaded depth.
    private val windowLoad = WindowLoadTracker()
    val loadingMore: StateFlow<Boolean> = windowLoad.loading

    // Account scope to run the window-load watchdog on, captured when the subscription opens.
    @Volatile
    private var scope: CoroutineScope? = null

    override fun updateFilter(
        key: ChatroomQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter>? =
        if (key.account.isWriteable()) {
            val filters = filterNip04DMs(key.room.users, key.account, giftWraps.windowSince(user(key)))
            windowLoad.setExpectedRelays(filters?.mapTo(mutableSetOf()) { it.relay } ?: emptySet())
            filters
        } else {
            windowLoad.setExpectedRelays(emptySet())
            emptyList()
        }

    /** Re-issues the NIP-04 subscription at the (now-wider) shared gift-wrap floor and tracks the load. */
    fun reload() {
        scope?.let { windowLoad.startLoading(it) }
        invalidateFilters()
    }

    override fun user(key: ChatroomQueryState) = key.account.userProfile()

    override fun list(key: ChatroomQueryState) = key.listId

    override fun newSub(key: ChatroomQueryState): Subscription {
        scope = key.account.scope
        windowLoad.startLoading(key.account.scope)

        // Custom listener (vs super.newSub) so every event — stored backfill included — keeps the
        // window-load watchdog alive and EOSEs mark relays answered, feeding [loadingMore].
        return requestNewSubscription(
            object : SubscriptionListener {
                override fun onEose(
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    windowLoad.onRelayResponded(relay)
                    newEose(key, relay, TimeUtils.now(), forFilters)
                }

                override fun onEvent(
                    event: Event,
                    isLive: Boolean,
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    windowLoad.onActivity()
                    if (isLive) {
                        windowLoad.onRelayResponded(relay)
                        newEose(key, relay, TimeUtils.now(), forFilters)
                    }
                }
            },
        )
    }
}
