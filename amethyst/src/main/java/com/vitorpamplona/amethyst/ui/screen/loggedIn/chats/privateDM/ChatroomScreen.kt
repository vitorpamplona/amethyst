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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.vitorpamplona.amethyst.ui.call.rememberCallWithPermission
import com.vitorpamplona.amethyst.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM.header.RenderRoomTopBar
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey
import com.vitorpamplona.quartz.nipACWebRtcCalls.tags.CallType

@Composable
fun ChatroomScreen(
    roomId: ChatroomKey,
    draftMessage: String? = null,
    replyToNote: HexKey? = null,
    editFromDraft: HexKey? = null,
    expiresDays: Int? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val context = LocalContext.current
    val startCall =
        rememberCallWithPermission(context) {
            val peerPubKey = roomId.users.firstOrNull() ?: return@rememberCallWithPermission
            accountViewModel.callController?.initiateCall(peerPubKey, CallType.VOICE)
            nav.nav(Route.ActiveCall(callId = "", peerPubKey = peerPubKey))
        }

    DisappearingScaffold(
        isInvertedLayout = true,
        topBar = {
            RenderRoomTopBar(
                room = roomId,
                accountViewModel = accountViewModel,
                nav = nav,
                onCallClick = { _ -> startCall() },
            )
        },
        accountViewModel = accountViewModel,
    ) {
        Column(Modifier.padding(it).consumeWindowInsets(it).statusBarsPadding()) {
            ChatroomView(
                room = roomId,
                draftMessage = draftMessage,
                replyToNote = replyToNote,
                editFromDraft = editFromDraft,
                expiresDays = expiresDays,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}
