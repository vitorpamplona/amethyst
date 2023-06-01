package com.vitorpamplona.amethyst.ui.screen.loggedIn

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.*
import androidx.compose.material.DrawerValue
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ModalBottomSheetLayout
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.Scaffold
import androidx.compose.material.rememberDrawerState
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.vitorpamplona.amethyst.ui.buttons.ChannelFabColumn
import com.vitorpamplona.amethyst.ui.buttons.NewNoteButton
import com.vitorpamplona.amethyst.ui.navigation.*
import com.vitorpamplona.amethyst.ui.navigation.AccountSwitchBottomSheet
import com.vitorpamplona.amethyst.ui.navigation.AppBottomBar
import com.vitorpamplona.amethyst.ui.navigation.AppNavigation
import com.vitorpamplona.amethyst.ui.navigation.AppTopBar
import com.vitorpamplona.amethyst.ui.navigation.DrawerContent
import com.vitorpamplona.amethyst.ui.navigation.Route
import com.vitorpamplona.amethyst.ui.navigation.currentRoute
import com.vitorpamplona.amethyst.ui.note.UserReactionsViewModel
import com.vitorpamplona.amethyst.ui.screen.AccountState
import com.vitorpamplona.amethyst.ui.screen.AccountStateViewModel
import com.vitorpamplona.amethyst.ui.screen.NostrChatroomListKnownFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.NostrChatroomListNewFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.NostrGlobalFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.NostrHomeFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.NostrHomeRepliesFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.NostrVideoFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.NotificationViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun MainScreen(accountViewModel: AccountViewModel, accountStateViewModel: AccountStateViewModel, startingPage: String? = null) {
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()
    val scaffoldState = rememberScaffoldState(rememberDrawerState(DrawerValue.Closed))
    val sheetState = rememberModalBottomSheetState(
        initialValue = ModalBottomSheetValue.Hidden,
        confirmValueChange = { it != ModalBottomSheetValue.HalfExpanded },
        skipHalfExpanded = true
    )

    val nav = remember {
        { route: String ->
            if (getRouteWithArguments(navController) != route) {
                navController.navigate(route)
            }
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

    val searchFeedViewModel: NostrGlobalFeedViewModel = viewModel(
        key = accountViewModel.userProfile().pubkeyHex + "NostrGlobalFeedViewModel",
        factory = NostrGlobalFeedViewModel.Factory(accountViewModel.account)
    )

    val videoFeedViewModel: NostrVideoFeedViewModel = viewModel(
        key = accountViewModel.userProfile().pubkeyHex + "NostrVideoFeedViewModel",
        factory = NostrVideoFeedViewModel.Factory(accountViewModel.account)
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

    ModalBottomSheetLayout(
        sheetState = sheetState,
        sheetContent = {
            AccountSwitchBottomSheet(accountViewModel = accountViewModel, accountStateViewModel = accountStateViewModel)
        }
    ) {
        Scaffold(
            modifier = Modifier
                .background(MaterialTheme.colors.primaryVariant)
                .statusBarsPadding(),
            bottomBar = {
                AppBottomBar(navController, accountViewModel)
            },
            topBar = {
                AppTopBar(followLists, navController, scaffoldState, accountViewModel)
            },
            drawerContent = {
                DrawerContent(nav, scaffoldState, sheetState, accountViewModel)
                BackHandler(enabled = scaffoldState.drawerState.isOpen) {
                    scope.launch { scaffoldState.drawerState.close() }
                }
            },
            floatingActionButton = {
                FloatingButtons(navController, accountViewModel, accountStateViewModel)
            },
            scaffoldState = scaffoldState
        ) {
            Column(modifier = Modifier.padding(bottom = it.calculateBottomPadding())) {
                AppNavigation(
                    homeFeedViewModel,
                    repliesFeedViewModel,
                    knownFeedViewModel,
                    newFeedViewModel,
                    searchFeedViewModel,
                    videoFeedViewModel,
                    notifFeedViewModel,
                    userReactionsStatsModel,
                    navController,
                    accountViewModel,
                    startingPage
                )
            }
        }
    }
}

@Composable
fun FloatingButtons(navController: NavHostController, accountViewModel: AccountViewModel, accountStateViewModel: AccountStateViewModel) {
    val accountState by accountStateViewModel.accountContent.collectAsState()

    val nav = remember {
        { route: String ->
            if (getRouteWithArguments(navController) != route) {
                navController.navigate(route)
            }
        }
    }

    Crossfade(targetState = accountState, animationSpec = tween(durationMillis = 100)) { state ->
        when (state) {
            is AccountState.LoggedInViewOnly -> {
                // Does nothing.
            }
            is AccountState.LoggedOff -> {
                // Does nothing.
            }
            is AccountState.LoggedIn -> {
                if (currentRoute(navController)?.substringBefore("?") == Route.Home.base) {
                    NewNoteButton(accountViewModel, nav)
                }
                if (currentRoute(navController) == Route.Message.base) {
                    ChannelFabColumn(state.account, nav)
                }
                if (currentRoute(navController)?.substringBefore("?") == Route.Video.base) {
                    NewImageButton(accountViewModel, nav)
                }
            }
        }
    }
}
