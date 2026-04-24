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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableStateListOf
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
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.search.AdvancedSearchBarState
import com.vitorpamplona.amethyst.commons.search.QuerySerializer
import com.vitorpamplona.amethyst.commons.search.SavedSearch
import com.vitorpamplona.amethyst.commons.search.SearchQuery
import com.vitorpamplona.amethyst.commons.search.SearchResult
import com.vitorpamplona.amethyst.commons.search.SearchResultFilter
import com.vitorpamplona.amethyst.commons.search.parseSearchInput
import com.vitorpamplona.amethyst.desktop.SearchHistoryStore
import com.vitorpamplona.amethyst.desktop.account.AccountState
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.amethyst.desktop.network.DesktopRelayConnectionManager
import com.vitorpamplona.amethyst.desktop.subscriptions.DesktopRelaySubscriptionsCoordinator
import com.vitorpamplona.amethyst.desktop.subscriptions.SearchFilterFactory
import com.vitorpamplona.amethyst.desktop.subscriptions.SubscriptionConfig
import com.vitorpamplona.amethyst.desktop.subscriptions.createMetadataSubscription
import com.vitorpamplona.amethyst.desktop.subscriptions.createSearchPeopleSubscription
import com.vitorpamplona.amethyst.desktop.subscriptions.generateSubId
import com.vitorpamplona.amethyst.desktop.subscriptions.rememberSubscription
import com.vitorpamplona.amethyst.desktop.ui.relay.LocalAccountRelays
import com.vitorpamplona.amethyst.desktop.ui.relay.LocalRelayCategories
import com.vitorpamplona.amethyst.desktop.ui.relay.SearchRelayEditor
import com.vitorpamplona.amethyst.desktop.ui.search.AdvancedSearchPanel
import com.vitorpamplona.amethyst.desktop.ui.search.SearchResultsList
import com.vitorpamplona.amethyst.desktop.ui.search.SearchSyncBanner
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull

@Composable
fun SearchScreen(
    localCache: DesktopLocalCache,
    relayManager: DesktopRelayConnectionManager,
    subscriptionsCoordinator: DesktopRelaySubscriptionsCoordinator? = null,
    account: AccountState.LoggedIn? = null,
    initialQuery: String = "",
    onNavigateToProfile: (String) -> Unit,
    onNavigateToThread: (String) -> Unit,
    onNavigateToHashtag: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val accountRelays = LocalAccountRelays.current
    val state = remember { AdvancedSearchBarState(scope) }
    val focusRequester = remember { FocusRequester() }
    var showRelayPicker by remember { mutableStateOf(false) }

    // Pre-fill initial query
    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotBlank()) {
            state.updateFromText(initialQuery)
        }
    }

    val connectedRelays by relayManager.connectedRelays.collectAsState()
    val relayStatuses by relayManager.relayStatuses.collectAsState()
    val allRelayUrls = remember(relayStatuses) { relayStatuses.keys }
    val relayCategories = LocalRelayCategories.current
    val searchRelays by relayCategories.searchRelays.collectAsState()
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

    // Skip people search when query specifies kinds that don't include profile (kind 0)
    val shouldSearchPeople =
        (debouncedQuery.kinds.isEmpty() && debouncedQuery.pseudoKinds.isEmpty()) ||
            debouncedQuery.kinds.contains(MetadataEvent.KIND)

    // Clear results and start loading when query changes
    LaunchedEffect(debouncedQuery) {
        if (!debouncedQuery.isEmpty && bech32Results.isEmpty()) {
            state.clearResults()
            state.initRelayStates(searchRelays)
            if (shouldSearchPeople) {
                state.startSearching("people-search")
            }
            state.startSearching("adv-search")
            // Timeout relays that silently ignore NIP-50 (e.g. strfry)
            kotlinx.coroutines.delay(10_000L)
            state.timeoutWaitingRelays()
        }
    }

    // NIP-50 people search subscription (use searchRelays from relay categories)
    rememberSubscription(searchRelays, debouncedQuery, relayManager = relayManager) {
        if (searchRelays.isEmpty() || debouncedQuery.isEmpty) {
            return@rememberSubscription null
        }
        if (bech32Results.isNotEmpty()) return@rememberSubscription null
        if (!shouldSearchPeople) {
            state.stopSearching("people-search")
            return@rememberSubscription null
        }

        createSearchPeopleSubscription(
            relays = searchRelays,
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
                        val user = localCache.getUserIfExists(event.pubKey)
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

    // NIP-50 advanced note search subscription (use searchRelays from relay categories)
    rememberSubscription(searchRelays, debouncedQuery, relayManager = relayManager) {
        if (searchRelays.isEmpty() || debouncedQuery.isEmpty) {
            return@rememberSubscription null
        }
        if (bech32Results.isNotEmpty()) return@rememberSubscription null

        val filters = SearchFilterFactory.createFilters(debouncedQuery)
        if (filters.isEmpty()) return@rememberSubscription null

        SubscriptionConfig(
            subId = generateSubId("adv-search"),
            filters = filters,
            relays = searchRelays,
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

    ReadingColumn {
        Column(
            modifier =
                modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
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
            val sidePadding = readingHorizontalPadding()
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .padding(horizontal = sidePadding, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Search",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    "${localCache.userCount()} users cached",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(16.dp))

            // Search bar with advanced toggle — honors the reading width cap so it
            // stays centered with the rest of the screen's content on wide windows.
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = sidePadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = textFieldValue,
                    onValueChange = {
                        textFieldValue = it
                        state.updateFromText(it.text)
                    },
                    // .height(44.dp) overrides M3's 56dp min-height (intended for
                    // mobile touch) — desktop inputs should feel closer to Slack /
                    // Raycast / VS Code. 44dp (not 40) keeps the bodyMedium
                    // placeholder from clipping vertically inside M3's built-in
                    // content padding.
                    modifier = Modifier.weight(1f).height(44.dp).focusRequester(focusRequester),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    placeholder = {
                        Text(
                            "Search people, tags, notes…",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            MaterialSymbols.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    trailingIcon = {
                        if (displayText.isNotEmpty()) {
                            IconButton(onClick = { state.clearSearch() }, modifier = Modifier.size(28.dp)) {
                                Icon(
                                    MaterialSymbols.Clear,
                                    contentDescription = "Clear",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                )
                if (account != null && !account.isReadOnly) {
                    IconButton(onClick = { showRelayPicker = true }) {
                        Icon(
                            MaterialSymbols.Dns,
                            contentDescription = "Search Relays",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = { state.togglePanel() }) {
                    Icon(
                        MaterialSymbols.Tune,
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

            // Search relay picker dialog
            if (showRelayPicker && account != null) {
                val pickerRelays =
                    remember {
                        mutableStateListOf<com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl>().also {
                            it.addAll(searchRelays)
                        }
                    }
                AlertDialog(
                    onDismissRequest = { showRelayPicker = false },
                    title = { Text("Search Relays") },
                    text = {
                        SearchRelayEditor(
                            localRelays = pickerRelays,
                            signer = account.signer,
                            onPublish = { event ->
                                relayManager.broadcastToAll(event)
                                accountRelays?.consumePublishedEvent(event)
                                accountRelays?.setSearchRelays(pickerRelays.toSet())
                            },
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { showRelayPicker = false }) {
                            Text("Close")
                        }
                    },
                )
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
            val hasAnyResults =
                bech32Results.isNotEmpty() || peopleResults.isNotEmpty() || noteResults.isNotEmpty()

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
        contentPadding = PaddingValues(horizontal = readingHorizontalPadding()),
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
                        MaterialSymbols.Star,
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
                            MaterialSymbols.Delete,
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
                        MaterialSymbols.History,
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
                symbol =
                    when (result) {
                        is SearchResult.UserResult -> MaterialSymbols.Person
                        is SearchResult.NoteResult -> MaterialSymbols.Description
                        is SearchResult.AddressResult -> MaterialSymbols.Description
                        is SearchResult.HashtagResult -> MaterialSymbols.Tag
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
                MaterialSymbols.AutoMirrored.ArrowForward,
                contentDescription = "Navigate",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
