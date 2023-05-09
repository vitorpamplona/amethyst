package com.vitorpamplona.amethyst.ui.screen.loggedIn

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.vitorpamplona.amethyst.service.NostrAccountDataSource
import com.vitorpamplona.amethyst.ui.dal.NotificationFeedFilter
import com.vitorpamplona.amethyst.ui.navigation.Route
import com.vitorpamplona.amethyst.ui.screen.CardFeedView
import com.vitorpamplona.amethyst.ui.screen.NotificationViewModel
import com.vitorpamplona.amethyst.ui.screen.ScrollStateKeys

@Composable
fun NotificationScreen(
    notifFeedViewModel: NotificationViewModel,
    accountViewModel: AccountViewModel,
    navController: NavController,
    scrollToTop: Boolean = false
) {
    val accountState by accountViewModel.accountLiveData.observeAsState()
    val account = accountState?.account ?: return

    if (scrollToTop) {
        notifFeedViewModel.clear()
    }

    LaunchedEffect(account.userProfile().pubkeyHex, account.defaultNotificationFollowList) {
        NostrAccountDataSource.resetFilters()
        NotificationFeedFilter.account = account
        notifFeedViewModel.clear()
        notifFeedViewModel.refresh()
    }

    val lifeCycleOwner = LocalLifecycleOwner.current
    DisposableEffect(accountViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                NotificationFeedFilter.account = account
                notifFeedViewModel.invalidateData()
            }
        }

        lifeCycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifeCycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(Modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier.padding(vertical = 0.dp)
        ) {
            CardFeedView(
                viewModel = notifFeedViewModel,
                accountViewModel = accountViewModel,
                navController = navController,
                routeForLastRead = Route.Notification.base,
                scrollStateKey = ScrollStateKeys.NOTIFICATION_SCREEN,
                scrollToTop = scrollToTop
            )
        }
    }
}
