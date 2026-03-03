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
package com.vitorpamplona.amethyst.desktop.ui.chats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.commons.search.SearchResult
import com.vitorpamplona.amethyst.commons.ui.components.UserSearchCard
import com.vitorpamplona.amethyst.commons.viewmodels.SearchBarState
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.amethyst.desktop.network.DesktopRelayConnectionManager
import com.vitorpamplona.amethyst.desktop.subscriptions.createMetadataSubscription
import com.vitorpamplona.amethyst.desktop.subscriptions.createSearchPeopleSubscription
import com.vitorpamplona.amethyst.desktop.subscriptions.rememberSubscription
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull

@Composable
fun NewDmDialog(
    cacheProvider: ICacheProvider,
    relayManager: DesktopRelayConnectionManager,
    localCache: DesktopLocalCache,
    onUserSelected: (ChatroomKey) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val searchState = remember { SearchBarState(cacheProvider, scope) }
    val searchText by searchState.searchText.collectAsState()
    val bech32Results by searchState.bech32Results.collectAsState()
    val cachedUsers by searchState.cachedUserResults.collectAsState()
    val relaySearchResults by searchState.relaySearchResults.collectAsState()
    val isSearchingRelays by searchState.isSearchingRelays.collectAsState()
    val relayStatuses by relayManager.relayStatuses.collectAsState()
    val focusRequester = remember { FocusRequester() }

    // NIP-50 relay search when local cache has few/no results
    rememberSubscription(relayStatuses, searchText, cachedUsers.size, relayManager = relayManager) {
        val configuredRelays = relayStatuses.keys
        if (configuredRelays.isEmpty()) return@rememberSubscription null

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

    // Bech32 npub metadata loading
    rememberSubscription(relayStatuses, searchText, relayManager = relayManager) {
        val configuredRelays = relayStatuses.keys
        if (configuredRelays.isEmpty() || searchText.length < 2) {
            return@rememberSubscription null
        }

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

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    "New Message",
                    style = MaterialTheme.typography.titleLarge,
                )

                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchState.updateSearchText(it) },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                    placeholder = { Text("Search users by name or npub...") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        if (searchText.isNotEmpty()) {
                            IconButton(onClick = { searchState.clearSearch() }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                )

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Bech32 user results
                    val userResults =
                        bech32Results.filterIsInstance<SearchResult.UserResult>()
                    items(userResults) { result ->
                        val user =
                            cacheProvider.getUserIfExists(result.pubKeyHex) as? User
                        if (user != null) {
                            UserSearchCard(
                                user = user,
                                onClick = {
                                    onUserSelected(
                                        ChatroomKey(setOf(user.pubkeyHex)),
                                    )
                                },
                            )
                        } else {
                            // Minimal card for unloaded users
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.medium,
                            ) {
                                Text(
                                    result.displayId,
                                    modifier = Modifier.padding(16.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }

                    // Cached user results
                    items(cachedUsers) { user ->
                        UserSearchCard(
                            user = user,
                            onClick = {
                                onUserSelected(
                                    ChatroomKey(setOf(user.pubkeyHex)),
                                )
                            },
                        )
                    }

                    // Relay search results
                    items(relaySearchResults) { user ->
                        UserSearchCard(
                            user = user,
                            onClick = {
                                onUserSelected(
                                    ChatroomKey(setOf(user.pubkeyHex)),
                                )
                            },
                        )
                    }

                    // Relay search loading indicator
                    if (isSearchingRelays) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                )
                            }
                        }
                    }

                    // No results hint
                    if (searchText.length >= 2 &&
                        !isSearchingRelays &&
                        userResults.isEmpty() &&
                        cachedUsers.isEmpty() &&
                        relaySearchResults.isEmpty()
                    ) {
                        item {
                            Text(
                                "No users found",
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
