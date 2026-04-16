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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.actions.uploads.SelectFromGallery
import com.vitorpamplona.amethyst.ui.actions.uploads.SelectedMedia
import com.vitorpamplona.amethyst.ui.components.ThinPaddingTextField
import com.vitorpamplona.amethyst.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.RefreshingChatroomFeedView
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.marmotGroup.send.MarmotFileSender
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.marmotGroup.send.MarmotFileUploader
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.utils.ChatFileUploadDialog
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.utils.ChatFileUploadState
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.utils.ThinSendButton
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DoubleVertSpacer
import com.vitorpamplona.amethyst.ui.theme.EditFieldBorder
import com.vitorpamplona.amethyst.ui.theme.EditFieldModifier
import com.vitorpamplona.amethyst.ui.theme.EditFieldTrailingIconModifier
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.collections.immutable.ImmutableList
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
            nav = nav,
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
    nav: INav,
    onMessageSent: suspend () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val messageState = remember { TextFieldState() }
    val canPost by remember { derivedStateOf { messageState.text.isNotBlank() } }
    val context = LocalContext.current

    var isUploading by remember { mutableStateOf(false) }
    val uploadState =
        remember {
            ChatFileUploadState(
                defaultServer = accountViewModel.account.settings.defaultFileServer,
                defaultStripMetadata = accountViewModel.account.settings.stripLocationOnUpload,
            )
        }

    // Upload dialog
    uploadState.multiOrchestrator?.let {
        MarmotGroupFileUploadDialog(
            nostrGroupId = nostrGroupId,
            state = uploadState,
            accountViewModel = accountViewModel,
            nav = nav,
            onUpload = { onMessageSent() },
            onCancel = uploadState::reset,
        )
    }

    Column(modifier = EditFieldModifier) {
        ThinPaddingTextField(
            state = messageState,
            modifier = Modifier.fillMaxWidth(),
            shape = EditFieldBorder,
            placeholder = {
                Text(
                    text = stringRes(R.string.reply_here),
                    color = MaterialTheme.colorScheme.placeholderText,
                )
            },
            leadingIcon = {
                MarmotGalleryLeadingIcon(
                    isUploading = isUploading,
                    onImageChosen = { selectedMedia ->
                        uploadState.load(selectedMedia)
                    },
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

@Composable
private fun MarmotGalleryLeadingIcon(
    isUploading: Boolean,
    onImageChosen: (ImmutableList<SelectedMedia>) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 4.dp, end = 4.dp),
    ) {
        SelectFromGallery(
            isUploading = isUploading,
            tint = MaterialTheme.colorScheme.placeholderText,
            modifier = Modifier,
            onImageChosen = onImageChosen,
        )
    }
}

@Composable
private fun MarmotGroupFileUploadDialog(
    nostrGroupId: HexKey,
    state: ChatFileUploadState,
    accountViewModel: AccountViewModel,
    nav: INav,
    onUpload: suspend () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    ChatFileUploadDialog(
        state = state,
        title = {
            val chatroom =
                remember(nostrGroupId) {
                    accountViewModel.account.marmotGroupList.getOrCreateGroup(nostrGroupId)
                }
            Text(chatroom.displayName.value ?: "Marmot Group")
        },
        upload = {
            scope.launch(Dispatchers.IO) {
                val exporterSecret = accountViewModel.marmotMediaExporterSecret(nostrGroupId)
                if (exporterSecret == null) {
                    launch(Dispatchers.Main) {
                        Toast
                            .makeText(
                                context,
                                "Not a member of this group",
                                Toast.LENGTH_SHORT,
                            ).show()
                    }
                    return@launch
                }

                MarmotFileUploader(accountViewModel.account).uploadMip04(
                    viewState = state,
                    exporterSecret = exporterSecret,
                    onError = { title, message ->
                        scope.launch(Dispatchers.Main) {
                            Toast.makeText(context, "$title: $message", Toast.LENGTH_LONG).show()
                        }
                    },
                    context = context,
                    onceUploaded = { uploads ->
                        MarmotFileSender(nostrGroupId, accountViewModel).send(uploads)
                        onUpload()
                    },
                )

                accountViewModel.account.settings.changeDefaultFileServer(state.selectedServer)
                accountViewModel.account.settings.changeStripLocationOnUpload(state.stripMetadata)
            }
        },
        onCancel = onCancel,
        accountViewModel = accountViewModel,
        nav = nav,
        isNip17 = false,
    )
}
