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

import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.NostrSearchEventOrUserDataSource
import com.vitorpamplona.amethyst.ui.components.BechLink
import com.vitorpamplona.amethyst.ui.components.InvoiceRequest
import com.vitorpamplona.amethyst.ui.components.LoadUrlPreview
import com.vitorpamplona.amethyst.ui.components.VideoView
import com.vitorpamplona.amethyst.ui.note.NoteCompose
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.UserLine
import com.vitorpamplona.amethyst.ui.theme.BitcoinOrange
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.QuoteBorder
import com.vitorpamplona.amethyst.ui.theme.Size10dp
import com.vitorpamplona.amethyst.ui.theme.Size5dp
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.amethyst.ui.theme.replyModifier
import com.vitorpamplona.amethyst.ui.theme.subtleBorder
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPostView(
    onClose: () -> Unit,
    edit: Note,
    versionLookingAt: Note?,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
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

        launch(Dispatchers.IO) {
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
                                postViewModel.editedFromNote?.let {
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

                                val user = postViewModel.account?.userProfile()
                                val lud16 = user?.info?.lnAddress()

                                if (lud16 != null && postViewModel.wantsInvoice) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(vertical = Size5dp, horizontal = Size10dp),
                                    ) {
                                        Column(Modifier.fillMaxWidth()) {
                                            InvoiceRequest(
                                                lud16,
                                                user.pubkeyHex,
                                                accountViewModel.account,
                                                stringResource(id = R.string.lightning_invoice),
                                                stringResource(id = R.string.lightning_create_and_add_invoice),
                                                onSuccess = {
                                                    postViewModel.message =
                                                        TextFieldValue(postViewModel.message.text + "\n\n" + it)
                                                    postViewModel.wantsInvoice = false
                                                },
                                                onClose = { postViewModel.wantsInvoice = false },
                                                onError = { title, message -> accountViewModel.toast(title, message) },
                                            )
                                        }
                                    }
                                }

                                /*
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = Size5dp, horizontal = Size10dp),
                                ) {
                                    Column {
                                        Text(
                                            text = stringResource(R.string.message_to_author),
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
                                                    text = stringResource(R.string.message_to_author_placeholder),
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

                        ShowUserSuggestionListForEdit(
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
fun ShowUserSuggestionListForEdit(
    editPostViewModel: EditPostViewModel,
    accountViewModel: AccountViewModel,
    modifier: Modifier = Modifier.heightIn(0.dp, 200.dp),
) {
    val userSuggestions = editPostViewModel.userSuggestions
    if (userSuggestions.isNotEmpty()) {
        LazyColumn(
            contentPadding =
                PaddingValues(
                    top = 10.dp,
                ),
            modifier = modifier,
        ) {
            itemsIndexed(
                userSuggestions,
                key = { _, item -> item.pubkeyHex },
            ) { _, item ->
                UserLine(item, accountViewModel) { editPostViewModel.autocompleteWithUser(item) }
                HorizontalDivider(
                    thickness = DividerThickness,
                )
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
        UploadFromGallery(
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
                )
                .focusRequester(focusRequester)
                .onFocusChanged {
                    if (it.isFocused) {
                        keyboardController?.show()
                    }
                },
        placeholder = {
            Text(
                text = stringResource(R.string.what_s_on_your_mind),
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
