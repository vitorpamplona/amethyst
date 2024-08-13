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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.vitorpamplona.amethyst.service.NostrAccountDataSource
import com.vitorpamplona.amethyst.ui.components.SelectNotificationProvider
import com.vitorpamplona.amethyst.ui.feeds.ScrollStateKeys
import com.vitorpamplona.amethyst.ui.navigation.Route
import com.vitorpamplona.amethyst.ui.screen.SharedPreferencesViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.DividerThickness

@Composable
fun NotificationScreen(
    notifFeedContentState: CardFeedContentState,
    notifSummaryState: NotificationSummaryState,
    sharedPreferencesViewModel: SharedPreferencesViewModel,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    SelectNotificationProvider(sharedPreferencesViewModel)

    WatchAccountForNotifications(notifFeedContentState, accountViewModel)

    val lifeCycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifeCycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    NostrAccountDataSource.account = accountViewModel.account
                    NostrAccountDataSource.invalidateFilters()
                }
            }

        lifeCycleOwner.lifecycle.addObserver(observer)
        onDispose { lifeCycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(Modifier.fillMaxHeight()) {
        SummaryBar(
            state = notifSummaryState,
        )
        HorizontalDivider(
            thickness = DividerThickness,
        )
        RefreshableCardView(
            feedContent = notifFeedContentState,
            accountViewModel = accountViewModel,
            nav = nav,
            routeForLastRead = Route.Notification.base,
            scrollStateKey = ScrollStateKeys.NOTIFICATION_SCREEN,
        )
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun checkifItNeedsToRequestNotificationPermission(sharedPreferencesViewModel: SharedPreferencesViewModel): PermissionState {
    val notificationPermissionState =
        rememberPermissionState(
            Manifest.permission.POST_NOTIFICATIONS,
        )

    if (!sharedPreferencesViewModel.sharedPrefs.dontAskForNotificationPermissions) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!notificationPermissionState.status.isGranted) {
                sharedPreferencesViewModel.dontAskForNotificationPermissions()

                // This will pause the APP, including the connection with relays.
                LaunchedEffect(notificationPermissionState) {
                    notificationPermissionState.launchPermissionRequest()
                }
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
        NostrAccountDataSource.account = accountViewModel.account
        NostrAccountDataSource.invalidateFilters()
        notifFeedContentState.checkKeysInvalidateDataAndSendToTop()
    }
}
