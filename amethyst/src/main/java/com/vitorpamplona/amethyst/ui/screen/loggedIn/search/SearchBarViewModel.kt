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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.search

import android.util.Log
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.Channel
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.ammolite.relays.BundledUpdate
import com.vitorpamplona.quartz.events.findHashtags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@Stable
class SearchBarViewModel(
    val account: Account,
) : ViewModel() {
    val focusRequester = FocusRequester()
    var searchValue by mutableStateOf("")

    private var _searchResultsUsers = MutableStateFlow<List<User>>(emptyList())
    private var _searchResultsNotes = MutableStateFlow<List<Note>>(emptyList())
    private var _searchResultsChannels = MutableStateFlow<List<Channel>>(emptyList())
    private var _hashtagResults = MutableStateFlow<List<String>>(emptyList())

    val searchResultsUsers = _searchResultsUsers.asStateFlow()
    val searchResultsNotes = _searchResultsNotes.asStateFlow()
    val searchResultsChannels = _searchResultsChannels.asStateFlow()
    val hashtagResults = _hashtagResults.asStateFlow()

    val isSearching by derivedStateOf { searchValue.isNotBlank() }

    fun updateSearchValue(newValue: String) {
        searchValue = newValue
    }

    private suspend fun runSearch() {
        if (searchValue.isBlank()) {
            _hashtagResults.value = emptyList()
            _searchResultsUsers.value = emptyList()
            _searchResultsChannels.value = emptyList()
            _searchResultsNotes.value = emptyList()
            return
        }

        _hashtagResults.emit(findHashtags(searchValue))
        _searchResultsUsers.emit(
            LocalCache
                .findUsersStartingWith(searchValue)
                .sortedWith(
                    compareBy(
                        { it.toBestDisplayName().startsWith(searchValue, true) },
                        { account.isFollowing(it) },
                        { it.toBestDisplayName() },
                    ),
                ).reversed(),
        )
        _searchResultsNotes.emit(
            LocalCache
                .findNotesStartingWith(searchValue)
                .sortedWith(compareBy({ it.createdAt() }, { it.idHex }))
                .reversed(),
        )
        _searchResultsChannels.emit(LocalCache.findChannelsStartingWith(searchValue))
    }

    fun clear() {
        searchValue = ""
        _searchResultsUsers.value = emptyList()
        _searchResultsChannels.value = emptyList()
        _searchResultsNotes.value = emptyList()
        _searchResultsChannels.value = emptyList()
    }

    private val bundler = BundledUpdate(250, Dispatchers.IO)

    fun invalidateData() {
        bundler.invalidate {
            // adds the time to perform the refresh into this delay
            // holding off new updates in case of heavy refresh routines.
            runSearch()
        }
    }

    override fun onCleared() {
        bundler.cancel()
        Log.d("Init", "OnCleared: ${this.javaClass.simpleName}")
        super.onCleared()
    }

    fun isSearchingFun() = searchValue.isNotBlank()

    class Factory(
        val account: Account,
    ) : ViewModelProvider.Factory {
        override fun <SearchBarViewModel : ViewModel> create(modelClass: Class<SearchBarViewModel>): SearchBarViewModel = SearchBarViewModel(account) as SearchBarViewModel
    }
}
