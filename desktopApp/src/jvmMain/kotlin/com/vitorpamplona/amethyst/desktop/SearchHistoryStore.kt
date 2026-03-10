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

import com.vitorpamplona.amethyst.commons.search.QueryParser
import com.vitorpamplona.amethyst.commons.search.QuerySerializer
import com.vitorpamplona.amethyst.commons.search.SearchQuery
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.prefs.Preferences

object SearchHistoryStore {
    private val prefs: Preferences = Preferences.userNodeForPackage(SearchHistoryStore::class.java)

    private const val KEY_HISTORY = "search_history"
    private const val KEY_SAVED = "saved_searches"
    private const val SEPARATOR = "\n"
    private const val SAVED_SEPARATOR = "\t"
    private const val MAX_HISTORY = 20

    private val _history = MutableStateFlow<List<SearchQuery>>(emptyList())
    val history: StateFlow<List<SearchQuery>> = _history.asStateFlow()

    private val _savedSearches = MutableStateFlow<List<SavedSearch>>(emptyList())
    val savedSearches: StateFlow<List<SavedSearch>> = _savedSearches.asStateFlow()

    init {
        _history.value = loadHistory()
        _savedSearches.value = loadSaved()
    }

    fun addToHistory(query: SearchQuery) {
        if (query.isEmpty) return
        val serialized = QuerySerializer.serialize(query)
        val current = _history.value.toMutableList()
        current.removeAll { QuerySerializer.serialize(it) == serialized }
        current.add(0, query)
        if (current.size > MAX_HISTORY) {
            current.subList(MAX_HISTORY, current.size).clear()
        }
        _history.value = current.toList()
        persistHistory(current)
    }

    fun clearHistory() {
        _history.value = emptyList()
        prefs.remove(KEY_HISTORY)
    }

    fun saveSearch(
        query: SearchQuery,
        label: String,
    ) {
        if (query.isEmpty) return
        val saved =
            SavedSearch(
                id = System.currentTimeMillis().toString(),
                label = label,
                query = query,
                createdAt = System.currentTimeMillis() / 1000,
            )
        val current = _savedSearches.value + saved
        _savedSearches.value = current
        persistSaved(current)
    }

    fun deleteSavedSearch(id: String) {
        val current = _savedSearches.value.filter { it.id != id }
        _savedSearches.value = current
        persistSaved(current)
    }

    private fun loadHistory(): List<SearchQuery> {
        val raw = prefs.get(KEY_HISTORY, "")
        if (raw.isBlank()) return emptyList()
        return raw
            .split(SEPARATOR)
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val parsed = QueryParser.parse(line)
                if (parsed.isEmpty) null else parsed
            }
    }

    private fun persistHistory(queries: List<SearchQuery>) {
        val raw = queries.joinToString(SEPARATOR) { QuerySerializer.serialize(it) }
        prefs.put(KEY_HISTORY, raw)
    }

    private fun loadSaved(): List<SavedSearch> {
        val raw = prefs.get(KEY_SAVED, "")
        if (raw.isBlank()) return emptyList()
        return raw
            .split(SEPARATOR)
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val parts = line.split(SAVED_SEPARATOR)
                if (parts.size < 4) return@mapNotNull null
                val id = parts[0]
                val label = parts[1]
                val createdAt = parts[2].toLongOrNull() ?: return@mapNotNull null
                val queryText = parts[3]
                val query = QueryParser.parse(queryText)
                if (query.isEmpty) return@mapNotNull null
                SavedSearch(id = id, label = label, query = query, createdAt = createdAt)
            }
    }

    private fun persistSaved(searches: List<SavedSearch>) {
        val raw =
            searches.joinToString(SEPARATOR) { s ->
                listOf(s.id, s.label, s.createdAt.toString(), QuerySerializer.serialize(s.query))
                    .joinToString(SAVED_SEPARATOR)
            }
        prefs.put(KEY_SAVED, raw)
    }
}

data class SavedSearch(
    val id: String,
    val label: String,
    val query: SearchQuery,
    val createdAt: Long,
)
