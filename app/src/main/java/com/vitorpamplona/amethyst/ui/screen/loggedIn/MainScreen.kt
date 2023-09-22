package com.vitorpamplona.amethyst.ui.screen.loggedIn

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.with
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vitorpamplona.amethyst.ui.buttons.ChannelFabColumn
import com.vitorpamplona.amethyst.ui.buttons.NewCommunityNoteButton
import com.vitorpamplona.amethyst.ui.buttons.NewImageButton
import com.vitorpamplona.amethyst.ui.buttons.NewNoteButton
import com.vitorpamplona.amethyst.ui.navigation.*
import com.vitorpamplona.amethyst.ui.navigation.Route.Companion.InvertedLayouts
import com.vitorpamplona.amethyst.ui.note.UserReactionsViewModel
import com.vitorpamplona.amethyst.ui.screen.AccountState
import com.vitorpamplona.amethyst.ui.screen.AccountStateViewModel
import com.vitorpamplona.amethyst.ui.screen.NostrChatroomListKnownFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.NostrChatroomListNewFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.NostrDiscoverChatFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.NostrDiscoverCommunityFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.NostrDiscoverLiveFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.NostrHomeFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.NostrHomeRepliesFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.NostrVideoFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.NotificationViewModel
import com.vitorpamplona.amethyst.ui.screen.ThemeViewModel
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalMaterialApi::class, ExperimentalAnimationApi::class)
@Composable
fun MainScreen(
    accountViewModel: AccountViewModel,
    accountStateViewModel: AccountStateViewModel,
    themeViewModel: ThemeViewModel
) {
    val scope = rememberCoroutineScope()
    val scaffoldState = rememberScaffoldState(rememberDrawerState(DrawerValue.Closed))
    val sheetState = rememberModalBottomSheetState(
        initialValue = ModalBottomSheetValue.Hidden,
        confirmValueChange = { it != ModalBottomSheetValue.HalfExpanded },
        skipHalfExpanded = true
    )

    val navController = rememberNavController()
    val navState = navController.currentBackStackEntryAsState()

    val orientation = LocalConfiguration.current.orientation
    val currentDrawerState = scaffoldState.drawerState.currentValue
    LaunchedEffect(key1 = orientation) {
        if (orientation == Configuration.ORIENTATION_LANDSCAPE && currentDrawerState == DrawerValue.Closed) {
            scaffoldState.drawerState.close()
        }
    }

    val nav = remember(navController) {
        { route: String ->
            scope.launch {
                if (getRouteWithArguments(navController) != route) {
                    navController.navigate(route)
                }
            }
            Unit
        }
    }

    val navPopBack = remember(navController) {
        {
            navController.popBackStack()
            Unit
        }
    }

    val followLists: FollowListViewModel = viewModel(
        key = accountViewModel.userProfile().pubkeyHex + "FollowListViewModel",
        factory = FollowListViewModel.Factory(accountViewModel.account)
    )

    // Avoids creating ViewModels for performance reasons (up to 1 second delays)
    val homeFeedViewModel: NostrHomeFeedViewModel = viewModel(
        key = accountViewModel.userProfile().pubkeyHex + "NostrHomeFeedViewModel",
        factory = NostrHomeFeedViewModel.Factory(accountViewModel.account)
    )

    val repliesFeedViewModel: NostrHomeRepliesFeedViewModel = viewModel(
        key = accountViewModel.userProfile().pubkeyHex + "NostrHomeRepliesFeedViewModel",
        factory = NostrHomeRepliesFeedViewModel.Factory(accountViewModel.account)
    )

    val videoFeedViewModel: NostrVideoFeedViewModel = viewModel(
        key = accountViewModel.userProfile().pubkeyHex + "NostrVideoFeedViewModel",
        factory = NostrVideoFeedViewModel.Factory(accountViewModel.account)
    )

    val discoveryLiveFeedViewModel: NostrDiscoverLiveFeedViewModel = viewModel(
        key = accountViewModel.userProfile().pubkeyHex + "NostrDiscoveryLiveFeedViewModel",
        factory = NostrDiscoverLiveFeedViewModel.Factory(accountViewModel.account)
    )

    val discoveryCommunityFeedViewModel: NostrDiscoverCommunityFeedViewModel = viewModel(
        key = accountViewModel.userProfile().pubkeyHex + "NostrDiscoveryCommunityFeedViewModel",
        factory = NostrDiscoverCommunityFeedViewModel.Factory(accountViewModel.account)
    )

    val discoveryChatFeedViewModel: NostrDiscoverChatFeedViewModel = viewModel(
        key = accountViewModel.userProfile().pubkeyHex + "NostrDiscoveryChatFeedViewModel",
        factory = NostrDiscoverChatFeedViewModel.Factory(accountViewModel.account)
    )

    val notifFeedViewModel: NotificationViewModel = viewModel(
        key = accountViewModel.userProfile().pubkeyHex + "NotificationViewModel",
        factory = NotificationViewModel.Factory(accountViewModel.account)
    )

    val userReactionsStatsModel: UserReactionsViewModel = viewModel(
        key = accountViewModel.userProfile().pubkeyHex + "UserReactionsViewModel",
        factory = UserReactionsViewModel.Factory(accountViewModel.account)
    )

    val knownFeedViewModel: NostrChatroomListKnownFeedViewModel = viewModel(
        key = accountViewModel.userProfile().pubkeyHex + "NostrChatroomListKnownFeedViewModel",
        factory = NostrChatroomListKnownFeedViewModel.Factory(accountViewModel.account)
    )

    val newFeedViewModel: NostrChatroomListNewFeedViewModel = viewModel(
        key = accountViewModel.userProfile().pubkeyHex + "NostrChatroomListNewFeedViewModel",
        factory = NostrChatroomListNewFeedViewModel.Factory(accountViewModel.account)
    )

    val navBottomRow = remember(navController) {
        { route: Route, selected: Boolean ->
            if (!selected) {
                navController.navigate(route.base) {
                    popUpTo(Route.Home.route)
                    launchSingleTop = true
                }
            } else {
                // deals with scroll to top here to avoid passing as parameter
                // and having to deal with all recompositions with scroll to top true
                when (route.base) {
                    Route.Home.base -> {
                        homeFeedViewModel.sendToTop()
                        repliesFeedViewModel.sendToTop()
                    }
                    Route.Video.base -> {
                        videoFeedViewModel.sendToTop()
                    }
                    Route.Discover.base -> {
                        discoveryLiveFeedViewModel.sendToTop()
                        discoveryCommunityFeedViewModel.sendToTop()
                        discoveryChatFeedViewModel.sendToTop()
                    }
                    Route.Notification.base -> {
                        notifFeedViewModel.invalidateDataAndSendToTop()
                    }
                }

                navController.navigate(route.route) {
                    popUpTo(route.route)
                    launchSingleTop = true
                }
            }
        }
    }

    val bottomBarHeightPx = with(LocalDensity.current) { 50.dp.roundToPx().toFloat() }
    val bottomBarOffsetHeightPx = remember { mutableStateOf(0f) }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                val newOffset = bottomBarOffsetHeightPx.value + delta

                val currentRoute = navState.value?.destination?.route

                bottomBarOffsetHeightPx.value = if (currentRoute !in InvertedLayouts) {
                    newOffset.coerceIn(-bottomBarHeightPx, 0f)
                } else {
                    newOffset.coerceIn(0f, bottomBarHeightPx)
                }

                return Offset.Zero
            }
        }
    }

    val shouldShow = remember {
        derivedStateOf {
            abs(bottomBarOffsetHeightPx.value) < bottomBarHeightPx / 2.0f
        }
    }

    WatchNavStateToUpdateBarVisibility(navState, bottomBarOffsetHeightPx)

    ModalBottomSheetLayout(
        sheetState = sheetState,
        sheetContent = {
            AccountSwitchBottomSheet(accountViewModel = accountViewModel, accountStateViewModel = accountStateViewModel)
        }
    ) {
        Scaffold(
            modifier = Modifier
                .background(MaterialTheme.colors.primaryVariant)
                .statusBarsPadding()
                .nestedScroll(nestedScrollConnection),
            bottomBar = {
                AnimatedContent(
                    targetState = shouldShow.value,
                    transitionSpec = {
                        slideInVertically { height -> height } togetherWith
                            slideOutVertically { height -> height }
                    }
                ) { isVisible ->
                    if (isVisible) {
                        AppBottomBar(accountViewModel, navState, navBottomRow)
                    }
                }
            },
            topBar = {
                AnimatedContent(
                    targetState = shouldShow.value,
                    transitionSpec = {
                        slideInVertically { height -> 0 } togetherWith
                            slideOutVertically { height -> 0 }
                    }
                ) { isVisible ->
                    if (isVisible) {
                        AppTopBar(
                            followLists,
                            navState,
                            scaffoldState,
                            accountViewModel,
                            nav = nav,
                            navPopBack
                        )
                    }
                }
            },
            drawerContent = {
                DrawerContent(nav, scaffoldState, sheetState, accountViewModel)
                BackHandler(enabled = scaffoldState.drawerState.isOpen) {
                    scope.launch { scaffoldState.drawerState.close() }
                }
            },
            floatingActionButton = {
                Crossfade(
                    targetState = shouldShow.value,
                    animationSpec = tween(durationMillis = 100)
                ) { state ->
                    if (state) {
                        FloatingButtons(
                            navState,
                            accountViewModel,
                            accountStateViewModel,
                            nav,
                            navBottomRow
                        )
                    }
                }
            },
            scaffoldState = scaffoldState
        ) {
            Column(
                modifier = Modifier
                    .padding(bottom = it.calculateBottomPadding())
            ) {
                AppNavigation(
                    homeFeedViewModel = homeFeedViewModel,
                    repliesFeedViewModel = repliesFeedViewModel,
                    knownFeedViewModel = knownFeedViewModel,
                    newFeedViewModel = newFeedViewModel,
                    videoFeedViewModel = videoFeedViewModel,
                    discoveryLiveFeedViewModel = discoveryLiveFeedViewModel,
                    discoveryCommunityFeedViewModel = discoveryCommunityFeedViewModel,
                    discoveryChatFeedViewModel = discoveryChatFeedViewModel,
                    notifFeedViewModel = notifFeedViewModel,
                    userReactionsStatsModel = userReactionsStatsModel,
                    navController = navController,
                    accountViewModel = accountViewModel,
                    themeViewModel = themeViewModel
                )
            }
        }
    }
}

@Composable
fun WatchNavStateToUpdateBarVisibility(navState: State<NavBackStackEntry?>, bottomBarOffsetHeightPx: MutableState<Float>) {
    LaunchedEffect(key1 = navState.value) {
        bottomBarOffsetHeightPx.value = 0f
    }
}

@Composable
fun FloatingButtons(
    navEntryState: State<NavBackStackEntry?>,
    accountViewModel: AccountViewModel,
    accountStateViewModel: AccountStateViewModel,
    nav: (String) -> Unit,
    navScrollToTop: (Route, Boolean) -> Unit
) {
    val accountState by accountStateViewModel.accountContent.collectAsState()
    val context = LocalContext.current

    Crossfade(targetState = accountState, animationSpec = tween(durationMillis = 100)) { state ->
        when (state) {
            is AccountState.LoggedInViewOnly -> {
                if (accountViewModel.loggedInWithExternalSigner()) {
                    WritePermissionButtons(navEntryState, accountViewModel, nav, navScrollToTop)
                }
            }
            is AccountState.LoggedOff -> {
                // Does nothing.
            }
            is AccountState.LoggedIn -> {
                WritePermissionButtons(navEntryState, accountViewModel, nav, navScrollToTop)
            }
        }
    }
}

@Composable
private fun WritePermissionButtons(
    navEntryState: State<NavBackStackEntry?>,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
    navScrollToTop: (Route, Boolean) -> Unit
) {
    val currentRoute by remember(navEntryState.value) {
        derivedStateOf {
            navEntryState.value?.destination?.route?.substringBefore("?")
        }
    }

    when (currentRoute) {
        Route.Home.base -> NewNoteButton(accountViewModel, nav)
        Route.Message.base -> ChannelFabColumn(accountViewModel, nav)
        Route.Video.base -> NewImageButton(accountViewModel, nav, navScrollToTop)
        Route.Community.base -> {
            val communityId by remember(navEntryState.value) {
                derivedStateOf {
                    navEntryState.value?.arguments?.getString("id")
                }
            }

            communityId?.let {
                NewCommunityNoteButton(it, accountViewModel, nav)
            }
        }
    }
}
