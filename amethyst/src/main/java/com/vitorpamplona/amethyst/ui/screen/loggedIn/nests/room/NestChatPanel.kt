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
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.viewmodels.NestViewModel
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.navigation.navs.BouncingIntentNav
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.ChatroomMessageCompose
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.send.ChannelNewMessageViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.send.EditFieldRow
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingSpaceEvent

/**
 * In-room chat panel. Renders the kind-1311 transcript that the
 * [NestViewModel] is collecting (`viewModel.chat`) using the same
 * per-message renderer that NIP-53 live-stream chat uses
 * ([ChatroomMessageCompose]).
 *
 * Composer is the same [EditFieldRow] used by every other public-chat
 * surface in the app, so we get @-mention picker, file/image/video
 * upload, reply preview, draft auto-save, emoji suggestions, content
 * warnings, expiration, geohash, and zap-split forwarding for free.
 *
 * To stay aligned with the rest of the app, the composer is backed by
 * a [ChannelNewMessageViewModel] loaded with a [LiveActivitiesChannel]
 * keyed by the meeting space's address. `ChannelNewMessageViewModel.
 * createTemplate()` already handles the `LiveActivitiesChannel` →
 * `LiveActivitiesChatMessageEvent` path: when `channel.info` is null
 * (we never populate it for kind-30312 — it's typed to the kind-30311
 * `LiveActivitiesEvent`), it falls through to the
 * `LiveActivitiesChatMessageEvent.message(message, channel.toATag())`
 * branch, which produces exactly the same event the original slim
 * composer was emitting.
 *
 * Data flow: messages still arrive via the existing nest pipeline
 * (`RoomChatFilterAssemblerSubscription` → `NestViewModel.onChatEvent`
 * → `viewModel.chat`). The composer emits via
 * `account.signAndSendPrivately(template, channelRelays)` inside
 * `ChannelNewMessageViewModel.sendPostSync`, which lands on the same
 * relays the listener subscribes to, so the user sees their own
 * message immediately when it round-trips. Drafts are stored as
 * NIP-37 wrap events through the standard account draft pipeline —
 * they do not pollute `viewModel.chat` because that flow only accepts
 * kind-1311 events.
 *
 * Navigation: the activity has no Compose NavHost in scope. Taps that
 * resolve to a NIP-19 entity (profile, quoted note, hashtag, addressable
 * channel) are dispatched through [BouncingIntentNav] — a `nostr:` URI
 * Intent at MainActivity. Anything that doesn't have a `nostr:` URI is
 * a no-op for now. The audio room keeps running in its own task while
 * the user explores; back from MainActivity returns to the room.
 *
 * The composer's default back-press handler from [EditFieldRow] would
 * try to `nav.popBack()` (a no-op on [BouncingIntentNav]) and clear
 * the field — both wrong inside an Activity-scoped chat panel where
 * back means "leave the room". We pass `interceptBackPress = false`
 * so system back continues to flow to the activity.
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

    val channel =
        remember(event) {
            accountViewModel.checkGetOrCreateLiveActivityChannel(event.address())
        }
    val routeForLastRead = remember(event) { "NestChat/${event.address().toValue()}" }

    val channelScreenModel: ChannelNewMessageViewModel =
        composeViewModel(key = "Nest/${event.address().toValue()}")
    channelScreenModel.init(accountViewModel)
    channelScreenModel.load(channel)

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
                    onWantsToReply = channelScreenModel::reply,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        EditFieldRow(
            channelScreenModel = channelScreenModel,
            accountViewModel = accountViewModel,
            onSendNewMessage = {},
            nav = nav,
            interceptBackPress = false,
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
