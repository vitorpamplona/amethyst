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
import com.vitorpamplona.amethyst.model.TopFilter
import com.vitorpamplona.amethyst.model.topNavFeeds.IFeedTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.model.topNavFeeds.OutboxLoaderState
import com.vitorpamplona.amethyst.model.topNavFeeds.allFollows.AllFollowsTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.model.topNavFeeds.aroundMe.LocationTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.model.topNavFeeds.global.GlobalTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.model.topNavFeeds.hashtag.HashtagTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.allcommunities.AllCommunitiesTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.author.AuthorsTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.community.SingleCommunityTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.muted.MutedAuthorsTopNavPerRelayFilterSet
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMetadataEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
        else -> emptySet()
    }

/**
 * Drives the Relay Groups discovery feed. A top-bar filter ([Global], [AllFollows],
 * [AroundMe], …) selects a relay set via the account's shared `topNavFilterFlow` +
 * outbox resolution; a [favorites] toggle swaps in the user's kind-10009… kind-10012
 * favorite-relays list instead. Either way the feed is every group (kind 39000) those
 * relays host, read from [LocalCache] as directory events arrive. Selection is kept in
 * this ViewModel (not persisted) — a discovery screen resets to Global each visit.
 */
@Stable
class RelayGroupDiscoveryViewModel : ViewModel() {
    private lateinit var account: Account

    val filter = MutableStateFlow<TopFilter>(TopFilter.Global)
    val favorites = MutableStateFlow(false)

    lateinit var relays: StateFlow<Set<NormalizedRelayUrl>>
        private set

    lateinit var groups: StateFlow<List<RelayGroupChannel>>
        private set

    @OptIn(ExperimentalCoroutinesApi::class)
    fun init(acc: Account) {
        if (this::account.isInitialized) return
        account = acc

        val perRelaySet = OutboxLoaderState(account.topNavFilterFlow(filter), LocalCache, viewModelScope).flow

        relays =
            combine(favorites, perRelaySet, account.relayFeedsList.flow) { showFavorites, perRelay, favoriteRelays ->
                if (showFavorites) favoriteRelays else perRelay.relayKeys()
            }.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

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

    fun isFavorite(relay: NormalizedRelayUrl): Boolean = relay in account.relayFeedsList.flow.value
}
