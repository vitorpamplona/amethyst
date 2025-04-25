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

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.playback.composable.VideoView
import com.vitorpamplona.amethyst.ui.actions.mediaServers.ServerType
import com.vitorpamplona.amethyst.ui.actions.uploads.SelectFromGallery
import com.vitorpamplona.amethyst.ui.components.BechLink
import com.vitorpamplona.amethyst.ui.components.LoadUrlPreview
import com.vitorpamplona.amethyst.ui.navigation.INav
import com.vitorpamplona.amethyst.ui.note.NoteCompose
import com.vitorpamplona.amethyst.ui.note.creators.invoice.InvoiceRequest
import com.vitorpamplona.amethyst.ui.note.creators.uploads.ImageVideoDescription
import com.vitorpamplona.amethyst.ui.note.creators.userSuggestions.ShowUserSuggestionList
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.CloseButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.PostButton
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.BitcoinOrange
import com.vitorpamplona.amethyst.ui.theme.QuoteBorder
import com.vitorpamplona.amethyst.ui.theme.Size10dp
import com.vitorpamplona.amethyst.ui.theme.Size5dp
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.amethyst.ui.theme.replyModifier
import com.vitorpamplona.amethyst.ui.theme.subtleBorder
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPostView(
    onClose: () -> Unit,
    edit: Note,
    versionLookingAt: Note?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val postViewModel: EditPostViewModel = viewModel()
    postViewModel.prepare(edit, versionLookingAt, accountViewModel)

    val context = LocalContext.current

    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    var showRelaysDialog by remember { mutableStateOf(false) }
    var relayList = remember { accountViewModel.account.activeWriteRelays().toImmutableList() }

    LaunchedEffect(Unit) {
        postViewModel.load(edit, versionLookingAt, accountViewModel)
    }

    Dialog(
        onDismissRequest = { onClose() },
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
                                    postViewModel.cancel()
                                    scope.launch {
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
                        ).fillMaxSize(),
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
                                postViewModel.editedFromNote?.let {
                                    Row(Modifier.heightIn(max = 200.dp)) {
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

                                MessageField(postViewModel)

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

                                postViewModel.multiOrchestrator?.let {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(vertical = Size5dp, horizontal = Size10dp),
                                    ) {
                                        ImageVideoDescription(
                                            it,
                                            accountViewModel.account.settings.defaultFileServer,
                                            onAdd = { alt, server, sensitiveContent, mediaQuality ->
                                                postViewModel.upload(alt, sensitiveContent, mediaQuality, false, server, accountViewModel.toastManager::toast, context)
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

                                val user = postViewModel.account?.userProfile()
                                val lud16 = user?.info?.lnAddress()

                                if (lud16 != null && postViewModel.wantsInvoice) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(vertical = Size5dp, horizontal = Size10dp),
                                    ) {
                                        InvoiceRequest(
                                            lud16,
                                            user.pubkeyHex,
                                            accountViewModel,
                                            stringRes(id = R.string.lightning_invoice),
                                            stringRes(id = R.string.lightning_create_and_add_invoice),
                                            onSuccess = {
                                                postViewModel.message =
                                                    TextFieldValue(postViewModel.message.text + "\n\n" + it)
                                                postViewModel.wantsInvoice = false
                                            },
                                            onError = { title, message -> accountViewModel.toastManager.toast(title, message) },
                                        )
                                    }
                                }

                                /*
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = Size5dp, horizontal = Size10dp),
                                ) {
                                    Column {
                                        Text(
                                            text = stringRes(R.string.message_to_author),
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.W500,
                                        )

                                        HorizontalDivider(thickness = DividerThickness)

                                        MyTextField(
                                            value = postViewModel.subject,
                                            onValueChange = { postViewModel.updateSubject(it) },
                                            modifier = Modifier.fillMaxWidth(),
                                            placeholder = {
                                                Text(
                                                    text = stringRes(R.string.message_to_author_placeholder),
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
                                }*/
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

                        BottomRowActions(postViewModel)
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomRowActions(postViewModel: EditPostViewModel) {
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

        if (postViewModel.canAddInvoice) {
            AddLnInvoiceButton(postViewModel.wantsInvoice) {
                postViewModel.wantsInvoice = !postViewModel.wantsInvoice
            }
        }
    }
}

@Composable
private fun MessageField(postViewModel: EditPostViewModel) {
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
                ).focusRequester(focusRequester)
                .onFocusChanged {
                    if (it.isFocused) {
                        keyboardController?.show()
                    }
                },
        placeholder = {
            Text(
                text = stringRes(R.string.what_s_on_your_mind),
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
