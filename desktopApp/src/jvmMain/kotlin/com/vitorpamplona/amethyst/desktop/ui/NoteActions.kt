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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.vitorpamplona.amethyst.commons.icons.Bookmark
import com.vitorpamplona.amethyst.commons.icons.BookmarkFilled
import com.vitorpamplona.amethyst.commons.icons.Reply
import com.vitorpamplona.amethyst.commons.icons.Repost
import com.vitorpamplona.amethyst.commons.icons.Zap
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.nip18Reposts.RepostAction
import com.vitorpamplona.amethyst.commons.model.nip25Reactions.ReactionAction
import com.vitorpamplona.amethyst.commons.model.nip51Bookmarks.BookmarkAction
import com.vitorpamplona.amethyst.commons.model.nip57Zaps.ZapAction
import com.vitorpamplona.amethyst.commons.service.lnurl.LightningAddressResolver
import com.vitorpamplona.amethyst.commons.ui.components.UserAvatar
import com.vitorpamplona.amethyst.commons.util.toZapAmount
import com.vitorpamplona.amethyst.desktop.account.AccountState
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.amethyst.desktop.network.DesktopHttpClient
import com.vitorpamplona.amethyst.desktop.network.DesktopRelayConnectionManager
import com.vitorpamplona.amethyst.desktop.nwc.NwcPaymentHandler
import com.vitorpamplona.amethyst.desktop.ui.note.ShareMenu
import com.vitorpamplona.amethyst.desktop.ui.note.rememberShareMenuState
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip47WalletConnect.Nip47WalletConnect
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.BookmarkListEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.nip88Polls.response.PollResponseEvent
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

private val ZAP_AMOUNTS = listOf(21L, 100L, 500L, 1000L, 5000L, 10000L)

/**
 * Mutually exclusive popup state for note action bar.
 * Only one popup can be open at a time.
 */
sealed class ActivePopup {
    data object None : ActivePopup()

    data object ZapReceipts : ActivePopup()

    data object Reactions : ActivePopup()

    data object EmojiPicker : ActivePopup()

    data object RepostOptions : ActivePopup()

    data object Boosts : ActivePopup()
}

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
@Immutable
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
                            label = { Text(amount.toZapAmount()) },
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
                Text("Zap ${selectedAmount.toZapAmount()} sats")
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
                Text("${totalAmount.toZapAmount()} sats")
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
                                text = "${receipt.amountSats.toZapAmount()} sats",
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
 * Floating popup showing zap receipts from a Note's zaps map.
 * Uses Popup + ElevatedCard for rich scrollable content.
 */
@Composable
fun ZapReceiptsPopup(
    note: Note,
    localCache: DesktopLocalCache,
    relayManager: DesktopRelayConnectionManager,
    onDismiss: () -> Unit,
    onNavigateToProfile: (String) -> Unit = {},
) {
    var metadataVersion by remember { mutableIntStateOf(0) }

    // Fetch missing metadata for zap senders
    LaunchedEffect(note.idHex) {
        val pubKeys =
            note.zaps.keys
                .mapNotNull { it.event?.pubKey }
                .distinct()
                .filter { localCache.getUserIfExists(it)?.profilePicture() == null }
        if (pubKeys.isNotEmpty()) {
            fetchMetadataForUsers(pubKeys, relayManager, localCache) { metadataVersion++ }
        }
    }

    @Suppress("UNUSED_EXPRESSION")
    metadataVersion

    data class ZapEntry(
        val pubKey: String,
        val pictureUrl: String?,
        val name: String,
        val amount: Long,
        val message: String?,
    )

    val zapEntries =
        remember(note.zaps, metadataVersion) {
            note.zaps
                .mapNotNull { (request, receipt) ->
                    val pubKey = request.event?.pubKey ?: return@mapNotNull null
                    val user = request.author
                    val name = user?.toBestDisplayName() ?: pubKey.take(12)
                    val pictureUrl = user?.profilePicture()
                    val amount =
                        (receipt?.event as? LnZapEvent)?.amount?.toLong()
                            ?: return@mapNotNull null
                    val message = request.event?.content?.ifBlank { null }
                    ZapEntry(pubKey, pictureUrl, name, amount, message)
                }.sortedByDescending { it.amount }
        }

    val totalSats = remember(zapEntries) { zapEntries.sumOf { it.amount } }

    Popup(
        alignment = Alignment.TopCenter,
        offset = IntOffset(0, -40),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        ElevatedCard(
            modifier = Modifier.widthIn(max = 280.dp),
        ) {
            Column(
                modifier =
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .heightIn(max = 300.dp)
                        .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (zapEntries.isEmpty()) {
                    Text(
                        "No zaps yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    // Header: total sats
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            Zap,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            "${totalSats.toZapAmount()} sats",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    HorizontalDivider()

                    // Sorted receipts
                    zapEntries.take(10).forEach { entry ->
                        Row(
                            modifier =
                                Modifier.fillMaxWidth().clickable {
                                    onDismiss()
                                    onNavigateToProfile(entry.pubKey)
                                },
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            UserAvatar(
                                userHex = entry.pubKey,
                                pictureUrl = entry.pictureUrl,
                                size = 24.dp,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                if (!entry.message.isNullOrBlank()) {
                                    Text(
                                        text = entry.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                }
                            }
                            Text(
                                text = "${entry.amount.toZapAmount()} sats",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    if (zapEntries.size > 10) {
                        Text(
                            text = "and ${zapEntries.size - 10} more...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Floating popup showing reactions grouped by emoji from a Note's reactions map.
 * Uses Popup + ElevatedCard for rich scrollable content.
 */
@Composable
fun ReactionsPopup(
    note: Note,
    localCache: DesktopLocalCache,
    relayManager: DesktopRelayConnectionManager,
    onDismiss: () -> Unit,
    onNavigateToProfile: (String) -> Unit = {},
) {
    var metadataVersion by remember { mutableIntStateOf(0) }

    LaunchedEffect(note.idHex) {
        val pubKeys =
            note.reactions.values
                .flatten()
                .mapNotNull { it.event?.pubKey }
                .distinct()
                .filter { localCache.getUserIfExists(it)?.profilePicture() == null }
        if (pubKeys.isNotEmpty()) {
            fetchMetadataForUsers(pubKeys, relayManager, localCache) { metadataVersion++ }
        }
    }

    @Suppress("UNUSED_EXPRESSION")
    metadataVersion

    val totalCount = remember(note.reactions, metadataVersion) { note.countReactions() }

    Popup(
        alignment = Alignment.TopCenter,
        offset = IntOffset(0, -40),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        ElevatedCard(
            modifier = Modifier.widthIn(max = 280.dp),
        ) {
            Column(
                modifier =
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .heightIn(max = 300.dp)
                        .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (note.reactions.isEmpty()) {
                    Text(
                        "No reactions yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    // Header: total count
                    Text(
                        "$totalCount reaction${if (totalCount != 1) "s" else ""}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    HorizontalDivider()

                    // Group by emoji
                    note.reactions.forEach { (emoji, reactionNotes) ->
                        val displayEmoji = if (emoji == "+") "\u2764\ufe0f" else emoji
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    displayEmoji,
                                    fontSize = 16.sp,
                                )
                                Text(
                                    "${reactionNotes.size}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            // Sender avatars + names
                            reactionNotes.take(5).forEach { reactionNote ->
                                val pubKey = reactionNote.event?.pubKey ?: return@forEach
                                val user = reactionNote.author
                                val senderName = user?.toBestDisplayName() ?: pubKey.take(12)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier =
                                        Modifier.padding(start = 24.dp).clickable {
                                            onDismiss()
                                            onNavigateToProfile(pubKey)
                                        },
                                ) {
                                    UserAvatar(
                                        userHex = pubKey,
                                        pictureUrl = user?.profilePicture(),
                                        size = 20.dp,
                                    )
                                    Text(
                                        text = senderName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (reactionNotes.size > 5) {
                                Text(
                                    text = "and ${reactionNotes.size - 5} more...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 24.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Floating popup showing who boosted (reposted) a note.
 * Shows kind 6/1621 reposts only (matching Android — quotes are not aggregated on Note).
 * Uses Popup + ElevatedCard for rich scrollable content.
 */
@Composable
fun BoostsPopup(
    note: Note,
    localCache: DesktopLocalCache,
    relayManager: DesktopRelayConnectionManager,
    onDismiss: () -> Unit,
    onNavigateToThread: (String) -> Unit = {},
    onNavigateToProfile: (String) -> Unit = {},
) {
    var metadataVersion by remember { mutableIntStateOf(0) }

    data class BoostEntry(
        val pubKey: String,
        val pictureUrl: String?,
        val name: String,
    )

    LaunchedEffect(note.idHex) {
        val pubKeys =
            note.boosts
                .mapNotNull { it.event?.pubKey }
                .distinct()
                .filter { localCache.getUserIfExists(it)?.profilePicture() == null }
        if (pubKeys.isNotEmpty()) {
            fetchMetadataForUsers(pubKeys, relayManager, localCache) { metadataVersion++ }
        }
    }

    @Suppress("UNUSED_EXPRESSION")
    metadataVersion

    val boostEntries =
        remember(note.boosts, metadataVersion) {
            note.boosts.mapNotNull { boostNote ->
                val pubKey = boostNote.event?.pubKey ?: return@mapNotNull null
                val user = boostNote.author
                BoostEntry(pubKey, user?.profilePicture(), user?.toBestDisplayName() ?: pubKey.take(12))
            }
        }

    Popup(
        alignment = Alignment.TopCenter,
        offset = IntOffset(0, -40),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        ElevatedCard(
            modifier = Modifier.widthIn(max = 280.dp),
        ) {
            Column(
                modifier =
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .heightIn(max = 300.dp)
                        .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (boostEntries.isEmpty()) {
                    Text(
                        "No reposts yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        "${boostEntries.size} repost${if (boostEntries.size != 1) "s" else ""}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    HorizontalDivider()

                    boostEntries.take(10).forEach { entry ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier =
                                Modifier.clickable {
                                    onDismiss()
                                    onNavigateToProfile(entry.pubKey)
                                },
                        ) {
                            UserAvatar(userHex = entry.pubKey, pictureUrl = entry.pictureUrl, size = 24.dp)
                            Text(entry.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    if (boostEntries.size > 10) {
                        Text(
                            text = "and ${boostEntries.size - 10} more...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Fetches metadata for multiple users in a single subscription.
 */
@OptIn(DelicateCoroutinesApi::class)
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
                object : SubscriptionListener {
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

private val EMOJI_OPTIONS = listOf("+", "\u2764\ufe0f", "\ud83e\udd19", "\ud83d\udd25", "\ud83d\udc40", "\ud83d\ude02")

/**
 * Action buttons row for a note (react, reply, repost, zap, bookmark).
 * Supports click (action), long-press (view details popup), and right-click (customize).
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun NoteActionsRow(
    event: Event,
    relayManager: DesktopRelayConnectionManager,
    localCache: DesktopLocalCache,
    account: AccountState.LoggedIn,
    onReplyClick: () -> Unit,
    onZapFeedback: (ZapFeedback) -> Unit,
    modifier: Modifier = Modifier,
    note: Note? = null,
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
    onNavigateToThread: (String) -> Unit = {},
    onNavigateToProfile: (String) -> Unit = {},
) {
    var isLiked by remember { mutableStateOf(false) }
    var isReposted by remember { mutableStateOf(false) }
    var localReactionCount by remember(reactionCount) { mutableStateOf(reactionCount) }
    var localRepostCount by remember(repostCount) { mutableStateOf(repostCount) }
    var isZapping by remember { mutableStateOf(false) }
    var showZapDialog by remember { mutableStateOf(false) }
    var showZapReceiptsDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Mutually exclusive popup state
    var activePopup by remember { mutableStateOf<ActivePopup>(ActivePopup.None) }

    // Quote compose state
    var quoteEvent by remember { mutableStateOf<Event?>(null) }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Reply button with count — long-press = same as click (open thread)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier =
                    Modifier
                        .size(32.dp)
                        .combinedClickable(
                            onClick = onReplyClick,
                            onLongClick = onReplyClick,
                            indication = ripple(bounded = false, radius = 16.dp),
                            interactionSource = remember { MutableInteractionSource() },
                        ),
                contentAlignment = Alignment.Center,
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

        // Like button with count — long-press = reactions popup, right-click = emoji picker
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                Box(
                    modifier =
                        Modifier
                            .size(32.dp)
                            .combinedClickable(
                                onClick = {
                                    if (!isLiked) {
                                        scope.launch {
                                            reactToNote(
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
                                onLongClick = {
                                    if (note != null) {
                                        activePopup = ActivePopup.Reactions
                                    }
                                },
                                indication = ripple(bounded = false, radius = 16.dp),
                                interactionSource = remember { MutableInteractionSource() },
                            ).onPointerEvent(PointerEventType.Press) { pointerEvent ->
                                if (pointerEvent.buttons.isSecondaryPressed) {
                                    activePopup = ActivePopup.EmojiPicker
                                }
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (isLiked) MaterialSymbols.Favorite else MaterialSymbols.FavoriteBorder,
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

                // Reactions popup (long-press)
                if (activePopup is ActivePopup.Reactions && note != null) {
                    ReactionsPopup(
                        note = note,
                        localCache = localCache,
                        relayManager = relayManager,
                        onDismiss = { activePopup = ActivePopup.None },
                        onNavigateToProfile = onNavigateToProfile,
                    )
                }

                // Emoji picker (right-click)
                DropdownMenu(
                    expanded = activePopup is ActivePopup.EmojiPicker,
                    onDismissRequest = { activePopup = ActivePopup.None },
                ) {
                    EMOJI_OPTIONS.forEach { emoji ->
                        val displayEmoji = if (emoji == "+") "\u2764\ufe0f" else emoji
                        DropdownMenuItem(
                            text = { Text(displayEmoji, fontSize = 20.sp) },
                            onClick = {
                                activePopup = ActivePopup.None
                                if (!isLiked) {
                                    scope.launch {
                                        reactToNote(
                                            event = EventHintBundle(event, null),
                                            reaction = emoji,
                                            account = account,
                                            relayManager = relayManager,
                                        )
                                        isLiked = true
                                        localReactionCount++
                                    }
                                }
                            },
                        )
                    }
                }
            }
            if (localReactionCount > 0) {
                Text(
                    text = "$localReactionCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isLiked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Repost button with count — right-click = repost options
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                Box(
                    modifier =
                        Modifier
                            .size(32.dp)
                            .combinedClickable(
                                onClick = {
                                    if (!isReposted) {
                                        scope.launch {
                                            repostNote(
                                                event = EventHintBundle(event, null),
                                                account = account,
                                                relayManager = relayManager,
                                            )
                                            isReposted = true
                                            localRepostCount++
                                        }
                                    }
                                },
                                onLongClick = {
                                    if (note != null) {
                                        activePopup = ActivePopup.Boosts
                                    }
                                },
                                indication = ripple(bounded = false, radius = 16.dp),
                                interactionSource = remember { MutableInteractionSource() },
                            ).onPointerEvent(PointerEventType.Press) { pointerEvent ->
                                if (pointerEvent.buttons.isSecondaryPressed) {
                                    activePopup = ActivePopup.RepostOptions
                                }
                            },
                    contentAlignment = Alignment.Center,
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

                // Repost options (right-click)
                DropdownMenu(
                    expanded = activePopup is ActivePopup.RepostOptions,
                    onDismissRequest = { activePopup = ActivePopup.None },
                ) {
                    DropdownMenuItem(
                        text = { Text("Repost") },
                        onClick = {
                            activePopup = ActivePopup.None
                            if (!isReposted) {
                                scope.launch {
                                    repostNote(
                                        event = EventHintBundle(event, null),
                                        account = account,
                                        relayManager = relayManager,
                                    )
                                    isReposted = true
                                    localRepostCount++
                                }
                            }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Quote") },
                        onClick = {
                            activePopup = ActivePopup.None
                            quoteEvent = event
                        },
                    )
                }

                // Boosts popup (long-press)
                if (activePopup is ActivePopup.Boosts && note != null) {
                    BoostsPopup(
                        note = note,
                        localCache = localCache,
                        relayManager = relayManager,
                        onDismiss = { activePopup = ActivePopup.None },
                        onNavigateToThread = onNavigateToThread,
                        onNavigateToProfile = onNavigateToProfile,
                    )
                }
            }
            if (localRepostCount > 0) {
                Text(
                    text = "$localRepostCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isReposted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Zap button with amount — long-press = zap receipts popup, right-click = custom zap dialog
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                    if (isZapping) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        Box(
                            modifier =
                                Modifier
                                    .size(32.dp)
                                    .combinedClickable(
                                        onClick = { showZapDialog = true },
                                        onLongClick = {
                                            if (note != null) {
                                                activePopup = ActivePopup.ZapReceipts
                                            } else {
                                                showZapReceiptsDialog = true
                                            }
                                        },
                                        indication = ripple(bounded = false, radius = 16.dp),
                                        interactionSource = remember { MutableInteractionSource() },
                                    ).onPointerEvent(PointerEventType.Press) { pointerEvent ->
                                        if (pointerEvent.buttons.isSecondaryPressed) {
                                            showZapDialog = true
                                        }
                                    },
                            contentAlignment = Alignment.Center,
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

                // Zap receipts popup (long-press)
                if (activePopup is ActivePopup.ZapReceipts && note != null) {
                    ZapReceiptsPopup(
                        note = note,
                        localCache = localCache,
                        relayManager = relayManager,
                        onDismiss = { activePopup = ActivePopup.None },
                        onNavigateToProfile = onNavigateToProfile,
                    )
                }
            }
            if (zapAmountSats > 0) {
                Text(
                    text = zapAmountSats.toZapAmount(),
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

        // Overflow menu: copy / broadcast / share links + mute / report.
        val shareMenuState = rememberShareMenuState()
        Box {
            IconButton(
                onClick = { shareMenuState.open() },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    MaterialSymbols.MoreVert,
                    contentDescription = "More actions",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            ShareMenu(
                state = shareMenuState,
                event = event,
                relayManager = relayManager,
            )
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

    // Zap receipts dialog (from clicking the amount text)
    if (showZapReceiptsDialog) {
        ZapReceiptsDialog(
            receipts = zapReceipts,
            totalAmount = zapAmountSats,
            localCache = localCache,
            relayManager = relayManager,
            onDismiss = { showZapReceiptsDialog = false },
        )
    }

    // Quote compose dialog
    if (quoteEvent != null) {
        ComposeNoteDialog(
            onDismiss = { quoteEvent = null },
            relayManager = relayManager,
            account = account,
            localCache = localCache,
            quoteOf = quoteEvent,
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
 * Casts a NIP-88 poll vote: builds a kind-1018 [PollResponseEvent] referencing [poll],
 * signs it, optimistically consumes it locally (so the tally + hasVoted gate flip
 * immediately), then broadcasts to all relays. The relay echo of the same signed event
 * is deduped by id, so no double count.
 *
 * MUST be launched on a long-lived scope (e.g. `localCache.appScope`) — never a card's
 * `rememberCoroutineScope()` — so scrolling the poll out of composition between the
 * local consume and the broadcast can't cancel the send.
 */
suspend fun voteOnPoll(
    poll: PollEvent,
    responses: Set<String>,
    account: AccountState.LoggedIn,
    relayManager: DesktopRelayConnectionManager,
    localCache: DesktopLocalCache,
) {
    if (responses.isEmpty()) return
    withContext(Dispatchers.IO) {
        val template = PollResponseEvent.build(EventHintBundle(poll), responses)
        val signed = account.signer.sign(template)
        localCache.consume(signed, null, wasVerified = true)
        // Publish to the poll's OWN declared relays (NIP-88 `relay` tags) as well as our
        // connected relays — the poll author and other viewers read votes from the poll's
        // relays, which we may not be connected to. broadcastToAll alone would lose the vote
        // for everyone but us (mirrors the read path in DesktopPollCard.responseRelays).
        val targetRelays = (poll.relays() + relayManager.connectedRelays.value).toSet()
        if (targetRelays.isNotEmpty()) {
            relayManager.publish(signed, targetRelays)
        } else {
            relayManager.broadcastToAll(signed)
        }
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
internal suspend fun zapNote(
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
        val httpClient = DesktopHttpClient.currentClient()
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
@OptIn(DelicateCoroutinesApi::class)
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
                object : SubscriptionListener {
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
