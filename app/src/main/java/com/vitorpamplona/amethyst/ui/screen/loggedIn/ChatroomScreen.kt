package com.vitorpamplona.amethyst.ui.screen.loggedIn

import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.NostrChatroomDataSource
import com.vitorpamplona.amethyst.ui.actions.NewPostViewModel
import com.vitorpamplona.amethyst.ui.actions.PostButton
import com.vitorpamplona.amethyst.ui.actions.UploadFromGallery
import com.vitorpamplona.amethyst.ui.components.ObserveDisplayNip05Status
import com.vitorpamplona.amethyst.ui.dal.ChatroomFeedFilter
import com.vitorpamplona.amethyst.ui.note.ChatroomMessageCompose
import com.vitorpamplona.amethyst.ui.note.UserPicture
import com.vitorpamplona.amethyst.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.ui.screen.ChatroomFeedView
import com.vitorpamplona.amethyst.ui.screen.NostrChatRoomFeedViewModel

@Composable
fun ChatroomScreen(userId: String?, accountViewModel: AccountViewModel, nav: (String) -> Unit) {
    val accountState by accountViewModel.accountLiveData.observeAsState()
    val account = accountState?.account
    val context = LocalContext.current
    val chatRoomScreenModel: NewPostViewModel = viewModel()

    chatRoomScreenModel.account = account

    if (account != null && userId != null) {
        val replyTo = remember { mutableStateOf<Note?>(null) }

        ChatroomFeedFilter.loadMessagesBetween(account, userId)
        NostrChatroomDataSource.loadMessagesBetween(account, userId)

        val feedViewModel: NostrChatRoomFeedViewModel = viewModel()
        val lifeCycleOwner = LocalLifecycleOwner.current

        LaunchedEffect(userId) {
            feedViewModel.invalidateData()
            chatRoomScreenModel.imageUploadingError.collect { error ->
                Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
            }
        }

        DisposableEffect(userId) {
            val observer = LifecycleEventObserver { _, event ->
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
            onDispose {
                lifeCycleOwner.lifecycle.removeObserver(observer)
            }
        }

        Column(Modifier.fillMaxHeight()) {
            NostrChatroomDataSource.withUser?.let {
                ChatroomHeader(it, account.userProfile(), nav = nav)
            }

            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(vertical = 0.dp)
                    .weight(1f, true)
            ) {
                ChatroomFeedView(feedViewModel, accountViewModel, nav, "Room/$userId") {
                    replyTo.value = it
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(Modifier.padding(horizontal = 10.dp).animateContentSize(), verticalAlignment = Alignment.CenterVertically) {
                val replyingNote = replyTo.value
                if (replyingNote != null) {
                    Column(Modifier.weight(1f)) {
                        ChatroomMessageCompose(
                            baseNote = replyingNote,
                            null,
                            innerQuote = true,
                            accountViewModel = accountViewModel,
                            nav = nav,
                            onWantsToReply = {
                                replyTo.value = it
                            }
                        )
                    }

                    Column(Modifier.padding(end = 10.dp)) {
                        IconButton(
                            modifier = Modifier.size(30.dp),
                            onClick = { replyTo.value = null }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cancel,
                                null,
                                modifier = Modifier.padding(end = 5.dp).size(30.dp),
                                tint = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                            )
                        }
                    }
                }
            }

            // LAST ROW
            Row(
                modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp, top = 5.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = chatRoomScreenModel.message,
                    onValueChange = { chatRoomScreenModel.updateMessage(it) },
                    keyboardOptions = KeyboardOptions.Default.copy(
                        capitalization = KeyboardCapitalization.Sentences
                    ),
                    modifier = Modifier.weight(1f, true),
                    shape = RoundedCornerShape(25.dp),
                    placeholder = {
                        Text(
                            text = stringResource(id = R.string.reply_here),
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                        )
                    },
                    textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
                    trailingIcon = {
                        PostButton(
                            onPost = {
                                account.sendPrivateMessage(chatRoomScreenModel.message.text, userId, replyTo.value, null, wantsToMarkAsSensitive = false)
                                chatRoomScreenModel.message = TextFieldValue("")
                                replyTo.value = null
                                feedViewModel.invalidateData() // Don't wait a full second before updating
                            },
                            isActive = chatRoomScreenModel.message.text.isNotBlank() && !chatRoomScreenModel.isUploadingImage,
                            modifier = Modifier.padding(end = 10.dp)
                        )
                    },
                    leadingIcon = {
                        UploadFromGallery(
                            isUploading = chatRoomScreenModel.isUploadingImage,
                            tint = MaterialTheme.colors.onSurface.copy(alpha = 0.32f),
                            modifier = Modifier.padding(start = 5.dp)
                        ) {
                            chatRoomScreenModel.upload(it, "", account.defaultFileServer, context)
                        }
                    },
                    colors = TextFieldDefaults.textFieldColors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
            }
        }
    }
}

@Composable
fun ChatroomHeader(baseUser: User, accountUser: User, nav: (String) -> Unit) {
    Column(
        modifier = Modifier.clickable(
            onClick = { nav("User/${baseUser.pubkeyHex}") }
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UserPicture(
                    baseUser = baseUser,
                    baseUserAccount = accountUser,
                    size = 35.dp
                )

                Column(modifier = Modifier.padding(start = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        UsernameDisplay(baseUser)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ObserveDisplayNip05Status(baseUser)
                    }
                }
            }
        }

        Divider(
            modifier = Modifier.padding(start = 12.dp, end = 12.dp),
            thickness = 0.25.dp
        )
    }
}
