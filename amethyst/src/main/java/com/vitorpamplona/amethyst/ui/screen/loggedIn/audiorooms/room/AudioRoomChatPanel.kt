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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.audiorooms.room

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.viewmodels.AudioRoomViewModel
import com.vitorpamplona.amethyst.ui.note.ClickableUserPicture
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Size35dp
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent
import kotlinx.coroutines.launch

/**
 * Live-chat panel (T1 #2) — renders the kind-1311 transcript for
 * the room and provides a send field. Messages flow from
 * [AudioRoomViewModel.chat] (populated by the LocalCache observer
 * in [AudioRoomActivityContent]); send routes through
 * `account.signAndComputeBroadcast` directly so the VM stays free
 * of platform / signing dependencies.
 */
@Composable
internal fun AudioRoomChatPanel(
    roomATag: ATag,
    viewModel: AudioRoomViewModel,
    accountViewModel: AccountViewModel,
    modifier: Modifier = Modifier,
) {
    val messages by viewModel.chat.collectAsState()
    val listState = rememberLazyListState()

    // Auto-scroll to bottom on new message arrival.
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (messages.isEmpty()) {
            Text(
                text = stringRes(R.string.audio_room_chat_empty),
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
            // Truncated pubkey as the inline name placeholder. A future
            // pass can resolve to the user's NIP-01 display name; for
            // the v1 chat panel we keep the dependency surface tight.
            Text(
                text = msg.pubKey.take(8),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
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
    val scope = rememberCoroutineScope()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringRes(R.string.audio_room_chat_placeholder)) },
            singleLine = false,
            maxLines = 4,
        )
        Spacer(Modifier.width(8.dp))
        TextButton(
            enabled = draft.isNotBlank(),
            onClick = {
                val toSend = draft.trim()
                if (toSend.isEmpty()) return@TextButton
                draft = ""
                scope.launch {
                    runCatching {
                        accountViewModel.account.signAndComputeBroadcast(
                            LiveActivitiesChatMessageEvent.message(
                                post = toSend,
                                activity = roomATag,
                            ),
                        )
                    }
                }
            },
        ) {
            Text(stringRes(R.string.audio_room_chat_send))
        }
    }
}
