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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.outlined.AddReaction
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.vitorpamplona.amethyst.commons.model.IAccount
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.commons.ui.chat.ChatMessageCompose
import com.vitorpamplona.amethyst.commons.ui.chat.ChatroomHeader
import com.vitorpamplona.amethyst.commons.ui.chat.DmBroadcastBanner
import com.vitorpamplona.amethyst.commons.ui.chat.DmBroadcastStatus
import com.vitorpamplona.amethyst.commons.ui.chat.GroupChatroomHeader
import com.vitorpamplona.amethyst.commons.ui.components.LoadingState
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedState
import com.vitorpamplona.amethyst.commons.util.toTimeAgo
import com.vitorpamplona.amethyst.commons.viewmodels.ChatNewMessageState
import com.vitorpamplona.amethyst.commons.viewmodels.ChatroomFeedViewModel
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip04Dm.messages.PrivateDmEvent
import com.vitorpamplona.quartz.nip17Dm.NIP17Factory
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey
import kotlinx.coroutines.launch

private val isMacOS = System.getProperty("os.name").lowercase().contains("mac")

/**
 * Right panel of the DM split-pane layout (flexible width).
 *
 * Displays:
 * - ChatroomHeader at top (shared component from commons)
 * - Message list (LazyColumn, reversed - newest at bottom, auto-scroll)
 * - Message input at bottom with Send button and NIP-17 toggle
 *
 * @param roomKey The chatroom key for the selected conversation
 * @param account The user's account (IAccount)
 * @param cacheProvider The cache provider for user/note lookups
 * @param feedViewModel ChatroomFeedViewModel for message data
 * @param messageState ChatNewMessageState for composition
 * @param onNavigateToProfile Called when user clicks on a profile
 */
@Composable
fun ChatPane(
    roomKey: ChatroomKey,
    account: IAccount,
    cacheProvider: ICacheProvider,
    feedViewModel: ChatroomFeedViewModel,
    messageState: ChatNewMessageState,
    dmBroadcastStatus: DmBroadcastStatus = DmBroadcastStatus.Idle,
    onNavigateToProfile: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val feedState by feedViewModel.feedState.feedContent.collectAsState()
    val messageText by messageState.message.collectAsState()
    val isNip17 by messageState.nip17.collectAsState()
    val requiresNip17 by messageState.requiresNip17.collectAsState()

    // Resolve users for the header
    val users = roomKey.users.mapNotNull { cacheProvider.getUserIfExists(it) as? User }
    val isGroup = users.size > 1

    // Load room into message state
    LaunchedEffect(roomKey) {
        messageState.load(roomKey)
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Header
        if (isGroup) {
            GroupChatroomHeader(
                users = users,
                onClick = { users.firstOrNull()?.let { onNavigateToProfile(it.pubkeyHex) } },
            )
        } else {
            users.firstOrNull()?.let { user ->
                ChatroomHeader(
                    user = user,
                    onClick = { onNavigateToProfile(user.pubkeyHex) },
                )
            } ?: run {
                // Fallback header with raw pubkey
                Text(
                    text = roomKey.users.firstOrNull()?.take(20) ?: "Unknown",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(10.dp),
                )
            }
        }

        HorizontalDivider()

        // Broadcast status banner
        DmBroadcastBanner(status = dmBroadcastStatus)

        // Message list
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (feedState) {
                is FeedState.Loading -> {
                    LoadingState("Loading messages...")
                }

                is FeedState.Empty -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "No messages yet. Send the first one!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                is FeedState.Loaded -> {
                    val loaded = feedState as FeedState.Loaded
                    val loadedState by loaded.feed.collectAsState()
                    val messages = loadedState.list

                    MessageList(
                        messages = messages,
                        account = account,
                        cacheProvider = cacheProvider,
                        onAuthorClick = onNavigateToProfile,
                        onReaction = { note, emoji ->
                            scope.launch {
                                try {
                                    sendWrappedReaction(note, emoji, roomKey, account)
                                } catch (e: Exception) {
                                    println("Failed to send reaction: ${e.message}")
                                }
                            }
                        },
                    )
                }

                is FeedState.FeedError -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "Error loading messages",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }

        HorizontalDivider()

        // Message input
        MessageInput(
            messageText = messageText.text,
            isNip17 = isNip17,
            requiresNip17 = requiresNip17,
            canSend = messageState.canSend,
            onMessageChange = { messageState.updateMessage(messageText.copy(text = it)) },
            onToggleNip17 = { messageState.toggleNip17() },
            onSend = {
                scope.launch {
                    if (messageState.send()) {
                        messageState.clear()
                    }
                }
            },
        )
    }
}

/**
 * Scrollable message list, reversed so newest messages appear at the bottom.
 */
@Composable
private fun MessageList(
    messages: List<Note>,
    account: IAccount,
    cacheProvider: ICacheProvider,
    onAuthorClick: (String) -> Unit,
    onReaction: (Note, String) -> Unit = { _, _ -> },
) {
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    LazyColumn(
        state = listState,
        reverseLayout = true,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.fillMaxSize().padding(vertical = 4.dp),
    ) {
        items(messages, key = { it.idHex }) { note ->
            val isMe = note.author?.pubkeyHex == account.pubKey
            val isDraft = note.isDraft()

            MessageWithReactions(
                note = note,
                isMe = isMe,
                isDraft = isDraft,
                account = account,
                onAuthorClick = onAuthorClick,
                onReaction = { emoji -> onReaction(note, emoji) },
            )
        }
    }
}

/**
 * Common reaction emojis for quick access.
 */
private val QUICK_REACTIONS =
    listOf(
        "\uD83D\uDC4D", // thumbs up
        "\u2764\uFE0F", // red heart
        "\uD83D\uDE02", // face with tears of joy
        "\uD83D\uDD25", // fire
    )

/**
 * Wraps a ChatMessageCompose with reaction icon in the detail row
 * and displays existing reactions below the bubble.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun MessageWithReactions(
    note: Note,
    isMe: Boolean,
    isDraft: Boolean,
    account: IAccount,
    onAuthorClick: (String) -> Unit,
    onReaction: (String) -> Unit,
) {
    var isHovered by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    val showIcon = (isHovered || showPicker) && !isDraft

    // Decrypt NIP-04 content asynchronously; NIP-17 content is already plaintext
    val event = note.event
    var decryptedContent by remember(note.idHex) { mutableStateOf<String?>(null) }

    LaunchedEffect(note.idHex, event) {
        decryptedContent =
            when (event) {
                is PrivateDmEvent -> {
                    try {
                        event.decryptContent(account.signer)
                    } catch (_: Exception) {
                        event.content
                    }
                }

                else -> {
                    event?.content
                }
            }
    }

    // Observe reaction changes
    val reactions = note.reactions

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .onPointerEvent(PointerEventType.Enter) { isHovered = true }
                .onPointerEvent(PointerEventType.Exit) { isHovered = false },
    ) {
        Column {
            ChatMessageCompose(
                note = note,
                isLoggedInUser = isMe,
                isDraft = isDraft,
                isComplete = true,
                hasDetailsToShow = reactions.isNotEmpty(),
                drawAuthorInfo = !isMe,
                onClick = { false },
                onAuthorClick = {
                    note.author?.pubkeyHex?.let { onAuthorClick(it) }
                },
                authorLine = {
                    note.author?.let { author ->
                        Text(
                            text = author.toBestDisplayName(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                detailRow = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        // Timestamp
                        note.createdAt()?.let { timestamp ->
                            Text(
                                text = timestamp.toTimeAgo(withDot = false),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            )
                        }

                        // Reaction counts
                        reactions.forEach { (emoji, notes) ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            ) {
                                Text(
                                    text =
                                        if (notes.size > 1) {
                                            "$emoji ${notes.size}"
                                        } else {
                                            emoji
                                        },
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                )
                            }
                        }

                        // AddReaction icon on hover
                        if (showIcon) {
                            Box {
                                IconButton(
                                    onClick = { showPicker = !showPicker },
                                    modifier = Modifier.size(20.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.AddReaction,
                                        contentDescription = "React",
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }

                                if (showPicker) {
                                    Popup(
                                        alignment = Alignment.TopCenter,
                                        offset = IntOffset(0, -44),
                                        onDismissRequest = { showPicker = false },
                                        properties = PopupProperties(focusable = true),
                                    ) {
                                        ReactionBar(
                                            onReaction = { emoji ->
                                                onReaction(emoji)
                                                showPicker = false
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
            ) { _ ->
                SelectionContainer {
                    Text(
                        text = decryptedContent ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

/**
 * Sends a NIP-17 gift-wrapped reaction to a note within a group DM.
 */
private suspend fun sendWrappedReaction(
    note: Note,
    emoji: String,
    roomKey: ChatroomKey,
    account: IAccount,
) {
    val event = note.event
    if (event == null) {
        println("sendWrappedReaction: note.event is null for ${note.idHex}")
        return
    }
    val eventBundle = EventHintBundle(event)
    val recipients = roomKey.users.toList()
    println("sendWrappedReaction: sending '$emoji' to ${recipients.size} recipients for event ${event.id.take(8)}")

    val result =
        NIP17Factory().createReactionWithinGroup(
            content = emoji,
            originalNote = eventBundle,
            to = recipients,
            signer = account.signer,
        )

    println("sendWrappedReaction: created ${result.wraps.size} wraps, broadcasting...")
    account.sendGiftWraps(result.wraps)
}

/**
 * Floating row of quick emoji reaction buttons.
 */
@Composable
private fun ReactionBar(onReaction: (String) -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 2.dp,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            QUICK_REACTIONS.forEach { emoji ->
                TextButton(
                    onClick = { onReaction(emoji) },
                    modifier = Modifier.size(32.dp),
                    contentPadding =
                        androidx.compose.foundation.layout
                            .PaddingValues(0.dp),
                ) {
                    Text(
                        text = emoji,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

/**
 * Message input area at the bottom of the chat pane.
 */
@Composable
private fun MessageInput(
    messageText: String,
    isNip17: Boolean,
    requiresNip17: Boolean,
    canSend: Boolean,
    onMessageChange: (String) -> Unit,
    onToggleNip17: () -> Unit,
    onSend: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = messageText,
                onValueChange = onMessageChange,
                modifier =
                    Modifier
                        .weight(1f)
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            // Cmd+Enter (Mac) or Ctrl+Enter to send
                            val hasModifier = if (isMacOS) event.isMetaPressed else event.isCtrlPressed
                            if (event.key == Key.Enter && hasModifier) {
                                if (canSend) onSend()
                                true
                            } else {
                                false
                            }
                        },
                placeholder = { Text("Message... (${if (isMacOS) "\u2318" else "Ctrl"}+Enter to send)") },
                singleLine = false,
                maxLines = 4,
                shape = RoundedCornerShape(12.dp),
            )

            IconButton(
                onClick = onSend,
                enabled = canSend,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint =
                        if (canSend) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        },
                )
            }
        }

        // NIP-17 indicator
        Spacer(Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp),
        ) {
            IconButton(
                onClick = onToggleNip17,
                enabled = !requiresNip17,
                modifier = Modifier.size(20.dp),
            ) {
                Icon(
                    imageVector = if (isNip17) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = if (isNip17) "NIP-17 (encrypted)" else "NIP-04 (legacy)",
                    modifier = Modifier.size(16.dp),
                    tint =
                        if (isNip17) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        },
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = if (isNip17) "NIP-17" else "NIP-04",
                style = MaterialTheme.typography.labelSmall,
                color =
                    if (isNip17) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    },
            )
            if (requiresNip17) {
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "(required for groups)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }
        }
    }
}
