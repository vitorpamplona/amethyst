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
package com.vitorpamplona.amethyst.desktop.ui.deck

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.feeds.custom.FeedDefinition
import com.vitorpamplona.amethyst.commons.feeds.custom.FeedDefinitionRepository
import com.vitorpamplona.amethyst.commons.feeds.custom.FeedSource
import com.vitorpamplona.amethyst.commons.feeds.custom.MAX_PINNED_FEEDS
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun FeedsDrawerTab(
    onSelectFeed: (FeedDefinition) -> Unit,
    onDismiss: () -> Unit,
    localCache: DesktopLocalCache? = LocalDesktopCache.current,
    relayManager: com.vitorpamplona.amethyst.desktop.network.DesktopRelayConnectionManager? = LocalRelayManager.current,
    feedRepository: FeedDefinitionRepository = LocalFeedRepository.current,
    scope: CoroutineScope = LocalFeedScope.current,
) {
    val grouped by feedRepository.groupedFeeds.collectAsState()
    var showBuilder by remember { mutableStateOf(false) }
    var editingFeed by remember { mutableStateOf<FeedDefinition?>(null) }
    var deletingFeed by remember { mutableStateOf<FeedDefinition?>(null) }

    // Author search state — hoisted here because AlertDialog can't run LaunchedEffect
    var authorQuery by remember { mutableStateOf("") }
    var authorLocal by remember {
        mutableStateOf(emptyList<com.vitorpamplona.amethyst.commons.model.User>())
    }
    var authorRelay by remember {
        mutableStateOf(emptyList<com.vitorpamplona.amethyst.commons.model.User>())
    }
    var authorSearching by remember { mutableStateOf(false) }

    LaunchedEffect(authorQuery) {
        if (authorQuery.length < 2 || localCache == null) {
            authorLocal = emptyList()
            authorRelay = emptyList()
            authorSearching = false
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(300)
        val results = localCache.findUsersStartingWith(authorQuery, 10)
        authorLocal = results

        if (relayManager != null) {
            // Use all connected relays — some support NIP-50 search
            val relays = relayManager.connectedRelays.value
            if (relays.isNotEmpty()) {
                authorSearching = true
                authorRelay = emptyList()
                val ch = kotlinx.coroutines.channels.Channel<com.vitorpamplona.amethyst.commons.model.User>(64)
                val subId =
                    com.vitorpamplona.amethyst.desktop.subscriptions
                        .generateSubId("author-search")
                relayManager.subscribe(
                    subId = subId,
                    filters =
                        listOf(
                            com.vitorpamplona.amethyst.desktop.subscriptions.FilterBuilders
                                .searchPeople(authorQuery, 30),
                        ),
                    relays = relays,
                    listener =
                        object : com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener {
                            override fun onEvent(
                                event: com.vitorpamplona.quartz.nip01Core.core.Event,
                                isLive: Boolean,
                                relay: com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl,
                                forFilters: List<com.vitorpamplona.quartz.nip01Core.relay.filters.Filter>?,
                            ) {
                                if (event is com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent) {
                                    localCache.consumeMetadata(event)
                                    localCache.getUserIfExists(event.pubKey)?.let {
                                        ch.trySend(it)
                                    }
                                }
                            }

                            override fun onEose(
                                relay: com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl,
                                forFilters: List<com.vitorpamplona.quartz.nip01Core.relay.filters.Filter>?,
                            ) {
                                ch.close()
                            }

                            override fun onClosed(
                                message: String,
                                relay: com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl,
                                forFilters: List<com.vitorpamplona.quartz.nip01Core.relay.filters.Filter>?,
                            ) {
                                ch.close()
                            }
                        },
                )
                try {
                    kotlinx.coroutines.withTimeoutOrNull(8000) {
                        for (user in ch) {
                            if (authorRelay.none { it.pubkeyHex == user.pubkeyHex }) {
                                authorRelay = authorRelay + user
                            }
                        }
                    }
                } finally {
                    authorSearching = false
                    relayManager.unsubscribe(subId)
                }
            }
        }
    }

    // Reset search state when dialogs close
    LaunchedEffect(showBuilder, editingFeed) {
        if (!showBuilder && editingFeed == null) {
            authorQuery = ""
            authorLocal = emptyList()
            authorRelay = emptyList()
            authorSearching = false
        }
    }

    // Create dialog
    if (showBuilder) {
        FeedBuilderDialog(
            localCache = localCache,
            authorQuery = authorQuery,
            onAuthorQueryChange = { authorQuery = it },
            authorSuggestions = authorLocal,
            authorRelayResults = authorRelay,
            authorSearching = authorSearching,
            onSave = { feed ->
                scope.launch { feedRepository.add(feed) }
                showBuilder = false
            },
            onDismiss = { showBuilder = false },
        )
    }

    // Edit dialog
    editingFeed?.let { feed ->
        FeedBuilderDialog(
            initial = feed,
            localCache = localCache,
            authorQuery = authorQuery,
            onAuthorQueryChange = { authorQuery = it },
            authorSuggestions = authorLocal,
            authorRelayResults = authorRelay,
            authorSearching = authorSearching,
            onSave = { updated ->
                scope.launch { feedRepository.update(updated) }
                editingFeed = null
            },
            onDismiss = { editingFeed = null },
        )
    }

    // Delete confirmation
    deletingFeed?.let { feed ->
        AlertDialog(
            onDismissRequest = { deletingFeed = null },
            title = { Text("Delete Feed") },
            text = { Text("Delete \"${feed.name}\"? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { feedRepository.delete(feed.id) }
                    deletingFeed = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingFeed = null }) { Text("Cancel") }
            },
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (grouped.pinned.isNotEmpty()) {
                item(key = "pinned-header") {
                    SectionHeader("Pinned (${grouped.pinned.size}/$MAX_PINNED_FEEDS)")
                }
                items(grouped.pinned, key = { "pinned-${it.id}" }) { feed ->
                    FeedRow(
                        feed = feed,
                        onSelect = { onSelectFeed(feed) },
                        onPin = null,
                        onUnpin = { scope.launch { feedRepository.unpin(feed.id) } },
                        onEdit =
                            if (feed.source is FeedSource.Filter) {
                                { editingFeed = feed }
                            } else {
                                null
                            },
                        onDelete =
                            if (feed.id.startsWith("default-")) {
                                null
                            } else {
                                { deletingFeed = feed }
                            },
                    )
                }
            }

            if (grouped.myFeeds.isNotEmpty()) {
                item(key = "my-header") {
                    SectionHeader("My Feeds")
                }
                items(grouped.myFeeds, key = { "my-${it.id}" }) { feed ->
                    FeedRow(
                        feed = feed,
                        onSelect = { onSelectFeed(feed) },
                        onPin = { scope.launch { feedRepository.pin(feed.id) } },
                        onUnpin = null,
                        onEdit =
                            if (feed.source is FeedSource.Filter) {
                                { editingFeed = feed }
                            } else {
                                null
                            },
                        onDelete = { deletingFeed = feed },
                    )
                }
            }

            if (grouped.algoFeeds.isNotEmpty()) {
                item(key = "algo-header") {
                    SectionHeader("Algo Feeds")
                }
                items(grouped.algoFeeds, key = { "algo-${it.id}" }) { feed ->
                    FeedRow(
                        feed = feed,
                        onSelect = { onSelectFeed(feed) },
                        onPin = { scope.launch { feedRepository.pin(feed.id) } },
                        onUnpin =
                            if (feed.pinned) {
                                { scope.launch { feedRepository.unpin(feed.id) } }
                            } else {
                                null
                            },
                        onEdit = null,
                        onDelete = { deletingFeed = feed },
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = { showBuilder = true }) {
                Text("+ Create Feed")
            }
            OutlinedButton(onClick = { /* TODO: browse DVMs */ }) {
                Text("Browse DVMs")
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
private fun FeedRow(
    feed: FeedDefinition,
    onSelect: () -> Unit,
    onPin: (() -> Unit)?,
    onUnpin: (() -> Unit)?,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onSelect),
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = feed.emoji, fontSize = 20.sp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = feed.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (onEdit != null) {
                IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                    Icon(MaterialSymbols.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp))
                }
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(
                        MaterialSymbols.Close,
                        contentDescription = "Delete",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
            if (onUnpin != null) {
                TextButton(onClick = onUnpin, modifier = Modifier.size(height = 32.dp, width = 60.dp)) {
                    Text("Unpin", style = MaterialTheme.typography.labelSmall)
                }
            }
            if (onPin != null) {
                TextButton(onClick = onPin, modifier = Modifier.size(height = 32.dp, width = 48.dp)) {
                    Text("Pin", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
