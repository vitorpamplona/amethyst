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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.relayGroup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.RefreshingChatroomFeedView
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.dal.ChannelFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.datasource.ChannelFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.send.ChannelNewMessageViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.send.EditFieldRow
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DoubleVertSpacer
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId

@Composable
fun RelayGroupChannelView(
    channelId: GroupId?,
    draft: Note? = null,
    replyTo: Note? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (channelId == null) return

    LoadRelayGroupChannel(channelId, accountViewModel) { group ->
        PrepareChannelViewModels(
            baseChannel = group,
            draft = draft,
            replyTo = replyTo,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}

@Composable
private fun PrepareChannelViewModels(
    baseChannel: RelayGroupChannel,
    draft: Note? = null,
    replyTo: Note? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val feedViewModel: ChannelFeedViewModel =
        viewModel(
            key = baseChannel.groupId.toKey() + "ChannelFeedViewModel",
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

    ChannelView(
        channel = baseChannel,
        feedViewModel = feedViewModel,
        newPostModel = channelScreenModel,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

@Composable
private fun ChannelView(
    channel: RelayGroupChannel,
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
            RefreshingChatroomFeedView(
                feedContentState = feedViewModel.feedState,
                accountViewModel = accountViewModel,
                nav = nav,
                routeForLastRead = "RelayGroup/${channel.groupId.toKey()}",
                avoidDraft = newPostModel.draftTag,
                onWantsToReply = newPostModel::reply,
                onWantsToEditDraft = newPostModel::editFromDraft,
            )
        }

        Spacer(modifier = DoubleVertSpacer)

        // NIP-29 relays reject writes from non-members, so only show the composer when the
        // relay-signed roster (39001/39002) lists me as a member/mod/admin. Collect the metadata
        // flow so the composer appears the moment my join is accepted. Otherwise, explain why.
        val channelState by channel
            .flow()
            .metadata.stateFlow
            .collectAsStateWithLifecycle()
        val liveChannel = channelState.channel as? RelayGroupChannel ?: channel
        val canPost = liveChannel.membershipOf(accountViewModel.userProfile().pubkeyHex).isMember()

        if (canPost) {
            EditFieldRow(
                newPostModel,
                accountViewModel,
                onSendNewMessage = feedViewModel.feedState::sendToTop,
                nav,
            )
        } else {
            JoinToPostNotice(liveChannel)
        }
    }
}

/**
 * Shown in place of the composer when I'm not (yet) a member: a relay group won't accept my kind-9
 * chat until its roster lists me, so typing would only earn a silent relay rejection. Points me at
 * the top-bar Join action (open groups) or explains the invite requirement (closed groups).
 */
@Composable
private fun JoinToPostNotice(channel: RelayGroupChannel) {
    val message =
        if (channel.isClosed()) {
            stringRes(R.string.relay_group_invite_only_to_post)
        } else {
            stringRes(R.string.relay_group_join_to_post)
        }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        )
    }
}
