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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.notifications.publicMessages

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement.Absolute.spacedBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.actions.UrlUserTagTransformation
import com.vitorpamplona.amethyst.ui.actions.uploads.SelectFromGallery
import com.vitorpamplona.amethyst.ui.actions.uploads.TakePictureButton
import com.vitorpamplona.amethyst.ui.actions.uploads.TakeVideoButton
import com.vitorpamplona.amethyst.ui.components.ThinPaddingTextField
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.navs.Nav
import com.vitorpamplona.amethyst.ui.navigation.topbars.PostingTopBar
import com.vitorpamplona.amethyst.ui.note.NoteCompose
import com.vitorpamplona.amethyst.ui.note.creators.contentWarning.ContentSensitivityExplainer
import com.vitorpamplona.amethyst.ui.note.creators.contentWarning.MarkAsSensitiveButton
import com.vitorpamplona.amethyst.ui.note.creators.emojiSuggestions.ShowEmojiSuggestionList
import com.vitorpamplona.amethyst.ui.note.creators.emojiSuggestions.WatchAndLoadMyEmojiList
import com.vitorpamplona.amethyst.ui.note.creators.invoice.AddLnInvoiceButton
import com.vitorpamplona.amethyst.ui.note.creators.invoice.NewPostInvoiceRequest
import com.vitorpamplona.amethyst.ui.note.creators.location.AddGeoHashButton
import com.vitorpamplona.amethyst.ui.note.creators.location.LocationAsHash
import com.vitorpamplona.amethyst.ui.note.creators.previews.DisplayPreviews
import com.vitorpamplona.amethyst.ui.note.creators.secretEmoji.AddSecretEmojiButton
import com.vitorpamplona.amethyst.ui.note.creators.secretEmoji.SecretEmojiRequest
import com.vitorpamplona.amethyst.ui.note.creators.uploads.ImageVideoDescription
import com.vitorpamplona.amethyst.ui.note.creators.userSuggestions.ShowUserSuggestionList
import com.vitorpamplona.amethyst.ui.note.creators.zapraiser.AddZapraiserButton
import com.vitorpamplona.amethyst.ui.note.creators.zapraiser.ZapRaiserRequest
import com.vitorpamplona.amethyst.ui.note.creators.zapsplits.ForwardZapTo
import com.vitorpamplona.amethyst.ui.note.creators.zapsplits.ForwardZapToButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM.send.MessageFieldRow
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.Font14SP
import com.vitorpamplona.amethyst.ui.theme.Size10dp
import com.vitorpamplona.amethyst.ui.theme.Size5dp
import com.vitorpamplona.amethyst.ui.theme.imageModifier
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun NewPublicMessageScreen(
    to: Set<HexKey>? = null,
    reply: Note? = null,
    draft: Note? = null,
    accountViewModel: AccountViewModel,
    nav: Nav,
) {
    val postViewModel: NewPublicMessageViewModel = viewModel()
    postViewModel.init(accountViewModel)

    LaunchedEffect(postViewModel, accountViewModel) {
        withContext(Dispatchers.IO) {
            to?.let {
                postViewModel.load(it)
            }
            reply?.let {
                postViewModel.reply(it)
            }
            draft?.let {
                postViewModel.editFromDraft(it)
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
                titleRes = R.string.public_message,
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
                        nav.popBack()
                    }
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
            PublicMessageScreenContent(postViewModel, accountViewModel, nav)
        }
    }
}

@Composable
fun PublicMessageScreenContent(
    postViewModel: NewPublicMessageViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val scrollState = rememberScrollState()

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Size10dp).weight(1f)) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(scrollState, reverseScrolling = true),
                verticalArrangement = spacedBy(Size10dp),
            ) {
                val replyTo = postViewModel.replyingTo

                if (replyTo == null) {
                    SendDirectMessageTo(postViewModel, accountViewModel)
                } else {
                    Row {
                        NoteCompose(
                            baseNote = replyTo,
                            modifier = MaterialTheme.colorScheme.imageModifier,
                            isQuotedNote = true,
                            unPackReply = false,
                            makeItShort = true,
                            quotesLeft = 1,
                            accountViewModel = accountViewModel,
                            nav = nav,
                        )
                    }
                }

                MessageFieldRow(postViewModel, accountViewModel, postViewModel.toUsers.text.isNotBlank())

                DisplayPreviews(postViewModel.urlPreviews, accountViewModel, nav)

                if (postViewModel.wantsToMarkAsSensitive) {
                    ContentSensitivityExplainer()
                }

                if (postViewModel.wantsToAddGeoHash) {
                    LocationAsHash(postViewModel)
                }

                if (postViewModel.wantsForwardZapTo) {
                    ForwardZapTo(postViewModel, accountViewModel)
                }

                postViewModel.multiOrchestrator?.let {
                    Row(
                        verticalAlignment = CenterVertically,
                        modifier = Modifier.padding(vertical = Size5dp, horizontal = Size10dp),
                    ) {
                        val context = LocalContext.current
                        ImageVideoDescription(
                            it,
                            accountViewModel.account.settings.defaultFileServer,
                            onAdd = { alt, server, sensitiveContent, mediaQuality, _ ->
                                postViewModel.upload(alt, if (sensitiveContent) "" else null, mediaQuality, server, accountViewModel.toastManager::toast, context)
                                accountViewModel.account.settings.changeDefaultFileServer(server)
                            },
                            onDelete = postViewModel::deleteMediaToUpload,
                            onCancel = { postViewModel.multiOrchestrator = null },
                            accountViewModel = accountViewModel,
                        )
                    }
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
private fun BottomRowActions(
    postViewModel: NewPublicMessageViewModel,
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
        SelectFromGallery(
            isUploading = postViewModel.isUploadingImage,
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier,
        ) {
            postViewModel.selectImage(it)
        }

        TakePictureButton(
            onPictureTaken = {
                postViewModel.selectImage(it)
            },
        )

        TakeVideoButton(
            onVideoTaken = {
                postViewModel.selectImage(it)
            },
        )

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
    postViewModel: NewPublicMessageViewModel,
    accountViewModel: AccountViewModel,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        if (postViewModel.toUsers.text.isBlank()) {
            launch {
                delay(200)
                focusRequester.requestFocus()
            }
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
        }

        HorizontalDivider(thickness = DividerThickness)
    }
}
