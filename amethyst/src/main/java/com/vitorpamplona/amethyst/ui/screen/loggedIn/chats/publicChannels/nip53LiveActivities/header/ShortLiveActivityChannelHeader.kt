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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.nip53LiveActivities.header

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.nip53LiveActivities.LiveActivitiesChannel
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.channel.observeChannel
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.LikeReaction
import com.vitorpamplona.amethyst.ui.note.UserPicture
import com.vitorpamplona.amethyst.ui.note.ZapReaction
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.nip53LiveActivities.LiveFlag
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.nip53LiveActivities.OfflineFlag
import com.vitorpamplona.amethyst.ui.screen.loggedIn.home.CheckIfVideoIsOnline
import com.vitorpamplona.amethyst.ui.theme.RowColSpacing
import com.vitorpamplona.amethyst.ui.theme.Size34dp
import com.vitorpamplona.amethyst.ui.theme.Size35dp
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent

@Composable
fun ShortLiveActivityChannelHeader(
    baseChannel: LiveActivitiesChannel,
    showFlag: Boolean,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val channelState by observeChannel(baseChannel, accountViewModel)
    val channel = channelState?.channel as? LiveActivitiesChannel ?: return

    ShortLiveActivityChannelHeader(
        name = channel.toBestDisplayName(),
        creator = channel.creator,
        liveActivitiesEvent = channel.info,
        showFlag = showFlag,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

@Composable
fun ShortLiveActivityChannelHeader(
    name: String,
    creator: User?,
    liveActivitiesEvent: LiveActivitiesEvent?,
    showFlag: Boolean,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        creator?.let {
            UserPicture(
                user = it,
                size = Size34dp,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }

        Column(
            modifier =
                Modifier
                    .padding(start = 10.dp)
                    .height(35.dp)
                    .weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        liveActivitiesEvent?.let {
            Row(
                modifier =
                    Modifier
                        .height(Size35dp)
                        .padding(start = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LiveChannelActionOptions(it, showFlag, accountViewModel, nav)
            }
        }
    }
}

@Composable
fun LiveChannelActionOptions(
    activity: LiveActivitiesEvent,
    showFlag: Boolean = true,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val isLive by remember(activity) { derivedStateOf { activity.isLive() } }
    val url = activity.streaming()

    if (showFlag && isLive && url != null) {
        CheckIfVideoIsOnline(url, accountViewModel) { isOnline ->
            if (isOnline) {
                LiveFlag()
            } else {
                OfflineFlag()
            }
        }
        Spacer(modifier = StdHorzSpacer)
    }

    val note = remember(activity) { LocalCache.getAddressableNoteIfExists(activity.address()) }
    note?.let {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = RowColSpacing,
        ) {
            LikeReaction(
                baseNote = it,
                grayTint = MaterialTheme.colorScheme.onSurface,
                accountViewModel = accountViewModel,
                nav,
            )
        }
        Spacer(modifier = StdHorzSpacer)
        ZapReaction(
            baseNote = it,
            grayTint = MaterialTheme.colorScheme.onSurface,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}
