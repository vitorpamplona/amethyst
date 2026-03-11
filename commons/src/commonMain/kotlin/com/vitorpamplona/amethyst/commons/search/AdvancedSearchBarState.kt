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

import com.vitorpamplona.amethyst.commons.chess.RelaySyncState
import com.vitorpamplona.amethyst.commons.chess.RelaySyncStatus
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

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

    private val activeSubIds = MutableStateFlow<Set<String>>(emptySet())
    val isSearching: StateFlow<Boolean> =
        activeSubIds
            .map { it.isNotEmpty() }
            .stateIn(scope, SharingStarted.Eagerly, false)

    private val eventDeduplicator = EventDeduplicator()

    // Expanded panel state
    private val _panelExpanded = MutableStateFlow(false)
    val panelExpanded: StateFlow<Boolean> = _panelExpanded.asStateFlow()

    // Per-relay sync status
    private val _relayStates = MutableStateFlow<ImmutableList<RelaySyncState>>(persistentListOf())
    val relayStates: StateFlow<ImmutableList<RelaySyncState>> = _relayStates.asStateFlow()

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

    fun initRelayStates(relays: Set<NormalizedRelayUrl>) {
        _relayStates.value =
            relays
                .map {
                    RelaySyncState(
                        url = it.url,
                        displayName = it.displayUrl(),
                        status = RelaySyncStatus.WAITING,
                    )
                }.toImmutableList()
    }

    fun updateRelayState(
        relayUrl: String,
        status: RelaySyncStatus,
        eventsDelta: Int = 0,
    ) {
        _relayStates.update { states ->
            states
                .map {
                    if (it.url == relayUrl) {
                        it.copy(status = status, eventsReceived = it.eventsReceived + eventsDelta)
                    } else {
                        it
                    }
                }.toImmutableList()
        }
    }

    fun timeoutWaitingRelays() {
        _relayStates.update { states ->
            states
                .map {
                    if (it.status == RelaySyncStatus.WAITING || it.status == RelaySyncStatus.CONNECTING) {
                        it.copy(status = RelaySyncStatus.FAILED)
                    } else {
                        it
                    }
                }.toImmutableList()
        }
        activeSubIds.value = emptySet()
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
        _relayStates.value = persistentListOf()
        activeSubIds.value = emptySet()
        eventDeduplicator.clear()
    }

    // Results management (called from subscription callbacks)
    fun startSearching(subId: String) {
        activeSubIds.update { it + subId }
    }

    fun stopSearching(subId: String) {
        activeSubIds.update { it - subId }
    }

    fun trackRelayEvent(
        relayUrl: String,
        eventId: String,
    ): Boolean {
        val isNew = eventDeduplicator.tryAdd(eventId)
        if (isNew) {
            updateRelayState(relayUrl, RelaySyncStatus.RECEIVING, eventsDelta = 1)
        }
        return isNew
    }

    fun clearResults() {
        _peopleResults.value = persistentListOf()
        _noteResults.value = persistentListOf()
        eventDeduplicator.clear()
    }

    fun addPeopleResult(user: User) {
        val current = _peopleResults.value
        if (current.none { it.pubkeyHex == user.pubkeyHex }) {
            _peopleResults.value = (current + user).toImmutableList()
        }
    }

    fun addNoteResults(events: List<Event>) {
        if (events.isNotEmpty()) {
            val current = _noteResults.value
            _noteResults.value = (current + events).sortedByDescending { it.createdAt }.toImmutableList()
        }
    }
}
