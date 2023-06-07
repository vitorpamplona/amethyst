package com.vitorpamplona.amethyst.ui.actions

import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Log
import android.util.Size
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.ArrowForwardIos
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.NostrSearchEventOrUserDataSource
import com.vitorpamplona.amethyst.ui.components.*
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.TextSpinner
import com.vitorpamplona.amethyst.ui.screen.loggedIn.UserLine
import com.vitorpamplona.amethyst.ui.theme.BitcoinOrange
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun NewPostView(onClose: () -> Unit, baseReplyTo: Note? = null, quote: Note? = null, accountViewModel: AccountViewModel, nav: (String) -> Unit) {
    val accountState by accountViewModel.accountLiveData.observeAsState()
    val account = remember(accountState) { accountState?.account } ?: return

    val postViewModel: NewPostViewModel = viewModel()

    val context = LocalContext.current

    // initialize focus reference to be able to request focus programmatically
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        postViewModel.load(account, baseReplyTo, quote)
        delay(100)
        focusRequester.requestFocus()

        launch(Dispatchers.IO) {
            postViewModel.imageUploadingError.collect { error ->
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            NostrSearchEventOrUserDataSource.clear()
        }
    }

    Dialog(
        onDismissRequest = { onClose() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
            ) {
                Column(
                    modifier = Modifier
                        .padding(start = 10.dp, end = 10.dp, top = 10.dp)
                        .imePadding()
                        .weight(1f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CloseButton(onCancel = {
                            postViewModel.cancel()
                            onClose()
                        })

                        PostButton(
                            onPost = {
                                scope.launch(Dispatchers.IO) {
                                    postViewModel.sendPost()
                                    onClose()
                                }
                            },
                            isActive = postViewModel.canPost()
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(scrollState)
                        ) {
                            Notifying(postViewModel.mentions?.toImmutableList()) {
                                postViewModel.removeFromReplyList(it)
                            }

                            OutlinedTextField(
                                value = postViewModel.message,
                                onValueChange = {
                                    postViewModel.updateMessage(it)
                                },
                                keyboardOptions = KeyboardOptions.Default.copy(
                                    capitalization = KeyboardCapitalization.Sentences
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        width = 1.dp,
                                        color = MaterialTheme.colors.surface,
                                        shape = RoundedCornerShape(8.dp)
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
                                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                                    )
                                },
                                colors = TextFieldDefaults
                                    .outlinedTextFieldColors(
                                        unfocusedBorderColor = Color.Transparent,
                                        focusedBorderColor = Color.Transparent
                                    ),
                                visualTransformation = UrlUserTagTransformation(MaterialTheme.colors.primary),
                                textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Content)
                            )

                            if (postViewModel.wantsPoll) {
                                postViewModel.pollOptions.values.forEachIndexed { index, _ ->
                                    NewPollOption(postViewModel, index)
                                }

                                Button(
                                    onClick = { postViewModel.pollOptions[postViewModel.pollOptions.size] = "" },
                                    border = BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.32f)),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                                    )
                                ) {
                                    Image(
                                        painterResource(id = android.R.drawable.ic_input_add),
                                        contentDescription = "Add poll option button",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            val url = postViewModel.contentToAddUrl
                            if (url != null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    ImageVideoDescription(
                                        url,
                                        account.defaultFileServer,
                                        onAdd = { description, server ->
                                            postViewModel.upload(url, description, server, context)
                                            account.changeDefaultFileServer(server)
                                        },
                                        onCancel = {
                                            postViewModel.contentToAddUrl = null
                                        },
                                        onError = {
                                            scope.launch {
                                                postViewModel.imageUploadingError.emit(it)
                                            }
                                        }
                                    )
                                }
                            }

                            val user = postViewModel.account?.userProfile()
                            val lud16 = user?.info?.lnAddress()

                            if (lud16 != null && postViewModel.wantsInvoice) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 5.dp)) {
                                    InvoiceRequest(
                                        lud16,
                                        user.pubkeyHex,
                                        account,
                                        stringResource(id = R.string.lightning_invoice),
                                        stringResource(id = R.string.lightning_create_and_add_invoice),
                                        onSuccess = {
                                            postViewModel.message = TextFieldValue(postViewModel.message.text + "\n\n" + it)
                                            postViewModel.wantsInvoice = false
                                        },
                                        onClose = {
                                            postViewModel.wantsInvoice = false
                                        }
                                    )
                                }
                            }

                            val myUrlPreview = postViewModel.urlPreview
                            if (myUrlPreview != null) {
                                Row(modifier = Modifier.padding(top = 5.dp)) {
                                    if (isValidURL(myUrlPreview)) {
                                        val removedParamsFromUrl =
                                            myUrlPreview.split("?")[0].lowercase()
                                        if (imageExtensions.any { removedParamsFromUrl.endsWith(it) }) {
                                            AsyncImage(
                                                model = myUrlPreview,
                                                contentDescription = myUrlPreview,
                                                contentScale = ContentScale.FillWidth,
                                                modifier = Modifier
                                                    .padding(top = 4.dp)
                                                    .fillMaxWidth()
                                                    .clip(shape = RoundedCornerShape(15.dp))
                                                    .border(
                                                        1.dp,
                                                        MaterialTheme.colors.onSurface.copy(alpha = 0.12f),
                                                        RoundedCornerShape(15.dp)
                                                    )
                                            )
                                        } else if (videoExtensions.any { removedParamsFromUrl.endsWith(it) }) {
                                            VideoView(myUrlPreview)
                                        } else {
                                            UrlPreview(myUrlPreview, myUrlPreview)
                                        }
                                    } else if (startsWithNIP19Scheme(myUrlPreview)) {
                                        BechLink(
                                            myUrlPreview,
                                            true,
                                            MaterialTheme.colors.background,
                                            accountViewModel,
                                            nav
                                        )
                                    } else if (noProtocolUrlValidator.matcher(myUrlPreview).matches()) {
                                        UrlPreview("https://$myUrlPreview", myUrlPreview)
                                    }
                                }
                            }
                        }
                    }

                    val userSuggestions = postViewModel.userSuggestions
                    if (userSuggestions.isNotEmpty()) {
                        LazyColumn(
                            contentPadding = PaddingValues(
                                top = 10.dp
                            ),
                            modifier = Modifier.heightIn(0.dp, 300.dp)
                        ) {
                            itemsIndexed(
                                userSuggestions,
                                key = { _, item -> item.pubkeyHex }
                            ) { _, item ->
                                UserLine(item, accountViewModel) {
                                    postViewModel.autocompleteWithUser(item)
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        UploadFromGallery(
                            isUploading = postViewModel.isUploadingImage,
                            tint = MaterialTheme.colors.onBackground,
                            modifier = Modifier
                        ) {
                            postViewModel.selectImage(it)
                        }

                        if (postViewModel.canUsePoll) {
                            // These should be hashtag recommendations the user selects in the future.
                            // val hashtag = stringResource(R.string.poll_hashtag)
                            // postViewModel.includePollHashtagInMessage(postViewModel.wantsPoll, hashtag)
                            AddPollButton(postViewModel.wantsPoll) {
                                postViewModel.wantsPoll = !postViewModel.wantsPoll
                            }
                        }

                        if (postViewModel.canAddInvoice) {
                            AddLnInvoiceButton(postViewModel.wantsInvoice) {
                                postViewModel.wantsInvoice = !postViewModel.wantsInvoice
                            }
                        }

                        MarkAsSensitive(postViewModel) {
                            postViewModel.wantsToMarkAsSensitive = !postViewModel.wantsToMarkAsSensitive
                        }

                        ForwardZapTo(postViewModel) {
                            postViewModel.wantsForwardZapTo = !postViewModel.wantsForwardZapTo
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Notifying(baseMentions: ImmutableList<User>?, onClick: (User) -> Unit) {
    val mentions = baseMentions?.toSet()

    FlowRow(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 10.dp)) {
        if (!mentions.isNullOrEmpty()) {
            Text(
                stringResource(R.string.reply_notify),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
            )

            mentions.forEachIndexed { idx, user ->
                val innerUserState by user.live().metadata.observeAsState()
                innerUserState?.user?.let { myUser ->
                    Spacer(modifier = Modifier.width(5.dp))

                    val tags = remember(innerUserState) {
                        myUser.info?.latestMetadata?.tags?.toImmutableListOfLists()
                    }

                    Button(
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = MaterialTheme.colors.primary.copy(alpha = 0.32f)
                        ),
                        onClick = {
                            onClick(myUser)
                        }
                    ) {
                        CreateTextWithEmoji(
                            text = remember(innerUserState) { "✖ ${myUser.toBestDisplayName()}" },
                            tags = tags,
                            color = Color.White,
                            textAlign = TextAlign.Center
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
    onClick: () -> Unit
) {
    IconButton(
        onClick = {
            onClick()
        }
    ) {
        if (!isPollActive) {
            Icon(
                painter = painterResource(R.drawable.ic_poll),
                null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colors.onBackground
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_lists),
                null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colors.onBackground
            )
        }
    }
}

@Composable
private fun AddLnInvoiceButton(
    isLnInvoiceActive: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        onClick = {
            onClick()
        }
    ) {
        if (!isLnInvoiceActive) {
            Icon(
                imageVector = Icons.Default.CurrencyBitcoin,
                null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colors.onBackground
            )
        } else {
            Icon(
                imageVector = Icons.Default.CurrencyBitcoin,
                null,
                modifier = Modifier.size(20.dp),
                tint = BitcoinOrange
            )
        }
    }
}

@Composable
private fun ForwardZapTo(
    postViewModel: NewPostViewModel,
    onClick: () -> Unit
) {
    IconButton(
        onClick = {
            onClick()
        }
    ) {
        Box(
            Modifier
                .height(20.dp)
                .width(25.dp)
        ) {
            if (!postViewModel.wantsForwardZapTo) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = stringResource(R.string.zaps),
                    modifier = Modifier
                        .size(20.dp)
                        .align(Alignment.CenterStart),
                    tint = MaterialTheme.colors.onBackground
                )
                Icon(
                    imageVector = Icons.Default.ArrowForwardIos,
                    contentDescription = stringResource(R.string.zaps),
                    modifier = Modifier
                        .size(13.dp)
                        .align(Alignment.CenterEnd),
                    tint = MaterialTheme.colors.onBackground
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.Bolt,
                    contentDescription = stringResource(id = R.string.zaps),
                    modifier = Modifier
                        .size(20.dp)
                        .align(Alignment.CenterStart),
                    tint = BitcoinOrange
                )
                Icon(
                    imageVector = Icons.Outlined.ArrowForwardIos,
                    contentDescription = stringResource(id = R.string.zaps),
                    modifier = Modifier
                        .size(13.dp)
                        .align(Alignment.CenterEnd),
                    tint = BitcoinOrange
                )
            }
        }
    }

    if (postViewModel.wantsForwardZapTo) {
        OutlinedTextField(
            value = postViewModel.forwardZapToEditting,
            onValueChange = {
                postViewModel.updateZapForwardTo(it)
            },
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets(0.dp, 0.dp, 0.dp, 0.dp))
                .padding(0.dp),
            placeholder = {
                Text(
                    text = stringResource(R.string.zap_forward_lnAddress),
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f),
                    fontSize = 14.sp
                )
            },
            colors = TextFieldDefaults
                .outlinedTextFieldColors(
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = Color.Transparent
                ),
            visualTransformation = UrlUserTagTransformation(MaterialTheme.colors.primary),
            textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Content)
        )
    }
}

@Composable
private fun MarkAsSensitive(
    postViewModel: NewPostViewModel,
    onClick: () -> Unit
) {
    IconButton(
        onClick = {
            onClick()
        }
    ) {
        Box(
            Modifier
                .height(20.dp)
                .width(23.dp)
        ) {
            if (!postViewModel.wantsToMarkAsSensitive) {
                Icon(
                    imageVector = Icons.Default.Visibility,
                    contentDescription = stringResource(R.string.content_warning),
                    modifier = Modifier
                        .size(18.dp)
                        .align(Alignment.BottomStart),
                    tint = MaterialTheme.colors.onBackground
                )
                Icon(
                    imageVector = Icons.Rounded.Warning,
                    contentDescription = stringResource(R.string.content_warning),
                    modifier = Modifier
                        .size(10.dp)
                        .align(Alignment.TopEnd),
                    tint = MaterialTheme.colors.onBackground
                )
            } else {
                Icon(
                    imageVector = Icons.Default.VisibilityOff,
                    contentDescription = stringResource(id = R.string.content_warning),
                    modifier = Modifier
                        .size(18.dp)
                        .align(Alignment.BottomStart),
                    tint = Color.Red
                )
                Icon(
                    imageVector = Icons.Rounded.Warning,
                    contentDescription = stringResource(id = R.string.content_warning),
                    modifier = Modifier
                        .size(10.dp)
                        .align(Alignment.TopEnd),
                    tint = Color.Yellow
                )
            }
        }
    }
}

@Composable
fun CloseButton(onCancel: () -> Unit) {
    Button(
        onClick = {
            onCancel()
        },
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults
            .buttonColors(
                backgroundColor = Color.Gray
            )
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_close),
            contentDescription = stringResource(id = R.string.cancel),
            modifier = Modifier.size(20.dp),
            tint = Color.White
        )
    }
}

@Composable
fun PostButton(onPost: () -> Unit = {}, isActive: Boolean, modifier: Modifier = Modifier) {
    Button(
        modifier = modifier,
        onClick = {
            if (isActive) {
                onPost()
            }
        },
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults
            .buttonColors(
                backgroundColor = if (isActive) MaterialTheme.colors.primary else Color.Gray
            )
    ) {
        Text(text = stringResource(R.string.post), color = Color.White)
    }
}

@Composable
fun SaveButton(onPost: () -> Unit = {}, isActive: Boolean, modifier: Modifier = Modifier) {
    Button(
        modifier = modifier,
        onClick = {
            if (isActive) {
                onPost()
            }
        },
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults
            .buttonColors(
                backgroundColor = if (isActive) MaterialTheme.colors.primary else Color.Gray
            )
    ) {
        Text(text = stringResource(R.string.save), color = Color.White)
    }
}

@Composable
fun CreateButton(onPost: () -> Unit = {}, isActive: Boolean, modifier: Modifier = Modifier) {
    Button(
        modifier = modifier,
        onClick = {
            if (isActive) {
                onPost()
            }
        },
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults
            .buttonColors(
                backgroundColor = if (isActive) MaterialTheme.colors.primary else Color.Gray
            )
    ) {
        Text(text = stringResource(R.string.create), color = Color.White)
    }
}

@Composable
fun SearchButton(onPost: () -> Unit = {}, isActive: Boolean, modifier: Modifier = Modifier) {
    Button(
        modifier = modifier,
        onClick = {
            if (isActive) {
                onPost()
            }
        },
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults
            .buttonColors(
                backgroundColor = if (isActive) MaterialTheme.colors.primary else Color.Gray
            )
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_search),
            null,
            modifier = Modifier.size(26.dp),
            tint = Color.White
        )
    }
}

enum class ServersAvailable {
    // IMGUR,
    NOSTR_BUILD,
    NOSTRIMG,
    NOSTRFILES_DEV,

    // IMGUR_NIP_94,
    NOSTRIMG_NIP_94,
    NOSTR_BUILD_NIP_94,
    NOSTRFILES_DEV_NIP_94,
    NIP95
}

@Composable
fun ImageVideoDescription(
    uri: Uri,
    defaultServer: ServersAvailable,
    onAdd: (String, ServersAvailable) -> Unit,
    onCancel: () -> Unit,
    onError: (String) -> Unit
) {
    val resolver = LocalContext.current.contentResolver
    val mediaType = resolver.getType(uri) ?: ""

    val isImage = mediaType.startsWith("image")
    val isVideo = mediaType.startsWith("video")

    val fileServers = listOf(
        // Triple(ServersAvailable.IMGUR, stringResource(id = R.string.upload_server_imgur), stringResource(id = R.string.upload_server_imgur_explainer)),
        Triple(ServersAvailable.NOSTRIMG, stringResource(id = R.string.upload_server_nostrimg), stringResource(id = R.string.upload_server_nostrimg_explainer)),
        Triple(ServersAvailable.NOSTR_BUILD, stringResource(id = R.string.upload_server_nostrbuild), stringResource(id = R.string.upload_server_nostrbuild_explainer)),
        Triple(ServersAvailable.NOSTRFILES_DEV, stringResource(id = R.string.upload_server_nostrfilesdev), stringResource(id = R.string.upload_server_nostrfilesdev_explainer)),
        // Triple(ServersAvailable.IMGUR_NIP_94, stringResource(id = R.string.upload_server_imgur_nip94), stringResource(id = R.string.upload_server_imgur_nip94_explainer)),
        Triple(ServersAvailable.NOSTRIMG_NIP_94, stringResource(id = R.string.upload_server_nostrimg_nip94), stringResource(id = R.string.upload_server_nostrimg_nip94_explainer)),
        Triple(ServersAvailable.NOSTR_BUILD_NIP_94, stringResource(id = R.string.upload_server_nostrbuild_nip94), stringResource(id = R.string.upload_server_nostrbuild_nip94_explainer)),
        Triple(ServersAvailable.NOSTRFILES_DEV_NIP_94, stringResource(id = R.string.upload_server_nostrfilesdev_nip94), stringResource(id = R.string.upload_server_nostrfilesdev_nip94_explainer)),
        Triple(ServersAvailable.NIP95, stringResource(id = R.string.upload_server_relays_nip95), stringResource(id = R.string.upload_server_relays_nip95_explainer))
    )

    val fileServerOptions = remember { fileServers.map { it.second }.toImmutableList() }
    val fileServerExplainers = remember { fileServers.map { it.third }.toImmutableList() }

    var selectedServer by remember { mutableStateOf(defaultServer) }
    var message by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 30.dp, end = 30.dp)
            .clip(shape = RoundedCornerShape(10.dp))
            .border(
                1.dp,
                MaterialTheme.colors.onSurface.copy(alpha = 0.12f),
                RoundedCornerShape(15.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(30.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
            ) {
                Text(
                    text = stringResource(
                        if (isImage) {
                            R.string.content_description_add_image
                        } else {
                            if (isVideo) {
                                R.string.content_description_add_video
                            } else {
                                R.string.content_description_add_document
                            }
                        }
                    ),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.W500,
                    modifier = Modifier
                        .padding(start = 10.dp)
                        .weight(1.0f)
                        .windowInsetsPadding(WindowInsets(0.dp, 0.dp, 0.dp, 0.dp))
                )

                IconButton(
                    modifier = Modifier.size(30.dp),
                    onClick = onCancel
                ) {
                    Icon(
                        imageVector = Icons.Default.Cancel,
                        null,
                        modifier = Modifier
                            .padding(end = 5.dp)
                            .size(30.dp),
                        tint = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                    )
                }
            }

            Divider()

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .windowInsetsPadding(WindowInsets(0.dp, 0.dp, 0.dp, 0.dp))
            ) {
                if (mediaType.startsWith("image")) {
                    AsyncImage(
                        model = uri.toString(),
                        contentDescription = uri.toString(),
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets(0.dp, 0.dp, 0.dp, 0.dp))
                    )
                } else if (mediaType.startsWith("video") && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

                    LaunchedEffect(key1 = uri) {
                        launch(Dispatchers.IO) {
                            try {
                                bitmap = resolver.loadThumbnail(uri, Size(1200, 1000), null)
                            } catch (e: Exception) {
                                onError("Unable to load file")
                                Log.e("NewPostView", "Couldn't create thumbnail for $uri")
                            }
                        }
                    }

                    bitmap?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = "some useful description",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .fillMaxWidth()
                        )
                    }
                } else {
                    VideoView(uri.toString())
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextSpinner(
                    label = stringResource(id = R.string.file_server),
                    placeholder = fileServers.filter { it.first == defaultServer }.firstOrNull()?.second ?: fileServers[0].second,
                    options = fileServerOptions,
                    explainers = fileServerExplainers,
                    onSelect = {
                        selectedServer = fileServers[it].first
                    },
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets(0.dp, 0.dp, 0.dp, 0.dp))
                        .weight(1f)
                )
            }

            if (isNIP94Server(selectedServer) ||
                selectedServer == ServersAvailable.NIP95
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets(0.dp, 0.dp, 0.dp, 0.dp))
                ) {
                    OutlinedTextField(
                        label = { Text(text = stringResource(R.string.content_description)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)),
                        value = message,
                        onValueChange = { message = it },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.content_description_example),
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                            )
                        },
                        keyboardOptions = KeyboardOptions.Default.copy(
                            capitalization = KeyboardCapitalization.Sentences
                        )
                    )
                }
            }

            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                onClick = {
                    onAdd(message, selectedServer)
                },
                shape = RoundedCornerShape(15.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.colors.primary
                )
            ) {
                Text(text = stringResource(R.string.add_content), color = Color.White, fontSize = 20.sp)
            }
        }
    }
}

@Stable
data class ImmutableListOfLists<T>(val lists: List<List<T>> = emptyList())

fun List<List<String>>.toImmutableListOfLists(): ImmutableListOfLists<String> {
    return ImmutableListOfLists(this)
}
