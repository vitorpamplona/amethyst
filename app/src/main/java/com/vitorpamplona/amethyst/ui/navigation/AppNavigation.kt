package com.vitorpamplona.amethyst.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.rememberPagerState
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

@OptIn(ExperimentalPagerApi::class)
@Composable
fun AppNavigation(
    navController: NavHostController,
    accountViewModel: AccountViewModel,
    nextPage: String? = null
) {
    val homePagerState = rememberPagerState()

    NavHost(navController, startDestination = Route.Home.route) {
        Route.Search.let { route ->
            composable(route.route, route.arguments, content = {
                SearchScreen(
                    accountViewModel = accountViewModel,
                    navController = navController,
                    scrollToTop = it.arguments?.getBoolean("scrollToTop") ?: false
                )
            })
        }

        Route.Home.let { route ->
            composable(route.route, route.arguments, content = {
                HomeScreen(
                    accountViewModel = accountViewModel,
                    navController = navController,
                    pagerState = homePagerState,
                    scrollToTop = it.arguments?.getBoolean("scrollToTop") ?: false
                )
            })
        }

        composable(Route.Message.route, content = { ChatroomListScreen(accountViewModel, navController) })
        composable(Route.Notification.route, content = { NotificationScreen(accountViewModel, navController) })
        composable(Route.BlockedUsers.route, content = { HiddenUsersScreen(accountViewModel, navController) })
        composable(Route.Bookmarks.route, content = { BookmarkListScreen(accountViewModel, navController) })

        Route.Profile.let { route ->
            composable(route.route, route.arguments, content = {
                ProfileScreen(
                    userId = it.arguments?.getString("id"),
                    accountViewModel = accountViewModel,
                    navController = navController
                )
            })
        }

        Route.Note.let { route ->
            composable(route.route, route.arguments, content = {
                ThreadScreen(
                    noteId = it.arguments?.getString("id"),
                    accountViewModel = accountViewModel,
                    navController = navController
                )
            })
        }

        Route.Hashtag.let { route ->
            composable(route.route, route.arguments, content = {
                HashtagScreen(
                    tag = it.arguments?.getString("id"),
                    accountViewModel = accountViewModel,
                    navController = navController
                )
            })
        }

        Route.Room.let { route ->
            composable(route.route, route.arguments, content = {
                ChatroomScreen(
                    userId = it.arguments?.getString("id"),
                    accountViewModel = accountViewModel,
                    navController = navController
                )
            })
        }

        Route.Channel.let { route ->
            composable(route.route, route.arguments, content = {
                ChannelScreen(
                    channelId = it.arguments?.getString("id"),
                    accountViewModel = accountViewModel,
                    navController = navController
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

    if (nextPage != null) {
        navController.navigate(nextPage)
    }
}
