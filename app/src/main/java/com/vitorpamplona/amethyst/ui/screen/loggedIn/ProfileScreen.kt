package com.vitorpamplona.amethyst.ui.screen

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.pagerTabIndicatorOffset
import com.google.accompanist.pager.rememberPagerState
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.NostrUserProfileDataSource
import com.vitorpamplona.amethyst.service.model.ReportEvent
import com.vitorpamplona.amethyst.ui.components.AsyncImageProxy
import com.vitorpamplona.amethyst.ui.components.ResizeImage
import com.vitorpamplona.amethyst.ui.actions.NewUserMetadataView
import com.vitorpamplona.amethyst.ui.components.InvoiceRequest
import com.vitorpamplona.amethyst.ui.dal.UserProfileFollowersFeedFilter
import com.vitorpamplona.amethyst.ui.dal.UserProfileFollowsFeedFilter
import com.vitorpamplona.amethyst.ui.dal.UserProfileConversationsFeedFilter
import com.vitorpamplona.amethyst.ui.dal.UserProfileNewThreadFeedFilter
import com.vitorpamplona.amethyst.ui.dal.UserProfileReportsFeedFilter
import com.vitorpamplona.amethyst.ui.dal.UserProfileZapsFeedFilter
import com.vitorpamplona.amethyst.ui.note.UserPicture
import com.vitorpamplona.amethyst.ui.note.showAmount
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.BitcoinOrange
import kotlinx.coroutines.launch
import nostr.postr.toNsec

@OptIn(ExperimentalPagerApi::class)
@Composable
fun ProfileScreen(userId: String?, accountViewModel: AccountViewModel, navController: NavController) {
    val accountState by accountViewModel.accountLiveData.observeAsState()
    val account = accountState?.account ?: return

    if (userId == null) return

    UserProfileNewThreadFeedFilter.loadUserProfile(account, userId)
    UserProfileConversationsFeedFilter.loadUserProfile(account, userId)
    UserProfileFollowersFeedFilter.loadUserProfile(account, userId)
    UserProfileFollowsFeedFilter.loadUserProfile(account, userId)
    UserProfileZapsFeedFilter.loadUserProfile(userId)
    UserProfileReportsFeedFilter.loadUserProfile(userId)

    NostrUserProfileDataSource.loadUserProfile(userId)

    val lifeCycleOwner = LocalLifecycleOwner.current

    DisposableEffect(accountViewModel) {
        val observer = LifecycleEventObserver { source, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                println("Profile Start")
                NostrUserProfileDataSource.loadUserProfile(userId)
                NostrUserProfileDataSource.start()
            }
            if (event == Lifecycle.Event.ON_PAUSE) {
                println("Profile Stop")
                NostrUserProfileDataSource.loadUserProfile(null)
                NostrUserProfileDataSource.stop()
            }
        }

        lifeCycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifeCycleOwner.lifecycle.removeObserver(observer)
            println("Profile Dispose")
            NostrUserProfileDataSource.loadUserProfile(null)
            NostrUserProfileDataSource.stop()
        }
    }

    val baseUser = NostrUserProfileDataSource.user ?: return

    var columnSize by remember { mutableStateOf(IntSize.Zero) }
    var tabsSize by remember { mutableStateOf(IntSize.Zero) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colors.background
    ) {
        val pagerState = rememberPagerState()
        val coroutineScope = rememberCoroutineScope()
        val scrollState = rememberScrollState()

        Column(modifier = Modifier
            .fillMaxSize()
            .onSizeChanged {
                columnSize = it
            }
        ) {
            Box(
                modifier = Modifier
                    .verticalScroll(scrollState)
                    .nestedScroll(object : NestedScrollConnection {
                        override fun onPreScroll(
                            available: Offset,
                            source: NestedScrollSource
                        ): Offset {
                            // When scrolling vertically, scroll the container first.
                            return if (available.y < 0 && scrollState.canScrollForward) {
                                coroutineScope.launch {
                                    scrollState.scrollBy(-available.y)
                                }
                                Offset(0f, available.y)
                            } else {
                                Offset.Zero
                            }
                        }
                    })
                    .fillMaxHeight()
            ) {
                Column(modifier = Modifier.padding()) {
                    ProfileHeader(baseUser, navController, account, accountViewModel)
                    ScrollableTabRow(
                        selectedTabIndex = pagerState.currentPage,
                        indicator = { tabPositions ->
                            TabRowDefaults.Indicator(
                                Modifier.pagerTabIndicatorOffset(pagerState, tabPositions),
                                color = MaterialTheme.colors.primary
                            )
                        },
                        edgePadding = 8.dp,
                        modifier = Modifier.onSizeChanged {
                            tabsSize = it
                        }
                    ) {
                        val tabs = listOf<@Composable() (() -> Unit)?>(
                            {
                                Text(text = "Notes")
                            },
                            {
                                Text(text = "Replies")
                            },
                            {
                                val userState by baseUser.live().follows.observeAsState()
                                val userFollows = userState?.user?.follows?.size ?: "--"

                                Text(text = "$userFollows Follows")
                            },
                            {
                                val userState by baseUser.live().follows.observeAsState()
                                val userFollows = userState?.user?.followers?.size ?: "--"

                                Text(text = "$userFollows Followers")
                            },
                            {
                                val userState by baseUser.live().zaps.observeAsState()
                                val userZaps = userState?.user?.zappedAmount()

                                Text(text = "${showAmount(userZaps)} Zaps")
                            },
                            {
                                val userState by baseUser.live().reports.observeAsState()
                                val userReports = userState?.user?.reports?.values?.flatten()?.count()

                                Text(text = "${userReports} Reports")
                            },
                            {
                                val userState by baseUser.live().relays.observeAsState()
                                val userRelaysBeingUsed =
                                    userState?.user?.relaysBeingUsed?.size ?: "--"

                                val userStateRelayInfo by baseUser.live().relayInfo.observeAsState()
                                val userRelays = userStateRelayInfo?.user?.relays?.size ?: "--"

                                Text(text = "$userRelaysBeingUsed / $userRelays Relays")
                            }
                        )

                        tabs.forEachIndexed { index, function ->
                            Tab(
                                selected = pagerState.currentPage == index,
                                onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } },
                                text = function
                            )
                        }
                    }
                    HorizontalPager(
                        count = 7,
                        state = pagerState,
                        modifier = with(LocalDensity.current) {
                            Modifier.height((columnSize.height - tabsSize.height).toDp())
                        }
                    ) {
                        when (pagerState.currentPage) {
                            0 -> TabNotesNewThreads(baseUser, accountViewModel, navController)
                            1 -> TabNotesConversations(baseUser, accountViewModel, navController)
                            2 -> TabFollows(baseUser, accountViewModel, navController)
                            3 -> TabFollowers(baseUser, accountViewModel, navController)
                            4 -> TabReceivedZaps(baseUser, accountViewModel, navController)
                            5 -> TabReports(baseUser, accountViewModel, navController)
                            6 -> TabRelays(baseUser, accountViewModel, navController)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileHeader(
    baseUser: User,
    navController: NavController,
    account: Account,
    accountViewModel: AccountViewModel
) {
    val ctx = LocalContext.current.applicationContext
    var popupExpanded by remember { mutableStateOf(false) }

    val accountUserState by account.userProfile().live().follows.observeAsState()
    val accountUser = accountUserState?.user ?: return

    Box {
        DrawBanner(baseUser)

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
                    tint = MaterialTheme.colors.onSurface.copy(alpha = 0.32f),
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More Options",
                )

                UserProfileDropDownMenu(baseUser, popupExpanded, { popupExpanded = false }, accountViewModel)
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
                    baseUser, navController, account.userProfile(), 100.dp,
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
                    MessageButton(baseUser, navController)

                    if (accountUser == baseUser && account.isWriteable()) {
                        NSecCopyButton(account)
                    }

                    NPubCopyButton(baseUser)

                    if (accountUser == baseUser) {
                        EditButton(account)
                    } else {
                        if (account.isHidden(baseUser)) {
                            ShowUserButton {
                                account.showUser(baseUser.pubkeyHex)
                                LocalPreferences(ctx).saveToEncryptedStorage(account)
                            }
                        } else if (accountUser.isFollowing(baseUser)) {
                            UnfollowButton { account.unfollow(baseUser) }
                        } else {
                            FollowButton { account.follow(baseUser) }
                        }
                    }
                }
            }

            DrawAdditionalInfo(baseUser, account)

            Divider(modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun DrawAdditionalInfo(baseUser: User, account: Account) {
    val userState by baseUser.live().metadata.observeAsState()
    val user = userState?.user ?: return

    val uri = LocalUriHandler.current

    user.bestDisplayName()?.let {
        Text( "$it",
            modifier = Modifier.padding(top = 7.dp),
            fontWeight = FontWeight.Bold,
            fontSize = 25.sp
        )
    }

    user.bestUsername()?.let {
        Text(
            "@$it",
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f),
            modifier = Modifier.padding(top = 1.dp, bottom = 1.dp, start = 5.dp)
        )
    }

    val website = user.info?.website
    if (!website.isNullOrEmpty()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                tint = MaterialTheme.colors.onSurface.copy(alpha = 0.32f),
                imageVector = Icons.Default.Link,
                contentDescription = "Website",
                modifier = Modifier.size(16.dp)
            )

            ClickableText(
                text = AnnotatedString(website.removePrefix("https://")),
                onClick = { website.let { runCatching { uri.openUri(it) } } },
                style = LocalTextStyle.current.copy(color = MaterialTheme.colors.primary),
                modifier = Modifier.padding(top = 1.dp, bottom = 1.dp, start = 5.dp)
            )
        }
    }

    var ZapExpanded by remember { mutableStateOf(false) }

    val lud16 = user.info?.lud16?.trim() ?: user.info?.lud06?.trim()

    if (!lud16.isNullOrEmpty()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                tint = BitcoinOrange,
                imageVector = Icons.Default.Bolt,
                contentDescription = "Lightning Address",
                modifier = Modifier.size(16.dp)
            )

            ClickableText(
                text = AnnotatedString(lud16),
                onClick = { ZapExpanded = !ZapExpanded },
                style = LocalTextStyle.current.copy(color = MaterialTheme.colors.primary),
                modifier = Modifier.padding(top = 1.dp, bottom = 1.dp, start = 5.dp).weight(1f)
            )
        }

        if (ZapExpanded) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 5.dp)) {
                InvoiceRequest(lud16, baseUser.pubkeyHex, account) {
                    ZapExpanded = false
                }
            }
        }
    }

    user.info?.about?.let {
        Text(
            "$it",
            color = MaterialTheme.colors.onSurface,
            modifier = Modifier.padding(top = 5.dp, bottom = 5.dp)
        )
    }
}

@Composable
private fun DrawBanner(baseUser: User) {
    val userState by baseUser.live().metadata.observeAsState()
    val user = userState?.user ?: return

    val banner = user.info?.banner

    if (banner != null && banner.isNotBlank()) {
        AsyncImageProxy(
            model = ResizeImage(banner, 125.dp),
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
}

@Composable
fun TabNotesNewThreads(user: User, accountViewModel: AccountViewModel, navController: NavController) {
    val accountState by accountViewModel.accountLiveData.observeAsState()
    if (accountState != null) {
        val feedViewModel: NostrUserProfileNewThreadsFeedViewModel = viewModel()

        LaunchedEffect(Unit) {
            feedViewModel.refresh()
        }

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
fun TabNotesConversations(user: User, accountViewModel: AccountViewModel, navController: NavController) {
    val accountState by accountViewModel.accountLiveData.observeAsState()
    if (accountState != null) {
        val feedViewModel: NostrUserProfileConversationsFeedViewModel = viewModel()

        LaunchedEffect(Unit) {
            feedViewModel.refresh()
        }

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

    LaunchedEffect(Unit) {
        feedViewModel.refresh()
    }

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

    LaunchedEffect(Unit) {
        feedViewModel.refresh()
    }

    Column(Modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier.padding(vertical = 0.dp)
        ) {
            UserFeedView(feedViewModel, accountViewModel, navController)
        }
    }
}

@Composable
fun TabReceivedZaps(user: User, accountViewModel: AccountViewModel, navController: NavController) {
    val accountState by accountViewModel.accountLiveData.observeAsState()
    if (accountState != null) {
        val feedViewModel: NostrUserProfileZapsFeedViewModel = viewModel()

        LaunchedEffect(Unit) {
            feedViewModel.refresh()
        }

        Column(Modifier.fillMaxHeight()) {
            Column(
                modifier = Modifier.padding(vertical = 0.dp)
            ) {
                LnZapFeedView(feedViewModel, accountViewModel, navController)
            }
        }
    }
}

@Composable
fun TabReports(user: User, accountViewModel: AccountViewModel, navController: NavController) {
    val accountState by accountViewModel.accountLiveData.observeAsState()
    if (accountState != null) {
        val feedViewModel: NostrUserProfileReportFeedViewModel = viewModel()

        LaunchedEffect(Unit) {
            feedViewModel.refresh()
        }

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
fun TabRelays(user: User, accountViewModel: AccountViewModel, navController: NavController) {
    val feedViewModel: RelayFeedViewModel = viewModel()

    LaunchedEffect(key1 = user) {
        feedViewModel.subscribeTo(user)
    }

    Column(Modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier.padding(vertical = 0.dp)
        ) {
            RelayFeedView(feedViewModel, accountViewModel, navController)
        }
    }
}

@Composable
private fun NSecCopyButton(
    account: Account
) {
    val clipboardManager = LocalClipboardManager.current
    var popupExpanded by remember { mutableStateOf(false) }

    Button(
        modifier = Modifier
            .padding(horizontal = 3.dp)
            .width(50.dp),
        onClick = { popupExpanded = true },
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults
            .buttonColors(
                backgroundColor = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
            )
    ) {
        Icon(
            tint = Color.White,
            imageVector = Icons.Default.Key,
            contentDescription = "Copies the Nsec ID (your password) to the clipboard for backup"
        )

        DropdownMenu(
            expanded = popupExpanded,
            onDismissRequest = { popupExpanded = false }
        ) {
            DropdownMenuItem(onClick = {  account.loggedIn.privKey?.let { clipboardManager.setText(AnnotatedString(it.toNsec())) }; popupExpanded = false }) {
                Text("Copy Private Key to the Clipboard")
            }
        }
    }
}

@Composable
private fun NPubCopyButton(
    user: User
) {
    val clipboardManager = LocalClipboardManager.current
    var popupExpanded by remember { mutableStateOf(false) }

    Button(
        modifier = Modifier
            .padding(horizontal = 3.dp)
            .width(50.dp),
        onClick = { popupExpanded = true },
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults
            .buttonColors(
                backgroundColor = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
            ),
    ) {
        Icon(
            tint = Color.White,
            imageVector = Icons.Default.Share,
            contentDescription = "Copies the public key to the clipboard for sharing"
        )

        DropdownMenu(
            expanded = popupExpanded,
            onDismissRequest = { popupExpanded = false }
        ) {
            DropdownMenuItem(onClick = { clipboardManager.setText(AnnotatedString(user.pubkeyNpub())); popupExpanded = false }) {
                Text("Copy Public Key (NPub) to the Clipboard")
            }
        }
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
        DropdownMenuItem(onClick = { clipboardManager.setText(AnnotatedString(user.pubkeyNpub())); onDismiss() }) {
            Text("Copy User ID")
        }

        if ( account.userProfile() != user) {
            Divider()
            if (account.isHidden(user)) {
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
                    Text("Block & Hide User")
                }
            }
            Divider()
            DropdownMenuItem(onClick = {
                accountViewModel.report(user, ReportEvent.ReportType.SPAM);
                user.let { accountViewModel.hide(it, context) }
                onDismiss()
            }) {
                Text("Report Spam / Scam")
            }
            DropdownMenuItem(onClick = {
                accountViewModel.report(user, ReportEvent.ReportType.IMPERSONATION);
                user.let { accountViewModel.hide(it, context) }
                onDismiss()
            }) {
                Text("Report Impersonation")
            }
            DropdownMenuItem(onClick = {
                accountViewModel.report(user, ReportEvent.ReportType.EXPLICIT);
                user.let { accountViewModel.hide(it, context) }
                onDismiss()
            }) {
                Text("Report Explicit Content")
            }
            DropdownMenuItem(onClick = {
                accountViewModel.report(user, ReportEvent.ReportType.ILLEGAL);
                user.let { accountViewModel.hide(it, context) }
                onDismiss()
            }) {
                Text("Report Illegal Behaviour")
            }
        }
    }
}