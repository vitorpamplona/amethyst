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
package com.vitorpamplona.amethyst.ios.ui.chats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.IAccount
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.commons.ui.chat.ChatMessageCompose
import com.vitorpamplona.amethyst.commons.ui.chat.ChatroomHeader
import com.vitorpamplona.amethyst.commons.ui.components.LoadingState
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedState
import com.vitorpamplona.amethyst.commons.viewmodels.ChatNewMessageState
import com.vitorpamplona.amethyst.commons.viewmodels.ChatroomFeedViewModel
import com.vitorpamplona.amethyst.ios.util.toTimeAgo
import com.vitorpamplona.quartz.nip04Dm.messages.PrivateDmEvent
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey
import kotlinx.coroutines.launch

/**
 * iOS DM chat screen for a single conversation.
 *
 * Displays messages in a scrollable list with chat bubbles (using shared
 * ChatMessageCompose from commons), and a text input field at the bottom.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IosChatScreen(
    roomKey: ChatroomKey,
    account: IAccount,
    cacheProvider: ICacheProvider,
    onBack: () -> Unit,
    onNavigateToProfile: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val feedViewModel = remember(roomKey) { ChatroomFeedViewModel(roomKey, account, cacheProvider) }
    val messageState = remember(roomKey) { ChatNewMessageState(account, cacheProvider, scope) }
    val feedState by feedViewModel.feedState.feedContent.collectAsState()
    val messageText by messageState.message.collectAsState()

    LaunchedEffect(roomKey) {
        messageState.load(roomKey)
    }

    // Resolve header user
    val users = roomKey.users.mapNotNull { cacheProvider.getUserIfExists(it) }

    Column(modifier = modifier.fillMaxSize()) {
        // Top bar with back button and user info
        TopAppBar(
            title = {
                if (users.isNotEmpty()) {
                    ChatroomHeader(
                        user = users.first(),
                        onClick = { users.first().let { onNavigateToProfile(it.pubkeyHex) } },
                    )
                } else {
                    Text(
                        text = roomKey.users.firstOrNull()?.take(16) ?: "Chat",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                    )
                }
            },
        )

        HorizontalDivider()

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
                            "No messages yet. Say hello!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                is FeedState.Loaded -> {
                    val loaded = feedState as FeedState.Loaded
                    val loadedState by loaded.feed.collectAsState()
                    val messages = loadedState.list

                    IosMessageList(
                        messages = messages,
                        account = account,
                        onAuthorClick = onNavigateToProfile,
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
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = messageText.text,
                onValueChange = { messageState.updateMessage(messageText.copy(text = it)) },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message...") },
                singleLine = false,
                maxLines = 4,
                shape = RoundedCornerShape(20.dp),
            )

            IconButton(
                onClick = {
                    scope.launch {
                        if (messageState.canSend) {
                            if (messageState.send()) {
                                messageState.clear()
                            }
                        }
                    }
                },
                enabled = messageState.canSend,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint =
                        if (messageState.canSend) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        },
                )
            }
        }
    }
}

/**
 * Scrollable message list with chat bubbles, reversed so newest is at bottom.
 */
@Composable
private fun IosMessageList(
    messages: List<Note>,
    account: IAccount,
    onAuthorClick: (String) -> Unit,
) {
    val listState = rememberLazyListState()

    // Auto-scroll to bottom on new messages
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

            // Decrypt NIP-04 content if needed
            val event = note.event
            var decryptedContent by remember(note.idHex) { mutableStateOf<String?>(null) }

            LaunchedEffect(note.idHex, event) {
                try {
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
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    platform.Foundation.NSLog("LaunchedEffect error (decrypt): " + (e.message ?: "unknown"))
                }
            }

            ChatMessageCompose(
                note = note,
                isLoggedInUser = isMe,
                isDraft = isDraft,
                isComplete = true,
                hasDetailsToShow = false,
                drawAuthorInfo = !isMe,
                onClick = { false },
                onAuthorClick = {
                    note.author?.pubkeyHex?.let { onAuthorClick(it) }
                },
                authorLine = {
                    if (!isMe) {
                        note.author?.let { author ->
                            Text(
                                text = author.toBestDisplayName(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                detailRow = {
                    note.createdAt()?.let { timestamp ->
                        Text(
                            text = timestamp.toTimeAgo(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
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
