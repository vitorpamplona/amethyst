package com.vitorpamplona.amethyst.ui.note

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.model.LnZapEvent
import com.vitorpamplona.amethyst.service.model.LnZapRequestEvent
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.FollowButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.ShowUserButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.UnfollowButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.showAmountAxis
import com.vitorpamplona.amethyst.ui.theme.BitcoinOrange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun ZapNoteCompose(baseNote: Pair<Note, Note>, accountViewModel: AccountViewModel, nav: (String) -> Unit) {
    val baseNoteRequest by baseNote.first.live().metadata.observeAsState()
    val noteZapRequest = remember(baseNoteRequest) { baseNoteRequest?.note } ?: return

    var baseAuthor by remember {
        mutableStateOf(noteZapRequest.author)
    }

    if (baseAuthor == null) {
        BlankNote()
    } else {
        Column(
            modifier =
            Modifier.clickable(
                onClick = { nav("User/${baseAuthor?.pubkeyHex}") }
            ),
            verticalArrangement = Arrangement.Center
        ) {
            LaunchedEffect(Unit) {
                launch(Dispatchers.Default) {
                    (noteZapRequest.event as? LnZapRequestEvent)?.let {
                        val decryptedContent = accountViewModel.decryptZap(noteZapRequest)
                        if (decryptedContent != null) {
                            baseAuthor = LocalCache.getOrCreateUser(decryptedContent.pubKey)
                        }
                    }
                }
            }

            baseAuthor?.let {
                RenderZapNote(it, baseNote.second, nav, accountViewModel)
            }

            Divider(
                modifier = Modifier.padding(top = 10.dp),
                thickness = 0.25.dp
            )
        }
    }
}

@Composable
private fun RenderZapNote(
    baseAuthor: User,
    zapNote: Note,
    nav: (String) -> Unit,
    accountViewModel: AccountViewModel
) {
    Row(
        modifier = Modifier
            .padding(
                start = 12.dp,
                end = 12.dp,
                top = 10.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserPicture(baseAuthor, nav, accountViewModel, 55.dp)

        Column(
            modifier = Modifier
                .padding(start = 10.dp)
                .weight(1f)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UsernameDisplay(baseAuthor)
            }

            AboutDisplay(baseAuthor)
        }

        Column(
            modifier = Modifier.padding(start = 10.dp),
            verticalArrangement = Arrangement.Center
        ) {
            ZapAmount(zapNote)
        }

        Column(modifier = Modifier.padding(start = 10.dp)) {
            UserActionOptions(baseAuthor, accountViewModel)
        }
    }
}

@Composable
private fun ZapAmount(zapEventNote: Note) {
    val noteState by zapEventNote.live().metadata.observeAsState()
    val noteZap = remember(noteState) { noteState?.note } ?: return

    var zapAmount by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(key1 = noteZap) {
        launch(Dispatchers.IO) {
            zapAmount = showAmountAxis((noteZap.event as? LnZapEvent)?.amount)
        }
    }

    zapAmount?.let {
        Text(
            text = it,
            color = BitcoinOrange,
            fontSize = 20.sp,
            fontWeight = FontWeight.W500
        )
    }
}

@Composable
fun UserActionOptions(
    baseAuthor: User,
    accountViewModel: AccountViewModel
) {
    val coroutineScope = rememberCoroutineScope()

    val accountState by accountViewModel.accountLiveData.observeAsState()
    val isHidden = remember(accountState) { accountState?.account?.isHidden(baseAuthor) } ?: return

    val userState by accountViewModel.account.userProfile().live().follows.observeAsState()
    val isFollowing = remember(userState) { userState?.user?.isFollowingCached(baseAuthor) } ?: return

    if (isHidden) {
        ShowUserButton {
            accountViewModel.show(baseAuthor)
        }
    } else if (isFollowing) {
        UnfollowButton { coroutineScope.launch(Dispatchers.IO) { accountViewModel.unfollow(baseAuthor) } }
    } else {
        FollowButton({ coroutineScope.launch(Dispatchers.IO) { accountViewModel.follow(baseAuthor) } })
    }
}

@Composable
fun AboutDisplay(baseAuthor: User) {
    val baseAuthorState by baseAuthor.live().metadata.observeAsState()
    val userAboutMe by remember(baseAuthorState) {
        derivedStateOf {
            baseAuthorState?.user?.info?.about ?: ""
        }
    }

    Text(
        userAboutMe,
        color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}
