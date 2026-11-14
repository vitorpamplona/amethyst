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
package com.vitorpamplona.amethyst.commons.nipsOnNostr

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.rendering.unknown.unknownKindAddress
import com.vitorpamplona.amethyst.commons.service.http.IRoleBasedHttpClientBuilder
import com.vitorpamplona.quartz.experimental.nipsOnNostr.NipTextEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAll
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** An official NIP being loaded from the nips repo. */
@Immutable
sealed interface OfficialNipState {
    data object Loading : OfficialNipState

    data object Failed : OfficialNipState

    @Immutable
    class Loaded(
        val view: NipTextView,
    ) : OfficialNipState
}

/** Loads official NIP [number] for the in-app reader. */
class OfficialNipViewModel(
    val number: String,
    private val http: IRoleBasedHttpClientBuilder,
) : ViewModel() {
    private val _state = MutableStateFlow<OfficialNipState>(OfficialNipState.Loading)
    val state: StateFlow<OfficialNipState> = _state

    init {
        load()
    }

    fun load() {
        _state.value = OfficialNipState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            val text = OfficialNips.text(number, http)
            _state.value = if (text == null) OfficialNipState.Failed else OfficialNipState.Loaded(NipTextView.ofMarkdown(number, text))
        }
    }
}

/** What the app knows about an event kind: who defines it and what its events look like. */
@Immutable
class EventKindState(
    /** NIPs on Nostr whose `k` tags define the kind, newest first. */
    val definitions: List<AddressableNote>,
    /** Recent events of the kind, newest first. */
    val recent: List<Note>,
    /** Events of the kind the relays sent that the app keeps out of feeds (private settings, for one), so it can't draw. */
    val notShown: Int,
    val loadingDefinitions: Boolean,
    val loadingRecent: Boolean,
)

/** Backs the event-kind page: the NIPs that define [kind] and a sample of its recent events. */
class EventKindViewModel(
    val kind: Int,
    private val account: Account,
) : ViewModel() {
    private val recent = MutableStateFlow<Pair<List<Note>, Int>>(emptyList<Note>() to 0)
    private val loadingDefinitions = MutableStateFlow(true)
    private val loadingRecent = MutableStateFlow(true)

    val state: StateFlow<EventKindState> =
        combine(NipsOnNostr.changes, recent, loadingDefinitions, loadingRecent) { _, recent, loadingDefs, loadingRecent ->
            EventKindState(NipsOnNostr.definitionsOf(kind), recent.first, recent.second, loadingDefs, loadingRecent)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, EventKindState(NipsOnNostr.definitionsOf(kind), emptyList(), 0, true, true))

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                NipsOnNostr.fetchDefinitionsOf(account, kind)
            } finally {
                loadingDefinitions.value = false
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                recent.value = fetchRecent()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Relays that fail just leave the sample short.
            } finally {
                loadingRecent.value = false
            }
        }
    }

    private suspend fun fetchRecent(): Pair<List<Note>, Int> {
        val relays = account.followPlusAllMineWithSearch.flow.value
        if (relays.isEmpty()) return emptyList<Note>() to 0
        val filter = Filter(kinds = listOf(kind), limit = RECENT_LIMIT)
        val events =
            account.client
                .fetchAll(filters = relays.associateWith { listOf(filter) }, idleTimeoutMs = 6_000L)
                .filter { it.kind == kind }
                .distinctBy { it.id }
                .sortedByDescending { it.createdAt }
                .take(RECENT_LIMIT)
        events.forEach { account.cache.justConsume(it, null, false) }
        // An unknown replaceable or addressable kind lives at its address, which getNoteIfExists(event)
        // only checks for the kinds quartz has a class for.
        val notes =
            events
                .mapNotNull { event ->
                    unknownKindAddress(event)?.let { account.cache.getAddressableNoteIfExists(it) } ?: account.cache.getNoteIfExists(event)
                }.filter { it.event != null }
        return notes to (events.size - notes.size)
    }

    companion object {
        const val RECENT_LIMIT = 20
    }
}

/** One row of the NIP catalogue. */
@Immutable
sealed interface NipCatalogItem {
    @Immutable
    class Official(
        val entry: OfficialNipEntry,
    ) : NipCatalogItem

    @Immutable
    class OnNostr(
        val note: AddressableNote,
    ) : NipCatalogItem
}

/** Which NIPs the catalogue lists. */
enum class NipCatalogFilter {
    ALL,
    OFFICIAL,
    ON_NOSTR,
}

@Immutable
class NipCatalogState(
    val official: List<OfficialNipEntry>,
    val onNostr: List<AddressableNote>,
    val loadingOfficial: Boolean,
    val officialFailed: Boolean,
    val loadingOnNostr: Boolean,
)

/**
 * Backs the NIPs tab: the official index from the nips repo and the NIPs published on Nostr,
 * filtered by [query] (number, title or kind) and by [filter].
 */
class NipCatalogViewModel(
    private val account: Account,
    private val http: IRoleBasedHttpClientBuilder,
) : ViewModel() {
    val query = MutableStateFlow("")
    val filter = MutableStateFlow(NipCatalogFilter.ALL)

    private val official = MutableStateFlow<List<OfficialNipEntry>>(emptyList())
    private val loadingOfficial = MutableStateFlow(true)
    private val officialFailed = MutableStateFlow(false)
    private val loadingOnNostr = MutableStateFlow(true)

    private val all =
        combine(official, NipsOnNostr.changes, loadingOfficial, officialFailed, loadingOnNostr) { official, _, loadingOfficial, failed, loadingOnNostr ->
            NipCatalogState(official, NipsOnNostr.cached(), loadingOfficial, failed, loadingOnNostr)
        }

    val state: StateFlow<NipCatalogState> =
        combine(all, query, filter) { all, query, filter ->
            val q = query.trim()
            NipCatalogState(
                official = if (filter == NipCatalogFilter.ON_NOSTR) emptyList() else all.official.filter { matches(it, q) },
                onNostr = if (filter == NipCatalogFilter.OFFICIAL) emptyList() else all.onNostr.filter { matches(it, q) },
                loadingOfficial = all.loadingOfficial,
                officialFailed = all.officialFailed,
                loadingOnNostr = all.loadingOnNostr,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NipCatalogState(emptyList(), NipsOnNostr.cached(), true, false, true))

    val isEmpty = state.map { it.official.isEmpty() && it.onNostr.isEmpty() }

    init {
        refresh(force = false)
    }

    fun refresh(force: Boolean = true) {
        viewModelScope.launch(Dispatchers.IO) {
            loadingOfficial.value = true
            val index = OfficialNips.index(http)
            officialFailed.value = index == null
            if (index != null) official.value = index
            loadingOfficial.value = false
        }
        viewModelScope.launch(Dispatchers.IO) {
            loadingOnNostr.value = true
            try {
                NipsOnNostr.fetchAll(account, force)
            } finally {
                loadingOnNostr.value = false
            }
        }
    }

    private fun matches(
        entry: OfficialNipEntry,
        q: String,
    ): Boolean {
        if (q.isEmpty()) return true
        val number = NipLinks.normalize(q)
        if (number != null && entry.number == number) return true
        if (entry.title.contains(q, ignoreCase = true)) return true
        val kind = q.toIntOrNull() ?: return false
        return officialKinds(entry.number).any { it.kind == kind }
    }

    private fun matches(
        note: AddressableNote,
        q: String,
    ): Boolean {
        if (q.isEmpty()) return true
        val event = note.event as? NipTextEvent ?: return false
        if (event.title()?.contains(q, ignoreCase = true) == true) return true
        if (event.dTag().contains(q, ignoreCase = true)) return true
        val kind = q.toIntOrNull()
        if (kind != null && definedKinds(event.tags).any { it.kind == kind }) return true
        return event.content.contains(q, ignoreCase = true) && q.length >= 3
    }
}
