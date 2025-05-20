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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip99Classifieds

import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.actions.RelaySelectionDialogEasy
import com.vitorpamplona.amethyst.ui.actions.mediaServers.ServerType
import com.vitorpamplona.amethyst.ui.actions.uploads.SelectFromGallery
import com.vitorpamplona.amethyst.ui.actions.uploads.SelectedMedia
import com.vitorpamplona.amethyst.ui.actions.uploads.TakePictureButton
import com.vitorpamplona.amethyst.ui.navigation.INav
import com.vitorpamplona.amethyst.ui.navigation.Nav
import com.vitorpamplona.amethyst.ui.note.BaseUserPicture
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
import com.vitorpamplona.amethyst.ui.note.creators.previews.DisplayPreviews
import com.vitorpamplona.amethyst.ui.note.creators.secretEmoji.AddSecretEmojiButton
import com.vitorpamplona.amethyst.ui.note.creators.secretEmoji.SecretEmojiRequest
import com.vitorpamplona.amethyst.ui.note.creators.uploads.ImageVideoDescription
import com.vitorpamplona.amethyst.ui.note.creators.userSuggestions.ShowUserSuggestionList
import com.vitorpamplona.amethyst.ui.note.creators.zapraiser.AddZapraiserButton
import com.vitorpamplona.amethyst.ui.note.creators.zapraiser.ZapRaiserRequest
import com.vitorpamplona.amethyst.ui.note.creators.zapsplits.ForwardZapTo
import com.vitorpamplona.amethyst.ui.note.creators.zapsplits.ForwardZapToButton
import com.vitorpamplona.amethyst.ui.painterRes
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Size10dp
import com.vitorpamplona.amethyst.ui.theme.Size35dp
import com.vitorpamplona.amethyst.ui.theme.Size5dp
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun NewProductScreen(
    message: String? = null,
    attachment: Uri? = null,
    quote: Note? = null,
    draft: Note? = null,
    accountViewModel: AccountViewModel,
    nav: Nav,
) {
    val postViewModel: NewProductViewModel = viewModel()
    postViewModel.init(accountViewModel)

    val context = LocalContext.current

    LaunchedEffect(Unit) {
        postViewModel.reloadRelaySet()
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

    NewProductScreen(
        postViewModel,
        accountViewModel,
        nav,
    )
}

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun NewProductScreen(
    postViewModel: NewProductViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    WatchAndLoadMyEmojiList(accountViewModel)

    val scope = rememberCoroutineScope()
    var showRelaysDialog by remember { mutableStateOf(false) }

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
                                onClick = { showRelaysDialog = true },
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
                                postViewModel.sendPost()
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
                                        postViewModel.sendDraftSync()
                                        postViewModel.cancel()
                                    }
                                }
                                nav.popBack()
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
            RelaySelectionDialogEasy(
                preSelectedList = postViewModel.relayList ?: persistentListOf(),
                onClose = { showRelaysDialog = false },
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
            NewProductBody(
                postViewModel,
                accountViewModel,
                nav,
            )
        }
    }
}

@Composable
private fun NewProductBody(
    postViewModel: NewProductViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val scrollState = rememberScrollState()

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Size10dp)
                .weight(1f)
                .verticalScroll(scrollState),
        ) {
            Row(
                verticalAlignment = CenterVertically,
                modifier = Modifier.padding(vertical = Size5dp, horizontal = Size10dp),
            ) {
                SellProduct(postViewModel = postViewModel)
            }

            Row(
                modifier = Modifier.padding(vertical = Size10dp),
            ) {
                BaseUserPicture(
                    accountViewModel.userProfile(),
                    Size35dp,
                    accountViewModel = accountViewModel,
                )
                MessageField(R.string.description, postViewModel)
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
                        uris = it,
                        defaultServer = accountViewModel.account.settings.defaultFileServer,
                        includeNIP95 = false,
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
private fun BottomRowActions(postViewModel: NewProductViewModel) {
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
