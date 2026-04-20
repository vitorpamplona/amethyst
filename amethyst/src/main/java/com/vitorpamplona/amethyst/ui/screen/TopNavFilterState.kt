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
package com.vitorpamplona.amethyst.ui.screen

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.TopFilter
import com.vitorpamplona.amethyst.model.nip51Lists.interestSets.InterestSet
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import com.vitorpamplona.quartz.nip51Lists.followList.FollowListEvent
import com.vitorpamplona.quartz.nip51Lists.interestSet.InterestSetEvent
import com.vitorpamplona.quartz.nip51Lists.peopleList.PeopleListEvent
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppDefinitionEvent
import com.vitorpamplona.quartz.utils.Log
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform

@Stable
class TopNavFilterState(
    val account: Account,
    val scope: CoroutineScope,
) {
    val allFollows =
        FeedDefinition(
            code = TopFilter.AllFollows,
            name = ResourceName(R.string.follow_list_kind3follows),
        )

    val userFollows =
        FeedDefinition(
            code = TopFilter.AllUserFollows,
            name = ResourceName(R.string.follow_list_kind3follows_users_only),
        )

    val kind3Follows =
        FeedDefinition(
            code = TopFilter.DefaultFollows,
            name = ResourceName(R.string.follow_list_kind3_follows_users_only),
        )

    val globalFollow =
        FeedDefinition(
            code = TopFilter.Global,
            name = ResourceName(R.string.follow_list_global),
        )

    val aroundMe =
        FeedDefinition(
            code = TopFilter.AroundMe,
            name = ResourceName(R.string.follow_list_aroundme),
        )

    val muteListFollow =
        FeedDefinition(
            code = TopFilter.MuteList(account.muteList.getMuteListAddress()),
            name = ResourceName(R.string.follow_list_mute_list),
        )

    val chessFollow =
        FeedDefinition(
            code = TopFilter.Chess,
            name = ResourceName(R.string.follow_list_chess),
        )

    val mineFollow =
        FeedDefinition(
            code = TopFilter.Mine,
            name = ResourceName(R.string.follow_list_mine),
        )

    val allFavoriteAlgoFeedsFollow =
        FeedDefinition(
            code = TopFilter.AllFavoriteAlgoFeeds,
            name = ResourceName(R.string.follow_list_all_favorite_dvms),
        )

    val defaultLists = persistentListOf(allFollows, userFollows, kind3Follows, aroundMe, globalFollow, muteListFollow)

    fun mergePeopleLists(
        peopleLists: List<AddressableNote>,
        followLists: List<AddressableNote>,
    ): List<FeedDefinition> {
        val peopleListsDefs =
            peopleLists.map {
                FeedDefinition(
                    TopFilter.PeopleList(it.address),
                    PeopleListName(it),
                )
            }

        val followListsDefs =
            followLists.map {
                FeedDefinition(
                    TopFilter.PeopleList(it.address),
                    PeopleListName(it),
                )
            }

        return (peopleListsDefs + followListsDefs).sortedBy { it.name.name() }
    }

    val livePeopleListsFlow: Flow<List<FeedDefinition>> =
        combine(
            account.peopleLists.peopleListNotes,
            account.followLists.followListNotes,
            ::mergePeopleLists,
        ).onStart {
            emit(
                mergePeopleLists(
                    account.peopleLists.peopleListNotes.value,
                    account.followLists.followListNotes.value,
                ),
            )
        }

    fun mergeInterests(
        hashtagList: Set<String>,
        geotagList: Set<String>,
        communityList: List<AddressableNote>,
        relayList: Set<NormalizedRelayUrl>,
        favoriteAlgoFeedsList: List<AddressableNote>,
        interestSetList: List<InterestSet>,
    ): List<FeedDefinition> {
        val hashtags =
            hashtagList.map {
                FeedDefinition(
                    TopFilter.Hashtag(it),
                    HashtagName(it),
                )
            }

        val geotags =
            geotagList.map {
                FeedDefinition(
                    TopFilter.Geohash(it),
                    GeoHashName(it),
                )
            }

        val communities =
            communityList.map { communityNote ->
                FeedDefinition(
                    TopFilter.Community(communityNote.address),
                    CommunityName(communityNote),
                )
            }

        val relays =
            relayList.map { relayUrl ->
                FeedDefinition(
                    TopFilter.Relay(relayUrl.url),
                    RelayName(relayUrl),
                )
            }

        // Favorites can only be added through FavoriteAlgoFeedToggle, which itself checks
        // that the AppDefinitionEvent advertises kind 5300. Don't re-check here: on
        // cold start the AppDefinitionEvent may not be in cache yet, and dropping
        // the entry means the persisted TopFilter.FavoriteAlgoFeed can't find its chip
        // in the spinner (user sees "Select an option" while the banner fires the
        // RPC — the bug we had before this change).
        val favoriteAlgoFeeds =
            favoriteAlgoFeedsList.map { feedNote ->
                FeedDefinition(
                    TopFilter.FavoriteAlgoFeed(feedNote.address),
                    FavoriteAlgoFeedName(feedNote),
                )
            }

        // Only show the "All favorite algo feeds" meta-chip when there is at least one
        // real favorite to merge; otherwise the chip opens to an empty feed.
        val allFavorites =
            if (favoriteAlgoFeeds.isNotEmpty()) listOf(allFavoriteAlgoFeedsFollow) else emptyList()

        val interestSets =
            interestSetList.map { set ->
                FeedDefinition(
                    TopFilter.InterestSet(InterestSetEvent.createAddress(account.signer.pubKey, set.identifier)),
                    InterestSetName(set),
                )
            }

        return (communities + hashtags + geotags + relays + allFavorites + favoriteAlgoFeeds + interestSets).sortedBy { it.name.name() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val liveInterestFlows: Flow<List<FeedDefinition>> =
        combine(
            account.hashtagList.flow,
            account.geohashList.flow,
            account.communityList.flowNotes,
            account.relayFeedsList.flow,
            combine(
                account.favoriteAlgoFeedsList.flowNotes,
                account.interestSets.listFeedFlow,
                ::Pair,
            ),
        ) { hashtagList, geotagList, communityList, relayList, favAndInterest ->
            mergeInterests(hashtagList, geotagList, communityList, relayList, favAndInterest.first, favAndInterest.second)
        }.onStart {
            emit(
                mergeInterests(
                    account.hashtagList.flow.value,
                    account.geohashList.flow.value,
                    account.communityList.flowNotes.value,
                    account.relayFeedsList.flow.value,
                    account.favoriteAlgoFeedsList.flowNotes.value,
                    account.interestSets.listFeedFlow.value,
                ),
            )
        }

    private val _kind3GlobalPeopleRoutes =
        combineTransform(
            livePeopleListsFlow,
            liveInterestFlows,
        ) { peopleLists, interests ->
            checkNotInMainThread()
            emit(
                listOf(
                    listOf(allFollows, userFollows, kind3Follows, aroundMe, globalFollow),
                    peopleLists,
                    interests,
                    listOf(muteListFollow),
                ).flatten().toImmutableList(),
            )
        }

    private val _badgeRoutes =
        livePeopleListsFlow.transform { peopleLists ->
            checkNotInMainThread()
            emit(
                listOf(
                    listOf(allFollows, userFollows, kind3Follows, globalFollow, mineFollow),
                    peopleLists,
                    listOf(muteListFollow),
                ).flatten().toImmutableList(),
            )
        }

    private val _kind3GlobalPeople =
        livePeopleListsFlow.transform { peopleLists ->
            checkNotInMainThread()
            emit(
                listOf(
                    listOf(allFollows, userFollows, kind3Follows, aroundMe, globalFollow),
                    peopleLists,
                    listOf(muteListFollow),
                ).flatten().toImmutableList(),
            )
        }

    val kind3GlobalPeopleRoutes =
        _kind3GlobalPeopleRoutes
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, defaultLists)

    val kind3GlobalPeople =
        _kind3GlobalPeople
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, defaultLists)

    val badgeRoutes =
        _badgeRoutes
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, persistentListOf(allFollows, userFollows, kind3Follows, globalFollow, mineFollow, muteListFollow))

    fun destroy() {
        Log.d("Init") { "OnCleared: ${this.javaClass.simpleName}" }
    }
}

@Stable
sealed class Name {
    abstract fun name(): String

    open fun name(context: Context) = name()
}

@Stable
class GeoHashName(
    val geoHashTag: String,
) : Name() {
    override fun name() = "/g/$geoHashTag"
}

@Stable
class HashtagName(
    val hashTag: String,
) : Name() {
    override fun name() = "#$hashTag"
}

@Stable
class RelayName(
    val url: NormalizedRelayUrl,
) : Name() {
    override fun name() = url.displayUrl()
}

@Stable
class ResourceName(
    val resourceId: Int,
) : Name() {
    override fun name() = " $resourceId " // Space to make sure it goes first

    override fun name(context: Context) = stringRes(context, resourceId)
}

@Stable
class PeopleListName(
    val note: AddressableNote,
) : Name() {
    override fun name(): String {
        val noteEvent = note.event
        return if (noteEvent is PeopleListEvent) {
            noteEvent.titleOrName() ?: note.dTag()
        } else if (noteEvent is FollowListEvent) {
            noteEvent.title() ?: note.dTag()
        } else {
            note.dTag()
        }
    }
}

@Stable
class CommunityName(
    val note: AddressableNote,
) : Name() {
    override fun name() = "/n/${(note.dTag())}"
}

@Stable
class FavoriteAlgoFeedName(
    val note: AddressableNote,
) : Name() {
    override fun name(): String =
        (note.event as? AppDefinitionEvent)?.appMetaData()?.name?.takeIf { it.isNotBlank() }
            ?: note.dTag()
}

@Stable
class InterestSetName(
    val set: InterestSet,
) : Name() {
    override fun name() = "⁂ ${set.title}"
}

@Immutable
class FeedDefinition(
    val code: TopFilter,
    val name: Name,
    val route: Route? = null,
)
