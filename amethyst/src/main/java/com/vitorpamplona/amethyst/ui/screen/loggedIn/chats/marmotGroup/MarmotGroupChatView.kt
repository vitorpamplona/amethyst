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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.marmotGroup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.RefreshingChatroomFeedView
import com.vitorpamplona.amethyst.ui.theme.DoubleVertSpacer
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun MarmotGroupChatView(
    nostrGroupId: HexKey,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val feedViewModel: MarmotGroupFeedViewModel =
        viewModel(
            key = nostrGroupId + "MarmotGroupFeedViewModel",
            factory =
                MarmotGroupFeedViewModel.Factory(
                    nostrGroupId,
                    accountViewModel.account,
                ),
        )

    WatchLifecycleAndUpdateModel(feedViewModel)

    Column(Modifier.fillMaxHeight()) {
        Column(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .padding(vertical = 0.dp)
                    .weight(1f, true),
        ) {
            RefreshingChatroomFeedView(
                feedContentState = feedViewModel.feedState,
                accountViewModel = accountViewModel,
                nav = nav,
                routeForLastRead = "MarmotGroup/$nostrGroupId",
                onWantsToReply = { },
                onWantsToEditDraft = { },
            )
        }

        Spacer(modifier = DoubleVertSpacer)

        MarmotGroupMessageComposer(
            nostrGroupId = nostrGroupId,
            accountViewModel = accountViewModel,
            onMessageSent = {
                feedViewModel.feedState.sendToTop()
            },
        )
    }
}

@Composable
fun MarmotGroupMessageComposer(
    nostrGroupId: HexKey,
    accountViewModel: AccountViewModel,
    onMessageSent: suspend () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val messageState = remember { TextFieldState() }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        OutlinedTextField(
            state = messageState,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Message") },
            lineLimits =
                androidx.compose.foundation.text.input.TextFieldLineLimits
                    .MultiLine(maxHeightInLines = 4),
        )
        IconButton(
            onClick = {
                val text = messageState.text.toString().trim()
                if (text.isNotEmpty()) {
                    scope.launch(Dispatchers.IO) {
                        accountViewModel.sendMarmotGroupMessage(nostrGroupId, text)
                        messageState.clearText()
                        onMessageSent()
                    }
                }
            },
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send",
            )
        }
    }
}
