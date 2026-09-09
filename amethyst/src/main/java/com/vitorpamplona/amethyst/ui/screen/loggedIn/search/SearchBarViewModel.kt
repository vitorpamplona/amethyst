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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.search

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.actions.ConcordActions
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.relayClient.search.SearchQueryState
import com.vitorpamplona.amethyst.commons.search.QueryParser
import com.vitorpamplona.amethyst.commons.search.SearchFilterBuilder
import com.vitorpamplona.amethyst.commons.search.SearchResultFilter
import com.vitorpamplona.amethyst.commons.search.SearchScope
import com.vitorpamplona.amethyst.commons.search.SearchSortOrder
import com.vitorpamplona.amethyst.commons.search.SearchSource
import com.vitorpamplona.amethyst.commons.search.nameSearchTerms
import com.vitorpamplona.amethyst.commons.search.wholeInputNip19
import com.vitorpamplona.amethyst.commons.ui.feeds.InvalidatableContent
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.ui.dal.sortedByDefaultFeedOrder
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.note.creators.userSuggestions.userUriPrefixes
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.common.relaySetupInfoBuilder
import com.vitorpamplona.quartz.buzz.invite.BuzzInviteLink
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrlOrNull
import com.vitorpamplona.quartz.nip05DnsIdentifiers.INip05Client
import com.vitorpamplona.quartz.nip05DnsIdentifiers.Nip05Id
import com.vitorpamplona.quartz.nip10Notes.content.findHashtags
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.decodeEventIdAsHexOrNull
import com.vitorpamplona.quartz.nip19Bech32.entities.Entity
import com.vitorpamplona.quartz.nip19Bech32.entities.IPubKeyEntity
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NNote
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip19Bech32.entities.NSec
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.Rfc3986
import com.vitorpamplona.quartz.utils.startsWithAny
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

@Stable
@OptIn(FlowPreview::class)
class SearchBarViewModel(
    val account: Account,
    val nip05Client: INip05Client,
    /**
     * The query the calling screen seeded the box with — `from:<npub>` off a profile,
     * `kind:article` off the articles feed. It is ordinary field text from here on: the reader
     * can edit or delete it like anything they typed themselves.
     */
    initialQuery: String? = null,
) : ViewModel(),
    InvalidatableContent {
    val focusRequester = FocusRequester()
    var searchValue by mutableStateOf(initialQuery.orEmpty())

    val invalidations = MutableStateFlow(0)
    val searchValueFlow = MutableStateFlow(searchValue)

    /**
     * True while a token picker is open under the field.
     *
     * The search screen pins its bars on this. A picker is a scrollable inside the *top bar*, and
     * [com.vitorpamplona.amethyst.commons.ui.layouts.DisappearingBarNestedScroll] moves the bars
     * on `consumed + available` — a sum that is conserved as a scroll walks up the nested-scroll
     * chain, so no connection under the picker can hide its scrolling from the scaffold. Scrolling
     * the list of kinds slid the whole chrome away with it. The scaffold has to be told instead.
     */
    val pickerOpen = MutableStateFlow(false)

    /** The scope the reader picked, which is not always the one that applies — see [scope]. */
    private val pickedScope = MutableStateFlow(SearchScope.ALL)

    /**
     * True while the query names a `kind:`, which only an event can have.
     *
     * The People half of the toggle cannot answer such a query — a person is not an event of any
     * kind — so leaving it selectable offers the reader a scope guaranteed to come back empty.
     */
    val scopePinnedToNotes: StateFlow<Boolean> =
        searchValueFlow
            .map { QueryParser.parse(it).isEventOnly }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /**
     * The scope that actually applies: the reader's pick, unless the query names a kind.
     *
     * Derived rather than written back over [pickedScope] on purpose — dropping the `kind:` chip
     * has to give the reader the scope they chose before, not leave them pinned to Notes by a
     * filter that is no longer there.
     */
    val scope: StateFlow<SearchScope> =
        combine(pickedScope, scopePinnedToNotes) { picked, pinned ->
            if (pinned) SearchScope.NOTES else picked
        }.stateIn(viewModelScope, SharingStarted.Eagerly, SearchScope.ALL)
    val source = MutableStateFlow(SearchSource.RELAYS)
    val followsOnly = MutableStateFlow(false)
    val sortOrder = MutableStateFlow(SearchSortOrder.EVENT_DEFAULT)

    val searchTerm =
        searchValueFlow
            .debounce(300)
            .distinctUntilChanged()
            .onEach(::updateDataSource)
            .stateIn(viewModelScope, SharingStarted.Eagerly, searchValue)

    val searchDataSourceState =
        SearchQueryState(
            searchQuery = MutableStateFlow(searchValue),
            account = account,
            searchRelays = account.searchRelayList.flow,
            indexerRelays = account.indexerRelayList.flow,
            followPlusAllMineWithSearchRelays = account.followPlusAllMineWithSearch.flow,
        )

    @Suppress("unused")
    val sourceWatcher =
        source
            .onEach { updateDataSource(searchValue) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, SearchSource.RELAYS)

    val listState: LazyListState = LazyListState(0, 0)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val directNip05Resolver: Flow<User?> =
        searchTerm
            .debounce(400)
            .mapLatest { term ->
                // NIP-05 resolution: user@domain or bare .bit domain
                val nip05 =
                    if (term.contains('@')) {
                        Nip05Id.parse(term)
                    } else if (term.endsWith(".bit", ignoreCase = true)) {
                        // Bare .bit domain → synthesize _@domain.bit
                        Nip05Id("_", term.lowercase())
                    } else {
                        null
                    }
                when {
                    nip05 != null -> {
                        runCatching {
                            nip05Client.get(nip05)?.let { info ->
                                val user = account.cache.checkGetOrCreateUser(info.pubkey)
                                if (user != null) {
                                    info.relays.forEach {
                                        it.normalizeRelayUrlOrNull()?.let { relay ->
                                            account.cache.relayHints.addKey(user.pubkey(), relay)
                                        }
                                    }
                                }
                                user
                            }
                        }.getOrNull()
                    }

                    term.startsWithAny(userUriPrefixes) -> {
                        runCatching {
                            Nip19Parser.uriToRoute(term)?.entity?.let { parsed ->
                                when (parsed) {
                                    is NSec -> {
                                        account.cache.getOrCreateUser(parsed.toPubKey().toHexKey())
                                    }

                                    is NPub -> {
                                        account.cache.getOrCreateUser(parsed.hex)
                                    }

                                    is NProfile -> {
                                        val user = account.cache.getOrCreateUser(parsed.hex)
                                        parsed.relay.forEach { relay ->
                                            account.cache.relayHints.addKey(user.pubkey(), relay)
                                        }
                                        user
                                    }

                                    else -> {
                                        null
                                    }
                                }
                            }
                        }.getOrNull()
                    }

                    term.length == 64 && Hex.isHex64(term) -> {
                        account.cache.getOrCreateUser(term)
                    }

                    else -> {
                        null
                    }
                }
            }.flowOn(Dispatchers.IO)

    /**
     * The routes the box opens on its own, which is now **only** an invite link.
     *
     * It used to auto-navigate on any nip19 code found anywhere in the text, which the token
     * language broke: `from:npub1…` contains an npub, so typing an author filter threw the reader
     * out of the search screen and onto that person's profile mid-query. A pasted code now
     * resolves into the results list instead (see [directEntity]) — still one tap away, but the
     * reader decides when to leave.
     *
     * Invite links survive because they cannot be typed by accident: both require a URL carrying
     * `/invite/`, which no token can produce, and both open a redeem flow rather than a profile
     * or a post.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val directRouteResolver: Flow<Route?> =
        searchTerm
            .mapLatest { term ->
                if (term.isBlank()) return@mapLatest null

                // A Concord invite link (…/invite/<naddr>#<fragment>) embeds a kind-33301 naddr that
                // Nip19Parser would otherwise extract and route to the generic event screen (which
                // can't render 33301). Detect the invite first and open the redeem flow — the whole
                // URL is carried so the fragment token survives.
                if (ConcordActions.parseInviteLink(term) != null) {
                    return@mapLatest Route.ConcordInvite(term)
                }

                // A pasted Buzz workspace invite (`…/invite/<token>`) opens the in-app join flow.
                if (term.contains("/invite/") && BuzzInviteLink.parse(term) != null) {
                    return@mapLatest Route.BuzzInvite(term)
                }

                null
            }.flowOn(Dispatchers.IO)

    /**
     * The nip19 entity the box holds *in its entirety*, or null.
     *
     * Whole-input only, and that is the point: `Nip19Parser` extracts a code from anywhere in a
     * string, so anything looser matches the npub inside `from:npub1…` and treats an author filter
     * as a request to open that person.
     */
    private fun directEntity(term: String): Entity? {
        val code = wholeInputNip19(term) ?: return null
        return runCatching { Nip19Parser.uriToRoute(code)?.entity }
            .onFailure { if (it is CancellationException) throw it }
            .getOrNull()
    }

    val searchResultsUsers =
        combine(
            searchValueFlow.debounce(100),
            invalidations.debounce(100),
            directNip05Resolver,
            scope,
            combine(followsOnly, account.kind3FollowList.flow) { only, follows ->
                if (only) follows.authorsPlusMe else null
            },
        ) { term, _, nip05Resolver, currentScope, follows ->
            if (currentScope == SearchScope.NOTES) return@combine emptyList<User>()

            if (nip05Resolver != null) {
                return@combine if (follows == null || nip05Resolver.pubkeyHex in follows) {
                    listOf(nip05Resolver)
                } else {
                    emptyList()
                }
            }

            // The leftover terms, not the whole box: a name search handed `#bitcoin` or
            // `from:npub1…` verbatim matches nobody, and since notes started honouring the
            // tokens, leaving people and channels on the raw text made one box mean two things.
            // A pasted npub/nprofile resolves to its owner even when the cache has never seen
            // them — this is what the auto-navigation used to do, minus the navigation.
            val direct =
                (directEntity(term) as? IPubKeyEntity)?.let {
                    LocalCache.consume(it)
                    LocalCache.getUserIfExists(it.hex) ?: LocalCache.getOrCreateUser(it.hex)
                }

            val nameTerm = plainTerms(term)
            val found =
                if (nameTerm.isBlank()) emptyList() else LocalCache.search.findUsersStartingWith(nameTerm, account)
            val users = (listOfNotNull(direct) + found).distinctBy { it.pubkeyHex }
            if (follows != null) users.filter { it.pubkeyHex in follows } else users
        }.flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, WhileSubscribed(5000), emptyList())

    val searchResultsNotes =
        combine(
            searchValueFlow.debounce(100),
            invalidations,
            scope,
            sortOrder,
            combine(followsOnly, account.kind3FollowList.flow) { only, follows ->
                if (only) follows.authorsPlusMe else null
            },
        ) { term, _, currentScope, order, follows ->
            if (currentScope == SearchScope.PEOPLE) return@combine emptyList()

            // The same filters the REQ carries, run against the cache — so `from:`, `to:`,
            // `since:`, `#t` and the rest narrow local results exactly as they narrow relay
            // results. A bech32 id typed in full is a lookup, not a search, and keeps its own
            // path through findNotesStartingWith.
            val parsed = QueryParser.parse(term)
            // A pasted note/nevent/naddr resolves even when the cache has never seen it —
            // what the auto-navigation used to do, minus the navigation.
            val direct =
                when (val entity = directEntity(term)) {
                    is NNote -> LocalCache.consume(entity).let { LocalCache.getOrCreateNote(entity.hex) }
                    is NEvent -> LocalCache.consume(entity).let { LocalCache.getOrCreateNote(entity.hex) }
                    is NAddress -> LocalCache.consume(entity).let { LocalCache.getOrCreateAddressableNote(entity.address()) }
                    else -> null
                }

            val raw =
                when {
                    parsed.isEmpty -> emptyList()
                    // An id, whole or half-typed, is a lookup rather than a search: it matches on
                    // `idHex`, which is not content and so nothing a filter's `search` can reach.
                    // Routed to the scan that knows how to resolve it — and only for text that
                    // could actually be one, so an ordinary query never pays for two scans.
                    looksLikeAnEventId(term) -> LocalCache.search.findNotesStartingWith(term, account.hiddenUsers)
                    else ->
                        LocalCache.search.findNotesMatching(
                            // The kind window the query named, so a `kind:` chip narrows the
                            // local results exactly as it narrows the relay's. Null asks every
                            // kind, which is what a query naming none means.
                            SearchFilterBuilder.build(parsed, parsed.kinds.takeIf { it.isNotEmpty() }?.toList(), limit = 200),
                            account.hiddenUsers,
                        )
                }
            val withDirect = (listOfNotNull(direct) + raw).distinctBy { it.idHex }
            val followed = if (follows != null) withDirect.filter { it.author?.pubkeyHex in follows } else withDirect
            // `-term` and the `kind:reply`/`kind:media` pseudo-kinds cannot be asked of a relay —
            // NIP-50 has no negation, and "is a reply" is a tag shape rather than an index — so
            // they are applied over the results instead. Desktop has always done this; this
            // screen never did, which left an exclusion the reader typed doing nothing at all.
            val filtered = followed.filter { note -> note.event?.let { SearchResultFilter.matches(it, parsed) } != false }

            when (order) {
                SearchSortOrder.POPULAR -> {
                    filtered.sortedWith(
                        compareByDescending<com.vitorpamplona.amethyst.commons.model.Note> { it.zapsAmount }
                            .thenByDescending { it.createdAt() ?: 0L },
                    )
                }

                SearchSortOrder.OLDEST -> {
                    filtered.sortedBy { it.createdAt() ?: 0L }
                }

                SearchSortOrder.RELEVANCE, SearchSortOrder.NEWEST -> {
                    filtered.sortedByDefaultFeedOrder()
                }

                else -> {
                    filtered.sortedByDefaultFeedOrder()
                }
            }
        }.flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, WhileSubscribed(5000), emptyList())

    val searchResultsPublicChatChannels =
        combine(
            searchValueFlow.debounce(100),
            invalidations,
            scope,
        ) { term, _, currentScope ->
            if (currentScope != SearchScope.ALL) emptyList() else LocalCache.search.findPublicChatChannelsStartingWith(plainTerms(term))
        }.flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, WhileSubscribed(5000), emptyList())

    val searchResultsEphemeralChannels =
        combine(
            searchValueFlow.debounce(100),
            invalidations,
            scope,
        ) { term, _, currentScope ->
            if (currentScope != SearchScope.ALL) emptyList() else LocalCache.search.findEphemeralChatChannelsStartingWith(plainTerms(term))
        }.flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, WhileSubscribed(5000), emptyList())

    val searchResultsLiveActivityChannels =
        combine(
            searchValueFlow.debounce(100),
            invalidations,
            scope,
        ) { term, _, currentScope ->
            if (currentScope != SearchScope.ALL) emptyList() else LocalCache.search.findLiveActivityChannelsStartingWith(plainTerms(term))
        }.flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, WhileSubscribed(5000), emptyList())

    val hashtagResults =
        combine(
            searchValueFlow.debounce(100),
            invalidations,
            scope,
        ) { term, _, currentScope ->
            if (currentScope == SearchScope.PEOPLE) emptyList() else findHashtags(term)
        }.flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, WhileSubscribed(5000), emptyList())

    val relayResults =
        combine(
            searchValueFlow.debounce(100),
            invalidations,
            scope,
        ) { term, _, currentScope ->
            if (currentScope != SearchScope.ALL) return@combine emptyList()
            if (term.length > 1) {
                val isTypingRelay = term.length > 7 && (term.startsWith("wss://") || term.startsWith("ws://"))
                val relayUrl =
                    if (isTypingRelay) {
                        runCatching { NormalizedRelayUrl(Rfc3986.normalize(term)) }.getOrNull()
                    } else {
                        null
                    }
                val lower = term.lowercase()

                val relays =
                    (
                        listOfNotNull(relayUrl) +
                            LocalCache.relayHints.relayDB.filter { _, relay -> relay.url.contains(lower) }
                    ).distinctBy { it.url }

                relays
                    .map { relaySetupInfoBuilder(it) }
                    .sortedByDescending { it.relayStat.receivedBytes }
                    .take(20)
            } else {
                emptyList()
            }
        }.flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, WhileSubscribed(5000), emptyList())

    override val isRefreshing = derivedStateOf { searchValue.isNotBlank() }

    /**
     * The single word a name search should be given — see [nameSearchTerms]. Not simply the
     * leftover text: a query that is nothing but `#bitcoin` leaves no leftovers, and handing the
     * finders an empty string means they answer with nobody rather than with the channel called
     * "Bitcoin" that the reader was plainly looking for.
     */
    private fun plainTerms(term: String): String = QueryParser.parse(term).nameSearchTerms()

    /**
     * Could this text name an event rather than describe one? A bech32 pointer, or a run of hex
     * long enough that it is nobody's search term.
     */
    private fun looksLikeAnEventId(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.contains(' ')) return false
        if (decodeEventIdAsHexOrNull(trimmed) != null) return true
        return trimmed.length >= 8 && trimmed.all { it in "0123456789abcdefABCDEF" }
    }

    override fun invalidateData(ignoreIfDoing: Boolean) {
        // force new query
        invalidations.update { it + 1 }
    }

    fun updateSearchValue(newValue: String) {
        searchValue = newValue
        searchValueFlow.tryEmit(newValue)
    }

    fun clear() = updateSearchValue("")

    suspend fun updateDataSource(searchTerm: String) {
        if (searchTerm.isBlank() || source.value == SearchSource.LOCAL) {
            searchDataSourceState.searchQuery.tryEmit("")
        } else {
            searchDataSourceState.searchQuery.tryEmit(searchTerm)
            listState.scrollToItem(0, 0)
        }
    }

    fun updateScope(newScope: SearchScope) {
        pickedScope.value = newScope
    }

    fun updateSource(newSource: SearchSource) {
        source.value = newSource
    }

    fun updateFollowsOnly(value: Boolean) {
        followsOnly.value = value
    }

    fun updateSortOrder(order: SearchSortOrder) {
        sortOrder.value = order
    }

    fun isSearchingFun() = searchValue.isNotBlank()

    class Factory(
        val account: Account,
        val nip05: INip05Client,
        val initialQuery: String? = null,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SearchBarViewModel(account, nip05, initialQuery) as T
    }
}
