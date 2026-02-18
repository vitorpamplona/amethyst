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
package com.vitorpamplona.amethyst.ui.note.nip22Comments

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.actions.uploads.SelectFromGallery
import com.vitorpamplona.amethyst.ui.actions.uploads.SelectedMedia
import com.vitorpamplona.amethyst.ui.actions.uploads.TakePictureButton
import com.vitorpamplona.amethyst.ui.actions.uploads.TakeVideoButton
import com.vitorpamplona.amethyst.ui.navigation.navs.Nav
import com.vitorpamplona.amethyst.ui.navigation.topbars.PostingTopBar
import com.vitorpamplona.amethyst.ui.note.BaseUserPicture
import com.vitorpamplona.amethyst.ui.note.NoteCompose
import com.vitorpamplona.amethyst.ui.note.creators.contentWarning.ContentSensitivityExplainer
import com.vitorpamplona.amethyst.ui.note.creators.contentWarning.MarkAsSensitiveButton
import com.vitorpamplona.amethyst.ui.note.creators.emojiSuggestions.ShowEmojiSuggestionList
import com.vitorpamplona.amethyst.ui.note.creators.emojiSuggestions.WatchAndLoadMyEmojiList
import com.vitorpamplona.amethyst.ui.note.creators.invoice.AddLnInvoiceButton
import com.vitorpamplona.amethyst.ui.note.creators.invoice.InvoiceRequest
import com.vitorpamplona.amethyst.ui.note.creators.location.AddGeoHashButton
import com.vitorpamplona.amethyst.ui.note.creators.location.LocationAsHash
import com.vitorpamplona.amethyst.ui.note.creators.messagefield.MessageField
import com.vitorpamplona.amethyst.ui.note.creators.notify.Notifying
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
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Size10dp
import com.vitorpamplona.amethyst.ui.theme.Size35dp
import com.vitorpamplona.amethyst.ui.theme.Size5dp
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.ui.theme.replyModifier
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ReplyCommentPostScreen(
    reply: Note? = null,
    message: String? = null,
    attachment: Uri? = null,
    quote: Note? = null,
    draft: Note? = null,
    accountViewModel: AccountViewModel,
    nav: Nav,
) {
    val postViewModel: CommentPostViewModel = viewModel()
    postViewModel.init(accountViewModel)

    val context = LocalContext.current

    LaunchedEffect(postViewModel, accountViewModel) {
        reply?.let {
            postViewModel.reply(it)
        }
        draft?.let {
            postViewModel.editFromDraft(it)
        }
        quote?.let {
            postViewModel.quote(it)
        }
        message?.ifBlank { null }?.let {
            postViewModel.updateMessage(TextFieldValue(it))
        }
        attachment?.let {
            withContext(Dispatchers.IO) {
                val mediaType = context.contentResolver.getType(it)
                postViewModel.selectImage(persistentListOf(SelectedMedia(it, mediaType)))
            }
        }
    }

    GenericCommentPostScreen(postViewModel, accountViewModel, nav)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenericCommentPostScreen(
    postViewModel: CommentPostViewModel,
    accountViewModel: AccountViewModel,
    nav: Nav,
) {
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
            GenericCommentPostBody(
                postViewModel,
                accountViewModel,
                nav,
            )
        }
    }
}

@Composable
private fun GenericCommentPostBody(
    postViewModel: CommentPostViewModel,
    accountViewModel: AccountViewModel,
    nav: Nav,
) {
    val scrollState = rememberScrollState()

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = Size10dp,
                        end = Size10dp,
                    ).weight(1f),
        ) {
            Column(Modifier.fillMaxWidth().verticalScroll(scrollState, reverseScrolling = true)) {
                postViewModel.externalIdentity?.let {
                    Row {
                        DisplayExternalId(it, accountViewModel, nav)
                        Spacer(modifier = StdVertSpacer)
                    }
                }

                postViewModel.replyingTo?.let {
                    Row {
                        NoteCompose(
                            baseNote = it,
                            modifier = MaterialTheme.colorScheme.replyModifier,
                            isQuotedNote = true,
                            unPackReply = false,
                            makeItShort = true,
                            quotesLeft = 1,
                            accountViewModel = accountViewModel,
                            nav = nav,
                        )
                        Spacer(modifier = StdVertSpacer)
                    }
                }

                Row {
                    Notifying(postViewModel.notifying?.toImmutableList(), accountViewModel) {
                        postViewModel.removeFromReplyList(it)
                    }
                }

                Row(
                    modifier = Modifier.padding(vertical = Size10dp),
                ) {
                    BaseUserPicture(
                        accountViewModel.userProfile(),
                        Size35dp,
                        accountViewModel = accountViewModel,
                    )
                    MessageField(
                        R.string.what_s_on_your_mind,
                        postViewModel,
                    )
                }

                DisplayPreviews(postViewModel.urlPreviews, accountViewModel, nav)

                if (postViewModel.wantsToMarkAsSensitive) {
                    Row(
                        verticalAlignment = CenterVertically,
                        modifier = Modifier.padding(vertical = Size5dp, horizontal = Size10dp),
                    ) {
                        ContentSensitivityExplainer()
                    }
                }

                if (postViewModel.wantsToAddGeoHash) {
                    Row(
                        verticalAlignment = CenterVertically,
                        modifier = Modifier.padding(vertical = Size5dp, horizontal = Size10dp),
                    ) {
                        LocationAsHash(postViewModel)
                    }
                }

                if (postViewModel.wantsForwardZapTo) {
                    Row(
                        verticalAlignment = CenterVertically,
                        modifier = Modifier.padding(top = Size5dp, bottom = Size5dp, start = Size10dp),
                    ) {
                        ForwardZapTo(postViewModel, accountViewModel)
                    }
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
                    postViewModel.lnAddress()?.let { lud16 ->
                        InvoiceRequest(
                            lud16,
                            accountViewModel.account.userProfile(),
                            accountViewModel,
                            stringRes(id = R.string.lightning_invoice),
                            stringRes(id = R.string.lightning_create_and_add_invoice),
                            onNewInvoice = {
                                postViewModel.insertAtCursor(it)
                                postViewModel.wantsInvoice = false
                            },
                            onError = { title, message -> accountViewModel.toastManager.toast(title, message) },
                        )
                    }
                }

                if (postViewModel.wantsSecretEmoji) {
                    Row(
                        verticalAlignment = CenterVertically,
                        modifier = Modifier.padding(vertical = Size5dp, horizontal = Size10dp),
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            SecretEmojiRequest {
                                postViewModel.insertAtCursor(it)
                                postViewModel.wantsSecretEmoji = false
                            }
                        }
                    }
                }

                if (postViewModel.wantsZapraiser && postViewModel.hasLnAddress()) {
                    Row(
                        verticalAlignment = CenterVertically,
                        modifier = Modifier.padding(vertical = Size5dp, horizontal = Size10dp),
                    ) {
                        ZapRaiserRequest(
                            stringRes(id = R.string.zapraiser),
                            postViewModel,
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
                modifier = Modifier.heightIn(0.dp, 300.dp),
            )
        }

        postViewModel.emojiSuggestions?.let {
            ShowEmojiSuggestionList(
                it,
                postViewModel::autocompleteWithEmoji,
                postViewModel::autocompleteWithEmojiUrl,
                modifier = Modifier.heightIn(0.dp, 300.dp),
            )
        }

        BottomRowActions(postViewModel)
    }
}

@Composable
private fun BottomRowActions(postViewModel: CommentPostViewModel) {
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
