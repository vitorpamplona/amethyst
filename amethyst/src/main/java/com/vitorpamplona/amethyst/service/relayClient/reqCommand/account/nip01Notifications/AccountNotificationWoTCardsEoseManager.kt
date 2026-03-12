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
package com.vitorpamplona.amethyst.service.relayClient.reqCommand.account.nip01Notifications

import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.TopFilter
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.PerUserEoseManager
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.account.AccountQueryState
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.watchers.filterContactCardsToTargetKeysFromTrustedAccountsInTheRelay
import com.vitorpamplona.amethyst.service.relays.SincePerRelayMap
import com.vitorpamplona.amethyst.ui.screen.loggedIn.notifications.dal.NotificationFeedFilter
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.client.subscriptions.Subscription
import com.vitorpamplona.quartz.nip01Core.tags.people.isTaggedUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

/**
 * Loads kind 30382 (contact card) events for notification authors from the trusted relay provider.
 * Only active when the WoT (Web of Trust) notification filter is selected.
 * This ensures the WoT rank data is available for all authors in the notification feed.
 */
class AccountNotificationWoTCardsEoseManager(
    client: INostrClient,
    val cache: LocalCache,
    allKeys: () -> Set<AccountQueryState>,
) : PerUserEoseManager<AccountQueryState>(client, allKeys) {
    override fun user(key: AccountQueryState) = key.account.userProfile()

    override fun updateFilter(
        key: AccountQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter>? = buildWoTCardsFiltersForNotifications(key).ifEmpty { null }

    /**
     * Builds relay filters to load 30382 contact cards for all notification authors
     * from the trusted rank provider relay. Only returns filters when the WoT filter is active.
     */
    fun buildWoTCardsFiltersForNotifications(key: AccountQueryState): List<RelayBasedFilter> {
        if (key.account.settings.defaultNotificationFollowList.value !is TopFilter.WoT) {
            return emptyList()
        }

        val rankProvider = key.account.trustProviderList.liveUserRankProvider.value ?: return emptyList()
        val loggedInUserHex = key.account.userProfile().pubkeyHex

        val notificationAuthors = collectNotificationAuthors(loggedInUserHex)
        if (notificationAuthors.isEmpty()) return emptyList()

        return listOfNotNull(
            filterContactCardsToTargetKeysFromTrustedAccountsInTheRelay(
                targets = notificationAuthors,
                trustedAccounts = listOf(rankProvider.pubkey),
                relay = rankProvider.relayUrl,
                since = null,
            ),
        )
    }

    /**
     * Collects the pubkeys of all users who have sent notification-kind events
     * tagged to the logged-in user. Scans LocalCache notes for qualifying events.
     */
    fun collectNotificationAuthors(loggedInUserHex: HexKey): Set<HexKey> {
        val authors = HashSet<HexKey>()
        cache.notes
            .filterIntoSet { _, note ->
                note.event?.kind in NotificationFeedFilter.NOTIFICATION_KINDS &&
                    note.event?.isTaggedUser(loggedInUserHex) == true
            }.mapNotNullTo(authors) { it.author?.pubkeyHex }
        return authors
    }

    private val userJobMap = mutableMapOf<User, List<Job>>()

    @OptIn(FlowPreview::class)
    override fun newSub(key: AccountQueryState): Subscription {
        val user = user(key)
        userJobMap[user]?.forEach { it.cancel() }
        userJobMap[user] =
            listOf(
                key.account.scope.launch(Dispatchers.IO) {
                    key.account.settings.defaultNotificationFollowList.sample(500).collectLatest {
                        invalidateFilters()
                    }
                },
                key.account.scope.launch(Dispatchers.IO) {
                    key.account.trustProviderList.liveUserRankProvider.sample(1000).collectLatest {
                        invalidateFilters()
                    }
                },
            )

        return super.newSub(key)
    }

    override fun endSub(
        key: User,
        subId: String,
    ) {
        super.endSub(key, subId)
        userJobMap[key]?.forEach { it.cancel() }
        userJobMap.remove(key)
    }
}
