package com.vitorpamplona.amethyst.ui.screen

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.LocalCacheState
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.dal.FeedFilter
import com.vitorpamplona.amethyst.ui.dal.HiddenAccountsFeedFilter
import com.vitorpamplona.amethyst.ui.dal.UserProfileFollowersFeedFilter
import com.vitorpamplona.amethyst.ui.dal.UserProfileFollowsFeedFilter
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NostrUserProfileFollowsUserFeedViewModel: UserFeedViewModel(UserProfileFollowsFeedFilter)
class NostrUserProfileFollowersUserFeedViewModel: UserFeedViewModel(UserProfileFollowersFeedFilter)
class NostrHiddenAccountsFeedViewModel: UserFeedViewModel(HiddenAccountsFeedFilter)

open class UserFeedViewModel(val dataSource: FeedFilter<User>): ViewModel() {
    private val _feedContent = MutableStateFlow<UserFeedState>(UserFeedState.Loading)
    val feedContent = _feedContent.asStateFlow()

    fun refresh() {
        val scope = CoroutineScope(Job() + Dispatchers.Default)
        scope.launch {
            refreshSuspended()
        }
    }

    private fun refreshSuspended() {
        val notes = dataSource.loadTop()

        val oldNotesState = feedContent.value
        if (oldNotesState is UserFeedState.Loaded) {
            // Using size as a proxy for has changed.
            updateFeed(notes)
        } else {
            updateFeed(notes)
        }
    }

    private fun updateFeed(notes: List<User>) {
        val scope = CoroutineScope(Job() + Dispatchers.Main)
        scope.launch {
            val currentState = feedContent.value
            if (notes.isEmpty()) {
                _feedContent.update { UserFeedState.Empty }
            } else if (currentState is UserFeedState.Loaded) {
                // updates the current list
                currentState.feed.value = notes
            } else {
                _feedContent.update { UserFeedState.Loaded(mutableStateOf(notes)) }
            }
        }
    }

    var handlerWaiting = AtomicBoolean()

    @Synchronized
    private fun invalidateData() {
        if (handlerWaiting.getAndSet(true)) return

        handlerWaiting.set(true)
        val scope = CoroutineScope(Job() + Dispatchers.Default)
        scope.launch {
            try {
                delay(50)
                refresh()
            } finally {
                withContext(NonCancellable) {
                    handlerWaiting.set(false)
                }
            }
            handlerWaiting.set(false)
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
        super.onCleared()
    }
}