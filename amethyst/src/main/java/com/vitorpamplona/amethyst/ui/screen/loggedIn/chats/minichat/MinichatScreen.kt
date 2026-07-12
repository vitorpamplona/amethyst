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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.minichat

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeNoteReplies
import com.vitorpamplona.amethyst.ui.components.ThinPaddingTextField
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.ChatroomMessageCompose
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.utils.ThinSendButton
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.EditFieldBorder
import com.vitorpamplona.amethyst.ui.theme.EditFieldModifier
import com.vitorpamplona.amethyst.ui.theme.EditFieldTrailingIconModifier
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon as SymbolIcon

/**
 * The minichat ("chat within a chat") of a single chat message: the message pinned at
 * top, then its kind-1111 thread replies as chat bubbles, with a composer that always
 * posts a kind-1111 rooted at that message (flat — replying inside a minichat doesn't
 * spawn sub-threads). Opened from the "N replies" chip on any chat message.
 *
 * It reuses [ChatroomMessageCompose] so the bubbles look exactly like the main chat.
 * Observing the root's replies also loads them from relays for public chats; Concord's
 * thread replies keep arriving over the account-wide plane ingestion.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MinichatScreen(
    rootId: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val rootNote = remember(rootId) { LocalCache.getOrCreateNote(rootId) }

    // Loads + reacts to the root's replies (kind-1111 among them).
    val replyState by observeNoteReplies(rootNote, accountViewModel)
    val replies =
        remember(replyState) {
            rootNote.replies
                .filter { it.event is CommentEvent }
                .sortedWith(compareBy({ it.createdAt() ?: 0L }, { it.idHex }))
        }

    val composer = remember { TextFieldState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val canPost by remember { derivedStateOf { composer.text.isNotBlank() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringRes(R.string.chat_minichat_title), fontWeight = FontWeight.Bold, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBack() }) {
                        SymbolIcon(symbol = MaterialSymbols.AutoMirrored.ArrowBack, contentDescription = stringRes(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxHeight().padding(padding)) {
            LazyColumn(Modifier.fillMaxWidth().weight(1f, true)) {
                item("root") {
                    ChatroomMessageCompose(
                        baseNote = rootNote,
                        routeForLastRead = null,
                        accountViewModel = accountViewModel,
                        nav = nav,
                        onWantsToReply = {},
                        onWantsToEditDraft = {},
                    )
                    HorizontalDivider()
                }
                items(replies, key = { it.idHex }) { reply ->
                    ChatroomMessageCompose(
                        baseNote = reply,
                        routeForLastRead = null,
                        accountViewModel = accountViewModel,
                        nav = nav,
                        onWantsToReply = {},
                        onWantsToEditDraft = {},
                    )
                }
            }

            Column(modifier = EditFieldModifier) {
                ThinPaddingTextField(
                    state = composer,
                    modifier = Modifier.fillMaxWidth(),
                    shape = EditFieldBorder,
                    placeholder = {
                        Text(
                            text = stringRes(R.string.reply_here),
                            color = MaterialTheme.colorScheme.placeholderText,
                        )
                    },
                    trailingIcon = {
                        ThinSendButton(
                            isActive = canPost,
                            modifier = EditFieldTrailingIconModifier,
                        ) {
                            val text = composer.text.toString().trim()
                            if (text.isNotEmpty()) {
                                composer.clearText()
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        accountViewModel.account.sendMinichatReply(rootNote, text)
                                    } catch (e: Exception) {
                                        launch(Dispatchers.Main) {
                                            Toast.makeText(context, "Failed to send message: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        }
                    },
                    colors =
                        TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                )
            }
        }
    }
}
