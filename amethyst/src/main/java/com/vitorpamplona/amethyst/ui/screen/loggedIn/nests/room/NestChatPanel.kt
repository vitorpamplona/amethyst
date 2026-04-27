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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.viewmodels.NestViewModel
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.navigation.navs.BouncingIntentNav
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.ChatroomMessageCompose
import com.vitorpamplona.amethyst.ui.screen.loggedIn.nests.room.send.NestEditFieldRow
import com.vitorpamplona.amethyst.ui.screen.loggedIn.nests.room.send.NestNewMessageViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingSpaceEvent
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel

/**
 * In-room chat panel. Renders the kind-1311 transcript that the
 * [NestViewModel] is collecting (`viewModel.chat`) using the same
 * per-message renderer that NIP-53 live-stream chat uses
 * ([ChatroomMessageCompose]).
 *
 * The composer is owned by a separate [NestNewMessageViewModel] —
 * scoped to *editing and sending* chat messages only — so we get
 * @-mention picker, file/image/video upload, reply preview, NIP-37
 * draft auto-save, emoji suggestions, content warnings, expiration,
 * geohash, and zap-split forwarding for the nest chat field. The
 * message list itself is still driven from the [NestViewModel] so
 * presence / reactions / chat all share a single lifecycle.
 *
 * Navigation: the activity has no Compose NavHost in scope. Taps
 * that resolve to a NIP-19 entity (profile, quoted note, hashtag,
 * addressable channel) are dispatched through [BouncingIntentNav]
 * — a `nostr:` URI Intent at MainActivity. Anything that doesn't
 * have a `nostr:` URI is a no-op for now. The audio room keeps
 * running in its own task while the user explores; back from
 * MainActivity returns to the room.
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

    val routeForLastRead = remember(event) { "NestChat/${event.address().toValue()}" }

    val nestScreenModel: NestNewMessageViewModel =
        composeViewModel(key = "Nest/${event.address().toValue()}")
    nestScreenModel.init(accountViewModel)
    nestScreenModel.load(event)

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
                    onWantsToReply = nestScreenModel::reply,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        NestEditFieldRow(
            nestScreenModel = nestScreenModel,
            accountViewModel = accountViewModel,
            onSendNewMessage = {},
            nav = nav,
        )
    }
}

@Composable
private fun NestChatMessageList(
    messages: List<LiveActivitiesChatMessageEvent>,
    routeForLastRead: String,
    accountViewModel: AccountViewModel,
    nav: BouncingIntentNav,
    onWantsToReply: (Note) -> Unit,
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
                onWantsToReply = onWantsToReply,
                onWantsToEditDraft = NEST_CHAT_NO_OP_NOTE,
            )
        }
    }
}

private val NEST_CHAT_NO_OP_NOTE: (Note) -> Unit = {}
