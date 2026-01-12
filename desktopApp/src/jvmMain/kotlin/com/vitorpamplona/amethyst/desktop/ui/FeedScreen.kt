/**
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.account.AccountState
import com.vitorpamplona.amethyst.commons.state.EventCollectionState
import com.vitorpamplona.amethyst.commons.subscriptions.FeedMode
import com.vitorpamplona.amethyst.commons.subscriptions.createContactListSubscription
import com.vitorpamplona.amethyst.commons.subscriptions.createFollowingFeedSubscription
import com.vitorpamplona.amethyst.commons.subscriptions.createGlobalFeedSubscription
import com.vitorpamplona.amethyst.commons.subscriptions.rememberSubscription
import com.vitorpamplona.amethyst.commons.ui.components.LoadingState
import com.vitorpamplona.amethyst.commons.ui.note.NoteCard
import com.vitorpamplona.amethyst.commons.util.toNoteDisplayData
import com.vitorpamplona.amethyst.desktop.network.DesktopRelayConnectionManager
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent

/**
 * Note card with action buttons.
 */
@Composable
fun FeedNoteCard(
    event: Event,
    relayManager: DesktopRelayConnectionManager,
    account: AccountState.LoggedIn?,
    onReply: () -> Unit,
    onNavigateToProfile: (String) -> Unit = {},
    onNavigateToThread: (String) -> Unit = {},
) {
    Column(
        modifier =
            Modifier.clickable {
                onNavigateToThread(event.id)
            },
    ) {
        NoteCard(
            note = event.toNoteDisplayData(),
            onAuthorClick = onNavigateToProfile,
        )

        // Action buttons (only if logged in)
        if (account != null) {
            NoteActionsRow(
                event = event,
                relayManager = relayManager,
                account = account,
                onReplyClick = onReply,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
fun FeedScreen(
    relayManager: DesktopRelayConnectionManager,
    account: AccountState.LoggedIn? = null,
    onCompose: () -> Unit = {},
    onNavigateToProfile: (String) -> Unit = {},
    onNavigateToThread: (String) -> Unit = {},
) {
    val connectedRelays by relayManager.connectedRelays.collectAsState()
    val relayStatuses by relayManager.relayStatuses.collectAsState()
    val scope = rememberCoroutineScope()
    val eventState =
        remember {
            EventCollectionState<Event>(
                getId = { it.id },
                sortComparator = compareByDescending { it.createdAt },
                maxSize = 200,
                scope = scope,
            )
        }
    val events by eventState.items.collectAsState()
    var replyToEvent by remember { mutableStateOf<Event?>(null) }
    var feedMode by remember { mutableStateOf(FeedMode.GLOBAL) }
    var followedUsers by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Load followed users for Following feed mode
    rememberSubscription(relayStatuses, account, feedMode, relayManager = relayManager) {
        val configuredRelays = relayStatuses.keys
        if (configuredRelays.isNotEmpty() && account != null && feedMode == FeedMode.FOLLOWING) {
            createContactListSubscription(
                relays = configuredRelays,
                pubKeyHex = account.pubKeyHex,
                onEvent = { event, _, _, _ ->
                    if (event is ContactListEvent) {
                        followedUsers = event.verifiedFollowKeySet()
                    }
                },
            )
        } else {
            null
        }
    }

    // Clear events when feed mode changes
    remember(feedMode) {
        eventState.clear()
    }

    // Subscribe to feed based on mode
    rememberSubscription(relayStatuses, feedMode, followedUsers, relayManager = relayManager) {
        val configuredRelays = relayStatuses.keys
        if (configuredRelays.isEmpty()) {
            return@rememberSubscription null
        }

        when (feedMode) {
            FeedMode.GLOBAL -> {
                createGlobalFeedSubscription(
                    relays = configuredRelays,
                    onEvent = { event, _, _, _ ->
                        eventState.addItem(event)
                    },
                )
            }
            FeedMode.FOLLOWING -> {
                if (followedUsers.isNotEmpty()) {
                    createFollowingFeedSubscription(
                        relays = configuredRelays,
                        followedUsers = followedUsers.toList(),
                        onEvent = { event, _, _, _ ->
                            eventState.addItem(event)
                        },
                    )
                } else {
                    null
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header with compose button
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        if (feedMode == FeedMode.GLOBAL) "Global Feed" else "Following Feed",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )

                    // Feed mode selector
                    if (account != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilterChip(
                                selected = feedMode == FeedMode.GLOBAL,
                                onClick = { feedMode = FeedMode.GLOBAL },
                                label = { Text("Global") },
                            )
                            FilterChip(
                                selected = feedMode == FeedMode.FOLLOWING,
                                onClick = { feedMode = FeedMode.FOLLOWING },
                                label = { Text("Following") },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${connectedRelays.size} relays connected",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (feedMode == FeedMode.FOLLOWING) {
                        Text(
                            " • ${followedUsers.size} followed",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = { relayManager.connect() },
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            // New Post button (primary action)
            Button(
                onClick = onCompose,
                enabled = account != null && !account.isReadOnly,
            ) {
                Icon(Icons.Default.Add, "New Post", Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("New Post")
            }
        }

        Spacer(Modifier.height(8.dp))

        if (connectedRelays.isEmpty()) {
            LoadingState("Connecting to relays...")
        } else if (feedMode == FeedMode.FOLLOWING && followedUsers.isEmpty()) {
            LoadingState("Loading followed users...")
        } else if (events.isEmpty()) {
            LoadingState(
                if (feedMode == FeedMode.FOLLOWING) {
                    "No notes from followed users yet"
                } else {
                    "Loading notes..."
                },
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(events, key = { it.id }) { event ->
                    FeedNoteCard(
                        event = event,
                        relayManager = relayManager,
                        account = account,
                        onReply = { replyToEvent = event },
                        onNavigateToProfile = onNavigateToProfile,
                        onNavigateToThread = onNavigateToThread,
                    )
                }
            }
        }

        // Reply dialog
        if (replyToEvent != null && account != null) {
            ComposeNoteDialog(
                onDismiss = { replyToEvent = null },
                relayManager = relayManager,
                account = account,
                replyTo = replyToEvent,
            )
        }
    }
}
