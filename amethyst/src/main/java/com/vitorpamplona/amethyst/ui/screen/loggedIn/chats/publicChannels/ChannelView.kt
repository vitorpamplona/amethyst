/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.model.Channel
import com.vitorpamplona.amethyst.model.LiveActivitiesChannel
import com.vitorpamplona.amethyst.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.ui.navigation.INav
import com.vitorpamplona.amethyst.ui.note.LoadChannel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.RefreshingChatroomFeedView
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.dal.ChannelFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.datasource.ChannelFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.nip53LiveActivities.ShowVideoStreaming
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.send.ChannelNewMessageViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.send.EditFieldRow
import com.vitorpamplona.amethyst.ui.theme.DoubleVertSpacer
import com.vitorpamplona.quartz.experimental.ephemChat.chat.RoomId
import kotlinx.coroutines.launch

@Composable
fun ChannelView(
    channelId: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (channelId == null) return

    LoadChannel(channelId, accountViewModel) {
        PrepareChannelViewModels(
            baseChannel = it,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}

@Composable
fun ChannelView(
    channelId: RoomId?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (channelId == null) return

    LoadChannel(channelId, accountViewModel) {
        PrepareChannelViewModels(
            baseChannel = it,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}

@Composable
fun PrepareChannelViewModels(
    baseChannel: Channel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val feedViewModel: ChannelFeedViewModel =
        viewModel(
            key = baseChannel.idHex + "ChannelFeedViewModel",
            factory =
                ChannelFeedViewModel.Factory(
                    baseChannel,
                    accountViewModel.account,
                ),
        )

    val channelScreenModel: ChannelNewMessageViewModel = viewModel()
    channelScreenModel.init(accountViewModel)
    channelScreenModel.load(baseChannel)

    ChannelView(
        channel = baseChannel,
        feedViewModel = feedViewModel,
        newPostModel = channelScreenModel,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

@Composable
fun ChannelView(
    channel: Channel,
    feedViewModel: ChannelFeedViewModel,
    newPostModel: ChannelNewMessageViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    WatchLifecycleAndUpdateModel(feedViewModel)
    ChannelFilterAssemblerSubscription(channel, accountViewModel.dataSources().channel, accountViewModel)

    Column(Modifier.fillMaxHeight()) {
        Column(
            modifier =
                remember {
                    Modifier
                        .fillMaxHeight()
                        .padding(vertical = 0.dp)
                        .weight(1f, true)
                },
        ) {
            if (channel is LiveActivitiesChannel) {
                ShowVideoStreaming(channel, accountViewModel)
            }
            RefreshingChatroomFeedView(
                viewModel = feedViewModel,
                accountViewModel = accountViewModel,
                nav = nav,
                routeForLastRead = "Channel/${channel.idHex}",
                avoidDraft = newPostModel.draftTag,
                onWantsToReply = newPostModel::reply,
                onWantsToEditDraft = newPostModel::editFromDraft,
            )
        }

        Spacer(modifier = DoubleVertSpacer)

        val scope = rememberCoroutineScope()

        // LAST ROW
        EditFieldRow(
            newPostModel,
            accountViewModel,
            onSendNewMessage = {
                scope.launch {
                    feedViewModel.sendToTop()
                }
            },
            nav,
        )
    }
}
