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
package com.vitorpamplona.amethyst.commons.viewmodels

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.commons.model.emphChat.EphemeralChatChannel
import com.vitorpamplona.amethyst.commons.model.nip28PublicChats.PublicChatChannel
import com.vitorpamplona.amethyst.commons.model.nip53LiveActivities.LiveActivitiesChannel
import com.vitorpamplona.amethyst.commons.search.SearchResult
import com.vitorpamplona.amethyst.commons.search.parseSearchInput
import com.vitorpamplona.amethyst.commons.ui.feeds.DefaultFeedOrder
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrlOrNull
import com.vitorpamplona.quartz.nip05DnsIdentifiers.INip05Client
import com.vitorpamplona.quartz.nip05DnsIdentifiers.Nip05Id
import com.vitorpamplona.quartz.nip10Notes.content.findHashtags
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip19Bech32.entities.NSec
import com.vitorpamplona.quartz.utils.DualCase
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.startsWithAny
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * User URI prefixes for Bech32 user lookup during search.
 * Matches npub, nprofile, nostr:npub, nostr:nprofile.
 */
private val userUriPrefixes =
    listOf(
        DualCase("npub"),
        DualCase("nprofile"),
        DualCase("nostr:npub"),
        DualCase("nostr:nprofile"),
    )

/**
 * State holder for search bar functionality.
 * Shared between Android and Desktop search screens.
 *
 * Handles:
 * - Search text input with debounce
 * - Bech32/hex parsing
 * - Local cache search (users, notes, channels, hashtags)
 * - NIP-05 DNS identifier resolution
 * - Relay search results aggregation
 * - Invalidation for cache refresh
 *
 * Platform-specific concerns (relay subscriptions, navigation, UI state) remain in the UI layer.
 */
@OptIn(FlowPreview::class)
class SearchBarState(
    private val cache: ICacheProvider,
    private val scope: CoroutineScope,
    private val nip05Client: INip05Client? = null,
    private val debounceMs: Long = 300L,
) {
    private val _searchText = MutableStateFlow("")
    val searchText: StateFlow<String> = _searchText.asStateFlow()

    /**
     * Invalidation counter — increment to force re-evaluation of all search results.
     */
    val invalidations = MutableStateFlow(0)

    private val _bech32Results = MutableStateFlow<List<SearchResult>>(emptyList())
    val bech32Results: StateFlow<List<SearchResult>> = _bech32Results.asStateFlow()

    private val _cachedUserResults = MutableStateFlow<List<User>>(emptyList())
    val cachedUserResults: StateFlow<List<User>> = _cachedUserResults.asStateFlow()

    private val _relaySearchResults = MutableStateFlow<List<User>>(emptyList())
    val relaySearchResults: StateFlow<List<User>> = _relaySearchResults.asStateFlow()

    private val _isSearchingRelays = MutableStateFlow(false)
    val isSearchingRelays: StateFlow<Boolean> = _isSearchingRelays.asStateFlow()

    /**
     * Debounced search term, used for triggering relay search subscriptions.
     */
    val debouncedSearchTerm: StateFlow<String> =
        _searchText
            .debounce(debounceMs)
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.Eagerly, "")

    /**
     * NIP-05 resolution: resolves user@domain and .bit domains, as well as
     * Bech32 user URIs (npub, nprofile, nsec) and hex pubkeys.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val directNip05Resolver: Flow<User?> =
        debouncedSearchTerm
            .debounce(100)
            .mapLatest { term ->
                resolveDirectUser(term)
            }.flowOn(Dispatchers.Default)

    /**
     * User search results combining NIP-05 resolution with local cache search.
     */
    val searchResultsUsers: StateFlow<List<User>> =
        combine(
            _searchText.debounce(100),
            invalidations.debounce(100),
            directNip05Resolver,
        ) { term, _, nip05Resolver ->
            if (nip05Resolver != null) {
                return@combine listOf(nip05Resolver)
            }

            if (term.isNotBlank()) {
                cache.findUsersStartingWith(term)
            } else {
                emptyList()
            }
        }.flowOn(Dispatchers.Default)
            .stateIn(scope, WhileSubscribed(5000), emptyList())

    /**
     * Note search results from local cache.
     */
    val searchResultsNotes: StateFlow<List<Note>> =
        combine(
            _searchText.debounce(100),
            invalidations,
        ) { term, _ ->
            cache
                .findNotesStartingWith(term)
                .sortedWith(DefaultFeedOrder)
        }.flowOn(Dispatchers.Default)
            .stateIn(scope, WhileSubscribed(5000), emptyList())

    /**
     * Public chat channel search results from local cache.
     */
    val searchResultsPublicChatChannels: StateFlow<List<PublicChatChannel>> =
        combine(
            _searchText.debounce(100),
            invalidations,
        ) { term, _ ->
            cache.findPublicChatChannelsStartingWith(term)
        }.flowOn(Dispatchers.Default)
            .stateIn(scope, WhileSubscribed(5000), emptyList())

    /**
     * Ephemeral chat channel search results from local cache.
     */
    val searchResultsEphemeralChannels: StateFlow<List<EphemeralChatChannel>> =
        combine(
            _searchText.debounce(100),
            invalidations,
        ) { term, _ ->
            cache.findEphemeralChatChannelsStartingWith(term)
        }.flowOn(Dispatchers.Default)
            .stateIn(scope, WhileSubscribed(5000), emptyList())

    /**
     * Live activity channel search results from local cache.
     */
    val searchResultsLiveActivityChannels: StateFlow<List<LiveActivitiesChannel>> =
        combine(
            _searchText.debounce(100),
            invalidations,
        ) { term, _ ->
            cache.findLiveActivityChannelsStartingWith(term)
        }.flowOn(Dispatchers.Default)
            .stateIn(scope, WhileSubscribed(5000), emptyList())

    /**
     * Hashtag search results extracted from search text.
     */
    val hashtagResults: StateFlow<List<String>> =
        combine(
            _searchText.debounce(100),
            invalidations,
        ) { term, _ ->
            findHashtags(term)
        }.flowOn(Dispatchers.Default)
            .stateIn(scope, WhileSubscribed(5000), emptyList())

    val hasResults: Boolean
        get() =
            _bech32Results.value.isNotEmpty() ||
                _cachedUserResults.value.isNotEmpty() ||
                _relaySearchResults.value.isNotEmpty()

    val shouldSearchRelays: Boolean
        get() =
            _searchText.value.length >= 2 &&
                _bech32Results.value.isEmpty() &&
                _cachedUserResults.value.size < 5

    val isSearching: Boolean
        get() = _searchText.value.isNotBlank()

    fun updateSearchText(text: String) {
        _searchText.value = text
        _relaySearchResults.value = emptyList()
        _isSearchingRelays.value = false

        // Parse Bech32/hex immediately (no debounce)
        _bech32Results.value = parseSearchInput(text)

        // Clear cached results if query too short or is bech32
        if (text.length < 2 || _bech32Results.value.isNotEmpty()) {
            _cachedUserResults.value = emptyList()
        }
    }

    fun clearSearch() {
        updateSearchText("")
    }

    /**
     * Force re-evaluation of all search results.
     */
    fun invalidateData() {
        invalidations.update { it + 1 }
    }

    fun startRelaySearch() {
        _isSearchingRelays.value = true
    }

    fun endRelaySearch() {
        _isSearchingRelays.value = false
    }

    fun addRelaySearchResult(user: User) {
        if (!_relaySearchResults.value.any { it.pubkeyHex == user.pubkeyHex }) {
            _relaySearchResults.value = _relaySearchResults.value + user
        }
    }

    /**
     * Resolves a search term to a User via NIP-05, Bech32, or hex lookup.
     */
    private suspend fun resolveDirectUser(term: String): User? {
        if (term.isBlank()) return null

        // NIP-05 resolution: user@domain or bare .bit domain
        val nip05 =
            if (term.contains('@')) {
                Nip05Id.parse(term)
            } else if (term.endsWith(".bit", ignoreCase = true)) {
                Nip05Id("_", term.lowercase())
            } else {
                null
            }

        val client = nip05Client
        if (nip05 != null && client != null) {
            return runCatching {
                client.get(nip05)?.let { info ->
                    val user = cache.getOrCreateUser(info.pubkey)
                    if (user != null) {
                        info.relays.forEach {
                            it.normalizeRelayUrlOrNull()?.let { relay ->
                                // Relay hint storage is platform-specific;
                                // the caller can hook into directNip05Resolver to handle this
                            }
                        }
                    }
                    user
                }
            }.getOrNull()
        }

        // Bech32 user URI parsing
        if (term.startsWithAny(userUriPrefixes)) {
            return runCatching {
                Nip19Parser.uriToRoute(term)?.entity?.let { parsed ->
                    when (parsed) {
                        is NSec -> {
                            cache.getOrCreateUser(parsed.toPubKeyHex())
                        }

                        is NPub -> {
                            cache.getOrCreateUser(parsed.hex)
                        }

                        is NProfile -> {
                            val user = cache.getOrCreateUser(parsed.hex)
                            // Relay hints from nprofile are available in parsed.relay
                            user
                        }

                        else -> {
                            null
                        }
                    }
                }
            }.getOrNull()
        }

        // Hex pubkey
        if (term.length == 64 && Hex.isHex64(term)) {
            return cache.getOrCreateUser(term)
        }

        return null
    }
}
