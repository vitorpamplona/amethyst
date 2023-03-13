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
import com.vitorpamplona.amethyst.ui.screen.loggedIn.SearchScreen

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

        Routes.forEach {
            it.buildScreen?.let { fn ->
                composable(it.route, it.arguments, content = fn(accountViewModel, accountStateViewModel, navController))
            }
        }
    }

    if (nextPage != null) {
        navController.navigate(nextPage)
    }
}
