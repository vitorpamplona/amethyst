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
package com.vitorpamplona.amethyst.ui.feeds

import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.emphChat.EphemeralChatChannel
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import com.vitorpamplona.amethyst.ui.dal.AdditiveComplexFeedFilter
import com.vitorpamplona.amethyst.ui.screen.loggedIn.notifications.equalImmutableLists
import com.vitorpamplona.ammolite.relays.BundledInsert
import com.vitorpamplona.ammolite.relays.BundledUpdate
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Stable
class ChannelFeedContentState(
    val localFilter: AdditiveComplexFeedFilter<EphemeralChatChannel, Note>,
    val viewModelScope: CoroutineScope,
) : InvalidatableContent {
    private val _feedContent = MutableStateFlow<ChannelFeedState>(ChannelFeedState.Loading)
    val feedContent = _feedContent.asStateFlow()

    // Simple counter that changes when it needs to invalidate everything
    private val _scrollToTop = MutableStateFlow<Int>(0)
    val scrollToTop = _scrollToTop.asStateFlow()
    var scrolltoTopPending = false

    private var lastFeedKey: Any? = null

    override val isRefreshing: MutableState<Boolean> = mutableStateOf(false)

    fun sendToTop() {
        if (scrolltoTopPending) return

        scrolltoTopPending = true
        viewModelScope.launch(Dispatchers.IO) { _scrollToTop.emit(_scrollToTop.value + 1) }
    }

    suspend fun sentToTop() {
        scrolltoTopPending = false
    }

    private fun refresh() {
        viewModelScope.launch(Dispatchers.Default) { refreshSuspended() }
    }

    fun refreshSuspended() {
        checkNotInMainThread()

        isRefreshing.value = true
        try {
            lastFeedKey = localFilter.feedKey()
            val notes = localFilter.loadTop().distinctBy { it }.toImmutableList()

            val oldNotesState = _feedContent.value
            if (oldNotesState is ChannelFeedState.Loaded) {
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

    private fun updateFeed(notes: ImmutableList<EphemeralChatChannel>) {
        val currentState = _feedContent.value
        if (notes.isEmpty()) {
            _feedContent.tryEmit(ChannelFeedState.Empty)
        } else if (currentState is ChannelFeedState.Loaded) {
            currentState.feed.tryEmit(LoadedFeedState<EphemeralChatChannel>(notes, localFilter.showHiddenKey()))
        } else {
            _feedContent.tryEmit(
                ChannelFeedState.Loaded(MutableStateFlow(LoadedFeedState<EphemeralChatChannel>(notes, localFilter.showHiddenKey()))),
            )
        }
    }

    fun refreshFromOldState(newItems: Set<Note>) {
        val oldNotesState = _feedContent.value
        if (oldNotesState is ChannelFeedState.Loaded) {
            val oldList = oldNotesState.feed.value.list

            val newList =
                localFilter
                    .updateListWith(oldList, newItems)
                    .distinctBy { it }
                    .toImmutableList()
            if (!equalImmutableLists(newList, oldNotesState.feed.value.list)) {
                updateFeed(newList)
            }
        } else if (oldNotesState is ChannelFeedState.Empty) {
            val newList =
                localFilter
                    .updateListWith(emptyList(), newItems)
                    .distinctBy { it }
                    .toImmutableList()
            if (newList.isNotEmpty()) {
                updateFeed(newList)
            }
        } else {
            // Refresh Everything
            refreshSuspended()
        }
    }

    private val bundler = BundledUpdate(250, Dispatchers.Default)
    private val bundlerInsert = BundledInsert<Set<Note>>(250, Dispatchers.Default)

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
        bundlerInsert.invalidateList(newItems) { refreshFromOldState(it.flatten().toSet()) }
    }

    fun updateFeedWith(newNotes: Set<Note>) {
        if ((_feedContent.value is ChannelFeedState.Loaded || _feedContent.value is ChannelFeedState.Empty)) {
            invalidateInsertData(newNotes)
        } else {
            // Refresh Everything
            invalidateData()
        }
    }

    fun destroy() {
        Log.d("Init", "OnCleared: ${this.javaClass.simpleName}")
        bundlerInsert.cancel()
        bundler.cancel()
    }
}
