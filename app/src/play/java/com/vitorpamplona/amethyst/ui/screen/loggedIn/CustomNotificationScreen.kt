package com.vitorpamplona.amethyst.ui.screen.loggedIn

import androidx.compose.runtime.Composable
import com.vitorpamplona.amethyst.ui.note.UserReactionsViewModel
import com.vitorpamplona.amethyst.ui.screen.NotificationViewModel

@Composable
fun CustomNotificationScreen(
    notifFeedViewModel: NotificationViewModel,
    userReactionsStatsModel: UserReactionsViewModel,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) = NotificationScreen(
    notifFeedViewModel = notifFeedViewModel,
    userReactionsStatsModel = userReactionsStatsModel,
    accountViewModel = accountViewModel,
    nav = nav
)
