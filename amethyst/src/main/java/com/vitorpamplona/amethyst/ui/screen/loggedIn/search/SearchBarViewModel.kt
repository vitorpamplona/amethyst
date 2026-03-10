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
import com.vitorpamplona.amethyst.commons.ui.feeds.InvalidatableContent
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relayClient.searchCommand.SearchQueryState
import com.vitorpamplona.amethyst.ui.dal.DefaultFeedOrder
import com.vitorpamplona.amethyst.ui.note.creators.userSuggestions.userUriPrefixes
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.common.relaySetupInfoBuilder
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrlOrNull
import com.vitorpamplona.quartz.nip05DnsIdentifiers.INip05Client
import com.vitorpamplona.quartz.nip05DnsIdentifiers.Nip05Id
import com.vitorpamplona.quartz.nip10Notes.content.findHashtags
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
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

    val searchTerm =
        searchValueFlow
            .debounce(300)
            .distinctUntilChanged()
            .onEach(::updateDataSource)
            .stateIn(viewModelScope, SharingStarted.Eagerly, searchValue)

    val searchDataSourceState = SearchQueryState(MutableStateFlow(searchValue), account)

    val listState: LazyListState = LazyListState(0, 0)

    val directNip05Resolver: Flow<User?> =
        searchTerm
            .debounce(400)
            .mapLatest { term ->
                if (term.contains('@')) {
                    runCatching {
                        Nip05Id.parse(term)?.let { nip05 ->
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

    val searchResultsUsers =
        combine(
            searchValueFlow.debounce(100),
            invalidations.debounce(100),
            directNip05Resolver,
        ) { term, version, nip05Resolver ->
            if (nip05Resolver != null) {
                return@combine listOf(nip05Resolver)
            }

            if (term.isNotBlank()) {
                LocalCache.findUsersStartingWith(term, account)
            } else {
                emptyList()
            }
        }.flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, WhileSubscribed(5000), emptyList())

    val searchResultsNotes =
        combine(
            searchValueFlow.debounce(100),
            invalidations,
        ) { term, version ->
            LocalCache
                .findNotesStartingWith(term, account.hiddenUsers)
                .sortedWith(DefaultFeedOrder)
        }.flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, WhileSubscribed(5000), emptyList())

    val searchResultsPublicChatChannels =
        combine(
            searchValueFlow.debounce(100),
            invalidations,
        ) { term, version ->
            LocalCache.findPublicChatChannelsStartingWith(term)
        }.flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, WhileSubscribed(5000), emptyList())

    val searchResultsEphemeralChannels =
        combine(
            searchValueFlow.debounce(100),
            invalidations,
        ) { term, version ->
            LocalCache.findEphemeralChatChannelsStartingWith(term)
        }.flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, WhileSubscribed(5000), emptyList())

    val searchResultsLiveActivityChannels =
        combine(
            searchValueFlow.debounce(100),
            invalidations,
        ) { term, version ->
            LocalCache.findLiveActivityChannelsStartingWith(term)
        }.flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, WhileSubscribed(5000), emptyList())

    val hashtagResults =
        combine(
            searchValueFlow.debounce(100),
            invalidations,
        ) { term, version ->
            findHashtags(term)
        }.flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, WhileSubscribed(5000), emptyList())

    val relayResults =
        combine(
            searchValueFlow.debounce(100),
            invalidations,
        ) { term, version ->
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
                    listOfNotNull(relayUrl) +
                        LocalCache.relayHints.relayDB.filter { _, relay -> relay.url.contains(lower) }

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
        if (searchTerm.isBlank()) {
            searchDataSourceState.searchQuery.tryEmit("")
        } else {
            searchDataSourceState.searchQuery.tryEmit(searchTerm)
            listState.scrollToItem(0, 0)
        }
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
