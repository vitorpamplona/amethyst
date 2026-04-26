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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM.send

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.actions.MentionPreservingInputTransformation
import com.vitorpamplona.amethyst.ui.actions.StrippingFailureDialog
import com.vitorpamplona.amethyst.ui.actions.UrlUserTagOutputTransformation
import com.vitorpamplona.amethyst.ui.actions.uploads.SelectFromGallery
import com.vitorpamplona.amethyst.ui.components.ThinPaddingTextField
import com.vitorpamplona.amethyst.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.routeFor
import com.vitorpamplona.amethyst.ui.note.ClickableUserPicture
import com.vitorpamplona.amethyst.ui.note.creators.emojiSuggestions.ShowEmojiSuggestionList
import com.vitorpamplona.amethyst.ui.note.creators.userSuggestions.ShowUserSuggestionList
import com.vitorpamplona.amethyst.ui.note.showCount
import com.vitorpamplona.amethyst.ui.note.timeAheadNoDot
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
import com.vitorpamplona.amethyst.ui.theme.Size25dp
import com.vitorpamplona.amethyst.ui.theme.SpacedBy10dp
import com.vitorpamplona.amethyst.ui.theme.SuggestionListDefaultHeightChat
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

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
        if (channelScreenModel.message.text.isNotBlank()) {
            accountViewModel.launchSigner {
                channelScreenModel.sendDraftSync()
            }
        }
        channelScreenModel.cancel()
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

    StrippingFailureDialog(channelScreenModel.strippingFailureConfirmation)

    channelScreenModel.encryptedUploadErrorTitle?.let { title ->
        EncryptedUploadErrorDialog(
            title = title,
            message = channelScreenModel.encryptedUploadErrorMessage ?: "",
            onDismiss = channelScreenModel::dismissEncryptedUploadError,
            onRetryWithoutEncryption = channelScreenModel::retryWithoutEncryption,
        )
    }

    Column(
        modifier = EditFieldModifier,
    ) {
        channelScreenModel.userSuggestions?.let {
            ShowUserSuggestionList(
                it,
                channelScreenModel::autocompleteWithUser,
                accountViewModel,
                SuggestionListDefaultHeightChat,
            )
        }

        channelScreenModel.emojiSuggestions?.let {
            ShowEmojiSuggestionList(
                it,
                channelScreenModel::autocompleteWithEmoji,
                channelScreenModel::autocompleteWithEmojiUrl,
                SuggestionListDefaultHeightChat,
            )
        }

        if (channelScreenModel.wantsExpirationDate) {
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.Center) {
                val context = LocalContext.current
                Text(
                    stringRes(
                        R.string.this_message_will_disappear_in,
                        timeAheadNoDot(channelScreenModel.expirationDate, context),
                    ),
                    fontSize = Font12SP,
                    color = MaterialTheme.colorScheme.placeholderText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        val missingRelays by channelScreenModel.recipientsMissingDmRelays.collectAsStateWithLifecycle()
        if (missingRelays.isNotEmpty()) {
            RecipientMissingRelaysWarning(missingRelays, accountViewModel, nav)
        } else {
            EditField(channelScreenModel, onSendNewMessage, accountViewModel)
        }
    }
}

@Composable
fun EditField(
    channelScreenModel: ChatNewMessageViewModel,
    onSendNewMessage: () -> Unit,
    accountViewModel: AccountViewModel,
) {
    ThinPaddingTextField(
        state = channelScreenModel.message,
        onTextChanged = { channelScreenModel.onMessageChanged() },
        inputTransformation = MentionPreservingInputTransformation,
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
        outputTransformation = UrlUserTagOutputTransformation(MaterialTheme.colorScheme.primary),
    )
}

@Preview
@Composable
fun RecipientMissingRelaysWarningPreview() {
    val user1 = LocalCache.getOrCreateUser("460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c")
    val user2 = LocalCache.getOrCreateUser("ca89cb11f1c75d5b6622268ff43d2288ea8b2cb5b9aa996ff9ff704fc904b78b")

    ThemeComparisonColumn {
        RecipientMissingRelaysWarning(
            persistentListOf(user1, user2),
            mockAccountViewModel(),
            EmptyNav(),
        )
    }
}

@Composable
fun RecipientMissingRelaysWarning(
    users: ImmutableList<User>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = SpacedBy10dp,
    ) {
        UserGallery(users) { user ->
            ClickableUserPicture(
                user,
                Size25dp,
                accountViewModel,
                onClick = {
                    nav.nav { routeFor(user) }
                },
            )
        }

        Text(
            text = stringRes(R.string.recipient_missing_dm_relays),
            color = MaterialTheme.colorScheme.error,
            fontSize = Font12SP,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun UserGallery(
    users: ImmutableList<User>,
    galleryUser: @Composable RowScope.(user: User) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy((-10).dp),
    ) {
        users.take(6).forEach {
            key(it.pubkeyHex) {
                galleryUser(it)
            }
        }

        if (users.size > 6) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(Size25dp)
                        .clip(shape = CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
            ) {
                Text(
                    text = "+" + showCount(users.size - 6),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
fun KeyboardLeadingIcon(
    channelScreenModel: ChatNewMessageViewModel,
    accountViewModel: AccountViewModel,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 4.dp, end = 4.dp),
    ) {
        SelectFromGallery(
            isUploading = channelScreenModel.isUploadingImage,
            tint = MaterialTheme.colorScheme.placeholderText,
            modifier = Modifier,
            onImageChosen = channelScreenModel::pickedMedia,
        )
    }
}

@Composable
fun EncryptedUploadErrorDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onRetryWithoutEncryption: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(message)
                Text(
                    stringRes(R.string.upload_without_encryption_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onRetryWithoutEncryption) {
                Text(stringRes(R.string.retry_without_encryption))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringRes(R.string.cancel))
            }
        },
    )
}
