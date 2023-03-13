package com.vitorpamplona.amethyst.ui.note

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.FollowButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.ShowUserButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.UnfollowButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun UserCompose(baseUser: User, accountViewModel: AccountViewModel, navController: NavController) {
    val accountState by accountViewModel.accountLiveData.observeAsState()
    val account = accountState?.account ?: return

    val userState by account.userProfile().live().follows.observeAsState()
    val userFollows = userState?.user ?: return

    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier =
        Modifier.clickable(
            onClick = { navController.navigate("User/${baseUser.pubkeyHex}") }
        )
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
            UserPicture(baseUser, navController, account.userProfile(), 55.dp)

            Column(modifier = Modifier.padding(start = 10.dp).weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UsernameDisplay(baseUser)
                }

                val baseUserState by baseUser.live().metadata.observeAsState()
                val user = baseUserState?.user ?: return

                Text(
                    user.info?.about ?: "",
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Column(modifier = Modifier.padding(start = 10.dp)) {
                if (account.isHidden(baseUser)) {
                    ShowUserButton {
                        account.showUser(baseUser.pubkeyHex)
                    }
                } else if (userFollows.isFollowing(baseUser)) {
                    UnfollowButton { coroutineScope.launch(Dispatchers.IO) { account.unfollow(baseUser) } }
                } else {
                    FollowButton { coroutineScope.launch(Dispatchers.IO) { account.follow(baseUser) } }
                }
            }
        }

        Divider(
            modifier = Modifier.padding(top = 10.dp),
            thickness = 0.25.dp
        )
    }
}
