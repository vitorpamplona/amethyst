package com.vitorpamplona.amethyst.ui.screen.loggedIn

import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Channel
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.NostrChannelDataSource
import com.vitorpamplona.amethyst.ui.actions.NewChannelView
import com.vitorpamplona.amethyst.ui.actions.NewMessageTagger
import com.vitorpamplona.amethyst.ui.actions.NewPostViewModel
import com.vitorpamplona.amethyst.ui.actions.PostButton
import com.vitorpamplona.amethyst.ui.actions.ServersAvailable
import com.vitorpamplona.amethyst.ui.actions.UploadFromGallery
import com.vitorpamplona.amethyst.ui.components.ResizeImage
import com.vitorpamplona.amethyst.ui.components.RobohashAsyncImageProxy
import com.vitorpamplona.amethyst.ui.navigation.Route
import com.vitorpamplona.amethyst.ui.note.ChatroomMessageCompose
import com.vitorpamplona.amethyst.ui.screen.NostrChannelFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.RefreshingChatroomFeedView
import com.vitorpamplona.amethyst.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ChannelScreen(
    channelId: String?,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    if (channelId == null) return

    var channelBase by remember { mutableStateOf<Channel?>(null) }

    LaunchedEffect(channelId) {
        withContext(Dispatchers.IO) {
            val newChannelBase = LocalCache.checkGetOrCreateChannel(channelId)
            if (newChannelBase != channelBase) {
                channelBase = newChannelBase
            }
        }
    }

    channelBase?.let {
        PrepareChannelViewModels(
            baseChannel = it,
            accountViewModel = accountViewModel,
            nav = nav
        )
    }
}

@Composable
fun PrepareChannelViewModels(baseChannel: Channel, accountViewModel: AccountViewModel, nav: (String) -> Unit) {
    val feedViewModel: NostrChannelFeedViewModel = viewModel(
        key = baseChannel.idHex + "ChannelFeedViewModel",
        factory = NostrChannelFeedViewModel.Factory(
            baseChannel,
            accountViewModel.account
        )
    )

    val channelScreenModel: NewPostViewModel = viewModel()
    channelScreenModel.account = accountViewModel.account

    ChannelScreen(
        channel = baseChannel,
        feedViewModel = feedViewModel,
        newPostModel = channelScreenModel,
        accountViewModel = accountViewModel,
        nav = nav
    )
}

@Composable
fun ChannelScreen(
    channel: Channel,
    feedViewModel: NostrChannelFeedViewModel,
    newPostModel: NewPostViewModel,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    val context = LocalContext.current

    NostrChannelDataSource.loadMessagesBetween(accountViewModel.account, channel)

    val lifeCycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        NostrChannelDataSource.start()
        feedViewModel.invalidateData()

        launch(Dispatchers.IO) {
            newPostModel.imageUploadingError.collect { error ->
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    DisposableEffect(accountViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                println("Channel Start")
                NostrChannelDataSource.start()
                feedViewModel.invalidateData()
            }
            if (event == Lifecycle.Event.ON_PAUSE) {
                println("Channel Stop")

                NostrChannelDataSource.clear()
                NostrChannelDataSource.stop()
            }
        }

        lifeCycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifeCycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(Modifier.fillMaxHeight()) {
        ChannelHeader(
            channel,
            accountViewModel,
            nav = nav
        )

        val replyTo = remember { mutableStateOf<Note?>(null) }

        Column(
            modifier = remember {
                Modifier
                    .fillMaxHeight()
                    .padding(vertical = 0.dp)
                    .weight(1f, true)
            }
        ) {
            RefreshingChatroomFeedView(
                viewModel = feedViewModel,
                accountViewModel = accountViewModel,
                nav = nav,
                routeForLastRead = "Channel/${channel.idHex}",
                onWantsToReply = {
                    replyTo.value = it
                }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        replyTo.value?.let {
            DisplayReplyingToNote(it, accountViewModel, nav) {
                replyTo.value = null
            }
        }

        val scope = rememberCoroutineScope()

        // LAST ROW
        EditFieldRow(newPostModel, isPrivate = false, accountViewModel = accountViewModel) {
            scope.launch(Dispatchers.IO) {
                val tagger = NewMessageTagger(
                    channelHex = channel.idHex,
                    mentions = listOfNotNull(replyTo.value?.author),
                    replyTos = listOfNotNull(replyTo.value),
                    message = newPostModel.message.text
                )
                tagger.run()
                accountViewModel.account.sendChannelMessage(
                    message = tagger.message,
                    toChannel = channel.idHex,
                    replyTo = tagger.replyTos,
                    mentions = tagger.mentions,
                    wantsToMarkAsSensitive = false
                )
                newPostModel.message = TextFieldValue("")
                replyTo.value = null
                feedViewModel.sendToTop()
            }
        }
    }
}

@Composable
fun DisplayReplyingToNote(
    replyingNote: Note?,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
    onCancel: () -> Unit
) {
    Row(
        Modifier
            .padding(horizontal = 10.dp)
            .animateContentSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (replyingNote != null) {
            Column(remember { Modifier.weight(1f) }) {
                ChatroomMessageCompose(
                    baseNote = replyingNote,
                    null,
                    innerQuote = true,
                    accountViewModel = accountViewModel,
                    nav = nav,
                    onWantsToReply = {}
                )
            }

            Column(Modifier.padding(end = 10.dp)) {
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
                        tint = MaterialTheme.colors.placeholderText
                    )
                }
            }
        }
    }
}

@Composable
fun EditFieldRow(
    channelScreenModel: NewPostViewModel,
    isPrivate: Boolean,
    accountViewModel: AccountViewModel,
    onSendNewMessage: () -> Unit
) {
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .padding(start = 10.dp, end = 10.dp, bottom = 10.dp, top = 5.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = channelScreenModel.message,
            onValueChange = {
                channelScreenModel.updateMessage(it)
            },
            keyboardOptions = KeyboardOptions.Default.copy(
                capitalization = KeyboardCapitalization.Sentences
            ),
            shape = RoundedCornerShape(25.dp),
            modifier = Modifier.weight(1f, true),
            placeholder = {
                Text(
                    text = stringResource(R.string.reply_here),
                    color = MaterialTheme.colors.placeholderText
                )
            },
            textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
            trailingIcon = {
                PostButton(
                    onPost = {
                        onSendNewMessage()
                    },
                    isActive = channelScreenModel.message.text.isNotBlank() && !channelScreenModel.isUploadingImage,
                    modifier = Modifier.padding(end = 10.dp)
                )
            },
            leadingIcon = {
                UploadFromGallery(
                    isUploading = channelScreenModel.isUploadingImage,
                    tint = MaterialTheme.colors.placeholderText,
                    modifier = Modifier.padding(start = 5.dp)
                ) {
                    val fileServer = if (isPrivate) {
                        // TODO: Make private servers
                        when (accountViewModel.account.defaultFileServer) {
                            ServersAvailable.NOSTR_BUILD -> ServersAvailable.NOSTR_BUILD
                            ServersAvailable.NOSTRIMG -> ServersAvailable.NOSTRIMG
                            ServersAvailable.NOSTRFILES_DEV -> ServersAvailable.NOSTRFILES_DEV
                            ServersAvailable.NOSTR_BUILD_NIP_94 -> ServersAvailable.NOSTR_BUILD
                            ServersAvailable.NOSTRIMG_NIP_94 -> ServersAvailable.NOSTRIMG
                            ServersAvailable.NOSTRFILES_DEV_NIP_94 -> ServersAvailable.NOSTRFILES_DEV
                            ServersAvailable.NIP95 -> ServersAvailable.NOSTR_BUILD
                        }
                    } else {
                        accountViewModel.account.defaultFileServer
                    }

                    channelScreenModel.upload(it, "", fileServer, context)
                }
            },
            colors = TextFieldDefaults.textFieldColors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            )
        )
    }
}

@Composable
fun ChannelHeader(channelHex: String, accountViewModel: AccountViewModel, nav: (String) -> Unit) {
    var baseChannel by remember { mutableStateOf<Channel?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(key1 = channelHex) {
        scope.launch(Dispatchers.IO) {
            baseChannel = LocalCache.checkGetOrCreateChannel(channelHex)
        }
    }

    baseChannel?.let {
        ChannelHeader(it, accountViewModel, nav)
    }
}

@Composable
fun ChannelHeader(baseChannel: Channel, accountViewModel: AccountViewModel, nav: (String) -> Unit) {
    val channelState by baseChannel.live.observeAsState()
    val channel = remember(channelState) { channelState?.channel } ?: return

    val context = LocalContext.current.applicationContext

    Column(
        Modifier.clickable {
            nav("Channel/${baseChannel.idHex}")
        }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RobohashAsyncImageProxy(
                    robot = channel.idHex,
                    model = ResizeImage(channel.profilePicture(), 35.dp),
                    contentDescription = context.getString(R.string.profile_image),
                    modifier = Modifier
                        .width(35.dp)
                        .height(35.dp)
                        .clip(shape = CircleShape)
                )

                Column(
                    modifier = Modifier
                        .padding(start = 10.dp)
                        .weight(1f)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${channel.info.name}",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${channel.info.about}",
                            color = MaterialTheme.colors.placeholderText,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 12.sp
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .height(35.dp)
                        .padding(bottom = 3.dp)
                ) {
                    ChannelActionOptions(channel, accountViewModel, nav)
                }
            }
        }

        Divider(
            modifier = Modifier.padding(start = 12.dp, end = 12.dp),
            thickness = 0.25.dp
        )
    }
}

@Composable
private fun ChannelActionOptions(
    channel: Channel,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    NoteCopyButton(channel)

    val isMe by remember(accountViewModel) {
        derivedStateOf {
            channel.creator == accountViewModel.account.userProfile()
        }
    }

    val accountState by accountViewModel.accountLiveData.observeAsState()
    val isFollowing by remember(accountState) {
        derivedStateOf {
            accountState?.account?.followingChannels?.contains(channel.idHex) ?: false
        }
    }

    if (isMe) {
        EditButton(accountViewModel, channel)
    }

    if (isFollowing) {
        LeaveButton(accountViewModel, channel, nav)
    } else {
        JoinButton(accountViewModel, channel, nav)
    }
}

@Composable
private fun NoteCopyButton(
    note: Channel
) {
    var popupExpanded by remember { mutableStateOf(false) }

    Button(
        modifier = Modifier
            .padding(horizontal = 3.dp)
            .width(50.dp),
        onClick = { popupExpanded = true },
        shape = ButtonBorder,
        colors = ButtonDefaults
            .buttonColors(
                backgroundColor = MaterialTheme.colors.placeholderText
            )
    ) {
        Icon(
            tint = Color.White,
            imageVector = Icons.Default.Share,
            contentDescription = stringResource(R.string.copies_the_note_id_to_the_clipboard_for_sharing)
        )

        DropdownMenu(
            expanded = popupExpanded,
            onDismissRequest = { popupExpanded = false }
        ) {
            val clipboardManager = LocalClipboardManager.current

            DropdownMenuItem(onClick = { clipboardManager.setText(AnnotatedString("nostr:" + note.idNote())); popupExpanded = false }) {
                Text(stringResource(R.string.copy_channel_id_note_to_the_clipboard))
            }
        }
    }
}

@Composable
private fun EditButton(accountViewModel: AccountViewModel, channel: Channel) {
    var wantsToPost by remember {
        mutableStateOf(false)
    }

    if (wantsToPost) {
        NewChannelView({ wantsToPost = false }, accountViewModel, channel)
    }

    Button(
        modifier = Modifier
            .padding(horizontal = 3.dp)
            .width(50.dp),
        onClick = { wantsToPost = true },
        shape = ButtonBorder,
        colors = ButtonDefaults
            .buttonColors(
                backgroundColor = MaterialTheme.colors.primary
            )
    ) {
        Icon(
            tint = Color.White,
            imageVector = Icons.Default.EditNote,
            contentDescription = stringResource(R.string.edits_the_channel_metadata)
        )
    }
}

@Composable
private fun JoinButton(accountViewModel: AccountViewModel, channel: Channel, nav: (String) -> Unit) {
    Button(
        modifier = Modifier.padding(horizontal = 3.dp),
        onClick = {
            accountViewModel.account.joinChannel(channel.idHex)
        },
        shape = ButtonBorder,
        colors = ButtonDefaults
            .buttonColors(
                backgroundColor = MaterialTheme.colors.primary
            ),
        contentPadding = PaddingValues(vertical = 6.dp, horizontal = 16.dp)
    ) {
        Text(text = stringResource(R.string.join), color = Color.White)
    }
}

@Composable
private fun LeaveButton(accountViewModel: AccountViewModel, channel: Channel, nav: (String) -> Unit) {
    Button(
        modifier = Modifier.padding(horizontal = 3.dp),
        onClick = {
            accountViewModel.account.leaveChannel(channel.idHex)
            nav(Route.Message.route)
        },
        shape = ButtonBorder,
        colors = ButtonDefaults
            .buttonColors(
                backgroundColor = MaterialTheme.colors.primary
            ),
        contentPadding = PaddingValues(vertical = 6.dp, horizontal = 16.dp)
    ) {
        Text(text = stringResource(R.string.leave), color = Color.White)
    }
}
