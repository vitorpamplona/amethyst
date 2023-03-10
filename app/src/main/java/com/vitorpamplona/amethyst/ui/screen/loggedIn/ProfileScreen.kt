package com.vitorpamplona.amethyst.ui.screen.loggedIn

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.pagerTabIndicatorOffset
import com.google.accompanist.pager.rememberPagerState
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.RoboHashCache
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.NostrUserProfileDataSource
import com.vitorpamplona.amethyst.service.model.BadgeDefinitionEvent
import com.vitorpamplona.amethyst.service.model.BadgeProfilesEvent
import com.vitorpamplona.amethyst.service.model.IdentityClaim
import com.vitorpamplona.amethyst.service.model.ReportEvent
import com.vitorpamplona.amethyst.ui.actions.NewUserMetadataView
import com.vitorpamplona.amethyst.ui.components.DisplayNip05ProfileStatus
import com.vitorpamplona.amethyst.ui.components.InvoiceRequest
import com.vitorpamplona.amethyst.ui.components.ResizeImage
import com.vitorpamplona.amethyst.ui.components.ZoomableImageDialog
import com.vitorpamplona.amethyst.ui.dal.UserProfileConversationsFeedFilter
import com.vitorpamplona.amethyst.ui.dal.UserProfileFollowersFeedFilter
import com.vitorpamplona.amethyst.ui.dal.UserProfileFollowsFeedFilter
import com.vitorpamplona.amethyst.ui.dal.UserProfileNewThreadFeedFilter
import com.vitorpamplona.amethyst.ui.dal.UserProfileReportsFeedFilter
import com.vitorpamplona.amethyst.ui.dal.UserProfileZapsFeedFilter
import com.vitorpamplona.amethyst.ui.note.UserPicture
import com.vitorpamplona.amethyst.ui.note.showAmount
import com.vitorpamplona.amethyst.ui.screen.FeedView
import com.vitorpamplona.amethyst.ui.screen.LnZapFeedView
import com.vitorpamplona.amethyst.ui.screen.NostrUserProfileConversationsFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.NostrUserProfileFollowersUserFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.NostrUserProfileFollowsUserFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.NostrUserProfileNewThreadsFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.NostrUserProfileReportFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.NostrUserProfileZapsFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.RelayFeedView
import com.vitorpamplona.amethyst.ui.screen.RelayFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.UserFeedView
import com.vitorpamplona.amethyst.ui.theme.BitcoinOrange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal

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

        Column(
            modifier = Modifier
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
                        backgroundColor = MaterialTheme.colors.background,
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
                                Text(text = stringResource(R.string.notes))
                            },
                            {
                                Text(text = stringResource(R.string.replies))
                            },
                            {
                                val userState by baseUser.live().follows.observeAsState()
                                val userFollows = userState?.user?.transientFollowCount() ?: "--"

                                Text(text = "$userFollows ${stringResource(R.string.follows)}")
                            },
                            {
                                val userState by baseUser.live().follows.observeAsState()
                                val userFollowers = userState?.user?.transientFollowerCount() ?: "--"

                                Text(text = "$userFollowers ${stringResource(id = R.string.followers)}")
                            },
                            {
                                val userState by baseUser.live().zaps.observeAsState()
                                val userZaps = userState?.user

                                var zapAmount by remember { mutableStateOf<BigDecimal?>(null) }

                                LaunchedEffect(key1 = userState) {
                                    withContext(Dispatchers.IO) {
                                        val tempAmount = userZaps?.zappedAmount()
                                        withContext(Dispatchers.Main) {
                                            zapAmount = tempAmount
                                        }
                                    }
                                }

                                Text(text = "${showAmount(zapAmount)} ${stringResource(id = R.string.zaps)}")
                            },
                            {
                                val userState by baseUser.live().reports.observeAsState()
                                val userReports = userState?.user?.reports?.values?.flatten()?.count()

                                Text(text = "$userReports ${stringResource(R.string.reports)}")
                            },
                            {
                                val userState by baseUser.live().relays.observeAsState()
                                val userRelaysBeingUsed = userState?.user?.relaysBeingUsed?.size ?: "--"

                                val userStateRelayInfo by baseUser.live().relayInfo.observeAsState()
                                val userRelays = userStateRelayInfo?.user?.latestContactList?.relays()?.size ?: "--"

                                Text(text = "$userRelaysBeingUsed / $userRelays ${stringResource(R.string.relays)}")
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
    var popupExpanded by remember { mutableStateOf(false) }
    var zoomImageDialogOpen by remember { mutableStateOf(false) }

    val accountUserState by account.userProfile().live().follows.observeAsState()
    val accountUser = accountUserState?.user ?: return

    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    Box {
        DrawBanner(baseUser)

        Box(
            modifier = Modifier
                .padding(horizontal = 10.dp)
                .size(40.dp)
                .align(Alignment.TopEnd)
        ) {
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
                    contentDescription = stringResource(R.string.more_options)
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
                    baseUser = baseUser,
                    baseUserAccount = account.userProfile(),
                    size = 100.dp,
                    pictureModifier = Modifier.border(
                        3.dp,
                        MaterialTheme.colors.background,
                        CircleShape
                    ),
                    onClick = {
                        if (baseUser.profilePicture() != null) {
                            zoomImageDialogOpen = true
                        }
                    },
                    onLongClick = {
                        ResizeImage(it.info?.picture, 100.dp).proxyUrl()?.let { it1 ->
                            clipboardManager.setText(
                                AnnotatedString(it1)
                            )
                        }
                    }
                )

                Spacer(Modifier.weight(1f))

                Row(
                    modifier = Modifier
                        .height(35.dp)
                        .padding(bottom = 3.dp)
                ) {
                    MessageButton(baseUser, navController)

                    // No need for this button anymore
                    // NPubCopyButton(baseUser)

                    if (accountUser == baseUser) {
                        EditButton(account)
                    }

                    if (account.isHidden(baseUser)) {
                        ShowUserButton {
                            account.showUser(baseUser.pubkeyHex)
                        }
                    } else if (accountUser.isFollowing(baseUser)) {
                        UnfollowButton { coroutineScope.launch(Dispatchers.IO) { account.unfollow(baseUser) } }
                    } else {
                        FollowButton { coroutineScope.launch(Dispatchers.IO) { account.follow(baseUser) } }
                    }
                }
            }

            DrawAdditionalInfo(baseUser, account, navController)

            Divider(modifier = Modifier.padding(top = 6.dp))
        }
    }

    if (zoomImageDialogOpen) {
        ZoomableImageDialog(baseUser.profilePicture()!!, onDismiss = { zoomImageDialogOpen = false })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DrawAdditionalInfo(baseUser: User, account: Account, navController: NavController) {
    val userState by baseUser.live().metadata.observeAsState()
    val user = userState?.user ?: return

    val userBadgeState by baseUser.live().badges.observeAsState()
    val userBadge = userBadgeState?.user ?: return

    val uri = LocalUriHandler.current
    val clipboardManager = LocalClipboardManager.current

    Row(verticalAlignment = Alignment.Bottom) {
        user.bestDisplayName()?.let {
            Text(
                it,
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
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = user.pubkeyDisplayHex(),
            modifier = Modifier.padding(top = 1.dp, bottom = 1.dp),
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
        )

        IconButton(
            modifier = Modifier.size(30.dp).padding(start = 5.dp),
            onClick = { clipboardManager.setText(AnnotatedString(user.pubkeyNpub())); }
        ) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                null,
                modifier = Modifier.padding(end = 5.dp).size(15.dp),
                tint = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
            )
        }
    }

    userBadge.acceptedBadges?.let { note ->
        (note.event as? BadgeProfilesEvent)?.let { event ->
            FlowRow(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 5.dp)) {
                event.badgeAwardEvents().forEach { badgeAwardEvent ->
                    val baseNote = LocalCache.notes[badgeAwardEvent]
                    if (baseNote != null) {
                        val badgeAwardState by baseNote.live().metadata.observeAsState()
                        val baseBadgeDefinition = badgeAwardState?.note?.replyTo?.firstOrNull()

                        if (baseBadgeDefinition != null) {
                            BadgeThumb(baseBadgeDefinition, navController, 50.dp)
                        }
                    }
                }
            }
        }
    }

    DisplayNip05ProfileStatus(user)

    val website = user.info?.website
    if (!website.isNullOrEmpty()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                tint = MaterialTheme.colors.onSurface.copy(alpha = 0.32f),
                imageVector = Icons.Default.Link,
                contentDescription = stringResource(R.string.website),
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

    var zapExpanded by remember { mutableStateOf(false) }

    val lud16 = user.info?.lud16?.trim() ?: user.info?.lud06?.trim()

    if (!lud16.isNullOrEmpty()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                tint = BitcoinOrange,
                imageVector = Icons.Default.Bolt,
                contentDescription = stringResource(R.string.lightning_address),
                modifier = Modifier.size(16.dp)
            )

            ClickableText(
                text = AnnotatedString(lud16),
                onClick = { zapExpanded = !zapExpanded },
                style = LocalTextStyle.current.copy(color = MaterialTheme.colors.primary),
                modifier = Modifier
                    .padding(top = 1.dp, bottom = 1.dp, start = 5.dp)
                    .weight(1f)
            )
        }

        if (zapExpanded) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 5.dp)) {
                InvoiceRequest(lud16, baseUser.pubkeyHex, account) {
                    zapExpanded = false
                }
            }
        }
    }

    val identities = user.info?.latestMetadata?.identityClaims()
    if (!identities.isNullOrEmpty()) {
        identities.forEach { identity: IdentityClaim ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    tint = Color.Unspecified,
                    painter = painterResource(id = identity.toIcon()),
                    contentDescription = stringResource(identity.toDescriptor()),
                    modifier = Modifier.size(16.dp)
                )

                ClickableText(
                    text = AnnotatedString(identity.identity),
                    onClick = { runCatching { uri.openUri(identity.toProofUrl()) } },
                    style = LocalTextStyle.current.copy(color = MaterialTheme.colors.primary),
                    modifier = Modifier
                        .padding(top = 1.dp, bottom = 1.dp, start = 5.dp)
                        .weight(1f)
                )
            }
        }
    }

    user.info?.about?.let {
        Text(
            it,
            color = MaterialTheme.colors.onSurface,
            modifier = Modifier.padding(top = 5.dp, bottom = 5.dp)
        )
    }
}

@Composable
fun BadgeThumb(
    note: Note,
    navController: NavController,
    size: Dp,
    pictureModifier: Modifier = Modifier
) {
    BadgeThumb(note, size, pictureModifier) {
        navController.navigate("Note/${it.idHex}")
    }
}

@Composable
fun BadgeThumb(
    baseNote: Note,
    size: Dp,
    pictureModifier: Modifier = Modifier,
    onClick: ((Note) -> Unit)? = null
) {
    val noteState by baseNote.live().metadata.observeAsState()
    val note = noteState?.note ?: return

    val event = (note.event as? BadgeDefinitionEvent)
    val image = event?.thumb() ?: event?.image()

    val ctx = LocalContext.current.applicationContext

    Box(
        Modifier
            .width(size)
            .height(size)
    ) {
        if (image == null) {
            Image(
                painter = BitmapPainter(RoboHashCache.get(ctx, "ohnothisauthorisnotfound")),
                contentDescription = stringResource(R.string.unknown_author),
                modifier = pictureModifier
                    .fillMaxSize(1f)
                    .background(MaterialTheme.colors.background)
            )
        } else {
            AsyncImage(
                model = image,
                contentDescription = stringResource(id = R.string.profile_image),
                placeholder = BitmapPainter(RoboHashCache.get(ctx, note.idHex)),
                fallback = BitmapPainter(RoboHashCache.get(ctx, note.idHex)),
                error = BitmapPainter(RoboHashCache.get(ctx, note.idHex)),
                modifier = pictureModifier
                    .fillMaxSize(1f)
                    .clip(shape = CircleShape)
                    .background(MaterialTheme.colors.background)
                    .run {
                        if (onClick != null) {
                            this.clickable(onClick = { onClick(note) })
                        } else {
                            this
                        }
                    }

            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawBanner(baseUser: User) {
    val userState by baseUser.live().metadata.observeAsState()
    val user = userState?.user ?: return

    val banner = user.info?.banner
    val clipboardManager = LocalClipboardManager.current
    var zoomImageDialogOpen by remember { mutableStateOf(false) }

    if (!banner.isNullOrBlank()) {
        AsyncImage(
            model = banner,
            contentDescription = stringResource(id = R.string.profile_image),
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth()
                .height(125.dp)
                .combinedClickable(
                    onClick = {},
                    onLongClick = {
                        clipboardManager.setText(AnnotatedString(banner))
                    }
                )
                .clickable { zoomImageDialogOpen = true }
        )

        if (zoomImageDialogOpen) {
            ZoomableImageDialog(imageUrl = banner, onDismiss = { zoomImageDialogOpen = false })
        }
    } else {
        Image(
            painter = painterResource(R.drawable.profile_banner),
            contentDescription = stringResource(id = R.string.profile_banner),
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
fun TabFollows(baseUser: User, accountViewModel: AccountViewModel, navController: NavController) {
    val feedViewModel: NostrUserProfileFollowsUserFeedViewModel = viewModel()

    val userState by baseUser.live().follows.observeAsState()

    LaunchedEffect(userState) {
        feedViewModel.invalidateData()
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
fun TabFollowers(baseUser: User, accountViewModel: AccountViewModel, navController: NavController) {
    val feedViewModel: NostrUserProfileFollowersUserFeedViewModel = viewModel()

    val userState by baseUser.live().follows.observeAsState()

    LaunchedEffect(userState) {
        feedViewModel.invalidateData()
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
fun TabReceivedZaps(baseUser: User, accountViewModel: AccountViewModel, navController: NavController) {
    val feedViewModel: NostrUserProfileZapsFeedViewModel = viewModel()

    val userState by baseUser.live().zaps.observeAsState()

    LaunchedEffect(userState) {
        feedViewModel.invalidateData()
    }

    Column(Modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier.padding(vertical = 0.dp)
        ) {
            LnZapFeedView(feedViewModel, accountViewModel, navController)
        }
    }
}

@Composable
fun TabReports(baseUser: User, accountViewModel: AccountViewModel, navController: NavController) {
    val feedViewModel: NostrUserProfileReportFeedViewModel = viewModel()

    val userState by baseUser.live().reports.observeAsState()

    LaunchedEffect(userState) {
        feedViewModel.invalidateData()
    }

    Column(Modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier.padding(vertical = 0.dp)
        ) {
            FeedView(feedViewModel, accountViewModel, navController, null)
        }
    }
}

@Composable
fun TabRelays(user: User, accountViewModel: AccountViewModel, navController: NavController) {
    val feedViewModel: RelayFeedViewModel = viewModel()

    val lifeCycleOwner = LocalLifecycleOwner.current

    DisposableEffect(user) {
        val observer = LifecycleEventObserver { source, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                println("Profile Relay Start")
                feedViewModel.subscribeTo(user)
            }
            if (event == Lifecycle.Event.ON_PAUSE) {
                println("Profile Relay Stop")
                feedViewModel.unsubscribeTo(user)
            }
        }

        lifeCycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifeCycleOwner.lifecycle.removeObserver(observer)
            println("Profile Relay Dispose")
            feedViewModel.unsubscribeTo(user)
        }
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
            )
    ) {
        Icon(
            tint = Color.White,
            imageVector = Icons.Default.Share,
            contentDescription = stringResource(R.string.copies_the_public_key_to_the_clipboard_for_sharing)
        )

        DropdownMenu(
            expanded = popupExpanded,
            onDismissRequest = { popupExpanded = false }
        ) {
            DropdownMenuItem(onClick = { clipboardManager.setText(AnnotatedString(user.pubkeyNpub())); popupExpanded = false }) {
                Text(stringResource(R.string.copy_public_key_npub_to_the_clipboard))
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
            )
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_dm),
            stringResource(R.string.send_a_direct_message),
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

    if (wantsToEdit) {
        NewUserMetadataView({ wantsToEdit = false }, account)
    }

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
            contentDescription = stringResource(R.string.edits_the_user_s_metadata)
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
        Text(text = stringResource(R.string.unfollow), color = Color.White)
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
        Text(text = stringResource(R.string.follow), color = Color.White, textAlign = TextAlign.Center)
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
        Text(text = stringResource(R.string.unblock), color = Color.White)
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
            Text(stringResource(R.string.copy_user_id))
        }

        if (account.userProfile() != user) {
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
                    Text(stringResource(R.string.unblock_user))
                }
            } else {
                DropdownMenuItem(onClick = { user.let { accountViewModel.hide(it, context) }; onDismiss() }) {
                    Text(stringResource(id = R.string.block_hide_user))
                }
            }
            Divider()
            DropdownMenuItem(onClick = {
                accountViewModel.report(user, ReportEvent.ReportType.SPAM)
                user.let { accountViewModel.hide(it, context) }
                onDismiss()
            }) {
                Text(stringResource(id = R.string.report_spam_scam))
            }
            DropdownMenuItem(onClick = {
                accountViewModel.report(user, ReportEvent.ReportType.PROFANITY)
                user.let { accountViewModel.hide(it, context) }
                onDismiss()
            }) {
                Text(stringResource(R.string.report_hateful_speech))
            }
            DropdownMenuItem(onClick = {
                accountViewModel.report(user, ReportEvent.ReportType.IMPERSONATION)
                user.let { accountViewModel.hide(it, context) }
                onDismiss()
            }) {
                Text(stringResource(id = R.string.report_impersonation))
            }
            DropdownMenuItem(onClick = {
                accountViewModel.report(user, ReportEvent.ReportType.NUDITY)
                user.let { accountViewModel.hide(it, context) }
                onDismiss()
            }) {
                Text(stringResource(R.string.report_nudity_porn))
            }
            DropdownMenuItem(onClick = {
                accountViewModel.report(user, ReportEvent.ReportType.ILLEGAL)
                user.let { accountViewModel.hide(it, context) }
                onDismiss()
            }) {
                Text(stringResource(id = R.string.report_illegal_behaviour))
            }
        }
    }
}
