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
import com.vitorpamplona.amethyst.commons.search.RenderableKinds
import com.vitorpamplona.amethyst.commons.search.SearchHistory
import com.vitorpamplona.amethyst.commons.search.SearchHistoryStorage
import com.vitorpamplona.amethyst.commons.search.SearchPipeline
import com.vitorpamplona.amethyst.commons.search.SearchResultKind
import com.vitorpamplona.amethyst.commons.search.SearchScope
import com.vitorpamplona.amethyst.commons.search.SearchSortOrder
import com.vitorpamplona.amethyst.commons.search.SearchSource
import com.vitorpamplona.amethyst.commons.search.SearchState
import com.vitorpamplona.amethyst.commons.search.wholeInputNip19
import com.vitorpamplona.amethyst.commons.ui.feeds.InvalidatableContent
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

@Stable
@OptIn(FlowPreview::class)
class SearchBarViewModel(
    val account: Account,
    val nip05Client: INip05Client,
    /** Where this device keeps what has been searched for; see [history]. */
    historyStorage: SearchHistoryStorage,
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

    /** A refresh the app asked for: the screen was composed, or the reader came back to it. */
    private val manualInvalidations = MutableStateFlow(0)

    /**
     * What is being searched, and everything about it that is not Android's: the text, its parse,
     * the two debounce windows, the scope, the sort orders. Shared with Desktop, which had grown
     * its own copy of all of it.
     *
     * What stays here is what a shared holder cannot own: a `LazyListState`, a `FocusRequester`,
     * invite-link routing, NIP-05 resolution, and the seven result flows — the *acquisition* of
     * results, which on Android is a cache scan and on Desktop is a relay callback.
     */
    val state = SearchState(viewModelScope, initialText = initialQuery.orEmpty())

    /**
     * When the results are worth recomputing.
     *
     * This used to be a counter that only the lifecycle touched, which meant the result lists were
     * computed once per keystroke and then frozen: the REQ went out, relays answered a few hundred
     * milliseconds later, `LocalCache` filled up — and nothing re-ran the scan, so what the reader
     * saw was whatever had already been cached when they stopped typing. Everything that arrived
     * because of their search only appeared if they typed another character or left the screen and
     * came back.
     *
     * The cache already publishes what it takes in, so the fix is to listen: a lifecycle refresh
     * and an arriving bundle are the same event to a result list. Merged and debounced once, so a
     * burst of relay traffic costs one rescan rather than one per bundle.
     */
    private val refreshes: StateFlow<Int> =
        merge(
            manualInvalidations,
            merge(
                account.cache.getEventStream().newEventBundles,
                account.cache.getEventStream().deletedEventBundles,
            ).sample(RESCAN_INTERVAL_MS),
        ).scan(0) { count, _ -> count + 1 }
            .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** True while a token picker is open under the field.
     *
     * The search screen pins its bars on this. A picker is a scrollable inside the *top bar*, and
     * [com.vitorpamplona.amethyst.commons.ui.layouts.DisappearingBarNestedScroll] moves the bars
     * on `consumed + available` — a sum that is conserved as a scroll walks up the nested-scroll
     * chain, so no connection under the picker can hide its scrolling from the scaffold. Scrolling
     * the list of kinds slid the whole chrome away with it. The scaffold has to be told instead.
     */
    val pickerOpen = MutableStateFlow(false)

    /**
     * What has been searched for on this device, and what has been kept.
     *
     * Desktop has had this since the advanced bar was written; Android never did, because the
     * code sat in a desktop object on `java.util.prefs`. Nothing about it was desktop-specific,
     * so the move to commons is the whole of the port.
     */
    val history = SearchHistory(historyStorage, viewModelScope)

    /** Records the query the reader actually ran — the Enter key, not every pause in typing. */
    fun remember() = history.remember(state.query)

    val queryAsksNothing get() = state.asksNothing
    val searchSettled get() = state.settled
    val scopePinnedToNotes get() = state.scopePinnedToNotes
    val scope get() = state.scope

    val source get() = state.source
    val followsOnly get() = state.followsOnly
    val sortOrder get() = state.eventSortOrder

    // EVERYTHING `updateDataSource` TOUCHES MUST BE DECLARED ABOVE THIS LINE.
    //
    // `searchTerm` and `sourceWatcher` below are both shared `Eagerly`, so they call
    // `updateDataSource` while the constructor is still running. Kotlin initialises properties in
    // declaration order, so anything that function reads from further down the class is still
    // null at that moment and the app dies opening search. It has happened twice now -- once on
    // `listState`, once on `searchDataSourceState` when a refactor moved it below -- so the two
    // fields it needs live here, above the collectors, and should stay here.
    val listState: LazyListState = LazyListState(0, 0)

    val searchDataSourceState =
        SearchQueryState(
            searchQuery = MutableStateFlow(searchValue),
            account = account,
            searchRelays = account.searchRelayList.flow,
            indexerRelays = account.indexerRelayList.flow,
            followPlusAllMineWithSearchRelays = account.followPlusAllMineWithSearch.flow,
        )

    val searchTerm =
        state.debouncedForRelays
            .map { it.text }
            .onEach(::updateDataSource)
            .stateIn(viewModelScope, SharingStarted.Eagerly, searchValue)

    @Suppress("unused")
    val sourceWatcher =
        source
            .onEach { updateDataSource(searchValue) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, SearchSource.RELAYS)

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
            state.debounced,
            refreshes,
            directNip05Resolver,
            scope,
            combine(followsOnly, account.kind3FollowList.flow) { only, follows ->
                if (only) follows.authorsPlusMe else null
            },
        ) { input, _, nip05Resolver, currentScope, follows ->
            if (!currentScope.shows(SearchResultKind.PEOPLE)) return@combine emptyList<User>()

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
                (directEntity(input.text) as? IPubKeyEntity)?.let {
                    LocalCache.consume(it)
                    LocalCache.getUserIfExists(it.hex) ?: LocalCache.getOrCreateUser(it.hex)
                }

            val nameTerm = input.nameTerms
            val found =
                if (nameTerm.isBlank()) emptyList() else LocalCache.search.findUsersStartingWith(nameTerm, account)
            val users = (listOfNotNull(direct) + found).distinctBy { it.pubkeyHex }
            if (follows != null) users.filter { it.pubkeyHex in follows } else users
        }.flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, WhileSubscribed(5000), emptyList())

    val searchResultsNotes =
        combine(
            state.debounced,
            refreshes,
            scope,
            sortOrder,
            combine(followsOnly, account.kind3FollowList.flow) { only, follows ->
                if (only) follows.authorsPlusMe else null
            },
        ) { input, _, currentScope, order, follows ->
            if (!currentScope.shows(SearchResultKind.NOTES)) return@combine emptyList()

            // The same filters the REQ carries, run against the cache — so `from:`, `to:`,
            // `since:`, `#t` and the rest narrow local results exactly as they narrow relay
            // results. A bech32 id typed in full is a lookup, not a search, and keeps its own
            // path through findNotesStartingWith.
            val parsed = input.query
            // A pasted note/nevent/naddr resolves even when the cache has never seen it —
            // what the auto-navigation used to do, minus the navigation.
            val direct =
                when (val entity = directEntity(input.text)) {
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
                    looksLikeAnEventId(input.text) -> LocalCache.findNotesStartingWith(input.text, account.hiddenUsers.flow.value)
                    else ->
                        // The same filters the REQ carries, over the same kind window. Built by
                        // the pipeline rather than here, so the cache is asked exactly what the
                        // relays are asked — the window was the last thing the two disagreed on,
                        // the local scan having had none at all and so matching kinds no relay was
                        // ever asked for. Asked flat rather than in RenderableKinds.GROUPS: the
                        // groups exist for a relay's per-filter cap, and a cache has none.
                        LocalCache.findNotesMatching(
                            SearchPipeline.filters(parsed, RenderableKinds.ALL, limit = 200),
                            account.hiddenUsers.flow.value,
                        )
                }
            val withDirect = (listOfNotNull(direct) + raw).distinctBy { it.idHex }
            val followed = if (follows != null) withDirect.filter { it.author?.pubkeyHex in follows } else withDirect

            // The post-filters and the ordering, in that order, from the one place that owns
            // both. Doing it by hand here is what let `-term` and Relevance quietly do nothing:
            // there was no sequence to be missing a step from.
            SearchPipeline.narrowAndOrder(
                items = followed,
                query = parsed,
                order = order,
                event = { it.event },
                zapTotal = { it.zapsAmount.toDouble() },
            )
        }.flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, WhileSubscribed(5000), emptyList())

    val searchResultsPublicChatChannels =
        combine(
            state.debounced,
            refreshes,
            scope,
        ) { input, _, currentScope ->
            if (!currentScope.shows(SearchResultKind.PUBLIC_CHATS)) emptyList() else LocalCache.findPublicChatChannelsStartingWith(input.nameTerms)
        }.flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, WhileSubscribed(5000), emptyList())

    val searchResultsEphemeralChannels =
        combine(
            state.debounced,
            refreshes,
            scope,
        ) { input, _, currentScope ->
            if (!currentScope.shows(SearchResultKind.EPHEMERAL_CHATS)) emptyList() else LocalCache.findEphemeralChatChannelsStartingWith(input.nameTerms)
        }.flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, WhileSubscribed(5000), emptyList())

    val searchResultsLiveActivityChannels =
        combine(
            state.debounced,
            refreshes,
            scope,
        ) { input, _, currentScope ->
            if (!currentScope.shows(SearchResultKind.LIVE_ACTIVITIES)) emptyList() else LocalCache.findLiveActivityChannelsStartingWith(input.nameTerms)
        }.flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, WhileSubscribed(5000), emptyList())

    val hashtagResults =
        combine(
            state.debounced,
            refreshes,
            scope,
        ) { input, _, currentScope ->
            if (!currentScope.shows(SearchResultKind.HASHTAGS)) emptyList() else findHashtags(input.text)
        }.flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, WhileSubscribed(5000), emptyList())

    val relayResults =
        combine(
            state.debounced,
            refreshes,
            scope,
        ) { input, _, currentScope ->
            if (!currentScope.shows(SearchResultKind.RELAYS)) return@combine emptyList()
            val term = input.text
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

    /** True when the box holds something to search for — which is not the same as searching. */
    val hasQuery = derivedStateOf { searchValue.isNotBlank() }

    /**
     * True while a search is actually under way.
     *
     * This is [InvalidatableContent]'s contract, and every other implementor uses it to mean "a
     * refresh is running". Search had it returning `searchValue.isNotBlank()` — "the box has
     * text" — so three call sites read a name that said one thing and meant another, and nothing
     * on screen could say whether results were still coming. Both meanings now exist under their
     * own names, and the field shows this one as a spinner.
     *
     * No EOSE from the search subscription reaches this screen, so "under way" is the same
     * heuristic the empty state already trusts: the grace window since the query last changed.
     */
    override val isRefreshing = derivedStateOf { searchValue.isNotBlank() && !state.settled.value }

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
        manualInvalidations.update { it + 1 }
    }

    fun updateSearchValue(newValue: String) {
        searchValue = newValue
        state.updateText(newValue)
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

    fun updateScope(newScope: SearchScope) = state.updateScope(newScope)

    fun updateSource(newSource: SearchSource) = state.updateSource(newSource)

    fun updateFollowsOnly(value: Boolean) = state.updateFollowsOnly(value)

    fun updateSortOrder(order: SearchSortOrder) = state.updateEventSortOrder(order)

    fun isSearchingFun() = searchValue.isNotBlank()

    companion object {
        /**
         * At most one cache-driven rescan per this long.
         *
         * Sampled rather than debounced, and that distinction is the whole of it: a debounce waits
         * for quiet, and a cache taking in a search's own results — plus whatever the rest of the
         * app is subscribed to — may not go quiet for seconds. The list would have stalled exactly
         * when it had the most to show. Sampling caps the cost instead, and guarantees the scan
         * runs while events are still arriving.
         *
         * Wider than [SearchState.LOCAL_DEBOUNCE_MS] because this one walks the cache without the
         * reader having asked for anything.
         */
        private const val RESCAN_INTERVAL_MS = 400L
    }

    class Factory(
        val account: Account,
        val nip05: INip05Client,
        val historyStorage: SearchHistoryStorage,
        val initialQuery: String? = null,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SearchBarViewModel(account, nip05, historyStorage, initialQuery) as T
    }
}
