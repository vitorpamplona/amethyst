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
package com.vitorpamplona.amethyst.ui.note.creators.userSuggestions

import androidx.compose.runtime.Stable
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.logTime
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.service.relayClient.searchCommand.SearchQueryState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

@Stable
class UserSuggestionState(
    val account: Account,
    val requireAtSymbol: Boolean = true,
) {
    val invalidations = MutableStateFlow<Int>(0)
    val currentWord = MutableStateFlow("")
    val searchDataSourceState = SearchQueryState(MutableStateFlow(""), account)

    @OptIn(FlowPreview::class)
    val searchTerm =
        currentWord
            .debounce(300)
            .distinctUntilChanged()
            .map(::userSearchTermOrNull)
            .onEach(::updateDataSource)

    @OptIn(FlowPreview::class)
    val results =
        combine(searchTerm, invalidations.debounce(100)) { prefix, version ->
            if (prefix != null) {
                logTime("UserSuggestionState Search $prefix version $version") {
                    account.cache.findUsersStartingWith(prefix, account)
                }
            } else {
                emptyList()
            }
        }.flowOn(Dispatchers.IO)

    fun reset() {
        currentWord.tryEmit("")
    }

    fun processCurrentWord(word: String) {
        currentWord.tryEmit(word)
    }

    fun invalidateData() {
        // force new query
        invalidations.update { it + 1 }
    }

    fun userSearchTermOrNull(currentWord: String): String? =
        if (requireAtSymbol) {
            if (currentWord.startsWith("@") && currentWord.length > 2) {
                currentWord.removePrefix("@")
            } else {
                null
            }
        } else {
            if (currentWord.length > 1) {
                currentWord
            } else {
                null
            }
        }

    fun updateDataSource(searchTerm: String?) {
        if (searchTerm != null) {
            searchDataSourceState.searchQuery.tryEmit(searchTerm)
        } else {
            searchDataSourceState.searchQuery.tryEmit("")
        }
    }

    fun replaceCurrentWord(
        message: TextFieldValue,
        word: String,
        item: User,
    ): TextFieldValue {
        val lastWordStart = message.selection.end - word.length
        val wordToInsert = "@${item.pubkeyNpub()}"

        return TextFieldValue(
            message.text.replaceRange(lastWordStart, message.selection.end, wordToInsert),
            TextRange(lastWordStart + wordToInsert.length, lastWordStart + wordToInsert.length),
        )
    }
}
