package com.vitorpamplona.amethyst.ui.screen

import android.util.Log
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.LocalCacheState
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.NostrChannelDataSource
import com.vitorpamplona.amethyst.service.NostrChatRoomDataSource
import com.vitorpamplona.amethyst.service.NostrChatroomListDataSource
import com.vitorpamplona.amethyst.service.NostrDataSource
import com.vitorpamplona.amethyst.service.NostrGlobalDataSource
import com.vitorpamplona.amethyst.service.NostrHomeDataSource
import com.vitorpamplona.amethyst.service.NostrThreadDataSource
import com.vitorpamplona.amethyst.service.NostrUserProfileDataSource
import com.vitorpamplona.amethyst.service.model.RepostEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nostr.postr.events.TextNoteEvent

class NostrChannelFeedViewModel: FeedViewModel(NostrChannelDataSource)
class NostrChatRoomFeedViewModel: FeedViewModel(NostrChatRoomDataSource)
class NostrGlobalFeedViewModel: FeedViewModel(NostrGlobalDataSource)
class NostrThreadFeedViewModel: FeedViewModel(NostrThreadDataSource)
class NostrUserProfileFeedViewModel: FeedViewModel(NostrUserProfileDataSource)

class NostrChatroomListKnownFeedViewModel: FeedViewModel(NostrChatroomListDataSource) {
    override fun newListFromDataSource(): List<Note> {
        // Filter: all channels + PMs the account has replied to
        return super.newListFromDataSource().filter {
            val me = NostrChatroomListDataSource.account.userProfile()
            it.channel != null || me.messages[it.author]?.firstOrNull { me == it.author } != null
        }
    }
}
class NostrChatroomListNewFeedViewModel: FeedViewModel(NostrChatroomListDataSource) {
    override fun newListFromDataSource(): List<Note> {
        // Filter: no channels + PMs the account has never replied to
        return super.newListFromDataSource().filter {
            val me = NostrChatroomListDataSource.account.userProfile()
            it.channel == null && me.messages[it.author]?.firstOrNull { me == it.author } == null
        }
    }
}

class NostrHomeFeedViewModel: FeedViewModel(NostrHomeDataSource) {
    override fun newListFromDataSource(): List<Note> {
        // Filter: no replies
        return dataSource.feed().filter {
            it.event is RepostEvent || it.replyTo == null || it.replyTo?.size == 0
        }.take(100)
    }
}

class NostrHomeRepliesFeedViewModel: FeedViewModel(NostrHomeDataSource) {}


abstract class FeedViewModel(val dataSource: NostrDataSource<Note>): ViewModel() {
    private val _feedContent = MutableStateFlow<FeedState>(FeedState.Loading)
    val feedContent = _feedContent.asStateFlow()

    open fun newListFromDataSource(): List<Note> {
        return dataSource.loadTop()
    }

    fun hardRefresh() {
        dataSource.resetFilters()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.Default) {
            val notes = newListFromDataSource()

            val oldNotesState = feedContent.value
            if (oldNotesState is FeedState.Loaded) {
                if (notes != oldNotesState.feed) {
                    withContext(Dispatchers.Main) {
                        updateFeed(notes)
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    updateFeed(notes)
                }
            }
        }
    }

    fun updateFeed(notes: List<Note>) {
        if (notes.isEmpty()) {
            _feedContent.update { FeedState.Empty }
        } else {
            _feedContent.update { FeedState.Loaded(notes) }
        }
    }

    var handlerWaiting = false
    fun invalidateData() {
        synchronized(handlerWaiting) {
            if (handlerWaiting) return

            handlerWaiting = true
            val scope = CoroutineScope(Job() + Dispatchers.Default)
            scope.launch {
                delay(100)
                refresh()
                handlerWaiting = false
            }
        }
    }

    private val cacheListener: (LocalCacheState) -> Unit = {
        invalidateData()
    }

    init {
        LocalCache.live.observeForever(cacheListener)
    }

    override fun onCleared() {
        LocalCache.live.removeObserver(cacheListener)

        dataSource.stop()
        viewModelScope.cancel()
        super.onCleared()
    }
}