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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.calendars.datasource

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

class CalendarsSubAssembler(
    client: INostrClient,
    allKeys: () -> Set<CalendarsQueryState>,
) : PerUserAndFollowListEoseManager<CalendarsQueryState, TopFilter>(client, allKeys) {
    override fun updateFilter(
        key: CalendarsQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter> {
        val feedSettings = key.followsPerRelay()

        return makeCalendarsFilter(feedSettings, since, key.feedStates.calendarAppointmentsFeed.lastNoteCreatedAtIfFilled())
    }

    override fun user(key: CalendarsQueryState) = key.account.userProfile()

    override fun list(key: CalendarsQueryState) = key.listName()

    fun CalendarsQueryState.listNameFlow() = account.settings.defaultCalendarsFollowList

    fun CalendarsQueryState.listName() = listNameFlow().value

    fun CalendarsQueryState.followsPerRelayFlow() = account.liveCalendarsFollowListsPerRelay

    fun CalendarsQueryState.followsPerRelay() = followsPerRelayFlow().value

    val userJobMap = mutableMapOf<User, List<Job>>()

    @OptIn(FlowPreview::class)
    override fun newSub(key: CalendarsQueryState): Subscription {
        val user = user(key)
        userJobMap[user]?.forEach { it.cancel() }
        userJobMap[user] =
            listOf(
                key.scope.launch(Dispatchers.IO) {
                    key.listNameFlow().collectLatest {
                        // Calendar events are addressables stored in LocalCache's WeakReference
                        // map: while the user is viewing list B the strong refs from list A's UI
                        // are gone and the GC may reclaim those notes. If the EOSE cursor for the
                        // list the user is switching back to still says "you have everything up
                        // to T", the relay won't re-send the now-evicted events. Clearing the
                        // cursor here forces a fresh fetch so the feed comes back whole.
                        clearEoseFor(key)
                        invalidateFilters()
                    }
                },
                key.scope.launch(Dispatchers.IO) {
                    key.followsPerRelayFlow().sample(500).collectLatest {
                        invalidateFilters()
                    }
                },
                key.account.scope.launch(Dispatchers.IO) {
                    key.feedStates.calendarAppointmentsFeed.lastNoteCreatedAtWhenFullyLoaded.sample(5000).collectLatest {
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
