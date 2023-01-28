package com.vitorpamplona.amethyst.ui.screen

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.TabRowDefaults
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.pagerTabIndicatorOffset
import com.google.accompanist.pager.rememberPagerState
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.model.toNote
import com.vitorpamplona.amethyst.service.NostrUserProfileDataSource
import com.vitorpamplona.amethyst.service.NostrUserProfileFollowersDataSource
import com.vitorpamplona.amethyst.service.NostrUserProfileFollowsDataSource
import com.vitorpamplona.amethyst.ui.actions.NewChannelView
import com.vitorpamplona.amethyst.ui.actions.NewUserMetadataView
import com.vitorpamplona.amethyst.ui.note.UserPicture
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import kotlinx.coroutines.launch
import nostr.postr.toNpub
import nostr.postr.toNsec

@OptIn(ExperimentalPagerApi::class)
@Composable
fun ProfileScreen(userId: String?, accountViewModel: AccountViewModel, navController: NavController) {
    val accountState by accountViewModel.accountLiveData.observeAsState()
    val account = accountState?.account ?: return

    val accountUserState by account.userProfile().live.observeAsState()
    val accountUser = accountUserState?.user

    if (userId != null && accountUser != null) {
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
                ProfileHeader(user, navController, account, accountUser, accountViewModel)

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
private fun ProfileHeader(
    user: User,
    navController: NavController,
    account: Account,
    accountUser: User,
    accountViewModel: AccountViewModel
) {
    val ctx = LocalContext.current.applicationContext
    var popupExpanded by remember { mutableStateOf(false) }

    Box {
        val banner = user.info.banner
        if (banner != null && banner.isNotBlank()) {
            AsyncImage(
                model = banner,
                contentDescription = "Profile Image",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(125.dp)
            )
        } else {
            Image(
                painter = painterResource(R.drawable.profile_banner),
                contentDescription = "Profile Banner",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(125.dp)
            )
        }

        Box(modifier = Modifier
            .padding(horizontal = 10.dp)
            .size(40.dp)
            .align(Alignment.TopEnd)) {

            Button(
                modifier = Modifier
                    .size(30.dp)
                    .align(Alignment.Center),
                onClick = { popupExpanded = true },
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults
                    .buttonColors(
                        backgroundColor = MaterialTheme.colors.background
                    ),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(
                    tint = Color.White,
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More Options",
                )

                UserProfileDropDownMenu(user, popupExpanded, { popupExpanded = false }, accountViewModel)
            }

        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
                .padding(top = 75.dp)
        ) {

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {

                UserPicture(
                    user, navController, account.userProfile(), 100.dp,
                    pictureModifier = Modifier.border(
                        3.dp,
                        MaterialTheme.colors.background,
                        CircleShape
                    )
                )

                Spacer(Modifier.weight(1f))

                Row(modifier = Modifier
                    .height(35.dp)
                    .padding(bottom = 3.dp)) {
                    MessageButton(user, navController)

                    if (accountUser == user && account.isWriteable()) {
                        NSecCopyButton(account)
                    }

                    NPubCopyButton(user)

                    if (accountUser == user) {
                        EditButton(account)
                    } else {
                        if (!account.isAcceptable(user)) {
                            ShowUserButton {
                                account.showUser(user.pubkeyHex)
                                LocalPreferences(ctx).saveToEncryptedStorage(account)
                            }
                        } else if (accountUser.isFollowing(user)) {
                            UnfollowButton { account.unfollow(user) }
                        } else {
                            FollowButton { account.follow(user) }
                        }
                    }
                }
            }

            Text(
                user.bestDisplayName() ?: "",
                modifier = Modifier.padding(top = 7.dp),
                fontWeight = FontWeight.Bold,
                fontSize = 25.sp
            )
            Text(
                " @${user.bestUsername()}",
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
            )
            Text(
                "${user.info.about}",
                color = MaterialTheme.colors.onSurface,
                modifier = Modifier.padding(top = 5.dp, bottom = 5.dp)
            )

            Divider(modifier = Modifier.padding(top = 6.dp))
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
                FeedView(feedViewModel, accountViewModel, navController, null)
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
private fun NSecCopyButton(
    account: Account
) {
    val clipboardManager = LocalClipboardManager.current

    Button(
        modifier = Modifier
            .padding(horizontal = 3.dp)
            .width(50.dp),
        onClick = { account.loggedIn.privKey?.let { clipboardManager.setText(AnnotatedString(it.toNsec())) } },
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults
            .buttonColors(
                backgroundColor = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
            ),
    ) {
        Icon(
            tint = Color.White,
            imageVector = Icons.Default.Password,
            contentDescription = "Copies the Nsec ID (your password) to the clipboard for backup"
        )
    }
}

@Composable
private fun NPubCopyButton(
    user: User
) {
    val clipboardManager = LocalClipboardManager.current

    Button(
        modifier = Modifier
            .padding(horizontal = 3.dp)
            .width(50.dp),
        onClick = { clipboardManager.setText(AnnotatedString(user.pubkey.toNpub())) },
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults
            .buttonColors(
                backgroundColor = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
            ),
    ) {
        Icon(
            tint = Color.White,
            imageVector = Icons.Default.Share,
            contentDescription = "Copies the Note ID to the clipboard for sharing"
        )
    }
}

@Composable
private fun MessageButton(user: User, navController: NavController) {
    Button(
        modifier = Modifier
            .padding(horizontal = 3.dp)
            .width(50.dp),
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
private fun EditButton(account: Account) {
    var wantsToEdit by remember {
        mutableStateOf(false)
    }

    if (wantsToEdit)
        NewUserMetadataView({ wantsToEdit = false }, account)

    Button(
        modifier = Modifier
            .padding(horizontal = 3.dp)
            .width(50.dp),
        onClick = { wantsToEdit = true },
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults
            .buttonColors(
                backgroundColor = MaterialTheme.colors.primary
            )
    ) {
        Icon(
            tint = Color.White,
            imageVector = Icons.Default.EditNote,
            contentDescription = "Edits the User's Metadata"
        )
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
            ),
        contentPadding = PaddingValues(vertical = 6.dp, horizontal = 16.dp)
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
            ),
        contentPadding = PaddingValues(vertical = 6.dp, horizontal = 16.dp)
    ) {
        Text(text = "Follow", color = Color.White, textAlign = TextAlign.Center)
    }
}

@Composable
fun ShowUserButton(onClick: () -> Unit) {
    Button(
        modifier = Modifier.padding(start = 3.dp),
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults
            .buttonColors(
                backgroundColor = MaterialTheme.colors.primary
            ),
        contentPadding = PaddingValues(vertical = 6.dp, horizontal = 16.dp)
    ) {
        Text(text = "Unblock", color = Color.White)
    }
}


@Composable
fun UserProfileDropDownMenu(user: User, popupExpanded: Boolean, onDismiss: () -> Unit, accountViewModel: AccountViewModel) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current.applicationContext

    val accountState by accountViewModel.accountLiveData.observeAsState()
    val account = accountState?.account ?: return

    DropdownMenu(
        expanded = popupExpanded,
        onDismissRequest = onDismiss
    ) {
        DropdownMenuItem(onClick = { clipboardManager.setText(AnnotatedString(user.pubkey.toNpub() ?: "")); onDismiss() }) {
            Text("Copy User ID")
        }
        Divider()
        if (!account.isAcceptable(user)) {
            DropdownMenuItem(onClick = {
                user.let {
                    accountViewModel.show(
                        it,
                        context
                    )
                }; onDismiss()
            }) {
                Text("Unblock User")
            }
        } else {
            DropdownMenuItem(onClick = { user.let { accountViewModel.hide(it, context) }; onDismiss() }) {
                Text("Block User")
            }
        }
    }
}