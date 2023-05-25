package com.vitorpamplona.amethyst.ui.navigation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.vitorpamplona.amethyst.ui.dal.GlobalFeedFilter
import com.vitorpamplona.amethyst.ui.dal.HomeConversationsFeedFilter
import com.vitorpamplona.amethyst.ui.dal.HomeNewThreadFeedFilter
import com.vitorpamplona.amethyst.ui.dal.NotificationFeedFilter
import com.vitorpamplona.amethyst.ui.dal.VideoFeedFilter
import com.vitorpamplona.amethyst.ui.note.UserReactionsViewModel
import com.vitorpamplona.amethyst.ui.screen.NostrGlobalFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.NostrHomeFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.NostrHomeRepliesFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.NostrVideoFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.NotificationViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.BookmarkListScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.ChannelScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.ChatroomListScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.ChatroomScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.HashtagScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.HiddenUsersScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.HomeScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.LoadRedirectScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.NotificationScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.ProfileScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.SearchScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.ThreadScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.VideoScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppNavigation(
    navController: NavHostController,
    accountViewModel: AccountViewModel,
    nextPage: String? = null
) {
    val homePagerState = rememberPagerState()
    var actionableNextPage by remember { mutableStateOf<String?>(nextPage) }

    // Avoids creating ViewModels for performance reasons (up to 1 second delays)
    val accountState by accountViewModel.accountLiveData.observeAsState()
    val account = remember(accountState) { accountState?.account } ?: return
    val accountHex = remember(accountState) { accountState?.account?.userProfile()?.pubkeyHex }

    HomeNewThreadFeedFilter.account = account
    HomeConversationsFeedFilter.account = account

    val homeFeedViewModel: NostrHomeFeedViewModel = viewModel()
    val repliesFeedViewModel: NostrHomeRepliesFeedViewModel = viewModel()

    GlobalFeedFilter.account = account
    val searchFeedViewModel: NostrGlobalFeedViewModel = viewModel()

    VideoFeedFilter.account = account
    val videoFeedViewModel: NostrVideoFeedViewModel = viewModel()

    NotificationFeedFilter.account = account
    val notifFeedViewModel: NotificationViewModel = viewModel()

    val userReactionsStatsModel: UserReactionsViewModel = viewModel()
    val scope = rememberCoroutineScope()

    LaunchedEffect(accountHex) {
        scope.launch(Dispatchers.IO) {
            userReactionsStatsModel.load(account.userProfile())
            userReactionsStatsModel.initializeSuspend()
        }
    }

    val nav = remember {
        { route: String ->
            if (getRouteWithArguments(navController) != route) {
                navController.navigate(route)
            }
        }
    }

    NavHost(navController, startDestination = Route.Home.route) {
        Route.Video.let { route ->
            composable(route.route, route.arguments, content = {
                val scrollToTop = it.arguments?.getBoolean("scrollToTop") ?: false

                VideoScreen(
                    videoFeedView = videoFeedViewModel,
                    accountViewModel = accountViewModel,
                    nav = nav,
                    scrollToTop = scrollToTop
                )

                // Avoids running scroll to top when back button is pressed
                // Changes this on a thread to avoid changing before it finishes the composition
                if (scrollToTop) {
                    LaunchedEffect(key1 = Unit) {
                        scope.launch {
                            delay(1000)
                            it.arguments?.remove("scrollToTop")
                        }
                    }
                }
            })
        }

        Route.Search.let { route ->
            composable(route.route, route.arguments, content = {
                val scrollToTop = it.arguments?.getBoolean("scrollToTop") ?: false

                SearchScreen(
                    searchFeedViewModel = searchFeedViewModel,
                    accountViewModel = accountViewModel,
                    nav = nav,
                    scrollToTop = scrollToTop
                )

                // Avoids running scroll to top when back button is pressed
                // Changes this on a thread to avoid changing before it finishes the composition
                if (scrollToTop) {
                    LaunchedEffect(key1 = Unit) {
                        scope.launch {
                            delay(1000)
                            it.arguments?.remove("scrollToTop")
                        }
                    }
                }
            })
        }

        Route.Home.let { route ->
            composable(route.route, route.arguments, content = { it ->
                val scrollToTop = it.arguments?.getBoolean("scrollToTop") ?: false
                val nip47 = it.arguments?.getString("nip47")

                HomeScreen(
                    homeFeedViewModel = homeFeedViewModel,
                    repliesFeedViewModel = repliesFeedViewModel,
                    accountViewModel = accountViewModel,
                    nav = nav,
                    pagerState = homePagerState,
                    scrollToTop = scrollToTop,
                    nip47 = nip47
                )

                // Avoids running scroll to top when back button is pressed
                if (scrollToTop) {
                    LaunchedEffect(key1 = Unit) {
                        scope.launch {
                            delay(1000)
                            it.arguments?.remove("scrollToTop")
                        }
                    }
                }
                if (nip47 != null) {
                    LaunchedEffect(key1 = Unit) {
                        scope.launch {
                            delay(1000)
                            it.arguments?.remove("nip47")
                        }
                    }
                }
            })
        }

        Route.Notification.let { route ->
            composable(route.route, route.arguments, content = {
                val scrollToTop = it.arguments?.getBoolean("scrollToTop") ?: false

                NotificationScreen(
                    notifFeedViewModel = notifFeedViewModel,
                    userReactionsStatsModel = userReactionsStatsModel,
                    accountViewModel = accountViewModel,
                    nav = nav,
                    scrollToTop = scrollToTop
                )

                // Avoids running scroll to top when back button is pressed
                // Changes this on a thread to avoid changing before it finishes the composition
                if (scrollToTop) {
                    LaunchedEffect(key1 = Unit) {
                        scope.launch {
                            delay(1000)
                            it.arguments?.remove("scrollToTop")
                        }
                    }
                }
            })
        }

        composable(Route.Message.route, content = { ChatroomListScreen(accountViewModel, nav) })
        composable(Route.BlockedUsers.route, content = { HiddenUsersScreen(accountViewModel, nav) })
        composable(Route.Bookmarks.route, content = { BookmarkListScreen(accountViewModel, nav) })

        Route.Profile.let { route ->
            composable(route.route, route.arguments, content = {
                ProfileScreen(
                    userId = it.arguments?.getString("id"),
                    accountViewModel = accountViewModel,
                    nav = nav
                )
            })
        }

        Route.Note.let { route ->
            composable(route.route, route.arguments, content = {
                ThreadScreen(
                    noteId = it.arguments?.getString("id"),
                    accountViewModel = accountViewModel,
                    nav = nav
                )
            })
        }

        Route.Hashtag.let { route ->
            composable(route.route, route.arguments, content = {
                HashtagScreen(
                    tag = it.arguments?.getString("id"),
                    accountViewModel = accountViewModel,
                    nav = nav
                )
            })
        }

        Route.Room.let { route ->
            composable(route.route, route.arguments, content = {
                ChatroomScreen(
                    userId = it.arguments?.getString("id"),
                    accountViewModel = accountViewModel,
                    nav = nav
                )
            })
        }

        Route.Channel.let { route ->
            composable(route.route, route.arguments, content = {
                ChannelScreen(
                    channelId = it.arguments?.getString("id"),
                    accountViewModel = accountViewModel,
                    nav = nav
                )
            })
        }

        Route.Event.let { route ->
            composable(route.route, route.arguments, content = {
                LoadRedirectScreen(
                    eventId = it.arguments?.getString("id"),
                    accountViewModel = accountViewModel,
                    navController = navController
                )
            })
        }
    }

    actionableNextPage?.let {
        LaunchedEffect(it) {
            nav(it)
        }
        actionableNextPage = null
    }
}
