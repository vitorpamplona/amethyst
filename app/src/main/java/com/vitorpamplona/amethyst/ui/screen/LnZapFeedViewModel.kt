package com.vitorpamplona.amethyst.ui.screen

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import com.vitorpamplona.amethyst.ui.components.BundledUpdate
import com.vitorpamplona.amethyst.ui.dal.FeedFilter
import com.vitorpamplona.amethyst.ui.dal.UserProfileZapsFeedFilter
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NostrUserProfileZapsFeedViewModel(user: User) : LnZapFeedViewModel(UserProfileZapsFeedFilter(user)) {
    class Factory(val user: User) : ViewModelProvider.Factory {
        override fun <NostrUserProfileZapsFeedViewModel : ViewModel> create(modelClass: Class<NostrUserProfileZapsFeedViewModel>): NostrUserProfileZapsFeedViewModel {
            return NostrUserProfileZapsFeedViewModel(user) as NostrUserProfileZapsFeedViewModel
        }
    }
}

@Stable
open class LnZapFeedViewModel(val dataSource: FeedFilter<ZapReqResponse>) : ViewModel() {
    private val _feedContent = MutableStateFlow<LnZapFeedState>(LnZapFeedState.Loading)
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
        if (oldNotesState is LnZapFeedState.Loaded) {
            // Using size as a proxy for has changed.
            if (!equalImmutableLists(notes, oldNotesState.feed.value)) {
                updateFeed(notes)
            }
        } else {
            updateFeed(notes)
        }
    }

    private fun updateFeed(notes: ImmutableList<ZapReqResponse>) {
        val scope = CoroutineScope(Job() + Dispatchers.Main)
        scope.launch {
            val currentState = _feedContent.value
            if (notes.isEmpty()) {
                _feedContent.update { LnZapFeedState.Empty }
            } else if (currentState is LnZapFeedState.Loaded) {
                // updates the current list
                currentState.feed.value = notes
            } else {
                _feedContent.update { LnZapFeedState.Loaded(mutableStateOf(notes)) }
            }
        }
    }

    private val bundler = BundledUpdate(250, Dispatchers.IO)

    fun invalidateData() {
        bundler.invalidate() {
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
