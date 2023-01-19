package com.vitorpamplona.amethyst.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.TabRowDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.pagerTabIndicatorOffset
import com.google.accompanist.pager.rememberPagerState
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.model.toNote
import com.vitorpamplona.amethyst.service.NostrUserProfileDataSource
import com.vitorpamplona.amethyst.service.NostrUserProfileFollowersDataSource
import com.vitorpamplona.amethyst.service.NostrUserProfileFollowsDataSource
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import kotlinx.coroutines.launch
import nostr.postr.toNpub

data class TabRowItem(
    val title: String,
    val screen: @Composable () -> Unit,
)

@OptIn(ExperimentalPagerApi::class)
@Composable
fun ProfileScreen(userId: String?, accountViewModel: AccountViewModel, navController: NavController) {
    val accountState by accountViewModel.accountLiveData.observeAsState()
    val account = accountState?.account ?: return

    val accountUserState by account.userProfile().live.observeAsState()
    val accountUser = accountUserState?.user

    if (userId != null && account != null && accountUser != null) {
        DisposableEffect(account) {
            NostrUserProfileDataSource.loadUserProfile(userId)
            NostrUserProfileFollowersDataSource.loadUserProfile(userId)
            NostrUserProfileFollowsDataSource.loadUserProfile(userId)

            onDispose {
                NostrUserProfileDataSource.stop()
                NostrUserProfileFollowsDataSource.stop()
                NostrUserProfileFollowersDataSource.stop()
            }
        }

        val baseUser = NostrUserProfileDataSource.user ?: return

        val userState by baseUser.live.observeAsState()
        val user = userState?.user ?: return

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colors.background
        ) {
            Column() {
                Box {
                    val banner = user.info.banner
                    if (banner != null && banner.isNotBlank()) {
                        AsyncImage(
                            model = banner,
                            contentDescription = "Profile Image",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth().height(125.dp)
                        )
                    } else {
                        Image(
                            painter = painterResource(R.drawable.profile_banner),
                            contentDescription = "Profile Banner",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth().height(125.dp)
                        )
                    }

                    Column(modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp)
                        .padding(top = 75.dp)) {

                        Row(horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom) {
                            AsyncImage(
                                model = user.profilePicture(),
                                contentDescription = "Profile Image",
                                modifier = Modifier
                                    .width(100.dp)
                                    .height(100.dp)
                                    .clip(shape = CircleShape)
                                    .border(3.dp, MaterialTheme.colors.background, CircleShape)
                                    .background(MaterialTheme.colors.background)
                            )

                            Spacer(Modifier.weight(1f))

                            MessageButton(user, navController)

                            NPubCopyButton(user)

                            if (accountUser == user) {
                                EditButton()
                            } else {
                                if (accountUser.isFollowing(user) == true) {
                                    UnfollowButton { account.unfollow(user) }
                                } else {
                                    FollowButton { account.follow(user) }
                                }
                            }
                        }

                        Text(
                            user.bestDisplayName() ?: "",
                            modifier = Modifier.padding(top = 7.dp),
                            fontWeight = FontWeight.Bold,
                            fontSize = 25.sp
                        )
                        Text(" @${user.bestUsername()}", color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f))
                        Text(
                            "${user.info.about}",
                            color = MaterialTheme.colors.onSurface,
                            modifier = Modifier.padding(top = 5.dp, bottom = 5.dp)
                        )

                        Divider(modifier = Modifier.padding(top = 6.dp))
                    }
                }

                val pagerState = rememberPagerState()
                val coroutineScope = rememberCoroutineScope()

                Column(modifier = Modifier.padding(start = 10.dp, end = 10.dp)) {
                    TabRow(
                        selectedTabIndex = pagerState.currentPage,
                        indicator = { tabPositions ->
                            TabRowDefaults.Indicator(
                                Modifier.pagerTabIndicatorOffset(pagerState, tabPositions),
                                color = MaterialTheme.colors.primary
                            )
                        },
                    ) {
                        Tab(
                            selected = pagerState.currentPage == 0,
                            onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                            text = {
                                Text(text = "Notes")
                            }
                        )

                        Tab(
                            selected = pagerState.currentPage == 1,
                            onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                            text = {
                                Text(text = "${user.follows?.size ?: "--"} Following")
                            }
                        )

                        Tab(
                            selected = pagerState.currentPage == 2,
                            onClick = { coroutineScope.launch { pagerState.animateScrollToPage(2) } },
                            text = {
                                Text(text = "${user.followers?.size ?: "--"} Followers")
                            }
                        )
                    }
                    HorizontalPager(count = 3, state = pagerState) {
                        when (pagerState.currentPage) {
                            0 -> TabNotes(user, accountViewModel, navController)
                            1 -> TabFollows(user, accountViewModel, navController)
                            2 -> TabFollowers(user, accountViewModel, navController)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TabNotes(user: User, accountViewModel: AccountViewModel, navController: NavController) {
    val accountState by accountViewModel.accountLiveData.observeAsState()
    if (accountState != null) {
        val feedViewModel: NostrUserProfileFeedViewModel = viewModel()

        Column(Modifier.fillMaxHeight()) {
            Column(
                modifier = Modifier.padding(vertical = 0.dp)
            ) {
                FeedView(feedViewModel, accountViewModel, navController)
            }
        }
    }
}

@Composable
fun TabFollows(user: User, accountViewModel: AccountViewModel, navController: NavController) {
    val feedViewModel: NostrUserProfileFollowsUserFeedViewModel = viewModel()

    Column(Modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier.padding(vertical = 0.dp)
        ) {
            UserFeedView(feedViewModel, accountViewModel, navController)
        }
    }
}

@Composable
fun TabFollowers(user: User, accountViewModel: AccountViewModel, navController: NavController) {
    val feedViewModel: NostrUserProfileFollowersUserFeedViewModel = viewModel()

    Column(Modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier.padding(vertical = 0.dp)
        ) {
            UserFeedView(feedViewModel, accountViewModel, navController)
        }
    }
}

@Composable
private fun NPubCopyButton(
    user: User
) {
    val clipboardManager = LocalClipboardManager.current

    Button(
        modifier = Modifier.padding(horizontal = 3.dp),
        onClick = { clipboardManager.setText(AnnotatedString(user.pubkey.toNpub())) },
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults
            .buttonColors(
                backgroundColor = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
            ),
    ) {
        Text(text = "npub", color = Color.White)
    }
}

@Composable
private fun MessageButton(user: User, navController: NavController) {
    Button(
        modifier = Modifier.padding(horizontal = 3.dp),
        onClick = { navController.navigate("Room/${user.pubkeyHex}") },
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults
            .buttonColors(
                backgroundColor = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
            ),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_dm),
            "Send a Direct Message",
            modifier = Modifier.size(20.dp),
            tint = Color.White
        )
    }
}

@Composable
private fun EditButton() {
    Button(
        modifier = Modifier.padding(horizontal = 3.dp),
        onClick = {},
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults
            .buttonColors(
                backgroundColor = MaterialTheme.colors.primary
            )
    ) {
        Text(text = "Edit", color = Color.White)
    }
}

@Composable
fun UnfollowButton(onClick: () -> Unit) {
    Button(
        modifier = Modifier.padding(horizontal = 3.dp),
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults
            .buttonColors(
                backgroundColor = MaterialTheme.colors.primary
            )
    ) {
        Text(text = "Unfollow", color = Color.White)
    }
}

@Composable
fun FollowButton(onClick: () -> Unit) {
    Button(
        modifier = Modifier.padding(start = 3.dp),
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults
            .buttonColors(
                backgroundColor = MaterialTheme.colors.primary
            )
    ) {
        Text(text = "Follow", color = Color.White)
    }
}


