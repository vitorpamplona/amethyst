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
package com.vitorpamplona.amethyst.ui.note.creators.userSuggestions

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.NostrSearchEventOrUserDataSource
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class UserSuggestionState(
    val accountViewModel: AccountViewModel,
) {
    var search = MutableStateFlow("")
    var results =
        search
            .debounce(500)
            .distinctUntilChanged()
            .map { word ->
                if (word.startsWith("@") && word.length > 2) {
                    val prefix = word.removePrefix("@")
                    NostrSearchEventOrUserDataSource.search(prefix)
                    accountViewModel.findUsersStartingWithSync(prefix)
                } else {
                    NostrSearchEventOrUserDataSource.clear()
                    search.tryEmit("")
                    emptyList()
                }
            }.flowOn(Dispatchers.IO)

    fun reset() {
        NostrSearchEventOrUserDataSource.clear()
        search.tryEmit("")
    }

    fun processCurrentWord(word: String) {
        search.tryEmit(word)
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
