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
import com.vitorpamplona.amethyst.model.ALL_FOLLOWS
import com.vitorpamplona.amethyst.model.ALL_USER_FOLLOWS
import com.vitorpamplona.amethyst.model.AROUND_ME
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.CHESS
import com.vitorpamplona.amethyst.model.GLOBAL_FOLLOWS
import com.vitorpamplona.amethyst.model.KIND3_FOLLOWS
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.experimental.audio.header.AudioHeaderEvent
import com.vitorpamplona.quartz.experimental.audio.track.AudioTrackEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStoryPrologueEvent
import com.vitorpamplona.quartz.experimental.nipsOnNostr.NipTextEvent
import com.vitorpamplona.quartz.experimental.zapPolls.PollNoteEvent
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.nip51Lists.PinListEvent
import com.vitorpamplona.quartz.nip51Lists.followList.FollowListEvent
import com.vitorpamplona.quartz.nip51Lists.muteList.MuteListEvent
import com.vitorpamplona.quartz.nip51Lists.peopleList.PeopleListEvent
import com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.nip54Wiki.WikiNoteEvent
import com.vitorpamplona.quartz.nip64Chess.ChessGameEvent
import com.vitorpamplona.quartz.nip64Chess.LiveChessGameChallengeEvent
import com.vitorpamplona.quartz.nip64Chess.LiveChessGameEndEvent
import com.vitorpamplona.quartz.nip72ModCommunities.approval.CommunityPostApprovalEvent
import com.vitorpamplona.quartz.nip84Highlights.HighlightEvent
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.nip99Classifieds.ClassifiedsEvent
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

@Stable
class TopNavFilterState(
    val account: Account,
    val scope: CoroutineScope,
) {
    val allFollows =
        PeopleListOutBoxFeedDefinition(
            code = ALL_FOLLOWS,
            name = ResourceName(R.string.follow_list_kind3follows),
            type = CodeNameType.HARDCODED,
            kinds = DEFAULT_FEED_KINDS,
            unpackList = listOf(ContactListEvent.blockListFor(account.signer.pubKey)),
        )

    val userFollows =
        PeopleListOutBoxFeedDefinition(
            code = ALL_USER_FOLLOWS,
            name = ResourceName(R.string.follow_list_kind3follows_users_only),
            type = CodeNameType.HARDCODED,
            kinds = DEFAULT_FEED_KINDS,
            unpackList = listOf(ContactListEvent.blockListFor(account.signer.pubKey)),
        )

    val kind3Follows =
        PeopleListOutBoxFeedDefinition(
            code = KIND3_FOLLOWS,
            name = ResourceName(R.string.follow_list_kind3_follows_users_only),
            type = CodeNameType.HARDCODED,
            kinds = DEFAULT_FEED_KINDS,
            unpackList = listOf(ContactListEvent.blockListFor(account.signer.pubKey)),
        )

    val globalFollow =
        GlobalFeedDefinition(
            code = GLOBAL_FOLLOWS,
            name = ResourceName(R.string.follow_list_global),
            type = CodeNameType.HARDCODED,
            kinds = DEFAULT_FEED_KINDS,
        )

    val aroundMe =
        AroundMeFeedDefinition(
            code = AROUND_ME,
            name = ResourceName(R.string.follow_list_aroundme),
            type = CodeNameType.HARDCODED,
            kinds = DEFAULT_FEED_KINDS,
        )

    val muteListFollow =
        PeopleListOutBoxFeedDefinition(
            code = MuteListEvent.blockListFor(account.userProfile().pubkeyHex),
            name = ResourceName(R.string.follow_list_mute_list),
            type = CodeNameType.HARDCODED,
            kinds = DEFAULT_FEED_KINDS,
            unpackList = listOf(MuteListEvent.blockListFor(account.userProfile().pubkeyHex)),
        )

    val chessFollow =
        GlobalFeedDefinition(
            code = CHESS,
            name = ResourceName(R.string.follow_list_chess),
            type = CodeNameType.HARDCODED,
            kinds =
                listOf(
                    ChessGameEvent.KIND, // Completed games (Kind 64)
                    LiveChessGameChallengeEvent.KIND, // Challenges (Kind 30064)
                    LiveChessGameEndEvent.KIND, // Game endings (Kind 30067)
                    // Note: LiveChessMoveEvent (Kind 30066) intentionally excluded - too noisy
                ),
        )

    val defaultLists = persistentListOf(allFollows, userFollows, kind3Follows, aroundMe, globalFollow, muteListFollow)

    fun mergePeopleLists(
        peopleLists: List<AddressableNote>,
        followLists: List<AddressableNote>,
    ): List<FeedDefinition> {
        val peopleListsDefs =
            peopleLists.map {
                PeopleListOutBoxFeedDefinition(
                    it.idHex,
                    PeopleListName(it),
                    CodeNameType.PEOPLE_LIST,
                    kinds = DEFAULT_FEED_KINDS,
                    listOf(it.idHex),
                )
            }

        val followListsDefs =
            followLists.map {
                PeopleListOutBoxFeedDefinition(
                    it.idHex,
                    PeopleListName(it),
                    CodeNameType.PEOPLE_LIST,
                    kinds = DEFAULT_FEED_KINDS,
                    listOf(it.idHex),
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
    ): List<FeedDefinition> {
        val hashtags =
            hashtagList.map {
                TagFeedDefinition(
                    "Hashtag/$it",
                    HashtagName(it),
                    CodeNameType.ROUTE,
                    route = Route.Hashtag(it),
                    kinds = DEFAULT_FEED_KINDS,
                    tTags = listOf(it),
                )
            }

        val geotags =
            geotagList.map {
                TagFeedDefinition(
                    "Geohash/$it",
                    GeoHashName(it),
                    CodeNameType.ROUTE,
                    route = Route.Geohash(it),
                    kinds = DEFAULT_FEED_KINDS,
                    gTags = listOf(it),
                )
            }

        val communities =
            communityList.map { communityNote ->
                TagFeedDefinition(
                    "Community/${communityNote.idHex}",
                    CommunityName(communityNote),
                    CodeNameType.ROUTE,
                    route = Route.Community(communityNote.address.kind, communityNote.address.pubKeyHex, communityNote.address.dTag),
                    kinds = DEFAULT_COMMUNITY_FEEDS,
                    aTags = listOf(communityNote.idHex),
                )
            }

        return (communities + hashtags + geotags).sortedBy { it.name.name() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val liveInterestFlows: Flow<List<FeedDefinition>> =
        combine(
            account.hashtagList.flow,
            account.geohashList.flow,
            account.communityList.flowNotes,
            ::mergeInterests,
        ).onStart {
            emit(
                mergeInterests(
                    account.hashtagList.flow.value,
                    account.geohashList.flow.value,
                    account.communityList.flowNotes.value,
                ),
            )
        }

    private val _kind3GlobalPeopleRoutes =
        combineTransform(
            livePeopleListsFlow,
            liveInterestFlows,
        ) { myLivePeopleListsFlow, myLiveKind3FollowsFlow ->
            checkNotInMainThread()
            emit(
                listOf(
                    listOf(allFollows, userFollows, kind3Follows, aroundMe, globalFollow),
                    myLivePeopleListsFlow,
                    myLiveKind3FollowsFlow,
                    listOf(muteListFollow),
                ).flatten().toImmutableList(),
            )
        }

    private val _kind3GlobalPeople =
        combineTransform(
            livePeopleListsFlow,
            liveInterestFlows,
        ) { myLivePeopleListsFlow, myLiveKind3FollowsFlow ->
            checkNotInMainThread()
            emit(
                listOf(
                    listOf(allFollows, userFollows, kind3Follows, aroundMe, globalFollow),
                    myLivePeopleListsFlow,
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

    fun destroy() {
        Log.d("Init", "OnCleared: ${this.javaClass.simpleName}")
    }
}

enum class CodeNameType {
    HARDCODED,
    PEOPLE_LIST,
    ROUTE,
}

abstract class Name {
    abstract fun name(): String

    open fun name(context: Context) = name()
}

class GeoHashName(
    val geoHashTag: String,
) : Name() {
    override fun name() = "/g/$geoHashTag"
}

class HashtagName(
    val hashTag: String,
) : Name() {
    override fun name() = "#$hashTag"
}

class ResourceName(
    val resourceId: Int,
) : Name() {
    override fun name() = " $resourceId " // Space to make sure it goes first

    override fun name(context: Context) = stringRes(context, resourceId)
}

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

class CommunityName(
    val note: AddressableNote,
) : Name() {
    override fun name() = "/n/${(note.dTag())}"
}

@Immutable
abstract class FeedDefinition(
    val code: String,
    val name: Name,
    val type: CodeNameType,
    val route: Route?,
)

@Immutable
class GlobalFeedDefinition(
    code: String,
    name: Name,
    type: CodeNameType,
    val kinds: List<Int>,
) : FeedDefinition(code, name, type, null)

@Immutable
class TagFeedDefinition(
    code: String,
    name: Name,
    type: CodeNameType,
    route: Route?,
    val kinds: List<Int>,
    val pTags: List<String>? = null,
    val eTags: List<String>? = null,
    val aTags: List<String>? = null,
    val tTags: List<String>? = null,
    val gTags: List<String>? = null,
) : FeedDefinition(code, name, type, route)

@Immutable
class AroundMeFeedDefinition(
    code: String,
    name: Name,
    type: CodeNameType,
    val kinds: List<Int>,
) : FeedDefinition(code, name, type, null)

@Immutable
class PeopleListOutBoxFeedDefinition(
    code: String,
    name: Name,
    type: CodeNameType,
    val kinds: List<Int>,
    val unpackList: List<String>,
) : FeedDefinition(code, name, type, null)

val DEFAULT_FEED_KINDS =
    listOf(
        TextNoteEvent.KIND,
        RepostEvent.KIND,
        GenericRepostEvent.KIND,
        ClassifiedsEvent.KIND,
        LongTextNoteEvent.KIND,
        PollNoteEvent.KIND,
        PollEvent.KIND,
        HighlightEvent.KIND,
        AudioTrackEvent.KIND,
        AudioHeaderEvent.KIND,
        PinListEvent.KIND,
        LiveActivitiesChatMessageEvent.KIND,
        LiveActivitiesEvent.KIND,
        WikiNoteEvent.KIND,
        NipTextEvent.KIND,
        InteractiveStoryPrologueEvent.KIND,
    )

val DEFAULT_COMMUNITY_FEEDS =
    listOf(
        TextNoteEvent.KIND,
        CommentEvent.KIND,
        LongTextNoteEvent.KIND,
        ClassifiedsEvent.KIND,
        HighlightEvent.KIND,
        AudioHeaderEvent.KIND,
        AudioTrackEvent.KIND,
        PollEvent.KIND,
        PinListEvent.KIND,
        WikiNoteEvent.KIND,
        NipTextEvent.KIND,
        CommunityPostApprovalEvent.KIND,
        InteractiveStoryPrologueEvent.KIND,
    )
