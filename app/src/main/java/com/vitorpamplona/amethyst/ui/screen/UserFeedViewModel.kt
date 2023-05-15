package com.vitorpamplona.amethyst.ui.screen

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.components.BundledUpdate
import com.vitorpamplona.amethyst.ui.dal.FeedFilter
import com.vitorpamplona.amethyst.ui.dal.HiddenAccountsFeedFilter
import com.vitorpamplona.amethyst.ui.dal.UserProfileFollowersFeedFilter
import com.vitorpamplona.amethyst.ui.dal.UserProfileFollowsFeedFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NostrUserProfileFollowsUserFeedViewModel : UserFeedViewModel(UserProfileFollowsFeedFilter)
class NostrUserProfileFollowersUserFeedViewModel : UserFeedViewModel(UserProfileFollowersFeedFilter)
class NostrHiddenAccountsFeedViewModel : UserFeedViewModel(HiddenAccountsFeedFilter)

open class UserFeedViewModel(val dataSource: FeedFilter<User>) : ViewModel() {
    private val _feedContent = MutableStateFlow<UserFeedState>(UserFeedState.Loading)
    val feedContent = _feedContent.asStateFlow()

    private fun refresh() {
        val scope = CoroutineScope(Job() + Dispatchers.Default)
        scope.launch {
            refreshSuspended()
        }
    }

    private fun refreshSuspended() {
        val notes = dataSource.loadTop()

        val oldNotesState = _feedContent.value
        if (oldNotesState is UserFeedState.Loaded) {
            // Using size as a proxy for has changed.
            if (notes != oldNotesState.feed.value) {
                updateFeed(notes)
            }
        } else {
            updateFeed(notes)
        }
    }

    private fun updateFeed(notes: List<User>) {
        val scope = CoroutineScope(Job() + Dispatchers.Main)
        scope.launch {
            val currentState = _feedContent.value
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

    private val bundler = BundledUpdate(250, Dispatchers.IO) {
        // adds the time to perform the refresh into this delay
        // holding off new updates in case of heavy refresh routines.
        refreshSuspended()
    }

    fun invalidateData() {
        bundler.invalidate()
    }

    var collectorJob: Job? = null

    init {
        collectorJob = viewModelScope.launch(Dispatchers.IO) {
            LocalCache.live.newEventBundles.collect { newNotes ->
                invalidateData()
            }
        }
    }

    override fun onCleared() {
        collectorJob?.cancel()
        super.onCleared()
    }
}
