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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.home

import android.content.Intent
import android.net.Uri
import android.os.Parcelable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.util.Consumer
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.actions.RelaySelectionDialogEasy
import com.vitorpamplona.amethyst.ui.actions.mediaServers.ServerType
import com.vitorpamplona.amethyst.ui.actions.uploads.SelectFromGallery
import com.vitorpamplona.amethyst.ui.actions.uploads.SelectedMedia
import com.vitorpamplona.amethyst.ui.actions.uploads.TakePictureButton
import com.vitorpamplona.amethyst.ui.components.getActivity
import com.vitorpamplona.amethyst.ui.navigation.Nav
import com.vitorpamplona.amethyst.ui.note.BaseUserPicture
import com.vitorpamplona.amethyst.ui.note.NoteCompose
import com.vitorpamplona.amethyst.ui.note.PollIcon
import com.vitorpamplona.amethyst.ui.note.RegularPostIcon
import com.vitorpamplona.amethyst.ui.note.buttons.CloseButton
import com.vitorpamplona.amethyst.ui.note.buttons.PostButton
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
import com.vitorpamplona.amethyst.ui.note.creators.zappolls.PollField
import com.vitorpamplona.amethyst.ui.note.creators.zapraiser.AddZapraiserButton
import com.vitorpamplona.amethyst.ui.note.creators.zapraiser.ZapRaiserRequest
import com.vitorpamplona.amethyst.ui.note.creators.zapsplits.ForwardZapTo
import com.vitorpamplona.amethyst.ui.note.creators.zapsplits.ForwardZapToButton
import com.vitorpamplona.amethyst.ui.painterRes
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.settings.SettingsRow
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Size10dp
import com.vitorpamplona.amethyst.ui.theme.Size35dp
import com.vitorpamplona.amethyst.ui.theme.Size5dp
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.ui.theme.replyModifier
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun ShortNotePostScreen(
    message: String? = null,
    attachment: Uri? = null,
    baseReplyTo: Note? = null,
    quote: Note? = null,
    fork: Note? = null,
    version: Note? = null,
    draft: Note? = null,
    accountViewModel: AccountViewModel,
    nav: Nav,
) {
    val postViewModel: ShortNotePostViewModel = viewModel()
    postViewModel.init(accountViewModel)

    val context = LocalContext.current
    val activity = context.getActivity()

    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            postViewModel.load(baseReplyTo, quote, fork, version, draft)
            message?.ifBlank { null }?.let {
                postViewModel.updateMessage(TextFieldValue(it))
            }
            attachment?.let {
                val mediaType = context.contentResolver.getType(it)
                postViewModel.selectImage(persistentListOf(SelectedMedia(it, mediaType)))
            }
        }
    }

    DisposableEffect(nav, activity) {
        // Microsoft's swift key sends Gifs as new actions
        val consumer =
            Consumer<Intent> { intent ->
                if (intent.action == Intent.ACTION_SEND) {
                    intent.getStringExtra(Intent.EXTRA_TEXT)?.ifBlank { null }?.let {
                        postViewModel.addToMessage(it)
                    }

                    (intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri)?.let {
                        val mediaType = context.contentResolver.getType(it)
                        postViewModel.selectImage(persistentListOf(SelectedMedia(it, mediaType)))
                    }
                }
            }

        activity.addOnNewIntentListener(consumer)
        onDispose { activity.removeOnNewIntentListener(consumer) }
    }

    NewPostScreenInner(postViewModel, accountViewModel, nav)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewPostScreenInner(
    postViewModel: ShortNotePostViewModel,
    accountViewModel: AccountViewModel,
    nav: Nav,
) {
    val scope = rememberCoroutineScope()

    WatchAndLoadMyEmojiList(accountViewModel)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = CenterVertically,
                    ) {
                        Spacer(modifier = StdHorzSpacer)

                        Box {
                            IconButton(
                                modifier = Modifier.align(Alignment.Center),
                                onClick = { postViewModel.showRelaysDialog = true },
                            ) {
                                Icon(
                                    painter = painterRes(R.drawable.relays),
                                    contentDescription = stringRes(id = R.string.relay_list_selector),
                                    modifier = Modifier.height(25.dp),
                                    tint = MaterialTheme.colorScheme.onBackground,
                                )
                            }
                        }
                        PostButton(
                            onPost = {
                                // uses the accountViewModel scope to avoid cancelling this
                                // function when the postViewModel is released
                                accountViewModel.viewModelScope.launch(Dispatchers.IO) {
                                    postViewModel.sendPostSync()
                                    delay(100)
                                    nav.popBack()
                                }
                            },
                            isActive = postViewModel.canPost(),
                        )
                    }
                },
                navigationIcon = {
                    Row {
                        Spacer(modifier = StdHorzSpacer)
                        CloseButton(
                            onPress = {
                                // uses the accountViewModel scope to avoid cancelling this
                                // function when the postViewModel is released
                                accountViewModel.viewModelScope.launch(Dispatchers.IO) {
                                    postViewModel.sendDraftSync()
                                    nav.popBack()
                                    postViewModel.cancel()
                                }
                            },
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
            )
        },
    ) { pad ->
        if (postViewModel.showRelaysDialog) {
            RelaySelectionDialogEasy(
                preSelectedList = postViewModel.relayList ?: persistentListOf(),
                onClose = { postViewModel.showRelaysDialog = false },
                onPost = { postViewModel.relayList = it.map { it.url }.toImmutableList() },
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
        Surface(
            modifier =
                Modifier
                    .padding(pad)
                    .consumeWindowInsets(pad)
                    .imePadding(),
        ) {
            NewPostScreenBody(postViewModel, accountViewModel, nav)
        }
    }
}

@Composable
private fun NewPostScreenBody(
    postViewModel: ShortNotePostViewModel,
    accountViewModel: AccountViewModel,
    nav: Nav,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier =
            Modifier.fillMaxSize(),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = Size10dp,
                        end = Size10dp,
                    ).weight(1f),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState),
            ) {
                postViewModel.originalNote?.let {
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
                    Notifying(postViewModel.pTags?.toImmutableList(), accountViewModel) {
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

                if (postViewModel.wantsPoll) {
                    Row(
                        verticalAlignment = CenterVertically,
                        modifier = Modifier.padding(vertical = Size5dp, horizontal = Size10dp),
                    ) {
                        PollField(postViewModel)
                    }
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
                        LocationAsHash(postViewModel) {
                            SettingsRow(
                                R.string.geohash_exclusive,
                                R.string.geohash_exclusive_explainer,
                            ) {
                                Switch(postViewModel.wantsExclusiveGeoPost, onCheckedChange = { postViewModel.wantsExclusiveGeoPost = it })
                            }
                        }
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
                            onAdd = { alt, server, sensitiveContent, mediaQuality ->
                                postViewModel.upload(alt, if (sensitiveContent) "" else null, mediaQuality, server, accountViewModel.toastManager::toast, context)
                                if (server.type != ServerType.NIP95) {
                                    accountViewModel.account.settings.changeDefaultFileServer(server)
                                }
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
                            accountViewModel.account.userProfile().pubkeyHex,
                            accountViewModel,
                            stringRes(id = R.string.lightning_invoice),
                            stringRes(id = R.string.lightning_create_and_add_invoice),
                            onSuccess = {
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
                accountViewModel,
                modifier = Modifier.heightIn(0.dp, 300.dp),
            )
        }

        BottomRowActions(postViewModel)
    }
}

@Composable
private fun BottomRowActions(postViewModel: ShortNotePostViewModel) {
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

        if (postViewModel.canUsePoll) {
            // These should be hashtag recommendations the user selects in the future.
            // val hashtag = stringRes(R.string.poll_hashtag)
            // postViewModel.includePollHashtagInMessage(postViewModel.wantsPoll, hashtag)
            AddPollButton(postViewModel.wantsPoll) {
                postViewModel.wantsPoll = !postViewModel.wantsPoll
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
private fun AddPollButton(
    isPollActive: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = { onClick() },
    ) {
        if (!isPollActive) {
            PollIcon()
        } else {
            RegularPostIcon()
        }
    }
}
