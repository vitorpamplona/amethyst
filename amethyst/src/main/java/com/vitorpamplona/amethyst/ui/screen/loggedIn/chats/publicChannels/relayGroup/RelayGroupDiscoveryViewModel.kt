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
import com.vitorpamplona.amethyst.model.topNavFeeds.IFeedTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.model.topNavFeeds.allFollows.AllFollowsTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.model.topNavFeeds.aroundMe.LocationTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.model.topNavFeeds.global.GlobalTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.model.topNavFeeds.hashtag.HashtagTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.allcommunities.AllCommunitiesTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.author.AuthorsTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.community.SingleCommunityTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.muted.MutedAuthorsTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.model.topNavFeeds.relay.RelayTopNavPerRelayFilterSet
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
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
 * The relays a top-nav filter resolves to. A NIP-29 group's kind-39000 is relay-signed
 * (not authored by follows, not hashtag/geo-tagged), so — unlike the author-based feeds —
 * the ONLY thing a filter contributes to group discovery is its RELAY SET. We take the
 * keys of whatever per-relay set the selected [TopFilter] produced and query kind 39000
 * on them.
 */
fun IFeedTopNavPerRelayFilterSet.relayKeys(): Set<NormalizedRelayUrl> =
    when (this) {
        is GlobalTopNavPerRelayFilterSet -> set.keys
        is AllFollowsTopNavPerRelayFilterSet -> set.keys
        is AuthorsTopNavPerRelayFilterSet -> set.keys
        is HashtagTopNavPerRelayFilterSet -> set.keys
        is LocationTopNavPerRelayFilterSet -> set.keys
        is AllCommunitiesTopNavPerRelayFilterSet -> set.keys
        is SingleCommunityTopNavPerRelayFilterSet -> set.keys
        is MutedAuthorsTopNavPerRelayFilterSet -> set.keys
        // A single relay chip (includes a starred favorite relay).
        is RelayTopNavPerRelayFilterSet -> setOf(relayUrl)
        // DVM algo-feed selections carry no group-hosting relays.
        else -> emptySet()
    }

/**
 * Drives the Relay Groups discovery feed. The top bar's [FeedFilterSpinner] writes the
 * selection to the account's persisted `defaultRelayGroupsDiscoveryFollowList` (Global /
 * Follows / a followed hashtag or geohash / a specific relay — including favorite relays,
 * which surface as relay chips), exactly like every other feed. Because a group's kind-39000
 * is relay-signed, the only thing the filter contributes is its RELAY SET ([relayKeys]); the
 * feed is every group those relays host, read from [LocalCache] as directory events arrive.
 */
@Stable
class RelayGroupDiscoveryViewModel : ViewModel() {
    private lateinit var account: Account

    lateinit var relays: StateFlow<Set<NormalizedRelayUrl>>
        private set

    lateinit var groups: StateFlow<List<RelayGroupChannel>>
        private set

    @OptIn(ExperimentalCoroutinesApi::class)
    fun init(acc: Account) {
        if (this::account.isInitialized) return
        account = acc

        relays =
            account.liveRelayGroupsDiscoveryFollowListsPerRelay
                .map { it.relayKeys() }
                .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

        groups =
            relays
                .flatMapLatest { relaySet ->
                    // Re-scan the cache whenever a new kind-39000 lands; the initial emit renders
                    // whatever's already cached for this relay set immediately.
                    LocalCache
                        .observeEvents<GroupMetadataEvent>(Filter(kinds = listOf(GroupMetadataEvent.KIND)))
                        .onStart { emit(emptyList()) }
                        .map { groupsOnRelays(relaySet) }
                }.flowOn(Dispatchers.IO)
                .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    }

    private fun groupsOnRelays(relaySet: Set<NormalizedRelayUrl>): List<RelayGroupChannel> =
        if (relaySet.isEmpty()) {
            emptyList()
        } else {
            LocalCache.relayGroupChannels
                .filter { key, channel -> key.relayUrl in relaySet && channel.event != null }
                .sortedWith(
                    compareByDescending<RelayGroupChannel> { it.memberCount() }
                        .thenBy { it.toBestDisplayName().lowercase() },
                )
        }
}
