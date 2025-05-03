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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.ephemChat.header

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.EphemeralChatChannel
import com.vitorpamplona.amethyst.model.FeatureSetType
import com.vitorpamplona.amethyst.service.Nip11CachedRetriever
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.channel.observeChannel
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.observeUserIsFollowingChannel
import com.vitorpamplona.amethyst.ui.components.RobohashFallbackAsyncImage
import com.vitorpamplona.amethyst.ui.navigation.INav
import com.vitorpamplona.amethyst.ui.note.produceStateIfNotNull
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.ephemChat.header.actions.JoinChatButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.ephemChat.header.actions.LeaveChatButton
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.HeaderPictureModifier
import com.vitorpamplona.amethyst.ui.theme.Size35dp
import com.vitorpamplona.ammolite.relays.RelayBriefInfoCache
import com.vitorpamplona.quartz.nip11RelayInfo.Nip11RelayInformation
import com.vitorpamplona.quartz.nip65RelayList.RelayUrlFormatter

@Composable
fun loadRelayInfo(
    relayUrl: String,
    accountViewModel: AccountViewModel,
): Nip11RelayInformation? {
    @Suppress("ProduceStateDoesNotAssignValue")
    val relayInfo by produceStateIfNotNull(
        Nip11CachedRetriever.getFromCache(relayUrl),
        relayUrl,
    ) {
        accountViewModel.retrieveRelayDocument(
            relayUrl,
            onInfo = {
                value = it
            },
            onError = { url, errorCode, exceptionMessage ->
            },
        )
    }

    return relayInfo
}

@Composable
fun ShortEphemeralChatChannelHeader(
    baseChannel: EphemeralChatChannel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val channelState by observeChannel(baseChannel)
    val channel = channelState?.channel as? EphemeralChatChannel ?: return

    Row(verticalAlignment = Alignment.CenterVertically) {
        DrawRelayIcon(baseChannel, accountViewModel)

        Column(
            Modifier
                .padding(start = 10.dp)
                .height(35.dp)
                .weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = remember(channelState) { channel.toBestDisplayName() },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Row(
            modifier =
                Modifier
                    .height(Size35dp)
                    .padding(start = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShortEphemeralChatActionOptions(channel, accountViewModel, nav)
        }
    }
}

@Composable
private fun DrawRelayIcon(
    channel: EphemeralChatChannel,
    accountViewModel: AccountViewModel,
) {
    val relayInfo = loadRelayInfo(channel.roomId.relayUrl, accountViewModel)
    val info =
        remember(channel.roomId.relayUrl) {
            RelayBriefInfoCache.get(RelayUrlFormatter.normalize(channel.roomId.relayUrl))
        }

    RobohashFallbackAsyncImage(
        robot = channel.idHex,
        model = relayInfo?.icon ?: info.favIcon,
        contentDescription = stringRes(R.string.profile_image),
        contentScale = ContentScale.Crop,
        modifier = HeaderPictureModifier,
        loadProfilePicture = accountViewModel.settings.showProfilePictures.value,
        loadRobohash = accountViewModel.settings.featureSet != FeatureSetType.PERFORMANCE,
    )
}

@Composable
fun ShortEphemeralChatActionOptions(
    channel: EphemeralChatChannel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    JoinEphemeralChatButtonIfNotAlreadyJoined(channel, accountViewModel, nav)
}

@Composable
fun JoinEphemeralChatButtonIfNotAlreadyJoined(
    channel: EphemeralChatChannel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val isFollowing by observeUserIsFollowingChannel(accountViewModel.account, channel)

    if (!isFollowing) {
        JoinChatButton(channel, accountViewModel, nav)
    } else {
        LeaveChatButton(channel, accountViewModel, nav)
    }
}
