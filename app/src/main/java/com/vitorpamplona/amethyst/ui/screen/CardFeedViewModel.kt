package com.vitorpamplona.amethyst.ui.screen

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.LocalCacheState
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.NostrDataSource
import com.vitorpamplona.amethyst.service.model.ReactionEvent
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

class CardFeedViewModel(val dataSource: NostrDataSource<Note>): ViewModel() {
    private val _feedContent = MutableStateFlow<CardFeedState>(CardFeedState.Loading)
    val feedContent = _feedContent.asStateFlow()

    private var lastNotes: List<Note>? = null

    fun refresh() {
        val scope = CoroutineScope(Job() + Dispatchers.Default)
        scope.launch {
            refreshSuspended()
        }
    }

    private fun refreshSuspended() {
        val notes = dataSource.loadTop()

        val lastNotesCopy = lastNotes

        val oldNotesState = feedContent.value
        if (lastNotesCopy != null && oldNotesState is CardFeedState.Loaded) {
            val newCards = convertToCard(notes.minus(lastNotesCopy))
            if (newCards.isNotEmpty()) {
                lastNotes = notes
                updateFeed((oldNotesState.feed.value + newCards).sortedBy { it.createdAt() }.reversed())
            }
        } else {
            val cards = convertToCard(notes)
            lastNotes = notes
            updateFeed(cards)
        }
    }

    private fun convertToCard(notes: List<Note>): List<Card> {
        val reactionsPerEvent = mutableMapOf<Note, MutableList<Note>>()
        notes
            .filter { it.event is ReactionEvent }
            .forEach {
                val reactedPost = it.replyTo?.last()
                if (reactedPost != null)
                    reactionsPerEvent.getOrPut(reactedPost, { mutableListOf() }).add(it)
            }

        val reactionCards = reactionsPerEvent.map { LikeSetCard(it.key, it.value) }

        val boostsPerEvent = mutableMapOf<Note, MutableList<Note>>()
        notes
            .filter { it.event is RepostEvent }
            .forEach {
                val boostedPost = it.replyTo?.last()
                if (boostedPost != null)
                    boostsPerEvent.getOrPut(boostedPost, { mutableListOf() }).add(it)
            }

        val boostCards = boostsPerEvent.map { BoostSetCard(it.key, it.value) }

        val textNoteCards = notes.filter { it.event !is ReactionEvent && it.event !is RepostEvent }.map { NoteCard(it) }

        return (reactionCards + boostCards + textNoteCards).sortedBy { it.createdAt() }.reversed()
    }

    fun updateFeed(notes: List<Card>) {
        val scope = CoroutineScope(Job() + Dispatchers.Main)
        scope.launch {
            val currentState = feedContent.value

            if (notes.isEmpty()) {
                _feedContent.update { CardFeedState.Empty }
            } else if (currentState is CardFeedState.Loaded) {
                // updates the current list
                currentState.feed.value = notes
            } else {
                _feedContent.update { CardFeedState.Loaded(mutableStateOf(notes)) }
            }
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