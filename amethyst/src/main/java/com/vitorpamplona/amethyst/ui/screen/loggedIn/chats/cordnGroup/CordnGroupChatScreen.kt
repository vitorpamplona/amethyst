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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.cordnGroup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.cordnGroups.CordnGroupChatroom
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.cordn.spec02Envelopes.CordnDeliveredMessage
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.launch

/**
 * One cordn room.
 *
 * Deliberately minimal — text in, text out. Reactions, media, threading and
 * edits all exist in `spec/02.md` and in `CordnAnnotationIndex`, and none of
 * them are here yet: a composer that could send a kind the room cannot yet
 * render would produce messages this client shows as blanks.
 *
 * It renders the room's own envelopes rather than `Note`s, so nothing on this
 * screen goes through `LocalCache`. That is the point — a cordn message is not
 * a Nostr event that happens to be encrypted, it is an MLS payload that never
 * touched a relay, and giving it a Note would make it searchable and
 * notifiable alongside things that were actually published.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CordnGroupChatScreen(
    coordinatorPubKey: HexKey,
    gid: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val runtime = accountViewModel.account.cordnRuntime
    val room = remember(coordinatorPubKey, gid) { runtime?.groups?.get(coordinatorPubKey, gid) }

    if (room == null) {
        // No room means no session for this coordinator, which is a real state
        // (it was forgotten, or never opened) and not an error to throw at the
        // user as a blank screen.
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text(stringRes(R.string.cordn_group_unavailable), style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    CordnGroupChat(room, accountViewModel, nav)
}

@Composable
private fun CordnGroupChat(
    room: CordnGroupChatroom,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val messages by room.messages.collectAsStateWithLifecycle()
    val annotations by room.annotations.collectAsStateWithLifecycle()
    val name by room.name.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            CordnChatTopBar(
                title = name?.takeIf { it.isNotBlank() } ?: stringRes(R.string.cordn_group_untitled, room.gid.take(8)),
                onInfo = { nav.nav(Route.CordnGroupInfo(room.coordinatorPubKey, room.gid)) },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            ) {
                items(messages, key = { it.envelope.id }) { message ->
                    CordnMessageRow(
                        message = message,
                        // The edit if there is one, and nothing at all if the
                        // message was withdrawn: rendering the original text of
                        // a deleted message would defeat the deletion.
                        text = if (annotations.isDeleted(message.envelope.id)) null else annotations.contentOf(message.envelope.id),
                        isEdited = annotations.isEdited(message.envelope.id),
                        accountViewModel = accountViewModel,
                    )
                }
            }

            HorizontalDivider()

            CordnComposer(
                draft = draft,
                onDraftChange = { draft = it },
                onSend = {
                    val text = draft.trim()
                    if (text.isNotEmpty()) {
                        draft = ""
                        scope.launch {
                            accountViewModel.account.cordnRuntime
                                ?.sessionOrNull(room.coordinatorPubKey)
                                ?.manager
                                ?.send(room.gid, text)
                        }
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CordnChatTopBar(
    title: String,
    onInfo: () -> Unit,
) {
    TopAppBar(
        title = { Text(title) },
        actions = {
            IconButton(onClick = onInfo) {
                Icon(MaterialSymbols.Info, contentDescription = stringRes(R.string.cordn_group_info))
            }
        },
    )
}

@Composable
private fun CordnMessageRow(
    message: CordnDeliveredMessage,
    text: String?,
    isEdited: Boolean,
    accountViewModel: AccountViewModel,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            text = message.envelope.pubKey.take(8),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (text == null) {
            Text(
                text = stringRes(R.string.cordn_message_deleted),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            if (isEdited) {
                Text(
                    text = stringRes(R.string.cordn_message_edited),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CordnComposer(
    draft: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringRes(R.string.cordn_composer_hint)) },
        )
        IconButton(onClick = onSend, enabled = draft.isNotBlank()) {
            Icon(MaterialSymbols.AutoMirrored.Send, contentDescription = stringRes(R.string.cordn_send))
        }
    }
}
