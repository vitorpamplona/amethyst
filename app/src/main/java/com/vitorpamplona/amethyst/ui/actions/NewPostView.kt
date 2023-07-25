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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.ArrowForwardIos
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.fonfon.kgeohash.toGeoHash
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.NostrSearchEventOrUserDataSource
import com.vitorpamplona.amethyst.service.ReverseGeoLocationUtil
import com.vitorpamplona.amethyst.service.noProtocolUrlValidator
import com.vitorpamplona.amethyst.ui.components.*
import com.vitorpamplona.amethyst.ui.note.CancelIcon
import com.vitorpamplona.amethyst.ui.note.CloseIcon
import com.vitorpamplona.amethyst.ui.note.NoteCompose
import com.vitorpamplona.amethyst.ui.note.PollIcon
import com.vitorpamplona.amethyst.ui.note.RegularPostIcon
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.TextSpinner
import com.vitorpamplona.amethyst.ui.screen.loggedIn.UserLine
import com.vitorpamplona.amethyst.ui.theme.BitcoinOrange
import com.vitorpamplona.amethyst.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.ui.theme.QuoteBorder
import com.vitorpamplona.amethyst.ui.theme.Size10dp
import com.vitorpamplona.amethyst.ui.theme.Size5dp
import com.vitorpamplona.amethyst.ui.theme.mediumImportanceLink
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.amethyst.ui.theme.replyModifier
import com.vitorpamplona.amethyst.ui.theme.subtleBorder
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun NewPostView(onClose: () -> Unit, baseReplyTo: Note? = null, quote: Note? = null, accountViewModel: AccountViewModel, nav: (String) -> Unit) {
    val account = remember(accountViewModel) { accountViewModel.account }

    val postViewModel: NewPostViewModel = viewModel()

    val context = LocalContext.current

    // initialize focus reference to be able to request focus programmatically
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    var showRelaysDialog by remember {
        mutableStateOf(false)
    }
    var relayList = account.activeRelays()?.filter {
        it.write
    }?.map {
        it
    } ?: account.convertLocalRelays().filter {
        it.write
    }

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
            if (showRelaysDialog) {
                RelaySelectionDialog(
                    list = relayList,
                    onClose = {
                        showRelaysDialog = false
                    },
                    onPost = {
                        relayList = it
                    },
                    accountViewModel = accountViewModel,
                    nav = nav
                )
            }

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

                        Box {
                            IconButton(
                                modifier = Modifier.align(Alignment.Center),
                                onClick = {
                                    showRelaysDialog = true
                                }
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.relays),
                                    contentDescription = null,
                                    modifier = Modifier.height(25.dp),
                                    tint = MaterialTheme.colors.onBackground
                                )
                            }
                        }
                        PostButton(
                            onPost = {
                                scope.launch(Dispatchers.IO) {
                                    postViewModel.sendPost(relayList = relayList)
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
                            postViewModel.originalNote?.let {
                                NoteCompose(
                                    baseNote = it,
                                    makeItShort = true,
                                    unPackReply = false,
                                    isQuotedNote = true,
                                    modifier = MaterialTheme.colors.replyModifier,
                                    accountViewModel = accountViewModel,
                                    nav = nav
                                )
                            }

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
                                        color = MaterialTheme.colors.placeholderText
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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(vertical = Size5dp, horizontal = Size10dp)
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        postViewModel.pollOptions.values.forEachIndexed { index, _ ->
                                            NewPollOption(postViewModel, index)
                                        }

                                        Button(
                                            onClick = {
                                                postViewModel.pollOptions[postViewModel.pollOptions.size] =
                                                    ""
                                            },
                                            border = BorderStroke(
                                                1.dp,
                                                MaterialTheme.colors.placeholderText
                                            ),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = MaterialTheme.colors.placeholderText
                                            )
                                        ) {
                                            Image(
                                                painterResource(id = android.R.drawable.ic_input_add),
                                                contentDescription = "Add poll option button",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            if (postViewModel.wantsToMarkAsSensitive) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .padding(vertical = Size5dp, horizontal = Size10dp)
                                ) {
                                    ContentSensitivityExplainer(postViewModel)
                                }
                            }

                            if (postViewModel.wantsToAddGeoHash) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .padding(vertical = Size5dp, horizontal = Size10dp)
                                ) {
                                    LocationAsHash(postViewModel)
                                }
                            }

                            if (postViewModel.wantsForwardZapTo) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(vertical = Size5dp, horizontal = Size10dp)
                                ) {
                                    FowardZapTo(postViewModel)
                                }
                            }

                            val url = postViewModel.contentToAddUrl
                            if (url != null) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = Size5dp, horizontal = Size10dp)) {
                                    ImageVideoDescription(
                                        url,
                                        account.defaultFileServer,
                                        onAdd = { description, server, sensitiveContent ->
                                            postViewModel.upload(url, description, sensitiveContent, server, context, relayList)
                                            account.changeDefaultFileServer(server)
                                        },
                                        onCancel = {
                                            postViewModel.contentToAddUrl = null
                                        },
                                        onError = {
                                            scope.launch {
                                                postViewModel.imageUploadingError.emit(it)
                                            }
                                        },
                                        accountViewModel = accountViewModel
                                    )
                                }
                            }

                            val user = postViewModel.account?.userProfile()
                            val lud16 = user?.info?.lnAddress()

                            if (lud16 != null && postViewModel.wantsInvoice) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = Size5dp, horizontal = Size10dp)) {
                                    Column(Modifier.fillMaxWidth()) {
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
                            }

                            if (lud16 != null && postViewModel.wantsZapraiser) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = Size5dp, horizontal = Size10dp)) {
                                    ZapRaiserRequest(
                                        stringResource(id = R.string.zapraiser),
                                        postViewModel
                                    )
                                }
                            }

                            val myUrlPreview = postViewModel.urlPreview
                            if (myUrlPreview != null) {
                                Row(modifier = Modifier.padding(vertical = Size5dp, horizontal = Size10dp)) {
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
                                                    .clip(shape = QuoteBorder)
                                                    .border(
                                                        1.dp,
                                                        MaterialTheme.colors.subtleBorder,
                                                        QuoteBorder
                                                    )
                                            )
                                        } else if (videoExtensions.any { removedParamsFromUrl.endsWith(it) }) {
                                            VideoView(myUrlPreview, accountViewModel = accountViewModel)
                                        } else {
                                            UrlPreview(myUrlPreview, myUrlPreview, accountViewModel)
                                        }
                                    } else if (startsWithNIP19Scheme(myUrlPreview)) {
                                        val bgColor = MaterialTheme.colors.background
                                        val backgroundColor = remember {
                                            mutableStateOf(bgColor)
                                        }

                                        BechLink(
                                            myUrlPreview,
                                            true,
                                            backgroundColor,
                                            accountViewModel,
                                            nav
                                        )
                                    } else if (noProtocolUrlValidator.matcher(myUrlPreview).matches()) {
                                        UrlPreview("https://$myUrlPreview", myUrlPreview, accountViewModel)
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

                        if (postViewModel.canAddZapRaiser) {
                            AddZapraiserButton(postViewModel.wantsZapraiser) {
                                postViewModel.wantsZapraiser = !postViewModel.wantsZapraiser
                            }
                        }

                        MarkAsSensitive(postViewModel) {
                            postViewModel.wantsToMarkAsSensitive = !postViewModel.wantsToMarkAsSensitive
                        }

                        AddGeoHash(postViewModel) {
                            postViewModel.wantsToAddGeoHash = !postViewModel.wantsToAddGeoHash
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

@Composable
fun ContentSensitivityExplainer(postViewModel: NewPostViewModel) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
        ) {
            Box(
                Modifier
                    .height(20.dp)
                    .width(25.dp)
            ) {
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

            Text(
                text = stringResource(R.string.add_sensitive_content_label),
                fontSize = 20.sp,
                fontWeight = FontWeight.W500,
                modifier = Modifier.padding(start = 10.dp)
            )
        }

        Divider()

        Text(
            text = stringResource(R.string.add_sensitive_content_explainer),
            color = MaterialTheme.colors.placeholderText,
            modifier = Modifier.padding(vertical = 10.dp)
        )
    }
}

@Composable
fun FowardZapTo(postViewModel: NewPostViewModel) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
        ) {
            Box(
                Modifier
                    .height(20.dp)
                    .width(25.dp)
            ) {
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

            Text(
                text = stringResource(R.string.zap_forward_title),
                fontSize = 20.sp,
                fontWeight = FontWeight.W500,
                modifier = Modifier.padding(start = 10.dp)
            )
        }

        Divider()

        Text(
            text = stringResource(R.string.zap_forward_explainer),
            color = MaterialTheme.colors.placeholderText,
            modifier = Modifier.padding(vertical = 10.dp)
        )

        OutlinedTextField(
            value = postViewModel.forwardZapToEditting,
            onValueChange = {
                postViewModel.updateZapForwardTo(it)
            },

            label = { Text(text = stringResource(R.string.zap_forward_lnAddress)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = stringResource(R.string.zap_forward_lnAddress),
                    color = MaterialTheme.colors.placeholderText
                )
            },
            singleLine = true,
            visualTransformation = UrlUserTagTransformation(
                MaterialTheme.colors.primary
            ),
            textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Content)
        )
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun LocationAsHash(postViewModel: NewPostViewModel) {
    val context = LocalContext.current

    val locationPermissionState = rememberPermissionState(
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    if (locationPermissionState.status.isGranted) {
        var locationDescriptionFlow by remember(postViewModel) {
            mutableStateOf<Flow<String>?>(null)
        }

        DisposableEffect(key1 = Unit) {
            postViewModel.startLocation(context = context)
            locationDescriptionFlow = postViewModel.location

            onDispose {
                postViewModel.stopLocation()
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
            ) {
                Box(
                    Modifier
                        .height(20.dp)
                        .width(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colors.primary
                    )
                }

                Text(
                    text = stringResource(R.string.geohash_title),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.W500,
                    modifier = Modifier.padding(start = 10.dp)
                )

                locationDescriptionFlow?.let { geoLocation ->
                    DisplayLocationObserver(geoLocation)
                }
            }

            Divider()

            Text(
                text = stringResource(R.string.geohash_explainer),
                color = MaterialTheme.colors.placeholderText,
                modifier = Modifier.padding(vertical = 10.dp)
            )
        }
    } else {
        LaunchedEffect(locationPermissionState) {
            locationPermissionState.launchPermissionRequest()
        }
    }
}

@Composable
fun DisplayLocationObserver(geoLocation: Flow<String>) {
    val location by geoLocation.collectAsState(initial = null)

    location?.let {
        DisplayLocationInTitle(geohash = it)
    }
}

@Composable
fun DisplayLocationInTitle(geohash: String) {
    val context = LocalContext.current
    val cityName = remember(geohash) {
        ReverseGeoLocationUtil().execute(geohash.toGeoHash().toLocation(), context)
    }

    Text(
        text = cityName ?: geohash,
        fontSize = 20.sp,
        fontWeight = FontWeight.W500,
        modifier = Modifier.padding(start = Size5dp)
    )
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
                color = MaterialTheme.colors.placeholderText
            )

            mentions.forEachIndexed { idx, user ->
                val innerUserState by user.live().metadata.observeAsState()
                innerUserState?.user?.let { myUser ->
                    Spacer(modifier = Modifier.width(5.dp))

                    val tags = remember(innerUserState) {
                        myUser.info?.latestMetadata?.tags?.toImmutableListOfLists()
                    }

                    Button(
                        shape = ButtonBorder,
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = MaterialTheme.colors.mediumImportanceLink
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
            PollIcon()
        } else {
            RegularPostIcon()
        }
    }
}

@Composable
private fun AddZapraiserButton(
    isLnInvoiceActive: Boolean,
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
            if (!isLnInvoiceActive) {
                Icon(
                    imageVector = Icons.Default.ShowChart,
                    null,
                    modifier = Modifier
                        .size(20.dp)
                        .align(Alignment.TopStart),
                    tint = MaterialTheme.colors.onBackground
                )
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = stringResource(R.string.zaps),
                    modifier = Modifier
                        .size(13.dp)
                        .align(Alignment.BottomEnd),
                    tint = MaterialTheme.colors.onBackground
                )
            } else {
                Icon(
                    imageVector = Icons.Default.ShowChart,
                    null,
                    modifier = Modifier
                        .size(20.dp)
                        .align(Alignment.TopStart),
                    tint = BitcoinOrange
                )
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = stringResource(R.string.zaps),
                    modifier = Modifier
                        .size(13.dp)
                        .align(Alignment.BottomEnd),
                    tint = BitcoinOrange
                )
            }
        }
    }
}

@Composable
fun AddGeoHash(postViewModel: NewPostViewModel, onClick: () -> Unit) {
    IconButton(
        onClick = {
            onClick()
        }
    ) {
        if (!postViewModel.wantsToAddGeoHash) {
            Icon(
                imageVector = Icons.Default.LocationOff,
                null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colors.onBackground
            )
        } else {
            Icon(
                imageVector = Icons.Default.LocationOn,
                null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colors.primary
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
        shape = ButtonBorder,
        colors = ButtonDefaults
            .buttonColors(
                backgroundColor = Color.Gray
            )
    ) {
        CloseIcon()
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
        shape = ButtonBorder,
        colors = ButtonDefaults
            .buttonColors(
                backgroundColor = if (isActive) MaterialTheme.colors.primary else Color.Gray
            ),
        contentPadding = PaddingValues(0.dp)
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
        shape = ButtonBorder,
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
        shape = ButtonBorder,
        colors = ButtonDefaults
            .buttonColors(
                backgroundColor = if (isActive) MaterialTheme.colors.primary else Color.Gray
            )
    ) {
        Text(text = stringResource(R.string.create), color = Color.White)
    }
}

enum class ServersAvailable {
    // IMGUR,
    NOSTR_BUILD,
    NOSTRIMG,
    NOSTRFILES_DEV,
    NOSTRCHECK_ME,

    // IMGUR_NIP_94,
    NOSTRIMG_NIP_94,
    NOSTR_BUILD_NIP_94,
    NOSTRFILES_DEV_NIP_94,
    NOSTRCHECK_ME_NIP_94,
    NIP95
}

@Composable
fun ImageVideoDescription(
    uri: Uri,
    defaultServer: ServersAvailable,
    onAdd: (String, ServersAvailable, Boolean) -> Unit,
    onCancel: () -> Unit,
    onError: (String) -> Unit,
    accountViewModel: AccountViewModel
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
        Triple(ServersAvailable.NOSTRCHECK_ME, stringResource(id = R.string.upload_server_nostrcheckme), stringResource(id = R.string.upload_server_nostrcheckme_explainer)),
        // Triple(ServersAvailable.IMGUR_NIP_94, stringResource(id = R.string.upload_server_imgur_nip94), stringResource(id = R.string.upload_server_imgur_nip94_explainer)),
        Triple(ServersAvailable.NOSTRIMG_NIP_94, stringResource(id = R.string.upload_server_nostrimg_nip94), stringResource(id = R.string.upload_server_nostrimg_nip94_explainer)),
        Triple(ServersAvailable.NOSTR_BUILD_NIP_94, stringResource(id = R.string.upload_server_nostrbuild_nip94), stringResource(id = R.string.upload_server_nostrbuild_nip94_explainer)),
        Triple(ServersAvailable.NOSTRFILES_DEV_NIP_94, stringResource(id = R.string.upload_server_nostrfilesdev_nip94), stringResource(id = R.string.upload_server_nostrfilesdev_nip94_explainer)),
        Triple(ServersAvailable.NOSTRCHECK_ME_NIP_94, stringResource(id = R.string.upload_server_nostrcheckme_nip94), stringResource(id = R.string.upload_server_nostrcheckme_nip94_explainer)),
        Triple(ServersAvailable.NIP95, stringResource(id = R.string.upload_server_relays_nip95), stringResource(id = R.string.upload_server_relays_nip95_explainer))
    )

    val fileServerOptions = remember { fileServers.map { it.second }.toImmutableList() }
    val fileServerExplainers = remember { fileServers.map { it.third }.toImmutableList() }

    var selectedServer by remember { mutableStateOf(defaultServer) }
    var message by remember { mutableStateOf("") }
    var sensitiveContent by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 30.dp, end = 30.dp)
            .clip(shape = QuoteBorder)
            .border(
                1.dp,
                MaterialTheme.colors.subtleBorder,
                QuoteBorder
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
                    modifier = Modifier
                        .size(30.dp)
                        .padding(end = 5.dp),
                    onClick = onCancel
                ) {
                    CancelIcon()
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
                    VideoView(uri.toString(), accountViewModel = accountViewModel)
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
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SettingSwitchItem(
                        checked = sensitiveContent,
                        onCheckedChange = { sensitiveContent = it },
                        title = R.string.add_sensitive_content_label,
                        description = R.string.add_sensitive_content_description
                    )
                }

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
                                color = MaterialTheme.colors.placeholderText
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
                    onAdd(message, selectedServer, sensitiveContent)
                },
                shape = QuoteBorder,
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

@Composable
fun SettingSwitchItem(
    modifier: Modifier = Modifier,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    title: Int,
    description: Int,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1.0f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            val contentAlpha = if (enabled) ContentAlpha.high else ContentAlpha.disabled

            Text(
                text = stringResource(id = title),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.alpha(contentAlpha)
            )
            Text(
                text = stringResource(id = description),
                style = MaterialTheme.typography.caption,
                color = Color.Gray,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.alpha(contentAlpha)
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled
        )
    }
}
