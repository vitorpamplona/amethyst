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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.badges.datasource

import com.vitorpamplona.amethyst.model.TopFilter
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.PerUserAndFollowListEoseManager
import com.vitorpamplona.amethyst.service.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.client.subscriptions.Subscription
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

class BadgesSubAssembler(
    client: INostrClient,
    allKeys: () -> Set<BadgesQueryState>,
) : PerUserAndFollowListEoseManager<BadgesQueryState, TopFilter>(client, allKeys) {
    override fun updateFilter(
        key: BadgesQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter> {
        val listName = key.listName()
        val defaultSince = key.feedStates.badgesFeed.lastNoteCreatedAtIfFilled()

        return if (listName == TopFilter.Mine) {
            val outbox = key.account.outboxRelays.flow.value
            filterBadgesMine(key.account.userProfile().pubkeyHex, outbox, since)
        } else {
            makeBadgesFilter(key.followsPerRelay(), since, defaultSince)
        }
    }

    override fun user(key: BadgesQueryState) = key.account.userProfile()

    override fun list(key: BadgesQueryState) = key.listName()

    fun BadgesQueryState.listNameFlow() = account.settings.defaultBadgesFollowList

    fun BadgesQueryState.listName() = listNameFlow().value

    fun BadgesQueryState.followsPerRelayFlow() = account.liveBadgesFollowListsPerRelay

    fun BadgesQueryState.followsPerRelay() = followsPerRelayFlow().value

    val userJobMap = mutableMapOf<User, List<Job>>()

    @OptIn(FlowPreview::class)
    override fun newSub(key: BadgesQueryState): Subscription {
        val user = user(key)
        userJobMap[user]?.forEach { it.cancel() }
        userJobMap[user] =
            listOf(
                key.scope.launch(Dispatchers.IO) {
                    key.listNameFlow().collectLatest {
                        invalidateFilters()
                    }
                },
                key.scope.launch(Dispatchers.IO) {
                    key.followsPerRelayFlow().sample(500).collectLatest {
                        invalidateFilters()
                    }
                },
                key.account.scope.launch(Dispatchers.IO) {
                    key.feedStates.badgesFeed.lastNoteCreatedAtWhenFullyLoaded.sample(5000).collectLatest {
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
    }
}
