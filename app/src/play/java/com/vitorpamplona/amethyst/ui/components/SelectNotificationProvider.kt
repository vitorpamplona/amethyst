package com.vitorpamplona.amethyst.ui.components

import androidx.compose.runtime.Composable
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.vitorpamplona.amethyst.ui.screen.SharedPreferencesViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.CheckifItNeedsToRequestNotificationPermission

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun SelectNotificationProvider(sharedPreferencesViewModel: SharedPreferencesViewModel) {
    CheckifItNeedsToRequestNotificationPermission(sharedPreferencesViewModel)
}

@Composable
fun PushNotificationSettingsRow(sharedPreferencesViewModel: SharedPreferencesViewModel) {
}
