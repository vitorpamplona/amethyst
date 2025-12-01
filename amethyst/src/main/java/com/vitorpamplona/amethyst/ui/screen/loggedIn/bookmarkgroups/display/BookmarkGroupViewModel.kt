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
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.tags.AddressBookmark
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.tags.BookmarkIdTag
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.tags.EventBookmark
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlin.collections.emptyList

@Stable
class BookmarkGroupViewModel(
    val account: Account,
    val bookmarkGroupIdentifier: String,
) : ViewModel() {
    val selectedBookmarkGroupFlow =
        account.labeledBookmarkLists
            .getLabeledBookmarkListFlow(bookmarkGroupIdentifier)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(2500), null)

    fun publicPosts() =
        selectedBookmarkGroupFlow
            .filterNotNull()
            .map { group -> group.publicPostBookmarks.map { account.cache.getOrCreateNote(it.eventId) } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun privatePosts() =
        selectedBookmarkGroupFlow
            .filterNotNull()
            .map { group -> group.privatePostBookmarks.map { account.cache.getOrCreateNote(it.eventId) } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun publicArticles() =
        selectedBookmarkGroupFlow
            .filterNotNull()
            .map { group -> group.publicArticleBookmarks.map { account.cache.getOrCreateAddressableNote(it.address) } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun privateArticles() =
        selectedBookmarkGroupFlow
            .filterNotNull()
            .map { group -> group.privateArticleBookmarks.map { account.cache.getOrCreateAddressableNote(it.address) } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    suspend fun deleteBookmarkGroup(groupIdentifier: String) {
        account.labeledBookmarkLists.deleteBookmarkList(groupIdentifier, account)
    }

    suspend fun addBookmarkToGroup(
        groupIdentifier: String = bookmarkGroupIdentifier,
        bookmark: BookmarkIdTag,
        isPrivate: Boolean,
    ) {
        account.labeledBookmarkLists.addBookmarkToList(
            bookmark,
            groupIdentifier,
            isPrivate,
            account,
        )
    }

    suspend fun movePostBookmark(
        groupIdentifier: String = bookmarkGroupIdentifier,
        postId: String,
        isCurrentlyPrivate: Boolean,
    ) {
        val eventBookmark = EventBookmark(postId)
        moveBookmark(groupIdentifier, eventBookmark, isCurrentlyPrivate)
    }

    suspend fun moveArticleBookmark(
        groupIdentifier: String = bookmarkGroupIdentifier,
        articleAddress: Address,
        isCurrentlyPrivate: Boolean,
    ) {
        val eventBookmark = AddressBookmark(articleAddress)
        moveBookmark(groupIdentifier, eventBookmark, isCurrentlyPrivate)
    }

    suspend fun moveBookmark(
        groupIdentifier: String = bookmarkGroupIdentifier,
        bookmark: BookmarkIdTag,
        isCurrentlyPrivate: Boolean,
    ) {
        account.labeledBookmarkLists.moveBookmarkInList(
            bookmark,
            groupIdentifier,
            isCurrentlyPrivate,
            account,
        )
    }

    suspend fun removePostBookmark(
        groupIdentifier: String = bookmarkGroupIdentifier,
        bookmarkPostId: String,
        isPrivate: Boolean,
    ) {
        val eventBookmark = EventBookmark(bookmarkPostId)
        removeBookmarkFromGroup(
            groupIdentifier,
            eventBookmark,
            isPrivate,
        )
    }

    suspend fun removeArticleBookmark(
        groupIdentifier: String = bookmarkGroupIdentifier,
        bookmarkArticleAddress: Address,
        isPrivate: Boolean,
    ) {
        val eventBookmark = AddressBookmark(bookmarkArticleAddress)
        removeBookmarkFromGroup(
            groupIdentifier,
            eventBookmark,
            isPrivate,
        )
    }

    suspend fun removeBookmarkFromGroup(
        groupIdentifier: String = bookmarkGroupIdentifier,
        bookmark: BookmarkIdTag,
        isPrivate: Boolean,
    ) {
        account.labeledBookmarkLists.removeBookmarkFromList(
            bookmark,
            groupIdentifier,
            isPrivate,
            account,
        )
    }

    class Initializer(
        val account: Account,
        val bookmarkGroupIdentifier: String,
    ) : ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T = BookmarkGroupViewModel(account, bookmarkGroupIdentifier) as T
    }
}
