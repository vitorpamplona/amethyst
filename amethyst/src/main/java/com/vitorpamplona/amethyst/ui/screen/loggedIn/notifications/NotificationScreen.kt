/**
 * Copyright (c) 2024 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.ui.screen.loggedIn.notifications

import android.Manifest
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.vitorpamplona.amethyst.ui.components.SelectNotificationProvider
import com.vitorpamplona.amethyst.ui.feeds.ScrollStateKeys
import com.vitorpamplona.amethyst.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.ui.navigation.AppBottomBar
import com.vitorpamplona.amethyst.ui.navigation.INav
import com.vitorpamplona.amethyst.ui.navigation.Route
import com.vitorpamplona.amethyst.ui.screen.SharedPreferencesViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel

@Composable
fun NotificationScreen(
    sharedPreferencesViewModel: SharedPreferencesViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    NotificationScreen(
        notifFeedContentState = accountViewModel.feedStates.notifications,
        notifSummaryState = accountViewModel.feedStates.notificationSummary,
        sharedPreferencesViewModel = sharedPreferencesViewModel,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

@Composable
fun NotificationScreen(
    notifFeedContentState: CardFeedContentState,
    notifSummaryState: NotificationSummaryState,
    sharedPreferencesViewModel: SharedPreferencesViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    SelectNotificationProvider(sharedPreferencesViewModel)

    WatchAccountForNotifications(notifFeedContentState, accountViewModel)

    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            Column {
                NotificationTopBar(accountViewModel, nav)
                SummaryBar(
                    state = notifSummaryState,
                )
            }
        },
        bottomBar = {
            AppBottomBar(Route.Notification, accountViewModel) { route ->
                if (route == Route.Notification) {
                    notifFeedContentState.invalidateDataAndSendToTop(true)
                } else {
                    nav.newStack(route)
                }
            }
        },
        accountViewModel = accountViewModel,
    ) {
        Column(
            modifier = Modifier.padding(it).consumeWindowInsets(it),
        ) {
            RefreshableCardView(
                feedContent = notifFeedContentState,
                accountViewModel = accountViewModel,
                nav = nav,
                routeForLastRead = "Notification",
                scrollStateKey = ScrollStateKeys.NOTIFICATION_SCREEN,
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun checkifItNeedsToRequestNotificationPermission(sharedPreferencesViewModel: SharedPreferencesViewModel): PermissionState {
    val notificationPermissionState =
        rememberPermissionState(
            Manifest.permission.POST_NOTIFICATIONS,
        )

    if (!sharedPreferencesViewModel.sharedPrefs.dontAskForNotificationPermissions) {
        if (!notificationPermissionState.status.isGranted) {
            sharedPreferencesViewModel.dontAskForNotificationPermissions()

            // This will pause the APP, including the connection with relays.
            LaunchedEffect(notificationPermissionState) {
                notificationPermissionState.launchPermissionRequest()
            }
        }
    }

    return notificationPermissionState
}

@Composable
fun WatchAccountForNotifications(
    notifFeedContentState: CardFeedContentState,
    accountViewModel: AccountViewModel,
) {
    val listState by
        accountViewModel.account.liveNotificationFollowLists.collectAsStateWithLifecycle()

    LaunchedEffect(accountViewModel, listState) {
        notifFeedContentState.checkKeysInvalidateDataAndSendToTop()
    }
}
