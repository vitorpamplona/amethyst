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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import com.vitorpamplona.amethyst.ui.actions.MentionPreservingInputTransformation
import com.vitorpamplona.amethyst.ui.actions.UrlUserTagOutputTransformation
import com.vitorpamplona.amethyst.ui.actions.uploads.SelectFromGallery
import com.vitorpamplona.amethyst.ui.actions.uploads.SelectedMedia
import com.vitorpamplona.amethyst.ui.components.ThinPaddingTextField
import com.vitorpamplona.amethyst.ui.feeds.WatchLifecycleAndUpdateModel
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.creators.userSuggestions.ShowUserSuggestionList
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.RefreshingChatroomFeedView
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.marmotGroup.send.MarmotFileSender
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.marmotGroup.send.MarmotFileUploader
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.marmotGroup.send.MarmotNewMessageViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.utils.ChatFileUploadDialog
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.utils.ChatFileUploadState
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.utils.DisplayReplyingToNote
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.utils.ThinSendButton
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DoubleVertSpacer
import com.vitorpamplona.amethyst.ui.theme.EditFieldBorder
import com.vitorpamplona.amethyst.ui.theme.EditFieldModifier
import com.vitorpamplona.amethyst.ui.theme.EditFieldTrailingIconModifier
import com.vitorpamplona.amethyst.ui.theme.SuggestionListDefaultHeightChat
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun MarmotGroupChatView(
    nostrGroupId: HexKey,
    draftMessage: String? = null,
    replyToInnerNote: HexKey? = null,
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

    val newMessageModel: MarmotNewMessageViewModel = viewModel(key = nostrGroupId + "MarmotNewMessageViewModel")
    newMessageModel.init(accountViewModel)
    newMessageModel.load(nostrGroupId)

    // Resolve the navigation-supplied replyId (e.g. tapping reply on an MLS
    // message in the Notifications screen) into the actual Note once it has
    // landed in LocalCache. checkGetOrCreateNote is a no-op for unknown ids.
    if (replyToInnerNote != null) {
        LaunchedEffect(replyToInnerNote) {
            val parent = accountViewModel.checkGetOrCreateNote(replyToInnerNote)
            if (parent != null) {
                newMessageModel.reply(parent)
            }
        }
    }

    if (draftMessage != null) {
        LaunchedEffect(draftMessage) {
            newMessageModel.editFromDraft(draftMessage)
        }
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
                onWantsToReply = { note -> newMessageModel.reply(note) },
                onWantsToEditDraft = { },
            )
        }

        Spacer(modifier = DoubleVertSpacer)

        MarmotGroupMessageComposer(
            nostrGroupId = nostrGroupId,
            newMessageModel = newMessageModel,
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
    newMessageModel: MarmotNewMessageViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
    onMessageSent: suspend () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val canPost by remember { derivedStateOf { newMessageModel.canPost() } }
    val context = LocalContext.current

    var isUploading by remember { mutableStateOf(false) }

    DisposableEffect(nostrGroupId) {
        onDispose { newMessageModel.userSuggestions?.reset() }
    }

    // Upload dialog
    newMessageModel.uploadState?.let { uploadState ->
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
    }

    newMessageModel.replyTo.value?.let {
        DisplayReplyingToNote(it, accountViewModel, nav) {
            newMessageModel.clearReply()
        }
    }

    Column(modifier = EditFieldModifier) {
        newMessageModel.userSuggestions?.let {
            ShowUserSuggestionList(
                it,
                newMessageModel::autocompleteWithUser,
                accountViewModel,
                SuggestionListDefaultHeightChat,
            )
        }

        ThinPaddingTextField(
            state = newMessageModel.message,
            onTextChanged = { newMessageModel.onMessageChanged() },
            onContentReceived = { uri, mimeType ->
                newMessageModel.pickedMedia(persistentListOf(SelectedMedia(uri, mimeType)))
            },
            inputTransformation = MentionPreservingInputTransformation,
            outputTransformation = UrlUserTagOutputTransformation(MaterialTheme.colorScheme.primary),
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
                    onImageChosen = newMessageModel::pickedMedia,
                )
            },
            trailingIcon = {
                ThinSendButton(
                    isActive = canPost,
                    modifier = EditFieldTrailingIconModifier,
                ) {
                    scope.launch(Dispatchers.IO) {
                        try {
                            newMessageModel.sendPost()
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
