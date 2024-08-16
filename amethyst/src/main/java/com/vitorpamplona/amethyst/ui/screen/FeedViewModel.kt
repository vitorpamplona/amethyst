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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.Channel
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.dal.BookmarkPrivateFeedFilter
import com.vitorpamplona.amethyst.ui.dal.BookmarkPublicFeedFilter
import com.vitorpamplona.amethyst.ui.dal.ChannelFeedFilter
import com.vitorpamplona.amethyst.ui.dal.ChatroomFeedFilter
import com.vitorpamplona.amethyst.ui.dal.CommunityFeedFilter
import com.vitorpamplona.amethyst.ui.dal.DraftEventsFeedFilter
import com.vitorpamplona.amethyst.ui.dal.FeedFilter
import com.vitorpamplona.amethyst.ui.dal.GeoHashFeedFilter
import com.vitorpamplona.amethyst.ui.dal.HashtagFeedFilter
import com.vitorpamplona.amethyst.ui.dal.NIP90ContentDiscoveryResponseFilter
import com.vitorpamplona.amethyst.ui.dal.ThreadFeedFilter
import com.vitorpamplona.amethyst.ui.dal.UserProfileAppRecommendationsFeedFilter
import com.vitorpamplona.amethyst.ui.dal.UserProfileBookmarksFeedFilter
import com.vitorpamplona.amethyst.ui.dal.UserProfileConversationsFeedFilter
import com.vitorpamplona.amethyst.ui.dal.UserProfileGalleryFeedFilter
import com.vitorpamplona.amethyst.ui.dal.UserProfileNewThreadFeedFilter
import com.vitorpamplona.amethyst.ui.dal.UserProfileReportsFeedFilter
import com.vitorpamplona.amethyst.ui.feeds.FeedContentState
import com.vitorpamplona.amethyst.ui.feeds.InvalidatableContent
import com.vitorpamplona.quartz.events.ChatroomKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class NostrChannelFeedViewModel(
    val channel: Channel,
    val account: Account,
) : FeedViewModel(ChannelFeedFilter(channel, account)) {
    class Factory(
        val channel: Channel,
        val account: Account,
    ) : ViewModelProvider.Factory {
        override fun <NostrChannelFeedViewModel : ViewModel> create(modelClass: Class<NostrChannelFeedViewModel>): NostrChannelFeedViewModel = NostrChannelFeedViewModel(channel, account) as NostrChannelFeedViewModel
    }
}

class NostrChatroomFeedViewModel(
    val user: ChatroomKey,
    val account: Account,
) : FeedViewModel(ChatroomFeedFilter(user, account)) {
    class Factory(
        val user: ChatroomKey,
        val account: Account,
    ) : ViewModelProvider.Factory {
        override fun <NostrChatRoomFeedViewModel : ViewModel> create(modelClass: Class<NostrChatRoomFeedViewModel>): NostrChatRoomFeedViewModel = NostrChatroomFeedViewModel(user, account) as NostrChatRoomFeedViewModel
    }
}

class NostrThreadFeedViewModel(
    account: Account,
    noteId: String,
) : FeedViewModel(ThreadFeedFilter(account, noteId)) {
    class Factory(
        val account: Account,
        val noteId: String,
    ) : ViewModelProvider.Factory {
        override fun <NostrThreadFeedViewModel : ViewModel> create(modelClass: Class<NostrThreadFeedViewModel>): NostrThreadFeedViewModel = NostrThreadFeedViewModel(account, noteId) as NostrThreadFeedViewModel
    }
}

class NostrUserProfileNewThreadsFeedViewModel(
    val user: User,
    val account: Account,
) : FeedViewModel(UserProfileNewThreadFeedFilter(user, account)) {
    class Factory(
        val user: User,
        val account: Account,
    ) : ViewModelProvider.Factory {
        override fun <NostrUserProfileNewThreadsFeedViewModel : ViewModel> create(modelClass: Class<NostrUserProfileNewThreadsFeedViewModel>): NostrUserProfileNewThreadsFeedViewModel =
            NostrUserProfileNewThreadsFeedViewModel(user, account)
                as NostrUserProfileNewThreadsFeedViewModel
    }
}

class NostrUserProfileConversationsFeedViewModel(
    val user: User,
    val account: Account,
) : FeedViewModel(UserProfileConversationsFeedFilter(user, account)) {
    class Factory(
        val user: User,
        val account: Account,
    ) : ViewModelProvider.Factory {
        override fun <NostrUserProfileConversationsFeedViewModel : ViewModel> create(modelClass: Class<NostrUserProfileConversationsFeedViewModel>): NostrUserProfileConversationsFeedViewModel =
            NostrUserProfileConversationsFeedViewModel(user, account)
                as NostrUserProfileConversationsFeedViewModel
    }
}

class NostrHashtagFeedViewModel(
    val hashtag: String,
    val account: Account,
) : FeedViewModel(HashtagFeedFilter(hashtag, account)) {
    class Factory(
        val hashtag: String,
        val account: Account,
    ) : ViewModelProvider.Factory {
        override fun <NostrHashtagFeedViewModel : ViewModel> create(modelClass: Class<NostrHashtagFeedViewModel>): NostrHashtagFeedViewModel = NostrHashtagFeedViewModel(hashtag, account) as NostrHashtagFeedViewModel
    }
}

class NostrGeoHashFeedViewModel(
    val geohash: String,
    val account: Account,
) : FeedViewModel(GeoHashFeedFilter(geohash, account)) {
    class Factory(
        val geohash: String,
        val account: Account,
    ) : ViewModelProvider.Factory {
        override fun <NostrGeoHashFeedViewModel : ViewModel> create(modelClass: Class<NostrGeoHashFeedViewModel>): NostrGeoHashFeedViewModel = NostrGeoHashFeedViewModel(geohash, account) as NostrGeoHashFeedViewModel
    }
}

class NostrCommunityFeedViewModel(
    val note: AddressableNote,
    val account: Account,
) : FeedViewModel(CommunityFeedFilter(note, account)) {
    class Factory(
        val note: AddressableNote,
        val account: Account,
    ) : ViewModelProvider.Factory {
        override fun <NostrCommunityFeedViewModel : ViewModel> create(modelClass: Class<NostrCommunityFeedViewModel>): NostrCommunityFeedViewModel = NostrCommunityFeedViewModel(note, account) as NostrCommunityFeedViewModel
    }
}

class NostrUserProfileReportFeedViewModel(
    val user: User,
) : FeedViewModel(UserProfileReportsFeedFilter(user)) {
    class Factory(
        val user: User,
    ) : ViewModelProvider.Factory {
        override fun <NostrUserProfileReportFeedViewModel : ViewModel> create(modelClass: Class<NostrUserProfileReportFeedViewModel>): NostrUserProfileReportFeedViewModel = NostrUserProfileReportFeedViewModel(user) as NostrUserProfileReportFeedViewModel
    }
}

class NostrUserProfileGalleryFeedViewModel(
    val user: User,
    val account: Account,
) : FeedViewModel(UserProfileGalleryFeedFilter(user, account)) {
    class Factory(
        val user: User,
        val account: Account,
    ) : ViewModelProvider.Factory {
        override fun <NostrUserProfileGalleryFeedViewModel : ViewModel> create(modelClass: Class<NostrUserProfileGalleryFeedViewModel>): NostrUserProfileGalleryFeedViewModel =
            NostrUserProfileGalleryFeedViewModel(user, account)
                as NostrUserProfileGalleryFeedViewModel
    }
}

class NostrUserProfileBookmarksFeedViewModel(
    val user: User,
    val account: Account,
) : FeedViewModel(UserProfileBookmarksFeedFilter(user, account)) {
    class Factory(
        val user: User,
        val account: Account,
    ) : ViewModelProvider.Factory {
        override fun <NostrUserProfileBookmarksFeedViewModel : ViewModel> create(modelClass: Class<NostrUserProfileBookmarksFeedViewModel>): NostrUserProfileBookmarksFeedViewModel =
            NostrUserProfileBookmarksFeedViewModel(user, account)
                as NostrUserProfileBookmarksFeedViewModel
    }
}

@Stable
class NostrBookmarkPublicFeedViewModel(
    val account: Account,
) : FeedViewModel(BookmarkPublicFeedFilter(account)) {
    class Factory(
        val account: Account,
    ) : ViewModelProvider.Factory {
        override fun <NostrBookmarkPublicFeedViewModel : ViewModel> create(modelClass: Class<NostrBookmarkPublicFeedViewModel>): NostrBookmarkPublicFeedViewModel = NostrBookmarkPublicFeedViewModel(account) as NostrBookmarkPublicFeedViewModel
    }
}

@Stable
class NostrBookmarkPrivateFeedViewModel(
    val account: Account,
) : FeedViewModel(BookmarkPrivateFeedFilter(account)) {
    class Factory(
        val account: Account,
    ) : ViewModelProvider.Factory {
        override fun <NostrBookmarkPrivateFeedViewModel : ViewModel> create(modelClass: Class<NostrBookmarkPrivateFeedViewModel>): NostrBookmarkPrivateFeedViewModel = NostrBookmarkPrivateFeedViewModel(account) as NostrBookmarkPrivateFeedViewModel
    }
}

@Stable
class NostrNIP90ContentDiscoveryFeedViewModel(
    val account: Account,
    val dvmkey: String,
    val requestid: String,
) : FeedViewModel(NIP90ContentDiscoveryResponseFilter(account, dvmkey, requestid)) {
    class Factory(
        val account: Account,
        val dvmkey: String,
        val requestid: String,
    ) : ViewModelProvider.Factory {
        override fun <NostrNIP90ContentDiscoveryFeedViewModel : ViewModel> create(modelClass: Class<NostrNIP90ContentDiscoveryFeedViewModel>): NostrNIP90ContentDiscoveryFeedViewModel = NostrNIP90ContentDiscoveryFeedViewModel(account, dvmkey, requestid) as NostrNIP90ContentDiscoveryFeedViewModel
    }
}

@Stable
class NostrDraftEventsFeedViewModel(
    val account: Account,
) : FeedViewModel(DraftEventsFeedFilter(account)) {
    class Factory(
        val account: Account,
    ) : ViewModelProvider.Factory {
        override fun <NostrDraftEventsFeedViewModel : ViewModel> create(modelClass: Class<NostrDraftEventsFeedViewModel>): NostrDraftEventsFeedViewModel = NostrDraftEventsFeedViewModel(account) as NostrDraftEventsFeedViewModel
    }
}

class NostrUserAppRecommendationsFeedViewModel(
    val user: User,
) : FeedViewModel(UserProfileAppRecommendationsFeedFilter(user)) {
    class Factory(
        val user: User,
    ) : ViewModelProvider.Factory {
        override fun <NostrUserAppRecommendationsFeedViewModel : ViewModel> create(modelClass: Class<NostrUserAppRecommendationsFeedViewModel>): NostrUserAppRecommendationsFeedViewModel =
            NostrUserAppRecommendationsFeedViewModel(user)
                as NostrUserAppRecommendationsFeedViewModel
    }
}

@Stable
abstract class FeedViewModel(
    localFilter: FeedFilter<Note>,
) : ViewModel(),
    InvalidatableContent {
    val feedState = FeedContentState(localFilter, viewModelScope)

    fun sendToTop() = feedState.sendToTop()

    suspend fun sentToTop() = feedState.sentToTop()

    override fun invalidateData(ignoreIfDoing: Boolean) = feedState.invalidateData(ignoreIfDoing)

    private var collectorJob: Job? = null

    init {
        Log.d("Init", "Starting new Model: ${this.javaClass.simpleName}")
        collectorJob =
            viewModelScope.launch(Dispatchers.IO) {
                LocalCache.live.newEventBundles.collect { newNotes ->
                    feedState.updateFeedWith(newNotes)
                }
            }
    }

    override fun onCleared() {
        Log.d("Init", "OnCleared: ${this.javaClass.simpleName}")
        collectorJob?.cancel()
        super.onCleared()
    }
}
