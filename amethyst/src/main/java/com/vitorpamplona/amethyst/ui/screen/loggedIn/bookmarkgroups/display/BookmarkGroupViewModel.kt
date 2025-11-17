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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.bookmarkgroups.display

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.tags.AddressBookmark
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.tags.BookmarkIdTag
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.tags.EventBookmark
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@Stable
class BookmarkGroupViewModel(
    val account: Account,
    bookmarkGroupIdentifier: String,
) : ViewModel() {
    val selectedBookmarkGroupFlow =
        account.labeledBookmarkLists
            .getLabeledBookmarkListFlow(bookmarkGroupIdentifier)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(2500), null)

    fun publicPosts() =
        selectedBookmarkGroupFlow
            .filterNotNull()
            .map { group ->
                group.publicBookmarks
                    .filter { it is EventBookmark }
                    .map { account.cache.getOrCreateNote((it as EventBookmark).eventId) }
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun privatePosts() =
        selectedBookmarkGroupFlow
            .filterNotNull()
            .map { group ->
                group.privateBookmarks
                    .filter { it is EventBookmark }
                    .map { account.cache.getOrCreateNote((it as EventBookmark).eventId) }
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun publicArticles() =
        selectedBookmarkGroupFlow
            .filterNotNull()
            .map { group ->
                group.publicBookmarks
                    .filter { it is AddressBookmark }
                    .map { account.cache.getOrCreateAddressableNote((it as AddressBookmark).address) }
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun privateArticles() =
        selectedBookmarkGroupFlow
            .filterNotNull()
            .map { group ->
                group.privateBookmarks
                    .filter { it is AddressBookmark }
                    .map { account.cache.getOrCreateAddressableNote((it as AddressBookmark).address) }
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // TODO: Add implementations for Hashtag and Link bookmarks

    suspend fun deleteBookmarkGroup() {
        selectedBookmarkGroupFlow.value?.let {
            account.labeledBookmarkLists.deleteBookmarkList(it, account)
        }
    }

    suspend fun addBookmarkToGroup(
        bookmark: BookmarkIdTag,
        isPrivate: Boolean,
        account: Account,
    ) {
        selectedBookmarkGroupFlow.value?.let {
            account.labeledBookmarkLists.addBookmarkToList(
                bookmark,
                it,
                isPrivate,
                account,
            )
        }
    }

    suspend fun removeBookmarkFromGroup(
        bookmark: BookmarkIdTag,
        isPrivate: Boolean,
        account: Account,
    ) {
        selectedBookmarkGroupFlow.value?.let {
            account.labeledBookmarkLists.removeBookmarkFromList(
                bookmark,
                it,
                isPrivate,
                account,
            )
        }
    }

    class Initializer(
        val account: Account,
        val bookmarkGroupIdentifier: String,
    ) : ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T = BookmarkGroupViewModel(account, bookmarkGroupIdentifier) as T
    }
}
