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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM.send

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.actions.UrlUserTagTransformation
import com.vitorpamplona.amethyst.ui.actions.uploads.SelectFromGallery
import com.vitorpamplona.amethyst.ui.components.ThinPaddingTextField
import com.vitorpamplona.amethyst.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.creators.emojiSuggestions.ShowEmojiSuggestionList
import com.vitorpamplona.amethyst.ui.note.creators.userSuggestions.ShowUserSuggestionList
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM.send.upload.RoomChatFileUploadDialog
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.utils.DisplayReplyingToNote
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.utils.ThinSendButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.mockAccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.EditFieldBorder
import com.vitorpamplona.amethyst.ui.theme.EditFieldModifier
import com.vitorpamplona.amethyst.ui.theme.EditFieldTrailingIconModifier
import com.vitorpamplona.amethyst.ui.theme.Font12SP
import com.vitorpamplona.amethyst.ui.theme.PostKeyboard
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.ui.theme.placeholderText

@Preview
@Composable
fun PrivateMessageEditFieldRowPreview() {
    val channelScreenModel: ChatNewMessageViewModel = viewModel()
    val accountViewModel = mockAccountViewModel()
    channelScreenModel.init(accountViewModel)

    ThemeComparisonColumn {
        PrivateMessageEditFieldRow(
            channelScreenModel = channelScreenModel,
            accountViewModel = accountViewModel,
            onSendNewMessage = {},
            nav = EmptyNav(),
        )
    }
}

@Composable
fun PrivateMessageEditFieldRow(
    channelScreenModel: ChatNewMessageViewModel,
    accountViewModel: AccountViewModel,
    onSendNewMessage: () -> Unit,
    nav: INav,
) {
    BackHandler {
        accountViewModel.launchSigner {
            channelScreenModel.sendDraftSync()
            channelScreenModel.cancel()
        }
        nav.popBack()
    }

    channelScreenModel.replyTo.value?.let {
        DisplayReplyingToNote(it, accountViewModel, nav) {
            channelScreenModel.clearReply()
        }
    }

    channelScreenModel.uploadState?.let { uploading ->
        uploading.multiOrchestrator?.let { selectedFiles ->
            RoomChatFileUploadDialog(
                channelScreenModel = channelScreenModel,
                state = uploading,
                onUpload = onSendNewMessage,
                onCancel = uploading::reset,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }

    Column(
        modifier = EditFieldModifier,
    ) {
        channelScreenModel.userSuggestions?.let {
            ShowUserSuggestionList(
                it,
                channelScreenModel::autocompleteWithUser,
                accountViewModel,
            )
        }

        channelScreenModel.emojiSuggestions?.let {
            ShowEmojiSuggestionList(
                it,
                channelScreenModel::autocompleteWithEmoji,
                channelScreenModel::autocompleteWithEmojiUrl,
            )
        }

        channelScreenModel.expirationDays?.let {
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.Center) {
                Text(
                    stringResource(R.string.this_message_will_disappear_in_days, it),
                    fontSize = Font12SP,
                    color = MaterialTheme.colorScheme.placeholderText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        EditField(channelScreenModel, onSendNewMessage, accountViewModel)
    }
}

@Composable
fun EditField(
    channelScreenModel: ChatNewMessageViewModel,
    onSendNewMessage: () -> Unit,
    accountViewModel: AccountViewModel,
) {
    ThinPaddingTextField(
        value = channelScreenModel.message,
        onValueChange = { channelScreenModel.updateMessage(it) },
        keyboardOptions = PostKeyboard,
        shape = EditFieldBorder,
        modifier = Modifier.fillMaxWidth(),
        placeholder = {
            Text(
                text = stringRes(R.string.reply_here),
                color = MaterialTheme.colorScheme.placeholderText,
            )
        },
        trailingIcon = {
            ThinSendButton(
                isActive = channelScreenModel.canPost(),
                modifier = EditFieldTrailingIconModifier,
            ) {
                accountViewModel.launchSigner {
                    channelScreenModel.sendPostSync()
                    onSendNewMessage()
                }
            }
        },
        leadingIcon = {
            KeyboardLeadingIcon(channelScreenModel, accountViewModel)
        },
        colors =
            TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        visualTransformation = UrlUserTagTransformation(MaterialTheme.colorScheme.primary),
    )
}

@Composable
fun KeyboardLeadingIcon(
    channelScreenModel: ChatNewMessageViewModel,
    accountViewModel: AccountViewModel,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 4.dp, end = 10.dp),
    ) {
        SelectFromGallery(
            isUploading = channelScreenModel.isUploadingImage,
            tint = MaterialTheme.colorScheme.placeholderText,
            modifier = Modifier,
            onImageChosen = channelScreenModel::pickedMedia,
        )

        ToggleNip17Button(channelScreenModel, accountViewModel)
    }
}
