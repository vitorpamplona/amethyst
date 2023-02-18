package com.vitorpamplona.amethyst.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.vitorpamplona.amethyst.ui.dal.NotificationFeedFilter
import com.vitorpamplona.amethyst.ui.navigation.Route
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel

@Composable
fun NotificationScreen(accountViewModel: AccountViewModel, navController: NavController) {
    val accountState by accountViewModel.accountLiveData.observeAsState()
    val account = accountState?.account ?: return

    NotificationFeedFilter.account = account
    val feedViewModel: NotificationViewModel = viewModel()

    LaunchedEffect(Unit) {
        feedViewModel.refresh()
    }

    Column(Modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier.padding(vertical = 0.dp)
        ) {
            CardFeedView(feedViewModel, accountViewModel = accountViewModel, navController, Route.Notification.route)
        }
    }
}