/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.datasource

import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.PerUserEoseManager
import com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip23LongForm.subassemblies.filterLongFormByGeohash
import com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip23LongForm.subassemblies.filterLongFormByHashtag
import com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip28Chats.subassemblies.filterPublicChatsByGeohash
import com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip28Chats.subassemblies.filterPublicChatsByHashtag
import com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip51FollowSets.subassemblies.filterFollowSetsByGeohash
import com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip51FollowSets.subassemblies.filterFollowSetsByHashtag
import com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip53LiveActivities.subassemblies.filterLiveActivitiesByGeohash
import com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip53LiveActivities.subassemblies.filterLiveActivitiesByHashtag
import com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip72Communities.subassemblies.filterCommunityPostsByGeohash
import com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip72Communities.subassemblies.filterCommunityPostsByHashtag
import com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip90DVMs.subassemblies.filterContentDVMsByGeohash
import com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip90DVMs.subassemblies.filterContentDVMsByHashtag
import com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip99Classifieds.subassemblies.filterClassifiedsByGeohash
import com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip99Classifieds.subassemblies.filterClassifiedsByHashtag
import com.vitorpamplona.ammolite.relays.NostrClient
import com.vitorpamplona.ammolite.relays.TypedFilter
import com.vitorpamplona.ammolite.relays.datasources.Subscription
import com.vitorpamplona.ammolite.relays.filters.EOSETime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MixGeohashHashtagsDiscoverySubAssembler(
    client: NostrClient,
    allKeys: () -> Set<DiscoveryQueryState>,
) : PerUserEoseManager<DiscoveryQueryState>(client, allKeys) {
    override fun updateFilter(
        key: DiscoveryQueryState,
        since: Map<String, EOSETime>?,
    ): List<TypedFilter>? = updateFilter(key.followLists()?.hashtags, key.followLists()?.geotags, since)

    fun updateFilter(
        hashtags: Set<String>?,
        geotags: Set<String>?,
        since: Map<String, EOSETime>?,
    ): List<TypedFilter>? {
        val hashtagFilters =
            if (hashtags != null && !hashtags.isEmpty()) {
                listOfNotNull(
                    filterClassifiedsByHashtag(hashtags, since),
                    filterPublicChatsByHashtag(hashtags, since),
                    filterFollowSetsByHashtag(hashtags, since),
                    filterLongFormByHashtag(hashtags, since),
                    filterContentDVMsByHashtag(hashtags, since),
                    filterLiveActivitiesByHashtag(hashtags, since),
                    filterCommunityPostsByHashtag(hashtags, since),
                ).flatten()
            } else {
                emptyList()
            }

        val geoHashFilters =
            if (geotags != null && !geotags.isEmpty()) {
                listOfNotNull(
                    filterClassifiedsByGeohash(geotags, since),
                    filterPublicChatsByGeohash(geotags, since),
                    filterFollowSetsByGeohash(geotags, since),
                    filterLongFormByGeohash(geotags, since),
                    filterContentDVMsByGeohash(geotags, since),
                    filterLiveActivitiesByGeohash(geotags, since),
                    filterCommunityPostsByGeohash(geotags, since),
                ).flatten()
            } else {
                emptyList()
            }

        return hashtagFilters + geoHashFilters
    }

    override fun user(key: DiscoveryQueryState) = key.account.userProfile()

    fun DiscoveryQueryState.followListsFlow() = account.liveDiscoveryFollowLists

    fun DiscoveryQueryState.followLists() = followListsFlow().value

    val userJobMap = mutableMapOf<User, Job>()

    override fun newSub(key: DiscoveryQueryState): Subscription {
        val user = user(key)
        userJobMap[user]?.cancel()
        userJobMap[user] =
            key.scope.launch(Dispatchers.Default) {
                key.followListsFlow().collectLatest {
                    invalidateFilters()
                }
            }

        return super.newSub(key)
    }

    override fun endSub(
        key: User,
        subId: String,
    ) {
        return super.endSub(key, subId)
        userJobMap[key]?.cancel()
    }
}
