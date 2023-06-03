package com.vitorpamplona.amethyst.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.vitorpamplona.amethyst.ui.note.UserReactionsViewModel
import com.vitorpamplona.amethyst.ui.screen.NostrChatroomListKnownFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.NostrChatroomListNewFeedViewModel
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AppNavigation(
    homeFeedViewModel: NostrHomeFeedViewModel,
    repliesFeedViewModel: NostrHomeRepliesFeedViewModel,
    knownFeedViewModel: NostrChatroomListKnownFeedViewModel,
    newFeedViewModel: NostrChatroomListNewFeedViewModel,
    searchFeedViewModel: NostrGlobalFeedViewModel,
    videoFeedViewModel: NostrVideoFeedViewModel,
    notifFeedViewModel: NotificationViewModel,
    userReactionsStatsModel: UserReactionsViewModel,

    navController: NavHostController,
    accountViewModel: AccountViewModel,
    nextPage: String? = null
) {
    var actionableNextPage by remember { mutableStateOf<String?>(nextPage) }

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

                if (scrollToTop) {
                    videoFeedViewModel.sendToTop()
                    it.arguments?.remove("scrollToTop")
                }

                VideoScreen(
                    videoFeedView = videoFeedViewModel,
                    accountViewModel = accountViewModel,
                    nav = nav
                )
            })
        }

        Route.Search.let { route ->
            composable(route.route, route.arguments, content = {
                val scrollToTop = it.arguments?.getBoolean("scrollToTop") ?: false

                if (scrollToTop) {
                    searchFeedViewModel.sendToTop()
                    it.arguments?.remove("scrollToTop")
                }

                SearchScreen(
                    searchFeedViewModel = searchFeedViewModel,
                    accountViewModel = accountViewModel,
                    nav = nav
                )
            })
        }

        Route.Home.let { route ->
            composable(route.route, route.arguments, content = { it ->
                val scrollToTop = it.arguments?.getBoolean("scrollToTop") ?: false
                val nip47 = it.arguments?.getString("nip47")

                if (scrollToTop) {
                    homeFeedViewModel.sendToTop()
                    repliesFeedViewModel.sendToTop()
                    it.arguments?.remove("scrollToTop")
                }

                HomeScreen(
                    homeFeedViewModel = homeFeedViewModel,
                    repliesFeedViewModel = repliesFeedViewModel,
                    accountViewModel = accountViewModel,
                    nav = nav,
                    nip47 = nip47
                )

                if (nip47 != null) {
                    LaunchedEffect(key1 = Unit) {
                        launch {
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

                if (scrollToTop) {
                    notifFeedViewModel.clear()
                    notifFeedViewModel.sendToTop()
                    it.arguments?.remove("scrollToTop")
                }

                NotificationScreen(
                    notifFeedViewModel = notifFeedViewModel,
                    userReactionsStatsModel = userReactionsStatsModel,
                    accountViewModel = accountViewModel,
                    nav = nav
                )
            })
        }

        composable(
            Route.Message.route,
            content = {
                ChatroomListScreen(
                    knownFeedViewModel,
                    newFeedViewModel,
                    accountViewModel,
                    nav
                )
            }
        )

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
