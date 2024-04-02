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

import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.map
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.NostrChatroomDataSource
import com.vitorpamplona.amethyst.ui.actions.CloseButton
import com.vitorpamplona.amethyst.ui.actions.NewPostViewModel
import com.vitorpamplona.amethyst.ui.actions.PostButton
import com.vitorpamplona.amethyst.ui.actions.ServerOption
import com.vitorpamplona.amethyst.ui.actions.UploadFromGallery
import com.vitorpamplona.amethyst.ui.actions.UrlUserTagTransformation
import com.vitorpamplona.amethyst.ui.note.ClickableUserPicture
import com.vitorpamplona.amethyst.ui.note.DisplayRoomSubject
import com.vitorpamplona.amethyst.ui.note.DisplayUserSetAsSubject
import com.vitorpamplona.amethyst.ui.note.IncognitoIconOff
import com.vitorpamplona.amethyst.ui.note.IncognitoIconOn
import com.vitorpamplona.amethyst.ui.note.LoadUser
import com.vitorpamplona.amethyst.ui.note.NonClickableUserPictures
import com.vitorpamplona.amethyst.ui.note.QuickActionAlertDialog
import com.vitorpamplona.amethyst.ui.note.UserCompose
import com.vitorpamplona.amethyst.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.ui.screen.NostrChatroomFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.RefreshingChatroomFeedView
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.EditFieldBorder
import com.vitorpamplona.amethyst.ui.theme.EditFieldModifier
import com.vitorpamplona.amethyst.ui.theme.EditFieldTrailingIconModifier
import com.vitorpamplona.amethyst.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.ui.theme.Size30Modifier
import com.vitorpamplona.amethyst.ui.theme.Size34dp
import com.vitorpamplona.amethyst.ui.theme.StdPadding
import com.vitorpamplona.amethyst.ui.theme.ZeroPadding
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.quartz.events.ChatMessageEvent
import com.vitorpamplona.quartz.events.ChatroomKey
import com.vitorpamplona.quartz.events.findURLs
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ChatroomScreen(
    roomId: String?,
    draftMessage: String? = null,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    if (roomId == null) return

    LoadRoom(roomId, accountViewModel) {
        it?.let {
            PrepareChatroomViewModels(
                room = it,
                draftMessage = draftMessage,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

@Composable
fun ChatroomScreenByAuthor(
    authorPubKeyHex: String?,
    draftMessage: String? = null,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    if (authorPubKeyHex == null) return

    LoadRoomByAuthor(authorPubKeyHex, accountViewModel) {
        it?.let {
            PrepareChatroomViewModels(
                room = it,
                draftMessage = draftMessage,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

@Composable
fun LoadRoom(
    roomId: String,
    accountViewModel: AccountViewModel,
    content: @Composable (ChatroomKey?) -> Unit,
) {
    var room by remember(roomId) { mutableStateOf<ChatroomKey?>(null) }

    if (room == null) {
        LaunchedEffect(key1 = roomId) {
            launch(Dispatchers.IO) {
                val newRoom =
                    accountViewModel.userProfile().privateChatrooms.keys.firstOrNull {
                        it.hashCode().toString() == roomId
                    }
                if (room != newRoom) {
                    room = newRoom
                }
            }
        }
    }

    content(room)
}

@Composable
fun LoadRoomByAuthor(
    authorPubKeyHex: String,
    accountViewModel: AccountViewModel,
    content: @Composable (ChatroomKey?) -> Unit,
) {
    val room by
        remember(authorPubKeyHex) {
            mutableStateOf<ChatroomKey?>(ChatroomKey(persistentSetOf(authorPubKeyHex)))
        }

    content(room)
}

@OptIn(FlowPreview::class)
@Composable
fun PrepareChatroomViewModels(
    room: ChatroomKey,
    draftMessage: String?,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val feedViewModel: NostrChatroomFeedViewModel =
        viewModel(
            key = room.hashCode().toString() + "ChatroomViewModels",
            factory =
                NostrChatroomFeedViewModel.Factory(
                    room,
                    accountViewModel.account,
                ),
        )

    val newPostModel: NewPostViewModel = viewModel()
    newPostModel.accountViewModel = accountViewModel
    newPostModel.account = accountViewModel.account
    newPostModel.requiresNIP24 = room.users.size > 1
    if (newPostModel.requiresNIP24) {
        newPostModel.nip24 = true
    }

    LaunchedEffect(key1 = newPostModel) {
        launch(Dispatchers.IO) {
            val hasNIP24 =
                accountViewModel.userProfile().privateChatrooms[room]?.roomMessages?.any {
                    it.event is ChatMessageEvent &&
                        (it.event as ChatMessageEvent).pubKey != accountViewModel.userProfile().pubkeyHex
                }
            if (hasNIP24 == true && newPostModel.nip24 == false) {
                newPostModel.nip24 = true
            }
        }
    }

    if (draftMessage != null) {
        LaunchedEffect(key1 = draftMessage) { newPostModel.message = TextFieldValue(draftMessage) }
    }

    ChatroomScreen(
        room = room,
        feedViewModel = feedViewModel,
        newPostModel = newPostModel,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

@Composable
fun ChatroomScreen(
    room: ChatroomKey,
    feedViewModel: NostrChatroomFeedViewModel,
    newPostModel: NewPostViewModel,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val context = LocalContext.current

    NostrChatroomDataSource.loadMessagesBetween(accountViewModel.account, room)

    val lifeCycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(room, accountViewModel) {
        launch(Dispatchers.IO) {
            newPostModel.imageUploadingError.collect { error ->
                withContext(Dispatchers.Main) { Toast.makeText(context, error, Toast.LENGTH_SHORT).show() }
            }
        }
    }

    DisposableEffect(room, accountViewModel) {
        NostrChatroomDataSource.loadMessagesBetween(accountViewModel.account, room)
        NostrChatroomDataSource.start()
        feedViewModel.invalidateData()

        onDispose { NostrChatroomDataSource.stop() }
    }

    DisposableEffect(lifeCycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    println("Private Message Start")
                    NostrChatroomDataSource.start()
                    feedViewModel.invalidateData()
                }
                if (event == Lifecycle.Event.ON_PAUSE) {
                    println("Private Message Stop")
                    NostrChatroomDataSource.stop()
                }
            }

        lifeCycleOwner.lifecycle.addObserver(observer)
        onDispose { lifeCycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(Modifier.fillMaxHeight()) {
        val replyTo = remember { mutableStateOf<Note?>(null) }
        Column(
            modifier = Modifier.fillMaxHeight().padding(vertical = 0.dp).weight(1f, true),
        ) {
            RefreshingChatroomFeedView(
                viewModel = feedViewModel,
                accountViewModel = accountViewModel,
                nav = nav,
                routeForLastRead = "Room/${room.hashCode()}",
                avoidDraft = newPostModel.draftTag,
                onWantsToReply = {
                    replyTo.value = it
                },
                onWantsToEditDraft = {
                    newPostModel.load(accountViewModel, null, null, null, null, it)
                },
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        replyTo.value?.let { DisplayReplyingToNote(it, accountViewModel, nav) { replyTo.value = null } }

        val scope = rememberCoroutineScope()

        LaunchedEffect(key1 = newPostModel.draftTag) {
            launch(Dispatchers.IO) {
                newPostModel.draftTextChanges
                    .receiveAsFlow()
                    .debounce(1000)
                    .collectLatest {
                        innerSendPost(newPostModel, room, replyTo, accountViewModel, newPostModel.draftTag)
                    }
            }
        }

        // LAST ROW
        PrivateMessageEditFieldRow(newPostModel, isPrivate = true, accountViewModel) {
            scope.launch(Dispatchers.IO) {
                innerSendPost(newPostModel, room, replyTo, accountViewModel, null)

                accountViewModel.deleteDraft(newPostModel.draftTag)

                newPostModel.message = TextFieldValue("")

                replyTo.value = null
                feedViewModel.sendToTop()
            }
        }
    }
}

private fun innerSendPost(
    newPostModel: NewPostViewModel,
    room: ChatroomKey,
    replyTo: MutableState<Note?>,
    accountViewModel: AccountViewModel,
    dTag: String?,
) {
    val urls = findURLs(newPostModel.message.text)
    val usedAttachments = newPostModel.nip94attachments.filter { it.urls().intersect(urls.toSet()).isNotEmpty() }

    if (newPostModel.nip24 || room.users.size > 1 || replyTo.value?.event is ChatMessageEvent) {
        accountViewModel.account.sendNIP24PrivateMessage(
            message = newPostModel.message.text,
            toUsers = room.users.toList(),
            replyingTo = replyTo.value,
            mentions = null,
            wantsToMarkAsSensitive = false,
            nip94attachments = usedAttachments,
            draftTag = dTag,
        )
    } else {
        accountViewModel.account.sendPrivateMessage(
            message = newPostModel.message.text,
            toUser = room.users.first(),
            replyingTo = replyTo.value,
            mentions = null,
            wantsToMarkAsSensitive = false,
            nip94attachments = usedAttachments,
            draftTag = dTag,
        )
    }
}

@Composable
fun PrivateMessageEditFieldRow(
    channelScreenModel: NewPostViewModel,
    isPrivate: Boolean,
    accountViewModel: AccountViewModel,
    onSendNewMessage: () -> Unit,
) {
    Column(
        modifier = EditFieldModifier,
    ) {
        val context = LocalContext.current

        ShowUserSuggestionList(channelScreenModel, accountViewModel)

        MyTextField(
            value = channelScreenModel.message,
            onValueChange = { channelScreenModel.updateMessage(it) },
            keyboardOptions =
                KeyboardOptions.Default.copy(
                    capitalization = KeyboardCapitalization.Sentences,
                ),
            shape = EditFieldBorder,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = stringResource(R.string.reply_here),
                    color = MaterialTheme.colorScheme.placeholderText,
                )
            },
            trailingIcon = {
                ThinSendButton(
                    isActive =
                        channelScreenModel.message.text.isNotBlank() && !channelScreenModel.isUploadingImage,
                    modifier = EditFieldTrailingIconModifier,
                ) {
                    onSendNewMessage()
                }
            },
            leadingIcon = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 6.dp),
                ) {
                    UploadFromGallery(
                        isUploading = channelScreenModel.isUploadingImage,
                        tint = MaterialTheme.colorScheme.placeholderText,
                        modifier = Modifier.size(30.dp).padding(start = 2.dp),
                    ) {
                        channelScreenModel.upload(
                            galleryUri = it,
                            alt = null,
                            sensitiveContent = false,
                            isPrivate = isPrivate,
                            server = ServerOption(accountViewModel.account.defaultFileServer, false),
                            context = context,
                        )
                    }

                    var wantsToActivateNIP24 by remember { mutableStateOf(false) }

                    if (wantsToActivateNIP24) {
                        NewFeatureNIP24AlertDialog(
                            accountViewModel = accountViewModel,
                            onConfirm = { channelScreenModel.toggleNIP04And24() },
                            onDismiss = { wantsToActivateNIP24 = false },
                        )
                    }

                    IconButton(
                        modifier = Size30Modifier,
                        onClick = {
                            if (
                                !accountViewModel.hideNIP24WarningDialog &&
                                !channelScreenModel.nip24 &&
                                !channelScreenModel.requiresNIP24
                            ) {
                                wantsToActivateNIP24 = true
                            } else {
                                channelScreenModel.toggleNIP04And24()
                            }
                        },
                    ) {
                        if (channelScreenModel.nip24) {
                            IncognitoIconOn(
                                modifier =
                                    Modifier
                                        .padding(top = 2.dp)
                                        .size(18.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            IncognitoIconOff(
                                modifier =
                                    Modifier
                                        .padding(top = 2.dp)
                                        .size(18.dp),
                                tint = MaterialTheme.colorScheme.placeholderText,
                            )
                        }
                    }
                }
            },
            colors =
                TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            visualTransformation = UrlUserTagTransformation(MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
fun ShowUserSuggestionList(
    channelScreenModel: NewPostViewModel,
    accountViewModel: AccountViewModel,
    modifier: Modifier = Modifier.heightIn(0.dp, 200.dp),
) {
    val userSuggestions = channelScreenModel.userSuggestions
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
                UserLine(item, accountViewModel) { channelScreenModel.autocompleteWithUser(item) }
                HorizontalDivider(
                    thickness = DividerThickness,
                )
            }
        }
    }
}

@Composable
fun NewFeatureNIP24AlertDialog(
    accountViewModel: AccountViewModel,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    QuickActionAlertDialog(
        title = stringResource(R.string.new_feature_nip24_might_not_be_available_title),
        textContent = stringResource(R.string.new_feature_nip24_might_not_be_available_description),
        buttonIconResource = R.drawable.incognito,
        buttonText = stringResource(R.string.new_feature_nip24_activate),
        onClickDoOnce = {
            scope.launch(Dispatchers.IO) { onConfirm() }
            onDismiss()
        },
        onClickDontShowAgain = {
            scope.launch(Dispatchers.IO) {
                onConfirm()
                accountViewModel.dontShowNIP24WarningDialog()
            }
            onDismiss()
        },
        onDismiss = onDismiss,
    )
}

@Composable
fun ThinSendButton(
    isActive: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    IconButton(
        enabled = isActive,
        modifier = modifier,
        onClick = onClick,
    ) {
        Icon(
            imageVector = Icons.Default.Send,
            contentDescription = stringResource(id = R.string.accessibility_send),
            modifier = Size20Modifier,
        )
    }
}

@Composable
fun ChatroomHeader(
    room: ChatroomKey,
    modifier: Modifier = StdPadding,
    accountViewModel: AccountViewModel,
    onClick: () -> Unit,
) {
    if (room.users.size == 1) {
        LoadUser(baseUserHex = room.users.first(), accountViewModel) { baseUser ->
            if (baseUser != null) {
                ChatroomHeader(
                    baseUser = baseUser,
                    modifier = modifier,
                    accountViewModel = accountViewModel,
                    onClick = onClick,
                )
            }
        }
    } else {
        GroupChatroomHeader(
            room = room,
            modifier = modifier,
            accountViewModel = accountViewModel,
            onClick = onClick,
        )
    }
}

@Composable
fun ChatroomHeader(
    baseUser: User,
    modifier: Modifier = StdPadding,
    accountViewModel: AccountViewModel,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clickable(
                    onClick = onClick,
                ),
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            modifier = modifier,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ClickableUserPicture(
                    baseUser = baseUser,
                    accountViewModel = accountViewModel,
                    size = Size34dp,
                )

                Column(modifier = Modifier.padding(start = 10.dp)) {
                    UsernameDisplay(baseUser)
                }
            }
        }
    }
}

@Composable
fun GroupChatroomHeader(
    room: ChatroomKey,
    modifier: Modifier = StdPadding,
    accountViewModel: AccountViewModel,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            modifier = modifier,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NonClickableUserPictures(
                    users = room.users,
                    accountViewModel = accountViewModel,
                    size = Size34dp,
                )

                Column(modifier = Modifier.padding(start = 10.dp)) {
                    RoomNameOnlyDisplay(room, Modifier, FontWeight.Bold, accountViewModel.userProfile())
                    DisplayUserSetAsSubject(room, accountViewModel, FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
private fun EditRoomSubjectButton(
    room: ChatroomKey,
    accountViewModel: AccountViewModel,
) {
    var wantsToPost by remember { mutableStateOf(false) }

    if (wantsToPost) {
        NewSubjectView({ wantsToPost = false }, accountViewModel, room)
    }

    Button(
        modifier = Modifier.padding(horizontal = 3.dp).width(50.dp),
        onClick = { wantsToPost = true },
        contentPadding = ZeroPadding,
    ) {
        Icon(
            tint = Color.White,
            imageVector = Icons.Default.EditNote,
            contentDescription = stringResource(R.string.edits_the_channel_metadata),
        )
    }
}

@Composable
fun NewSubjectView(
    onClose: () -> Unit,
    accountViewModel: AccountViewModel,
    room: ChatroomKey,
) {
    Dialog(
        onDismissRequest = { onClose() },
        properties =
            DialogProperties(
                dismissOnClickOutside = false,
            ),
    ) {
        Surface {
            val groupName =
                remember {
                    mutableStateOf<String>(accountViewModel.userProfile().privateChatrooms[room]?.subject ?: "")
                }
            val message = remember { mutableStateOf<String>("") }
            val scope = rememberCoroutineScope()

            Column(
                modifier = Modifier.padding(10.dp).verticalScroll(rememberScrollState()),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CloseButton(onPress = { onClose() })

                    PostButton(
                        onPost = {
                            scope.launch(Dispatchers.IO) {
                                accountViewModel.account.sendNIP24PrivateMessage(
                                    message = message.value,
                                    toUsers = room.users.toList(),
                                    subject = groupName.value.ifBlank { null },
                                    replyingTo = null,
                                    mentions = null,
                                    wantsToMarkAsSensitive = false,
                                )
                            }

                            onClose()
                        },
                        true,
                    )
                }

                Spacer(modifier = Modifier.height(15.dp))

                OutlinedTextField(
                    label = { Text(text = stringResource(R.string.messages_new_message_subject)) },
                    modifier = Modifier.fillMaxWidth(),
                    value = groupName.value,
                    onValueChange = { groupName.value = it },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.messages_new_message_subject_caption),
                            color = MaterialTheme.colorScheme.placeholderText,
                        )
                    },
                    keyboardOptions =
                        KeyboardOptions.Default.copy(
                            capitalization = KeyboardCapitalization.Sentences,
                        ),
                    textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
                )

                Spacer(modifier = Modifier.height(15.dp))

                OutlinedTextField(
                    label = { Text(text = stringResource(R.string.messages_new_subject_message)) },
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    value = message.value,
                    onValueChange = { message.value = it },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.messages_new_subject_message_placeholder),
                            color = MaterialTheme.colorScheme.placeholderText,
                        )
                    },
                    keyboardOptions =
                        KeyboardOptions.Default.copy(
                            capitalization = KeyboardCapitalization.Sentences,
                        ),
                    textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
                    maxLines = 10,
                )
            }
        }
    }
}

@Composable
fun LongRoomHeader(
    room: ChatroomKey,
    lineModifier: Modifier = StdPadding,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val list = remember(room) { room.users.toPersistentList() }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(id = R.string.messages_group_descriptor),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
        )

        EditRoomSubjectButton(room, accountViewModel)
    }

    LazyColumn(
        modifier = Modifier,
        state = rememberLazyListState(),
    ) {
        itemsIndexed(list, key = { _, item -> item }) { _, item ->
            LoadUser(baseUserHex = item, accountViewModel) {
                if (it != null) {
                    UserCompose(
                        baseUser = it,
                        overallModifier = lineModifier,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                    HorizontalDivider(
                        thickness = DividerThickness,
                    )
                }
            }
        }
    }
}

@Composable
fun RoomNameOnlyDisplay(
    room: ChatroomKey,
    modifier: Modifier,
    fontWeight: FontWeight = FontWeight.Bold,
    loggedInUser: User,
) {
    val roomSubject by
        loggedInUser
            .live()
            .messages
            .map { it.user.privateChatrooms[room]?.subject }
            .distinctUntilChanged()
            .observeAsState(loggedInUser.privateChatrooms[room]?.subject)

    Crossfade(targetState = roomSubject, modifier) {
        if (it != null && it.isNotBlank()) {
            DisplayRoomSubject(it, fontWeight)
        }
    }
}
