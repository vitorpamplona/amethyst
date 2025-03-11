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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM.send

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.actions.UrlUserTagTransformation
import com.vitorpamplona.amethyst.ui.actions.uploads.SelectFromGallery
import com.vitorpamplona.amethyst.ui.components.ThinPaddingTextField
import com.vitorpamplona.amethyst.ui.navigation.EmptyNav
import com.vitorpamplona.amethyst.ui.navigation.INav
import com.vitorpamplona.amethyst.ui.note.IncognitoIconOff
import com.vitorpamplona.amethyst.ui.note.IncognitoIconOn
import com.vitorpamplona.amethyst.ui.note.QuickActionAlertDialog
import com.vitorpamplona.amethyst.ui.note.ShowEmojiSuggestionList
import com.vitorpamplona.amethyst.ui.note.ShowUserSuggestionList
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM.send.upload.RoomChatFileUploadDialog
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.utils.DisplayReplyingToNote
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.utils.ThinSendButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.mockAccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.EditFieldBorder
import com.vitorpamplona.amethyst.ui.theme.EditFieldLeadingIconModifier
import com.vitorpamplona.amethyst.ui.theme.EditFieldModifier
import com.vitorpamplona.amethyst.ui.theme.EditFieldTrailingIconModifier
import com.vitorpamplona.amethyst.ui.theme.Size30Modifier
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonRow
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@Preview
@Composable
fun PrivateMessageEditFieldRow() {
    val channelScreenModel: ChatNewMessageViewModel = viewModel()
    val accountViewModel = mockAccountViewModel()
    channelScreenModel.init(accountViewModel)

    ThemeComparisonRow {
        PrivateMessageEditFieldRow(
            channelScreenModel = channelScreenModel,
            accountViewModel = accountViewModel,
            onSendNewMessage = {},
            nav = EmptyNav,
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
    channelScreenModel.replyTo.value?.let {
        DisplayReplyingToNote(it, accountViewModel, nav) {
            channelScreenModel.clearReply()
        }
    }

    LaunchedEffect(key1 = channelScreenModel.draftTag) {
        launch(Dispatchers.IO) {
            channelScreenModel.draftTextChanges
                .receiveAsFlow()
                .debounce(1000)
                .collectLatest {
                    channelScreenModel.sendDraft()
                }
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
        ShowUserSuggestionList(
            channelScreenModel.userSuggestions.userSuggestions,
            channelScreenModel::autocompleteWithUser,
            accountViewModel,
        )

        ShowEmojiSuggestionList(
            channelScreenModel.emojiSuggestions,
            channelScreenModel::autocompleteWithEmoji,
            channelScreenModel::autocompleteWithEmojiUrl,
            accountViewModel,
        )

        ThinPaddingTextField(
            value = channelScreenModel.message,
            onValueChange = { channelScreenModel.updateMessage(it) },
            keyboardOptions =
                KeyboardOptions.Default.copy(
                    capitalization = KeyboardCapitalization.Sentences,
                ),
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
                    isActive = channelScreenModel.message.text.isNotBlank() && !channelScreenModel.isUploadingImage,
                    modifier = EditFieldTrailingIconModifier,
                ) {
                    channelScreenModel.sendPost(onSendNewMessage)
                }
            },
            leadingIcon = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 6.dp),
                ) {
                    SelectFromGallery(
                        isUploading = channelScreenModel.isUploadingImage,
                        tint = MaterialTheme.colorScheme.placeholderText,
                        modifier = EditFieldLeadingIconModifier,
                        onImageChosen = channelScreenModel::pickedMedia,
                    )

                    var wantsToActivateNIP17 by remember { mutableStateOf(false) }

                    if (wantsToActivateNIP17) {
                        NewFeatureNIP17AlertDialog(
                            accountViewModel = accountViewModel,
                            onConfirm = { channelScreenModel.toggleNIP04And24() },
                            onDismiss = { wantsToActivateNIP17 = false },
                        )
                    }

                    IconButton(
                        modifier = Size30Modifier,
                        onClick = {
                            if (
                                !accountViewModel.account.settings.hideNIP17WarningDialog &&
                                !channelScreenModel.nip17 &&
                                !channelScreenModel.requiresNIP17
                            ) {
                                wantsToActivateNIP17 = true
                            } else {
                                channelScreenModel.toggleNIP04And24()
                            }
                        },
                    ) {
                        if (channelScreenModel.nip17) {
                            IncognitoIconOn(
                                modifier =
                                    Modifier
                                        .padding(top = 2.dp)
                                        .size(18.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            IncognitoIconOff(
                                modifier =
                                    Modifier
                                        .padding(top = 2.dp)
                                        .size(18.dp),
                                tint = MaterialTheme.colorScheme.placeholderText,
                            )
                        }
                    }
                }
            },
            colors =
                TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            visualTransformation = UrlUserTagTransformation(MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
fun NewFeatureNIP17AlertDialog(
    accountViewModel: AccountViewModel,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    QuickActionAlertDialog(
        title = stringRes(R.string.new_feature_nip17_might_not_be_available_title),
        textContent = stringRes(R.string.new_feature_nip17_might_not_be_available_description),
        buttonIconResource = R.drawable.incognito,
        buttonText = stringRes(R.string.new_feature_nip17_activate),
        onClickDoOnce = {
            scope.launch { onConfirm() }
            onDismiss()
        },
        onClickDontShowAgain = {
            scope.launch {
                onConfirm()
                accountViewModel.account.settings.setHideNIP17WarningDialog()
            }
            onDismiss()
        },
        onDismiss = onDismiss,
    )
}
