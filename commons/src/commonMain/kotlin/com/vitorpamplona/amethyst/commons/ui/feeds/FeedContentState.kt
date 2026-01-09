/**
 * Copyright (c) 2025 Vitor Pamplona
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
package com.vitorpamplona.amethyst.commons.ui.feeds

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.commons.service.BasicBundledInsert
import com.vitorpamplona.amethyst.commons.service.BasicBundledUpdate
import com.vitorpamplona.amethyst.commons.threading.checkNotInMainThread
import com.vitorpamplona.amethyst.commons.utils.equalImmutableLists
import com.vitorpamplona.quartz.nip09Deletions.DeletionEvent
import com.vitorpamplona.quartz.utils.flattenToSet
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Stable
class FeedContentState(
    val localFilter: IFeedFilter<Note>,
    val viewModelScope: CoroutineScope,
    val cacheProvider: ICacheProvider,
) : InvalidatableContent {
    private val _feedContent = MutableStateFlow<FeedState>(FeedState.Loading)
    val feedContent = _feedContent.asStateFlow()

    // Simple counter that changes when it needs to invalidate everything
    private val _scrollToTop = MutableStateFlow<Int>(0)
    val scrollToTop = _scrollToTop.asStateFlow()
    var scrollToTopPending = false

    private var lastFeedKey: Any? = null

    override val isRefreshing: MutableState<Boolean> = mutableStateOf(false)

    val lastNoteCreatedAtWhenFullyLoaded = MutableStateFlow<Long?>(null)

    fun sendToTop() {
        if (scrollToTopPending) return

        scrollToTopPending = true
        viewModelScope.launch(Dispatchers.IO) { _scrollToTop.emit(_scrollToTop.value + 1) }
    }

    suspend fun sentToTop() {
        scrollToTopPending = false
    }

    private fun refresh() {
        viewModelScope.launch(Dispatchers.IO) { refreshSuspended() }
    }

    fun visibleNotes(): List<Note> {
        val currentState = _feedContent.value
        return if (currentState is FeedState.Loaded) {
            currentState.feed.value.list
        } else {
            emptyList()
        }
    }

    fun lastNoteCreatedAtIfFilled() = lastNoteCreatedAtWhenFullyLoaded.value

    fun refreshSuspended() {
        checkNotInMainThread()

        isRefreshing.value = true
        try {
            lastFeedKey = localFilter.feedKey()
            val notes = localFilter.loadTop().distinctBy { it.idHex }.toImmutableList()

            val oldNotesState = _feedContent.value
            if (oldNotesState is FeedState.Loaded) {
                if (!equalImmutableLists(notes, oldNotesState.feed.value.list)) {
                    updateFeed(notes)
                }
            } else {
                updateFeed(notes)
            }
        } finally {
            isRefreshing.value = false
        }
    }

    private fun updateFeed(notes: ImmutableList<Note>) {
        if (notes.size >= localFilter.limit()) {
            // feeds might not be sorted by created at, so full search
            val lastNoteTime = notes.minOfOrNull { it.createdAt() ?: Long.MAX_VALUE }
            if (lastNoteTime != lastNoteCreatedAtWhenFullyLoaded.value) {
                lastNoteCreatedAtWhenFullyLoaded.tryEmit(lastNoteTime)
            }
        } else {
            lastNoteCreatedAtWhenFullyLoaded.tryEmit(null)
        }

        val currentState = _feedContent.value
        if (notes.isEmpty()) {
            _feedContent.tryEmit(FeedState.Empty)
        } else if (currentState is FeedState.Loaded) {
            currentState.feed.tryEmit(LoadedFeedState(notes, localFilter.showHiddenKey()))
        } else {
            _feedContent.tryEmit(
                FeedState.Loaded(MutableStateFlow(LoadedFeedState(notes, localFilter.showHiddenKey()))),
            )
        }
    }

    fun deleteFromFeed(deletedNotes: Set<Note>) {
        val feed = _feedContent.value
        if (feed is FeedState.Loaded) {
            val notes = (feed.feed.value.list - deletedNotes).toImmutableList()
            updateFeed(notes)
        }
    }

    fun refreshFromOldState(newItems: Set<Note>) {
        val oldNotesState = _feedContent.value
        if (localFilter is AdditiveFeedFilter && lastFeedKey == localFilter.feedKey()) {
            if (oldNotesState is FeedState.Loaded) {
                val deletionEvents: List<DeletionEvent> = newItems.mapNotNull { it.event as? DeletionEvent }

                val oldList =
                    if (deletionEvents.isEmpty()) {
                        oldNotesState.feed.value.list
                    } else {
                        oldNotesState.feed.value.list
                            .filter {
                                val noteEvent = it.event
                                if (noteEvent != null) {
                                    !cacheProvider.hasBeenDeleted(noteEvent)
                                } else {
                                    false
                                }
                            }.toImmutableList()
                    }

                val newList =
                    localFilter
                        .updateListWith(oldList, newItems)
                        .distinctBy { it.idHex }
                        .toImmutableList()

                if (!equalImmutableLists(newList, oldNotesState.feed.value.list)) {
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

    private val bundler = BasicBundledUpdate(250, Dispatchers.IO, viewModelScope)
    private val bundlerInsert = BasicBundledInsert<Set<Note>>(250, Dispatchers.IO, viewModelScope)

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
            bundler.invalidate(false) {
                // adds the time to perform the refresh into this delay
                // holding off new updates in case of heavy refresh routines.
                refreshSuspended()
                sendToTop()
            }
        }
    }

    fun invalidateInsertData(newItems: Set<Note>) {
        bundlerInsert.invalidateList(newItems) {
            refreshFromOldState(it.flattenToSet())
        }
    }

    fun updateFeedWith(newNotes: Set<Note>) {
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
