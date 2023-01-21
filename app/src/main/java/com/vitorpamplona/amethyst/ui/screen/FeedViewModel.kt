package com.vitorpamplona.amethyst.ui.screen

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

class NostrChannelFeedViewModel: FeedViewModel(NostrChannelDataSource)
class NostrChatRoomFeedViewModel: FeedViewModel(NostrChatRoomDataSource)
class NostrHomeFeedViewModel: FeedViewModel(NostrHomeDataSource)
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
        viewModelScope.launch(Dispatchers.IO) {
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
    @Synchronized
    fun invalidateData() {
        if (handlerWaiting) return

        handlerWaiting = true
        val scope = CoroutineScope(Job() + Dispatchers.IO)
        scope.launch {
            delay(100)
            refresh()
            handlerWaiting = false
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