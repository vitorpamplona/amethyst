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

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.BooleanType
import com.vitorpamplona.amethyst.ui.actions.InformationDialog
import com.vitorpamplona.amethyst.ui.actions.NotifyRequestDialog
import com.vitorpamplona.amethyst.ui.buttons.ChannelFabColumn
import com.vitorpamplona.amethyst.ui.buttons.NewCommunityNoteButton
import com.vitorpamplona.amethyst.ui.buttons.NewImageButton
import com.vitorpamplona.amethyst.ui.buttons.NewNoteButton
import com.vitorpamplona.amethyst.ui.navigation.AccountSwitchBottomSheet
import com.vitorpamplona.amethyst.ui.navigation.AppBottomBar
import com.vitorpamplona.amethyst.ui.navigation.AppNavigation
import com.vitorpamplona.amethyst.ui.navigation.AppTopBar
import com.vitorpamplona.amethyst.ui.navigation.DrawerContent
import com.vitorpamplona.amethyst.ui.navigation.FollowListViewModel
import com.vitorpamplona.amethyst.ui.navigation.Route
import com.vitorpamplona.amethyst.ui.navigation.Route.Companion.InvertedLayouts
import com.vitorpamplona.amethyst.ui.navigation.getRouteWithArguments
import com.vitorpamplona.amethyst.ui.note.UserReactionsViewModel
import com.vitorpamplona.amethyst.ui.screen.AccountState
import com.vitorpamplona.amethyst.ui.screen.AccountStateViewModel
import com.vitorpamplona.amethyst.ui.screen.NostrChatroomListKnownFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.NostrChatroomListNewFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.NostrDiscoverChatFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.NostrDiscoverCommunityFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.NostrDiscoverLiveFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.NostrDiscoverMarketplaceFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.NostrDiscoverNIP89FeedViewModel
import com.vitorpamplona.amethyst.ui.screen.NostrHomeFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.NostrHomeRepliesFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.NostrVideoFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.NotificationViewModel
import com.vitorpamplona.amethyst.ui.screen.SharedPreferencesViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.encoders.RelayUrlFormatter
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    accountViewModel: AccountViewModel,
    accountStateViewModel: AccountStateViewModel,
    sharedPreferencesViewModel: SharedPreferencesViewModel,
) {
    val scope = rememberCoroutineScope()
    var openBottomSheet by rememberSaveable { mutableStateOf(false) }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val sheetState =
        rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { it != SheetValue.PartiallyExpanded },
        )

    val openSheetFunction =
        remember {
            {
                scope.launch {
                    openBottomSheet = true
                    sheetState.show()
                }
                Unit
            }
        }

    val navController = rememberNavController()

    val orientation = LocalConfiguration.current.orientation
    val currentDrawerState = drawerState.currentValue
    LaunchedEffect(key1 = orientation) {
        if (
            orientation == Configuration.ORIENTATION_LANDSCAPE && currentDrawerState == DrawerValue.Closed
        ) {
            drawerState.close()
        }
    }

    val nav =
        remember(navController) {
            { route: String ->
                scope.launch {
                    if (getRouteWithArguments(navController) != route) {
                        navController.navigate(route)
                    }
                }
                Unit
            }
        }

    val navPopBack =
        remember(navController) {
            {
                navController.navigateUp()
                Unit
            }
        }

    DisplayErrorMessages(accountViewModel)
    DisplayNotifyMessages(accountViewModel, nav)

    val followListsViewModel: FollowListViewModel =
        viewModel(
            key = "FollowListViewModel",
            factory = FollowListViewModel.Factory(accountViewModel.account),
        )

    // Avoids creating ViewModels for performance reasons (up to 1 second delays)
    val homeFeedViewModel: NostrHomeFeedViewModel =
        viewModel(
            key = "NostrHomeFeedViewModel",
            factory = NostrHomeFeedViewModel.Factory(accountViewModel.account),
        )

    val repliesFeedViewModel: NostrHomeRepliesFeedViewModel =
        viewModel(
            key = "NostrHomeRepliesFeedViewModel",
            factory = NostrHomeRepliesFeedViewModel.Factory(accountViewModel.account),
        )

    val videoFeedViewModel: NostrVideoFeedViewModel =
        viewModel(
            key = "NostrVideoFeedViewModel",
            factory = NostrVideoFeedViewModel.Factory(accountViewModel.account),
        )

    val discoverMarketplaceFeedViewModel: NostrDiscoverMarketplaceFeedViewModel =
        viewModel(
            key = "NostrDiscoveryMarketplaceFeedViewModel",
            factory = NostrDiscoverMarketplaceFeedViewModel.Factory(accountViewModel.account),
        )

    val discoverNIP89FeedViewModel: NostrDiscoverNIP89FeedViewModel =
        viewModel(
            key = "NostrDiscoveryNIP89FeedViewModel",
            factory = NostrDiscoverNIP89FeedViewModel.Factory(accountViewModel.account),
        )

    val discoveryLiveFeedViewModel: NostrDiscoverLiveFeedViewModel =
        viewModel(
            key = "NostrDiscoveryLiveFeedViewModel",
            factory = NostrDiscoverLiveFeedViewModel.Factory(accountViewModel.account),
        )

    val discoveryCommunityFeedViewModel: NostrDiscoverCommunityFeedViewModel =
        viewModel(
            key = "NostrDiscoveryCommunityFeedViewModel",
            factory = NostrDiscoverCommunityFeedViewModel.Factory(accountViewModel.account),
        )

    val discoveryChatFeedViewModel: NostrDiscoverChatFeedViewModel =
        viewModel(
            key = "NostrDiscoveryChatFeedViewModel",
            factory = NostrDiscoverChatFeedViewModel.Factory(accountViewModel.account),
        )

    val notifFeedViewModel: NotificationViewModel =
        viewModel(
            key = "NotificationViewModel",
            factory = NotificationViewModel.Factory(accountViewModel.account),
        )

    val userReactionsStatsModel: UserReactionsViewModel =
        viewModel(
            key = "UserReactionsViewModel",
            factory = UserReactionsViewModel.Factory(accountViewModel.account),
        )

    val knownFeedViewModel: NostrChatroomListKnownFeedViewModel =
        viewModel(
            key = "NostrChatroomListKnownFeedViewModel",
            factory = NostrChatroomListKnownFeedViewModel.Factory(accountViewModel.account),
        )

    val newFeedViewModel: NostrChatroomListNewFeedViewModel =
        viewModel(
            key = "NostrChatroomListNewFeedViewModel",
            factory = NostrChatroomListNewFeedViewModel.Factory(accountViewModel.account),
        )

    val navBottomRow =
        remember(navController) {
            { route: Route, selected: Boolean ->
                scope.launch {
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
                                discoverMarketplaceFeedViewModel.sendToTop()
                                discoveryLiveFeedViewModel.sendToTop()
                                discoveryCommunityFeedViewModel.sendToTop()
                                discoveryChatFeedViewModel.sendToTop()
                            }
                            Route.Notification.base -> {
                                notifFeedViewModel.invalidateDataAndSendToTop(true)
                            }
                        }

                        navController.navigate(route.route) {
                            popUpTo(route.route)
                            launchSingleTop = true
                        }
                    }
                }

                Unit
            }
        }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            DrawerContent(nav, drawerState, openSheetFunction, accountViewModel)
            BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }
        },
        content = {
            MainScaffold(
                navController = navController,
                navBottomRow = navBottomRow,
                navPopBack = navPopBack,
                openDrawer = { scope.launch { drawerState.open() } },
                accountStateViewModel = accountStateViewModel,
                userReactionsStatsModel = userReactionsStatsModel,
                followListsViewModel = followListsViewModel,
                homeFeedViewModel = homeFeedViewModel,
                repliesFeedViewModel = repliesFeedViewModel,
                knownFeedViewModel = knownFeedViewModel,
                newFeedViewModel = newFeedViewModel,
                videoFeedViewModel = videoFeedViewModel,
                discoverNIP89FeedViewModel = discoverNIP89FeedViewModel,
                discoverMarketplaceFeedViewModel = discoverMarketplaceFeedViewModel,
                discoveryLiveFeedViewModel = discoveryLiveFeedViewModel,
                discoveryCommunityFeedViewModel = discoveryCommunityFeedViewModel,
                discoveryChatFeedViewModel = discoveryChatFeedViewModel,
                notifFeedViewModel = notifFeedViewModel,
                sharedPreferencesViewModel = sharedPreferencesViewModel,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        },
    )

    // Sheet content
    if (openBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                scope
                    .launch { sheetState.hide() }
                    .invokeOnCompletion {
                        if (!sheetState.isVisible) {
                            openBottomSheet = false
                        }
                    }
            },
            sheetState = sheetState,
        ) {
            AccountSwitchBottomSheet(
                accountViewModel = accountViewModel,
                accountStateViewModel = accountStateViewModel,
            )
        }
    }
}

@Composable
private fun MainScaffold(
    navController: NavHostController,
    navBottomRow: (Route, Boolean) -> Unit,
    navPopBack: () -> Unit,
    openDrawer: () -> Unit,
    accountStateViewModel: AccountStateViewModel,
    userReactionsStatsModel: UserReactionsViewModel,
    followListsViewModel: FollowListViewModel,
    homeFeedViewModel: NostrHomeFeedViewModel,
    repliesFeedViewModel: NostrHomeRepliesFeedViewModel,
    knownFeedViewModel: NostrChatroomListKnownFeedViewModel,
    newFeedViewModel: NostrChatroomListNewFeedViewModel,
    videoFeedViewModel: NostrVideoFeedViewModel,
    discoverNIP89FeedViewModel: NostrDiscoverNIP89FeedViewModel,
    discoverMarketplaceFeedViewModel: NostrDiscoverMarketplaceFeedViewModel,
    discoveryLiveFeedViewModel: NostrDiscoverLiveFeedViewModel,
    discoveryCommunityFeedViewModel: NostrDiscoverCommunityFeedViewModel,
    discoveryChatFeedViewModel: NostrDiscoverChatFeedViewModel,
    notifFeedViewModel: NotificationViewModel,
    sharedPreferencesViewModel: SharedPreferencesViewModel,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val navState = navController.currentBackStackEntryAsState()

    val shouldShow = remember { mutableStateOf(true) }

    val modifier =
        if (accountViewModel.settings.automaticallyHideNavigationBars == BooleanType.ALWAYS) {
            val bottomBarHeightPx = with(LocalDensity.current) { 50.dp.roundToPx().toFloat() }
            val bottomBarOffsetHeightPx = remember { mutableFloatStateOf(0f) }

            val nestedScrollConnection =
                remember {
                    object : NestedScrollConnection {
                        override fun onPreScroll(
                            available: Offset,
                            source: NestedScrollSource,
                        ): Offset {
                            val newOffset = bottomBarOffsetHeightPx.floatValue + available.y

                            if (accountViewModel.settings.automaticallyHideNavigationBars == BooleanType.ALWAYS) {
                                val newBottomBarOffset =
                                    if (navState.value?.destination?.route !in InvertedLayouts) {
                                        newOffset.coerceIn(-bottomBarHeightPx, 0f)
                                    } else {
                                        newOffset.coerceIn(0f, bottomBarHeightPx)
                                    }

                                if (newBottomBarOffset != bottomBarOffsetHeightPx.floatValue) {
                                    bottomBarOffsetHeightPx.floatValue = newBottomBarOffset
                                }
                            } else {
                                if (abs(bottomBarOffsetHeightPx.floatValue) > 0.1) {
                                    bottomBarOffsetHeightPx.floatValue = 0f
                                }
                            }

                            val newShouldShow = abs(bottomBarOffsetHeightPx.floatValue) < bottomBarHeightPx / 2.0f

                            if (shouldShow.value != newShouldShow) {
                                shouldShow.value = newShouldShow
                            }

                            return Offset.Zero
                        }
                    }
                }

            WatchNavStateToUpdateBarVisibility(navState) {
                bottomBarOffsetHeightPx.floatValue = 0f
                shouldShow.value = true
            }

            Modifier
                .statusBarsPadding()
                .nestedScroll(nestedScrollConnection)
        } else {
            Modifier
                .statusBarsPadding()
        }

    Scaffold(
        modifier = modifier,
        bottomBar = {
            AnimatedContent(
                targetState = shouldShow.value,
                transitionSpec = AnimatedContentTransitionScope<Boolean>::bottomBarTransitionSpec,
                label = "BottomBarAnimatedContent",
            ) { isVisible ->
                if (isVisible) {
                    AppBottomBar(accountViewModel, navState, navBottomRow)
                }
            }
        },
        topBar = {
            AnimatedContent(
                targetState = shouldShow.value,
                transitionSpec = AnimatedContentTransitionScope<Boolean>::topBarTransitionSpec,
                label = "TopBarAnimatedContent",
            ) { isVisible ->
                if (isVisible) {
                    AppTopBar(
                        followListsViewModel,
                        navState,
                        openDrawer,
                        accountViewModel,
                        nav = nav,
                        navPopBack,
                    )
                }
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = shouldShow.value,
                enter = remember { scaleIn() },
                exit = remember { scaleOut() },
            ) {
                Box(
                    modifier = Modifier.defaultMinSize(minWidth = 55.dp, minHeight = 55.dp),
                ) {
                    FloatingButtons(
                        navState,
                        accountViewModel,
                        accountStateViewModel,
                        nav,
                        navBottomRow,
                    )
                }
            }
        },
    ) {
        Column(
            modifier =
                Modifier
                    .padding(it)
                    .consumeWindowInsets(it)
                    .systemBarsPadding()
                    .imePadding(),
        ) {
            AppNavigation(
                homeFeedViewModel = homeFeedViewModel,
                repliesFeedViewModel = repliesFeedViewModel,
                knownFeedViewModel = knownFeedViewModel,
                newFeedViewModel = newFeedViewModel,
                videoFeedViewModel = videoFeedViewModel,
                discoverNip89FeedViewModel = discoverNIP89FeedViewModel,
                discoverMarketplaceFeedViewModel = discoverMarketplaceFeedViewModel,
                discoveryLiveFeedViewModel = discoveryLiveFeedViewModel,
                discoveryCommunityFeedViewModel = discoveryCommunityFeedViewModel,
                discoveryChatFeedViewModel = discoveryChatFeedViewModel,
                notifFeedViewModel = notifFeedViewModel,
                userReactionsStatsModel = userReactionsStatsModel,
                navController = navController,
                accountViewModel = accountViewModel,
                sharedPreferencesViewModel = sharedPreferencesViewModel,
            )
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
private fun <S> AnimatedContentTransitionScope<S>.topBarTransitionSpec(): ContentTransform = topBarAnimation

@OptIn(ExperimentalAnimationApi::class)
private fun <S> AnimatedContentTransitionScope<S>.bottomBarTransitionSpec(): ContentTransform = bottomBarAnimation

@ExperimentalAnimationApi
val topBarAnimation: ContentTransform =
    slideInVertically { height -> 0 } togetherWith slideOutVertically { height -> 0 }

val bottomBarAnimation: ContentTransform =
    slideInVertically { height -> height } togetherWith slideOutVertically { height -> height }

@Composable
private fun DisplayErrorMessages(accountViewModel: AccountViewModel) {
    val openDialogMsg = accountViewModel.toasts.collectAsStateWithLifecycle(null)

    openDialogMsg.value?.let { obj ->
        when (obj) {
            is ResourceToastMsg ->
                if (obj.params != null) {
                    InformationDialog(
                        stringRes(obj.titleResId),
                        stringRes(obj.resourceId, *obj.params),
                    ) {
                        accountViewModel.clearToasts()
                    }
                } else {
                    InformationDialog(
                        stringRes(obj.titleResId),
                        stringRes(obj.resourceId),
                    ) {
                        accountViewModel.clearToasts()
                    }
                }

            is StringToastMsg ->
                InformationDialog(
                    obj.title,
                    obj.msg,
                ) {
                    accountViewModel.clearToasts()
                }
        }
    }
}

@Composable
private fun DisplayNotifyMessages(
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val openDialogMsg =
        accountViewModel.account.transientPaymentRequests.collectAsStateWithLifecycle(null)

    openDialogMsg.value?.firstOrNull()?.let { request ->
        NotifyRequestDialog(
            title =
                stringRes(
                    id = R.string.payment_required_title,
                    RelayUrlFormatter.displayUrl(request.relayUrl),
                ),
            textContent = request.description,
            accountViewModel = accountViewModel,
            nav = nav,
        ) {
            accountViewModel.dismissPaymentRequest(request)
        }
    }
}

@Composable
fun WatchNavStateToUpdateBarVisibility(
    navState: State<NavBackStackEntry?>,
    onReset: () -> Unit,
) {
    LaunchedEffect(key1 = navState.value) { onReset() }

    val lifeCycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifeCycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    onReset()
                }
            }

        lifeCycleOwner.lifecycle.addObserver(observer)
        onDispose { lifeCycleOwner.lifecycle.removeObserver(observer) }
    }
}

@Composable
fun FloatingButtons(
    navEntryState: State<NavBackStackEntry?>,
    accountViewModel: AccountViewModel,
    accountStateViewModel: AccountStateViewModel,
    nav: (String) -> Unit,
    navScrollToTop: (Route, Boolean) -> Unit,
) {
    val accountState by accountStateViewModel.accountContent.collectAsStateWithLifecycle()

    when (accountState) {
        is AccountState.Loading -> {
            // Does nothing.
        }
        is AccountState.LoggedInViewOnly -> {
            WritePermissionButtons(navEntryState, accountViewModel, nav, navScrollToTop)
        }
        is AccountState.LoggedOff -> {
            // Does nothing.
        }
        is AccountState.LoggedIn -> {
            WritePermissionButtons(navEntryState, accountViewModel, nav, navScrollToTop)
        }
    }
}

@Composable
private fun WritePermissionButtons(
    navEntryState: State<NavBackStackEntry?>,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
    navScrollToTop: (Route, Boolean) -> Unit,
) {
    val currentRoute by
        remember(navEntryState.value) {
            derivedStateOf {
                navEntryState.value
                    ?.destination
                    ?.route
                    ?.substringBefore("?")
            }
        }

    when (currentRoute) {
        Route.Home.base -> NewNoteButton(accountViewModel, nav)
        Route.Message.base -> {
            if (
                accountViewModel.settings.windowSizeClass.value
                    ?.widthSizeClass ==
                WindowWidthSizeClass.Compact
            ) {
                ChannelFabColumn(accountViewModel, nav)
            }
        }
        Route.Video.base -> NewImageButton(accountViewModel, nav, navScrollToTop)
        Route.Community.base -> {
            val communityId by
                remember(navEntryState.value) {
                    derivedStateOf { navEntryState.value?.arguments?.getString("id") }
                }

            communityId?.let { NewCommunityNoteButton(it, accountViewModel, nav) }
        }
    }
}
