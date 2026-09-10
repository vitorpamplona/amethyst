/*
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
package com.vitorpamplona.amethyst.desktop

import com.vitorpamplona.amethyst.commons.search.SearchHistory
import com.vitorpamplona.amethyst.commons.search.SearchHistoryStorage
import com.vitorpamplona.amethyst.commons.search.SearchQuery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import java.util.prefs.Preferences

/**
 * Desktop's half of the search history: a `Preferences` node, and nothing else.
 *
 * Everything the history *does* — the cap, the ordering, the de-duplication, the encoding — moved
 * to [SearchHistory] in commons, where Android can have it too. What is left here is the one
 * genuinely desktop thing, which is where the two strings are kept. The stored format is
 * unchanged, so an existing history keeps loading.
 */
object SearchHistoryStore {
    private val prefs: Preferences = Preferences.userNodeForPackage(SearchHistoryStore::class.java)

    private val storage =
        object : SearchHistoryStorage {
            override suspend fun read(key: String): String? = prefs.get(key, "").takeIf { it.isNotBlank() }

            override suspend fun write(
                key: String,
                value: String?,
            ) {
                if (value.isNullOrBlank()) prefs.remove(key) else prefs.put(key, value)
            }
        }

    private val store = SearchHistory(storage, CoroutineScope(Dispatchers.Default + SupervisorJob()))

    val history: StateFlow<List<SearchQuery>> get() = store.recent

    val savedSearches get() = store.saved

    fun addToHistory(query: SearchQuery) = store.remember(query)

    fun clearHistory() = store.clearRecent()

    fun saveSearch(
        query: SearchQuery,
        label: String,
    ) = store.save(query, label)

    fun deleteSavedSearch(id: String) = store.forget(id)
}
