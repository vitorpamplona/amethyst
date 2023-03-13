package com.vitorpamplona.amethyst.ui.screen.loggedIn

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.vitorpamplona.amethyst.buttons.NewChannelButton
import com.vitorpamplona.amethyst.buttons.NewNoteButton
import com.vitorpamplona.amethyst.ui.navigation.AccountSwitchBottomSheet
import com.vitorpamplona.amethyst.ui.navigation.AppBottomBar
import com.vitorpamplona.amethyst.ui.navigation.AppNavigation
import com.vitorpamplona.amethyst.ui.navigation.AppTopBar
import com.vitorpamplona.amethyst.ui.navigation.DrawerContent
import com.vitorpamplona.amethyst.ui.navigation.Route
import com.vitorpamplona.amethyst.ui.navigation.currentRoute
import com.vitorpamplona.amethyst.ui.screen.AccountState
import com.vitorpamplona.amethyst.ui.screen.AccountStateViewModel

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun MainScreen(accountViewModel: AccountViewModel, accountStateViewModel: AccountStateViewModel, startingPage: String? = null) {
    val navController = rememberNavController()
    val scaffoldState = rememberScaffoldState(rememberDrawerState(DrawerValue.Closed))
    val sheetState = rememberModalBottomSheetState(
        initialValue = ModalBottomSheetValue.Hidden,
        confirmValueChange = { it != ModalBottomSheetValue.HalfExpanded },
        skipHalfExpanded = true
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
                AppTopBar(navController, scaffoldState, accountViewModel)
            },
            drawerContent = {
                DrawerContent(navController, scaffoldState, sheetState, accountViewModel)
            },
            floatingActionButton = {
                FloatingButton(navController, accountStateViewModel)
            },
            scaffoldState = scaffoldState
        ) {
            Column(modifier = Modifier.padding(bottom = it.calculateBottomPadding())) {
                AppNavigation(navController, accountViewModel, startingPage)
            }
        }
    }
}

@Composable
fun FloatingButton(navController: NavHostController, accountViewModel: AccountStateViewModel) {
    val accountState by accountViewModel.accountContent.collectAsState()

    if (currentRoute(navController)?.substringBefore("?") == Route.Home.base) {
        Crossfade(targetState = accountState, animationSpec = tween(durationMillis = 100)) { state ->
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

    if (currentRoute(navController) == Route.Message.base) {
        Crossfade(targetState = accountState, animationSpec = tween(durationMillis = 100)) { state ->
            when (state) {
                is AccountState.LoggedInViewOnly -> {
                    // Does nothing.
                }
                is AccountState.LoggedOff -> {
                    // Does nothing.
                }
                is AccountState.LoggedIn -> {
                    NewChannelButton(state.account)
                }
            }
        }
    }
}
