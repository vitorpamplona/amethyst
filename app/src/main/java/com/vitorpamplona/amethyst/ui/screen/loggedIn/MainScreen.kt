package com.vitorpamplona.amethyst.ui.screen

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.DrawerValue
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.rememberDrawerState
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.vitorpamplona.amethyst.buttons.NewNoteButton
import com.vitorpamplona.amethyst.ui.navigation.AppBottomBar
import com.vitorpamplona.amethyst.ui.navigation.AppNavigation
import com.vitorpamplona.amethyst.ui.navigation.AppTopBar
import com.vitorpamplona.amethyst.ui.navigation.DrawerContent
import com.vitorpamplona.amethyst.ui.navigation.Route
import com.vitorpamplona.amethyst.ui.navigation.currentRoute
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel

@Composable
fun MainScreen(accountViewModel: AccountViewModel, accountStateViewModel: AccountStateViewModel) {
    val navController = rememberNavController()
    val scaffoldState = rememberScaffoldState(rememberDrawerState(DrawerValue.Closed))

    Scaffold(
        modifier = Modifier
            .background(MaterialTheme.colors.primaryVariant)
            .statusBarsPadding(),
        bottomBar = {
            AppBottomBar(navController)
        },
        topBar = {
            AppTopBar(navController, scaffoldState, accountViewModel)
        },
        drawerContent = {
            DrawerContent(navController, scaffoldState, accountViewModel, accountStateViewModel)
        },
        floatingActionButton = {
            FloatingButton(navController, accountStateViewModel)
        },
        scaffoldState = scaffoldState
    ) {
        Column(modifier = Modifier.padding(bottom = it.calculateBottomPadding())) {
            AppNavigation(navController, accountViewModel)
        }
    }
}

@Composable
fun FloatingButton(navController: NavHostController, accountViewModel: AccountStateViewModel) {
    val accountState by accountViewModel.accountContent.collectAsStateWithLifecycle()

    if (currentRoute(navController) == Route.Home.route) {
        Crossfade(targetState = accountState) { state ->
            when (state) {
                is AccountState.LoggedInViewOnly -> {
                    // Does nothing.
                }
                is AccountState.LoggedOff -> {
                    // Does nothing.
                }
                is AccountState.LoggedIn -> {
                    NewNoteButton(state.account)
                }
            }
        }
    }
}