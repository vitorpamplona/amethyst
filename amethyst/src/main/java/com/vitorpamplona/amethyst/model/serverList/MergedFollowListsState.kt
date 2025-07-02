/**
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
package com.vitorpamplona.amethyst.model.serverList

import com.vitorpamplona.amethyst.model.nip02FollowLists.FollowListState
import com.vitorpamplona.amethyst.model.nip51Lists.GeohashListState
import com.vitorpamplona.amethyst.model.nip51Lists.HashtagListState
import com.vitorpamplona.amethyst.model.nip72Communities.CommunityListState
import com.vitorpamplona.quartz.nip01Core.hints.types.AddressHint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

class MergedFollowListsState(
    val kind3List: FollowListState,
    val hashtagList: HashtagListState,
    val geohashList: GeohashListState,
    val communityList: CommunityListState,
    val scope: CoroutineScope,
) {
    fun mergeLists(
        kind3: FollowListState.Kind3Follows,
        hashtages: Set<String>,
        geohashes: Set<String>,
        community: Set<AddressHint>,
    ): FollowListState.Kind3Follows =
        FollowListState.Kind3Follows(
            kind3.authors,
            kind3.authorsPlusMe,
            kind3.hashtags + hashtages,
            kind3.geotags + geohashes,
            kind3.communities + community.map { it.addressId },
        )

    val flow: StateFlow<FollowListState.Kind3Follows> =
        combine(
            kind3List.flow,
            hashtagList.flow,
            geohashList.flow,
            communityList.flow,
        ) { kind3, hashtag, geohash, community ->
            mergeLists(kind3, hashtag, geohash, community)
        }.onStart {
            emit(
                mergeLists(
                    kind3List.flow.value,
                    hashtagList.flow.value,
                    geohashList.flow.value,
                    communityList.flow.value,
                ),
            )
        }.flowOn(Dispatchers.Default)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                kind3List.flow.value,
            )
}
