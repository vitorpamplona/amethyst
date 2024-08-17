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
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.GLOBAL_FOLLOWS
import com.vitorpamplona.amethyst.model.KIND3_FOLLOWS
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.events.ContactListEvent
import com.vitorpamplona.quartz.events.DeletionEvent
import com.vitorpamplona.quartz.events.MuteListEvent
import com.vitorpamplona.quartz.events.PeopleListEvent
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
        CodeName(
            KIND3_FOLLOWS,
            ResourceName(R.string.follow_list_kind3follows),
            CodeNameType.HARDCODED,
        )
    val globalFollow =
        CodeName(GLOBAL_FOLLOWS, ResourceName(R.string.follow_list_global), CodeNameType.HARDCODED)
    val muteListFollow =
        CodeName(
            MuteListEvent.blockListFor(account.userProfile().pubkeyHex),
            ResourceName(R.string.follow_list_mute_list),
            CodeNameType.HARDCODED,
        )
    val defaultLists = persistentListOf(kind3Follow, globalFollow, muteListFollow)

    fun getPeopleLists(): List<CodeName> =
        account
            .getAllPeopleLists()
            .map {
                CodeName(
                    it.idHex,
                    PeopleListName(it),
                    CodeNameType.PEOPLE_LIST,
                )
            }.sortedBy { it.name.name() }

    val livePeopleListsFlow = MutableStateFlow(emptyList<CodeName>())

    fun updateFeedWith(newNotes: Set<Note>) {
        checkNotInMainThread()

        val hasNewList =
            newNotes.any {
                val noteEvent = it.event

                noteEvent?.pubKey() == account.userProfile().pubkeyHex &&
                    (
                        (
                            noteEvent is PeopleListEvent ||
                                noteEvent is MuteListEvent ||
                                noteEvent is ContactListEvent
                        ) ||
                            (
                                noteEvent is DeletionEvent &&
                                    (
                                        noteEvent.deleteEvents().any {
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
    val liveKind3FollowsFlow: Flow<List<CodeName>> =
        account.liveKind3Follows.transformLatest {
            checkNotInMainThread()

            val communities =
                it.communities.mapNotNull {
                    LocalCache.checkGetOrCreateAddressableNote(it)?.let { communityNote ->
                        CodeName(
                            "Community/${communityNote.idHex}",
                            CommunityName(communityNote),
                            CodeNameType.ROUTE,
                        )
                    }
                }

            val hashtags =
                it.hashtags.map {
                    CodeName("Hashtag/$it", HashtagName(it), CodeNameType.ROUTE)
                }

            val geotags =
                it.geotags.map {
                    CodeName("Geohash/$it", GeoHashName(it), CodeNameType.ROUTE)
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
                    listOf(kind3Follow, globalFollow),
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
                    listOf(kind3Follow, globalFollow),
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
data class CodeName(
    val code: String,
    val name: Name,
    val type: CodeNameType,
)
