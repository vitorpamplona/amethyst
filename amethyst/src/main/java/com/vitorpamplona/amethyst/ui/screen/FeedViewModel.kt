/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.amethyst.ui.screen

import android.util.Log
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.dal.FeedFilter
import com.vitorpamplona.amethyst.ui.feeds.FeedContentState
import com.vitorpamplona.amethyst.ui.feeds.InvalidatableContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Stable
abstract class FeedViewModel(
    localFilter: FeedFilter<Note>,
) : ViewModel(),
    InvalidatableContent {
    val feedState = FeedContentState(localFilter, viewModelScope)

    override val isRefreshing = feedState.isRefreshing

    fun sendToTop() = feedState.sendToTop()

    suspend fun sentToTop() = feedState.sentToTop()

    override fun invalidateData(ignoreIfDoing: Boolean) = feedState.invalidateData(ignoreIfDoing)

    private var collectorJob: Job? = null

    init {
        Log.d("Init", "Starting new Model: ${this.javaClass.simpleName}")
        collectorJob =
            viewModelScope.launch(Dispatchers.IO) {
                LocalCache.live.newEventBundles.collect { newNotes ->
                    feedState.updateFeedWith(newNotes)
                }
            }
    }

    override fun onCleared() {
        Log.d("Init", "OnCleared: ${this.javaClass.simpleName}")
        collectorJob?.cancel()
        super.onCleared()
    }
}
