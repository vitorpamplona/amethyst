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
import com.vitorpamplona.amethyst.commons.nip64Chess.RelaySyncState
import com.vitorpamplona.amethyst.commons.nip64Chess.RelaySyncStatus
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * Desktop's search: the relay traffic, the results it produces, and the panel around them.
 *
 * Everything about *what is being searched* — the text, its parse, the debounce, the scope, the
 * sort orders — is [state], shared with Android. This class used to own its own copy of all of
 * it, which is how it came to rank Relevance against the whole box (chips included, so a query
 * with a `from:` in it scored on the literal text of its own tokens) while Android ranked against
 * the leftover words.
 *
 * What is left here is genuinely Desktop's: results arrive as relay callbacks rather than from a
 * cache scan, and are held as raw [Event]s because Desktop renders them that way. Per-relay sync
 * status and the expanded form panel have no Android counterpart at all.
 */
@OptIn(FlowPreview::class)
class AdvancedSearchBarState(
    private val scope: CoroutineScope,
) {
    /** What is being searched. Shared. */
    val state = SearchState(scope)

    val query: StateFlow<SearchQuery> =
        state.current
            .map { it.query }
            .stateIn(scope, SharingStarted.Eagerly, state.query)

    val debouncedQuery: StateFlow<SearchQuery> =
        state.debouncedForRelays
            .map { it.query }
            .stateIn(scope, SharingStarted.Eagerly, SearchQuery.EMPTY)

    /**
     * What the field shows, which is simply the box.
     *
     * There used to be a `ChangeSource` flag here deciding whether to show the raw text or the
     * serialized query, because a form edit wrote to the query and typing wrote to the text and
     * the two could disagree. [SearchState.edit] writes the token into the box instead, so a
     * button press and a typed token are the same thing and there is nothing left to decide.
     */
    val displayText: StateFlow<String> get() = state.text

    val eventSortOrder: StateFlow<SearchSortOrder> get() = state.eventSortOrder
    val peopleSortOrder: StateFlow<SearchSortOrder> get() = state.peopleSortOrder

    // People search results (from cache + relay)
    private val _peopleResults = MutableStateFlow<ImmutableList<User>>(persistentListOf())
    val peopleResults: StateFlow<ImmutableList<User>> = _peopleResults.asStateFlow()

    // Note/event results (from relay subscriptions)
    private val _noteResults = MutableStateFlow<ImmutableList<Event>>(persistentListOf())
    val noteResults: StateFlow<ImmutableList<Event>> = _noteResults.asStateFlow()

    /**
     * The results in the order the reader asked for, through the shared pipeline.
     *
     * Ranked on the query's *leftover* terms rather than on the whole box: `from:npub1…` and
     * `kind:article` are filters, and hunting for their literal text inside an event's content
     * ranks on noise. This was scoring the raw field text until the pipeline took the job over.
     */
    val sortedNoteResults: StateFlow<ImmutableList<Event>> =
        combine(_noteResults, state.eventSortOrder, query) { notes, order, q ->
            SearchPipeline.rank(notes, order, q.text, { it }).toImmutableList()
        }.stateIn(scope, SharingStarted.Eagerly, persistentListOf())

    val sortedPeopleResults: StateFlow<ImmutableList<User>> =
        combine(_peopleResults, state.peopleSortOrder) { people, order ->
            SearchResultSorter.sortPeople(people, order).toImmutableList()
        }.stateIn(scope, SharingStarted.Eagerly, persistentListOf())

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
    fun updateFromText(rawText: String) = state.updateText(rawText)

    // Form panel inputs. Each writes its token into the box; see SearchState.edit.

    fun updateKinds(kinds: List<Int>) = state.edit { it.copy(kinds = kinds.toImmutableList()) }

    fun updatePseudoKinds(pseudoKinds: List<String>) = state.edit { it.copy(pseudoKinds = pseudoKinds.toImmutableList()) }

    fun addAuthor(hexOrName: String) =
        state.edit { current ->
            val hex = decodePublicKeyAsHexOrNull(hexOrName)
            when {
                hex != null && hex !in current.authors -> current.copy(authors = (current.authors + hex).toImmutableList())
                hex == null && hexOrName !in current.authorNames -> current.copy(authorNames = (current.authorNames + hexOrName).toImmutableList())
                else -> current
            }
        }

    fun removeAuthor(hex: String) =
        state.edit {
            it.copy(
                authors = it.authors.filter { author -> author != hex }.toImmutableList(),
                authorNames = it.authorNames.filter { name -> name != hex }.toImmutableList(),
            )
        }

    fun updateDateRange(
        since: Long?,
        until: Long?,
    ) = state.edit { it.copy(since = since, until = until) }

    fun addHashtag(tag: String) =
        state.edit { current ->
            val cleaned = tag.removePrefix("#")
            if (cleaned in current.hashtags) current else current.copy(hashtags = (current.hashtags + cleaned).toImmutableList())
        }

    fun removeHashtag(tag: String) = state.edit { it.copy(hashtags = it.hashtags.filter { h -> h != tag }.toImmutableList()) }

    fun addExcludeTerm(term: String) =
        state.edit { current ->
            if (term in current.excludeTerms) current else current.copy(excludeTerms = (current.excludeTerms + term).toImmutableList())
        }

    fun removeExcludeTerm(term: String) = state.edit { it.copy(excludeTerms = it.excludeTerms.filter { t -> t != term }.toImmutableList()) }

    fun updateLanguage(lang: String?) = state.edit { it.copy(language = lang) }

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

    fun updateEventSortOrder(order: SearchSortOrder) = state.updateEventSortOrder(order)

    fun updatePeopleSortOrder(order: SearchSortOrder) = state.updatePeopleSortOrder(order)

    fun clearSearch() {
        state.clear()
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
            _noteResults.value = (current + events).toImmutableList()
        }
    }
}
