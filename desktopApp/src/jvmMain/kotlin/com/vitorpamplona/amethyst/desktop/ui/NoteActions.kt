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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.Bookmark
import com.vitorpamplona.amethyst.commons.icons.BookmarkFilled
import com.vitorpamplona.amethyst.commons.icons.Reply
import com.vitorpamplona.amethyst.commons.icons.Repost
import com.vitorpamplona.amethyst.commons.icons.Zap
import com.vitorpamplona.amethyst.commons.model.nip18Reposts.RepostAction
import com.vitorpamplona.amethyst.commons.model.nip25Reactions.ReactionAction
import com.vitorpamplona.amethyst.commons.model.nip51Bookmarks.BookmarkAction
import com.vitorpamplona.amethyst.commons.model.nip57Zaps.ZapAction
import com.vitorpamplona.amethyst.commons.services.lnurl.LightningAddressResolver
import com.vitorpamplona.amethyst.desktop.account.AccountState
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.amethyst.desktop.network.DesktopRelayConnectionManager
import com.vitorpamplona.amethyst.desktop.nwc.NwcPaymentHandler
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.IRequestListener
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NNote
import com.vitorpamplona.quartz.nip47WalletConnect.Nip47WalletConnect
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.BookmarkListEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

private val ZAP_AMOUNTS = listOf(21L, 100L, 500L, 1000L, 5000L, 10000L)

/**
 * Feedback from a zap operation for UI display.
 */
sealed class ZapFeedback {
    data class Success(
        val amountSats: Long,
    ) : ZapFeedback()

    data class ExternalWallet(
        val amountSats: Long,
    ) : ZapFeedback()

    data class Error(
        val message: String,
    ) : ZapFeedback()

    data object Timeout : ZapFeedback()

    data class NoLightningAddress(
        val pubKey: String,
    ) : ZapFeedback()
}

/**
 * Data class representing a zap receipt for display.
 */
data class ZapReceipt(
    val senderPubKey: String,
    val amountSats: Long,
    val message: String?,
    val createdAt: Long,
)

/**
 * Converts an LnZapEvent to a ZapReceipt for display.
 */
fun LnZapEvent.toZapReceipt(localCache: DesktopLocalCache): ZapReceipt? {
    val senderPubKey = zappedRequestAuthor() ?: return null
    val amountSats = amount?.toLong() ?: return null

    return ZapReceipt(
        senderPubKey = senderPubKey,
        amountSats = amountSats,
        message = zapRequest?.content?.ifBlank { null },
        createdAt = createdAt,
    )
}

/**
 * Gets display name for a pubkey, looking up from cache.
 * Falls back to shortened npub if not found.
 */
fun getDisplayName(
    pubKey: String,
    localCache: DesktopLocalCache,
): String {
    val user = localCache.getUserIfExists(pubKey) ?: return pubKey.take(12)
    return user.toBestDisplayName()
}

/**
 * Dialog for selecting zap amount and optional message.
 */
@Composable
fun ZapAmountDialog(
    onDismiss: () -> Unit,
    onZap: (Long, String) -> Unit,
) {
    var selectedAmount by remember { mutableStateOf(21L) }
    var message by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Zap") },
        text = {
            Column {
                Text(
                    "Select amount in sats",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ZAP_AMOUNTS.take(3).forEach { amount ->
                        FilterChip(
                            selected = selectedAmount == amount,
                            onClick = { selectedAmount = amount },
                            label = { Text("$amount") },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ZAP_AMOUNTS.drop(3).forEach { amount ->
                        FilterChip(
                            selected = selectedAmount == amount,
                            onClick = { selectedAmount = amount },
                            label = { Text(formatSats(amount)) },
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Message (optional)") },
                    placeholder = { Text("Add a comment...") },
                    singleLine = false,
                    maxLines = 3,
                )
            }
        },
        confirmButton = {
            Button(onClick = { onZap(selectedAmount, message) }) {
                Text("Zap ${formatSats(selectedAmount)} sats")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

private fun formatSats(amount: Long): String = if (amount >= 1000) "${amount / 1000}k" else "$amount"

/**
 * Dialog for choosing bookmark visibility (public or private).
 */
@Composable
fun BookmarkDialog(
    onDismiss: () -> Unit,
    onBookmark: (isPrivate: Boolean) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Bookmark") },
        text = {
            Column {
                Text(
                    "Choose bookmark visibility",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = true,
                        onClick = { onBookmark(false) },
                        label = { Text("Public") },
                        modifier = Modifier.weight(1f),
                    )
                    FilterChip(
                        selected = false,
                        onClick = { onBookmark(true) },
                        label = { Text("Private") },
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Private bookmarks are encrypted and only visible to you.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

/**
 * Dialog for displaying zap receipts.
 * Automatically loads missing user metadata when opened.
 */
@Composable
fun ZapReceiptsDialog(
    receipts: List<ZapReceipt>,
    totalAmount: Long,
    localCache: DesktopLocalCache,
    relayManager: DesktopRelayConnectionManager,
    onDismiss: () -> Unit,
) {
    var isLoading by remember { mutableStateOf(false) }
    // Trigger recomposition when metadata loads
    var metadataVersion by remember { mutableIntStateOf(0) }

    // Find users without metadata and load them
    LaunchedEffect(receipts) {
        val pubKeysNeedingMetadata =
            receipts
                .map { it.senderPubKey }
                .distinct()
                .filter { pubKey ->
                    val user = localCache.getUserIfExists(pubKey)
                    user?.metadataOrNull()?.flow?.value == null
                }

        if (pubKeysNeedingMetadata.isNotEmpty()) {
            isLoading = true
            fetchMetadataForUsers(pubKeysNeedingMetadata, relayManager, localCache) {
                metadataVersion++
            }
            isLoading = false
        }
    }

    // Force read metadataVersion to trigger recomposition
    @Suppress("UNUSED_EXPRESSION")
    metadataVersion

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Zap,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Text("${formatSats(totalAmount)} sats")
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
        },
        text = {
            if (receipts.isEmpty()) {
                Text(
                    "No zaps yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    receipts.sortedByDescending { it.amountSats }.take(10).forEach { receipt ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = getDisplayName(receipt.senderPubKey, localCache),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                if (!receipt.message.isNullOrBlank()) {
                                    Text(
                                        text = receipt.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                }
                            }
                            Text(
                                text = "${formatSats(receipt.amountSats)} sats",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    if (receipts.size > 10) {
                        Text(
                            text = "and ${receipts.size - 10} more...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}

/**
 * Fetches metadata for multiple users in a single subscription.
 */
private suspend fun fetchMetadataForUsers(
    pubKeys: List<String>,
    relayManager: DesktopRelayConnectionManager,
    localCache: DesktopLocalCache,
    onMetadataLoaded: () -> Unit,
) = withContext(Dispatchers.IO) {
    if (pubKeys.isEmpty()) return@withContext

    val subId = "metadata-zaps-${pubKeys.hashCode()}"
    val relays = relayManager.connectedRelays.value
    val remaining = pubKeys.toMutableSet()

    val filters =
        listOf(
            Filter(
                kinds = listOf(MetadataEvent.KIND),
                authors = pubKeys,
            ),
        )

    suspendCancellableCoroutine { continuation ->
        val timeoutJob =
            kotlinx.coroutines.GlobalScope.launch {
                kotlinx.coroutines.delay(5000) // 5 second timeout
                if (continuation.isActive) {
                    relayManager.unsubscribe(subId)
                    continuation.resume(Unit)
                }
            }

        relayManager.subscribe(
            subId = subId,
            filters = filters,
            relays = relays,
            listener =
                object : IRequestListener {
                    override fun onEvent(
                        event: Event,
                        isLive: Boolean,
                        relay: NormalizedRelayUrl,
                        forFilters: List<Filter>?,
                    ) {
                        if (event is MetadataEvent) {
                            localCache.consumeMetadata(event)
                            remaining.remove(event.pubKey)
                            onMetadataLoaded()

                            // All metadata loaded
                            if (remaining.isEmpty() && continuation.isActive) {
                                timeoutJob.cancel()
                                relayManager.unsubscribe(subId)
                                continuation.resume(Unit)
                            }
                        }
                    }

                    override fun onEose(
                        relay: NormalizedRelayUrl,
                        forFilters: List<Filter>?,
                    ) {
                        // Wait for all relays or timeout
                    }
                },
        )

        continuation.invokeOnCancellation {
            timeoutJob.cancel()
            relayManager.unsubscribe(subId)
        }
    }
}

/**
 * Action buttons row for a note (react, reply, repost, zap, bookmark).
 */
@Composable
fun NoteActionsRow(
    event: Event,
    relayManager: DesktopRelayConnectionManager,
    localCache: DesktopLocalCache,
    account: AccountState.LoggedIn,
    onReplyClick: () -> Unit,
    onZapFeedback: (ZapFeedback) -> Unit,
    modifier: Modifier = Modifier,
    zapCount: Int = 0,
    zapAmountSats: Long = 0,
    zapReceipts: List<ZapReceipt> = emptyList(),
    reactionCount: Int = 0,
    replyCount: Int = 0,
    repostCount: Int = 0,
    nwcConnection: Nip47WalletConnect.Nip47URINorm? = null,
    isBookmarked: Boolean = false,
    bookmarkList: BookmarkListEvent? = null,
    onBookmarkChanged: (BookmarkListEvent) -> Unit = {},
) {
    var isLiked by remember { mutableStateOf(false) }
    var isReposted by remember { mutableStateOf(false) }
    var localReactionCount by remember(reactionCount) { mutableStateOf(reactionCount) }
    var localRepostCount by remember(repostCount) { mutableStateOf(repostCount) }
    var isZapping by remember { mutableStateOf(false) }
    var showZapDialog by remember { mutableStateOf(false) }
    var showZapReceiptsDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Reply button with count
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onReplyClick,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Reply,
                    contentDescription = "Reply",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            if (replyCount > 0) {
                Text(
                    text = "$replyCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Like button with count
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = {
                    if (!isLiked) {
                        scope.launch {
                            reactToNote(
                                // TODO: Bring a hint to where the event came from
                                event = EventHintBundle(event, null),
                                reaction = "+",
                                account = account,
                                relayManager = relayManager,
                            )
                            isLiked = true
                            localReactionCount++
                        }
                    }
                },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (isLiked) "Unlike" else "Like",
                    tint =
                        if (isLiked) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    modifier = Modifier.size(18.dp),
                )
            }
            if (localReactionCount > 0) {
                Text(
                    text = "$localReactionCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isLiked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Repost button with count
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = {
                    if (!isReposted) {
                        scope.launch {
                            repostNote(
                                // TODO: Bring a hint to where the event came from
                                event = EventHintBundle(event, null),
                                account = account,
                                relayManager = relayManager,
                            )
                            isReposted = true
                            localRepostCount++
                        }
                    }
                },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Repost,
                    contentDescription = "Repost",
                    tint =
                        if (isReposted) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    modifier = Modifier.size(18.dp),
                )
            }
            if (localRepostCount > 0) {
                Text(
                    text = "$localRepostCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isReposted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Zap button with amount (clickable to show receipts)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                if (isZapping) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    IconButton(
                        onClick = { showZapDialog = true },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Zap,
                            contentDescription = "Zap",
                            tint =
                                if (zapAmountSats > 0) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            if (zapAmountSats > 0) {
                Text(
                    text = formatSats(zapAmountSats),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { showZapReceiptsDialog = true },
                )
            }
        }

        // Bookmark button
        var isBookmarking by remember { mutableStateOf(false) }
        var localIsBookmarked by remember(isBookmarked) { mutableStateOf(isBookmarked) }
        var showBookmarkDialog by remember { mutableStateOf(false) }

        IconButton(
            onClick = {
                if (!isBookmarking) {
                    if (localIsBookmarked) {
                        // Remove bookmark immediately
                        scope.launch {
                            isBookmarking = true
                            val newBookmarkList =
                                removeBookmark(
                                    event = event,
                                    bookmarkList = bookmarkList,
                                    account = account,
                                    relayManager = relayManager,
                                )
                            if (newBookmarkList != null) {
                                localIsBookmarked = false
                                onBookmarkChanged(newBookmarkList)
                            }
                            isBookmarking = false
                        }
                    } else {
                        // Show dialog to choose public/private
                        showBookmarkDialog = true
                    }
                }
            },
            modifier = Modifier.size(32.dp),
            enabled = !isBookmarking,
        ) {
            Icon(
                if (localIsBookmarked) BookmarkFilled else Bookmark,
                contentDescription = if (localIsBookmarked) "Remove bookmark" else "Bookmark",
                tint =
                    if (localIsBookmarked) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                modifier = Modifier.size(18.dp),
            )
        }

        // Bookmark dialog
        if (showBookmarkDialog) {
            BookmarkDialog(
                onDismiss = { showBookmarkDialog = false },
                onBookmark = { isPrivate ->
                    showBookmarkDialog = false
                    scope.launch {
                        isBookmarking = true
                        val newBookmarkList =
                            addBookmark(
                                event = event,
                                bookmarkList = bookmarkList,
                                isPrivate = isPrivate,
                                account = account,
                                relayManager = relayManager,
                            )
                        if (newBookmarkList != null) {
                            localIsBookmarked = true
                            onBookmarkChanged(newBookmarkList)
                        }
                        isBookmarking = false
                    }
                },
            )
        }

        // Overflow menu (three dots)
        var showOverflowMenu by remember { mutableStateOf(false) }
        Box {
            IconButton(
                onClick = { showOverflowMenu = true },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "More options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            DropdownMenu(
                expanded = showOverflowMenu,
                onDismissRequest = { showOverflowMenu = false },
            ) {
                DropdownMenuItem(
                    text = { Text("Copy Note Link") },
                    onClick = {
                        val noteLink = "nostr:${NNote.create(event.id)}"
                        copyToClipboard(noteLink)
                        showOverflowMenu = false
                    },
                )
                DropdownMenuItem(
                    text = { Text("Copy Event Link") },
                    onClick = {
                        val relays = relayManager.connectedRelays.value.take(3)
                        val neventLink = "nostr:${NEvent.create(event.id, event.pubKey, event.kind, relays)}"
                        copyToClipboard(neventLink)
                        showOverflowMenu = false
                    },
                )
                DropdownMenuItem(
                    text = { Text("Copy Event ID") },
                    onClick = {
                        copyToClipboard(event.id)
                        showOverflowMenu = false
                    },
                )
            }
        }
    }

    // Zap amount selection dialog
    if (showZapDialog) {
        ZapAmountDialog(
            onDismiss = { showZapDialog = false },
            onZap = { amountSats, message ->
                showZapDialog = false
                scope.launch {
                    isZapping = true
                    val feedback =
                        zapNote(
                            event = event,
                            account = account,
                            relayManager = relayManager,
                            localCache = localCache,
                            amountSats = amountSats,
                            message = message,
                            nwcConnection = nwcConnection,
                        )
                    isZapping = false
                    onZapFeedback(feedback)
                }
            },
        )
    }

    // Zap receipts dialog
    if (showZapReceiptsDialog) {
        ZapReceiptsDialog(
            receipts = zapReceipts,
            totalAmount = zapAmountSats,
            localCache = localCache,
            relayManager = relayManager,
            onDismiss = { showZapReceiptsDialog = false },
        )
    }
}

/**
 * Creates a reaction event and broadcasts to relays.
 */
private suspend fun reactToNote(
    event: EventHintBundle<Event>,
    reaction: String,
    account: AccountState.LoggedIn,
    relayManager: DesktopRelayConnectionManager,
) {
    withContext(Dispatchers.IO) {
        val signedEvent = ReactionAction.reactTo(event, reaction, account.signer)
        relayManager.broadcastToAll(signedEvent)
    }
}

/**
 * Adds an event to bookmarks (public or private).
 * Returns the new bookmark list event, or null if operation failed.
 */
private suspend fun addBookmark(
    event: Event,
    bookmarkList: BookmarkListEvent?,
    isPrivate: Boolean,
    account: AccountState.LoggedIn,
    relayManager: DesktopRelayConnectionManager,
): BookmarkListEvent? =
    withContext(Dispatchers.IO) {
        try {
            val newBookmarkList =
                if (bookmarkList != null) {
                    BookmarkAction.addBookmark(
                        existingList = bookmarkList,
                        eventId = event.id,
                        isPrivate = isPrivate,
                        signer = account.signer,
                    )
                } else {
                    BookmarkAction.createWithBookmark(
                        eventId = event.id,
                        isPrivate = isPrivate,
                        signer = account.signer,
                    )
                }

            // Broadcast to all relays
            relayManager.broadcastToAll(newBookmarkList)

            newBookmarkList
        } catch (e: Exception) {
            println("Failed to add bookmark: ${e.message}")
            null
        }
    }

/**
 * Removes an event from bookmarks (checks both public and private).
 * Returns the new bookmark list event, or null if operation failed.
 */
private suspend fun removeBookmark(
    event: Event,
    bookmarkList: BookmarkListEvent?,
    account: AccountState.LoggedIn,
    relayManager: DesktopRelayConnectionManager,
): BookmarkListEvent? =
    withContext(Dispatchers.IO) {
        try {
            if (bookmarkList == null) return@withContext null

            val newBookmarkList =
                BookmarkAction.removeBookmark(
                    existingList = bookmarkList,
                    eventId = event.id,
                    signer = account.signer,
                )

            // Broadcast to all relays
            relayManager.broadcastToAll(newBookmarkList)

            newBookmarkList
        } catch (e: Exception) {
            println("Failed to remove bookmark: ${e.message}")
            null
        }
    }

/**
 * Creates a repost event and broadcasts to relays.
 */
private suspend fun repostNote(
    event: EventHintBundle<Event>,
    account: AccountState.LoggedIn,
    relayManager: DesktopRelayConnectionManager,
) {
    withContext(Dispatchers.IO) {
        val signedEvent = RepostAction.repost(event, account.signer)
        relayManager.broadcastToAll(signedEvent)
    }
}

/**
 * Creates a zap request and pays via NWC or opens external wallet.
 * Returns feedback for UI display.
 */
private suspend fun zapNote(
    event: Event,
    account: AccountState.LoggedIn,
    relayManager: DesktopRelayConnectionManager,
    localCache: DesktopLocalCache,
    amountSats: Long,
    message: String = "",
    nwcConnection: Nip47WalletConnect.Nip47URINorm? = null,
): ZapFeedback =
    withContext(Dispatchers.IO) {
        // Get author's lightning address from cache
        var user = localCache.getUserIfExists(event.pubKey)
        var lnAddress = user?.lnAddress()

        // TODO: Use UserFinderFilterAssemblerSubscription pattern from Amethyst
        // to proactively load metadata when zap button is displayed.
        // For now, fetch on-demand if missing.
        if (lnAddress == null) {
            lnAddress = fetchUserLightningAddress(event.pubKey, relayManager, localCache)
        }

        if (lnAddress == null) {
            return@withContext ZapFeedback.NoLightningAddress(event.pubKey.take(8))
        }

        // Create HTTP client and resolver
        val httpClient =
            OkHttpClient
                .Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
        val resolver = LightningAddressResolver(httpClient)

        // Get relay URLs for zap request
        val relays = relayManager.connectedRelays.value

        // Fetch invoice
        val result =
            ZapAction.fetchZapInvoice(
                targetEvent = event,
                lnAddress = lnAddress,
                amountSats = amountSats,
                message = message,
                relays = relays,
                signer = account.signer,
                resolver = resolver,
            )

        when (result) {
            is ZapAction.ZapResult.Invoice -> {
                // Pay via NWC if configured, otherwise open external wallet
                if (nwcConnection != null) {
                    // Get/create Note for tracking the payment
                    val zappedNote = localCache.getOrCreateNote(event.id)
                    if (zappedNote.event == null) {
                        zappedNote.loadEvent(event, localCache.getOrCreateUser(event.pubKey), emptyList())
                    }

                    val paymentHandler = NwcPaymentHandler(relayManager, localCache)
                    when (val paymentResult = paymentHandler.payInvoice(result.bolt11, nwcConnection, zappedNote)) {
                        is NwcPaymentHandler.PaymentResult.Success -> {
                            ZapFeedback.Success(amountSats)
                        }

                        is NwcPaymentHandler.PaymentResult.Error -> {
                            ZapFeedback.Error(paymentResult.message)
                        }

                        is NwcPaymentHandler.PaymentResult.Timeout -> {
                            ZapFeedback.Timeout
                        }
                    }
                } else {
                    // Fallback: open lightning: URI in external wallet
                    openLightningUri(result.bolt11)
                    ZapFeedback.ExternalWallet(amountSats)
                }
            }

            is ZapAction.ZapResult.Error -> {
                ZapFeedback.Error(result.message)
            }
        }
    }

private fun openLightningUri(bolt11: String) {
    val uri = "lightning:$bolt11"
    try {
        val os = System.getProperty("os.name").lowercase()
        val command =
            when {
                os.contains("mac") -> arrayOf("open", uri)
                os.contains("win") -> arrayOf("cmd", "/c", "start", uri)
                else -> arrayOf("xdg-open", uri) // Linux
            }
        Runtime.getRuntime().exec(command)
    } catch (e: Exception) {
        println("Failed to open lightning URI: ${e.message}")
        println("Invoice: $bolt11")
    }
}

/**
 * Fetches user metadata on-demand to get lightning address.
 * Returns the lightning address if found, null otherwise.
 */
private suspend fun fetchUserLightningAddress(
    pubKey: String,
    relayManager: DesktopRelayConnectionManager,
    localCache: DesktopLocalCache,
): String? =
    suspendCancellableCoroutine { continuation ->
        val relays = relayManager.connectedRelays.value
        if (relays.isEmpty()) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }

        val subId = "meta-zap-${pubKey.take(8)}"
        var resumed = false

        // Set timeout
        val timeoutJob =
            kotlinx.coroutines.GlobalScope.launch {
                kotlinx.coroutines.delay(5000) // 5 second timeout
                if (!resumed) {
                    resumed = true
                    relayManager.unsubscribe(subId)
                    continuation.resume(null)
                }
            }

        val filters =
            listOf(
                Filter(
                    kinds = listOf(MetadataEvent.KIND),
                    authors = listOf(pubKey),
                    limit = 1,
                ),
            )

        // Subscribe to fetch metadata
        relayManager.subscribe(
            subId = subId,
            filters = filters,
            relays = relays,
            listener =
                object : IRequestListener {
                    override fun onEvent(
                        event: Event,
                        isLive: Boolean,
                        relay: NormalizedRelayUrl,
                        forFilters: List<Filter>?,
                    ) {
                        if (event is MetadataEvent && !resumed) {
                            localCache.consumeMetadata(event)
                            val user = localCache.getUserIfExists(pubKey)
                            val lnAddress = user?.lnAddress()
                            if (lnAddress != null && !resumed) {
                                resumed = true
                                timeoutJob.cancel()
                                relayManager.unsubscribe(subId)
                                continuation.resume(lnAddress)
                            }
                        }
                    }

                    override fun onEose(
                        relay: NormalizedRelayUrl,
                        forFilters: List<Filter>?,
                    ) {
                        // If we get EOSE without finding address, wait for timeout or other relays
                    }
                },
        )

        continuation.invokeOnCancellation {
            timeoutJob.cancel()
            relayManager.unsubscribe(subId)
        }
    }

/**
 * Copies text to the system clipboard.
 */
private fun copyToClipboard(text: String) {
    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
    clipboard.setContents(StringSelection(text), null)
}
