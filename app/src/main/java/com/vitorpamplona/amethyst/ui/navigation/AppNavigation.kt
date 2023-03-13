package com.vitorpamplona.amethyst.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.vitorpamplona.amethyst.ui.dal.GlobalFeedFilter
import com.vitorpamplona.amethyst.ui.screen.AccountStateViewModel
import com.vitorpamplona.amethyst.ui.screen.NostrGlobalFeedViewModel
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

@Composable
fun AppNavigation(
    navController: NavHostController,
    accountViewModel: AccountViewModel,
    accountStateViewModel: AccountStateViewModel,
    nextPage: String? = null
) {
    val accountState by accountViewModel.accountLiveData.observeAsState()
    val account = accountState?.account ?: return

    GlobalFeedFilter.account = account
    val globalFeedViewModel: NostrGlobalFeedViewModel = viewModel()

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
                    accountStateViewModel = accountStateViewModel,
                    navController = navController
                )
            })
        }
    }

    if (nextPage != null) {
        navController.navigate(nextPage)
    }
}
