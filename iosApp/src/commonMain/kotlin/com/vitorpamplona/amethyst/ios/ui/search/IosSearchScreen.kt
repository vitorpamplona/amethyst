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
package com.vitorpamplona.amethyst.ios.ui.search

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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.search.SearchResult
import com.vitorpamplona.amethyst.commons.ui.components.UserSearchCard
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedState
import com.vitorpamplona.amethyst.commons.viewmodels.SearchBarState
import com.vitorpamplona.amethyst.ios.cache.IosLocalCache
import com.vitorpamplona.amethyst.ios.feeds.IosHashtagFeedFilter
import com.vitorpamplona.amethyst.ios.feeds.IosSearchFeedFilter
import com.vitorpamplona.amethyst.ios.network.IosRelayConnectionManager
import com.vitorpamplona.amethyst.ios.subscriptions.FilterBuilders
import com.vitorpamplona.amethyst.ios.subscriptions.IosSubscriptionsCoordinator
import com.vitorpamplona.amethyst.ios.subscriptions.SubscriptionConfig
import com.vitorpamplona.amethyst.ios.subscriptions.generateSubId
import com.vitorpamplona.amethyst.ios.subscriptions.rememberSubscription
import com.vitorpamplona.amethyst.ios.ui.note.NoteCard
import com.vitorpamplona.amethyst.ios.ui.toNoteDisplayData
import com.vitorpamplona.amethyst.ios.viewmodels.IosFeedViewModel
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent

/**
 * iOS Search screen with user search, hashtag search, and NIP-50 relay search.
 *
 * Features:
 * - Bech32/hex parsing (npub, note, nevent, naddr) → direct navigation
 * - Hashtag detection (#tag) → subscribes to "t" tag filter + shows matching notes
 * - User search → local cache + NIP-50 relay search for profiles
 * - Text search → local cache content search + NIP-50 relay search for notes
 */
@Composable
fun IosSearchScreen(
    relayManager: IosRelayConnectionManager,
    localCache: IosLocalCache,
    coordinator: IosSubscriptionsCoordinator,
    onNavigateToProfile: (String) -> Unit,
    onNavigateToThread: (String) -> Unit,
    onNavigateToHashtag: ((String) -> Unit)? = null,
    onBoost: ((String) -> Unit)? = null,
    onLike: ((String) -> Unit)? = null,
    onZap: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val searchBarState = remember { SearchBarState(localCache, scope) }

    val searchText by searchBarState.searchText.collectAsState()
    val bech32Results by searchBarState.bech32Results.collectAsState()
    val cachedUserResults by searchBarState.cachedUserResults.collectAsState()
    val relaySearchResults by searchBarState.relaySearchResults.collectAsState()
    val isSearchingRelays by searchBarState.isSearchingRelays.collectAsState()

    val relayStatuses by relayManager.relayStatuses.collectAsState()
    val allRelayUrls = remember(relayStatuses) { relayStatuses.keys }

    // Detect hashtag query
    val isHashtagQuery = searchText.startsWith("#") && searchText.length > 1
    val hashtagValue = if (isHashtagQuery) searchText.substring(1).trim() else ""

    // Detect if we should do a text/note search (not bech32, not hashtag, >=2 chars)
    val isTextSearch = searchText.length >= 2 && bech32Results.isEmpty() && !isHashtagQuery

    // NIP-50 people search subscription
    rememberSubscription(allRelayUrls, searchText, relayManager = relayManager) {
        if (allRelayUrls.isEmpty() || !searchBarState.shouldSearchRelays) {
            return@rememberSubscription null
        }
        searchBarState.startRelaySearch()
        SubscriptionConfig(
            subId = generateSubId("search-people"),
            filters = listOf(FilterBuilders.searchPeople(searchText, limit = 20)),
            relays = allRelayUrls,
            onEvent = { event, _, relay, _ ->
                coordinator.consumeEvent(event, relay)
                if (event is MetadataEvent) {
                    val user = localCache.getUserIfExists(event.pubKey)
                    if (user != null) {
                        searchBarState.addRelaySearchResult(user)
                    }
                }
            },
        )
    }

    // NIP-50 note search subscription (for text queries)
    rememberSubscription(allRelayUrls, searchText, isTextSearch, relayManager = relayManager) {
        if (allRelayUrls.isEmpty() || !isTextSearch) return@rememberSubscription null
        SubscriptionConfig(
            subId = generateSubId("search-notes"),
            filters = listOf(FilterBuilders.searchNotes(searchText, limit = 50)),
            relays = allRelayUrls,
            onEvent = { event, _, relay, _ -> coordinator.consumeEvent(event, relay) },
        )
    }

    // Hashtag subscription (subscribes to "t" tag on relays)
    rememberSubscription(allRelayUrls, hashtagValue, relayManager = relayManager) {
        if (allRelayUrls.isEmpty() || hashtagValue.isBlank()) return@rememberSubscription null
        SubscriptionConfig(
            subId = generateSubId("search-hashtag"),
            filters =
                listOf(
                    FilterBuilders.byTags(
                        tags = mapOf("t" to listOf(hashtagValue.lowercase())),
                        kinds = listOf(1),
                        limit = 100,
                    ),
                ),
            relays = allRelayUrls,
            onEvent = { event, _, relay, _ -> coordinator.consumeEvent(event, relay) },
        )
    }

    // Feed view models for note results
    val hashtagViewModel =
        remember(hashtagValue) {
            if (hashtagValue.isNotBlank()) {
                IosFeedViewModel(IosHashtagFeedFilter(hashtagValue, localCache), localCache)
            } else {
                null
            }
        }

    val textSearchViewModel =
        remember(searchText, isTextSearch) {
            if (isTextSearch) {
                IosFeedViewModel(IosSearchFeedFilter(searchText, localCache), localCache)
            } else {
                null
            }
        }

    DisposableEffect(hashtagViewModel) {
        onDispose { hashtagViewModel?.destroy() }
    }

    DisposableEffect(textSearchViewModel) {
        onDispose { textSearchViewModel?.destroy() }
    }

    // Collect all user results
    val allUsers =
        remember(cachedUserResults, relaySearchResults) {
            (cachedUserResults + relaySearchResults).distinctBy { it.pubkeyHex }
        }

    Column(modifier = modifier.fillMaxSize()) {
        // Search bar
        OutlinedTextField(
            value = searchText,
            onValueChange = { searchBarState.updateSearchText(it) },
            placeholder = { Text("Search users, #hashtags, npub...") },
            leadingIcon = {
                Icon(
                    Icons.Default.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingIcon = {
                if (searchText.isNotEmpty()) {
                    IconButton(onClick = { searchBarState.clearSearch() }) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = "Clear",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        if (searchText.isBlank()) {
            SearchEmptyState()
        } else if (isHashtagQuery && hashtagValue.isNotBlank()) {
            HashtagSearchContent(
                hashtagValue = hashtagValue,
                viewModel = hashtagViewModel,
                localCache = localCache,
                onNavigateToThread = onNavigateToThread,
                onNavigateToProfile = onNavigateToProfile,
                onBoost = onBoost,
                onLike = onLike,
                onZap = onZap,
            )
        } else if (bech32Results.isNotEmpty()) {
            Bech32ResultsContent(
                results = bech32Results,
                localCache = localCache,
                onNavigateToProfile = onNavigateToProfile,
                onNavigateToThread = onNavigateToThread,
            )
        } else if (searchText.length >= 2) {
            UserAndNoteSearchContent(
                users = allUsers,
                isSearchingRelays = isSearchingRelays,
                textSearchViewModel = textSearchViewModel,
                localCache = localCache,
                searchText = searchText,
                onNavigateToProfile = onNavigateToProfile,
                onNavigateToThread = onNavigateToThread,
                onBoost = onBoost,
                onLike = onLike,
                onZap = onZap,
            )
        }
    }
}

@Composable
private fun SearchEmptyState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Default.Search,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Search Nostr",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Find users by name or npub, search #hashtags, or paste note/event IDs",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun HashtagSearchContent(
    hashtagValue: String,
    viewModel: IosFeedViewModel?,
    localCache: IosLocalCache,
    onNavigateToThread: (String) -> Unit,
    onNavigateToProfile: (String) -> Unit,
    onBoost: ((String) -> Unit)?,
    onLike: ((String) -> Unit)?,
    onZap: ((String) -> Unit)?,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        HashtagChip(
            hashtag = hashtagValue,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        viewModel?.let { vm ->
            val feedState by vm.feedState.feedContent.collectAsState()
            when (val state = feedState) {
                is FeedState.Loading -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Searching for #$hashtagValue...",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                is FeedState.Loaded -> {
                    val loadedState by state.feed.collectAsState()
                    if (loadedState.list.isEmpty()) {
                        Text(
                            "No notes found with #$hashtagValue yet",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        SectionHeader("Notes tagged #$hashtagValue")
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                        ) {
                            items(loadedState.list.take(50), key = { it.idHex }) { note ->
                                NoteCardItem(
                                    note = note,
                                    localCache = localCache,
                                    onNavigateToThread = onNavigateToThread,
                                    onNavigateToProfile = onNavigateToProfile,
                                    onBoost = onBoost,
                                    onLike = onLike,
                                    onZap = onZap,
                                )
                            }
                        }
                    }
                }

                is FeedState.Empty -> {
                    Text(
                        "No notes found with #$hashtagValue",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> {}
            }
        }
    }
}

@Composable
private fun Bech32ResultsContent(
    results: List<SearchResult>,
    localCache: IosLocalCache,
    onNavigateToProfile: (String) -> Unit,
    onNavigateToThread: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item { SectionHeader("Direct Match") }
        items(results) { result ->
            Bech32ResultCard(
                result = result,
                localCache = localCache,
                onNavigateToProfile = onNavigateToProfile,
                onNavigateToThread = onNavigateToThread,
            )
        }
    }
}

@Composable
private fun UserAndNoteSearchContent(
    users: List<User>,
    isSearchingRelays: Boolean,
    textSearchViewModel: IosFeedViewModel?,
    localCache: IosLocalCache,
    searchText: String,
    onNavigateToProfile: (String) -> Unit,
    onNavigateToThread: (String) -> Unit,
    onBoost: ((String) -> Unit)?,
    onLike: ((String) -> Unit)?,
    onZap: ((String) -> Unit)?,
) {
    // Collect note search feed state at this composable level
    val noteFeedState =
        textSearchViewModel?.let {
            val fs by it.feedState.feedContent.collectAsState()
            fs
        }

    val notesList =
        when (val state = noteFeedState) {
            is FeedState.Loaded -> {
                val loaded by state.feed.collectAsState()
                loaded.list.take(50)
            }

            else -> {
                emptyList()
            }
        }

    val isLoadingNotes = noteFeedState is FeedState.Loading

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // User results
        if (users.isNotEmpty() || isSearchingRelays) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionHeader("Users")
                    if (isSearchingRelays) {
                        Spacer(Modifier.width(8.dp))
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    }
                }
            }
            items(users, key = { "user-${it.pubkeyHex}" }) { user ->
                UserSearchCard(
                    user = user,
                    onClick = { onNavigateToProfile(user.pubkeyHex) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }

        // Note results
        if (notesList.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                SectionHeader("Notes")
            }
            items(notesList, key = { "note-${it.idHex}" }) { note ->
                NoteCardItem(
                    note = note,
                    localCache = localCache,
                    onNavigateToThread = onNavigateToThread,
                    onNavigateToProfile = onNavigateToProfile,
                    onBoost = onBoost,
                    onLike = onLike,
                    onZap = onZap,
                )
            }
        } else if (isLoadingNotes) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Searching notes...",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        // No results
        if (users.isEmpty() && notesList.isEmpty() && !isSearchingRelays && !isLoadingNotes) {
            item {
                Text(
                    "No results found for \"$searchText\"",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(4.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun HashtagChip(
    hashtag: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Tag,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                hashtag,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun Bech32ResultCard(
    result: SearchResult,
    localCache: IosLocalCache,
    onNavigateToProfile: (String) -> Unit,
    onNavigateToThread: (String) -> Unit,
) {
    when (result) {
        is SearchResult.UserResult -> {
            val user = localCache.getUserIfExists(result.pubKeyHex)
            if (user != null) {
                UserSearchCard(
                    user = user,
                    onClick = { onNavigateToProfile(result.pubKeyHex) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            } else {
                Card(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clickable { onNavigateToProfile(result.pubKeyHex) },
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                "User Profile",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                result.displayId,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        is SearchResult.NoteResult -> {
            Card(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clickable { onNavigateToThread(result.noteIdHex) },
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "Note / Event",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            result.displayId,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        is SearchResult.AddressResult -> {
            Card(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "Addressable Event (kind ${result.kind})",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            result.displayId,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        is SearchResult.HashtagResult -> {
            // Handled by hashtag section
        }
    }
}

@Composable
private fun NoteCardItem(
    note: Note,
    localCache: IosLocalCache,
    onNavigateToThread: (String) -> Unit,
    onNavigateToProfile: (String) -> Unit,
    onBoost: ((String) -> Unit)? = null,
    onLike: ((String) -> Unit)? = null,
    onZap: ((String) -> Unit)? = null,
) {
    val event = note.event ?: return
    NoteCard(
        note = note.toNoteDisplayData(localCache),
        onClick = { onNavigateToThread(event.id) },
        onAuthorClick = onNavigateToProfile,
        onBoost = onBoost,
        onLike = onLike,
        onZap = onZap,
    )
}
