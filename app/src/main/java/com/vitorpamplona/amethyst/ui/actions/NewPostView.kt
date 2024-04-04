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
package com.vitorpamplona.amethyst.ui.actions

import android.Manifest
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Log
import android.util.Size
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.Nip96MediaServers
import com.vitorpamplona.amethyst.service.NostrSearchEventOrUserDataSource
import com.vitorpamplona.amethyst.ui.components.BechLink
import com.vitorpamplona.amethyst.ui.components.CreateTextWithEmoji
import com.vitorpamplona.amethyst.ui.components.InvoiceRequest
import com.vitorpamplona.amethyst.ui.components.LoadUrlPreview
import com.vitorpamplona.amethyst.ui.components.VideoView
import com.vitorpamplona.amethyst.ui.components.ZapRaiserRequest
import com.vitorpamplona.amethyst.ui.note.BaseUserPicture
import com.vitorpamplona.amethyst.ui.note.CancelIcon
import com.vitorpamplona.amethyst.ui.note.CloseIcon
import com.vitorpamplona.amethyst.ui.note.LoadCityName
import com.vitorpamplona.amethyst.ui.note.NoteCompose
import com.vitorpamplona.amethyst.ui.note.PollIcon
import com.vitorpamplona.amethyst.ui.note.RegularPostIcon
import com.vitorpamplona.amethyst.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.ui.note.ZapSplitIcon
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.MyTextField
import com.vitorpamplona.amethyst.ui.screen.loggedIn.ShowUserSuggestionList
import com.vitorpamplona.amethyst.ui.screen.loggedIn.TextSpinner
import com.vitorpamplona.amethyst.ui.screen.loggedIn.TitleExplainer
import com.vitorpamplona.amethyst.ui.theme.BitcoinOrange
import com.vitorpamplona.amethyst.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.DoubleHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.Font14SP
import com.vitorpamplona.amethyst.ui.theme.QuoteBorder
import com.vitorpamplona.amethyst.ui.theme.Size10dp
import com.vitorpamplona.amethyst.ui.theme.Size18Modifier
import com.vitorpamplona.amethyst.ui.theme.Size55dp
import com.vitorpamplona.amethyst.ui.theme.Size5dp
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.ui.theme.mediumImportanceLink
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.amethyst.ui.theme.replyModifier
import com.vitorpamplona.amethyst.ui.theme.subtleBorder
import com.vitorpamplona.quartz.events.ClassifiedsEvent
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.Math.round

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun NewPostView(
    onClose: () -> Unit,
    baseReplyTo: Note? = null,
    quote: Note? = null,
    fork: Note? = null,
    version: Note? = null,
    draft: Note? = null,
    enableMessageInterface: Boolean = false,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val postViewModel: NewPostViewModel = viewModel()
    postViewModel.wantsDirectMessage = enableMessageInterface

    val context = LocalContext.current

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
            postViewModel.load(accountViewModel, baseReplyTo, quote, fork, version, draft)

            postViewModel.imageUploadingError.collect { error ->
                withContext(Dispatchers.Main) { Toast.makeText(context, error, Toast.LENGTH_SHORT).show() }
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

    Dialog(
        onDismissRequest = {
            scope.launch {
                postViewModel.sendDraftSync(relayList = relayList)
                onClose()
            }
        },
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnClickOutside = false,
                decorFitsSystemWindows = false,
            ),
    ) {
        if (showRelaysDialog) {
            RelaySelectionDialog(
                preSelectedList = relayList,
                onClose = { showRelaysDialog = false },
                onPost = { relayList = it },
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }

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
                                        contentDescription = stringResource(id = R.string.relay_list_selector),
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
                                        onClose()
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
                                        postViewModel.sendDraftSync(relayList = relayList)
                                        postViewModel.cancel()
                                        delay(100)
                                        onClose()
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
            Surface(
                modifier =
                    Modifier
                        .padding(
                            start = Size10dp,
                            top = pad.calculateTopPadding(),
                            end = Size10dp,
                            bottom = pad.calculateBottomPadding(),
                        )
                        .fillMaxSize(),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(),
                ) {
                    Column(
                        modifier =
                            Modifier
                                .imePadding()
                                .weight(1f),
                    ) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                        ) {
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .verticalScroll(scrollState),
                            ) {
                                postViewModel.originalNote?.let {
                                    Row(Modifier.heightIn(max = 200.dp)) {
                                        NoteCompose(
                                            baseNote = it,
                                            makeItShort = true,
                                            unPackReply = false,
                                            isQuotedNote = true,
                                            quotesLeft = 1,
                                            modifier = MaterialTheme.colorScheme.replyModifier,
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

                                MessageField(postViewModel)

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
                                                    roundedCorner = true,
                                                    accountViewModel = accountViewModel,
                                                )
                                            } else {
                                                LoadUrlPreview(myUrlPreview, myUrlPreview, accountViewModel)
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
                                            LoadUrlPreview("https://$myUrlPreview", myUrlPreview, accountViewModel)
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

                                val url = postViewModel.contentToAddUrl
                                if (url != null) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(vertical = Size5dp, horizontal = Size10dp),
                                    ) {
                                        ImageVideoDescription(
                                            url,
                                            accountViewModel.account.defaultFileServer,
                                            onAdd = { alt, server, sensitiveContent ->
                                                postViewModel.upload(url, alt, sensitiveContent, false, server, context)
                                                if (!server.isNip95) {
                                                    accountViewModel.account.changeDefaultFileServer(server.server)
                                                }
                                            },
                                            onCancel = { postViewModel.contentToAddUrl = null },
                                            onError = { scope.launch { postViewModel.imageUploadingError.emit(it) } },
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
                                                    accountViewModel.account,
                                                    stringResource(id = R.string.lightning_invoice),
                                                    stringResource(id = R.string.lightning_create_and_add_invoice),
                                                    onSuccess = {
                                                        postViewModel.insertAtCursor(it)
                                                        postViewModel.wantsInvoice = false
                                                    },
                                                    onClose = { postViewModel.wantsInvoice = false },
                                                    onError = { title, message -> accountViewModel.toast(title, message) },
                                                )
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
                                            stringResource(id = R.string.zapraiser),
                                            postViewModel,
                                        )
                                    }
                                }
                            }
                        }

                        ShowUserSuggestionList(
                            postViewModel,
                            accountViewModel,
                            modifier = Modifier.heightIn(0.dp, 300.dp),
                        )

                        BottomRowActions(postViewModel)
                    }
                }
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
        UploadFromGallery(
            isUploading = postViewModel.isUploadingImage,
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier,
        ) {
            postViewModel.selectImage(it)
        }

        if (postViewModel.canUsePoll) {
            // These should be hashtag recommendations the user selects in the future.
            // val hashtag = stringResource(R.string.poll_hashtag)
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

        if (postViewModel.canAddInvoice && postViewModel.hasLnAddress()) {
            AddLnInvoiceButton(postViewModel.wantsInvoice) {
                postViewModel.wantsInvoice = !postViewModel.wantsInvoice
            }
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

        ForwardZapTo(postViewModel) {
            postViewModel.wantsForwardZapTo = !postViewModel.wantsForwardZapTo
        }
    }
}

@Composable
private fun PollField(postViewModel: NewPostViewModel) {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        postViewModel.pollOptions.values.forEachIndexed { index, _ ->
            NewPollOption(postViewModel, index)
        }

        NewPollVoteValueRange(postViewModel)

        Button(
            onClick = { postViewModel.pollOptions[postViewModel.pollOptions.size] = "" },
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

    OutlinedTextField(
        value = postViewModel.message,
        onValueChange = { postViewModel.updateMessage(it) },
        keyboardOptions =
            KeyboardOptions.Default.copy(
                capitalization = KeyboardCapitalization.Sentences,
            ),
        modifier =
            Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(8.dp),
                )
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
                        stringResource(R.string.description)
                    } else {
                        stringResource(R.string.what_s_on_your_mind)
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
                    contentDescription = stringResource(id = R.string.content_warning),
                    modifier =
                        Modifier
                            .size(18.dp)
                            .align(Alignment.BottomStart),
                    tint = Color.Red,
                )
                Icon(
                    imageVector = Icons.Rounded.Warning,
                    contentDescription = stringResource(id = R.string.content_warning),
                    modifier =
                        Modifier
                            .size(10.dp)
                            .align(Alignment.TopEnd),
                    tint = Color.Yellow,
                )
            }

            Text(
                text = stringResource(R.string.add_sensitive_content_label),
                fontSize = 20.sp,
                fontWeight = FontWeight.W500,
                modifier = Modifier.padding(start = 10.dp),
            )
        }

        HorizontalDivider(thickness = DividerThickness)

        Text(
            text = stringResource(R.string.add_sensitive_content_explainer),
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
                text = stringResource(R.string.messages_new_message_to),
                fontSize = Font14SP,
                fontWeight = FontWeight.W500,
            )

            MyTextField(
                value = postViewModel.toUsers,
                onValueChange = { postViewModel.updateToUsers(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = stringResource(R.string.messages_new_message_to_caption),
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
                text = stringResource(R.string.messages_new_message_subject),
                fontSize = Font14SP,
                fontWeight = FontWeight.W500,
            )

            MyTextField(
                value = postViewModel.subject,
                onValueChange = { postViewModel.updateSubject(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = stringResource(R.string.messages_new_message_subject_caption),
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
                text = stringResource(R.string.classifieds_title),
                fontSize = Font14SP,
                fontWeight = FontWeight.W500,
            )

            MyTextField(
                value = postViewModel.title,
                onValueChange = {
                    postViewModel.updateTitle(it)
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = stringResource(R.string.classifieds_title_placeholder),
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
                text = stringResource(R.string.classifieds_price),
                fontSize = Font14SP,
                fontWeight = FontWeight.W500,
            )

            MyTextField(
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
                text = stringResource(R.string.classifieds_condition),
                fontSize = Font14SP,
                fontWeight = FontWeight.W500,
            )

            val conditionTypes =
                listOf(
                    Triple(
                        ClassifiedsEvent.CONDITION.NEW,
                        stringResource(id = R.string.classifieds_condition_new),
                        stringResource(id = R.string.classifieds_condition_new_explainer),
                    ),
                    Triple(
                        ClassifiedsEvent.CONDITION.USED_LIKE_NEW,
                        stringResource(id = R.string.classifieds_condition_like_new),
                        stringResource(id = R.string.classifieds_condition_like_new_explainer),
                    ),
                    Triple(
                        ClassifiedsEvent.CONDITION.USED_GOOD,
                        stringResource(id = R.string.classifieds_condition_good),
                        stringResource(id = R.string.classifieds_condition_good_explainer),
                    ),
                    Triple(
                        ClassifiedsEvent.CONDITION.USED_FAIR,
                        stringResource(id = R.string.classifieds_condition_fair),
                        stringResource(id = R.string.classifieds_condition_fair_explainer),
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
                MyTextField(
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
                text = stringResource(R.string.classifieds_category),
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

            val categoryTypes = categoryList.map { Triple(it, stringResource(id = it), null) }

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
                MyTextField(
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
                text = stringResource(R.string.classifieds_location),
                fontSize = Font14SP,
                fontWeight = FontWeight.W500,
            )

            MyTextField(
                value = postViewModel.locationText,
                onValueChange = {
                    postViewModel.updateLocation(it)
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = stringResource(R.string.classifieds_location_placeholder),
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
                text = stringResource(R.string.zap_split_title),
                fontSize = 20.sp,
                fontWeight = FontWeight.W500,
                modifier = Modifier.padding(horizontal = 10.dp).weight(1f),
            )

            OutlinedButton(onClick = { postViewModel.updateZapFromText() }) {
                Text(text = stringResource(R.string.load_from_text))
            }
        }

        HorizontalDivider(thickness = DividerThickness)

        Text(
            text = stringResource(R.string.zap_split_explainer),
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
                    UsernameDisplay(splitItem.key)
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
            label = { Text(text = stringResource(R.string.zap_split_search_and_add_user)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = stringResource(R.string.zap_split_search_and_add_user_placeholder),
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
                    text = stringResource(R.string.geohash_title),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.W500,
                    modifier = Modifier.padding(start = 10.dp),
                )

                DisplayLocationObserver(postViewModel)
            }

            HorizontalDivider(thickness = DividerThickness)

            Text(
                text = stringResource(R.string.geohash_explainer),
                color = MaterialTheme.colorScheme.placeholderText,
                modifier = Modifier.padding(vertical = 10.dp),
            )
        }
    } else {
        LaunchedEffect(locationPermissionState) { locationPermissionState.launchPermissionRequest() }
    }
}

@Composable
fun DisplayLocationObserver(postViewModel: NewPostViewModel) {
    val context = LocalContext.current
    var locationDescriptionFlow by remember(postViewModel) { mutableStateOf<Flow<String>?>(null) }

    DisposableEffect(key1 = context) {
        postViewModel.startLocation(context = context)
        locationDescriptionFlow = postViewModel.location

        onDispose { postViewModel.stopLocation() }
    }

    locationDescriptionFlow?.let {
        val location by it.collectAsStateWithLifecycle(null)

        location?.let { DisplayLocationInTitle(geohash = it) }
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
                stringResource(R.string.reply_notify),
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
                    contentDescription = stringResource(R.string.add_zapraiser),
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
                    contentDescription = stringResource(R.string.cancel_zapraiser),
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
                contentDescription = stringResource(id = R.string.add_location),
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onBackground,
            )
        } else {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = stringResource(id = R.string.remove_location),
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
                contentDescription = stringResource(id = R.string.add_bitcoin_invoice),
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onBackground,
            )
        } else {
            Icon(
                imageVector = Icons.Default.CurrencyBitcoin,
                contentDescription = stringResource(id = R.string.cancel_bitcoin_invoice),
                modifier = Modifier.size(20.dp),
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
                contentDescription = stringResource(R.string.classifieds),
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onBackground,
            )
        } else {
            Icon(
                imageVector = Icons.Default.Sell,
                contentDescription = stringResource(id = R.string.cancel_classifieds),
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
                    contentDescription = stringResource(R.string.add_content_warning),
                    modifier =
                        Modifier
                            .size(18.dp)
                            .align(Alignment.BottomStart),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
                Icon(
                    imageVector = Icons.Rounded.Warning,
                    contentDescription = stringResource(R.string.add_content_warning),
                    modifier =
                        Modifier
                            .size(10.dp)
                            .align(Alignment.TopEnd),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            } else {
                Icon(
                    imageVector = Icons.Default.VisibilityOff,
                    contentDescription = stringResource(id = R.string.remove_content_warning),
                    modifier =
                        Modifier
                            .size(18.dp)
                            .align(Alignment.BottomStart),
                    tint = Color.Red,
                )
                Icon(
                    imageVector = Icons.Rounded.Warning,
                    contentDescription = stringResource(id = R.string.remove_content_warning),
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
        Text(text = stringResource(R.string.post))
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
        Text(text = stringResource(R.string.save))
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
        Text(text = stringResource(R.string.create), color = Color.White)
    }
}

@Composable
fun ImageVideoDescription(
    uri: Uri,
    defaultServer: Nip96MediaServers.ServerName,
    onAdd: (String, ServerOption, Boolean) -> Unit,
    onCancel: () -> Unit,
    onError: (String) -> Unit,
    accountViewModel: AccountViewModel,
) {
    val resolver = LocalContext.current.contentResolver
    val mediaType = resolver.getType(uri) ?: ""

    val isImage = mediaType.startsWith("image")
    val isVideo = mediaType.startsWith("video")

    val fileServers =
        Nip96MediaServers.DEFAULT.map { ServerOption(it, false) } +
            listOf(
                ServerOption(
                    Nip96MediaServers.ServerName(
                        "NIP95",
                        stringResource(id = R.string.upload_server_relays_nip95),
                    ),
                    true,
                ),
            )

    val fileServerOptions =
        remember {
            fileServers.map { TitleExplainer(it.server.name, it.server.baseUrl) }.toImmutableList()
        }

    var selectedServer by remember { mutableStateOf(ServerOption(defaultServer, false)) }
    var message by remember { mutableStateOf("") }
    var sensitiveContent by remember { mutableStateOf(false) }

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
                Text(
                    text =
                        stringResource(
                            if (isImage) {
                                R.string.content_description_add_image
                            } else {
                                if (isVideo) {
                                    R.string.content_description_add_video
                                } else {
                                    R.string.content_description_add_document
                                }
                            },
                        ),
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
                if (mediaType.startsWith("image")) {
                    AsyncImage(
                        model = uri.toString(),
                        contentDescription = uri.toString(),
                        contentScale = ContentScale.FillWidth,
                        modifier =
                            Modifier
                                .padding(top = 4.dp)
                                .fillMaxWidth()
                                .windowInsetsPadding(WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)),
                    )
                } else if (
                    mediaType.startsWith("video") && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                ) {
                    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

                    LaunchedEffect(key1 = uri) {
                        launch(Dispatchers.IO) {
                            try {
                                bitmap = resolver.loadThumbnail(uri, Size(1200, 1000), null)
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                onError("Unable to load thumbnail")
                                Log.w("NewPostView", "Couldn't create thumbnail, but the video can be uploaded", e)
                            }
                        }
                    }

                    bitmap?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = "some useful description",
                            contentScale = ContentScale.FillWidth,
                            modifier =
                                Modifier
                                    .padding(top = 4.dp)
                                    .fillMaxWidth(),
                        )
                    }
                } else {
                    VideoView(uri.toString(), roundedCorner = true, accountViewModel = accountViewModel)
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextSpinner(
                    label = stringResource(id = R.string.file_server),
                    placeholder =
                        fileServers
                            .firstOrNull { it.server == accountViewModel.account.defaultFileServer }
                            ?.server
                            ?.name
                            ?: fileServers[0].server.name,
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
                    label = { Text(text = stringResource(R.string.content_description)) },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)),
                    value = message,
                    onValueChange = { message = it },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.content_description_example),
                            color = MaterialTheme.colorScheme.placeholderText,
                        )
                    },
                    keyboardOptions =
                        KeyboardOptions.Default.copy(
                            capitalization = KeyboardCapitalization.Sentences,
                        ),
                )
            }

            Button(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                onClick = { onAdd(message, selectedServer, sensitiveContent) },
                shape = QuoteBorder,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
            ) {
                Text(text = stringResource(R.string.add_content), color = Color.White, fontSize = 20.sp)
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
    ) {
        Column(
            modifier = Modifier.weight(1.0f),
            verticalArrangement = Arrangement.spacedBy(Size5dp),
        ) {
            Text(
                text = stringResource(id = title),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(id = description),
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
        )
    }
}
