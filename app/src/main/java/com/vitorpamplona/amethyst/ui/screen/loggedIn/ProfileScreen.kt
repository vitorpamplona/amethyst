/**
 * Copyright (c) 2024 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.ui.screen.loggedIn

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.map
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.NostrUserProfileDataSource
import com.vitorpamplona.amethyst.ui.actions.InformationDialog
import com.vitorpamplona.amethyst.ui.actions.NewUserMetadataView
import com.vitorpamplona.amethyst.ui.components.CreateTextWithEmoji
import com.vitorpamplona.amethyst.ui.components.DisplayNip05ProfileStatus
import com.vitorpamplona.amethyst.ui.components.InvoiceRequestCard
import com.vitorpamplona.amethyst.ui.components.RobohashAsyncImage
import com.vitorpamplona.amethyst.ui.components.RobohashFallbackAsyncImage
import com.vitorpamplona.amethyst.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.ui.components.ZoomableImageDialog
import com.vitorpamplona.amethyst.ui.dal.UserProfileReportsFeedFilter
import com.vitorpamplona.amethyst.ui.navigation.routeToMessage
import com.vitorpamplona.amethyst.ui.note.ClickableUserPicture
import com.vitorpamplona.amethyst.ui.note.DrawPlayName
import com.vitorpamplona.amethyst.ui.note.ErrorMessageDialog
import com.vitorpamplona.amethyst.ui.note.LightningAddressIcon
import com.vitorpamplona.amethyst.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.ui.note.payViaIntent
import com.vitorpamplona.amethyst.ui.qrcode.ShowQRDialog
import com.vitorpamplona.amethyst.ui.screen.FeedState
import com.vitorpamplona.amethyst.ui.screen.LnZapFeedView
import com.vitorpamplona.amethyst.ui.screen.NostrUserAppRecommendationsFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.NostrUserProfileBookmarksFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.NostrUserProfileConversationsFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.NostrUserProfileFollowersUserFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.NostrUserProfileFollowsUserFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.NostrUserProfileNewThreadsFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.NostrUserProfileReportFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.NostrUserProfileZapsFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.RefresheableFeedView
import com.vitorpamplona.amethyst.ui.screen.RefreshingFeedUserFeedView
import com.vitorpamplona.amethyst.ui.screen.RelayFeedView
import com.vitorpamplona.amethyst.ui.screen.RelayFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.UserFeedViewModel
import com.vitorpamplona.amethyst.ui.theme.BitcoinOrange
import com.vitorpamplona.amethyst.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.ui.theme.ButtonPadding
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.Size15Modifier
import com.vitorpamplona.amethyst.ui.theme.Size16Modifier
import com.vitorpamplona.amethyst.ui.theme.Size25Modifier
import com.vitorpamplona.amethyst.ui.theme.Size35dp
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.ZeroPadding
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.quartz.encoders.ATag
import com.vitorpamplona.quartz.events.AppDefinitionEvent
import com.vitorpamplona.quartz.events.BadgeDefinitionEvent
import com.vitorpamplona.quartz.events.BadgeProfilesEvent
import com.vitorpamplona.quartz.events.EmptyTagList
import com.vitorpamplona.quartz.events.GitHubIdentity
import com.vitorpamplona.quartz.events.IdentityClaim
import com.vitorpamplona.quartz.events.MastodonIdentity
import com.vitorpamplona.quartz.events.PayInvoiceErrorResponse
import com.vitorpamplona.quartz.events.PayInvoiceSuccessResponse
import com.vitorpamplona.quartz.events.ReportEvent
import com.vitorpamplona.quartz.events.TelegramIdentity
import com.vitorpamplona.quartz.events.TwitterIdentity
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal

@Composable
fun ProfileScreen(
    userId: String?,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    if (userId == null) return

    var userBase by remember { mutableStateOf(LocalCache.getUserIfExists(userId)) }

    if (userBase == null) {
        LaunchedEffect(userId) {
            // waits to resolve.
            launch(Dispatchers.IO) {
                val newUserBase = LocalCache.checkGetOrCreateUser(userId)
                if (newUserBase != userBase) {
                    userBase = newUserBase
                }
            }
        }
    }

    userBase?.let {
        PrepareViewModels(
            baseUser = it,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}

@Composable
fun PrepareViewModels(
    baseUser: User,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val followsFeedViewModel: NostrUserProfileFollowsUserFeedViewModel =
        viewModel(
            key = baseUser.pubkeyHex + "UserProfileFollowsUserFeedViewModel",
            factory =
                NostrUserProfileFollowsUserFeedViewModel.Factory(
                    baseUser,
                    accountViewModel.account,
                ),
        )

    val followersFeedViewModel: NostrUserProfileFollowersUserFeedViewModel =
        viewModel(
            key = baseUser.pubkeyHex + "UserProfileFollowersUserFeedViewModel",
            factory =
                NostrUserProfileFollowersUserFeedViewModel.Factory(
                    baseUser,
                    accountViewModel.account,
                ),
        )

    val appRecommendations: NostrUserAppRecommendationsFeedViewModel =
        viewModel(
            key = baseUser.pubkeyHex + "UserAppRecommendationsFeedViewModel",
            factory =
                NostrUserAppRecommendationsFeedViewModel.Factory(
                    baseUser,
                ),
        )

    val zapFeedViewModel: NostrUserProfileZapsFeedViewModel =
        viewModel(
            key = baseUser.pubkeyHex + "UserProfileZapsFeedViewModel",
            factory =
                NostrUserProfileZapsFeedViewModel.Factory(
                    baseUser,
                ),
        )

    val threadsViewModel: NostrUserProfileNewThreadsFeedViewModel =
        viewModel(
            key = baseUser.pubkeyHex + "UserProfileNewThreadsFeedViewModel",
            factory =
                NostrUserProfileNewThreadsFeedViewModel.Factory(
                    baseUser,
                    accountViewModel.account,
                ),
        )

    val repliesViewModel: NostrUserProfileConversationsFeedViewModel =
        viewModel(
            key = baseUser.pubkeyHex + "UserProfileConversationsFeedViewModel",
            factory =
                NostrUserProfileConversationsFeedViewModel.Factory(
                    baseUser,
                    accountViewModel.account,
                ),
        )

    val bookmarksFeedViewModel: NostrUserProfileBookmarksFeedViewModel =
        viewModel(
            key = baseUser.pubkeyHex + "UserProfileBookmarksFeedViewModel",
            factory =
                NostrUserProfileBookmarksFeedViewModel.Factory(
                    baseUser,
                    accountViewModel.account,
                ),
        )

    val reportsFeedViewModel: NostrUserProfileReportFeedViewModel =
        viewModel(
            key = baseUser.pubkeyHex + "UserProfileReportFeedViewModel",
            factory =
                NostrUserProfileReportFeedViewModel.Factory(
                    baseUser,
                ),
        )

    ProfileScreen(
        baseUser = baseUser,
        threadsViewModel,
        repliesViewModel,
        followsFeedViewModel,
        followersFeedViewModel,
        appRecommendations,
        zapFeedViewModel,
        bookmarksFeedViewModel,
        reportsFeedViewModel,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

@Composable
fun ProfileScreen(
    baseUser: User,
    threadsViewModel: NostrUserProfileNewThreadsFeedViewModel,
    repliesViewModel: NostrUserProfileConversationsFeedViewModel,
    followsFeedViewModel: NostrUserProfileFollowsUserFeedViewModel,
    followersFeedViewModel: NostrUserProfileFollowersUserFeedViewModel,
    appRecommendations: NostrUserAppRecommendationsFeedViewModel,
    zapFeedViewModel: NostrUserProfileZapsFeedViewModel,
    bookmarksFeedViewModel: NostrUserProfileBookmarksFeedViewModel,
    reportsFeedViewModel: NostrUserProfileReportFeedViewModel,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    NostrUserProfileDataSource.loadUserProfile(baseUser)

    val lifeCycleOwner = LocalLifecycleOwner.current

    DisposableEffect(accountViewModel) {
        NostrUserProfileDataSource.start()
        onDispose {
            NostrUserProfileDataSource.loadUserProfile(null)
            NostrUserProfileDataSource.stop()
        }
    }

    DisposableEffect(lifeCycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    println("Profidle Start")
                    NostrUserProfileDataSource.loadUserProfile(baseUser)
                    NostrUserProfileDataSource.start()
                }
                if (event == Lifecycle.Event.ON_PAUSE) {
                    println("Profile Stop")
                    NostrUserProfileDataSource.loadUserProfile(null)
                    NostrUserProfileDataSource.stop()
                }
            }

        lifeCycleOwner.lifecycle.addObserver(observer)
        onDispose { lifeCycleOwner.lifecycle.removeObserver(observer) }
    }

    RenderSurface(
        baseUser,
        threadsViewModel,
        repliesViewModel,
        appRecommendations,
        followsFeedViewModel,
        followersFeedViewModel,
        zapFeedViewModel,
        bookmarksFeedViewModel,
        reportsFeedViewModel,
        accountViewModel,
        nav,
    )
}

@Composable
private fun RenderSurface(
    baseUser: User,
    threadsViewModel: NostrUserProfileNewThreadsFeedViewModel,
    repliesViewModel: NostrUserProfileConversationsFeedViewModel,
    appRecommendations: NostrUserAppRecommendationsFeedViewModel,
    followsFeedViewModel: NostrUserProfileFollowsUserFeedViewModel,
    followersFeedViewModel: NostrUserProfileFollowersUserFeedViewModel,
    zapFeedViewModel: NostrUserProfileZapsFeedViewModel,
    bookmarksFeedViewModel: NostrUserProfileBookmarksFeedViewModel,
    reportsFeedViewModel: NostrUserProfileReportFeedViewModel,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
    ) {
        var columnSize by remember { mutableStateOf(IntSize.Zero) }
        var tabsSize by remember { mutableStateOf(IntSize.Zero) }

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { columnSize = it },
        ) {
            val coroutineScope = rememberCoroutineScope()
            val scrollState = rememberScrollState()

            val tabRowModifier = remember { Modifier.onSizeChanged { tabsSize = it } }

            val pagerModifier =
                with(LocalDensity.current) { Modifier.height((columnSize.height - tabsSize.height).toDp()) }

            Box(
                modifier =
                    remember {
                        Modifier
                            .verticalScroll(scrollState)
                            .nestedScroll(
                                object : NestedScrollConnection {
                                    override fun onPreScroll(
                                        available: Offset,
                                        source: NestedScrollSource,
                                    ): Offset {
                                        // When scrolling vertically, scroll the container first.
                                        return if (available.y < 0 && scrollState.canScrollForward) {
                                            coroutineScope.launch { scrollState.scrollBy(-available.y) }
                                            Offset(0f, available.y)
                                        } else {
                                            Offset.Zero
                                        }
                                    }
                                },
                            )
                            .fillMaxHeight()
                    },
            ) {
                RenderScreen(
                    baseUser,
                    tabRowModifier,
                    pagerModifier,
                    threadsViewModel,
                    repliesViewModel,
                    appRecommendations,
                    followsFeedViewModel,
                    followersFeedViewModel,
                    zapFeedViewModel,
                    bookmarksFeedViewModel,
                    reportsFeedViewModel,
                    accountViewModel,
                    nav,
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun RenderScreen(
    baseUser: User,
    tabRowModifier: Modifier,
    pagerModifier: Modifier,
    threadsViewModel: NostrUserProfileNewThreadsFeedViewModel,
    repliesViewModel: NostrUserProfileConversationsFeedViewModel,
    appRecommendations: NostrUserAppRecommendationsFeedViewModel,
    followsFeedViewModel: NostrUserProfileFollowsUserFeedViewModel,
    followersFeedViewModel: NostrUserProfileFollowersUserFeedViewModel,
    zapFeedViewModel: NostrUserProfileZapsFeedViewModel,
    bookmarksFeedViewModel: NostrUserProfileBookmarksFeedViewModel,
    reportsFeedViewModel: NostrUserProfileReportFeedViewModel,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val pagerState = rememberPagerState { 9 }

    Column {
        ProfileHeader(baseUser, appRecommendations, nav, accountViewModel)
        ScrollableTabRow(
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            selectedTabIndex = pagerState.currentPage,
            edgePadding = 8.dp,
            modifier = tabRowModifier,
            divider = { HorizontalDivider(thickness = DividerThickness) },
        ) {
            CreateAndRenderTabs(baseUser, pagerState)
        }
        HorizontalPager(
            state = pagerState,
            modifier = pagerModifier,
        ) { page ->
            CreateAndRenderPages(
                page,
                baseUser,
                threadsViewModel,
                repliesViewModel,
                followsFeedViewModel,
                followersFeedViewModel,
                zapFeedViewModel,
                bookmarksFeedViewModel,
                reportsFeedViewModel,
                accountViewModel,
                nav,
            )
        }
    }
}

@Composable
private fun CreateAndRenderPages(
    page: Int,
    baseUser: User,
    threadsViewModel: NostrUserProfileNewThreadsFeedViewModel,
    repliesViewModel: NostrUserProfileConversationsFeedViewModel,
    followsFeedViewModel: NostrUserProfileFollowsUserFeedViewModel,
    followersFeedViewModel: NostrUserProfileFollowersUserFeedViewModel,
    zapFeedViewModel: NostrUserProfileZapsFeedViewModel,
    bookmarksFeedViewModel: NostrUserProfileBookmarksFeedViewModel,
    reportsFeedViewModel: NostrUserProfileReportFeedViewModel,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    UpdateThreadsAndRepliesWhenBlockUnblock(
        baseUser,
        threadsViewModel,
        repliesViewModel,
        accountViewModel,
    )

    when (page) {
        0 -> TabNotesNewThreads(threadsViewModel, accountViewModel, nav)
        1 -> TabNotesConversations(repliesViewModel, accountViewModel, nav)
        2 -> TabFollows(baseUser, followsFeedViewModel, accountViewModel, nav)
        3 -> TabFollowers(baseUser, followersFeedViewModel, accountViewModel, nav)
        4 -> TabReceivedZaps(baseUser, zapFeedViewModel, accountViewModel, nav)
        5 -> TabBookmarks(bookmarksFeedViewModel, accountViewModel, nav)
        6 -> TabFollowedTags(baseUser, accountViewModel, nav)
        7 -> TabReports(baseUser, reportsFeedViewModel, accountViewModel, nav)
        8 -> TabRelays(baseUser, accountViewModel, nav)
    }
}

@Composable
fun UpdateThreadsAndRepliesWhenBlockUnblock(
    baseUser: User,
    threadsViewModel: NostrUserProfileNewThreadsFeedViewModel,
    repliesViewModel: NostrUserProfileConversationsFeedViewModel,
    accountViewModel: AccountViewModel,
) {
    val isHidden by
        accountViewModel.account.liveHiddenUsers
            .map {
                it.hiddenUsers.contains(baseUser.pubkeyHex) || it.spammers.contains(baseUser.pubkeyHex)
            }
            .observeAsState(accountViewModel.account.isHidden(baseUser))

    LaunchedEffect(key1 = isHidden) {
        threadsViewModel.invalidateData()
        repliesViewModel.invalidateData()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CreateAndRenderTabs(
    baseUser: User,
    pagerState: PagerState,
) {
    val coroutineScope = rememberCoroutineScope()

    val tabs =
        listOf<@Composable (() -> Unit)?>(
            { Text(text = stringResource(R.string.notes)) },
            { Text(text = stringResource(R.string.replies)) },
            { FollowTabHeader(baseUser) },
            { FollowersTabHeader(baseUser) },
            { ZapTabHeader(baseUser) },
            { BookmarkTabHeader(baseUser) },
            { FollowedTagsTabHeader(baseUser) },
            { ReportsTabHeader(baseUser) },
            { RelaysTabHeader(baseUser) },
        )

    tabs.forEachIndexed { index, function ->
        Tab(
            selected = pagerState.currentPage == index,
            onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } },
            text = function,
        )
    }
}

@Composable
private fun RelaysTabHeader(baseUser: User) {
    val userState by baseUser.live().relays.observeAsState()
    val userRelaysBeingUsed = remember(userState) { userState?.user?.relaysBeingUsed?.size ?: "--" }

    val userStateRelayInfo by baseUser.live().relayInfo.observeAsState()
    val userRelays =
        remember(userStateRelayInfo) {
            userStateRelayInfo?.user?.latestContactList?.relays()?.size ?: "--"
        }

    Text(text = "$userRelaysBeingUsed / $userRelays ${stringResource(R.string.relays)}")
}

@Composable
private fun ReportsTabHeader(baseUser: User) {
    val userState by baseUser.live().reports.observeAsState()
    var userReports by remember { mutableIntStateOf(0) }

    LaunchedEffect(key1 = userState) {
        launch(Dispatchers.IO) {
            val newSize = UserProfileReportsFeedFilter(baseUser).feed().size

            if (newSize != userReports) {
                userReports = newSize
            }
        }
    }

    Text(text = "$userReports ${stringResource(R.string.reports)}")
}

@Composable
private fun FollowedTagsTabHeader(baseUser: User) {
    var usertags by remember { mutableIntStateOf(0) }

    LaunchedEffect(key1 = baseUser) {
        launch(Dispatchers.IO) {
            val contactList = baseUser.latestContactList

            val newTags = (contactList?.verifiedFollowTagSet?.count() ?: 0)

            if (newTags != usertags) {
                usertags = newTags
            }
        }
    }

    Text(text = "$usertags ${stringResource(R.string.followed_tags)}")
}

@Composable
private fun BookmarkTabHeader(baseUser: User) {
    val userState by baseUser.live().bookmarks.observeAsState()

    var userBookmarks by remember { mutableIntStateOf(0) }

    LaunchedEffect(key1 = userState) {
        launch(Dispatchers.IO) {
            val bookmarkList = userState?.user?.latestBookmarkList

            val newBookmarks =
                (
                    bookmarkList?.taggedEvents()?.count()
                        ?: 0
                ) + (bookmarkList?.taggedAddresses()?.count() ?: 0)

            if (newBookmarks != userBookmarks) {
                userBookmarks = newBookmarks
            }
        }
    }

    Text(text = "$userBookmarks ${stringResource(R.string.bookmarks)}")
}

@Composable
private fun ZapTabHeader(baseUser: User) {
    val userState by baseUser.live().zaps.observeAsState()
    var zapAmount by remember { mutableStateOf<BigDecimal?>(null) }

    LaunchedEffect(key1 = userState) {
        launch(Dispatchers.Default) {
            val tempAmount = baseUser.zappedAmount()
            if (zapAmount != tempAmount) {
                zapAmount = tempAmount
            }
        }
    }

    Text(text = "${showAmountAxis(zapAmount)} ${stringResource(id = R.string.zaps)}")
}

@Composable
private fun FollowersTabHeader(baseUser: User) {
    val userState by baseUser.live().followers.observeAsState()
    var followerCount by remember { mutableStateOf("--") }

    val text = stringResource(R.string.followers)

    LaunchedEffect(key1 = userState) {
        launch(Dispatchers.IO) {
            val newFollower = (userState?.user?.transientFollowerCount()?.toString() ?: "--") + " " + text

            if (followerCount != newFollower) {
                followerCount = newFollower
            }
        }
    }

    Text(text = followerCount)
}

@Composable
private fun FollowTabHeader(baseUser: User) {
    val userState by baseUser.live().follows.observeAsState()
    var followCount by remember { mutableStateOf("--") }

    val text = stringResource(R.string.follows)

    LaunchedEffect(key1 = userState) {
        launch(Dispatchers.IO) {
            val newFollow = (userState?.user?.transientFollowCount()?.toString() ?: "--") + " " + text

            if (followCount != newFollow) {
                followCount = newFollow
            }
        }
    }

    Text(text = followCount)
}

@Composable
private fun ProfileHeader(
    baseUser: User,
    appRecommendations: NostrUserAppRecommendationsFeedViewModel,
    nav: (String) -> Unit,
    accountViewModel: AccountViewModel,
) {
    var popupExpanded by remember { mutableStateOf(false) }
    var zoomImageDialogOpen by remember { mutableStateOf(false) }

    Box {
        DrawBanner(baseUser, accountViewModel)

        Box(
            modifier =
                Modifier
                    .padding(horizontal = 10.dp)
                    .size(40.dp)
                    .align(Alignment.TopEnd),
        ) {
            Button(
                modifier =
                    Modifier
                        .size(30.dp)
                        .align(Alignment.Center),
                onClick = { popupExpanded = true },
                shape = ButtonBorder,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                contentPadding = ZeroPadding,
            ) {
                Icon(
                    tint = MaterialTheme.colorScheme.placeholderText,
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.more_options),
                )

                UserProfileDropDownMenu(
                    baseUser,
                    popupExpanded,
                    { popupExpanded = false },
                    accountViewModel,
                )
            }
        }

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .padding(top = 75.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                val clipboardManager = LocalClipboardManager.current

                ClickableUserPicture(
                    baseUser = baseUser,
                    accountViewModel = accountViewModel,
                    size = 100.dp,
                    modifier =
                        Modifier.border(
                            3.dp,
                            MaterialTheme.colorScheme.background,
                            CircleShape,
                        ),
                    onClick = {
                        if (baseUser.profilePicture() != null) {
                            zoomImageDialogOpen = true
                        }
                    },
                    onLongClick = {
                        it.info?.picture?.let { it1 ->
                            clipboardManager.setText(
                                AnnotatedString(it1),
                            )
                        }
                    },
                )

                Spacer(Modifier.weight(1f))

                Row(
                    modifier =
                        Modifier
                            .height(Size35dp)
                            .padding(bottom = 3.dp),
                ) {
                    MessageButton(baseUser, accountViewModel, nav)

                    ProfileActions(baseUser, accountViewModel)
                }
            }

            DrawAdditionalInfo(baseUser, appRecommendations, accountViewModel, nav)

            HorizontalDivider(modifier = Modifier.padding(top = 6.dp))
        }
    }

    val profilePic = baseUser.profilePicture()
    if (zoomImageDialogOpen && profilePic != null) {
        ZoomableImageDialog(
            RichTextParser.parseImageOrVideo(profilePic),
            onDismiss = { zoomImageDialogOpen = false },
            accountViewModel = accountViewModel,
        )
    }
}

@Composable
private fun ProfileActions(
    baseUser: User,
    accountViewModel: AccountViewModel,
) {
    val isMe by
        remember(accountViewModel) { derivedStateOf { accountViewModel.userProfile() == baseUser } }

    if (isMe) {
        EditButton(accountViewModel.account)
    }

    WatchIsHiddenUser(baseUser, accountViewModel) { isHidden ->
        if (isHidden) {
            ShowUserButton { accountViewModel.showUser(baseUser.pubkeyHex) }
        } else {
            DisplayFollowUnfollowButton(baseUser, accountViewModel)
        }
    }
}

@Composable
private fun DisplayFollowUnfollowButton(
    baseUser: User,
    accountViewModel: AccountViewModel,
) {
    val isLoggedInFollowingUser by
        accountViewModel.account
            .userProfile()
            .live()
            .follows
            .map { it.user.isFollowing(baseUser) }
            .distinctUntilChanged()
            .observeAsState(initial = accountViewModel.account.isFollowing(baseUser))

    val isUserFollowingLoggedIn by
        baseUser
            .live()
            .follows
            .map { it.user.isFollowing(accountViewModel.account.userProfile()) }
            .distinctUntilChanged()
            .observeAsState(initial = baseUser.isFollowing(accountViewModel.account.userProfile()))

    if (isLoggedInFollowingUser) {
        UnfollowButton {
            if (!accountViewModel.isWriteable()) {
                accountViewModel.toast(
                    R.string.read_only_user,
                    R.string.login_with_a_private_key_to_be_able_to_unfollow,
                )
            } else {
                accountViewModel.unfollow(baseUser)
            }
        }
    } else {
        if (isUserFollowingLoggedIn) {
            FollowButton(R.string.follow_back) {
                if (!accountViewModel.isWriteable()) {
                    accountViewModel.toast(
                        R.string.read_only_user,
                        R.string.login_with_a_private_key_to_be_able_to_follow,
                    )
                } else {
                    accountViewModel.follow(baseUser)
                }
            }
        } else {
            FollowButton(R.string.follow) {
                if (!accountViewModel.isWriteable()) {
                    accountViewModel.toast(
                        R.string.read_only_user,
                        R.string.login_with_a_private_key_to_be_able_to_follow,
                    )
                } else {
                    accountViewModel.follow(baseUser)
                }
            }
        }
    }
}

@Composable
fun WatchIsHiddenUser(
    baseUser: User,
    accountViewModel: AccountViewModel,
    content: @Composable (Boolean) -> Unit,
) {
    val isHidden by
        accountViewModel.account.liveHiddenUsers
            .map {
                it.hiddenUsers.contains(baseUser.pubkeyHex) || it.spammers.contains(baseUser.pubkeyHex)
            }
            .observeAsState(accountViewModel.account.isHidden(baseUser))

    content(isHidden)
}

fun getIdentityClaimIcon(identity: IdentityClaim): Int {
    return when (identity) {
        is TwitterIdentity -> R.drawable.twitter
        is TelegramIdentity -> R.drawable.telegram
        is MastodonIdentity -> R.drawable.mastodon
        is GitHubIdentity -> R.drawable.github
        else -> R.drawable.github
    }
}

fun getIdentityClaimDescription(identity: IdentityClaim): Int {
    return when (identity) {
        is TwitterIdentity -> R.string.twitter
        is TelegramIdentity -> R.string.telegram
        is MastodonIdentity -> R.string.mastodon
        is GitHubIdentity -> R.string.github
        else -> R.drawable.github
    }
}

@Composable
private fun DrawAdditionalInfo(
    baseUser: User,
    appRecommendations: NostrUserAppRecommendationsFeedViewModel,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val userState by baseUser.live().metadata.observeAsState()
    val user = remember(userState) { userState?.user } ?: return
    val tags = userState?.user?.info?.tags

    val uri = LocalUriHandler.current
    val clipboardManager = LocalClipboardManager.current

    val automaticallyShowProfilePicture =
        remember {
            accountViewModel.settings.showProfilePictures.value
        }

    user.toBestDisplayName().let {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 7.dp)) {
            CreateTextWithEmoji(
                text = it,
                tags = tags,
                fontWeight = FontWeight.Bold,
                fontSize = 25.sp,
            )
            Spacer(StdHorzSpacer)
            DrawPlayName(it)
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = user.pubkeyDisplayHex(),
            modifier = Modifier.padding(top = 1.dp, bottom = 1.dp),
            color = MaterialTheme.colorScheme.placeholderText,
        )

        IconButton(
            modifier =
                Modifier
                    .size(25.dp)
                    .padding(start = 5.dp),
            onClick = { clipboardManager.setText(AnnotatedString(user.pubkeyNpub())) },
        ) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = stringResource(id = R.string.copy_npub_to_clipboard),
                modifier = Size15Modifier,
                tint = MaterialTheme.colorScheme.placeholderText,
            )
        }

        var dialogOpen by remember { mutableStateOf(false) }

        if (dialogOpen) {
            ShowQRDialog(
                user,
                automaticallyShowProfilePicture,
                onScan = {
                    dialogOpen = false
                    nav(it)
                },
                onClose = { dialogOpen = false },
            )
        }

        IconButton(
            modifier = Size25Modifier,
            onClick = { dialogOpen = true },
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_qrcode),
                contentDescription = stringResource(id = R.string.show_npub_as_a_qr_code),
                modifier = Size15Modifier,
                tint = MaterialTheme.colorScheme.placeholderText,
            )
        }
    }

    DisplayBadges(baseUser, accountViewModel, nav)

    DisplayNip05ProfileStatus(user, accountViewModel)

    val website = user.info?.website
    if (!website.isNullOrEmpty()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                tint = MaterialTheme.colorScheme.placeholderText,
                imageVector = Icons.Default.Link,
                contentDescription = stringResource(R.string.website),
                modifier = Modifier.size(16.dp),
            )

            ClickableText(
                text = AnnotatedString(website.removePrefix("https://")),
                onClick = {
                    website.let {
                        runCatching {
                            if (it.contains("://")) {
                                uri.openUri(it)
                            } else {
                                uri.openUri("http://$it")
                            }
                        }
                    }
                },
                style = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.primary),
                modifier = Modifier.padding(top = 1.dp, bottom = 1.dp, start = 5.dp),
            )
        }
    }

    val lud16 = remember(userState) { user.info?.lud16?.trim() ?: user.info?.lud06?.trim() }
    val pubkeyHex = remember { baseUser.pubkeyHex }
    DisplayLNAddress(lud16, pubkeyHex, accountViewModel, nav)

    val identities = user.latestMetadata?.identityClaims()
    if (!identities.isNullOrEmpty()) {
        identities.forEach { identity: IdentityClaim ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    tint = Color.Unspecified,
                    painter = painterResource(id = getIdentityClaimIcon(identity)),
                    contentDescription = stringResource(getIdentityClaimDescription(identity)),
                    modifier = Modifier.size(16.dp),
                )

                ClickableText(
                    text = AnnotatedString(identity.identity),
                    onClick = { runCatching { uri.openUri(identity.toProofUrl()) } },
                    style = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.primary),
                    modifier =
                        Modifier
                            .padding(top = 1.dp, bottom = 1.dp, start = 5.dp)
                            .weight(1f),
                )
            }
        }
    }

    user.info?.about?.let {
        Row(
            modifier = Modifier.padding(top = 5.dp, bottom = 5.dp),
        ) {
            val defaultBackground = MaterialTheme.colorScheme.background
            val background = remember { mutableStateOf(defaultBackground) }

            TranslatableRichTextViewer(
                content = it,
                canPreview = false,
                quotesLeft = 1,
                tags = EmptyTagList,
                backgroundColor = background,
                id = it,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }

    DisplayAppRecommendations(appRecommendations, nav)
}

@Composable
fun DisplayLNAddress(
    lud16: String?,
    userHex: String,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var zapExpanded by remember { mutableStateOf(false) }

    var showErrorMessageDialog by remember { mutableStateOf<String?>(null) }

    if (showErrorMessageDialog != null) {
        ErrorMessageDialog(
            title = stringResource(id = R.string.error_dialog_zap_error),
            textContent = showErrorMessageDialog ?: "",
            onClickStartMessage = {
                scope.launch(Dispatchers.IO) {
                    val route = routeToMessage(userHex, showErrorMessageDialog, accountViewModel)
                    nav(route)
                }
            },
            onDismiss = { showErrorMessageDialog = null },
        )
    }

    var showInfoMessageDialog by remember { mutableStateOf<String?>(null) }
    if (showInfoMessageDialog != null) {
        InformationDialog(
            title = context.getString(R.string.payment_successful),
            textContent = showInfoMessageDialog ?: "",
        ) {
            showInfoMessageDialog = null
        }
    }

    if (!lud16.isNullOrEmpty()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LightningAddressIcon(modifier = Size16Modifier, tint = BitcoinOrange)

            ClickableText(
                text = AnnotatedString(lud16),
                onClick = { zapExpanded = !zapExpanded },
                style = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.primary),
                modifier =
                    Modifier
                        .padding(top = 1.dp, bottom = 1.dp, start = 5.dp)
                        .weight(1f),
            )
        }

        if (zapExpanded) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 5.dp),
            ) {
                InvoiceRequestCard(
                    lud16,
                    userHex,
                    accountViewModel.account,
                    onSuccess = {
                        zapExpanded = false
                        // pay directly
                        if (accountViewModel.account.hasWalletConnectSetup()) {
                            accountViewModel.account.sendZapPaymentRequestFor(it, null, onSent = {}) { response ->
                                if (response is PayInvoiceSuccessResponse) {
                                    showInfoMessageDialog = context.getString(R.string.payment_successful)
                                } else if (response is PayInvoiceErrorResponse) {
                                    showErrorMessageDialog =
                                        response.error?.message
                                            ?: response.error?.code?.toString()
                                            ?: context.getString(R.string.error_parsing_error_message)
                                }
                            }
                        } else {
                            payViaIntent(it, context) { showErrorMessageDialog = it }
                        }
                    },
                    onClose = { zapExpanded = false },
                    onError = { title, message -> accountViewModel.toast(title, message) },
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun DisplayAppRecommendations(
    appRecommendations: NostrUserAppRecommendationsFeedViewModel,
    nav: (String) -> Unit,
) {
    val feedState by appRecommendations.feedContent.collectAsStateWithLifecycle()

    LaunchedEffect(key1 = Unit) { appRecommendations.invalidateData() }

    Crossfade(
        targetState = feedState,
        animationSpec = tween(durationMillis = 100),
    ) { state ->
        when (state) {
            is FeedState.Loaded -> {
                Column {
                    Text(stringResource(id = R.string.recommended_apps))

                    FlowRow(
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(vertical = 5.dp),
                    ) {
                        state.feed.value.forEach { app -> WatchApp(app, nav) }
                    }
                }
            }
            else -> {}
        }
    }
}

@Composable
private fun WatchApp(
    baseApp: Note,
    nav: (String) -> Unit,
) {
    val appState by baseApp.live().metadata.observeAsState()

    var appLogo by remember(baseApp) { mutableStateOf<String?>(null) }

    LaunchedEffect(key1 = appState) {
        withContext(Dispatchers.Default) {
            val newAppLogo =
                (appState?.note?.event as? AppDefinitionEvent)?.appMetaData()?.picture?.ifBlank { null }
            if (newAppLogo != appLogo) {
                appLogo = newAppLogo
            }
        }
    }

    appLogo?.let {
        Box(
            remember {
                Modifier
                    .size(Size35dp)
                    .clickable { nav("Note/${baseApp.idHex}") }
            },
        ) {
            AsyncImage(
                model = appLogo,
                contentDescription = null,
                modifier =
                    remember {
                        Modifier
                            .size(Size35dp)
                            .clip(shape = CircleShape)
                    },
            )
        }
    }
}

@Composable
private fun DisplayBadges(
    baseUser: User,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val automaticallyShowProfilePicture =
        remember {
            accountViewModel.settings.showProfilePictures.value
        }

    LoadAddressableNote(
        aTag =
            ATag(
                BadgeProfilesEvent.KIND,
                baseUser.pubkeyHex,
                BadgeProfilesEvent.STANDARD_D_TAG,
                null,
            ),
        accountViewModel,
    ) { note ->
        if (note != null) {
            WatchAndRenderBadgeList(note, automaticallyShowProfilePicture, nav)
        }
    }
}

@Composable
private fun WatchAndRenderBadgeList(
    note: AddressableNote,
    loadProfilePicture: Boolean,
    nav: (String) -> Unit,
) {
    val badgeList by
        note
            .live()
            .metadata
            .map { (it.note.event as? BadgeProfilesEvent)?.badgeAwardEvents()?.toImmutableList() }
            .distinctUntilChanged()
            .observeAsState()

    badgeList?.let { list -> RenderBadgeList(list, loadProfilePicture, nav) }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun RenderBadgeList(
    list: ImmutableList<String>,
    loadProfilePicture: Boolean,
    nav: (String) -> Unit,
) {
    FlowRow(
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(vertical = 5.dp),
    ) {
        list.forEach { badgeAwardEvent -> LoadAndRenderBadge(badgeAwardEvent, loadProfilePicture, nav) }
    }
}

@Composable
private fun LoadAndRenderBadge(
    badgeAwardEventHex: String,
    loadProfilePicture: Boolean,
    nav: (String) -> Unit,
) {
    var baseNote by remember { mutableStateOf(LocalCache.getNoteIfExists(badgeAwardEventHex)) }

    LaunchedEffect(key1 = badgeAwardEventHex) {
        if (baseNote == null) {
            launch(Dispatchers.IO) { baseNote = LocalCache.checkGetOrCreateNote(badgeAwardEventHex) }
        }
    }

    baseNote?.let { ObserveAndRenderBadge(it, loadProfilePicture, nav) }
}

@Composable
private fun ObserveAndRenderBadge(
    it: Note,
    loadProfilePicture: Boolean,
    nav: (String) -> Unit,
) {
    val badgeAwardState by it.live().metadata.observeAsState()
    val baseBadgeDefinition by
        remember(badgeAwardState) { derivedStateOf { badgeAwardState?.note?.replyTo?.firstOrNull() } }

    baseBadgeDefinition?.let { BadgeThumb(it, loadProfilePicture, nav, Size35dp) }
}

@Composable
fun BadgeThumb(
    note: Note,
    loadProfilePicture: Boolean,
    nav: (String) -> Unit,
    size: Dp,
    pictureModifier: Modifier = Modifier,
) {
    BadgeThumb(note, loadProfilePicture, size, pictureModifier) { nav("Note/${note.idHex}") }
}

@Composable
fun BadgeThumb(
    baseNote: Note,
    loadProfilePicture: Boolean,
    size: Dp,
    pictureModifier: Modifier = Modifier,
    onClick: ((String) -> Unit)? = null,
) {
    Box(
        remember {
            Modifier
                .width(size)
                .height(size)
        },
    ) {
        WatchAndRenderBadgeImage(baseNote, loadProfilePicture, size, pictureModifier, onClick)
    }
}

@Composable
private fun WatchAndRenderBadgeImage(
    baseNote: Note,
    loadProfilePicture: Boolean,
    size: Dp,
    pictureModifier: Modifier,
    onClick: ((String) -> Unit)?,
) {
    val noteState by baseNote.live().metadata.observeAsState()
    val eventId = remember(noteState) { noteState?.note?.idHex } ?: return
    val image by
        remember(noteState) {
            derivedStateOf {
                val event = noteState?.note?.event as? BadgeDefinitionEvent
                event?.thumb()?.ifBlank { null } ?: event?.image()?.ifBlank { null }
            }
        }

    val bgColor = MaterialTheme.colorScheme.background

    if (image == null) {
        RobohashAsyncImage(
            robot = "authornotfound",
            contentDescription = stringResource(R.string.unknown_author),
            modifier =
                remember {
                    pictureModifier
                        .width(size)
                        .height(size)
                        .background(bgColor)
                },
        )
    } else {
        RobohashFallbackAsyncImage(
            robot = eventId,
            model = image!!,
            contentDescription = stringResource(id = R.string.profile_image),
            modifier =
                remember {
                    pictureModifier
                        .width(size)
                        .height(size)
                        .clip(shape = CircleShape)
                        .background(bgColor)
                        .run {
                            if (onClick != null) {
                                this.clickable(onClick = { onClick(eventId) })
                            } else {
                                this
                            }
                        }
                },
            loadProfilePicture = loadProfilePicture,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DrawBanner(
    baseUser: User,
    accountViewModel: AccountViewModel,
) {
    val userState by baseUser.live().metadata.observeAsState()
    val banner = remember(userState) { userState?.user?.info?.banner }

    val clipboardManager = LocalClipboardManager.current
    var zoomImageDialogOpen by remember { mutableStateOf(false) }

    if (!banner.isNullOrBlank()) {
        AsyncImage(
            model = banner,
            contentDescription = stringResource(id = R.string.profile_image),
            contentScale = ContentScale.FillWidth,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(125.dp)
                    .combinedClickable(
                        onClick = { zoomImageDialogOpen = true },
                        onLongClick = { clipboardManager.setText(AnnotatedString(banner)) },
                    ),
        )

        if (zoomImageDialogOpen) {
            ZoomableImageDialog(
                imageUrl = RichTextParser.parseImageOrVideo(banner),
                onDismiss = { zoomImageDialogOpen = false },
                accountViewModel = accountViewModel,
            )
        }
    } else {
        Image(
            painter = painterResource(R.drawable.profile_banner),
            contentDescription = stringResource(id = R.string.profile_banner),
            contentScale = ContentScale.FillWidth,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(125.dp),
        )
    }
}

@Composable
fun TabNotesNewThreads(
    feedViewModel: NostrUserProfileNewThreadsFeedViewModel,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    Column(Modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier.padding(vertical = 0.dp),
        ) {
            RefresheableFeedView(
                feedViewModel,
                null,
                enablePullRefresh = false,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

@Composable
fun TabNotesConversations(
    feedViewModel: NostrUserProfileConversationsFeedViewModel,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    Column(Modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier.padding(vertical = 0.dp),
        ) {
            RefresheableFeedView(
                feedViewModel,
                null,
                enablePullRefresh = false,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

@Composable
fun TabFollowedTags(
    baseUser: User,
    account: AccountViewModel,
    nav: (String) -> Unit,
) {
    Column(Modifier.fillMaxHeight().padding(vertical = 0.dp)) {
        baseUser.latestContactList?.let {
            it.unverifiedFollowTagSet().forEach { hashtag ->
                HashtagHeader(
                    tag = hashtag,
                    account = account,
                    onClick = { nav("Hashtag/$hashtag") },
                )
                HorizontalDivider(
                    thickness = DividerThickness,
                )
            }
        }
    }
}

@Composable
fun TabBookmarks(
    feedViewModel: NostrUserProfileBookmarksFeedViewModel,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    LaunchedEffect(Unit) { feedViewModel.invalidateData() }

    Column(Modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier.padding(vertical = 0.dp),
        ) {
            RefresheableFeedView(
                feedViewModel,
                null,
                enablePullRefresh = false,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

@Composable
fun TabFollows(
    baseUser: User,
    feedViewModel: UserFeedViewModel,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    WatchFollowChanges(baseUser, feedViewModel)

    Column(Modifier.fillMaxHeight()) {
        Column {
            RefreshingFeedUserFeedView(feedViewModel, accountViewModel, nav, enablePullRefresh = false)
        }
    }
}

@Composable
fun TabFollowers(
    baseUser: User,
    feedViewModel: UserFeedViewModel,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    WatchFollowerChanges(baseUser, feedViewModel)

    Column(Modifier.fillMaxHeight()) {
        Column {
            RefreshingFeedUserFeedView(feedViewModel, accountViewModel, nav, enablePullRefresh = false)
        }
    }
}

@Composable
private fun WatchFollowChanges(
    baseUser: User,
    feedViewModel: UserFeedViewModel,
) {
    val userState by baseUser.live().follows.observeAsState()

    LaunchedEffect(userState) { feedViewModel.invalidateData() }
}

@Composable
private fun WatchFollowerChanges(
    baseUser: User,
    feedViewModel: UserFeedViewModel,
) {
    val userState by baseUser.live().followers.observeAsState()

    LaunchedEffect(userState) { feedViewModel.invalidateData() }
}

@Composable
fun TabReceivedZaps(
    baseUser: User,
    zapFeedViewModel: NostrUserProfileZapsFeedViewModel,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    WatchZapsAndUpdateFeed(baseUser, zapFeedViewModel)

    Column(Modifier.fillMaxHeight()) {
        Column { LnZapFeedView(zapFeedViewModel, accountViewModel, nav) }
    }
}

@Composable
private fun WatchZapsAndUpdateFeed(
    baseUser: User,
    feedViewModel: NostrUserProfileZapsFeedViewModel,
) {
    val userState by baseUser.live().zaps.observeAsState()

    LaunchedEffect(userState) { feedViewModel.invalidateData() }
}

@Composable
fun TabReports(
    baseUser: User,
    feedViewModel: NostrUserProfileReportFeedViewModel,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    WatchReportsAndUpdateFeed(baseUser, feedViewModel)

    Column(Modifier.fillMaxHeight()) {
        Column {
            RefresheableFeedView(
                feedViewModel,
                null,
                enablePullRefresh = false,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

@Composable
private fun WatchReportsAndUpdateFeed(
    baseUser: User,
    feedViewModel: NostrUserProfileReportFeedViewModel,
) {
    val userState by baseUser.live().reports.observeAsState()
    LaunchedEffect(userState) { feedViewModel.invalidateData() }
}

@Composable
fun TabRelays(
    user: User,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val feedViewModel: RelayFeedViewModel = viewModel()

    val lifeCycleOwner = LocalLifecycleOwner.current

    DisposableEffect(user) {
        feedViewModel.subscribeTo(user)
        onDispose { feedViewModel.unsubscribeTo(user) }
    }

    DisposableEffect(lifeCycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
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
            modifier = Modifier.padding(vertical = 0.dp),
        ) {
            RelayFeedView(feedViewModel, accountViewModel, enablePullRefresh = false, nav = nav)
        }
    }
}

@Composable
private fun MessageButton(
    user: User,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()

    Button(
        modifier =
            Modifier
                .padding(horizontal = 3.dp)
                .width(50.dp),
        onClick = {
            scope.launch(Dispatchers.IO) { accountViewModel.createChatRoomFor(user) { nav("Room/$it") } }
        },
        contentPadding = ZeroPadding,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_dm),
            stringResource(R.string.send_a_direct_message),
            modifier = Modifier.size(20.dp),
            tint = Color.White,
        )
    }
}

@Composable
private fun EditButton(account: Account) {
    var wantsToEdit by remember { mutableStateOf(false) }

    if (wantsToEdit) {
        NewUserMetadataView({ wantsToEdit = false }, account)
    }

    InnerEditButton { wantsToEdit = true }
}

@Preview
@Composable
private fun InnerEditButtonPreview() {
    InnerEditButton {}
}

@Composable
private fun InnerEditButton(onClick: () -> Unit) {
    Button(
        modifier =
            Modifier
                .padding(horizontal = 3.dp)
                .width(50.dp),
        onClick = onClick,
        contentPadding = ZeroPadding,
    ) {
        Icon(
            tint = Color.White,
            imageVector = Icons.Default.EditNote,
            contentDescription = stringResource(R.string.edits_the_user_s_metadata),
        )
    }
}

@Composable
fun UnfollowButton(onClick: () -> Unit) {
    Button(
        modifier = Modifier.padding(horizontal = 3.dp),
        onClick = onClick,
        shape = ButtonBorder,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
            ),
        contentPadding = ButtonPadding,
    ) {
        Text(text = stringResource(R.string.unfollow), color = Color.White)
    }
}

@Composable
fun FollowButton(
    text: Int = R.string.follow,
    onClick: () -> Unit,
) {
    Button(
        modifier = Modifier.padding(start = 3.dp),
        onClick = onClick,
        shape = ButtonBorder,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
            ),
        contentPadding = ButtonPadding,
    ) {
        Text(text = stringResource(text), color = Color.White, textAlign = TextAlign.Center)
    }
}

@Composable
fun ShowUserButton(onClick: () -> Unit) {
    Button(
        modifier = Modifier.padding(start = 3.dp),
        onClick = onClick,
        shape = ButtonBorder,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
            ),
        contentPadding = ButtonPadding,
    ) {
        Text(text = stringResource(R.string.unblock), color = Color.White)
    }
}

@Composable
fun UserProfileDropDownMenu(
    user: User,
    popupExpanded: Boolean,
    onDismiss: () -> Unit,
    accountViewModel: AccountViewModel,
) {
    DropdownMenu(
        expanded = popupExpanded,
        onDismissRequest = onDismiss,
    ) {
        val clipboardManager = LocalClipboardManager.current

        DropdownMenuItem(
            text = { Text(stringResource(R.string.copy_user_id)) },
            onClick = {
                clipboardManager.setText(AnnotatedString(user.pubkeyNpub()))
                onDismiss()
            },
        )

        if (accountViewModel.userProfile() != user) {
            HorizontalDivider(thickness = DividerThickness)
            if (accountViewModel.account.isHidden(user)) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.unblock_user)) },
                    onClick = {
                        accountViewModel.show(user)
                        onDismiss()
                    },
                )
            } else {
                DropdownMenuItem(
                    text = { Text(stringResource(id = R.string.block_hide_user)) },
                    onClick = {
                        accountViewModel.hide(user)
                        onDismiss()
                    },
                )
            }
            HorizontalDivider(thickness = DividerThickness)
            DropdownMenuItem(
                text = { Text(stringResource(id = R.string.report_spam_scam)) },
                onClick = {
                    accountViewModel.report(user, ReportEvent.ReportType.SPAM)
                    onDismiss()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.report_hateful_speech)) },
                onClick = {
                    accountViewModel.report(user, ReportEvent.ReportType.PROFANITY)
                    onDismiss()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(id = R.string.report_impersonation)) },
                onClick = {
                    accountViewModel.report(user, ReportEvent.ReportType.IMPERSONATION)
                    onDismiss()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.report_nudity_porn)) },
                onClick = {
                    accountViewModel.report(user, ReportEvent.ReportType.NUDITY)
                    onDismiss()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(id = R.string.report_illegal_behaviour)) },
                onClick = {
                    accountViewModel.report(user, ReportEvent.ReportType.ILLEGAL)
                    onDismiss()
                },
            )
        }
    }
}
