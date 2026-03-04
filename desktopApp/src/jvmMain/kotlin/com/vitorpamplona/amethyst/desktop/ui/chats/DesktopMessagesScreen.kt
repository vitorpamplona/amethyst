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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.IAccount
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.commons.ui.chat.DmBroadcastStatus
import com.vitorpamplona.amethyst.commons.viewmodels.ChatNewMessageState
import com.vitorpamplona.amethyst.commons.viewmodels.ChatroomFeedViewModel
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.amethyst.desktop.model.DesktopIAccount
import com.vitorpamplona.amethyst.desktop.network.DesktopRelayConnectionManager

private val isMacOS = System.getProperty("os.name").lowercase().contains("mac")

/**
 * Desktop DM screen with split-pane layout.
 *
 * Left pane (280dp): ConversationListPane with Known/New tabs
 * Right pane (flex): ChatPane with messages + input, or empty state
 *
 * @param account The user's IAccount for DM operations
 * @param cacheProvider ICacheProvider for user/note lookups
 * @param onNavigateToProfile Called when navigating to a user profile
 */
@Composable
fun DesktopMessagesScreen(
    account: IAccount,
    cacheProvider: ICacheProvider,
    relayManager: DesktopRelayConnectionManager,
    localCache: DesktopLocalCache,
    onNavigateToProfile: (String) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val listState =
        remember(account) {
            ChatroomListState(account, cacheProvider, relayManager, localCache, scope)
        }
    val selectedRoom by listState.selectedRoom.collectAsState()
    val listFocusRequester = remember { FocusRequester() }
    var showNewDmDialog by remember { mutableStateOf(false) }

    Row(
        modifier =
            Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val isModifier = if (isMacOS) event.isMetaPressed else event.isCtrlPressed

                    when {
                        // Escape -> deselect conversation
                        event.key == Key.Escape -> {
                            listState.clearSelection()
                            true
                        }

                        // Cmd+Shift+N / Ctrl+Shift+N -> new DM
                        event.key == Key.N && isModifier && event.isShiftPressed -> {
                            showNewDmDialog = true
                            true
                        }

                        else -> {
                            false
                        }
                    }
                },
    ) {
        // Left pane: conversation list (280dp fixed)
        ConversationListPane(
            state = listState,
            selectedRoom = selectedRoom,
            onConversationSelected = { roomKey ->
                listState.selectRoom(roomKey)
            },
            onNewConversation = { showNewDmDialog = true },
            focusRequester = listFocusRequester,
        )

        VerticalDivider(modifier = Modifier.fillMaxHeight())

        // Right pane: chat or empty state (flex)
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            val currentRoom = selectedRoom
            if (currentRoom != null) {
                // Create feed VM and message state scoped to the selected room
                val feedViewModel =
                    remember(currentRoom) {
                        ChatroomFeedViewModel(currentRoom, account, cacheProvider)
                    }
                val messageState =
                    remember(currentRoom) {
                        ChatNewMessageState(account, cacheProvider, scope)
                    }

                val broadcastStatus =
                    if (account is DesktopIAccount) {
                        account.dmSendTracker.status
                            .collectAsState()
                            .value
                    } else {
                        DmBroadcastStatus.Idle
                    }

                ChatPane(
                    roomKey = currentRoom,
                    account = account,
                    cacheProvider = cacheProvider,
                    feedViewModel = feedViewModel,
                    messageState = messageState,
                    dmBroadcastStatus = broadcastStatus,
                    onNavigateToProfile = onNavigateToProfile,
                )
            } else {
                // Empty state
                EmptyConversationState()
            }
        }
    }

    if (showNewDmDialog) {
        NewDmDialog(
            cacheProvider = cacheProvider,
            relayManager = relayManager,
            localCache = localCache,
            onUserSelected = { roomKey ->
                listState.selectRoom(roomKey)
                showNewDmDialog = false
            },
            onDismiss = { showNewDmDialog = false },
        )
    }
}

/**
 * Shown when no conversation is selected in the right pane.
 */
@Composable
private fun EmptyConversationState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Default.Email,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.size(48.dp),
            )
            androidx.compose.foundation.layout.Spacer(
                modifier = Modifier.height(16.dp),
            )
            Text(
                "Select a conversation to start messaging",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
