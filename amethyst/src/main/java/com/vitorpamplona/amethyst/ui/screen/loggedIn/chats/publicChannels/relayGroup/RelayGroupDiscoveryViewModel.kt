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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.relayGroup

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.relayGroup.dal.GroupDiscoveryConstraint
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.relayGroup.dal.toGroupConstraints
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupAdminsEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMembersEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMetadataEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/**
 * Drives the Relay Groups discovery feed. The top bar's [com.vitorpamplona.amethyst.ui.navigation.topbars.FeedFilterSpinner]
 * writes the selection to the account's persisted `defaultRelayGroupsDiscoveryFollowList` (Global /
 * Follows / a followed hashtag or geohash / a specific relay — including favorite relays, which
 * surface as relay chips), exactly like every other feed.
 *
 * Unlike the note feeds, a group's kind-39000 is relay-signed, so the resolved filter is turned into
 * a per-relay [GroupDiscoveryConstraint] ([toGroupConstraints]): the relays to query, plus how each
 * group on them is matched (relay-key follow / follow-is-admin / follow-is-member for people filters,
 * `#t`/`#g` tag match for topic/geo filters, or every group for Global). The feed is every cached
 * group that satisfies its host relay's constraint, re-scanned as directory events arrive.
 */
@Stable
class RelayGroupDiscoveryViewModel : ViewModel() {
    private lateinit var account: Account

    /** The relay → constraint map the selected top-nav filter resolved to. */
    lateinit var constraints: StateFlow<Map<NormalizedRelayUrl, GroupDiscoveryConstraint>>
        private set

    lateinit var groups: StateFlow<List<RelayGroupChannel>>
        private set

    @OptIn(ExperimentalCoroutinesApi::class)
    fun init(acc: Account) {
        if (this::account.isInitialized) return
        account = acc

        constraints =
            account.liveRelayGroupsDiscoveryFollowListsPerRelay
                .map { it.toGroupConstraints() }
                .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

        groups =
            constraints
                .flatMapLatest { byRelay ->
                    // Re-scan the cache whenever any directory event lands — metadata (39000) OR
                    // a roster change (39001/39002), since the people match reads admins/members.
                    // The emitted list is ignored; matchingGroups reads the cache directly. The
                    // initial emit renders whatever's already cached for these relays immediately.
                    LocalCache
                        .observeEvents<BaseAddressableEvent>(
                            Filter(
                                kinds =
                                    listOf(
                                        GroupMetadataEvent.KIND,
                                        GroupAdminsEvent.KIND,
                                        GroupMembersEvent.KIND,
                                    ),
                            ),
                        ).onStart { emit(emptyList()) }
                        .map { matchingGroups(byRelay) }
                }.flowOn(Dispatchers.IO)
                .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    }

    private fun matchingGroups(byRelay: Map<NormalizedRelayUrl, GroupDiscoveryConstraint>): List<RelayGroupChannel> =
        if (byRelay.isEmpty()) {
            emptyList()
        } else {
            LocalCache.relayGroupChannels
                .filter { key, channel ->
                    channel.event != null && byRelay[key.relayUrl]?.matches(channel) == true
                }.sortedWith(
                    compareByDescending<RelayGroupChannel> { it.memberCount() }
                        .thenBy { it.toBestDisplayName().lowercase() },
                )
        }
}
