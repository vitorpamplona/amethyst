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
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.vitorpamplona.amethyst.model.EphemeralChatChannel
import com.vitorpamplona.amethyst.model.LiveActivitiesChannel
import com.vitorpamplona.amethyst.model.PublicChatChannel
import com.vitorpamplona.amethyst.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.ui.navigation.INav
import com.vitorpamplona.amethyst.ui.note.LoadChannel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.ephemChat.header.EphemeralChatTopBar
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.nip28PublicChat.header.PublicChatTopBar
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.nip53LiveActivities.header.LiveActivityTopBar
import com.vitorpamplona.quartz.experimental.ephemChat.chat.RoomId

@Composable
fun ChannelScreen(
    channelId: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (channelId == null) return

    DisappearingScaffold(
        isInvertedLayout = true,
        topBar = {
            LoadChannel(channelId, accountViewModel) {
                when (it) {
                    is EphemeralChatChannel -> EphemeralChatTopBar(it, accountViewModel, nav)
                    is PublicChatChannel -> PublicChatTopBar(it, accountViewModel, nav)
                    is LiveActivitiesChannel -> LiveActivityTopBar(it, accountViewModel, nav)
                }
            }
        },
        accountViewModel = accountViewModel,
    ) {
        Column(Modifier.padding(it)) {
            ChannelView(channelId, accountViewModel, nav)
        }
    }
}

@Composable
fun EphemeralChatScreen(
    channelId: RoomId,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    DisappearingScaffold(
        isInvertedLayout = true,
        topBar = {
            LoadChannel(channelId, accountViewModel) {
                EphemeralChatTopBar(it, accountViewModel, nav)
            }
        },
        accountViewModel = accountViewModel,
    ) {
        Column(Modifier.padding(it)) {
            ChannelView(channelId, accountViewModel, nav)
        }
    }
}
