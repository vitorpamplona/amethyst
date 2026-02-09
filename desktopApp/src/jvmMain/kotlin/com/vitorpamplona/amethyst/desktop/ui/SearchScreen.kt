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
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.search.SearchResult
import com.vitorpamplona.amethyst.commons.ui.components.UserSearchCard
import com.vitorpamplona.amethyst.commons.viewmodels.SearchBarState
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.amethyst.desktop.network.DesktopRelayConnectionManager
import com.vitorpamplona.amethyst.desktop.subscriptions.DesktopRelaySubscriptionsCoordinator
import com.vitorpamplona.amethyst.desktop.subscriptions.createMetadataSubscription
import com.vitorpamplona.amethyst.desktop.subscriptions.createSearchPeopleSubscription
import com.vitorpamplona.amethyst.desktop.subscriptions.rememberSubscription
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull

@Composable
fun SearchScreen(
    localCache: DesktopLocalCache,
    relayManager: DesktopRelayConnectionManager,
    subscriptionsCoordinator: DesktopRelaySubscriptionsCoordinator? = null,
    onNavigateToProfile: (String) -> Unit,
    onNavigateToThread: (String) -> Unit,
    onNavigateToHashtag: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val searchState = remember { SearchBarState(localCache, scope) }
    val focusRequester = remember { FocusRequester() }
    val relayStatuses by relayManager.relayStatuses.collectAsState()

    // Collect state from SearchBarState
    val searchText by searchState.searchText.collectAsState()
    val bech32Results by searchState.bech32Results.collectAsState()
    val cachedUserResults by searchState.cachedUserResults.collectAsState()
    val relaySearchResults by searchState.relaySearchResults.collectAsState()
    val isSearchingRelays by searchState.isSearchingRelays.collectAsState()

    // NIP-50 relay search when local cache has few/no results
    rememberSubscription(relayStatuses, searchText, cachedUserResults.size, relayManager = relayManager) {
        val configuredRelays = relayStatuses.keys
        if (configuredRelays.isEmpty()) return@rememberSubscription null

        // Only search relays if we have a real query and limited local results
        if (searchState.shouldSearchRelays) {
            searchState.startRelaySearch()
            createSearchPeopleSubscription(
                relays = configuredRelays,
                searchQuery = searchText,
                limit = 20,
                onEvent = { event, _, _, _ ->
                    if (event is MetadataEvent) {
                        localCache.consumeMetadata(event)
                        val user = localCache.getUserIfExists(event.pubKey)
                        if (user != null) {
                            searchState.addRelaySearchResult(user)
                        }
                    }
                },
                onEose = { _, _ ->
                    searchState.endRelaySearch()
                },
            )
        } else {
            null
        }
    }

    // Subscribe to metadata for searched users (to populate cache)
    rememberSubscription(relayStatuses, searchText, relayManager = relayManager) {
        val configuredRelays = relayStatuses.keys
        if (configuredRelays.isEmpty() || searchText.length < 2) {
            return@rememberSubscription null
        }

        // If it's a specific pubkey search, fetch that user's metadata
        val pubkeyHex = decodePublicKeyAsHexOrNull(searchText)
        if (pubkeyHex != null) {
            createMetadataSubscription(
                relays = configuredRelays,
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

    // Auto-focus the search field
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = modifier.fillMaxSize(),
    ) {
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

        // Search input field
        OutlinedTextField(
            value = searchText,
            onValueChange = { searchState.updateSearchText(it) },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            placeholder = { Text("Search by name, npub, nevent, or #hashtag") },
            leadingIcon = {
                Icon(
                    Icons.Default.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingIcon = {
                if (searchText.isNotEmpty()) {
                    IconButton(onClick = { searchState.clearSearch() }) {
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

        Spacer(Modifier.height(16.dp))

        // Results
        val hasResults = bech32Results.isNotEmpty() || cachedUserResults.isNotEmpty() || relaySearchResults.isNotEmpty()

        if (!hasResults && searchText.isNotEmpty() && searchText.length >= 2 && !isSearchingRelays) {
            Text(
                "No matches found. Try a name, npub, nevent, or #hashtag.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        } else if (isSearchingRelays && !hasResults) {
            Text(
                "Searching relays...",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        } else if (hasResults) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Bech32/hex results first
                if (bech32Results.isNotEmpty()) {
                    item {
                        Text(
                            "Direct lookup",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                    items(bech32Results) { result ->
                        SearchResultCard(
                            result = result,
                            onNavigateToProfile = onNavigateToProfile,
                            onNavigateToThread = onNavigateToThread,
                            onNavigateToHashtag = onNavigateToHashtag,
                        )
                    }
                }

                // Cached user results
                if (cachedUserResults.isNotEmpty()) {
                    if (bech32Results.isNotEmpty()) {
                        item {
                            HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        }
                    }
                    item {
                        Text(
                            "Cached users (${cachedUserResults.size})",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                    items(cachedUserResults, key = { "cached-${it.pubkeyHex}" }) { user ->
                        UserSearchCard(
                            user = user,
                            onClick = { onNavigateToProfile(user.pubkeyHex) },
                        )
                    }
                }

                // Relay search results (NIP-50)
                if (relaySearchResults.isNotEmpty()) {
                    if (bech32Results.isNotEmpty() || cachedUserResults.isNotEmpty()) {
                        item {
                            HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        }
                    }
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                "From relays (${relaySearchResults.size})",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                            if (isSearchingRelays) {
                                Text(
                                    "searching...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                    items(relaySearchResults, key = { "relay-${it.pubkeyHex}" }) { user ->
                        UserSearchCard(
                            user = user,
                            onClick = { onNavigateToProfile(user.pubkeyHex) },
                        )
                    }
                } else if (isSearchingRelays && cachedUserResults.isEmpty()) {
                    item {
                        Text(
                            "Searching relays...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }
            }
        } else {
            // Empty state
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Search for users or notes",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Enter a name or Nostr identifier:",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(16.dp))
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SearchHint("vitor", "Search by name")
                    SearchHint("npub1...", "User profile")
                    SearchHint("note1...", "Single note")
                    SearchHint("nevent1...", "Note with metadata")
                    SearchHint("#hashtag", "Hashtag search")
                }
            }
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
                        is SearchResult.AddressResult -> {
                            onNavigateToThread("${result.kind}:${result.pubKeyHex}:${result.dTag}")
                        }
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
