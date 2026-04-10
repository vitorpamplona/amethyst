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
package com.vitorpamplona.amethyst.ios.ui.lists

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.ios.account.AccountState
import com.vitorpamplona.amethyst.ios.cache.IosLocalCache
import com.vitorpamplona.amethyst.ios.network.IosRelayConnectionManager
import com.vitorpamplona.amethyst.ios.subscriptions.IosSubscriptionsCoordinator
import com.vitorpamplona.amethyst.ios.subscriptions.SubscriptionConfig
import com.vitorpamplona.amethyst.ios.subscriptions.generateSubId
import com.vitorpamplona.amethyst.ios.subscriptions.rememberSubscription
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip51Lists.relaySets.RelaySetEvent
import kotlinx.coroutines.launch

/**
 * Display data for a relay set (NIP-51 kind 30002).
 */
data class RelaySetDisplayData(
    val addressId: String,
    val title: String?,
    val description: String?,
    val relays: List<String>,
    val event: RelaySetEvent,
)

/**
 * NIP-51 Relay Sets management screen.
 *
 * Shows the user's relay sets and allows:
 * - Viewing relay URLs in each set
 * - Creating new relay sets
 * - Adding/removing relays from sets
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelayListScreen(
    account: AccountState.LoggedIn,
    relayManager: IosRelayConnectionManager,
    localCache: IosLocalCache,
    coordinator: IosSubscriptionsCoordinator,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val relayStatuses by relayManager.relayStatuses.collectAsState()
    val allRelayUrls = remember(relayStatuses) { relayStatuses.keys }

    // Subscribe to relay sets
    rememberSubscription(allRelayUrls, account.pubKeyHex, relayManager = relayManager) {
        if (allRelayUrls.isEmpty()) return@rememberSubscription null
        SubscriptionConfig(
            subId = generateSubId("relay-sets"),
            filters =
                listOf(
                    Filter(
                        kinds = listOf(RelaySetEvent.KIND),
                        authors = listOf(account.pubKeyHex),
                    ),
                ),
            relays = allRelayUrls,
            onEvent = { event, _, relay, _ -> coordinator.consumeEvent(event, relay) },
        )
    }

    var relaySets by remember { mutableStateOf<List<RelaySetDisplayData>>(emptyList()) }
    var showAddDialog by remember { mutableStateOf(false) }

    // Refresh from cache
    val cacheVersion by localCache.cacheVersion.collectAsState()
    LaunchedEffect(cacheVersion) {
        relaySets =
            localCache
                .findRelaySetsByAuthor(account.pubKeyHex)
                .mapNotNull { note ->
                    val event = note.event as? RelaySetEvent ?: return@mapNotNull null
                    RelaySetDisplayData(
                        addressId = note.idHex,
                        title = event.title(),
                        description = event.description(),
                        relays = event.relays().map { it.url },
                        event = event,
                    )
                }.sortedBy { it.title?.lowercase() ?: "" }
    }

    if (showAddDialog) {
        AddRelaySetDialog(
            onDismiss = { showAddDialog = false },
            onCreate = { title, relayUrl ->
                showAddDialog = false
                scope.launch {
                    try {
                        val normalized = RelayUrlNormalizer.normalizeOrNull(relayUrl)
                        if (normalized != null) {
                            val template =
                                RelaySetEvent.create(
                                    relays = listOf(normalized),
                                    signer = account.signer,
                                )
                            localCache.consume(template, null)
                            relayManager.broadcastToAll(template)
                            snackbarHostState.showSnackbar("📡 Relay set \"$title\" created!")
                        }
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        snackbarHostState.showSnackbar("Failed: " + (e.message ?: "unknown"))
                    }
                }
            },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Relay Sets") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            if (!account.isReadOnly) {
                FloatingActionButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, "Create Set")
                }
            }
        },
    ) { padding ->
        if (relaySets.isEmpty()) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Default.Wifi,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Text("No relay sets yet", style = MaterialTheme.typography.titleMedium)
            }
        } else {
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding =
                    androidx.compose.foundation.layout
                        .PaddingValues(16.dp),
            ) {
                items(relaySets, key = { it.addressId }) { set ->
                    RelaySetCard(set = set)
                }
            }
        }
    }
}

@Composable
private fun RelaySetCard(set: RelaySetDisplayData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                set.title ?: "Untitled",
                style = MaterialTheme.typography.titleSmall,
            )
            set.description?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            set.relays.forEach { relay ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Wifi,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        relay,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            Text(
                "${set.relays.size} relays",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun AddRelaySetDialog(
    onDismiss: () -> Unit,
    onCreate: (title: String, relayUrl: String) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var relayUrl by remember { mutableStateOf("wss://") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Relay Set") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Set Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = relayUrl,
                    onValueChange = { relayUrl = it },
                    label = { Text("Relay URL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(title, relayUrl) },
                enabled = title.isNotBlank() && relayUrl.startsWith("wss://"),
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
