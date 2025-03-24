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

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Parcelable
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Assistant
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.util.Consumer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.LocationState
import com.vitorpamplona.amethyst.service.NostrSearchEventOrUserDataSource
import com.vitorpamplona.amethyst.service.playback.composable.VideoView
import com.vitorpamplona.amethyst.service.uploads.MultiOrchestrator
import com.vitorpamplona.amethyst.ui.actions.NewPollOption
import com.vitorpamplona.amethyst.ui.actions.NewPollVoteValueRange
import com.vitorpamplona.amethyst.ui.actions.NewPostViewModel
import com.vitorpamplona.amethyst.ui.actions.RelaySelectionDialog
import com.vitorpamplona.amethyst.ui.actions.UrlUserTagTransformation
import com.vitorpamplona.amethyst.ui.actions.mediaServers.ServerName
import com.vitorpamplona.amethyst.ui.actions.mediaServers.ServerType
import com.vitorpamplona.amethyst.ui.actions.uploads.SelectFromGallery
import com.vitorpamplona.amethyst.ui.actions.uploads.SelectedMedia
import com.vitorpamplona.amethyst.ui.actions.uploads.SelectedMediaProcessing
import com.vitorpamplona.amethyst.ui.actions.uploads.ShowImageUploadGallery
import com.vitorpamplona.amethyst.ui.actions.uploads.TakePictureButton
import com.vitorpamplona.amethyst.ui.components.BechLink
import com.vitorpamplona.amethyst.ui.components.CreateTextWithEmoji
import com.vitorpamplona.amethyst.ui.components.InvoiceRequest
import com.vitorpamplona.amethyst.ui.components.LoadUrlPreview
import com.vitorpamplona.amethyst.ui.components.LoadingAnimation
import com.vitorpamplona.amethyst.ui.components.SecretEmojiRequest
import com.vitorpamplona.amethyst.ui.components.ThinPaddingTextField
import com.vitorpamplona.amethyst.ui.components.ZapRaiserRequest
import com.vitorpamplona.amethyst.ui.components.getActivity
import com.vitorpamplona.amethyst.ui.navigation.Nav
import com.vitorpamplona.amethyst.ui.note.BaseUserPicture
import com.vitorpamplona.amethyst.ui.note.CancelIcon
import com.vitorpamplona.amethyst.ui.note.CloseIcon
import com.vitorpamplona.amethyst.ui.note.LoadCityName
import com.vitorpamplona.amethyst.ui.note.NoteCompose
import com.vitorpamplona.amethyst.ui.note.PollIcon
import com.vitorpamplona.amethyst.ui.note.RegularPostIcon
import com.vitorpamplona.amethyst.ui.note.ShowEmojiSuggestionList
import com.vitorpamplona.amethyst.ui.note.ShowUserSuggestionList
import com.vitorpamplona.amethyst.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.ui.note.WatchAndLoadMyEmojiList
import com.vitorpamplona.amethyst.ui.note.ZapSplitIcon
import com.vitorpamplona.amethyst.ui.screen.loggedIn.settings.SettingsRow
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.BitcoinOrange
import com.vitorpamplona.amethyst.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.DoubleHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.Font14SP
import com.vitorpamplona.amethyst.ui.theme.QuoteBorder
import com.vitorpamplona.amethyst.ui.theme.Size10dp
import com.vitorpamplona.amethyst.ui.theme.Size18Modifier
import com.vitorpamplona.amethyst.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.ui.theme.Size35dp
import com.vitorpamplona.amethyst.ui.theme.Size55dp
import com.vitorpamplona.amethyst.ui.theme.Size5dp
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.ui.theme.mediumImportanceLink
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.amethyst.ui.theme.replyModifier
import com.vitorpamplona.amethyst.ui.theme.subtleBorder
import com.vitorpamplona.quartz.nip99Classifieds.tags.ConditionTag
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
import java.lang.Math.round

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
    enableMessageInterface: Boolean = false,
    accountViewModel: AccountViewModel,
    nav: Nav,
) {
    val postViewModel: NewPostViewModel = viewModel()
    postViewModel.account = accountViewModel.account
    postViewModel.wantsDirectMessage = enableMessageInterface
    postViewModel.wantsToAddGeoHash = enableGeolocation

    val context = LocalContext.current
    val activity = context.getActivity()

    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    var showRelaysDialog by remember { mutableStateOf(false) }
    var relayList = remember { accountViewModel.account.activeWriteRelays().toImmutableList() }

    var showCamera by remember {
        mutableStateOf(true)
    }

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
            postViewModel.load(accountViewModel, baseReplyTo, quote, fork, version, draft)
            message?.ifBlank { null }?.let {
                postViewModel.updateMessage(TextFieldValue(it))
            }
            attachment?.let {
                val mediaType = context.contentResolver.getType(it)
                postViewModel.selectImage(persistentListOf(SelectedMedia(it, mediaType)))
            }
        }
    }

    DisposableEffect(Unit) {
        NostrSearchEventOrUserDataSource.start()

        onDispose {
            NostrSearchEventOrUserDataSource.clear()
            NostrSearchEventOrUserDataSource.stop()
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

                        if (postViewModel.wantsDirectMessage) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = Size5dp, horizontal = Size10dp),
                            ) {
                                SendDirectMessageTo(postViewModel = postViewModel)
                            }
                        }

                        if (postViewModel.wantsProduct) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = Size5dp, horizontal = Size10dp),
                            ) {
                                SellProduct(postViewModel = postViewModel)
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
                            MessageField(postViewModel)
                        }

                        if (postViewModel.wantsPoll) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = Size5dp, horizontal = Size10dp),
                            ) {
                                PollField(postViewModel)
                            }
                        }

                        val myUrlPreview = postViewModel.urlPreview
                        if (myUrlPreview != null) {
                            Row(modifier = Modifier.padding(vertical = Size5dp, horizontal = Size10dp)) {
                                if (RichTextParser.isValidURL(myUrlPreview)) {
                                    if (RichTextParser.isImageUrl(myUrlPreview)) {
                                        AsyncImage(
                                            model = myUrlPreview,
                                            contentDescription = myUrlPreview,
                                            contentScale = ContentScale.FillWidth,
                                            modifier =
                                                Modifier
                                                    .padding(top = 4.dp)
                                                    .fillMaxWidth()
                                                    .clip(shape = QuoteBorder)
                                                    .border(
                                                        1.dp,
                                                        MaterialTheme.colorScheme.subtleBorder,
                                                        QuoteBorder,
                                                    ),
                                        )
                                    } else if (RichTextParser.isVideoUrl(myUrlPreview)) {
                                        VideoView(
                                            myUrlPreview,
                                            mimeType = null,
                                            roundedCorner = true,
                                            contentScale = ContentScale.FillWidth,
                                            accountViewModel = accountViewModel,
                                        )
                                    } else {
                                        LoadUrlPreview(myUrlPreview, myUrlPreview, null, accountViewModel)
                                    }
                                } else if (RichTextParser.startsWithNIP19Scheme(myUrlPreview)) {
                                    val bgColor = MaterialTheme.colorScheme.background
                                    val backgroundColor = remember { mutableStateOf(bgColor) }

                                    BechLink(
                                        word = myUrlPreview,
                                        canPreview = true,
                                        quotesLeft = 1,
                                        backgroundColor = backgroundColor,
                                        accountViewModel = accountViewModel,
                                        nav = nav,
                                    )
                                } else if (RichTextParser.isUrlWithoutScheme(myUrlPreview)) {
                                    LoadUrlPreview("https://$myUrlPreview", myUrlPreview, null, accountViewModel)
                                }
                            }
                        }

                        if (postViewModel.wantsToMarkAsSensitive) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = Size5dp, horizontal = Size10dp),
                            ) {
                                ContentSensitivityExplainer(postViewModel)
                            }
                        }

                        if (postViewModel.wantsToAddGeoHash) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = Size5dp, horizontal = Size10dp),
                            ) {
                                LocationAsHash(postViewModel)
                            }
                        }

                        if (postViewModel.wantsForwardZapTo) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = Size5dp, bottom = Size5dp, start = Size10dp),
                            ) {
                                FowardZapTo(postViewModel, accountViewModel)
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
                                    onError = { scope.launch { Toast.makeText(context, context.resources.getText(it), Toast.LENGTH_SHORT).show() } },
                                    accountViewModel = accountViewModel,
                                )
                            }
                        }

                        if (postViewModel.wantsInvoice) {
                            postViewModel.lnAddress()?.let { lud16 ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(vertical = Size5dp, horizontal = Size10dp),
                                ) {
                                    Column(Modifier.fillMaxWidth()) {
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
                                            onClose = { postViewModel.wantsInvoice = false },
                                            onError = { title, message -> accountViewModel.toastManager.toast(title, message) },
                                        )
                                    }
                                }
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

                ShowUserSuggestionList(
                    postViewModel.userSuggestions,
                    postViewModel::autocompleteWithUser,
                    accountViewModel,
                    modifier = Modifier.heightIn(0.dp, 300.dp),
                )

                ShowEmojiSuggestionList(
                    postViewModel.emojiSuggestions,
                    postViewModel::autocompleteWithEmoji,
                    postViewModel::autocompleteWithEmojiUrl,
                    accountViewModel,
                    modifier = Modifier.heightIn(0.dp, 300.dp),
                )

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
                if (postViewModel.wantsPoll) {
                    postViewModel.wantsProduct = false
                }
            }
        }

        AddClassifiedsButton(postViewModel) {
            postViewModel.wantsProduct = !postViewModel.wantsProduct
            if (postViewModel.wantsProduct) {
                postViewModel.wantsPoll = false
            }
        }

        ForwardZapTo(postViewModel) {
            postViewModel.wantsForwardZapTo = !postViewModel.wantsForwardZapTo
        }

        if (postViewModel.canAddZapRaiser) {
            AddZapraiserButton(postViewModel.wantsZapraiser) {
                postViewModel.wantsZapraiser = !postViewModel.wantsZapraiser
            }
        }

        MarkAsSensitive(postViewModel) {
            postViewModel.toggleMarkAsSensitive()
        }

        AddGeoHash(postViewModel) {
            postViewModel.wantsToAddGeoHash = !postViewModel.wantsToAddGeoHash
        }

        AddSecretEmoji(postViewModel.wantsSecretEmoji) {
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
private fun PollField(postViewModel: NewPostViewModel) {
    val optionsList = postViewModel.pollOptions
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        optionsList.forEach { value ->
            NewPollOption(postViewModel, value.key)
        }

        NewPollVoteValueRange(postViewModel)

        Button(
            onClick = {
                // postViewModel.pollOptions[postViewModel.pollOptions.size] = ""
                optionsList[optionsList.size] = ""
            },
            border =
                BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.placeholderText,
                ),
            colors =
                ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.placeholderText,
                ),
        ) {
            Image(
                painterResource(id = android.R.drawable.ic_input_add),
                contentDescription = "Add poll option button",
                modifier = Size18Modifier,
            )
        }
    }
}

@Composable
private fun MessageField(postViewModel: NewPostViewModel) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        launch {
            delay(200)
            focusRequester.requestFocus()
        }
    }

    ThinPaddingTextField(
        value = postViewModel.message,
        onValueChange = { postViewModel.updateMessage(it) },
        keyboardOptions =
            KeyboardOptions.Default.copy(
                capitalization = KeyboardCapitalization.Sentences,
            ),
        modifier =
            Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged {
                    if (it.isFocused) {
                        keyboardController?.show()
                    }
                },
        placeholder = {
            Text(
                text =
                    if (postViewModel.wantsProduct) {
                        stringRes(R.string.description)
                    } else {
                        stringRes(R.string.what_s_on_your_mind)
                    },
                color = MaterialTheme.colorScheme.placeholderText,
            )
        },
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
            ),
        visualTransformation = UrlUserTagTransformation(MaterialTheme.colorScheme.primary),
        textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
        contentPadding =
            TextFieldDefaults.contentPaddingWithoutLabel(
                start = 10.dp,
                top = 5.dp,
                end = 10.dp,
                bottom = 5.dp,
            ),
    )
}

@Composable
fun ContentSensitivityExplainer(postViewModel: NewPostViewModel) {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
        ) {
            Box(
                Modifier
                    .height(20.dp)
                    .width(25.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.VisibilityOff,
                    contentDescription = stringRes(id = R.string.content_warning),
                    modifier =
                        Modifier
                            .size(18.dp)
                            .align(Alignment.BottomStart),
                    tint = Color.Red,
                )
                Icon(
                    imageVector = Icons.Rounded.Warning,
                    contentDescription = stringRes(id = R.string.content_warning),
                    modifier =
                        Modifier
                            .size(10.dp)
                            .align(Alignment.TopEnd),
                    tint = Color.Yellow,
                )
            }

            Text(
                text = stringRes(R.string.add_sensitive_content_label),
                fontSize = 20.sp,
                fontWeight = FontWeight.W500,
                modifier = Modifier.padding(start = 10.dp),
            )
        }

        HorizontalDivider(thickness = DividerThickness)

        Text(
            text = stringRes(R.string.add_sensitive_content_explainer),
            color = MaterialTheme.colorScheme.placeholderText,
            modifier = Modifier.padding(vertical = 10.dp),
        )
    }
}

@Composable
fun SendDirectMessageTo(postViewModel: NewPostViewModel) {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringRes(R.string.messages_new_message_to),
                fontSize = Font14SP,
                fontWeight = FontWeight.W500,
            )

            ThinPaddingTextField(
                value = postViewModel.toUsers,
                onValueChange = { postViewModel.updateToUsers(it) },
                modifier = Modifier.fillMaxWidth(),
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

        Row(
            verticalAlignment = Alignment.CenterVertically,
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
fun SellProduct(postViewModel: NewPostViewModel) {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringRes(R.string.classifieds_title),
                fontSize = Font14SP,
                fontWeight = FontWeight.W500,
            )

            ThinPaddingTextField(
                value = postViewModel.title,
                onValueChange = {
                    postViewModel.updateTitle(it)
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = stringRes(R.string.classifieds_title_placeholder),
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

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringRes(R.string.classifieds_price),
                fontSize = Font14SP,
                fontWeight = FontWeight.W500,
            )

            ThinPaddingTextField(
                modifier = Modifier.fillMaxWidth(),
                value = postViewModel.price,
                onValueChange = {
                    postViewModel.updatePrice(it)
                },
                placeholder = {
                    Text(
                        text = "1000",
                        color = MaterialTheme.colorScheme.placeholderText,
                    )
                },
                keyboardOptions =
                    KeyboardOptions.Default.copy(
                        keyboardType = KeyboardType.Number,
                    ),
                singleLine = true,
                colors =
                    OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = Color.Transparent,
                    ),
            )
        }

        HorizontalDivider(thickness = DividerThickness)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringRes(R.string.classifieds_condition),
                fontSize = Font14SP,
                fontWeight = FontWeight.W500,
            )

            val conditionTypes =
                listOf(
                    Triple(
                        ConditionTag.CONDITION.NEW,
                        stringRes(id = R.string.classifieds_condition_new),
                        stringRes(id = R.string.classifieds_condition_new_explainer),
                    ),
                    Triple(
                        ConditionTag.CONDITION.USED_LIKE_NEW,
                        stringRes(id = R.string.classifieds_condition_like_new),
                        stringRes(id = R.string.classifieds_condition_like_new_explainer),
                    ),
                    Triple(
                        ConditionTag.CONDITION.USED_GOOD,
                        stringRes(id = R.string.classifieds_condition_good),
                        stringRes(id = R.string.classifieds_condition_good_explainer),
                    ),
                    Triple(
                        ConditionTag.CONDITION.USED_FAIR,
                        stringRes(id = R.string.classifieds_condition_fair),
                        stringRes(id = R.string.classifieds_condition_fair_explainer),
                    ),
                )

            val conditionOptions =
                remember {
                    conditionTypes.map { TitleExplainer(it.second, it.third) }.toImmutableList()
                }

            TextSpinner(
                placeholder = conditionTypes.filter { it.first == postViewModel.condition }.first().second,
                options = conditionOptions,
                onSelect = {
                    postViewModel.updateCondition(conditionTypes[it].first)
                },
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(end = 5.dp, bottom = 1.dp),
            ) { currentOption, modifier ->
                ThinPaddingTextField(
                    value = TextFieldValue(currentOption),
                    onValueChange = {},
                    readOnly = true,
                    modifier = modifier,
                    singleLine = true,
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = Color.Transparent,
                        ),
                )
            }
        }

        HorizontalDivider(thickness = DividerThickness)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringRes(R.string.classifieds_category),
                fontSize = Font14SP,
                fontWeight = FontWeight.W500,
            )

            val categoryList =
                listOf(
                    R.string.classifieds_category_clothing,
                    R.string.classifieds_category_accessories,
                    R.string.classifieds_category_electronics,
                    R.string.classifieds_category_furniture,
                    R.string.classifieds_category_collectibles,
                    R.string.classifieds_category_books,
                    R.string.classifieds_category_pets,
                    R.string.classifieds_category_sports,
                    R.string.classifieds_category_fitness,
                    R.string.classifieds_category_art,
                    R.string.classifieds_category_crafts,
                    R.string.classifieds_category_home,
                    R.string.classifieds_category_office,
                    R.string.classifieds_category_food,
                    R.string.classifieds_category_misc,
                    R.string.classifieds_category_other,
                )

            val categoryTypes = categoryList.map { Triple(it, stringRes(id = it), null) }

            val categoryOptions =
                remember {
                    categoryTypes.map { TitleExplainer(it.second, null) }.toImmutableList()
                }
            TextSpinner(
                placeholder =
                    categoryTypes.filter { it.second == postViewModel.category.text }.firstOrNull()?.second
                        ?: "",
                options = categoryOptions,
                onSelect = {
                    postViewModel.updateCategory(TextFieldValue(categoryTypes[it].second))
                },
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(end = 5.dp, bottom = 1.dp),
            ) { currentOption, modifier ->
                ThinPaddingTextField(
                    value = TextFieldValue(currentOption),
                    onValueChange = {},
                    readOnly = true,
                    modifier = modifier,
                    singleLine = true,
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = Color.Transparent,
                        ),
                )
            }
        }

        HorizontalDivider(thickness = DividerThickness)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringRes(R.string.classifieds_location),
                fontSize = Font14SP,
                fontWeight = FontWeight.W500,
            )

            ThinPaddingTextField(
                value = postViewModel.locationText,
                onValueChange = {
                    postViewModel.updateLocation(it)
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = stringRes(R.string.classifieds_location_placeholder),
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
fun FowardZapTo(
    postViewModel: NewPostViewModel,
    accountViewModel: AccountViewModel,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
        ) {
            ZapSplitIcon()

            Text(
                text = stringRes(R.string.zap_split_title),
                fontSize = 20.sp,
                fontWeight = FontWeight.W500,
                modifier = Modifier.padding(horizontal = 10.dp).weight(1f),
            )

            OutlinedButton(onClick = { postViewModel.updateZapFromText() }) {
                Text(text = stringRes(R.string.load_from_text))
            }
        }

        HorizontalDivider(thickness = DividerThickness)

        Text(
            text = stringRes(R.string.zap_split_explainer),
            color = MaterialTheme.colorScheme.placeholderText,
            modifier = Modifier.padding(vertical = 10.dp),
        )

        postViewModel.forwardZapTo.items.forEachIndexed { index, splitItem ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = Size10dp),
            ) {
                BaseUserPicture(splitItem.key, Size55dp, accountViewModel = accountViewModel)

                Spacer(modifier = DoubleHorzSpacer)

                Column(modifier = Modifier.weight(1f)) {
                    UsernameDisplay(splitItem.key, accountViewModel = accountViewModel)
                    Text(
                        text = String.format("%.0f%%", splitItem.percentage * 100),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                    )
                }

                Spacer(modifier = DoubleHorzSpacer)

                Slider(
                    value = splitItem.percentage,
                    onValueChange = { sliderValue ->
                        val rounded = (round(sliderValue * 100)) / 100.0f
                        postViewModel.updateZapPercentage(index, rounded)
                    },
                    modifier = Modifier.weight(1.5f),
                )
            }
        }

        OutlinedTextField(
            value = postViewModel.forwardZapToEditting,
            onValueChange = { postViewModel.updateZapForwardTo(it) },
            label = { Text(text = stringRes(R.string.zap_split_search_and_add_user)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = stringRes(R.string.zap_split_search_and_add_user_placeholder),
                    color = MaterialTheme.colorScheme.placeholderText,
                )
            },
            singleLine = true,
            visualTransformation =
                UrlUserTagTransformation(
                    MaterialTheme.colorScheme.primary,
                ),
            textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
        )
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun LocationAsHash(postViewModel: NewPostViewModel) {
    val locationPermissionState =
        rememberPermissionState(
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )

    LaunchedEffect(locationPermissionState.status.isGranted) {
        Amethyst.instance.locationManager.setLocationPermission(locationPermissionState.status.isGranted)
    }

    if (locationPermissionState.status.isGranted) {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
            ) {
                Box(
                    Modifier
                        .height(20.dp)
                        .width(20.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }

                Text(
                    text = stringRes(R.string.geohash_title),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.W500,
                    modifier = Modifier.padding(start = 10.dp),
                )

                DisplayLocationObserver(postViewModel)
            }

            HorizontalDivider(thickness = DividerThickness)

            Text(
                text = stringRes(R.string.geohash_explainer),
                color = MaterialTheme.colorScheme.placeholderText,
                modifier = Modifier.padding(vertical = 10.dp),
            )

            SettingsRow(
                R.string.geohash_exclusive,
                R.string.geohash_exclusive_explainer,
            ) {
                Switch(postViewModel.wantsExclusiveGeoPost, onCheckedChange = { postViewModel.wantsExclusiveGeoPost = it })
            }
        }
    } else {
        LaunchedEffect(locationPermissionState) { locationPermissionState.launchPermissionRequest() }
    }
}

@Composable
fun DisplayLocationObserver(postViewModel: NewPostViewModel) {
    val location by postViewModel.locationFlow().collectAsStateWithLifecycle()

    when (val myLocation = location) {
        is LocationState.LocationResult.Success -> {
            DisplayLocationInTitle(geohash = myLocation.geoHash.toString())
        }

        LocationState.LocationResult.LackPermission -> {
            Text(
                text = stringRes(R.string.lack_location_permissions),
                fontSize = 12.sp,
                lineHeight = 12.sp,
            )
        }
        LocationState.LocationResult.Loading -> {
            Text(
                text = stringRes(R.string.loading_location),
                fontSize = 12.sp,
                lineHeight = 12.sp,
            )
        }
    }
}

@Composable
fun DisplayLocationInTitle(geohash: String) {
    LoadCityName(
        geohashStr = geohash,
        onLoading = {
            Spacer(modifier = StdHorzSpacer)
            LoadingAnimation()
        },
    ) { cityName ->
        Text(
            text = cityName,
            fontSize = 20.sp,
            fontWeight = FontWeight.W500,
            modifier = Modifier.padding(start = Size5dp),
        )
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
                val innerUserState by user.live().metadata.observeAsState()
                innerUserState?.user?.let { myUser ->
                    val tags = myUser.info?.tags

                    Button(
                        shape = ButtonBorder,
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.mediumImportanceLink,
                            ),
                        onClick = { onClick(myUser) },
                    ) {
                        CreateTextWithEmoji(
                            text = remember(innerUserState) { "✖ ${myUser.toBestDisplayName()}" },
                            tags = tags,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
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

@Composable
private fun AddZapraiserButton(
    isLnInvoiceActive: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = { onClick() },
    ) {
        Box(
            Modifier
                .height(20.dp)
                .width(25.dp),
        ) {
            if (!isLnInvoiceActive) {
                Icon(
                    imageVector = Icons.Default.ShowChart,
                    null,
                    modifier =
                        Modifier
                            .size(20.dp)
                            .align(Alignment.TopStart),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = stringRes(R.string.add_zapraiser),
                    modifier =
                        Modifier
                            .size(13.dp)
                            .align(Alignment.BottomEnd),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            } else {
                Icon(
                    imageVector = Icons.Default.ShowChart,
                    null,
                    modifier =
                        Modifier
                            .size(20.dp)
                            .align(Alignment.TopStart),
                    tint = BitcoinOrange,
                )
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = stringRes(R.string.cancel_zapraiser),
                    modifier =
                        Modifier
                            .size(13.dp)
                            .align(Alignment.BottomEnd),
                    tint = BitcoinOrange,
                )
            }
        }
    }
}

@Composable
fun AddGeoHash(
    postViewModel: NewPostViewModel,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = { onClick() },
    ) {
        if (!postViewModel.wantsToAddGeoHash) {
            Icon(
                imageVector = Icons.Default.LocationOff,
                contentDescription = stringRes(id = R.string.add_location),
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onBackground,
            )
        } else {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = stringRes(id = R.string.remove_location),
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun AddLnInvoiceButton(
    isLnInvoiceActive: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = { onClick() },
    ) {
        if (!isLnInvoiceActive) {
            Icon(
                imageVector = Icons.Default.CurrencyBitcoin,
                contentDescription = stringRes(id = R.string.add_bitcoin_invoice),
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onBackground,
            )
        } else {
            Icon(
                imageVector = Icons.Default.CurrencyBitcoin,
                contentDescription = stringRes(id = R.string.cancel_bitcoin_invoice),
                modifier = Modifier.size(20.dp),
                tint = BitcoinOrange,
            )
        }
    }
}

@Composable
private fun AddSecretEmoji(
    isSecretEmojiActive: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = { onClick() },
    ) {
        if (!isSecretEmojiActive) {
            Icon(
                imageVector = Icons.Outlined.Assistant,
                contentDescription = stringRes(id = R.string.secret_emoji_maker_explainer),
                modifier = Size20Modifier,
                tint = MaterialTheme.colorScheme.onBackground,
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.Assistant,
                contentDescription = stringRes(id = R.string.secret_emoji_maker_explainer),
                modifier = Size20Modifier,
                tint = BitcoinOrange,
            )
        }
    }
}

@Composable
private fun ForwardZapTo(
    postViewModel: NewPostViewModel,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = { onClick() },
    ) {
        if (!postViewModel.wantsForwardZapTo) {
            ZapSplitIcon(tint = MaterialTheme.colorScheme.onBackground)
        } else {
            ZapSplitIcon(tint = BitcoinOrange)
        }
    }
}

@Composable
private fun AddClassifiedsButton(
    postViewModel: NewPostViewModel,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = { onClick() },
    ) {
        if (!postViewModel.wantsProduct) {
            Icon(
                imageVector = Icons.Default.Sell,
                contentDescription = stringRes(R.string.classifieds),
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onBackground,
            )
        } else {
            Icon(
                imageVector = Icons.Default.Sell,
                contentDescription = stringRes(id = R.string.cancel_classifieds),
                modifier = Modifier.size(20.dp),
                tint = BitcoinOrange,
            )
        }
    }
}

@Composable
private fun MarkAsSensitive(
    postViewModel: NewPostViewModel,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = { onClick() },
    ) {
        Box(
            Modifier
                .height(20.dp)
                .width(23.dp),
        ) {
            if (!postViewModel.wantsToMarkAsSensitive) {
                Icon(
                    imageVector = Icons.Default.Visibility,
                    contentDescription = stringRes(R.string.add_content_warning),
                    modifier =
                        Modifier
                            .size(18.dp)
                            .align(Alignment.BottomStart),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
                Icon(
                    imageVector = Icons.Rounded.Warning,
                    contentDescription = stringRes(R.string.add_content_warning),
                    modifier =
                        Modifier
                            .size(10.dp)
                            .align(Alignment.TopEnd),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            } else {
                Icon(
                    imageVector = Icons.Default.VisibilityOff,
                    contentDescription = stringRes(id = R.string.remove_content_warning),
                    modifier =
                        Modifier
                            .size(18.dp)
                            .align(Alignment.BottomStart),
                    tint = Color.Red,
                )
                Icon(
                    imageVector = Icons.Rounded.Warning,
                    contentDescription = stringRes(id = R.string.remove_content_warning),
                    modifier =
                        Modifier
                            .size(10.dp)
                            .align(Alignment.TopEnd),
                    tint = Color.Yellow,
                )
            }
        }
    }
}

@Composable
fun CloseButton(onPress: () -> Unit) {
    OutlinedButton(
        onClick = onPress,
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
        modifier = modifier,
        onClick = {
            if (isActive) {
                onPost()
            }
        },
        shape = ButtonBorder,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = if (isActive) MaterialTheme.colorScheme.primary else Color.Gray,
            ),
    ) {
        Text(text = stringRes(R.string.create), color = Color.White)
    }
}

@Composable
fun ImageVideoDescription(
    uris: MultiOrchestrator,
    defaultServer: ServerName,
    onAdd: (String, ServerName, Boolean, Int) -> Unit,
    onDelete: (SelectedMediaProcessing) -> Unit,
    onCancel: () -> Unit,
    onError: (Int) -> Unit,
    accountViewModel: AccountViewModel,
) {
    val nip95description = stringRes(id = R.string.upload_server_relays_nip95)

    val fileServers by accountViewModel.account.liveServerList.collectAsState()

    val fileServerOptions =
        remember(fileServers) {
            fileServers
                .map {
                    if (it.type == ServerType.NIP95) {
                        TitleExplainer(it.name, nip95description)
                    } else {
                        TitleExplainer(it.name, it.baseUrl)
                    }
                }.toImmutableList()
        }

    var selectedServer by remember {
        mutableStateOf(
            fileServers.firstOrNull { it == defaultServer } ?: fileServers[0],
        )
    }
    var message by remember { mutableStateOf("") }
    var sensitiveContent by remember { mutableStateOf(false) }

    // 0 = Low, 1 = Medium, 2 = High, 3=UNCOMPRESSED
    var mediaQualitySlider by remember { mutableIntStateOf(1) }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 30.dp, end = 30.dp)
                .clip(shape = QuoteBorder)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.subtleBorder,
                    QuoteBorder,
                ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(30.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
            ) {
                val text =
                    if (uris.size() == 1) {
                        if (uris.first().media.isImage() == true) {
                            R.string.content_description_add_image
                        } else {
                            if (uris.first().media.isVideo() == true) {
                                R.string.content_description_add_video
                            } else {
                                R.string.content_description_add_document
                            }
                        }
                    } else {
                        R.string.content_description_add_media
                    }

                Text(
                    text = stringRes(text),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.W500,
                    modifier =
                        Modifier
                            .padding(start = 10.dp)
                            .weight(1.0f)
                            .windowInsetsPadding(WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)),
                )

                IconButton(
                    modifier =
                        Modifier
                            .size(30.dp)
                            .padding(end = 5.dp),
                    onClick = onCancel,
                ) {
                    CancelIcon()
                }
            }

            HorizontalDivider(thickness = DividerThickness)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .windowInsetsPadding(WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)),
            ) {
                ShowImageUploadGallery(uris, onDelete, accountViewModel)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextSpinner(
                    label = stringRes(id = R.string.file_server),
                    placeholder =
                        fileServers
                            .firstOrNull { it == defaultServer }
                            ?.name
                            ?: fileServers[0].name,
                    options = fileServerOptions,
                    onSelect = { selectedServer = fileServers[it] },
                    modifier =
                        Modifier
                            .windowInsetsPadding(WindowInsets(0.dp, 0.dp, 0.dp, 0.dp))
                            .weight(1f),
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                SettingSwitchItem(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                    checked = sensitiveContent,
                    onCheckedChange = { sensitiveContent = it },
                    title = R.string.add_sensitive_content_label,
                    description = R.string.add_sensitive_content_description,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)),
            ) {
                OutlinedTextField(
                    label = { Text(text = stringRes(R.string.content_description)) },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)),
                    value = message,
                    onValueChange = { message = it },
                    placeholder = {
                        Text(
                            text = stringRes(R.string.content_description_example),
                            color = MaterialTheme.colorScheme.placeholderText,
                        )
                    },
                    keyboardOptions =
                        KeyboardOptions.Default.copy(
                            capitalization = KeyboardCapitalization.Sentences,
                        ),
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets(0.dp, 0.dp, 0.dp, 0.dp))
                        .padding(vertical = 8.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1.0f),
                    verticalArrangement = Arrangement.spacedBy(Size5dp),
                ) {
                    Text(
                        text = stringRes(R.string.media_compression_quality_label),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringRes(R.string.media_compression_quality_explainer),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text =
                                when (mediaQualitySlider) {
                                    0 -> stringRes(R.string.media_compression_quality_low)
                                    1 -> stringRes(R.string.media_compression_quality_medium)
                                    2 -> stringRes(R.string.media_compression_quality_high)
                                    3 -> stringRes(R.string.media_compression_quality_uncompressed)
                                    else -> stringRes(R.string.media_compression_quality_medium)
                                },
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }

                    Slider(
                        value = mediaQualitySlider.toFloat(),
                        onValueChange = { mediaQualitySlider = it.toInt() },
                        valueRange = 0f..3f,
                        steps = 2,
                    )
                }
            }

            Button(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                onClick = { onAdd(message, selectedServer, sensitiveContent, mediaQualitySlider) },
                shape = QuoteBorder,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
            ) {
                Text(text = stringRes(R.string.add_content), color = Color.White, fontSize = 20.sp)
            }
        }
    }
}

@Composable
fun SettingSwitchItem(
    modifier: Modifier =
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    title: Int,
    description: Int,
    enabled: Boolean = true,
) {
    Row(
        modifier =
            modifier
                .toggleable(
                    value = checked,
                    enabled = enabled,
                    role = Role.Switch,
                    onValueChange = onCheckedChange,
                ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Column(
            modifier = Modifier.weight(2.0f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = stringRes(id = title),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringRes(id = description),
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
            )
        }
    }
}
