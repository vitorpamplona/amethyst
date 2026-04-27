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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.viewmodels.NestViewModel
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.ui.components.ThinPaddingTextField
import com.vitorpamplona.amethyst.ui.navigation.navs.BouncingIntentNav
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.ChatroomMessageCompose
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.utils.ThinSendButton
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.EditFieldBorder
import com.vitorpamplona.amethyst.ui.theme.EditFieldModifier
import com.vitorpamplona.amethyst.ui.theme.EditFieldTrailingIconModifier
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingSpaceEvent
import kotlinx.coroutines.launch

/**
 * In-room chat panel. Renders the kind-1311 transcript that the
 * [NestViewModel] is collecting (`viewModel.chat`) using the same
 * per-message renderer that NIP-53 live-stream chat uses
 * ([ChatroomMessageCompose]) and the same composer primitives
 * ([ThinPaddingTextField] + [ThinSendButton]) so visually it matches
 * the rest of Amethyst's chat surfaces.
 *
 * Data flow stays inside [NestViewModel]: messages come from
 * `viewModel.chat`; sends route through `accountViewModel.account
 * .signAndComputeBroadcast` which is the same path the listener
 * subscribes to via `RoomChatFilterAssemblerSubscription` in
 * [NestActivityContent]. We deliberately do NOT spin up a parallel
 * `ChannelFeedViewModel` / `ChannelNewMessageViewModel` here — keeping
 * the VM single-source-of-truth means screen extras (presence,
 * reactions, hand-raise) and chat are aggregated under the same
 * lifecycle.
 *
 * Navigation: the activity has no Compose NavHost in scope. Taps that
 * resolve to a NIP-19 entity (profile, quoted note, hashtag, addressable
 * channel) are dispatched through [BouncingIntentNav] — a `nostr:` URI
 * Intent at MainActivity. Anything that doesn't have a `nostr:` URI is
 * a no-op for now. The audio room keeps running in its own task while
 * the user explores; back from MainActivity returns to the room.
 *
 * Composer is intentionally slim for v1: text-only, no draft handling,
 * no media attachments, no @-mention picker, no reply preview. Those
 * affordances live in `EditFieldRow` / `ChannelNewMessageViewModel`
 * which are pinned to the channel-VM model — out of scope here.
 */
@Composable
internal fun ColumnScope.NestChatPanel(
    event: MeetingSpaceEvent,
    viewModel: NestViewModel,
    accountViewModel: AccountViewModel,
    modifier: Modifier = Modifier,
) {
    val messages by viewModel.chat.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val nav = remember(context, scope) { BouncingIntentNav(context, scope) }

    val roomATag =
        remember(event) {
            ATag(
                kind = event.kind,
                pubKeyHex = event.pubKey,
                dTag = event.dTag(),
                relay = null,
            )
        }
    val routeForLastRead = remember(event) { "NestChat/${event.address().toValue()}" }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().weight(1f, fill = true)) {
            if (messages.isEmpty()) {
                Text(
                    text = stringRes(R.string.nest_chat_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            } else {
                NestChatMessageList(
                    messages = messages,
                    routeForLastRead = routeForLastRead,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        NestChatComposer(
            roomATag = roomATag,
            accountViewModel = accountViewModel,
        )
    }
}

@Composable
private fun NestChatMessageList(
    messages: List<LiveActivitiesChatMessageEvent>,
    routeForLastRead: String,
    accountViewModel: AccountViewModel,
    nav: BouncingIntentNav,
) {
    val listState = rememberLazyListState()

    // Auto-scroll to the newest message ONLY when the user is already
    // pinned near the bottom — otherwise a new message yanks them away
    // from the history they're scrolling. With reverseLayout=true the
    // bottom of the screen is index 0, so "pinned" = the first visible
    // item is index 0 or 1 (one row of tolerance for partial scroll).
    LaunchedEffect(listState) {
        snapshotFlow { messages.size }.collect { size ->
            if (size > 0 && listState.firstVisibleItemIndex <= 1) {
                listState.animateScrollToItem(0)
            }
        }
    }

    // viewModel.chat is sorted ascending by created_at (oldest first).
    // reverseLayout=true puts index 0 at the bottom, so reverse the
    // list here to keep newest-at-bottom rendering — same behavior
    // LiveStream chat ships in ChatFeedLoaded.
    val reversed = remember(messages) { messages.asReversed() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        reverseLayout = true,
    ) {
        items(
            items = reversed,
            key = { it.id },
            contentType = { it.kind },
        ) { event ->
            val note = remember(event.id) { LocalCache.getOrCreateNote(event.id) }
            ChatroomMessageCompose(
                baseNote = note,
                routeForLastRead = routeForLastRead,
                accountViewModel = accountViewModel,
                nav = nav,
                onWantsToReply = NEST_CHAT_NO_OP_NOTE,
                onWantsToEditDraft = NEST_CHAT_NO_OP_NOTE,
            )
        }
    }
}

@Composable
private fun NestChatComposer(
    roomATag: ATag,
    accountViewModel: AccountViewModel,
) {
    val state = remember { TextFieldState() }
    var isSending by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(modifier = EditFieldModifier) {
        ThinPaddingTextField(
            state = state,
            modifier = Modifier.fillMaxWidth(),
            shape = EditFieldBorder,
            enabled = !isSending,
            placeholder = {
                Text(
                    text = stringRes(R.string.nest_chat_placeholder),
                    color = MaterialTheme.colorScheme.placeholderText,
                )
            },
            trailingIcon = {
                ThinSendButton(
                    isActive = state.text.isNotBlank() && !isSending,
                    modifier = EditFieldTrailingIconModifier,
                ) {
                    val toSend = state.text.toString().trim()
                    if (toSend.isEmpty()) return@ThinSendButton
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
                            state.clearText()
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

private val NEST_CHAT_NO_OP_NOTE: (com.vitorpamplona.amethyst.model.Note) -> Unit = {}
