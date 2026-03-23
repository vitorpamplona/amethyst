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
package com.vitorpamplona.amethyst.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.chess.RelaySyncStatus
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.search.AdvancedSearchBarState
import com.vitorpamplona.amethyst.commons.search.QuerySerializer
import com.vitorpamplona.amethyst.commons.search.SavedSearch
import com.vitorpamplona.amethyst.commons.search.SearchQuery
import com.vitorpamplona.amethyst.commons.search.SearchResult
import com.vitorpamplona.amethyst.commons.search.SearchResultFilter
import com.vitorpamplona.amethyst.commons.search.parseSearchInput
import com.vitorpamplona.amethyst.desktop.SearchHistoryStore
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.amethyst.desktop.network.DesktopRelayConnectionManager
import com.vitorpamplona.amethyst.desktop.subscriptions.DesktopRelaySubscriptionsCoordinator
import com.vitorpamplona.amethyst.desktop.subscriptions.SearchFilterFactory
import com.vitorpamplona.amethyst.desktop.subscriptions.SubscriptionConfig
import com.vitorpamplona.amethyst.desktop.subscriptions.createMetadataSubscription
import com.vitorpamplona.amethyst.desktop.subscriptions.createSearchPeopleSubscription
import com.vitorpamplona.amethyst.desktop.subscriptions.generateSubId
import com.vitorpamplona.amethyst.desktop.subscriptions.rememberSubscription
import com.vitorpamplona.amethyst.desktop.ui.search.AdvancedSearchPanel
import com.vitorpamplona.amethyst.desktop.ui.search.SearchResultsList
import com.vitorpamplona.amethyst.desktop.ui.search.SearchSyncBanner
import com.vitorpamplona.amethyst.desktop.service.namecoin.DesktopNamecoinNameService
import com.vitorpamplona.amethyst.desktop.service.namecoin.LocalNamecoinPreferences
import com.vitorpamplona.amethyst.desktop.service.namecoin.LocalNamecoinService
import com.vitorpamplona.amethyst.desktop.service.namecoin.NamecoinResolveState
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinNameResolver
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinResolveOutcome
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull
import kotlinx.coroutines.flow.mapLatest

@Composable
fun SearchScreen(
    localCache: DesktopLocalCache,
    relayManager: DesktopRelayConnectionManager,
    subscriptionsCoordinator: DesktopRelaySubscriptionsCoordinator? = null,
    initialQuery: String = "",
    onNavigateToProfile: (String) -> Unit,
    onNavigateToThread: (String) -> Unit,
    onNavigateToHashtag: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val state = remember { AdvancedSearchBarState(scope) }
    val focusRequester = remember { FocusRequester() }

    // Pre-fill initial query
    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotBlank()) {
            state.updateFromText(initialQuery)
        }
    }

    val connectedRelays by relayManager.connectedRelays.collectAsState()
    val relayStatuses by relayManager.relayStatuses.collectAsState()
    val allRelayUrls = remember(relayStatuses) { relayStatuses.keys }
    val displayText by state.displayText.collectAsState()
    // Track TextFieldValue locally to preserve cursor position
    var textFieldValue by remember { mutableStateOf(TextFieldValue(displayText)) }
    // Sync from flow only when text changes externally (form-driven updates)
    LaunchedEffect(displayText) {
        if (textFieldValue.text != displayText) {
            textFieldValue = TextFieldValue(text = displayText, selection = TextRange(displayText.length))
        }
    }
    val query by state.query.collectAsState()
    val debouncedQuery by state.debouncedQuery.collectAsState()
    val panelExpanded by state.panelExpanded.collectAsState()
    val isSearching by state.isSearching.collectAsState()
    val peopleResults by state.peopleResults.collectAsState()
    val noteResults by state.noteResults.collectAsState()
    val relayStates by state.relayStates.collectAsState()

    // Bech32 parsing (immediate, no debounce)
    val bech32Results = remember(displayText) { parseSearchInput(displayText) }

    // Namecoin resolution
    val namecoinService = LocalNamecoinService.current
    val namecoinPrefs = LocalNamecoinPreferences.current
    val namecoinEnabled = namecoinPrefs?.settings?.collectAsState()?.value?.enabled ?: false
    val isNamecoinQuery = remember(displayText) {
        displayText.isNotBlank() && NamecoinNameResolver.isNamecoinIdentifier(displayText.trim())
    }

    var namecoinState by remember { mutableStateOf<NamecoinResolveState?>(null) }

    // Resolve Namecoin identifiers with cancellation of stale lookups
    LaunchedEffect(displayText, namecoinEnabled) {
        if (!namecoinEnabled || !isNamecoinQuery || namecoinService == null) {
            namecoinState = null
            return@LaunchedEffect
        }
        namecoinState = NamecoinResolveState.Loading
        try {
            val result = namecoinService.resolve(displayText.trim())
            namecoinState = if (result != null) {
                NamecoinResolveState.Resolved(result)
            } else {
                NamecoinResolveState.NotFound
            }
        } catch (e: Exception) {
            namecoinState = NamecoinResolveState.Error(e.message ?: "Resolution failed")
        }
    }

    // Skip people search when query specifies kinds that don't include profile (kind 0)
    val shouldSearchPeople =
        (debouncedQuery.kinds.isEmpty() && debouncedQuery.pseudoKinds.isEmpty()) ||
            debouncedQuery.kinds.contains(MetadataEvent.KIND)

    // Clear results and start loading when query changes
    LaunchedEffect(debouncedQuery) {
        if (!debouncedQuery.isEmpty && bech32Results.isEmpty()) {
            state.clearResults()
            state.initRelayStates(allRelayUrls)
            if (shouldSearchPeople) {
                state.startSearching("people-search")
            }
            state.startSearching("adv-search")
            // Timeout relays that silently ignore NIP-50 (e.g. strfry)
            kotlinx.coroutines.delay(10_000L)
            state.timeoutWaitingRelays()
        }
    }

    // NIP-50 people search subscription (use allRelayUrls — openReqSubscription will connect)
    rememberSubscription(connectedRelays, debouncedQuery, relayManager = relayManager) {
        if (allRelayUrls.isEmpty() || debouncedQuery.isEmpty) {
            return@rememberSubscription null
        }
        if (bech32Results.isNotEmpty()) return@rememberSubscription null
        if (!shouldSearchPeople) {
            state.stopSearching("people-search")
            return@rememberSubscription null
        }

        createSearchPeopleSubscription(
            relays = allRelayUrls,
            searchQuery =
                debouncedQuery.text.ifBlank {
                    QuerySerializer.serialize(debouncedQuery)
                },
            limit = 20,
            onEvent = { event, _, relay, _ ->
                if (state.trackRelayEvent(relay.url, event.id)) {
                    if (event is MetadataEvent) {
                        localCache.consumeMetadata(event)
                        @Suppress("UNCHECKED_CAST")
                        val user = localCache.getUserIfExists(event.pubKey) as? User
                        if (user != null) {
                            state.addPeopleResult(user)
                        }
                    }
                }
            },
            onEose = { relay, _ ->
                state.updateRelayState(relay.url, RelaySyncStatus.EOSE_RECEIVED)
                state.stopSearching("people-search")
            },
            onClosed = { relay, _, _ ->
                state.updateRelayState(relay.url, RelaySyncStatus.FAILED)
                state.stopSearching("people-search")
            },
        )
    }

    // NIP-50 advanced note search subscription (use allRelayUrls)
    rememberSubscription(connectedRelays, debouncedQuery, relayManager = relayManager) {
        if (allRelayUrls.isEmpty() || debouncedQuery.isEmpty) {
            return@rememberSubscription null
        }
        if (bech32Results.isNotEmpty()) return@rememberSubscription null

        val filters = SearchFilterFactory.createFilters(debouncedQuery)
        if (filters.isEmpty()) return@rememberSubscription null

        SubscriptionConfig(
            subId = generateSubId("adv-search"),
            filters = filters,
            relays = allRelayUrls,
            onEvent = { event, _, relay, _ ->
                if (event.kind == MetadataEvent.KIND) return@SubscriptionConfig
                if (state.trackRelayEvent(relay.url, event.id)) {
                    val filtered = SearchResultFilter.filter(listOf(event), debouncedQuery)
                    if (filtered.isNotEmpty()) {
                        state.addNoteResults(filtered)
                    }
                }
            },
            onEose = { relay, _ ->
                state.updateRelayState(relay.url, RelaySyncStatus.EOSE_RECEIVED)
                state.stopSearching("adv-search")
            },
            onClosed = { relay, _, _ ->
                state.updateRelayState(relay.url, RelaySyncStatus.FAILED)
                state.stopSearching("adv-search")
            },
        )
    }

    // Metadata subscription for bech32 pubkey lookups
    rememberSubscription(connectedRelays, displayText, relayManager = relayManager) {
        if (connectedRelays.isEmpty() || displayText.length < 2) {
            return@rememberSubscription null
        }
        val pubkeyHex = decodePublicKeyAsHexOrNull(displayText)
        if (pubkeyHex != null) {
            createMetadataSubscription(
                relays = connectedRelays,
                pubKeyHex = pubkeyHex,
                onEvent = { event, _, _, _ ->
                    if (event is MetadataEvent) {
                        localCache.consumeMetadata(event)
                    }
                },
            )
        } else {
            null
        }
    }

    // Save to history when search completes (snapshotFlow avoids LaunchedEffect race)
    LaunchedEffect(Unit) {
        snapshotFlow { isSearching to debouncedQuery }
            .collect { (searching, query) ->
                if (!searching && !query.isEmpty) {
                    SearchHistoryStore.addToHistory(query)
                }
            }
    }

    // History state
    val historyItems by SearchHistoryStore.history.collectAsState()
    val savedSearches by SearchHistoryStore.savedSearches.collectAsState()

    // Auto-focus
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.Escape -> {
                            if (panelExpanded) {
                                state.togglePanel()
                            } else if (displayText.isNotEmpty()) {
                                state.clearSearch()
                            }
                            true
                        }

                        else -> {
                            false
                        }
                    }
                },
    ) {
        // Progress bar at very top
        AnimatedVisibility(
            visible = isSearching,
            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
        ) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }

        // Relay status banner
        SearchSyncBanner(
            relayStates = relayStates,
            isSearching = isSearching,
        )

        // Title row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Search",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                "${localCache.userCount()} users cached",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(16.dp))

        // Search bar with advanced toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = textFieldValue,
                onValueChange = {
                    textFieldValue = it
                    state.updateFromText(it.text)
                },
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
                placeholder = { Text("Search notes, people, tags... or use operators") },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                trailingIcon = {
                    if (displayText.isNotEmpty()) {
                        IconButton(onClick = { state.clearSearch() }) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )
            IconButton(onClick = { state.togglePanel() }) {
                Icon(
                    Icons.Default.Tune,
                    contentDescription = "Advanced Search",
                    tint =
                        if (panelExpanded) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
        }

        // Expandable advanced panel
        AnimatedVisibility(
            visible = panelExpanded,
            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
        ) {
            AdvancedSearchPanel(
                query = query,
                onKindsChanged = { state.updateKinds(it) },
                onPseudoKindsChanged = { state.updatePseudoKinds(it) },
                onAuthorAdded = { state.addAuthor(it) },
                onAuthorRemoved = { state.removeAuthor(it) },
                onDateRangeChanged = { since, until -> state.updateDateRange(since, until) },
                onHashtagAdded = { state.addHashtag(it) },
                onHashtagRemoved = { state.removeHashtag(it) },
                onExcludeAdded = { state.addExcludeTerm(it) },
                onExcludeRemoved = { state.removeExcludeTerm(it) },
                onLanguageChanged = { state.updateLanguage(it) },
                onClear = { state.clearSearch() },
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Spacer(Modifier.height(16.dp))

        // Results
        // Namecoin results (shown before everything else)
        if (isNamecoinQuery && namecoinState != null) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Namecoin lookup",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
                when (val state = namecoinState) {
                    is NamecoinResolveState.Loading -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            LinearProgressIndicator(modifier = Modifier.width(120.dp))
                            Text(
                                "Resolving ${displayText.trim()}...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    is NamecoinResolveState.Resolved -> {
                        SearchResultCard(
                            result = SearchResult.UserResult(
                                pubKeyHex = state.result.pubkey,
                                displayId = "${state.result.namecoinName} → ${state.result.pubkey.take(12)}...",
                            ),
                            onNavigateToProfile = onNavigateToProfile,
                            onNavigateToThread = onNavigateToThread,
                            onNavigateToHashtag = onNavigateToHashtag,
                        )
                        if (state.result.relays.isNotEmpty()) {
                            Text(
                                "Relays: ${state.result.relays.joinToString(", ")}",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                    is NamecoinResolveState.NotFound -> {
                        Text(
                            "Name not found on Namecoin blockchain",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    is NamecoinResolveState.Error -> {
                        Text(
                            "Resolution error: ${state.message}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    null -> {}
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        val hasAnyResults =
            bech32Results.isNotEmpty() || peopleResults.isNotEmpty() || noteResults.isNotEmpty() ||
                (namecoinState is NamecoinResolveState.Resolved)

        if (bech32Results.isNotEmpty()) {
            // Show bech32 results (exact lookup)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Direct lookup",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
                bech32Results.forEach { result ->
                    SearchResultCard(
                        result = result,
                        onNavigateToProfile = onNavigateToProfile,
                        onNavigateToThread = onNavigateToThread,
                        onNavigateToHashtag = onNavigateToHashtag,
                    )
                }
            }
        } else if (hasAnyResults) {
            SearchResultsList(
                state = state,
                onNavigateToProfile = onNavigateToProfile,
                onNavigateToThread = onNavigateToThread,
                localCache = localCache,
            )
        } else if (!debouncedQuery.isEmpty && !isSearching) {
            Text(
                "No results found. Try broader terms or fewer filters.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        } else if (!isSearching) {
            // Empty state: show history + saved searches + operator hints
            SearchEmptyState(
                historyItems = historyItems,
                savedSearches = savedSearches,
                onLoadQuery = { query -> state.updateFromText(QuerySerializer.serialize(query)) },
                onDeleteSaved = { id -> SearchHistoryStore.deleteSavedSearch(id) },
                onClearHistory = { SearchHistoryStore.clearHistory() },
            )
        }
    }
}

@Composable
private fun SearchEmptyState(
    historyItems: List<SearchQuery>,
    savedSearches: List<SavedSearch>,
    onLoadQuery: (SearchQuery) -> Unit,
    onDeleteSaved: (String) -> Unit,
    onClearHistory: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Saved searches
        if (savedSearches.isNotEmpty()) {
            item {
                Text(
                    "Saved Searches",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            items(savedSearches, key = { "saved-${it.id}" }) { saved ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { onLoadQuery(saved.query) }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            saved.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            QuerySerializer.serialize(saved.query),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    IconButton(onClick = { onDeleteSaved(saved.id) }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        }

        // Recent history
        if (historyItems.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Recent",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onClearHistory) {
                        Text("Clear", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            items(historyItems.take(10), key = { "history-${QuerySerializer.serialize(it)}" }) { query ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { onLoadQuery(query) }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        QuerySerializer.serialize(query),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        }

        // Operator hints
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = if (historyItems.isEmpty() && savedSearches.isEmpty()) 32.dp else 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Search operators",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
            }
        }
        item { SearchHint("from:npub1...", "Filter by author") }
        item { SearchHint("kind:article", "Long-form content") }
        item { SearchHint("since:2025-01", "After January 2025") }
        item { SearchHint("#bitcoin", "Hashtag search") }
        item { SearchHint("\"exact phrase\"", "Exact match") }
        item { SearchHint("bitcoin OR nostr", "Either term") }
        item { SearchHint("-spam", "Exclude term") }
        item { SearchHint("lang:en", "Language filter") }
    }
}

@Composable
private fun SearchHint(
    example: String,
    description: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(0.6f),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            example,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(16.dp))
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SearchResultCard(
    result: SearchResult,
    onNavigateToProfile: (String) -> Unit,
    onNavigateToThread: (String) -> Unit,
    onNavigateToHashtag: (String) -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable {
                    when (result) {
                        is SearchResult.UserResult -> onNavigateToProfile(result.pubKeyHex)
                        is SearchResult.NoteResult -> onNavigateToThread(result.noteIdHex)
                        is SearchResult.AddressResult -> onNavigateToThread("${result.kind}:${result.pubKeyHex}:${result.dTag}")
                        is SearchResult.HashtagResult -> onNavigateToHashtag(result.hashtag)
                    }
                },
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector =
                    when (result) {
                        is SearchResult.UserResult -> Icons.Default.Person
                        is SearchResult.NoteResult -> Icons.Default.Description
                        is SearchResult.AddressResult -> Icons.Default.Description
                        is SearchResult.HashtagResult -> Icons.Default.Tag
                    },
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary,
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    when (result) {
                        is SearchResult.UserResult -> "User Profile"
                        is SearchResult.NoteResult -> "Note"
                        is SearchResult.AddressResult -> "Event (kind ${result.kind})"
                        is SearchResult.HashtagResult -> "#${result.hashtag}"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    when (result) {
                        is SearchResult.UserResult -> result.displayId
                        is SearchResult.NoteResult -> result.displayId
                        is SearchResult.AddressResult -> result.displayId
                        is SearchResult.HashtagResult -> "Search posts with this hashtag"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = if (result is SearchResult.HashtagResult) null else FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Navigate",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
