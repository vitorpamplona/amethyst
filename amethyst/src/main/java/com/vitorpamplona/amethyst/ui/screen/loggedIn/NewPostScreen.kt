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
package com.vitorpamplona.amethyst.ui.screen.loggedIn

import android.content.Intent
import android.net.Uri
import android.os.Parcelable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.Absolute.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.util.Consumer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.observeUser
import com.vitorpamplona.amethyst.ui.actions.NewPostViewModel
import com.vitorpamplona.amethyst.ui.actions.RelaySelectionDialog
import com.vitorpamplona.amethyst.ui.actions.mediaServers.ServerType
import com.vitorpamplona.amethyst.ui.actions.uploads.SelectFromGallery
import com.vitorpamplona.amethyst.ui.actions.uploads.SelectedMedia
import com.vitorpamplona.amethyst.ui.actions.uploads.TakePictureButton
import com.vitorpamplona.amethyst.ui.components.CreateTextWithEmoji
import com.vitorpamplona.amethyst.ui.components.getActivity
import com.vitorpamplona.amethyst.ui.navigation.INav
import com.vitorpamplona.amethyst.ui.navigation.Nav
import com.vitorpamplona.amethyst.ui.note.BaseUserPicture
import com.vitorpamplona.amethyst.ui.note.CloseIcon
import com.vitorpamplona.amethyst.ui.note.NoteCompose
import com.vitorpamplona.amethyst.ui.note.PollIcon
import com.vitorpamplona.amethyst.ui.note.RegularPostIcon
import com.vitorpamplona.amethyst.ui.note.creators.contentWarning.ContentSensitivityExplainer
import com.vitorpamplona.amethyst.ui.note.creators.contentWarning.MarkAsSensitiveButton
import com.vitorpamplona.amethyst.ui.note.creators.emojiSuggestions.ShowEmojiSuggestionList
import com.vitorpamplona.amethyst.ui.note.creators.emojiSuggestions.WatchAndLoadMyEmojiList
import com.vitorpamplona.amethyst.ui.note.creators.invoice.AddLnInvoiceButton
import com.vitorpamplona.amethyst.ui.note.creators.invoice.InvoiceRequest
import com.vitorpamplona.amethyst.ui.note.creators.location.AddGeoHashButton
import com.vitorpamplona.amethyst.ui.note.creators.location.LocationAsHash
import com.vitorpamplona.amethyst.ui.note.creators.messagefield.MessageField
import com.vitorpamplona.amethyst.ui.note.creators.previews.PreviewUrl
import com.vitorpamplona.amethyst.ui.note.creators.previews.PreviewUrlFillWidth
import com.vitorpamplona.amethyst.ui.note.creators.secretEmoji.AddSecretEmojiButton
import com.vitorpamplona.amethyst.ui.note.creators.secretEmoji.SecretEmojiRequest
import com.vitorpamplona.amethyst.ui.note.creators.uploads.ImageVideoDescription
import com.vitorpamplona.amethyst.ui.note.creators.userSuggestions.ShowUserSuggestionList
import com.vitorpamplona.amethyst.ui.note.creators.zappolls.PollField
import com.vitorpamplona.amethyst.ui.note.creators.zapraiser.AddZapraiserButton
import com.vitorpamplona.amethyst.ui.note.creators.zapraiser.ZapRaiserRequest
import com.vitorpamplona.amethyst.ui.note.creators.zapsplits.ForwardZapTo
import com.vitorpamplona.amethyst.ui.note.creators.zapsplits.ForwardZapToButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.settings.SettingsRow
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.ui.theme.FillWidthQuoteBorderModifier
import com.vitorpamplona.amethyst.ui.theme.HalfHorzPadding
import com.vitorpamplona.amethyst.ui.theme.Height100Modifier
import com.vitorpamplona.amethyst.ui.theme.Size10dp
import com.vitorpamplona.amethyst.ui.theme.Size35dp
import com.vitorpamplona.amethyst.ui.theme.Size5dp
import com.vitorpamplona.amethyst.ui.theme.SquaredQuoteBorderModifier
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.ui.theme.mediumImportanceLink
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.amethyst.ui.theme.replyModifier
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun NewPostScreen(
    message: String? = null,
    attachment: Uri? = null,
    baseReplyTo: Note? = null,
    quote: Note? = null,
    fork: Note? = null,
    version: Note? = null,
    draft: Note? = null,
    enableGeolocation: Boolean = false,
    accountViewModel: AccountViewModel,
    nav: Nav,
) {
    val postViewModel: NewPostViewModel = viewModel()
    postViewModel.init(accountViewModel)
    postViewModel.wantsToAddGeoHash = enableGeolocation

    val context = LocalContext.current
    val activity = context.getActivity()

    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    var showRelaysDialog by remember { mutableStateOf(false) }
    var relayList = remember { accountViewModel.account.activeWriteRelays().toImmutableList() }

    LaunchedEffect(key1 = postViewModel.draftTag) {
        launch(Dispatchers.IO) {
            postViewModel.draftTextChanges
                .receiveAsFlow()
                .debounce(1000)
                .collectLatest {
                    postViewModel.sendDraft(relayList = relayList)
                }
        }
    }

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

    WatchAndLoadMyEmojiList(accountViewModel)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Spacer(modifier = StdHorzSpacer)

                        Box {
                            IconButton(
                                modifier = Modifier.align(Alignment.Center),
                                onClick = { showRelaysDialog = true },
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.relays),
                                    contentDescription = stringRes(id = R.string.relay_list_selector),
                                    modifier = Modifier.height(25.dp),
                                    tint = MaterialTheme.colorScheme.onBackground,
                                )
                            }
                        }
                        PostButton(
                            onPost = {
                                postViewModel.sendPost(relayList = relayList)
                                scope.launch {
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
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        postViewModel.sendDraftSync(relayList = relayList)
                                        postViewModel.cancel()
                                    }
                                    delay(100)
                                    nav.popBack()
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
        if (showRelaysDialog) {
            RelaySelectionDialog(
                preSelectedList = relayList,
                onClose = { showRelaysDialog = false },
                onPost = { relayList = it },
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
                            Notifying(postViewModel.pTags?.toImmutableList()) {
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
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = Size5dp, horizontal = Size10dp),
                            ) {
                                PollField(postViewModel)
                            }
                        }

                        DisplayPreviews(postViewModel, accountViewModel, nav)

                        if (postViewModel.wantsToMarkAsSensitive) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = Size5dp, horizontal = Size10dp),
                            ) {
                                ContentSensitivityExplainer()
                            }
                        }

                        if (postViewModel.wantsToAddGeoHash) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
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
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = Size5dp, bottom = Size5dp, start = Size10dp),
                            ) {
                                ForwardZapTo(postViewModel, accountViewModel)
                            }
                        }

                        postViewModel.multiOrchestrator?.let {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = Size5dp, horizontal = Size10dp),
                            ) {
                                ImageVideoDescription(
                                    it,
                                    accountViewModel.account.settings.defaultFileServer,
                                    onAdd = { alt, server, sensitiveContent, mediaQuality ->
                                        postViewModel.upload(alt, if (sensitiveContent) "" else null, mediaQuality, false, server, accountViewModel.toastManager::toast, context)
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
                                verticalAlignment = Alignment.CenterVertically,
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
                                verticalAlignment = Alignment.CenterVertically,
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
    }
}

@Composable
private fun BottomRowActions(postViewModel: NewPostViewModel) {
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
fun DisplayPreviews(
    postViewModel: NewPostViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val urlPreviews by postViewModel.urlPreviews.results.collectAsStateWithLifecycle(emptyList())

    if (urlPreviews.isNotEmpty()) {
        Row(HalfHorzPadding) {
            if (urlPreviews.size > 1) {
                LazyRow(Height100Modifier, horizontalArrangement = spacedBy(Size5dp)) {
                    items(urlPreviews) {
                        Box(SquaredQuoteBorderModifier) {
                            PreviewUrl(it, accountViewModel, nav)
                        }
                    }
                }
            } else {
                Box(FillWidthQuoteBorderModifier) {
                    PreviewUrlFillWidth(urlPreviews[0], accountViewModel, nav)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Notifying(
    baseMentions: ImmutableList<User>?,
    onClick: (User) -> Unit,
) {
    val mentions = baseMentions?.toSet()

    FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        if (!mentions.isNullOrEmpty()) {
            Text(
                stringRes(R.string.reply_notify),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.placeholderText,
                modifier = Modifier.align(CenterVertically),
            )

            mentions.forEachIndexed { idx, user ->
                Button(
                    shape = ButtonBorder,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.mediumImportanceLink,
                        ),
                    onClick = { onClick(user) },
                ) {
                    DisplayUserNameWithDeleteMark(user)
                }
            }
        }
    }
}

@Composable
private fun DisplayUserNameWithDeleteMark(user: User) {
    val innerUserState by observeUser(user)
    innerUserState?.user?.let { myUser ->
        CreateTextWithEmoji(
            text = remember(innerUserState) { "✖ ${myUser.toBestDisplayName()}" },
            tags = myUser.info?.tags,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
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

@Composable
fun CloseButton(
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onPress,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = Size5dp),
    ) {
        CloseIcon()
    }
}

@Composable
fun PostButton(
    onPost: () -> Unit = {},
    isActive: Boolean,
    modifier: Modifier = Modifier,
) {
    Button(
        modifier = modifier,
        enabled = isActive,
        onClick = onPost,
    ) {
        Text(text = stringRes(R.string.post))
    }
}

@Composable
fun SaveButton(
    onPost: () -> Unit = {},
    isActive: Boolean,
    modifier: Modifier = Modifier,
) {
    Button(
        enabled = isActive,
        modifier = modifier,
        onClick = onPost,
    ) {
        Text(text = stringRes(R.string.save))
    }
}

@Composable
fun CreateButton(
    onPost: () -> Unit = {},
    isActive: Boolean,
    modifier: Modifier = Modifier,
) {
    Button(
        enabled = isActive,
        modifier = modifier,
        onClick = onPost,
    ) {
        Text(text = stringRes(R.string.create))
    }
}

@Composable
fun JoinButton(
    onPost: () -> Unit = {},
    isActive: Boolean,
    modifier: Modifier = Modifier,
) {
    Button(
        enabled = isActive,
        modifier = modifier,
        onClick = onPost,
    ) {
        Text(text = stringRes(R.string.join))
    }
}
