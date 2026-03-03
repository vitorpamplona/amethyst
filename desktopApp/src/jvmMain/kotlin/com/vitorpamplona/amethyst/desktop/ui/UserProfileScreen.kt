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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.vitorpamplona.amethyst.commons.model.nip02FollowList.FollowAction
import com.vitorpamplona.amethyst.commons.profile.ProfileBroadcastBanner
import com.vitorpamplona.amethyst.commons.profile.ProfileBroadcastStatus
import com.vitorpamplona.amethyst.commons.state.EventCollectionState
import com.vitorpamplona.amethyst.commons.state.FollowState
import com.vitorpamplona.amethyst.commons.ui.components.LoadingState
import com.vitorpamplona.amethyst.commons.ui.components.UserAvatar
import com.vitorpamplona.amethyst.desktop.account.AccountState
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.amethyst.desktop.network.DesktopRelayConnectionManager
import com.vitorpamplona.amethyst.desktop.subscriptions.DesktopRelaySubscriptionsCoordinator
import com.vitorpamplona.amethyst.desktop.subscriptions.FilterBuilders
import com.vitorpamplona.amethyst.desktop.subscriptions.SubscriptionConfig
import com.vitorpamplona.amethyst.desktop.subscriptions.createContactListSubscription
import com.vitorpamplona.amethyst.desktop.subscriptions.createMetadataSubscription
import com.vitorpamplona.amethyst.desktop.subscriptions.createUserPostsSubscription
import com.vitorpamplona.amethyst.desktop.subscriptions.rememberSubscription
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArrayOrNull
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/**
 * User profile screen showing user info, follow button, and their posts.
 */
@Composable
fun UserProfileScreen(
    pubKeyHex: String,
    relayManager: DesktopRelayConnectionManager,
    localCache: DesktopLocalCache,
    account: AccountState.LoggedIn?,
    nwcConnection: com.vitorpamplona.quartz.nip47WalletConnect.Nip47WalletConnect.Nip47URINorm? = null,
    subscriptionsCoordinator: DesktopRelaySubscriptionsCoordinator? = null,
    onBack: () -> Unit,
    onCompose: () -> Unit = {},
    onNavigateToProfile: (String) -> Unit = {},
    onZapFeedback: (ZapFeedback) -> Unit = {},
) {
    val connectedRelays by relayManager.connectedRelays.collectAsState()
    val relayStatuses by relayManager.relayStatuses.collectAsState()

    // User metadata
    var displayName by remember { mutableStateOf<String?>(null) }
    var about by remember { mutableStateOf<String?>(null) }
    var picture by remember { mutableStateOf<String?>(null) }
    var followersCount by remember { mutableStateOf(0) }
    var followingCount by remember { mutableStateOf(0) }

    // Profile editing state (only for own profile)
    val isOwnProfile = account != null && pubKeyHex == account.pubKeyHex
    var showEditDialog by remember { mutableStateOf(false) }
    var editingDisplayName by remember { mutableStateOf("") }
    var broadcastStatus by remember { mutableStateOf<ProfileBroadcastStatus>(ProfileBroadcastStatus.Idle) }
    var latestMetadataEvent by remember { mutableStateOf<MetadataEvent?>(null) }

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

    // Store the user's contact list separately for reliable access
    var myContactList by remember(account) { mutableStateOf<ContactListEvent?>(null) }
    var contactListLoaded by remember(account) { mutableStateOf(false) }
    var eoseReceivedCount by remember(account) { mutableStateOf(0) }

    // Load current user's contact list (for follow state)
    rememberSubscription(relayStatuses, account, relayManager = relayManager) {
        val configuredRelays = relayStatuses.keys
        if (configuredRelays.isNotEmpty() && account != null) {
            createContactListSubscription(
                relays = configuredRelays,
                pubKeyHex = account.pubKeyHex,
                onEvent = { event, _, _, _ ->
                    if (event is ContactListEvent) {
                        // Store the most recent contact list (by createdAt timestamp)
                        if (myContactList == null || event.createdAt > myContactList!!.createdAt) {
                            myContactList = event
                        }

                        followState.updateContactList(event, pubKeyHex)
                        contactListLoaded = true
                    }
                },
                onEose = { _, _ ->
                    eoseReceivedCount++

                    // Wait for EOSE from at least 2 relays or all relays before enabling button
                    val minEoseCount = minOf(2, configuredRelays.size)
                    if (eoseReceivedCount >= minEoseCount && !contactListLoaded) {
                        contactListLoaded = true
                    }
                },
            )
        } else {
            null
        }
    }

    // Clear posts when profile changes
    remember(pubKeyHex, retryTrigger) {
        eventState.clear()
        postsLoading = true
        postsError = null
    }

    // Subscribe to user metadata
    rememberSubscription(relayStatuses, pubKeyHex, retryTrigger, relayManager = relayManager) {
        val configuredRelays = relayStatuses.keys
        if (configuredRelays.isNotEmpty()) {
            createMetadataSubscription(
                relays = configuredRelays,
                pubKeyHex = pubKeyHex,
                onEvent = { event, _, _, _ ->
                    if (event is MetadataEvent) {
                        try {
                            val metadata = event.contactMetaData()
                            if (metadata != null) {
                                displayName = metadata.displayName ?: metadata.name
                                about = metadata.about
                                picture = metadata.picture
                            }

                            // Store MetadataEvent for editing (only for own profile)
                            if (isOwnProfile) {
                                val current = latestMetadataEvent
                                if (current == null || event.createdAt > current.createdAt) {
                                    latestMetadataEvent = event
                                }
                            }
                        } catch (e: Exception) {
                            // Ignore parse errors
                        }
                    }
                },
            )
        } else {
            null
        }
    }

    // Subscribe to profile user's contact list (for following count)
    rememberSubscription(relayStatuses, pubKeyHex, retryTrigger, relayManager = relayManager) {
        val configuredRelays = relayStatuses.keys
        if (configuredRelays.isNotEmpty()) {
            createContactListSubscription(
                relays = configuredRelays,
                pubKeyHex = pubKeyHex,
                onEvent = { event, _, _, _ ->
                    if (event is ContactListEvent) {
                        // Count the number of people this user follows
                        followingCount = event.verifiedFollowKeySet().size
                    }
                },
                onEose = { _, _ -> },
            )
        } else {
            null
        }
    }

    // Track unique followers (authors of contact lists that tag this pubkey)
    val followerAuthors = remember(pubKeyHex) { mutableSetOf<String>() }

    // Subscribe to followers (contact lists that tag this user)
    rememberSubscription(relayStatuses, pubKeyHex, retryTrigger, relayManager = relayManager) {
        val configuredRelays = relayStatuses.keys
        if (configuredRelays.isNotEmpty()) {
            // Clear previous followers when subscription restarts
            followerAuthors.clear()
            followersCount = 0

            SubscriptionConfig(
                subId = "followers-${pubKeyHex.take(8)}-${System.currentTimeMillis()}",
                filters =
                    listOf(
                        FilterBuilders.byPTags(
                            pubKeys = listOf(pubKeyHex),
                            kinds = listOf(3), // ContactListEvent
                            limit = 500,
                        ),
                    ),
                relays = configuredRelays,
                onEvent = { event, _, _, _ ->
                    // Count unique authors who follow this user
                    if (followerAuthors.add(event.pubKey)) {
                        followersCount = followerAuthors.size
                    }
                },
                onEose = { _, _ -> },
            )
        } else {
            null
        }
    }

    // Subscribe to user posts
    rememberSubscription(relayStatuses, pubKeyHex, retryTrigger, relayManager = relayManager) {
        val configuredRelays = relayStatuses.keys
        if (configuredRelays.isNotEmpty()) {
            postsLoading = true
            postsError = null
            createUserPostsSubscription(
                relays = configuredRelays,
                pubKeyHex = pubKeyHex,
                onEvent = { event, _, _, _ ->
                    eventState.addItem(event)
                },
                onEose = { _, _ ->
                    postsLoading = false
                },
            )
        } else {
            postsLoading = false
            postsError = "No relays configured"
            null
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Broadcast banner for profile updates
        ProfileBroadcastBanner(
            status = broadcastStatus,
            onTap = {
                // Clear banner on tap (could add retry logic for failed)
                if (broadcastStatus is ProfileBroadcastStatus.Success ||
                    broadcastStatus is ProfileBroadcastStatus.Failed
                ) {
                    broadcastStatus = ProfileBroadcastStatus.Idle
                }
            },
        )

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

            // Edit button for own profile
            if (isOwnProfile && account?.isReadOnly == false) {
                OutlinedButton(
                    onClick = {
                        editingDisplayName = displayName ?: ""
                        showEditDialog = true
                    },
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit profile",
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Edit Profile")
                }
            }

            // Follow/Unfollow button for other profiles
            if (account != null && !account.isReadOnly && pubKeyHex != account.pubKeyHex) {
                Column(horizontalAlignment = Alignment.End) {
                    Button(
                        onClick = {
                            scope.launch {
                                val currentStatus = followState.currentStatusOrNull()

                                followState.setFollowLoading()
                                try {
                                    val updatedEvent =
                                        if (currentStatus?.isFollowing == true) {
                                            unfollowUser(pubKeyHex, account, relayManager, myContactList)
                                        } else {
                                            followUser(pubKeyHex, account, relayManager, myContactList)
                                        }

                                    // Update both stored contact list and followState
                                    myContactList = updatedEvent
                                    followState.setFollowSuccess(updatedEvent, pubKeyHex)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    followState.setFollowError(e.message ?: "Failed to update follow status", e)
                                }
                            }
                        },
                        enabled = contactListLoaded && followState.state.value !is com.vitorpamplona.amethyst.commons.state.LoadingState.Loading,
                    ) {
                        val state = followState.state.collectAsState().value
                        val isFollowing = (state as? com.vitorpamplona.amethyst.commons.state.LoadingState.Success)?.data?.isFollowing ?: false
                        val isLoading = state is com.vitorpamplona.amethyst.commons.state.LoadingState.Loading

                        when {
                            !contactListLoaded -> {
                                androidx.compose.material3.CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Loading...")
                            }

                            isLoading -> {
                                androidx.compose.material3.CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(if (isFollowing) "Unfollowing..." else "Following...")
                            }

                            else -> {
                                Icon(
                                    if (isFollowing) Icons.Default.PersonRemove else Icons.Default.PersonAdd,
                                    contentDescription = if (isFollowing) "Unfollow" else "Follow",
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(if (isFollowing) "Unfollow" else "Follow")
                            }
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
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        // Profile picture with robohash fallback
                        UserAvatar(
                            userHex = pubKeyHex,
                            pictureUrl = picture,
                            size = 56.dp,
                            contentDescription = "Profile picture",
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                displayName ?: (pubKeyHex.hexToByteArrayOrNull()?.toNpub()?.take(20) ?: pubKeyHex.take(20)),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(4.dp))
                            val npub = pubKeyHex.hexToByteArrayOrNull()?.toNpub()
                            var copied by remember { mutableStateOf(false) }

                            // Reset copied state after delay
                            LaunchedEffect(copied) {
                                if (copied) {
                                    delay(2000)
                                    copied = false
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    (npub?.take(32) ?: pubKeyHex.take(32)) + "...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (npub != null) {
                                    IconButton(
                                        onClick = {
                                            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                                            clipboard.setContents(StringSelection(npub), null)
                                            copied = true
                                        },
                                        modifier = Modifier.size(20.dp),
                                    ) {
                                        Icon(
                                            if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                                            contentDescription = if (copied) "Copied" else "Copy npub",
                                            modifier = Modifier.size(14.dp),
                                            tint =
                                                if (copied) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (about != null) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            about!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column {
                            Text(
                                "$followersCount",
                                style = MaterialTheme.typography.titleSmall,
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
                                style = MaterialTheme.typography.titleSmall,
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

            Spacer(Modifier.height(16.dp))

            // User's posts
            Text(
                "Posts",
                style = MaterialTheme.typography.titleMedium,
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
                        items(events.distinctBy { it.id }, key = { it.id }) { event ->
                            FeedNoteCard(
                                event = event,
                                relayManager = relayManager,
                                localCache = localCache,
                                account = account,
                                nwcConnection = nwcConnection,
                                onReply = onCompose,
                                onZapFeedback = onZapFeedback,
                                onNavigateToProfile = onNavigateToProfile,
                            )
                        }
                    }
                }
            }
        }
    }

    // Edit Profile Dialog
    if (showEditDialog && account != null) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit Profile") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = editingDisplayName,
                        onValueChange = { editingDisplayName = it },
                        label = { Text("Display Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showEditDialog = false
                        scope.launch {
                            updateProfileDisplayName(
                                newDisplayName = editingDisplayName,
                                account = account,
                                relayManager = relayManager,
                                latestMetadataEvent = latestMetadataEvent,
                                currentDisplayName = displayName,
                                currentAbout = about,
                                currentPicture = picture,
                                onStatusUpdate = { broadcastStatus = it },
                                onSuccess = { displayName = editingDisplayName },
                            )
                        }
                    },
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
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
        // Use shared FollowAction from commons
        val updatedEvent = FollowAction.follow(pubKeyHex, account.signer, currentContactList)

        val connectedRelays = relayManager.connectedRelays.value
        if (connectedRelays.isEmpty()) {
            throw IllegalStateException("Cannot follow: No connected relays")
        }

        relayManager.broadcastToAll(updatedEvent)

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
        if (currentContactList != null) {
            // Use shared FollowAction from commons
            val updatedEvent = FollowAction.unfollow(pubKeyHex, account.signer, currentContactList)

            val connectedRelays = relayManager.connectedRelays.value
            if (connectedRelays.isEmpty()) {
                throw IllegalStateException("Cannot unfollow: No connected relays")
            }

            relayManager.broadcastToAll(updatedEvent)

            updatedEvent
        } else {
            throw IllegalStateException("Cannot unfollow: No contact list available")
        }
    }

/**
 * Updates the user's profile display name by creating and broadcasting a new MetadataEvent.
 */
private suspend fun updateProfileDisplayName(
    newDisplayName: String,
    account: AccountState.LoggedIn,
    relayManager: DesktopRelayConnectionManager,
    latestMetadataEvent: MetadataEvent?,
    currentDisplayName: String?,
    currentAbout: String?,
    currentPicture: String?,
    onStatusUpdate: (ProfileBroadcastStatus) -> Unit,
    onSuccess: () -> Unit,
) = withContext(Dispatchers.IO) {
    val connectedRelays = relayManager.connectedRelays.value
    if (connectedRelays.isEmpty()) {
        onStatusUpdate(ProfileBroadcastStatus.Failed("display name", "No connected relays"))
        return@withContext
    }

    val totalRelays = connectedRelays.size
    onStatusUpdate(ProfileBroadcastStatus.Broadcasting("display name", 0, totalRelays))

    try {
        // Create the new MetadataEvent
        val template =
            if (latestMetadataEvent != null) {
                MetadataEvent.updateFromPast(
                    latest = latestMetadataEvent,
                    displayName = newDisplayName,
                )
            } else {
                MetadataEvent.createNew(
                    name = currentDisplayName,
                    displayName = newDisplayName,
                    picture = currentPicture,
                    about = currentAbout,
                )
            }

        // Sign the event
        val signedEvent = account.signer.sign(template)

        // Broadcast to all relays
        relayManager.broadcastToAll(signedEvent)

        // Update progress (simplified - just show success after broadcast)
        // In a full implementation, you'd track OK responses from each relay
        onStatusUpdate(ProfileBroadcastStatus.Success("display name", totalRelays))
        onSuccess()

        // Auto-hide banner after delay
        delay(3000)
        onStatusUpdate(ProfileBroadcastStatus.Idle)
    } catch (e: Exception) {
        onStatusUpdate(ProfileBroadcastStatus.Failed("display name", e.message ?: "Unknown error"))
    }
}
