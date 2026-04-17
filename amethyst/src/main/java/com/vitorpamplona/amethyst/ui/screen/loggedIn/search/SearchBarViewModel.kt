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
import com.vitorpamplona.amethyst.commons.search.SearchScope
import com.vitorpamplona.amethyst.commons.search.SearchSortOrder
import com.vitorpamplona.amethyst.commons.search.SearchSource
import com.vitorpamplona.amethyst.commons.ui.feeds.InvalidatableContent
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relayClient.searchCommand.SearchQueryState
import com.vitorpamplona.amethyst.ui.dal.DefaultFeedOrder
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.note.creators.userSuggestions.userUriPrefixes
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.common.relaySetupInfoBuilder
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrlOrNull
import com.vitorpamplona.quartz.nip05DnsIdentifiers.INip05Client
import com.vitorpamplona.quartz.nip05DnsIdentifiers.Nip05Id
import com.vitorpamplona.quartz.nip10Notes.content.findHashtags
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NNote
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip19Bech32.entities.NSec
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.Rfc3986
import com.vitorpamplona.quartz.utils.startsWithAny
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

@Stable
@OptIn(FlowPreview::class)
class SearchBarViewModel(
    val account: Account,
    val nip05Client: INip05Client,
) : ViewModel(),
    InvalidatableContent {
    val focusRequester = FocusRequester()
    var searchValue by mutableStateOf("")

    val invalidations = MutableStateFlow(0)
    val searchValueFlow = MutableStateFlow("")

    val scope = MutableStateFlow(SearchScope.ALL)
    val source = MutableStateFlow(SearchSource.RELAYS)
    val followsOnly = MutableStateFlow(false)
    val sortOrder = MutableStateFlow(SearchSortOrder.DEFAULT_EVENT)

    val searchTerm =
        searchValueFlow
            .debounce(300)
            .distinctUntilChanged()
            .onEach(::updateDataSource)
            .stateIn(viewModelScope, SharingStarted.Eagerly, searchValue)

    val searchDataSourceState = SearchQueryState(MutableStateFlow(searchValue), account)

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
                if (nip05 != null) {
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
                } else if (term.startsWithAny(userUriPrefixes)) {
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
                } else if (term.length == 64 && Hex.isHex64(term)) {
                    account.cache.getOrCreateUser(term)
                } else {
                    null
                }
            }.flowOn(Dispatchers.IO)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val directEventResolver: Flow<Route?> =
        searchTerm
            .debounce(200)
            .mapLatest { term ->
                if (term.isBlank()) return@mapLatest null
                runCatching {
                    when (val parsed = Nip19Parser.uriToRoute(term)?.entity) {
                        is NEvent -> Route.Note(parsed.hex)
                        is NNote -> Route.Note(parsed.hex)
                        is NAddress -> Route.Note(parsed.aTag())
                        else -> null
                    }
                }.getOrNull()
            }.flowOn(Dispatchers.IO)

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

            if (term.isBlank()) return@combine emptyList<User>()
            val users = LocalCache.findUsersStartingWith(term, account)
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

            val raw = LocalCache.findNotesStartingWith(term, account.hiddenUsers)
            val filtered = if (follows != null) raw.filter { it.author?.pubkeyHex in follows } else raw

            when (order) {
                SearchSortOrder.POPULAR ->
                    filtered.sortedWith(
                        compareByDescending<com.vitorpamplona.amethyst.model.Note> { it.zapsAmount }
                            .thenByDescending { it.createdAt() ?: 0L },
                    )

                SearchSortOrder.OLDEST ->
                    filtered.sortedBy { it.createdAt() ?: 0L }

                SearchSortOrder.RELEVANCE, SearchSortOrder.NEWEST -> filtered.sortedWith(DefaultFeedOrder)

                else -> filtered.sortedWith(DefaultFeedOrder)
            }
        }.flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, WhileSubscribed(5000), emptyList())

    val searchResultsPublicChatChannels =
        combine(
            searchValueFlow.debounce(100),
            invalidations,
            scope,
        ) { term, _, currentScope ->
            if (currentScope != SearchScope.ALL) emptyList() else LocalCache.findPublicChatChannelsStartingWith(term)
        }.flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, WhileSubscribed(5000), emptyList())

    val searchResultsEphemeralChannels =
        combine(
            searchValueFlow.debounce(100),
            invalidations,
            scope,
        ) { term, _, currentScope ->
            if (currentScope != SearchScope.ALL) emptyList() else LocalCache.findEphemeralChatChannelsStartingWith(term)
        }.flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, WhileSubscribed(5000), emptyList())

    val searchResultsLiveActivityChannels =
        combine(
            searchValueFlow.debounce(100),
            invalidations,
            scope,
        ) { term, _, currentScope ->
            if (currentScope != SearchScope.ALL) emptyList() else LocalCache.findLiveActivityChannelsStartingWith(term)
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
        scope.value = newScope
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
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SearchBarViewModel(account, nip05) as T
    }
}
