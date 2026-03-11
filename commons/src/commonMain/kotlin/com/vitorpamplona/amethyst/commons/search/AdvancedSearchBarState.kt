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
package com.vitorpamplona.amethyst.commons.search

import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.quartz.nip01Core.core.Event
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn

enum class ChangeSource {
    TEXT,
    FORM,
    INIT,
}

@OptIn(FlowPreview::class)
class AdvancedSearchBarState(
    private val scope: CoroutineScope,
    private val debounceMs: Long = 300L,
) {
    private val _query = MutableStateFlow(SearchQuery.EMPTY)
    val query: StateFlow<SearchQuery> = _query.asStateFlow()

    private var _changeSource: ChangeSource = ChangeSource.INIT
    val changeSource get() = _changeSource

    private val _rawText = MutableStateFlow("")
    val rawText: StateFlow<String> = _rawText.asStateFlow()

    val displayText: StateFlow<String> =
        combine(_query, _rawText) { query, raw ->
            if (_changeSource == ChangeSource.TEXT) {
                raw
            } else {
                QuerySerializer.serialize(query)
            }
        }.stateIn(scope, SharingStarted.Eagerly, "")

    val debouncedQuery: StateFlow<SearchQuery> =
        _query
            .debounce(debounceMs)
            .stateIn(scope, SharingStarted.Eagerly, SearchQuery.EMPTY)

    // People search results (from cache + relay)
    private val _peopleResults = MutableStateFlow<ImmutableList<User>>(persistentListOf())
    val peopleResults: StateFlow<ImmutableList<User>> = _peopleResults.asStateFlow()

    // Note/event results (from relay subscriptions)
    private val _noteResults = MutableStateFlow<ImmutableList<Event>>(persistentListOf())
    val noteResults: StateFlow<ImmutableList<Event>> = _noteResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    // Expanded panel state
    private val _panelExpanded = MutableStateFlow(false)
    val panelExpanded: StateFlow<Boolean> = _panelExpanded.asStateFlow()

    // Text bar input
    fun updateFromText(rawText: String) {
        _changeSource = ChangeSource.TEXT
        _rawText.value = rawText
        _query.value = QueryParser.parse(rawText)
    }

    // Form panel inputs
    fun updateKinds(kinds: List<Int>) {
        _changeSource = ChangeSource.FORM
        _query.value = _query.value.copy(kinds = kinds.toImmutableList())
    }

    fun addAuthor(hexOrName: String) {
        _changeSource = ChangeSource.FORM
        val current = _query.value
        val hex =
            com.vitorpamplona.quartz.nip19Bech32
                .decodePublicKeyAsHexOrNull(hexOrName)
        if (hex != null) {
            if (hex !in current.authors) {
                _query.value = current.copy(authors = (current.authors + hex).toImmutableList())
            }
        } else {
            if (hexOrName !in current.authorNames) {
                _query.value = current.copy(authorNames = (current.authorNames + hexOrName).toImmutableList())
            }
        }
    }

    fun removeAuthor(hex: String) {
        _changeSource = ChangeSource.FORM
        val current = _query.value
        _query.value =
            current.copy(
                authors = current.authors.filter { it != hex }.toImmutableList(),
                authorNames = current.authorNames.filter { it != hex }.toImmutableList(),
            )
    }

    fun updateDateRange(
        since: Long?,
        until: Long?,
    ) {
        _changeSource = ChangeSource.FORM
        _query.value = _query.value.copy(since = since, until = until)
    }

    fun addHashtag(tag: String) {
        _changeSource = ChangeSource.FORM
        val current = _query.value
        val cleaned = tag.removePrefix("#")
        if (cleaned !in current.hashtags) {
            _query.value = current.copy(hashtags = (current.hashtags + cleaned).toImmutableList())
        }
    }

    fun removeHashtag(tag: String) {
        _changeSource = ChangeSource.FORM
        val current = _query.value
        _query.value = current.copy(hashtags = current.hashtags.filter { it != tag }.toImmutableList())
    }

    fun addExcludeTerm(term: String) {
        _changeSource = ChangeSource.FORM
        val current = _query.value
        if (term !in current.excludeTerms) {
            _query.value = current.copy(excludeTerms = (current.excludeTerms + term).toImmutableList())
        }
    }

    fun removeExcludeTerm(term: String) {
        _changeSource = ChangeSource.FORM
        val current = _query.value
        _query.value = current.copy(excludeTerms = current.excludeTerms.filter { it != term }.toImmutableList())
    }

    fun updateLanguage(lang: String?) {
        _changeSource = ChangeSource.FORM
        _query.value = _query.value.copy(language = lang)
    }

    fun togglePanel() {
        _panelExpanded.value = !_panelExpanded.value
    }

    fun clearSearch() {
        _changeSource = ChangeSource.INIT
        _rawText.value = ""
        _query.value = SearchQuery.EMPTY
        _peopleResults.value = persistentListOf()
        _noteResults.value = persistentListOf()
        _isSearching.value = false
    }

    // Results management (called from subscription callbacks)
    fun startSearching() {
        _isSearching.value = true
    }

    fun stopSearching() {
        _isSearching.value = false
    }

    fun clearResults() {
        _peopleResults.value = persistentListOf()
        _noteResults.value = persistentListOf()
    }

    fun addPeopleResult(user: User) {
        val current = _peopleResults.value
        if (current.none { it.pubkeyHex == user.pubkeyHex }) {
            _peopleResults.value = (current + user).toImmutableList()
        }
    }

    fun addNoteResults(events: List<Event>) {
        val current = _noteResults.value
        val existingIds = current.map { it.id }.toSet()
        val newEvents = events.filter { it.id !in existingIds }
        if (newEvents.isNotEmpty()) {
            _noteResults.value = (current + newEvents).sortedByDescending { it.createdAt }.toImmutableList()
        }
    }
}
