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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.Reply
import com.vitorpamplona.amethyst.commons.icons.Repost
import com.vitorpamplona.amethyst.commons.icons.Zap
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.icons.symbols.rememberMaterialSymbolPainter
import com.vitorpamplona.amethyst.commons.moderation.notifications.NotificationKinds
import com.vitorpamplona.amethyst.commons.moderation.notifications.PreferencesNotificationReadState
import com.vitorpamplona.amethyst.commons.moderation.notifications.PreferencesNotificationSettings
import com.vitorpamplona.amethyst.commons.moderation.notifications.nowEpochSeconds
import com.vitorpamplona.amethyst.commons.state.EventCollectionState
import com.vitorpamplona.amethyst.commons.ui.components.EmptyState
import com.vitorpamplona.amethyst.commons.ui.components.LoadingState
import com.vitorpamplona.amethyst.commons.ui.components.UserAvatar
import com.vitorpamplona.amethyst.desktop.account.AccountState
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.amethyst.desktop.network.DesktopRelayConnectionManager
import com.vitorpamplona.amethyst.desktop.subscriptions.DesktopRelaySubscriptionsCoordinator
import com.vitorpamplona.amethyst.desktop.subscriptions.createNotificationsSubscription
import com.vitorpamplona.amethyst.desktop.subscriptions.rememberSubscription
import com.vitorpamplona.amethyst.desktop.ui.components.ToggleableTimeAgoText
import com.vitorpamplona.amethyst.desktop.ui.notifications.LocalNotificationReadState
import com.vitorpamplona.amethyst.desktop.ui.notifications.LocalNotificationSettings
import com.vitorpamplona.amethyst.desktop.ui.notifications.NotificationFilter
import com.vitorpamplona.amethyst.desktop.ui.notifications.NotificationGroup
import com.vitorpamplona.amethyst.desktop.ui.notifications.groupNotifications
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArrayOrNull
import com.vitorpamplona.quartz.nip04Dm.messages.PrivateDmEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip17Dm.files.ChatMessageEncryptedFileHeaderEvent
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.nip61Nutzaps.nutzap.NutzapEvent

/**
 * Notification types for display.
 */
sealed class NotificationItem(
    open val event: Event,
    open val timestamp: Long,
) {
    data class Mention(
        override val event: Event,
        override val timestamp: Long,
    ) : NotificationItem(event, timestamp)

    data class Reply(
        override val event: Event,
        override val timestamp: Long,
    ) : NotificationItem(event, timestamp)

    data class Reaction(
        override val event: Event,
        override val timestamp: Long,
        val content: String,
    ) : NotificationItem(event, timestamp)

    data class Repost(
        override val event: Event,
        override val timestamp: Long,
    ) : NotificationItem(event, timestamp)

    data class Zap(
        override val event: Event,
        override val timestamp: Long,
        val amount: Long?,
    ) : NotificationItem(event, timestamp)

    /**
     * Encrypted direct message notification. `body` remains empty until the
     * decryption pipeline (NIP-04 signer / NIP-17 gift-wrap unwrap) is wired
     * — never populate with raw ciphertext.
     */
    data class Dm(
        override val event: Event,
        override val timestamp: Long,
    ) : NotificationItem(event, timestamp)
}

/**
 * The pubkey we should display + fetch metadata for. For NIP-57 zap
 * receipts the outer `event.pubKey` is the LNURL provider — the actual
 * zap sender lives in the nested zap request. For gift-wrapped DMs
 * the outer pubkey is an ephemeral key (not the real sender); until
 * decryption lands we fall back to `event.pubKey` which at least gives
 * a stable fallback avatar.
 */
val NotificationItem.effectiveAuthorPubKey: String
    get() =
        when (val e = event) {
            is LnZapEvent -> e.zapRequest?.pubKey ?: e.pubKey
            else -> event.pubKey
        }

/**
 * Route a raw Nostr event to the correct [NotificationItem] variant based
 * on kind. Returns null for kinds we don't render — the caller drops those.
 */
private fun classifyNotification(event: Event): NotificationItem? =
    when (event) {
        is ReactionEvent -> NotificationItem.Reaction(event, event.createdAt, event.content)
        is RepostEvent, is GenericRepostEvent -> NotificationItem.Repost(event, event.createdAt)
        is LnZapEvent -> NotificationItem.Zap(event, event.createdAt, event.amount?.toLong())
        // Nutzaps: treat like a zap; sats amount extraction requires the Cashu
        // token proof and is deferred to when Desktop renders zap detail.
        is NutzapEvent -> NotificationItem.Zap(event, event.createdAt, null)
        is TextNoteEvent -> {
            val isReply = event.tags.any { it.size > 1 && it[0] == "e" }
            if (isReply) {
                NotificationItem.Reply(event, event.createdAt)
            } else {
                NotificationItem.Mention(event, event.createdAt)
            }
        }
        // NIP-22 threaded comments are reply-shaped.
        is CommentEvent -> NotificationItem.Reply(event, event.createdAt)
        // NIP-28 channel messages read like public mentions.
        is ChannelMessageEvent -> NotificationItem.Mention(event, event.createdAt)
        // DMs (NIP-04 legacy + NIP-17 gift-wrap + rumor + file-header).
        is PrivateDmEvent,
        is ChatMessageEvent,
        is GiftWrapEvent,
        is ChatMessageEncryptedFileHeaderEvent,
        -> NotificationItem.Dm(event, event.createdAt)
        // Unknown kind — drop.
        else -> null
    }

@Composable
fun NotificationsScreen(
    relayManager: DesktopRelayConnectionManager,
    localCache: DesktopLocalCache,
    account: AccountState.LoggedIn,
    subscriptionsCoordinator: DesktopRelaySubscriptionsCoordinator? = null,
    onOpenSettings: () -> Unit = {},
    onNavigateToThread: (String) -> Unit = {},
    onNavigateToProfile: (String) -> Unit = {},
    onOpenMessages: () -> Unit = {},
) {
    val relayStatuses by relayManager.relayStatuses.collectAsState()
    val connectedRelays = relayStatuses.keys
    val scope = rememberCoroutineScope()
    val notificationState =
        remember {
            EventCollectionState<NotificationItem>(
                getId = { it.event.id },
                // Freshest first. Cannot rely on insertion order because the
                // cache-seed pass iterates an unordered Set and events can
                // arrive out-of-order across multiple relays.
                sortComparator =
                    compareByDescending<NotificationItem> { it.timestamp }
                        .thenBy { it.event.id },
                maxSize = 200,
                scope = scope,
            )
        }
    val notifications by notificationState.items.collectAsState()

    // Read-state: prefer a hoisted instance from App(); fall back to a
    // locally-scoped one so the screen still works if no provider is set.
    val hoistedReadState = LocalNotificationReadState.current
    val readState =
        remember(account.pubKeyHex, hoistedReadState) {
            hoistedReadState ?: PreferencesNotificationReadState(account.pubKeyHex)
        }
    val lastReadAt by readState.lastReadAt.collectAsState()

    var activeFilter by remember { mutableStateOf(NotificationFilter.All) }

    val hoistedSettings = LocalNotificationSettings.current
    val notifSettings =
        remember(hoistedSettings) { hoistedSettings ?: PreferencesNotificationSettings() }
    val osToastsEnabled by notifSettings.enabled.collectAsState()

    // Seed from cache — walk every kind in the shared SUBSCRIPTION_KINDS list
    // and apply the same semantic accept rule the live subscription uses.
    // Ensures a fresh inbox open shows DMs / comments / nutzaps that were
    // cached during a previous session.
    LaunchedEffect(Unit) {
        val myPubKey = account.pubKeyHex
        val kinds = NotificationKinds.SUBSCRIPTION_KINDS.toSet()
        val isTargetAuthoredByMe: (String) -> Boolean = { targetId ->
            localCache.notes
                .get(targetId)
                ?.event
                ?.pubKey == myPubKey
        }
        val cached =
            localCache.notes.filterIntoSet { _, note ->
                val event = note.event ?: return@filterIntoSet false
                event.kind in kinds &&
                    NotificationKinds.tagsAnEventForUser(event, myPubKey, isTargetAuthoredByMe)
            }
        cached.forEach { note ->
            val event = note.event ?: return@forEach
            classifyNotification(event)?.let { notificationState.addItem(it) }
        }
    }

    // Load metadata for notification authors via coordinator.
    // Use effectiveAuthorPubKey so zap receipts fetch the actual zapper's
    // profile (nested in the zap request) instead of the LNURL provider's.
    LaunchedEffect(notifications, subscriptionsCoordinator) {
        if (subscriptionsCoordinator != null && notifications.isNotEmpty()) {
            val pubkeys = notifications.map { it.effectiveAuthorPubKey }.distinct()
            subscriptionsCoordinator.loadMetadataForPubkeys(pubkeys)
        }
    }

    // Track EOSE to know when initial load is complete
    var eoseReceivedCount by remember { mutableStateOf(0) }
    val initialLoadComplete = eoseReceivedCount > 0

    // Subscribe to notifications
    rememberSubscription(connectedRelays, account.pubKeyHex, relayManager = relayManager) {
        if (connectedRelays.isNotEmpty()) {
            createNotificationsSubscription(
                relays = connectedRelays,
                pubKeyHex = account.pubKeyHex,
                onEvent = { event, _, relay, _ ->
                    subscriptionsCoordinator?.consumeEvent(event, relay)

                    // Semantic accept rule (shared with Android via commons).
                    // Rejects reactions/reposts that carry a spurious p=me
                    // tag but don't actually target one of my notes.
                    val myPubKey = account.pubKeyHex
                    val accepts =
                        NotificationKinds.tagsAnEventForUser(
                            event = event,
                            myPubKeyHex = myPubKey,
                            isTargetAuthoredByMe = { targetId ->
                                localCache.notes
                                    .get(targetId)
                                    ?.event
                                    ?.pubKey == myPubKey
                            },
                        )
                    if (!accepts) return@createNotificationsSubscription

                    val notification =
                        classifyNotification(event)
                            ?: return@createNotificationsSubscription
                    notificationState.addItem(notification)
                },
                onEose = { _, _ ->
                    eoseReceivedCount++
                },
            )
        } else {
            null
        }
    }

    // Advance last-read on entry and whenever new items arrive while the
    // inbox stays composed. Simple heuristic: any time the item count grows,
    // the user is looking at them.
    LaunchedEffect(notifications.size) {
        readState.markAsRead(nowEpochSeconds())
    }

    // Metadata version — recompose author names/pictures as they load.
    val metadataVersion by localCache.metadataVersion.collectAsState()

    // Distinct + filter (memoized by inputs)
    val distinct = remember(notifications) { notifications.distinctBy { it.event.id } }
    val counts =
        remember(distinct) {
            NotificationFilter.entries.associateWith { f -> distinct.count { f.accepts(it) } }
        }
    val filtered = remember(distinct, activeFilter) { distinct.filter { activeFilter.accepts(it) } }
    val groups = remember(filtered) { groupNotifications(filtered) }

    ReadingColumn {
        NotificationsHeader(
            title = "Notifications",
            connectedRelayCount = connectedRelays.size,
            onRefresh = { relayManager.connect() },
            onOpenSettings = onOpenSettings,
        )

        if (!osToastsEnabled) {
            OsNotificationsBanner(onEnable = onOpenSettings)
        }

        FilterTabsRow(
            active = activeFilter,
            counts = counts,
            onSelect = { activeFilter = it },
        )

        HorizontalDivider()

        when {
            connectedRelays.isEmpty() -> {
                LoadingState("Connecting to relays...")
            }
            distinct.isEmpty() && !initialLoadComplete -> {
                LoadingState("Loading notifications...")
            }
            distinct.isEmpty() && initialLoadComplete -> {
                EmptyState(
                    title = "No notifications yet",
                    description = "When someone interacts with your posts, you'll see it here",
                    onRefresh = { relayManager.connect() },
                )
            }
            groups.isEmpty() -> {
                EmptyState(
                    title = "Nothing in this filter",
                    description = "Try another tab or refresh.",
                    onRefresh = { relayManager.connect() },
                )
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = readingHorizontalPadding()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(groups, key = { it.id }, contentType = { it::class }) { group ->
                        NotificationGroupCard(
                            group = group,
                            lastReadAt = lastReadAt,
                            localCache = localCache,
                            metadataVersion = metadataVersion,
                            onNavigateToThread = onNavigateToThread,
                            onNavigateToProfile = onNavigateToProfile,
                            onOpenMessages = onOpenMessages,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationsHeader(
    title: String,
    connectedRelayCount: Int,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = readingHorizontalPadding(), vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onOpenSettings, modifier = Modifier.size(32.dp)) {
                Icon(
                    rememberMaterialSymbolPainter(MaterialSymbols.Tune),
                    contentDescription = "Notification settings",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(onClick = onRefresh, modifier = Modifier.size(32.dp)) {
                Icon(
                    rememberMaterialSymbolPainter(MaterialSymbols.Refresh),
                    contentDescription = "Refresh ($connectedRelayCount relays connected)",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun FilterTabsRow(
    active: NotificationFilter,
    counts: Map<NotificationFilter, Int>,
    onSelect: (NotificationFilter) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = readingHorizontalPadding() - 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        NotificationFilter.entries.forEach { f ->
            FilterChip(
                label = f.label,
                count = counts[f] ?: 0,
                selected = f == active,
                onClick = { onSelect(f) },
            )
        }
    }
}

@Composable
private fun FilterChip(
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg =
        if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    val fg =
        if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    Row(
        modifier =
            Modifier
                .clickable(onClick = onClick)
                .background(bg, RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = fg,
        )
        if (count > 0) {
            Text(
                if (count > 99) "99+" else count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = fg.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun NotificationGroupCard(
    group: NotificationGroup,
    lastReadAt: Long,
    localCache: DesktopLocalCache,
    metadataVersion: Long,
    onNavigateToThread: (String) -> Unit,
    onNavigateToProfile: (String) -> Unit,
    onOpenMessages: () -> Unit = {},
) {
    when (group) {
        is NotificationGroup.Single ->
            NotificationCard(
                notification = group.item,
                lastReadAt = lastReadAt,
                localCache = localCache,
                metadataVersion = metadataVersion,
                onNavigateToThread = onNavigateToThread,
                onNavigateToProfile = onNavigateToProfile,
                onOpenMessages = onOpenMessages,
            )
        is NotificationGroup.ReactionsOn -> {
            val newest = group.items.first()
            val reactors = remember(group.items) { group.items.map { it.event.pubKey }.distinct() }
            AggregateCard(
                icon = rememberMaterialSymbolPainter(MaterialSymbols.Favorite),
                tint = MaterialTheme.colorScheme.tertiary,
                title = "${group.items.size} reactions",
                subtitle = "on your post",
                timestamp = newest.timestamp,
                unread = newest.timestamp > lastReadAt,
                targetNoteId = group.targetNoteId,
                reactorPubKeys = reactors,
                localCache = localCache,
                metadataVersion = metadataVersion,
                onNavigateToThread = onNavigateToThread,
                onNavigateToProfile = onNavigateToProfile,
            )
        }
        is NotificationGroup.RepostsOn -> {
            val newest = group.items.first()
            val reposters = remember(group.items) { group.items.map { it.event.pubKey }.distinct() }
            AggregateCard(
                icon = rememberVectorPainter(Repost),
                tint = MaterialTheme.colorScheme.primary,
                title = "${group.items.size} reposts",
                subtitle = "of your post",
                timestamp = newest.timestamp,
                unread = newest.timestamp > lastReadAt,
                targetNoteId = group.targetNoteId,
                reactorPubKeys = reposters,
                localCache = localCache,
                metadataVersion = metadataVersion,
                onNavigateToThread = onNavigateToThread,
                onNavigateToProfile = onNavigateToProfile,
            )
        }
    }
}

@Composable
private fun AggregateCard(
    icon: androidx.compose.ui.graphics.painter.Painter,
    tint: androidx.compose.ui.graphics.Color,
    title: String,
    subtitle: String,
    timestamp: Long,
    unread: Boolean,
    targetNoteId: String,
    reactorPubKeys: List<String>,
    localCache: DesktopLocalCache,
    metadataVersion: Long,
    onNavigateToThread: (String) -> Unit,
    onNavigateToProfile: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val targetNote = remember(targetNoteId, metadataVersion) { localCache.notes.get(targetNoteId) }
    val targetPreview =
        remember(targetNote, metadataVersion) {
            targetNote
                ?.event
                ?.content
                ?.take(200)
                ?.replace("\n", " ")
        }

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onNavigateToThread(targetNoteId) },
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (unread) UnreadDot()
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(8.dp))
                Column(modifier = Modifier.padding(end = 8.dp).weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
                ToggleableTimeAgoText(
                    timestamp = timestamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
                Spacer(Modifier.size(4.dp))
                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        rememberMaterialSymbolPainter(
                            if (expanded) MaterialSymbols.ExpandLess else MaterialSymbols.ExpandMore,
                        ),
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            // Compact avatar preview (always shown when there's more than one reactor)
            if (reactorPubKeys.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy((-4).dp),
                ) {
                    reactorPubKeys.take(5).forEach { pk ->
                        val user = remember(pk, metadataVersion) { localCache.getUserIfExists(pk) }
                        UserAvatar(
                            userHex = pk,
                            pictureUrl = user?.profilePicture(),
                            size = 20.dp,
                            modifier = Modifier.clickable { onNavigateToProfile(pk) },
                        )
                    }
                    if (reactorPubKeys.size > 5) {
                        Spacer(Modifier.size(6.dp))
                        Text(
                            "+${reactorPubKeys.size - 5}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Expanded state: reactor list + note preview
            if (expanded) {
                Spacer(Modifier.height(10.dp))
                if (!targetPreview.isNullOrBlank()) {
                    Text(
                        text = "\"$targetPreview\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 3,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                reactorPubKeys.forEach { pk ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateToProfile(pk) }
                                .padding(vertical = 3.dp),
                    ) {
                        val user = remember(pk, metadataVersion) { localCache.getUserIfExists(pk) }
                        UserAvatar(
                            userHex = pk,
                            pictureUrl = user?.profilePicture(),
                            size = 22.dp,
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(
                            user?.toBestDisplayName() ?: pk.take(12),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OsNotificationsBanner(onEnable: () -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = readingHorizontalPadding(), vertical = 6.dp)
                .background(
                    MaterialTheme.colorScheme.tertiaryContainer,
                    androidx.compose.foundation.shape
                        .RoundedCornerShape(8.dp),
                ).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(end = 12.dp).weight(1f),
        ) {
            Icon(
                rememberMaterialSymbolPainter(MaterialSymbols.Notifications),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(8.dp))
            Column {
                Text(
                    "Get notified when someone interacts with you",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    "Turn on OS notifications to hear about zaps, replies, and mentions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                )
            }
        }
        androidx.compose.material3.TextButton(onClick = onEnable) {
            Text("Set up")
        }
    }
}

@Composable
private fun UnreadDot() {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier = Modifier.size(10.dp).padding(end = 2.dp)) {
        drawCircle(color = color, radius = 4.dp.toPx(), center = Offset(4.dp.toPx(), size.height / 2))
    }
    Spacer(Modifier.size(4.dp))
}

@Composable
fun NotificationCard(
    notification: NotificationItem,
    lastReadAt: Long = 0L,
    localCache: DesktopLocalCache? = null,
    metadataVersion: Long = 0L,
    onNavigateToThread: (String) -> Unit = {},
    onNavigateToProfile: (String) -> Unit = {},
    onOpenMessages: () -> Unit = {},
) {
    val (icon, label, color) =
        when (notification) {
            is NotificationItem.Mention -> {
                Triple(
                    rememberMaterialSymbolPainter(MaterialSymbols.Favorite),
                    "mentioned you",
                    MaterialTheme.colorScheme.primary,
                )
            }

            is NotificationItem.Reply -> {
                Triple(rememberVectorPainter(Reply), "replied", MaterialTheme.colorScheme.secondary)
            }

            is NotificationItem.Reaction -> {
                Triple(
                    rememberMaterialSymbolPainter(MaterialSymbols.Favorite),
                    "reacted ${notification.content}",
                    MaterialTheme.colorScheme.tertiary,
                )
            }

            is NotificationItem.Repost -> {
                Triple(rememberVectorPainter(Repost), "reposted", MaterialTheme.colorScheme.primary)
            }

            is NotificationItem.Zap -> {
                val amountText = notification.amount?.let { " ${it / 1000} sats" } ?: ""
                Triple(rememberVectorPainter(Zap), "zapped$amountText", MaterialTheme.colorScheme.primary)
            }

            is NotificationItem.Dm -> {
                Triple(
                    rememberMaterialSymbolPainter(MaterialSymbols.Mail),
                    "sent you an encrypted message",
                    MaterialTheme.colorScheme.primary,
                )
            }
        }

    // For zap receipts the outer event.pubKey is the LNURL provider — the
    // actual zap sender lives in the nested zap request. effectiveAuthorPubKey
    // returns the right one per kind.
    val pk = notification.effectiveAuthorPubKey
    val user = remember(pk, metadataVersion, localCache) { localCache?.getUserIfExists(pk) }
    val displayName =
        remember(user, metadataVersion, pk) {
            user?.toBestDisplayName()
                ?: pk.hexToByteArrayOrNull()?.toNpub()?.take(12)
                ?: pk.take(12)
        }
    val pictureUrl = remember(user, metadataVersion) { user?.profilePicture() }

    val unread by remember(notification.timestamp, lastReadAt) {
        derivedStateOf { notification.timestamp > lastReadAt }
    }

    // Target note id for click-through: reactions/reposts/replies reference an
    // `e` tag; for mentions we fall back to the notification event itself.
    val clickTarget =
        remember(notification) {
            notification.event.tags
                .firstOrNull { it.size > 1 && it[0] == "e" }
                ?.get(1)
                ?: notification.event.id
        }

    // DMs open the Messages column; everything else opens the target thread.
    val onCardClick: () -> Unit =
        if (notification is NotificationItem.Dm) {
            onOpenMessages
        } else {
            { onNavigateToThread(clickTarget) }
        }

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onCardClick),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (unread) UnreadDot()
                    UserAvatar(
                        userHex = pk,
                        pictureUrl = pictureUrl,
                        size = 28.dp,
                        modifier =
                            Modifier.clickable(
                                onClick = { onNavigateToProfile(pk) },
                            ),
                    )
                    Icon(
                        icon,
                        contentDescription = label,
                        tint = color,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = "$displayName $label",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                ToggleableTimeAgoText(
                    timestamp = notification.timestamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }

            if (notification is NotificationItem.Mention ||
                notification is NotificationItem.Reply
            ) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = notification.event.content.take(200),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                )
            }
        }
    }
}
