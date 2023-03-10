package com.vitorpamplona.amethyst.ui.screen.loggedIn

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
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
import androidx.navigation.NavController
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.RoboHashCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.NostrChatroomDataSource
import com.vitorpamplona.amethyst.ui.actions.PostButton
import com.vitorpamplona.amethyst.ui.components.AsyncImageProxy
import com.vitorpamplona.amethyst.ui.components.ObserveDisplayNip05Status
import com.vitorpamplona.amethyst.ui.components.ResizeImage
import com.vitorpamplona.amethyst.ui.dal.ChatroomFeedFilter
import com.vitorpamplona.amethyst.ui.note.ChatroomMessageCompose
import com.vitorpamplona.amethyst.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.ui.screen.ChatroomFeedView
import com.vitorpamplona.amethyst.ui.screen.NostrChatRoomFeedViewModel

@Composable
fun ChatroomScreen(userId: String?, accountViewModel: AccountViewModel, navController: NavController) {
    val accountState by accountViewModel.accountLiveData.observeAsState()
    val account = accountState?.account

    if (account != null && userId != null) {
        val newPost = remember { mutableStateOf(TextFieldValue("")) }
        val replyTo = remember { mutableStateOf<Note?>(null) }

        ChatroomFeedFilter.loadMessagesBetween(account, userId)
        NostrChatroomDataSource.loadMessagesBetween(account, userId)

        val feedViewModel: NostrChatRoomFeedViewModel = viewModel()
        val lifeCycleOwner = LocalLifecycleOwner.current

        LaunchedEffect(userId) {
            feedViewModel.refresh()
        }

        DisposableEffect(userId) {
            val observer = LifecycleEventObserver { source, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    println("Private Message Start")
                    NostrChatroomDataSource.start()
                    feedViewModel.refresh()
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
                ChatroomHeader(
                    it,
                    accountViewModel = accountViewModel,
                    navController = navController
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(vertical = 0.dp)
                    .weight(1f, true)
            ) {
                ChatroomFeedView(feedViewModel, accountViewModel, navController, "Room/$userId") {
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
                            navController = navController,
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
                    value = newPost.value,
                    onValueChange = { newPost.value = it },
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
                                account.sendPrivateMeesage(newPost.value.text, userId, replyTo.value)
                                newPost.value = TextFieldValue("")
                                replyTo.value = null
                                feedViewModel.refresh() // Don't wait a full second before updating
                            },
                            newPost.value.text.isNotBlank(),
                            modifier = Modifier.padding(end = 10.dp)
                        )
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
fun ChatroomHeader(baseUser: User, accountViewModel: AccountViewModel, navController: NavController) {
    val ctx = LocalContext.current.applicationContext

    Column(
        modifier = Modifier.clickable(
            onClick = { navController.navigate("User/${baseUser.pubkeyHex}") }
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val authorState by baseUser.live().metadata.observeAsState()
                val author = authorState?.user!!

                AsyncImageProxy(
                    model = ResizeImage(author.profilePicture(), 35.dp),
                    placeholder = BitmapPainter(RoboHashCache.get(ctx)),
                    fallback = BitmapPainter(RoboHashCache.get(ctx)),
                    error = BitmapPainter(RoboHashCache.get(ctx)),
                    contentDescription = stringResource(id = R.string.profile_image),
                    modifier = Modifier
                        .width(35.dp)
                        .height(35.dp)
                        .clip(shape = CircleShape)
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
