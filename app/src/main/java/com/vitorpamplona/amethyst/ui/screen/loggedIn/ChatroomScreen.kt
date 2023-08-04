package com.vitorpamplona.amethyst.ui.screen.loggedIn

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.NostrChatroomDataSource
import com.vitorpamplona.amethyst.ui.actions.NewPostViewModel
import com.vitorpamplona.amethyst.ui.components.ObserveDisplayNip05Status
import com.vitorpamplona.amethyst.ui.note.ClickableUserPicture
import com.vitorpamplona.amethyst.ui.note.LoadUser
import com.vitorpamplona.amethyst.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.ui.screen.NostrChatroomFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.RefreshingChatroomFeedView
import com.vitorpamplona.amethyst.ui.theme.Size34dp
import com.vitorpamplona.amethyst.ui.theme.StdPadding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ChatroomScreen(
    userId: String?,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    if (userId == null) return

    LoadUser(userId) {
        it?.let {
            PrepareChatroomViewModels(
                baseUser = it,
                accountViewModel = accountViewModel,
                nav = nav
            )
        }
    }
}

@Composable
fun PrepareChatroomViewModels(baseUser: User, accountViewModel: AccountViewModel, nav: (String) -> Unit) {
    val feedViewModel: NostrChatroomFeedViewModel = viewModel(
        key = baseUser.pubkeyHex + "ChatroomViewModels",
        factory = NostrChatroomFeedViewModel.Factory(
            baseUser,
            accountViewModel.account
        )
    )

    val newPostModel: NewPostViewModel = viewModel()
    newPostModel.account = accountViewModel.account

    ChatroomScreen(
        baseUser = baseUser,
        feedViewModel = feedViewModel,
        newPostModel = newPostModel,
        accountViewModel = accountViewModel,
        nav = nav
    )
}

@Composable
fun ChatroomScreen(
    baseUser: User,
    feedViewModel: NostrChatroomFeedViewModel,
    newPostModel: NewPostViewModel,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    val context = LocalContext.current

    NostrChatroomDataSource.loadMessagesBetween(accountViewModel.account, baseUser)

    val lifeCycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(baseUser, accountViewModel) {
        launch(Dispatchers.IO) {
            NostrChatroomDataSource.start()
            feedViewModel.invalidateData()

            newPostModel.imageUploadingError.collect { error ->
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    DisposableEffect(baseUser, accountViewModel) {
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
        val replyTo = remember { mutableStateOf<Note?>(null) }
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 0.dp)
                .weight(1f, true)
        ) {
            RefreshingChatroomFeedView(
                viewModel = feedViewModel,
                accountViewModel = accountViewModel,
                nav = nav,
                routeForLastRead = "Room/${baseUser.pubkeyHex}",
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
        EditFieldRow(newPostModel, isPrivate = true, accountViewModel) {
            scope.launch(Dispatchers.IO) {
                accountViewModel.account.sendPrivateMessage(
                    message = newPostModel.message.text,
                    toUser = baseUser,
                    replyingTo = replyTo.value,
                    mentions = null,
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
fun ChatroomHeader(
    baseUser: User,
    modifier: Modifier = StdPadding,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().clickable(
            onClick = { nav("User/${baseUser.pubkeyHex}") }
        )
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            modifier = modifier
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ClickableUserPicture(
                    baseUser = baseUser,
                    accountViewModel = accountViewModel,
                    size = Size34dp
                )

                Column(modifier = Modifier.padding(start = 10.dp)) {
                    UsernameDisplay(baseUser)
                    ObserveDisplayNip05Status(baseUser)
                }
            }
        }

        Divider(
            thickness = 0.25.dp
        )
    }
}
