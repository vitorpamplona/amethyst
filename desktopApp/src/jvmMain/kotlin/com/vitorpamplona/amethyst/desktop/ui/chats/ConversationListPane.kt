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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.ui.components.UserAvatar
import com.vitorpamplona.amethyst.commons.util.toTimeAgo
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey
import kotlinx.coroutines.launch

/**
 * Left panel of the DM split-pane layout (280dp fixed width).
 *
 * Displays two tabs (Known / New) with conversation cards showing:
 * - User avatar, display name, last message preview, timestamp
 * - Unread indicator (blue dot) for new messages
 * - Selected conversation highlighting
 * - "+" button for starting new DMs
 *
 * @param state ChatroomListState managing the conversation list
 * @param selectedRoom Currently selected room key (for highlighting)
 * @param onConversationSelected Called when a conversation card is tapped
 * @param onNewConversation Called when the "+" button is tapped
 */
@Composable
fun ConversationListPane(
    state: ChatroomListState,
    selectedRoom: ChatroomKey?,
    onConversationSelected: (ChatroomKey) -> Unit,
    onNewConversation: () -> Unit = {},
    focusRequester: FocusRequester = remember { FocusRequester() },
    modifier: Modifier = Modifier,
) {
    val selectedTab by state.selectedTab.collectAsState()
    val knownRooms by state.knownRooms.collectAsState()
    val newRooms by state.newRooms.collectAsState()
    val scope = rememberCoroutineScope()

    val currentList =
        when (selectedTab) {
            ConversationTab.KNOWN -> knownRooms
            ConversationTab.NEW -> newRooms
        }

    // Track focused index for keyboard navigation
    var focusedIndex by remember { mutableIntStateOf(-1) }
    val listScrollState = rememberLazyListState()

    // Derive the focused index from the selected room when it changes externally
    val selectedIndex by remember(selectedRoom, currentList) {
        derivedStateOf {
            currentList.indexOfFirst { it.roomKey == selectedRoom }
        }
    }

    Column(
        modifier =
            modifier
                .width(280.dp)
                .fillMaxHeight()
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                    when (event.key) {
                        Key.DirectionDown -> {
                            if (currentList.isNotEmpty()) {
                                val newIndex =
                                    if (focusedIndex < 0) {
                                        0
                                    } else {
                                        (focusedIndex + 1).coerceAtMost(currentList.size - 1)
                                    }
                                focusedIndex = newIndex
                                scope.launch { listScrollState.animateScrollToItem(newIndex) }
                            }
                            true
                        }

                        Key.DirectionUp -> {
                            if (currentList.isNotEmpty()) {
                                val newIndex =
                                    if (focusedIndex < 0) {
                                        currentList.size - 1
                                    } else {
                                        (focusedIndex - 1).coerceAtLeast(0)
                                    }
                                focusedIndex = newIndex
                                scope.launch { listScrollState.animateScrollToItem(newIndex) }
                            }
                            true
                        }

                        Key.Enter -> {
                            if (focusedIndex in currentList.indices) {
                                onConversationSelected(currentList[focusedIndex].roomKey)
                            }
                            true
                        }

                        else -> {
                            false
                        }
                    }
                },
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Messages",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )

            IconButton(
                onClick = onNewConversation,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "New conversation",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        // Tab selector
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = selectedTab == ConversationTab.KNOWN,
                onClick = {
                    state.selectTab(ConversationTab.KNOWN)
                    focusedIndex = -1
                },
                label = {
                    Text("Known (${knownRooms.size})")
                },
            )
            FilterChip(
                selected = selectedTab == ConversationTab.NEW,
                onClick = {
                    state.selectTab(ConversationTab.NEW)
                    focusedIndex = -1
                },
                label = {
                    Text("New (${newRooms.size})")
                },
            )
        }

        Spacer(Modifier.height(8.dp))

        // Conversation list
        if (currentList.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text =
                        if (selectedTab == ConversationTab.KNOWN) {
                            "No conversations yet"
                        } else {
                            "No new messages"
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                state = listScrollState,
                modifier = Modifier.weight(1f),
            ) {
                items(currentList, key = { it.roomKey.hashCode() }) { item ->
                    val index = currentList.indexOf(item)
                    val isFocused = index == focusedIndex
                    ConversationCard(
                        item = item,
                        isSelected = selectedRoom == item.roomKey,
                        isFocused = isFocused,
                        onClick = {
                            focusedIndex = index
                            onConversationSelected(item.roomKey)
                        },
                    )
                }
            }
        }
    }
}

/**
 * A single conversation card in the list.
 */
@Composable
private fun ConversationCard(
    item: ConversationItem,
    isSelected: Boolean,
    isFocused: Boolean = false,
    onClick: () -> Unit,
) {
    val backgroundColor =
        when {
            isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            isFocused -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else -> MaterialTheme.colorScheme.surface
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .background(backgroundColor)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Unread indicator
        if (item.hasUnread) {
            Box(
                modifier =
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
            )
            Spacer(Modifier.width(6.dp))
        } else {
            Spacer(Modifier.width(14.dp))
        }

        // Avatar
        val firstUser = item.users.firstOrNull()
        if (firstUser != null) {
            UserAvatar(
                userHex = firstUser.pubkeyHex,
                pictureUrl = firstUser.profilePicture(),
                size = 40.dp,
            )
        } else if (item.isGroup) {
            Icon(
                Icons.Default.Group,
                contentDescription = "Group",
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.width(10.dp))

        // Name, preview, timestamp
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )

                if (item.lastMessageTimestamp > 0) {
                    Text(
                        text = item.lastMessageTimestamp.toTimeAgo(withDot = false),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }

            if (item.lastMessagePreview.isNotEmpty()) {
                Text(
                    text = item.lastMessagePreview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Group indicator
            if (item.isGroup) {
                Text(
                    text = "Group (${item.users.size + 1})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                )
            }
        }
    }
}
