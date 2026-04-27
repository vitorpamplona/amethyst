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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.RefreshingChatroomFeedView
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.dal.ChannelFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.send.ChannelNewMessageViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.send.EditFieldRow
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingSpaceEvent

/**
 * In-room chat panel. Reuses the same `ChannelFeedViewModel` +
 * `RefreshingChatroomFeedView` + `EditFieldRow` stack that powers
 * NIP-53 live-stream chat (`LiveActivityChannelView`) so a Nest's
 * kind-1311 transcript renders with full Amethyst chat features —
 * mentions, embedded media, reply previews, draft handling, NIP-21
 * link rendering, etc. — instead of the bare-text v1 list.
 *
 * The kind-30312 address registers a [com.vitorpamplona.amethyst.commons.model.nip53LiveActivities.LiveActivitiesChannel]
 * inside [LocalCache.liveChatChannels] (see `LocalCache.consume(LiveActivitiesChatMessageEvent)`),
 * so we can hand the channel straight to `ChannelFeedViewModel`
 * without a kind-30311-specific path. The relay subscription that
 * populates the channel is the one started by
 * `RoomChatFilterAssemblerSubscription` in [NestActivityContent];
 * we don't need a second `ChannelFilterAssemblerSubscription` here
 * (it would be a no-op for kind-30312 channels because their
 * `LiveActivitiesChannel.info` is null and `relays()` is empty).
 *
 * Embedded inside the room screen's verticalScroll Column with a
 * fixed height — a LazyColumn child cannot share its parent's
 * scroll. The screen-level layout intentionally stays unchanged
 * (top section is fine; only the chat surface migrates).
 */
@Composable
internal fun NestChatPanel(
    event: MeetingSpaceEvent,
    accountViewModel: AccountViewModel,
    modifier: Modifier = Modifier,
) {
    val channel =
        remember(event) {
            LocalCache.getOrCreateLiveChannel(event.address())
        }

    val feedViewModel: ChannelFeedViewModel =
        viewModel(
            key = event.address().toValue() + "NestChannelFeedViewModel",
            factory =
                ChannelFeedViewModel.Factory(
                    channel,
                    accountViewModel.account,
                ),
        )

    val newPostModel: ChannelNewMessageViewModel =
        viewModel(key = event.address().toValue() + "NestChannelNewMessageViewModel")
    newPostModel.init(accountViewModel)
    newPostModel.load(channel)

    WatchLifecycleAndUpdateModel(feedViewModel)

    // The NestActivity is a separate Android Activity; it has no
    // Compose nav graph. Pass an EmptyNav so the chat composables
    // render correctly (their navigation taps become no-ops). A
    // future patch can plumb a deep-link-bouncing nav if we want
    // profile / quote-note taps inside chat to land in the main app.
    val nav = remember { EmptyNav() }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().height(NEST_CHAT_PANEL_HEIGHT)) {
            RefreshingChatroomFeedView(
                feedContentState = feedViewModel.feedState,
                accountViewModel = accountViewModel,
                nav = nav,
                routeForLastRead = "Channel/${channel.address.toValue()}",
                avoidDraft = newPostModel.draftTag,
                onWantsToReply = newPostModel::reply,
                onWantsToEditDraft = newPostModel::editFromDraft,
            )
        }
        Spacer(Modifier.height(8.dp))
        EditFieldRow(
            channelScreenModel = newPostModel,
            accountViewModel = accountViewModel,
            onSendNewMessage = feedViewModel.feedState::sendToTop,
            nav = nav,
        )
    }
}

private val NEST_CHAT_PANEL_HEIGHT = 420.dp
