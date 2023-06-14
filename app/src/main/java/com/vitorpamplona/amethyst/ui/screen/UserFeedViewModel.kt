package com.vitorpamplona.amethyst.ui.screen

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import com.vitorpamplona.amethyst.ui.components.BundledUpdate
import com.vitorpamplona.amethyst.ui.dal.FeedFilter
import com.vitorpamplona.amethyst.ui.dal.HiddenAccountsFeedFilter
import com.vitorpamplona.amethyst.ui.dal.UserProfileFollowersFeedFilter
import com.vitorpamplona.amethyst.ui.dal.UserProfileFollowsFeedFilter
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NostrUserProfileFollowsUserFeedViewModel(val user: User, val account: Account) : UserFeedViewModel(UserProfileFollowsFeedFilter(user, account)) {
    class Factory(val user: User, val account: Account) : ViewModelProvider.Factory {
        override fun <NostrUserProfileFollowsUserFeedViewModel : ViewModel> create(modelClass: Class<NostrUserProfileFollowsUserFeedViewModel>): NostrUserProfileFollowsUserFeedViewModel {
            return NostrUserProfileFollowsUserFeedViewModel(user, account) as NostrUserProfileFollowsUserFeedViewModel
        }
    }
}

class NostrUserProfileFollowersUserFeedViewModel(val user: User, val account: Account) : UserFeedViewModel(UserProfileFollowersFeedFilter(user, account)) {
    class Factory(val user: User, val account: Account) : ViewModelProvider.Factory {
        override fun <NostrUserProfileFollowersUserFeedViewModel : ViewModel> create(modelClass: Class<NostrUserProfileFollowersUserFeedViewModel>): NostrUserProfileFollowersUserFeedViewModel {
            return NostrUserProfileFollowersUserFeedViewModel(user, account) as NostrUserProfileFollowersUserFeedViewModel
        }
    }
}

class NostrHiddenAccountsFeedViewModel(val account: Account) : UserFeedViewModel(HiddenAccountsFeedFilter(account)) {
    class Factory(val account: Account) : ViewModelProvider.Factory {
        override fun <NostrHiddenAccountsFeedViewModel : ViewModel> create(modelClass: Class<NostrHiddenAccountsFeedViewModel>): NostrHiddenAccountsFeedViewModel {
            return NostrHiddenAccountsFeedViewModel(account) as NostrHiddenAccountsFeedViewModel
        }
    }
}

@Stable
open class UserFeedViewModel(val dataSource: FeedFilter<User>) : ViewModel(), InvalidatableViewModel {
    private val _feedContent = MutableStateFlow<UserFeedState>(UserFeedState.Loading)
    val feedContent = _feedContent.asStateFlow()

    private fun refresh() {
        val scope = CoroutineScope(Job() + Dispatchers.Default)
        scope.launch {
            refreshSuspended()
        }
    }

    private fun refreshSuspended() {
        checkNotInMainThread()

        val notes = dataSource.loadTop().toImmutableList()

        val oldNotesState = _feedContent.value
        if (oldNotesState is UserFeedState.Loaded) {
            // Using size as a proxy for has changed.
            if (!equalImmutableLists(notes, oldNotesState.feed.value)) {
                updateFeed(notes)
            }
        } else {
            updateFeed(notes)
        }
    }

    private fun updateFeed(notes: ImmutableList<User>) {
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

    private val bundler = BundledUpdate(250, Dispatchers.IO)

    override fun invalidateData(ignoreIfDoing: Boolean) {
        bundler.invalidate(ignoreIfDoing) {
            // adds the time to perform the refresh into this delay
            // holding off new updates in case of heavy refresh routines.
            refreshSuspended()
        }
    }

    var collectorJob: Job? = null

    init {
        collectorJob = viewModelScope.launch(Dispatchers.IO) {
            checkNotInMainThread()

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

interface InvalidatableViewModel {
    fun invalidateData(ignoreIfDoing: Boolean = false)
}
