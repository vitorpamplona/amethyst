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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.ui.components.UserAvatar
import com.vitorpamplona.amethyst.ios.account.AccountState
import com.vitorpamplona.amethyst.ios.cache.IosLocalCache
import com.vitorpamplona.amethyst.ios.network.IosRelayConnectionManager
import com.vitorpamplona.amethyst.ios.subscriptions.IosSubscriptionsCoordinator
import com.vitorpamplona.amethyst.ios.subscriptions.SubscriptionConfig
import com.vitorpamplona.amethyst.ios.subscriptions.generateSubId
import com.vitorpamplona.amethyst.ios.subscriptions.rememberSubscription
import com.vitorpamplona.quartz.nip51Lists.muteList.tags.UserTag
import com.vitorpamplona.quartz.nip51Lists.peopleList.PeopleListEvent
import kotlinx.coroutines.launch

/**
 * Display data for a people list.
 */
data class PeopleListDisplayData(
    val addressId: String,
    val dTag: String,
    val title: String,
    val description: String?,
    val imageUrl: String?,
    val memberCount: Int,
    val event: PeopleListEvent,
)

/**
 * NIP-51 Lists management screen.
 *
 * Shows all the user's people lists (kind 30000) and allows:
 * - Viewing list members
 * - Creating new lists
 * - Adding/removing people from lists
 * - Deleting lists
 *
 * Uses existing quartz PeopleListEvent for event creation/signing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListsManagementScreen(
    account: AccountState.LoggedIn,
    relayManager: IosRelayConnectionManager,
    localCache: IosLocalCache,
    coordinator: IosSubscriptionsCoordinator,
    onBack: () -> Unit,
    onNavigateToProfile: ((String) -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val relayStatuses by relayManager.relayStatuses.collectAsState()
    val allRelayUrls = remember(relayStatuses) { relayStatuses.keys }

    // Subscribe to user's people lists
    rememberSubscription(allRelayUrls, account.pubKeyHex, relayManager = relayManager) {
        if (allRelayUrls.isEmpty()) return@rememberSubscription null
        SubscriptionConfig(
            subId = generateSubId("people-lists"),
            filters =
                listOf(
                    com.vitorpamplona.quartz.nip01Core.relay.filters.Filter(
                        kinds = listOf(PeopleListEvent.KIND),
                        authors = listOf(account.pubKeyHex),
                    ),
                ),
            relays = allRelayUrls,
            onEvent = { event, _, relay, _ -> coordinator.consumeEvent(event, relay) },
        )
    }

    // Collect people lists from cache
    var peopleLists by remember { mutableStateOf<List<PeopleListDisplayData>>(emptyList()) }

    LaunchedEffect(allRelayUrls) {
        // Refresh list from cache periodically
        kotlinx.coroutines.delay(1000)
        refreshPeopleLists(account.pubKeyHex, localCache) { peopleLists = it }
    }

    // Refresh every time cache updates
    val cacheVersion by localCache.cacheVersion.collectAsState()
    LaunchedEffect(cacheVersion) {
        refreshPeopleLists(account.pubKeyHex, localCache) { peopleLists = it }
    }

    var showCreateDialog by remember { mutableStateOf(false) }
    var selectedList by remember { mutableStateOf<PeopleListDisplayData?>(null) }

    if (showCreateDialog) {
        CreateListDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { title, description ->
                showCreateDialog = false
                scope.launch {
                    try {
                        @OptIn(kotlin.uuid.ExperimentalUuidApi::class)
                        val event =
                            PeopleListEvent.createListWithDescription(
                                dTag =
                                    kotlin.uuid.Uuid
                                        .random()
                                        .toString(),
                                title = title,
                                description = description.ifBlank { null },
                                signer = account.signer,
                            )
                        localCache.consume(event, null)
                        relayManager.broadcastToAll(event)
                        refreshPeopleLists(account.pubKeyHex, localCache) { peopleLists = it }
                        snackbarHostState.showSnackbar("📋 List \"$title\" created!")
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        snackbarHostState.showSnackbar("Failed to create list")
                    }
                }
            },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("People Lists") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            if (!account.isReadOnly) {
                FloatingActionButton(onClick = { showCreateDialog = true }) {
                    Icon(Icons.Default.Add, "Create List")
                }
            }
        },
    ) { padding ->
        if (selectedList != null) {
            ListDetailView(
                list = selectedList!!,
                account = account,
                localCache = localCache,
                relayManager = relayManager,
                onBack = { selectedList = null },
                onNavigateToProfile = onNavigateToProfile,
                onListUpdated = {
                    scope.launch {
                        refreshPeopleLists(account.pubKeyHex, localCache) { peopleLists = it }
                    }
                },
            )
        } else {
            if (peopleLists.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        Icons.Default.List,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "No people lists yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Create a list to organize people you follow",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding =
                        androidx.compose.foundation.layout
                            .PaddingValues(16.dp),
                ) {
                    items(peopleLists, key = { it.addressId }) { list ->
                        PeopleListCard(
                            list = list,
                            onClick = { selectedList = list },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PeopleListCard(
    list: PeopleListDisplayData,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.People,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    list.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                if (!list.description.isNullOrBlank()) {
                    Text(
                        list.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
                Text(
                    "${list.memberCount} members",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListDetailView(
    list: PeopleListDisplayData,
    account: AccountState.LoggedIn,
    localCache: IosLocalCache,
    relayManager: IosRelayConnectionManager,
    onBack: () -> Unit,
    onNavigateToProfile: ((String) -> Unit)? = null,
    onListUpdated: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val members =
        remember(list) {
            list.event
                .publicMembers()
                .filterIsInstance<UserTag>()
                .map { it.pubKey }
        }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(list.title) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "Back")
                }
            },
        )

        if (!list.description.isNullOrBlank()) {
            Text(
                list.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        HorizontalDivider()

        if (members.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("No members yet", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding =
                    androidx.compose.foundation.layout
                        .PaddingValues(8.dp),
            ) {
                items(members, key = { it }) { pubKeyHex ->
                    val user = localCache.getUserIfExists(pubKeyHex)
                    val displayName = user?.toBestDisplayName() ?: pubKeyHex.take(16) + "..."
                    val pictureUrl = user?.profilePicture()

                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                                .then(
                                    if (onNavigateToProfile != null) {
                                        Modifier.clickable { onNavigateToProfile(pubKeyHex) }
                                    } else {
                                        Modifier
                                    },
                                ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        UserAvatar(
                            userHex = pubKeyHex,
                            pictureUrl = pictureUrl,
                            size = 40.dp,
                            contentDescription = "Profile",
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        if (!account.isReadOnly) {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        try {
                                            val updated =
                                                PeopleListEvent.removeUser(
                                                    earlierVersion = list.event,
                                                    pubKeyHex = pubKeyHex,
                                                    isUserPrivate = false,
                                                    signer = account.signer,
                                                )
                                            localCache.consume(updated, null)
                                            relayManager.broadcastToAll(updated)
                                            onListUpdated()
                                        } catch (e: Exception) {
                                            if (e is kotlinx.coroutines.CancellationException) throw e
                                        }
                                    }
                                },
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Remove",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateListDialog(
    onDismiss: () -> Unit,
    onCreate: (title: String, description: String) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create People List") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("List Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(title, description) },
                enabled = title.isNotBlank(),
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

/**
 * Scan the local cache for PeopleListEvent events authored by the user.
 */
private fun refreshPeopleLists(
    pubKeyHex: String,
    localCache: IosLocalCache,
    onResult: (List<PeopleListDisplayData>) -> Unit,
) {
    val lists =
        localCache
            .findPeopleListsByAuthor(pubKeyHex)
            .mapNotNull { note ->
                val event = note.event as? PeopleListEvent ?: return@mapNotNull null
                PeopleListDisplayData(
                    addressId = note.idHex,
                    dTag = event.dTag() ?: "",
                    title = event.titleOrName() ?: event.dTag() ?: "Untitled",
                    description = event.description(),
                    imageUrl = event.image(),
                    memberCount = event.publicMembers().size,
                    event = event,
                )
            }.sortedBy { it.title.lowercase() }
    onResult(lists)
}
