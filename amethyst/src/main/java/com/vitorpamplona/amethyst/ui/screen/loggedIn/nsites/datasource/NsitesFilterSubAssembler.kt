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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.nsites.datasource

import com.vitorpamplona.amethyst.model.TopFilter
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.PerUserAndFollowListEoseManager
import com.vitorpamplona.amethyst.service.relays.SincePerRelayMap
import com.vitorpamplona.amethyst.ui.screen.loggedIn.nsites.datasource.subassemblies.filterNsitesMine
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.client.subscriptions.Subscription
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

/**
 * Subscribes to NIP-5A static-site manifests (kinds 15128/35128), honoring the top-nav follow-list
 * selection at the relay level: one subscription per logged-in user + selected list, with the authors
 * resolved to each author's outbox relays (via [com.vitorpamplona.amethyst.model.Account.liveNsitesFollowListsPerRelay]).
 * Mirrors [com.vitorpamplona.amethyst.ui.screen.loggedIn.pictures.datasource.PicturesSubAssembler]; the
 * only spinner options are author-based (see `authorOnlyRoutes`), so [makeNsitesFilter] needs no
 * tag-based branches.
 */
class NsitesFilterSubAssembler(
    client: INostrClient,
    allKeys: () -> Set<NsitesQueryState>,
) : PerUserAndFollowListEoseManager<NsitesQueryState, TopFilter>(client, allKeys) {
    override fun updateFilter(
        key: NsitesQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter> {
        // "Mine" bypasses the follow-list machinery: query the user's own nSites by author against
        // their outbox relays (same pattern as badges/music). The shared TopFilter.Mine flow falls
        // back to all-follows, so it can't be used here.
        if (key.listName() == TopFilter.Mine) {
            return filterNsitesMine(key.account.userProfile().pubkeyHex, key.account.outboxRelays.flow.value, since)
        }
        return makeNsitesFilter(key.followsPerRelay(), since)
    }

    override fun user(key: NsitesQueryState) = key.account.userProfile()

    override fun list(key: NsitesQueryState) = key.listName()

    fun NsitesQueryState.listNameFlow() = account.settings.defaultNsitesFollowList

    fun NsitesQueryState.listName() = listNameFlow().value

    fun NsitesQueryState.followsPerRelayFlow() = account.liveNsitesFollowListsPerRelay

    fun NsitesQueryState.followsPerRelay() = followsPerRelayFlow().value

    val userJobMap = mutableMapOf<User, List<Job>>()

    @OptIn(FlowPreview::class)
    override fun newSub(key: NsitesQueryState): Subscription {
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
