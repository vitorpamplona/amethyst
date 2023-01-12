package com.vitorpamplona.amethyst.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.LocalCacheState
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.NostrDataSource
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch


class FeedViewModel(val dataSource: NostrDataSource): ViewModel() {
    private val _feedContent = MutableStateFlow<FeedState>(FeedState.Loading)
    val feedContent = _feedContent.asStateFlow()

    fun refresh() {
        // For some reason, view Model Scope doesn't call
        viewModelScope.launch {
            refreshSuspend()
        }
    }

    fun refreshSuspend() {
        val notes = dataSource.loadTop()

        val oldNotesState = feedContent.value
        if (oldNotesState is FeedState.Loaded) {
            if (notes != oldNotesState.feed) {
                updateFeed(notes)
            }
        } else {
            updateFeed(notes)
        }
    }

    fun updateFeed(notes: List<Note>) {
        if (notes.isEmpty()) {
            _feedContent.update { FeedState.Empty }
        } else {
            _feedContent.update { FeedState.Loaded(notes) }
        }
    }

    fun refreshCurrentList() {
        val state = feedContent.value
        if (state is FeedState.Loaded) {
            _feedContent.update { FeedState.Loaded(state.feed) }
        }
    }

    private val cacheListener: (LocalCacheState) -> Unit = {
        refresh()
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