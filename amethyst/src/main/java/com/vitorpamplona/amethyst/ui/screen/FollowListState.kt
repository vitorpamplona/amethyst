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
package com.vitorpamplona.amethyst.ui.screen

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.AROUND_ME
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.GLOBAL_FOLLOWS
import com.vitorpamplona.amethyst.model.KIND3_FOLLOWS
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import com.vitorpamplona.amethyst.ui.navigation.Route
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.experimental.audio.header.AudioHeaderEvent
import com.vitorpamplona.quartz.experimental.audio.track.AudioTrackEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStoryPrologueEvent
import com.vitorpamplona.quartz.experimental.zapPolls.PollNoteEvent
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip09Deletions.DeletionEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.nip51Lists.MuteListEvent
import com.vitorpamplona.quartz.nip51Lists.PeopleListEvent
import com.vitorpamplona.quartz.nip51Lists.PinListEvent
import com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.nip54Wiki.WikiNoteEvent
import com.vitorpamplona.quartz.nip72ModCommunities.approval.CommunityPostApprovalEvent
import com.vitorpamplona.quartz.nip84Highlights.HighlightEvent
import com.vitorpamplona.quartz.nip99Classifieds.ClassifiedsEvent
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest

@Stable
class FollowListState(
    val account: Account,
    val viewModelScope: CoroutineScope,
) {
    val kind3Follow =
        PeopleListOutBoxFeedDefinition(
            code = KIND3_FOLLOWS,
            name = ResourceName(R.string.follow_list_kind3follows),
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
            relays = account.activeGlobalRelays().toList(),
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

    val defaultLists = persistentListOf(kind3Follow, aroundMe, globalFollow, muteListFollow)

    fun getPeopleLists(): List<FeedDefinition> =
        account
            .getAllPeopleLists()
            .map {
                PeopleListOutBoxFeedDefinition(
                    it.idHex,
                    PeopleListName(it),
                    CodeNameType.PEOPLE_LIST,
                    kinds = DEFAULT_FEED_KINDS,
                    listOf(it.idHex),
                )
            }.sortedBy { it.name.name() }

    val livePeopleListsFlow = MutableStateFlow(emptyList<FeedDefinition>())

    fun updateFeedWith(newNotes: Set<Note>) {
        checkNotInMainThread()

        val hasNewList =
            newNotes.any { it ->
                val noteEvent = it.event

                noteEvent?.pubKey == account.userProfile().pubkeyHex &&
                    (
                        (
                            noteEvent is PeopleListEvent ||
                                noteEvent is MuteListEvent ||
                                noteEvent is ContactListEvent
                        ) ||
                            (
                                noteEvent is DeletionEvent &&
                                    (
                                        noteEvent.deleteEventIds().any {
                                            LocalCache.getNoteIfExists(it)?.event is PeopleListEvent
                                        } ||
                                            noteEvent.deleteAddresses().any {
                                                it.kind == PeopleListEvent.KIND
                                            }
                                    )
                            )
                    )
            }

        if (hasNewList) {
            livePeopleListsFlow.tryEmit(getPeopleLists())
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val liveKind3FollowsFlow: Flow<List<FeedDefinition>> =
        account.liveKind3Follows.transformLatest {
            checkNotInMainThread()

            val communities =
                it.addresses.mapNotNull {
                    LocalCache.checkGetOrCreateAddressableNote(it)?.let { communityNote ->
                        TagFeedDefinition(
                            "Community/${communityNote.idHex}",
                            CommunityName(communityNote),
                            CodeNameType.ROUTE,
                            route = Route.Community(communityNote.idHex),
                            kinds = DEFAULT_COMMUNITY_FEEDS,
                            aTags = listOf(communityNote.idHex),
                            relays = account.activeGlobalRelays().toList(),
                        )
                    }
                }

            val hashtags =
                it.hashtags.map {
                    TagFeedDefinition(
                        "Hashtag/$it",
                        HashtagName(it),
                        CodeNameType.ROUTE,
                        route = Route.Hashtag(it),
                        kinds = DEFAULT_FEED_KINDS,
                        tTags = listOf(it),
                        relays = account.activeGlobalRelays().toList(),
                    )
                }

            val geotags =
                it.geotags.map {
                    TagFeedDefinition(
                        "Geohash/$it",
                        GeoHashName(it),
                        CodeNameType.ROUTE,
                        route = Route.Geohash(it),
                        kinds = DEFAULT_FEED_KINDS,
                        gTags = listOf(it),
                        relays = account.activeGlobalRelays().toList(),
                    )
                }

            emit(
                (communities + hashtags + geotags).sortedBy { it.name.name() },
            )
        }

    private val _kind3GlobalPeopleRoutes =
        combineTransform(
            livePeopleListsFlow,
            liveKind3FollowsFlow,
        ) { myLivePeopleListsFlow, myLiveKind3FollowsFlow ->
            checkNotInMainThread()
            emit(
                listOf(
                    listOf(kind3Follow, aroundMe, globalFollow),
                    myLivePeopleListsFlow,
                    myLiveKind3FollowsFlow,
                    listOf(muteListFollow),
                ).flatten().toImmutableList(),
            )
        }

    private val _kind3GlobalPeople =
        combineTransform(
            livePeopleListsFlow,
            liveKind3FollowsFlow,
        ) { myLivePeopleListsFlow, myLiveKind3FollowsFlow ->
            checkNotInMainThread()
            emit(
                listOf(
                    listOf(kind3Follow, aroundMe, globalFollow),
                    myLivePeopleListsFlow,
                    listOf(muteListFollow),
                ).flatten().toImmutableList(),
            )
        }

    val kind3GlobalPeopleRoutes = _kind3GlobalPeopleRoutes.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, defaultLists)
    val kind3GlobalPeople = _kind3GlobalPeople.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, defaultLists)

    suspend fun initializeSuspend() {
        checkNotInMainThread()

        livePeopleListsFlow.emit(getPeopleLists())
    }

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
    override fun name() = (note.event as? PeopleListEvent)?.nameOrTitle() ?: note.dTag() ?: ""
}

class CommunityName(
    val note: AddressableNote,
) : Name() {
    override fun name() = "/n/${(note.dTag() ?: "")}"
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
    val relays: List<String>,
) : FeedDefinition(code, name, type, null)

@Immutable
class TagFeedDefinition(
    code: String,
    name: Name,
    type: CodeNameType,
    route: Route?,
    val kinds: List<Int>,
    val relays: List<String>,
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
        HighlightEvent.KIND,
        AudioTrackEvent.KIND,
        AudioHeaderEvent.KIND,
        PinListEvent.KIND,
        LiveActivitiesChatMessageEvent.KIND,
        LiveActivitiesEvent.KIND,
        WikiNoteEvent.KIND,
        InteractiveStoryPrologueEvent.KIND,
    )

val DEFAULT_COMMUNITY_FEEDS =
    listOf(
        TextNoteEvent.KIND,
        LongTextNoteEvent.KIND,
        ClassifiedsEvent.KIND,
        HighlightEvent.KIND,
        AudioHeaderEvent.KIND,
        AudioTrackEvent.KIND,
        PinListEvent.KIND,
        WikiNoteEvent.KIND,
        CommunityPostApprovalEvent.KIND,
        InteractiveStoryPrologueEvent.KIND,
    )
