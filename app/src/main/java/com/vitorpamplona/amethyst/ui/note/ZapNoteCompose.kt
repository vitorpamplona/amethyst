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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.model.LnZapEvent
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.FollowButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.ShowUserButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.UnfollowButton
import com.vitorpamplona.amethyst.ui.theme.BitcoinOrange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal

@Composable
fun ZapNoteCompose(baseNote: Pair<Note, Note>, accountViewModel: AccountViewModel, navController: NavController) {
    val accountState by accountViewModel.accountLiveData.observeAsState()
    val account = accountState?.account ?: return

    val userState by account.userProfile().live().follows.observeAsState()
    val userFollows = userState?.user ?: return

    val noteState by baseNote.second.live().metadata.observeAsState()
    val noteZap = noteState?.note ?: return

    val baseNoteRequest by baseNote.first.live().metadata.observeAsState()
    val noteZapRequest = baseNoteRequest?.note ?: return

    val baseAuthor = noteZapRequest.author

    val coroutineScope = rememberCoroutineScope()

    if (baseAuthor == null) {
        BlankNote()
    } else {
        Column(
            modifier =
            Modifier.clickable(
                onClick = { navController.navigate("User/${baseAuthor.pubkeyHex}") }
            ),
            verticalArrangement = Arrangement.Center
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
                UserPicture(baseAuthor, navController, account.userProfile(), 55.dp)

                Column(modifier = Modifier.padding(start = 10.dp).weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        UsernameDisplay(baseAuthor)
                    }

                    val baseAuthorState by baseAuthor.live().metadata.observeAsState()
                    val user = baseAuthorState?.user ?: return

                    Text(
                        user.info?.about ?: "",
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                var zapAmount by remember { mutableStateOf<BigDecimal?>(null) }

                LaunchedEffect(key1 = noteZap) {
                    withContext(Dispatchers.IO) {
                        zapAmount = (noteZap.event as? LnZapEvent)?.amount
                    }
                }

                Column(
                    modifier = Modifier.padding(start = 10.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        "${showAmount(zapAmount)} ${stringResource(R.string.sats)}",
                        color = BitcoinOrange,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.W500
                    )
                }

                Column(modifier = Modifier.padding(start = 10.dp)) {
                    if (account.isHidden(baseAuthor)) {
                        ShowUserButton {
                            account.showUser(baseAuthor.pubkeyHex)
                        }
                    } else if (userFollows.isFollowing(baseAuthor)) {
                        UnfollowButton { coroutineScope.launch(Dispatchers.IO) { account.unfollow(baseAuthor) } }
                    } else {
                        FollowButton { coroutineScope.launch(Dispatchers.IO) { account.follow(baseAuthor) } }
                    }
                }
            }

            Divider(
                modifier = Modifier.padding(top = 10.dp),
                thickness = 0.25.dp
            )
        }
    }
}
