package com.vitorpamplona.amethyst.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.rememberPagerState
import com.vitorpamplona.amethyst.ui.dal.GlobalFeedFilter
import com.vitorpamplona.amethyst.ui.dal.HomeConversationsFeedFilter
import com.vitorpamplona.amethyst.ui.dal.HomeNewThreadFeedFilter
import com.vitorpamplona.amethyst.ui.screen.NostrGlobalFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.NostrHomeFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.NostrHomeRepliesFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.ChannelScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.ChatroomListScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.ChatroomScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.FiltersScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.HomeScreen
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
    val accountState by accountViewModel.accountLiveData.observeAsState()
    val account = accountState?.account ?: return

    GlobalFeedFilter.account = account
    HomeNewThreadFeedFilter.account = account
    HomeConversationsFeedFilter.account = account

    val globalFeedViewModel: NostrGlobalFeedViewModel = viewModel()
    val homeFeedViewModel: NostrHomeFeedViewModel = viewModel()
    val homeRepliesFeedViewModel: NostrHomeRepliesFeedViewModel = viewModel()
    val homePagerState = rememberPagerState()

    NavHost(navController, startDestination = Route.Home.route) {
        Route.Search.let { route ->
            composable(route.route, route.arguments, content = {
                SearchScreen(
                    accountViewModel = accountViewModel,
                    feedViewModel = globalFeedViewModel,
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
                    homeFeedViewModel = homeFeedViewModel,
                    repliesFeedViewModel = homeRepliesFeedViewModel,
                    pagerState = homePagerState,
                    scrollToTop = it.arguments?.getBoolean("scrollToTop") ?: false
                )
            })
        }

        composable(Route.Message.route, content = { ChatroomListScreen(accountViewModel, navController) })
        composable(Route.Notification.route, content = { NotificationScreen(accountViewModel, navController) })
        composable(Route.Filters.route, content = { FiltersScreen(accountViewModel, navController) })

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
    }

    if (nextPage != null) {
        navController.navigate(nextPage)
    }
}
