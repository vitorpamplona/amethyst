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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.nests.room

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.viewmodels.NestViewModel
import com.vitorpamplona.amethyst.ui.note.ClickableUserPicture
import com.vitorpamplona.amethyst.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.LoadUser
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Size35dp
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent
import kotlinx.coroutines.launch

/**
 * Live-chat panel (T1 #2) — renders the kind-1311 transcript for
 * the room and provides a send field. Messages flow from
 * [NestViewModel.chat] (populated by the LocalCache observer
 * in [NestActivityContent]); send routes through
 * `account.signAndComputeBroadcast` directly so the VM stays free
 * of platform / signing dependencies.
 */
@Composable
internal fun NestChatPanel(
    roomATag: ATag,
    viewModel: NestViewModel,
    accountViewModel: AccountViewModel,
    modifier: Modifier = Modifier,
) {
    val messages by viewModel.chat.collectAsState()
    val listState = rememberLazyListState()

    // Only auto-scroll when the user is already pinned near the
    // bottom — otherwise a new message yanks them away from the
    // history they're scrolling through. Standard chat-app pattern.
    val isAtBottom by remember(messages.size) {
        derivedStateOf {
            val lastIdx = messages.lastIndex
            if (lastIdx < 0) {
                true
            } else {
                listState.firstVisibleItemIndex >= (lastIdx - PINNED_TO_BOTTOM_TOLERANCE)
            }
        }
    }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && isAtBottom) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (messages.isEmpty()) {
            Text(
                text = stringRes(R.string.nest_chat_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().height(220.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(messages, key = { it.id }) { msg ->
                    ChatRow(msg, accountViewModel)
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        ChatComposer(roomATag, accountViewModel)
    }
}

@Composable
private fun ChatRow(
    msg: LiveActivitiesChatMessageEvent,
    accountViewModel: AccountViewModel,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        ClickableUserPicture(
            baseUserHex = msg.pubKey,
            size = Size35dp,
            accountViewModel = accountViewModel,
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            // Same author-name pattern the rest of Amethyst's chat
            // surfaces use — LoadUser kicks the metadata fetch and
            // UsernameDisplay observes its kind-0 flow, falling back
            // to a truncated npub while the metadata is in flight or
            // missing. Replaces the "first 8 hex chars of pubkey"
            // placeholder the v1 chat panel shipped with.
            LoadUser(baseUserHex = msg.pubKey, accountViewModel = accountViewModel) { user ->
                if (user != null) {
                    UsernameDisplay(
                        baseUser = user,
                        fontWeight = FontWeight.Bold,
                        textColor = MaterialTheme.colorScheme.primary,
                        accountViewModel = accountViewModel,
                    )
                }
            }
            Text(
                text = msg.content,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ChatComposer(
    roomATag: ATag,
    accountViewModel: AccountViewModel,
) {
    var draft by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringRes(R.string.nest_chat_placeholder)) },
            singleLine = false,
            maxLines = 4,
            enabled = !isSending,
        )
        Spacer(Modifier.width(8.dp))
        TextButton(
            enabled = draft.isNotBlank() && !isSending,
            onClick = {
                val toSend = draft.trim()
                if (toSend.isEmpty()) return@TextButton
                isSending = true
                scope.launch {
                    val result =
                        runCatching {
                            accountViewModel.account.signAndComputeBroadcast(
                                LiveActivitiesChatMessageEvent.message(
                                    post = toSend,
                                    activity = roomATag,
                                ),
                            )
                        }
                    isSending = false
                    if (result.isSuccess) {
                        // Clear ONLY on success so a network failure
                        // doesn't lose the user's text — they can retry
                        // without retyping.
                        draft = ""
                    } else {
                        val why =
                            result.exceptionOrNull()?.message
                                ?: result.exceptionOrNull()?.let { it::class.simpleName }
                                ?: "unknown error"
                        accountViewModel.toastManager.toast(
                            R.string.nest_chat_send_failed_title,
                            why,
                            user = null,
                        )
                    }
                }
            },
        ) {
            Text(stringRes(R.string.nest_chat_send))
        }
    }
}

private const val PINNED_TO_BOTTOM_TOLERANCE = 1
