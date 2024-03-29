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

import android.util.Log
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.Channel
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import com.vitorpamplona.amethyst.ui.components.BundledInsert
import com.vitorpamplona.amethyst.ui.components.BundledUpdate
import com.vitorpamplona.amethyst.ui.dal.AdditiveFeedFilter
import com.vitorpamplona.amethyst.ui.dal.BookmarkPrivateFeedFilter
import com.vitorpamplona.amethyst.ui.dal.BookmarkPublicFeedFilter
import com.vitorpamplona.amethyst.ui.dal.ChannelFeedFilter
import com.vitorpamplona.amethyst.ui.dal.ChatroomFeedFilter
import com.vitorpamplona.amethyst.ui.dal.ChatroomListKnownFeedFilter
import com.vitorpamplona.amethyst.ui.dal.ChatroomListNewFeedFilter
import com.vitorpamplona.amethyst.ui.dal.CommunityFeedFilter
import com.vitorpamplona.amethyst.ui.dal.DiscoverChatFeedFilter
import com.vitorpamplona.amethyst.ui.dal.DiscoverCommunityFeedFilter
import com.vitorpamplona.amethyst.ui.dal.DiscoverLiveFeedFilter
import com.vitorpamplona.amethyst.ui.dal.DiscoverMarketplaceFeedFilter
import com.vitorpamplona.amethyst.ui.dal.DraftEventsFeedFilter
import com.vitorpamplona.amethyst.ui.dal.FeedFilter
import com.vitorpamplona.amethyst.ui.dal.GeoHashFeedFilter
import com.vitorpamplona.amethyst.ui.dal.HashtagFeedFilter
import com.vitorpamplona.amethyst.ui.dal.HomeConversationsFeedFilter
import com.vitorpamplona.amethyst.ui.dal.HomeNewThreadFeedFilter
import com.vitorpamplona.amethyst.ui.dal.ThreadFeedFilter
import com.vitorpamplona.amethyst.ui.dal.UserProfileAppRecommendationsFeedFilter
import com.vitorpamplona.amethyst.ui.dal.UserProfileBookmarksFeedFilter
import com.vitorpamplona.amethyst.ui.dal.UserProfileConversationsFeedFilter
import com.vitorpamplona.amethyst.ui.dal.UserProfileNewThreadFeedFilter
import com.vitorpamplona.amethyst.ui.dal.UserProfileReportsFeedFilter
import com.vitorpamplona.amethyst.ui.dal.VideoFeedFilter
import com.vitorpamplona.quartz.events.ChatroomKey
import com.vitorpamplona.quartz.events.DeletionEvent
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NostrChannelFeedViewModel(val channel: Channel, val account: Account) :
    FeedViewModel(ChannelFeedFilter(channel, account)) {
    class Factory(val channel: Channel, val account: Account) : ViewModelProvider.Factory {
        override fun <NostrChannelFeedViewModel : ViewModel> create(modelClass: Class<NostrChannelFeedViewModel>): NostrChannelFeedViewModel {
            return NostrChannelFeedViewModel(channel, account) as NostrChannelFeedViewModel
        }
    }
}

class NostrChatroomFeedViewModel(val user: ChatroomKey, val account: Account) :
    FeedViewModel(ChatroomFeedFilter(user, account)) {
    class Factory(val user: ChatroomKey, val account: Account) : ViewModelProvider.Factory {
        override fun <NostrChatRoomFeedViewModel : ViewModel> create(modelClass: Class<NostrChatRoomFeedViewModel>): NostrChatRoomFeedViewModel {
            return NostrChatroomFeedViewModel(user, account) as NostrChatRoomFeedViewModel
        }
    }
}

@Stable
class NostrVideoFeedViewModel(val account: Account) : FeedViewModel(VideoFeedFilter(account)) {
    class Factory(val account: Account) : ViewModelProvider.Factory {
        override fun <NostrVideoFeedViewModel : ViewModel> create(modelClass: Class<NostrVideoFeedViewModel>): NostrVideoFeedViewModel {
            return NostrVideoFeedViewModel(account) as NostrVideoFeedViewModel
        }
    }
}

class NostrDiscoverMarketplaceFeedViewModel(val account: Account) :
    FeedViewModel(
        DiscoverMarketplaceFeedFilter(account),
    ) {
    class Factory(val account: Account) : ViewModelProvider.Factory {
        override fun <NostrDiscoverMarketplaceFeedViewModel : ViewModel> create(modelClass: Class<NostrDiscoverMarketplaceFeedViewModel>): NostrDiscoverMarketplaceFeedViewModel {
            return NostrDiscoverMarketplaceFeedViewModel(account) as NostrDiscoverMarketplaceFeedViewModel
        }
    }
}

class NostrDiscoverLiveFeedViewModel(val account: Account) :
    FeedViewModel(DiscoverLiveFeedFilter(account)) {
    class Factory(val account: Account) : ViewModelProvider.Factory {
        override fun <NostrDiscoverLiveFeedViewModel : ViewModel> create(modelClass: Class<NostrDiscoverLiveFeedViewModel>): NostrDiscoverLiveFeedViewModel {
            return NostrDiscoverLiveFeedViewModel(account) as NostrDiscoverLiveFeedViewModel
        }
    }
}

class NostrDiscoverCommunityFeedViewModel(val account: Account) :
    FeedViewModel(DiscoverCommunityFeedFilter(account)) {
    class Factory(val account: Account) : ViewModelProvider.Factory {
        override fun <NostrDiscoverCommunityFeedViewModel : ViewModel> create(modelClass: Class<NostrDiscoverCommunityFeedViewModel>): NostrDiscoverCommunityFeedViewModel {
            return NostrDiscoverCommunityFeedViewModel(account) as NostrDiscoverCommunityFeedViewModel
        }
    }
}

class NostrDiscoverChatFeedViewModel(val account: Account) :
    FeedViewModel(DiscoverChatFeedFilter(account)) {
    class Factory(val account: Account) : ViewModelProvider.Factory {
        override fun <NostrDiscoverChatFeedViewModel : ViewModel> create(modelClass: Class<NostrDiscoverChatFeedViewModel>): NostrDiscoverChatFeedViewModel {
            return NostrDiscoverChatFeedViewModel(account) as NostrDiscoverChatFeedViewModel
        }
    }
}

class NostrThreadFeedViewModel(account: Account, noteId: String) :
    FeedViewModel(ThreadFeedFilter(account, noteId)) {
    class Factory(val account: Account, val noteId: String) : ViewModelProvider.Factory {
        override fun <NostrThreadFeedViewModel : ViewModel> create(modelClass: Class<NostrThreadFeedViewModel>): NostrThreadFeedViewModel {
            return NostrThreadFeedViewModel(account, noteId) as NostrThreadFeedViewModel
        }
    }
}

class NostrUserProfileNewThreadsFeedViewModel(val user: User, val account: Account) :
    FeedViewModel(UserProfileNewThreadFeedFilter(user, account)) {
    class Factory(val user: User, val account: Account) : ViewModelProvider.Factory {
        override fun <NostrUserProfileNewThreadsFeedViewModel : ViewModel> create(modelClass: Class<NostrUserProfileNewThreadsFeedViewModel>): NostrUserProfileNewThreadsFeedViewModel {
            return NostrUserProfileNewThreadsFeedViewModel(user, account)
                as NostrUserProfileNewThreadsFeedViewModel
        }
    }
}

class NostrUserProfileConversationsFeedViewModel(val user: User, val account: Account) :
    FeedViewModel(UserProfileConversationsFeedFilter(user, account)) {
    class Factory(val user: User, val account: Account) : ViewModelProvider.Factory {
        override fun <NostrUserProfileConversationsFeedViewModel : ViewModel> create(modelClass: Class<NostrUserProfileConversationsFeedViewModel>): NostrUserProfileConversationsFeedViewModel {
            return NostrUserProfileConversationsFeedViewModel(user, account)
                as NostrUserProfileConversationsFeedViewModel
        }
    }
}

class NostrHashtagFeedViewModel(val hashtag: String, val account: Account) :
    FeedViewModel(HashtagFeedFilter(hashtag, account)) {
    class Factory(val hashtag: String, val account: Account) : ViewModelProvider.Factory {
        override fun <NostrHashtagFeedViewModel : ViewModel> create(modelClass: Class<NostrHashtagFeedViewModel>): NostrHashtagFeedViewModel {
            return NostrHashtagFeedViewModel(hashtag, account) as NostrHashtagFeedViewModel
        }
    }
}

class NostrGeoHashFeedViewModel(val geohash: String, val account: Account) :
    FeedViewModel(GeoHashFeedFilter(geohash, account)) {
    class Factory(val geohash: String, val account: Account) : ViewModelProvider.Factory {
        override fun <NostrGeoHashFeedViewModel : ViewModel> create(modelClass: Class<NostrGeoHashFeedViewModel>): NostrGeoHashFeedViewModel {
            return NostrGeoHashFeedViewModel(geohash, account) as NostrGeoHashFeedViewModel
        }
    }
}

class NostrCommunityFeedViewModel(val note: AddressableNote, val account: Account) :
    FeedViewModel(CommunityFeedFilter(note, account)) {
    class Factory(val note: AddressableNote, val account: Account) : ViewModelProvider.Factory {
        override fun <NostrCommunityFeedViewModel : ViewModel> create(modelClass: Class<NostrCommunityFeedViewModel>): NostrCommunityFeedViewModel {
            return NostrCommunityFeedViewModel(note, account) as NostrCommunityFeedViewModel
        }
    }
}

class NostrUserProfileReportFeedViewModel(val user: User) :
    FeedViewModel(UserProfileReportsFeedFilter(user)) {
    class Factory(val user: User) : ViewModelProvider.Factory {
        override fun <NostrUserProfileReportFeedViewModel : ViewModel> create(modelClass: Class<NostrUserProfileReportFeedViewModel>): NostrUserProfileReportFeedViewModel {
            return NostrUserProfileReportFeedViewModel(user) as NostrUserProfileReportFeedViewModel
        }
    }
}

class NostrUserProfileBookmarksFeedViewModel(val user: User, val account: Account) :
    FeedViewModel(UserProfileBookmarksFeedFilter(user, account)) {
    class Factory(val user: User, val account: Account) : ViewModelProvider.Factory {
        override fun <NostrUserProfileBookmarksFeedViewModel : ViewModel> create(modelClass: Class<NostrUserProfileBookmarksFeedViewModel>): NostrUserProfileBookmarksFeedViewModel {
            return NostrUserProfileBookmarksFeedViewModel(user, account)
                as NostrUserProfileBookmarksFeedViewModel
        }
    }
}

class NostrChatroomListKnownFeedViewModel(val account: Account) :
    FeedViewModel(ChatroomListKnownFeedFilter(account)) {
    class Factory(val account: Account) : ViewModelProvider.Factory {
        override fun <NostrChatroomListKnownFeedViewModel : ViewModel> create(modelClass: Class<NostrChatroomListKnownFeedViewModel>): NostrChatroomListKnownFeedViewModel {
            return NostrChatroomListKnownFeedViewModel(account) as NostrChatroomListKnownFeedViewModel
        }
    }
}

class NostrChatroomListNewFeedViewModel(val account: Account) :
    FeedViewModel(ChatroomListNewFeedFilter(account)) {
    class Factory(val account: Account) : ViewModelProvider.Factory {
        override fun <NostrChatroomListNewFeedViewModel : ViewModel> create(modelClass: Class<NostrChatroomListNewFeedViewModel>): NostrChatroomListNewFeedViewModel {
            return NostrChatroomListNewFeedViewModel(account) as NostrChatroomListNewFeedViewModel
        }
    }
}

@Stable
class NostrHomeFeedViewModel(val account: Account) :
    FeedViewModel(HomeNewThreadFeedFilter(account)) {
    class Factory(val account: Account) : ViewModelProvider.Factory {
        override fun <NostrHomeFeedViewModel : ViewModel> create(modelClass: Class<NostrHomeFeedViewModel>): NostrHomeFeedViewModel {
            return NostrHomeFeedViewModel(account) as NostrHomeFeedViewModel
        }
    }
}

@Stable
class NostrHomeRepliesFeedViewModel(val account: Account) :
    FeedViewModel(HomeConversationsFeedFilter(account)) {
    class Factory(val account: Account) : ViewModelProvider.Factory {
        override fun <NostrHomeRepliesFeedViewModel : ViewModel> create(modelClass: Class<NostrHomeRepliesFeedViewModel>): NostrHomeRepliesFeedViewModel {
            return NostrHomeRepliesFeedViewModel(account) as NostrHomeRepliesFeedViewModel
        }
    }
}

@Stable
class NostrBookmarkPublicFeedViewModel(val account: Account) :
    FeedViewModel(BookmarkPublicFeedFilter(account)) {
    class Factory(val account: Account) : ViewModelProvider.Factory {
        override fun <NostrBookmarkPublicFeedViewModel : ViewModel> create(modelClass: Class<NostrBookmarkPublicFeedViewModel>): NostrBookmarkPublicFeedViewModel {
            return NostrBookmarkPublicFeedViewModel(account) as NostrBookmarkPublicFeedViewModel
        }
    }
}

@Stable
class NostrBookmarkPrivateFeedViewModel(val account: Account) :
    FeedViewModel(BookmarkPrivateFeedFilter(account)) {
    class Factory(val account: Account) : ViewModelProvider.Factory {
        override fun <NostrBookmarkPrivateFeedViewModel : ViewModel> create(modelClass: Class<NostrBookmarkPrivateFeedViewModel>): NostrBookmarkPrivateFeedViewModel {
            return NostrBookmarkPrivateFeedViewModel(account) as NostrBookmarkPrivateFeedViewModel
        }
    }
}

@Stable
class NostrDraftEventsFeedViewModel(val account: Account) :
    FeedViewModel(DraftEventsFeedFilter(account)) {
    class Factory(val account: Account) : ViewModelProvider.Factory {
        override fun <NostrDraftEventsFeedViewModel : ViewModel> create(modelClass: Class<NostrDraftEventsFeedViewModel>): NostrDraftEventsFeedViewModel {
            return NostrDraftEventsFeedViewModel(account) as NostrDraftEventsFeedViewModel
        }
    }
}

class NostrUserAppRecommendationsFeedViewModel(val user: User) :
    FeedViewModel(UserProfileAppRecommendationsFeedFilter(user)) {
    class Factory(val user: User) : ViewModelProvider.Factory {
        override fun <NostrUserAppRecommendationsFeedViewModel : ViewModel> create(modelClass: Class<NostrUserAppRecommendationsFeedViewModel>): NostrUserAppRecommendationsFeedViewModel {
            return NostrUserAppRecommendationsFeedViewModel(user)
                as NostrUserAppRecommendationsFeedViewModel
        }
    }
}

@Stable
abstract class FeedViewModel(val localFilter: FeedFilter<Note>) :
    ViewModel(), InvalidatableViewModel {
    private val _feedContent = MutableStateFlow<FeedState>(FeedState.Loading)
    val feedContent = _feedContent.asStateFlow()

    // Simple counter that changes when it needs to invalidate everything
    private val _scrollToTop = MutableStateFlow<Int>(0)
    val scrollToTop = _scrollToTop.asStateFlow()
    var scrolltoTopPending = false

    private var lastFeedKey: String? = null

    fun sendToTop() {
        if (scrolltoTopPending) return

        scrolltoTopPending = true
        viewModelScope.launch(Dispatchers.IO) { _scrollToTop.emit(_scrollToTop.value + 1) }
    }

    suspend fun sentToTop() {
        scrolltoTopPending = false
    }

    private fun refresh() {
        viewModelScope.launch(Dispatchers.Default) { refreshSuspended() }
    }

    fun refreshSuspended() {
        checkNotInMainThread()

        lastFeedKey = localFilter.feedKey()
        val notes = localFilter.loadTop().distinctBy { it.idHex }.toImmutableList()

        val oldNotesState = _feedContent.value
        if (oldNotesState is FeedState.Loaded) {
            if (!equalImmutableLists(notes, oldNotesState.feed.value)) {
                updateFeed(notes)
            }
        } else {
            updateFeed(notes)
        }
    }

    private fun updateFeed(notes: ImmutableList<Note>) {
        viewModelScope.launch(Dispatchers.Main) {
            val currentState = _feedContent.value
            if (notes.isEmpty()) {
                _feedContent.update { FeedState.Empty }
            } else if (currentState is FeedState.Loaded) {
                // updates the current list
                if (currentState.showHidden.value != localFilter.showHiddenKey()) {
                    currentState.showHidden.value = localFilter.showHiddenKey()
                }
                currentState.feed.value = notes
            } else {
                _feedContent.update {
                    FeedState.Loaded(mutableStateOf(notes), mutableStateOf(localFilter.showHiddenKey()))
                }
            }
        }
    }

    fun refreshFromOldState(newItems: Set<Note>) {
        val oldNotesState = _feedContent.value
        if (localFilter is AdditiveFeedFilter && lastFeedKey == localFilter.feedKey()) {
            if (oldNotesState is FeedState.Loaded) {
                val deletionEvents: List<DeletionEvent> =
                    newItems.mapNotNull {
                        val noteEvent = it.event
                        if (noteEvent is DeletionEvent) noteEvent else null
                    }

                val oldList =
                    if (deletionEvents.isEmpty()) {
                        oldNotesState.feed.value
                    } else {
                        val deletedEventIds = deletionEvents.flatMapTo(HashSet()) { it.deleteEvents() }
                        val deletedEventAddresses = deletionEvents.flatMapTo(HashSet()) { it.deleteAddresses() }
                        oldNotesState.feed.value.filter { !it.wasOrShouldBeDeletedBy(deletedEventIds, deletedEventAddresses) }.toImmutableList()
                    }

                val newList =
                    localFilter
                        .updateListWith(oldList, newItems)
                        .distinctBy { it.idHex }
                        .toImmutableList()
                if (!equalImmutableLists(newList, oldNotesState.feed.value)) {
                    updateFeed(newList)
                }
            } else if (oldNotesState is FeedState.Empty) {
                val newList =
                    localFilter
                        .updateListWith(emptyList(), newItems)
                        .distinctBy { it.idHex }
                        .toImmutableList()
                if (newList.isNotEmpty()) {
                    updateFeed(newList)
                }
            } else {
                // Refresh Everything
                refreshSuspended()
            }
        } else {
            // Refresh Everything
            refreshSuspended()
        }
    }

    private val bundler = BundledUpdate(250, Dispatchers.IO)
    private val bundlerInsert = BundledInsert<Set<Note>>(250, Dispatchers.IO)

    override fun invalidateData(ignoreIfDoing: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            bundler.invalidate(ignoreIfDoing) {
                // adds the time to perform the refresh into this delay
                // holding off new updates in case of heavy refresh routines.
                refreshSuspended()
            }
        }
    }

    fun checkKeysInvalidateDataAndSendToTop() {
        if (lastFeedKey != localFilter.feedKey()) {
            viewModelScope.launch(Dispatchers.IO) {
                bundler.invalidate(false) {
                    // adds the time to perform the refresh into this delay
                    // holding off new updates in case of heavy refresh routines.
                    refreshSuspended()
                    sendToTop()
                }
            }
        }
    }

    fun invalidateInsertData(newItems: Set<Note>) {
        bundlerInsert.invalidateList(newItems) { refreshFromOldState(it.flatten().toSet()) }
    }

    private var collectorJob: Job? = null

    init {
        Log.d("Init", "Starting new Model: ${this.javaClass.simpleName}")
        collectorJob =
            viewModelScope.launch(Dispatchers.IO) {
                LocalCache.live.newEventBundles.collect { newNotes ->
                    checkNotInMainThread()

                    if (
                        localFilter is AdditiveFeedFilter &&
                        (_feedContent.value is FeedState.Loaded || _feedContent.value is FeedState.Empty)
                    ) {
                        invalidateInsertData(newNotes)
                    } else {
                        // Refresh Everything
                        invalidateData()
                    }
                }
            }
    }

    override fun onCleared() {
        Log.d("Init", "OnCleared: ${this.javaClass.simpleName}")
        bundlerInsert.cancel()
        bundler.cancel()
        collectorJob?.cancel()
        super.onCleared()
    }
}
