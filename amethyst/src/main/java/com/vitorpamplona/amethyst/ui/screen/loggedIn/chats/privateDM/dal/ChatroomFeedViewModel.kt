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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM.dal

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedContentState
import com.vitorpamplona.amethyst.commons.ui.feeds.InvalidatableContent
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.ListChange
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.dal.ChangesFlowFilter
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ChatroomFeedViewModel(
    val user: ChatroomKey,
    val account: Account,
) : ListChangeFeedViewModel(ChatroomFeedFilter(user, account)) {
    class Factory(
        val user: ChatroomKey,
        val account: Account,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ChatroomFeedViewModel(user, account) as T
    }
}

@Stable
abstract class ListChangeFeedViewModel(
    localFilter: ChangesFlowFilter<Note>,
) : ViewModel(),
    InvalidatableContent {
    val feedState = FeedContentState(localFilter, viewModelScope, LocalCache)

    override val isRefreshing = feedState.isRefreshing

    override fun invalidateData(ignoreIfDoing: Boolean) = feedState.invalidateData(ignoreIfDoing)

    init {
        Log.d("Init", "Starting new Model: ${this.javaClass.simpleName}")
        viewModelScope.launch(Dispatchers.IO) {
            localFilter.changesFlow().collect {
                Log.d("Init", "Collecting changes to: ${this@ListChangeFeedViewModel.javaClass.simpleName}")
                when (it) {
                    is ListChange.Addition -> feedState.updateFeedWith(setOf(it.item))
                    is ListChange.Deletion -> feedState.deleteFromFeed(setOf(it.item))
                    is ListChange.SetAddition -> feedState.updateFeedWith(it.item)
                    is ListChange.SetDeletion -> feedState.deleteFromFeed(it.item)
                }
            }
        }
    }
}
