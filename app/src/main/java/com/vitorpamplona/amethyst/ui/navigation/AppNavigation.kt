package com.vitorpamplona.amethyst.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel

@Composable
fun AppNavigation(
    navController: NavHostController,
    accountViewModel: AccountViewModel
) {
    NavHost(navController, startDestination = Route.Home.route) {
        Routes.forEach {
            composable(it.route, it.arguments, content = it.buildScreen(accountViewModel, navController))
        }
    }
}