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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.account.AccountState
import com.vitorpamplona.amethyst.commons.actions.FollowAction
import com.vitorpamplona.amethyst.commons.state.EventCollectionState
import com.vitorpamplona.amethyst.commons.state.FollowState
import com.vitorpamplona.amethyst.commons.ui.components.LoadingState
import com.vitorpamplona.amethyst.desktop.network.DesktopRelayConnectionManager
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArrayOrNull
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.IRequestListener
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * User profile screen showing user info, follow button, and their posts.
 */
@Composable
fun UserProfileScreen(
    pubKeyHex: String,
    relayManager: DesktopRelayConnectionManager,
    account: AccountState.LoggedIn?,
    onBack: () -> Unit,
    onCompose: () -> Unit = {},
    onNavigateToProfile: (String) -> Unit = {},
) {
    val connectedRelays by relayManager.connectedRelays.collectAsState()
    val relayStatuses by relayManager.relayStatuses.collectAsState()

    // User metadata
    var displayName by remember { mutableStateOf<String?>(null) }
    var about by remember { mutableStateOf<String?>(null) }
    var picture by remember { mutableStateOf<String?>(null) }
    var followersCount by remember { mutableStateOf(0) }
    var followingCount by remember { mutableStateOf(0) }

    val scope = rememberCoroutineScope()

    // User's posts
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
    var postsLoading by remember { mutableStateOf(true) }
    var postsError by remember { mutableStateOf<String?>(null) }
    var retryTrigger by remember { mutableStateOf(0) }

    // Follow state
    val followState =
        remember(account) {
            FollowState(myPubKeyHex = account?.pubKeyHex ?: "")
        }

    // Load current user's contact list (for follow state)
    DisposableEffect(relayStatuses, account) {
        val configuredRelays = relayStatuses.keys
        if (configuredRelays.isNotEmpty() && account != null) {
            val contactListSubId = "my-contacts-${account.pubKeyHex}-${System.currentTimeMillis()}"
            relayManager.subscribe(
                subId = contactListSubId,
                filters =
                    listOf(
                        Filter(
                            kinds = listOf(ContactListEvent.KIND), // Kind 3
                            authors = listOf(account.pubKeyHex),
                            limit = 1,
                        ),
                    ),
                relays = configuredRelays,
                listener =
                    object : IRequestListener {
                        override fun onEvent(
                            event: Event,
                            isLive: Boolean,
                            relay: NormalizedRelayUrl,
                            forFilters: List<Filter>?,
                        ) {
                            if (event is ContactListEvent) {
                                followState.updateContactList(event, pubKeyHex)
                            }
                        }

                        override fun onEose(
                            relay: NormalizedRelayUrl,
                            forFilters: List<Filter>?,
                        ) {}
                    },
            )

            onDispose {
                relayManager.unsubscribe(contactListSubId)
            }
        } else {
            onDispose {}
        }
    }

    // Subscribe to user metadata and posts
    DisposableEffect(relayStatuses, pubKeyHex, retryTrigger) {
        val configuredRelays = relayStatuses.keys
        if (configuredRelays.isNotEmpty()) {
            postsLoading = true
            postsError = null
            eventState.clear()
            // Metadata subscription (kind 0)
            val metadataSubId = "profile-metadata-$pubKeyHex-${System.currentTimeMillis()}"
            relayManager.subscribe(
                subId = metadataSubId,
                filters =
                    listOf(
                        Filter(
                            kinds = listOf(0), // Metadata
                            authors = listOf(pubKeyHex),
                            limit = 1,
                        ),
                    ),
                relays = configuredRelays,
                listener =
                    object : IRequestListener {
                        override fun onEvent(
                            event: Event,
                            isLive: Boolean,
                            relay: NormalizedRelayUrl,
                            forFilters: List<Filter>?,
                        ) {
                            // Parse metadata JSON (simplified - full parsing in production)
                            try {
                                val content = event.content
                                displayName = extractJsonField(content, "display_name") ?: extractJsonField(content, "name")
                                about = extractJsonField(content, "about")
                                picture = extractJsonField(content, "picture")
                            } catch (e: Exception) {
                                // Ignore parse errors
                            }
                        }

                        override fun onEose(
                            relay: NormalizedRelayUrl,
                            forFilters: List<Filter>?,
                        ) {}
                    },
            )

            // Posts subscription (kind 1)
            val postsSubId = "profile-posts-$pubKeyHex-${System.currentTimeMillis()}"
            relayManager.subscribe(
                subId = postsSubId,
                filters =
                    listOf(
                        Filter(
                            kinds = listOf(TextNoteEvent.KIND),
                            authors = listOf(pubKeyHex),
                            limit = 50,
                        ),
                    ),
                relays = configuredRelays,
                listener =
                    object : IRequestListener {
                        override fun onEvent(
                            event: Event,
                            isLive: Boolean,
                            relay: NormalizedRelayUrl,
                            forFilters: List<Filter>?,
                        ) {
                            eventState.addItem(event)
                        }

                        override fun onEose(
                            relay: NormalizedRelayUrl,
                            forFilters: List<Filter>?,
                        ) {
                            // At least one relay finished sending events
                            postsLoading = false
                        }
                    },
            )

            // Set timeout for loading state
            val timeoutJob =
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
                    kotlinx.coroutines.delay(10000) // 10 second timeout
                    if (postsLoading) {
                        postsError = "Request timed out. Check relay connections."
                        postsLoading = false
                    }
                }

            onDispose {
                timeoutJob.cancel()
                relayManager.unsubscribe(metadataSubId)
                relayManager.unsubscribe(postsSubId)
            }
        } else {
            postsLoading = false
            postsError = "No relays configured"
            onDispose {}
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header with back button
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "Profile",
                    style = MaterialTheme.typography.headlineMedium,
                )
            }

            if (account != null && !account.isReadOnly && pubKeyHex != account.pubKeyHex) {
                Column(horizontalAlignment = Alignment.End) {
                    Button(
                        onClick = {
                            scope.launch {
                                followState.setFollowLoading()
                                try {
                                    val currentStatus = followState.currentStatusOrNull()
                                    val updatedEvent =
                                        if (currentStatus?.isFollowing == true) {
                                            unfollowUser(pubKeyHex, account, relayManager, currentStatus.contactList)
                                        } else {
                                            followUser(pubKeyHex, account, relayManager, currentStatus?.contactList)
                                        }
                                    followState.setFollowSuccess(updatedEvent, pubKeyHex)
                                } catch (e: Exception) {
                                    followState.setFollowError(e.message ?: "Failed to update follow status", e)
                                }
                            }
                        },
                        enabled = followState.state.value !is com.vitorpamplona.amethyst.commons.state.LoadingState.Loading,
                    ) {
                        val state = followState.state.collectAsState().value
                        val isFollowing = (state as? com.vitorpamplona.amethyst.commons.state.LoadingState.Success)?.data?.isFollowing ?: false
                        val isLoading = state is com.vitorpamplona.amethyst.commons.state.LoadingState.Loading

                        if (isLoading) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(if (isFollowing) "Unfollowing..." else "Following...")
                        } else {
                            Icon(
                                if (isFollowing) Icons.Default.PersonRemove else Icons.Default.PersonAdd,
                                contentDescription = if (isFollowing) "Unfollow" else "Follow",
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(if (isFollowing) "Unfollow" else "Follow")
                        }
                    }

                    val errorMessage =
                        followState.state
                            .collectAsState()
                            .value
                            .errorOrNull()
                    errorMessage?.let { error ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }

        if (connectedRelays.isEmpty()) {
            LoadingState("Connecting to relays...")
        } else {
            // Profile card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        // Profile picture placeholder
                        Surface(
                            modifier = Modifier.size(80.dp).clip(CircleShape),
                            color = MaterialTheme.colorScheme.primary,
                        ) {
                            // TODO: Load actual image from picture URL
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                displayName ?: (pubKeyHex.hexToByteArrayOrNull()?.toNpub()?.take(20) ?: pubKeyHex.take(20)),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                (pubKeyHex.hexToByteArrayOrNull()?.toNpub()?.take(32) ?: pubKeyHex.take(32)) + "...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    if (about != null) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            about!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Column {
                            Text(
                                "$followersCount",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "Followers",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Column {
                            Text(
                                "$followingCount",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "Following",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // User's posts
            Text(
                "Posts",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            when {
                postsError != null -> {
                    // Error state with retry
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "Failed to load posts",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                postsError!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(16.dp))
                            OutlinedButton(onClick = { retryTrigger++ }) {
                                Text("Retry")
                            }
                        }
                    }
                }
                postsLoading -> {
                    // Loading state
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            androidx.compose.material3.CircularProgressIndicator()
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "Loading posts...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                events.isEmpty() -> {
                    // Empty state (loaded but no posts)
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "No posts yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                else -> {
                    // Posts loaded successfully
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(events, key = { it.id }) { event ->
                            FeedNoteCard(
                                event = event,
                                relayManager = relayManager,
                                account = account,
                                onReply = onCompose,
                                onNavigateToProfile = onNavigateToProfile,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Simple JSON field extractor (not production-ready, just for demo).
 */
private fun extractJsonField(
    json: String,
    field: String,
): String? {
    val regex = """"$field"\s*:\s*"([^"]*)"""".toRegex()
    return regex.find(json)?.groupValues?.get(1)
}

/**
 * Follows a user by publishing an updated contact list event.
 */
private suspend fun followUser(
    pubKeyHex: String,
    account: AccountState.LoggedIn,
    relayManager: DesktopRelayConnectionManager,
    currentContactList: ContactListEvent?,
): ContactListEvent =
    withContext(Dispatchers.IO) {
        println("[UserProfile] Starting followUser: target=${pubKeyHex.take(8)}...")

        // Use shared FollowAction from commons
        val updatedEvent = FollowAction.follow(pubKeyHex, account.signer, currentContactList)

        println("[UserProfile] ContactListEvent created, broadcasting...")
        relayManager.broadcastToAll(updatedEvent)
        println("[UserProfile] Follow broadcast complete")

        updatedEvent
    }

/**
 * Unfollows a user by publishing an updated contact list event without them.
 */
private suspend fun unfollowUser(
    pubKeyHex: String,
    account: AccountState.LoggedIn,
    relayManager: DesktopRelayConnectionManager,
    currentContactList: ContactListEvent?,
): ContactListEvent =
    withContext(Dispatchers.IO) {
        println("[UserProfile] Starting unfollowUser: target=${pubKeyHex.take(8)}...")

        if (currentContactList != null) {
            println("[UserProfile] Removing from existing contact list")

            // Use shared FollowAction from commons
            val updatedEvent = FollowAction.unfollow(pubKeyHex, account.signer, currentContactList)

            println("[UserProfile] ContactListEvent updated, broadcasting...")
            relayManager.broadcastToAll(updatedEvent)
            println("[UserProfile] Unfollow broadcast complete")

            updatedEvent
        } else {
            println("[UserProfile] Error: No contact list to unfollow from")
            throw IllegalStateException("Cannot unfollow: No contact list available")
        }
    }
