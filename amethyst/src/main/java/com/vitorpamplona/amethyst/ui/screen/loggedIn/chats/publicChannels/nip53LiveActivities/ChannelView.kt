/**
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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.nip53LiveActivities

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.commons.model.nip53LiveActivities.LiveActivitiesChannel
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.LoadLiveActivityChannel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.RefreshingChatroomFeedView
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.dal.ChannelFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.datasource.ChannelFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.send.ChannelNewMessageViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.send.EditFieldRow
import com.vitorpamplona.amethyst.ui.theme.DoubleVertSpacer
import com.vitorpamplona.quartz.nip01Core.core.Address

@Composable
fun LiveActivityChannelView(
    channelId: Address?,
    draft: Note? = null,
    replyTo: Note? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (channelId == null) return

    LoadLiveActivityChannel(channelId, accountViewModel) {
        PrepareChannelViewModels(
            baseChannel = it,
            draft = draft,
            replyTo = replyTo,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}

@Composable
fun PrepareChannelViewModels(
    baseChannel: LiveActivitiesChannel,
    draft: Note? = null,
    replyTo: Note? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val feedViewModel: ChannelFeedViewModel =
        viewModel(
            key = baseChannel.address.toValue() + "ChannelFeedViewModel",
            factory =
                ChannelFeedViewModel.Factory(
                    baseChannel,
                    accountViewModel.account,
                ),
        )

    val channelScreenModel: ChannelNewMessageViewModel = viewModel()
    channelScreenModel.init(accountViewModel)
    channelScreenModel.load(baseChannel)

    if (draft != null) {
        LaunchedEffect(draft, channelScreenModel, accountViewModel) {
            channelScreenModel.editFromDraft(draft)
        }
    }

    if (replyTo != null) {
        LaunchedEffect(replyTo, channelScreenModel, accountViewModel) {
            channelScreenModel.reply(replyTo)
        }
    }

    LiveActivityChannelView(
        channel = baseChannel,
        feedViewModel = feedViewModel,
        newPostModel = channelScreenModel,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

@Composable
fun LiveActivityChannelView(
    channel: LiveActivitiesChannel,
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
                Modifier
                    .fillMaxHeight()
                    .padding(vertical = 0.dp)
                    .weight(1f, true),
        ) {
            ShowVideoStreaming(channel, accountViewModel)
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

        Spacer(modifier = DoubleVertSpacer)

        // LAST ROW
        EditFieldRow(
            newPostModel,
            accountViewModel,
            onSendNewMessage = feedViewModel.feedState::sendToTop,
            nav,
        )
    }
}
