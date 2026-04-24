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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.IAccount
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.commons.viewmodels.ChatNewMessageState
import com.vitorpamplona.amethyst.commons.viewmodels.ChatroomFeedViewModel
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.amethyst.desktop.model.DesktopIAccount
import com.vitorpamplona.amethyst.desktop.network.DesktopRelayConnectionManager
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey
import kotlinx.coroutines.CoroutineScope
import java.awt.Cursor

private val isMacOS = System.getProperty("os.name").lowercase().contains("mac")

/**
 * Desktop DM screen with two layout modes:
 *
 * - **Split mode** (compactMode = false): Side-by-side with conversation list (280dp) + chat pane.
 *   Used in single-pane layout where there's plenty of horizontal space.
 *
 * - **Compact mode** (compactMode = true): Stacked navigation — full-width contact list OR
 *   full-width chat. Used in multi-deck columns where width is limited.
 */
@Composable
fun DesktopMessagesScreen(
    account: IAccount,
    cacheProvider: ICacheProvider,
    relayManager: DesktopRelayConnectionManager,
    localCache: DesktopLocalCache,
    compactMode: Boolean = false,
    onNavigateToProfile: (String) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val accountRelays = com.vitorpamplona.amethyst.desktop.ui.relay.LocalAccountRelays.current
    var showDmRelayPicker by remember { mutableStateOf(false) }
    val listState =
        remember(account) {
            ChatroomListState(account, cacheProvider, relayManager, localCache, scope)
        }
    val selectedRoom by listState.selectedRoom.collectAsState()
    val listFocusRequester = remember { FocusRequester() }
    var showNewDmDialog by remember { mutableStateOf(false) }

    // Shared keyboard shortcuts
    val keyHandler =
        Modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            val isModifier = if (isMacOS) event.isMetaPressed else event.isCtrlPressed

            when {
                event.key == Key.Escape -> {
                    listState.clearSelection()
                    true
                }

                event.key == Key.N && isModifier && event.isShiftPressed -> {
                    showNewDmDialog = true
                    true
                }

                event.key == Key.R && isModifier && event.isShiftPressed -> {
                    showDmRelayPicker = true
                    true
                }

                else -> {
                    false
                }
            }
        }

    if (compactMode) {
        CompactMessagesContent(
            selectedRoom = selectedRoom,
            listState = listState,
            account = account,
            cacheProvider = cacheProvider,
            scope = scope,
            onNavigateToProfile = onNavigateToProfile,
            listFocusRequester = listFocusRequester,
            onShowNewDm = { showNewDmDialog = true },
            onShowRelayPicker = { showDmRelayPicker = true },
            keyHandler = keyHandler,
        )
    } else {
        SplitMessagesContent(
            selectedRoom = selectedRoom,
            listState = listState,
            account = account,
            cacheProvider = cacheProvider,
            scope = scope,
            onNavigateToProfile = onNavigateToProfile,
            listFocusRequester = listFocusRequester,
            onShowNewDm = { showNewDmDialog = true },
            onShowRelayPicker = { showDmRelayPicker = true },
            keyHandler = keyHandler,
        )
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

    if (showDmRelayPicker && accountRelays != null) {
        val pickerRelays =
            remember {
                androidx.compose.runtime.mutableStateListOf<com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl>().also {
                    it.addAll(accountRelays.dmRelayList.value.sortedBy { r -> r.url })
                }
            }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDmRelayPicker = false },
            title = { androidx.compose.material3.Text("DM Relays") },
            text = {
                com.vitorpamplona.amethyst.desktop.ui.relay.DmRelayEditor(
                    dmRelays = accountRelays.dmRelayList,
                    signer = account.signer,
                    onPublish = { event ->
                        relayManager.broadcastToAll(event)
                        accountRelays.consumePublishedEvent(event)
                        accountRelays.setDmRelays(pickerRelays.toSet())
                    },
                    onDmRelaysUpdated = { accountRelays.setDmRelays(it) },
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showDmRelayPicker = false }) {
                    androidx.compose.material3.Text("Close")
                }
            },
        )
    }
}

/**
 * Compact (stacked) layout for deck columns.
 * Shows either the contact list OR the chat, never both.
 */
@Composable
private fun CompactMessagesContent(
    selectedRoom: ChatroomKey?,
    listState: ChatroomListState,
    account: IAccount,
    cacheProvider: ICacheProvider,
    scope: CoroutineScope,
    onNavigateToProfile: (String) -> Unit,
    listFocusRequester: FocusRequester,
    onShowNewDm: () -> Unit,
    onShowRelayPicker: () -> Unit = {},
    keyHandler: Modifier,
) {
    Box(modifier = Modifier.fillMaxSize().then(keyHandler)) {
        val currentRoom = selectedRoom
        if (currentRoom != null) {
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
                onBack = { listState.clearSelection() },
            )
        } else {
            ConversationListPane(
                state = listState,
                selectedRoom = selectedRoom,
                onConversationSelected = { listState.selectRoom(it) },
                onNewConversation = onShowNewDm,
                onShowRelayPicker = onShowRelayPicker,
                focusRequester = listFocusRequester,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Split (side-by-side) layout for single-pane mode.
 * Contact list (280dp) + divider + chat pane (flex).
 */
@Composable
private fun SplitMessagesContent(
    selectedRoom: ChatroomKey?,
    listState: ChatroomListState,
    account: IAccount,
    cacheProvider: ICacheProvider,
    scope: CoroutineScope,
    onNavigateToProfile: (String) -> Unit,
    listFocusRequester: FocusRequester,
    onShowNewDm: () -> Unit,
    onShowRelayPicker: () -> Unit = {},
    keyHandler: Modifier,
) {
    // Draggable split: the conversation list width is user-adjustable and
    // persisted so it survives app restarts.
    var listWidth by remember {
        androidx.compose.runtime.mutableStateOf(
            com.vitorpamplona.amethyst.desktop.DesktopPreferences.messagesListWidthDp.dp,
        )
    }
    val density = androidx.compose.ui.platform.LocalDensity.current

    Row(modifier = Modifier.fillMaxSize().then(keyHandler)) {
        ConversationListPane(
            state = listState,
            selectedRoom = selectedRoom,
            onConversationSelected = { listState.selectRoom(it) },
            onNewConversation = onShowNewDm,
            onShowRelayPicker = onShowRelayPicker,
            focusRequester = listFocusRequester,
            modifier = Modifier.width(listWidth),
        )

        MessagesDraggableDivider(
            onDrag = { deltaPx ->
                val deltaDp = with(density) { deltaPx.toDp() }
                val next = (listWidth + deltaDp).coerceIn(220.dp, 480.dp)
                listWidth = next
                com.vitorpamplona.amethyst.desktop.DesktopPreferences.messagesListWidthDp = next.value
            },
        )

        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surface),
        ) {
            val currentRoom = selectedRoom
            if (currentRoom != null) {
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
                EmptyConversationState()
            }
        }
    }
}

/**
 * Shown when no conversation is selected in the split layout right pane.
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
                MaterialSymbols.Email,
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

/**
 * Draggable vertical divider between the conversation list pane and the chat
 * pane. The hit area is split in half horizontally: the left half takes the
 * conversation list's surfaceContainer fill, the right half takes the chat
 * pane's surface fill, so the divider reads as a continuation of the two panes
 * instead of a single contrasting stripe. Cursor flips to the horizontal
 * resize arrow on hover.
 */
@Composable
private fun MessagesDraggableDivider(onDrag: (Float) -> Unit) {
    Row(
        modifier =
            Modifier
                .width(12.dp)
                .fillMaxHeight()
                .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x)
                    }
                },
    ) {
        Box(
            modifier =
                Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceContainer),
        )
        Box(
            modifier =
                Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surface),
        )
    }
}
