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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.search.AdvancedSearchBarState
import com.vitorpamplona.amethyst.commons.search.SearchResult
import com.vitorpamplona.amethyst.commons.search.SearchResultFilter
import com.vitorpamplona.amethyst.commons.search.parseSearchInput
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
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull

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
    val state = remember { AdvancedSearchBarState(localCache, scope) }
    val focusRequester = remember { FocusRequester() }

    // Pre-fill initial query
    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotBlank()) {
            state.updateFromText(initialQuery)
        }
    }

    val relayStatuses by relayManager.relayStatuses.collectAsState()
    val connectedRelays by relayManager.connectedRelays.collectAsState()
    val displayText by state.displayText.collectAsState()
    val query by state.query.collectAsState()
    val debouncedQuery by state.debouncedQuery.collectAsState()
    val panelExpanded by state.panelExpanded.collectAsState()
    val isSearching by state.isSearching.collectAsState()
    val peopleResults by state.peopleResults.collectAsState()
    val noteResults by state.noteResults.collectAsState()

    // Bech32 parsing (immediate, no debounce)
    val bech32Results = remember(displayText) { parseSearchInput(displayText) }

    // NIP-50 people search subscription
    rememberSubscription(connectedRelays, debouncedQuery, relayManager = relayManager) {
        if (connectedRelays.isEmpty() || debouncedQuery.isEmpty) {
            return@rememberSubscription null
        }
        // Skip if bech32 detected
        if (bech32Results.isNotEmpty()) return@rememberSubscription null

        state.startSearching()
        state.clearResults()

        createSearchPeopleSubscription(
            relays = connectedRelays,
            searchQuery =
                debouncedQuery.text.ifBlank {
                    com.vitorpamplona.amethyst.commons.search.QuerySerializer
                        .serialize(debouncedQuery)
                },
            limit = 20,
            onEvent = { event, _, _, _ ->
                if (event is MetadataEvent) {
                    localCache.consumeMetadata(event)
                    @Suppress("UNCHECKED_CAST")
                    val user = localCache.getUserIfExists(event.pubKey) as? User
                    if (user != null) {
                        state.addPeopleResult(user)
                    }
                }
            },
            onEose = { _, _ ->
                state.stopSearching()
            },
        )
    }

    // NIP-50 advanced note search subscription (kinds beyond people)
    rememberSubscription(connectedRelays, debouncedQuery, relayManager = relayManager) {
        if (connectedRelays.isEmpty() || debouncedQuery.isEmpty) {
            return@rememberSubscription null
        }
        if (bech32Results.isNotEmpty()) return@rememberSubscription null

        val filters = SearchFilterFactory.createFilters(debouncedQuery)
        if (filters.isEmpty()) return@rememberSubscription null

        SubscriptionConfig(
            subId = generateSubId("adv-search"),
            filters = filters,
            relays = connectedRelays,
            onEvent = { event, _, _, _ ->
                // Skip metadata events (handled by people search)
                if (event.kind == 0) return@SubscriptionConfig
                val filtered = SearchResultFilter.filter(listOf(event), debouncedQuery)
                if (filtered.isNotEmpty()) {
                    state.addNoteResults(filtered)
                }
            },
            onEose = { _, _ ->
                state.stopSearching()
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

    // Auto-focus
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(modifier = modifier.fillMaxSize()) {
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
                value =
                    TextFieldValue(
                        text = displayText,
                        selection = TextRange(displayText.length),
                    ),
                onValueChange = { state.updateFromText(it.text) },
                modifier =
                    Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
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
            )
        } else if (!debouncedQuery.isEmpty && !isSearching) {
            Text(
                "No results found. Try broader terms or fewer filters.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        } else if (isSearching) {
            Text(
                "Searching relays...",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            // Empty state with operator hints
            EmptySearchHints()
        }
    }
}

@Composable
private fun EmptySearchHints() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Search for users, notes, or content",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Use operators to refine your search:",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SearchHint("from:npub1...", "Filter by author")
            SearchHint("kind:article", "Long-form content")
            SearchHint("since:2025-01", "After January 2025")
            SearchHint("#bitcoin", "Hashtag search")
            SearchHint("\"exact phrase\"", "Exact match")
            SearchHint("bitcoin OR nostr", "Either term")
            SearchHint("-spam", "Exclude term")
            SearchHint("lang:en", "Language filter")
        }
    }
}

@Composable
private fun SearchHint(
    identifier: String,
    description: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(0.6f),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            identifier,
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
                        is SearchResult.CachedUserResult -> onNavigateToProfile(result.user.pubkeyHex)
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
                        is SearchResult.CachedUserResult -> Icons.Default.Person
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
                        is SearchResult.CachedUserResult -> result.user.toBestDisplayName()
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
                        is SearchResult.CachedUserResult -> result.user.pubkeyDisplayHex()
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
