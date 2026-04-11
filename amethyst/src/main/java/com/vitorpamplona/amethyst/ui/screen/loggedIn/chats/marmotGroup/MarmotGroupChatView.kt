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

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.reply_here
import com.vitorpamplona.amethyst.ui.components.ThinPaddingTextField
import com.vitorpamplona.amethyst.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.RefreshingChatroomFeedView
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.utils.ThinSendButton
import com.vitorpamplona.amethyst.ui.theme.DoubleVertSpacer
import com.vitorpamplona.amethyst.ui.theme.EditFieldBorder
import com.vitorpamplona.amethyst.ui.theme.EditFieldModifier
import com.vitorpamplona.amethyst.ui.theme.EditFieldTrailingIconModifier
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

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

    val chatroom =
        remember(nostrGroupId) {
            accountViewModel.account.marmotGroupList.getOrCreateGroup(nostrGroupId)
        }

    DisposableEffect(nostrGroupId) {
        chatroom.markAsRead()
        onDispose { }
    }

    Column(Modifier.fillMaxHeight()) {
        Column(
            modifier =
                Modifier
                    .fillMaxHeight()
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
    val canPost by remember { derivedStateOf { messageState.text.isNotBlank() } }
    val context = LocalContext.current

    Column(modifier = EditFieldModifier) {
        ThinPaddingTextField(
            state = messageState,
            modifier = Modifier.fillMaxWidth(),
            shape = EditFieldBorder,
            placeholder = {
                Text(
                    text = stringResource(Res.string.reply_here),
                    color = MaterialTheme.colorScheme.placeholderText,
                )
            },
            trailingIcon = {
                ThinSendButton(
                    isActive = canPost,
                    modifier = EditFieldTrailingIconModifier,
                ) {
                    val text = messageState.text.toString().trim()
                    if (text.isNotEmpty()) {
                        scope.launch(Dispatchers.IO) {
                            try {
                                accountViewModel.sendMarmotGroupMessage(nostrGroupId, text)
                                messageState.clearText()
                                onMessageSent()
                            } catch (e: Exception) {
                                launch(Dispatchers.Main) {
                                    Toast
                                        .makeText(
                                            context,
                                            "Failed to send message: ${e.message}",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                }
                            }
                        }
                    }
                }
            },
            colors =
                TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
        )
    }
}
