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

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement.Absolute.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.richtext.BaseMediaContent
import com.vitorpamplona.amethyst.commons.richtext.EncryptedMediaUrlImage
import com.vitorpamplona.amethyst.commons.richtext.EncryptedMediaUrlVideo
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlImage
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlVideo
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.amethyst.ui.actions.UrlUserTagTransformation
import com.vitorpamplona.amethyst.ui.actions.uploads.SelectFromGallery
import com.vitorpamplona.amethyst.ui.actions.uploads.SelectedMedia
import com.vitorpamplona.amethyst.ui.actions.uploads.TakePictureButton
import com.vitorpamplona.amethyst.ui.actions.uploads.TakeVideoButton
import com.vitorpamplona.amethyst.ui.components.ThinPaddingTextField
import com.vitorpamplona.amethyst.ui.components.ZoomableContentView
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.navs.Nav
import com.vitorpamplona.amethyst.ui.navigation.routes.routeToMessage
import com.vitorpamplona.amethyst.ui.navigation.topbars.PostingTopBar
import com.vitorpamplona.amethyst.ui.note.BaseUserPicture
import com.vitorpamplona.amethyst.ui.note.creators.contentWarning.ContentSensitivityExplainer
import com.vitorpamplona.amethyst.ui.note.creators.contentWarning.MarkAsSensitiveButton
import com.vitorpamplona.amethyst.ui.note.creators.emojiSuggestions.ShowEmojiSuggestionList
import com.vitorpamplona.amethyst.ui.note.creators.emojiSuggestions.WatchAndLoadMyEmojiList
import com.vitorpamplona.amethyst.ui.note.creators.invoice.AddLnInvoiceButton
import com.vitorpamplona.amethyst.ui.note.creators.invoice.NewPostInvoiceRequest
import com.vitorpamplona.amethyst.ui.note.creators.location.AddGeoHashButton
import com.vitorpamplona.amethyst.ui.note.creators.location.LocationAsHash
import com.vitorpamplona.amethyst.ui.note.creators.messagefield.IMessageField
import com.vitorpamplona.amethyst.ui.note.creators.messagefield.MessageField
import com.vitorpamplona.amethyst.ui.note.creators.previews.PreviewUrl
import com.vitorpamplona.amethyst.ui.note.creators.secretEmoji.AddSecretEmojiButton
import com.vitorpamplona.amethyst.ui.note.creators.secretEmoji.SecretEmojiRequest
import com.vitorpamplona.amethyst.ui.note.creators.uploads.ImageVideoDescription
import com.vitorpamplona.amethyst.ui.note.creators.userSuggestions.ShowUserSuggestionList
import com.vitorpamplona.amethyst.ui.note.creators.zapraiser.AddZapraiserButton
import com.vitorpamplona.amethyst.ui.note.creators.zapraiser.ZapRaiserRequest
import com.vitorpamplona.amethyst.ui.note.creators.zapsplits.ForwardZapTo
import com.vitorpamplona.amethyst.ui.note.creators.zapsplits.ForwardZapToButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM.send.upload.SuccessfulUploads
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.Font14SP
import com.vitorpamplona.amethyst.ui.theme.QuoteBorder
import com.vitorpamplona.amethyst.ui.theme.Size10dp
import com.vitorpamplona.amethyst.ui.theme.Size35dp
import com.vitorpamplona.amethyst.ui.theme.Size5dp
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun NewGroupDMScreen(
    message: String? = null,
    attachment: String? = null,
    accountViewModel: AccountViewModel,
    nav: Nav,
) {
    NewGroupDMScreen(message, attachment?.ifBlank { null }?.toUri(), accountViewModel, nav)
}

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun NewGroupDMScreen(
    message: String? = null,
    attachment: Uri? = null,
    accountViewModel: AccountViewModel,
    nav: Nav,
) {
    val postViewModel: ChatNewMessageViewModel = viewModel()
    postViewModel.init(accountViewModel)

    val context = LocalContext.current

    LaunchedEffect(postViewModel, accountViewModel) {
        message?.ifBlank { null }?.let {
            postViewModel.updateMessage(TextFieldValue(it))
        }
        attachment?.let {
            withContext(Dispatchers.IO) {
                val mediaType = context.contentResolver.getType(it)
                postViewModel.pickedMedia(persistentListOf(SelectedMedia(it, mediaType)))
            }
        }
    }

    WatchAndLoadMyEmojiList(accountViewModel)

    BackHandler {
        accountViewModel.launchSigner {
            postViewModel.sendDraftSync()
            postViewModel.cancel()
        }
        nav.popBack()
    }

    Scaffold(
        topBar = {
            PostingTopBar(
                titleRes = R.string.private_message,
                isActive = postViewModel::canPost,
                onCancel = {
                    // uses the accountViewModel scope to avoid cancelling this
                    // function when the postViewModel is released
                    accountViewModel.launchSigner {
                        postViewModel.sendDraftSync()
                        postViewModel.cancel()
                    }
                    nav.popBack()
                },
                onPost = {
                    // uses the accountViewModel scope to avoid cancelling this
                    // function when the postViewModel is released
                    accountViewModel.launchSigner {
                        postViewModel.sendPostSync()
                        postViewModel.room?.let {
                            nav.nav(routeToMessage(it, null, null, null, null, accountViewModel))
                        }
                    }
                    nav.popBack()
                },
            )
        },
    ) { pad ->
        Surface(
            modifier =
                Modifier
                    .padding(pad)
                    .consumeWindowInsets(pad)
                    .imePadding(),
        ) {
            GroupDMScreenContent(postViewModel, accountViewModel, nav)
        }
    }
}

@Composable
fun GroupDMScreenContent(
    postViewModel: ChatNewMessageViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Size10dp).weight(1f)) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(scrollState, reverseScrolling = true),
                verticalArrangement = spacedBy(Size10dp),
            ) {
                SendDirectMessageTo(postViewModel, accountViewModel)

                MessageFieldRow(postViewModel, accountViewModel)

                DisplayPreviews(postViewModel, accountViewModel, nav)

                if (postViewModel.wantsToMarkAsSensitive) {
                    ContentSensitivityExplainer()
                }

                if (postViewModel.wantsToAddGeoHash) {
                    LocationAsHash(postViewModel)
                }

                if (postViewModel.wantsForwardZapTo) {
                    ForwardZapTo(postViewModel, accountViewModel)
                }

                if (postViewModel.wantsInvoice) {
                    NewPostInvoiceRequest(
                        onSuccess = {
                            postViewModel.insertAtCursor(it)
                            postViewModel.wantsInvoice = false
                        },
                        accountViewModel,
                    )
                }

                if (postViewModel.wantsSecretEmoji) {
                    SecretEmojiRequest {
                        postViewModel.insertAtCursor(it)
                        postViewModel.wantsSecretEmoji = false
                    }
                }

                if (postViewModel.wantsZapraiser && postViewModel.hasLnAddress()) {
                    ZapRaiserRequest(
                        stringRes(id = R.string.zapraiser),
                        postViewModel,
                    )
                }

                postViewModel.uploadState?.let { uploading ->
                    uploading.multiOrchestrator?.let { selectedFiles ->
                        ImageVideoDescription(
                            selectedFiles,
                            accountViewModel.account.settings.defaultFileServer,
                            onAdd = { alt, server, sensitiveContent, mediaQuality, _ ->
                                postViewModel.uploadAndHold(
                                    accountViewModel.toastManager::toast,
                                    context,
                                    onceUploaded = { },
                                )
                                accountViewModel.account.settings.changeDefaultFileServer(server)
                            },
                            onDelete = { postViewModel.uploadState?.deleteMediaToUpload(it) },
                            onCancel = uploading::reset,
                            accountViewModel = accountViewModel,
                        )
                    }
                }
            }
        }

        postViewModel.userSuggestions?.let {
            ShowUserSuggestionList(
                it,
                postViewModel::autocompleteWithUser,
                accountViewModel,
                Modifier.heightIn(0.dp, 300.dp),
            )
        }

        postViewModel.emojiSuggestions?.let {
            ShowEmojiSuggestionList(
                it,
                postViewModel::autocompleteWithEmoji,
                postViewModel::autocompleteWithEmojiUrl,
                Modifier.heightIn(0.dp, 300.dp),
            )
        }

        BottomRowActions(postViewModel, accountViewModel)
    }
}

@Composable
fun MessageFieldRow(
    postViewModel: IMessageField,
    accountViewModel: AccountViewModel,
    requestFocus: Boolean = false,
) {
    Row {
        BaseUserPicture(
            accountViewModel.userProfile(),
            Size35dp,
            accountViewModel,
        )
        MessageField(R.string.write_a_message, postViewModel, requestFocus)
    }
}

@Composable
fun DisplayPreviews(
    postViewModel: ChatNewMessageViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val urlPreviews by postViewModel.urlPreviews.results.collectAsStateWithLifecycle(emptyList())

    if (postViewModel.uploadsWaitingToBeSent.isNotEmpty() || urlPreviews.isNotEmpty()) {
        Row(modifier = Modifier.padding(vertical = Size5dp)) {
            LazyRow(modifier = Modifier.height(100.dp)) {
                items(postViewModel.uploadsWaitingToBeSent) {
                    Box(modifier = Modifier.aspectRatio(1f).clip(shape = QuoteBorder)) {
                        ShowImageUploadGallery(it, accountViewModel)
                    }
                }

                items(urlPreviews) {
                    Box(modifier = Modifier.aspectRatio(1f).clip(shape = QuoteBorder)) {
                        PreviewUrl(it, accountViewModel, nav)
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomRowActions(
    postViewModel: ChatNewMessageViewModel,
    accountViewModel: AccountViewModel,
) {
    val scrollState = rememberScrollState()
    Row(
        modifier =
            Modifier
                .horizontalScroll(scrollState)
                .fillMaxWidth()
                .height(50.dp),
        verticalAlignment = CenterVertically,
    ) {
        if (postViewModel.room != null) {
            SelectFromGallery(
                isUploading = postViewModel.isUploadingImage,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier,
            ) {
                postViewModel.pickedMedia(it)
            }
        } else {
            IconButton(
                onClick = { accountViewModel.toastManager.toast(R.string.messages_cant_upload_title, R.string.messages_cant_upload_explainer) },
            ) {
                Icon(
                    imageVector = Icons.Default.AddPhotoAlternate,
                    contentDescription = stringRes(id = R.string.upload_image),
                    modifier = Modifier.height(25.dp),
                    tint = MaterialTheme.colorScheme.placeholderText,
                )
            }
        }

        if (postViewModel.room != null) {
            TakePictureButton(
                onPictureTaken = { postViewModel.pickedMedia(it) },
            )
        } else {
            IconButton(
                onClick = {
                    accountViewModel.toastManager.toast(R.string.messages_cant_upload_title, R.string.messages_cant_upload_explainer)
                },
            ) {
                Icon(
                    imageVector = Icons.Outlined.CameraAlt,
                    contentDescription = stringRes(id = R.string.take_a_picture),
                    modifier = Modifier.height(22.dp),
                    tint = MaterialTheme.colorScheme.placeholderText,
                )
            }
        }

        if (postViewModel.room != null) {
            TakeVideoButton(
                onVideoTaken = { postViewModel.pickedMedia(it) },
            )
        } else {
            IconButton(
                onClick = {
                    accountViewModel.toastManager.toast(R.string.messages_cant_upload_title, R.string.messages_cant_upload_explainer)
                },
            ) {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = stringRes(id = R.string.record_a_video),
                    modifier = Modifier.height(22.dp),
                    tint = MaterialTheme.colorScheme.placeholderText,
                )
            }
        }

        ForwardZapToButton(postViewModel.wantsForwardZapTo) {
            postViewModel.wantsForwardZapTo = !postViewModel.wantsForwardZapTo
        }

        if (postViewModel.canAddZapRaiser) {
            AddZapraiserButton(postViewModel.wantsZapraiser) {
                postViewModel.wantsZapraiser = !postViewModel.wantsZapraiser
            }
        }

        MarkAsSensitiveButton(postViewModel.wantsToMarkAsSensitive) {
            postViewModel.toggleMarkAsSensitive()
        }

        AddGeoHashButton(postViewModel.wantsToAddGeoHash) {
            postViewModel.wantsToAddGeoHash = !postViewModel.wantsToAddGeoHash
        }

        AddSecretEmojiButton(postViewModel.wantsSecretEmoji) {
            postViewModel.wantsSecretEmoji = !postViewModel.wantsSecretEmoji
        }

        if (postViewModel.canAddInvoice && postViewModel.hasLnAddress()) {
            AddLnInvoiceButton(postViewModel.wantsInvoice) {
                postViewModel.wantsInvoice = !postViewModel.wantsInvoice
            }
        }
    }
}

@Composable
fun SendDirectMessageTo(
    postViewModel: ChatNewMessageViewModel,
    accountViewModel: AccountViewModel,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        launch {
            delay(200)
            focusRequester.requestFocus()
        }
    }

    Column(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringRes(R.string.messages_new_message_to),
                fontSize = Font14SP,
                fontWeight = FontWeight.W500,
            )

            ThinPaddingTextField(
                value = postViewModel.toUsers,
                onValueChange = postViewModel::updateToUsers,
                modifier =
                    Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .onFocusChanged {
                            if (it.isFocused) {
                                keyboardController?.show()
                            }
                        },
                placeholder = {
                    Text(
                        text = stringRes(R.string.messages_new_message_to_caption),
                        color = MaterialTheme.colorScheme.placeholderText,
                    )
                },
                visualTransformation =
                    UrlUserTagTransformation(
                        MaterialTheme.colorScheme.primary,
                    ),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = Color.Transparent,
                    ),
            )

            ToggleNip17Button(postViewModel, accountViewModel)
        }

        HorizontalDivider(thickness = DividerThickness)

        Row(
            verticalAlignment = CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringRes(R.string.messages_new_message_subject),
                fontSize = Font14SP,
                fontWeight = FontWeight.W500,
            )

            ThinPaddingTextField(
                value = postViewModel.subject,
                onValueChange = { postViewModel.updateSubject(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = stringRes(R.string.messages_new_message_subject_caption),
                        color = MaterialTheme.colorScheme.placeholderText,
                    )
                },
                visualTransformation =
                    UrlUserTagTransformation(
                        MaterialTheme.colorScheme.primary,
                    ),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = Color.Transparent,
                    ),
            )
        }

        HorizontalDivider(thickness = DividerThickness)
    }
}

@Composable
fun ShowImageUploadGallery(
    data: SuccessfulUploads,
    accountViewModel: AccountViewModel,
) {
    val isImage = data.result.mimeTypeBeforeEncryption?.startsWith("image/") == true || RichTextParser.isImageUrl(data.result.url)

    if (data.cipher != null) {
        Amethyst.instance.keyCache.add(data.result.url, data.cipher, data.result.mimeTypeBeforeEncryption)
    }

    val content by remember(data) {
        mutableStateOf<BaseMediaContent>(
            if (data.cipher != null) {
                if (isImage) {
                    EncryptedMediaUrlImage(
                        url = data.result.url,
                        description = data.caption,
                        hash = data.result.hashBeforeEncryption,
                        blurhash =
                            data.result.fileHeader.blurHash
                                ?.blurhash,
                        dim = data.result.fileHeader.dim,
                        uri = null,
                        mimeType = data.result.mimeTypeBeforeEncryption,
                        encryptionAlgo = data.cipher.name(),
                        encryptionKey = data.cipher.keyBytes,
                        encryptionNonce = data.cipher.nonce,
                    )
                } else {
                    EncryptedMediaUrlVideo(
                        url = data.result.url,
                        description = data.caption,
                        hash = data.result.hashBeforeEncryption,
                        blurhash =
                            data.result.fileHeader.blurHash
                                ?.blurhash,
                        dim = data.result.fileHeader.dim,
                        uri = null,
                        mimeType = data.result.mimeTypeBeforeEncryption,
                        encryptionAlgo = data.cipher.name(),
                        encryptionKey = data.cipher.keyBytes,
                        encryptionNonce = data.cipher.nonce,
                    )
                }
            } else {
                if (isImage) {
                    MediaUrlImage(
                        url = data.result.url,
                        description = data.caption,
                        hash = data.result.fileHeader.hash,
                        blurhash =
                            data.result.fileHeader.blurHash
                                ?.blurhash,
                        dim = data.result.fileHeader.dim,
                        uri = null,
                        mimeType = data.result.mimeTypeBeforeEncryption,
                    )
                } else {
                    MediaUrlVideo(
                        url = data.result.url,
                        description = data.caption,
                        hash = data.result.fileHeader.hash,
                        blurhash =
                            data.result.fileHeader.blurHash
                                ?.blurhash,
                        dim = data.result.fileHeader.dim,
                        uri = null,
                        mimeType = data.result.mimeTypeBeforeEncryption,
                    )
                }
            },
        )
    }

    ZoomableContentView(
        content,
        persistentListOf(content),
        roundedCorner = false,
        contentScale = ContentScale.Crop,
        accountViewModel,
    )
}
