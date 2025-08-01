/**
 * Copyright (c) 2025 Vitor Pamplona
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
package com.vitorpamplona.amethyst.ui.screen.loggedIn

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.AccountSettings
import com.vitorpamplona.amethyst.service.notifications.PushNotificationUtils
import com.vitorpamplona.amethyst.service.relayClient.authCommand.compose.RelayAuthSubscription
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.account.AccountFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.navigation.AppNavigation
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.screen.AccountStateViewModel
import com.vitorpamplona.amethyst.ui.screen.SharedPreferencesViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.datasource.ChatroomListFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.datasource.DiscoveryFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.home.datasource.HomeFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.video.datasource.VideoFilterAssemblerSubscription
import com.vitorpamplona.quartz.nip55AndroidSigner.client.IActivityLauncher
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun LoggedInPage(
    accountSettings: AccountSettings,
    route: Route?,
    accountStateViewModel: AccountStateViewModel,
    sharedPreferencesViewModel: SharedPreferencesViewModel,
) {
    val accountViewModel: AccountViewModel =
        viewModel(
            key = "AccountViewModel",
            factory =
                AccountViewModel.Factory(
                    accountSettings,
                    sharedPreferencesViewModel.sharedPrefs,
                    Amethyst.instance,
                ),
        )

    accountViewModel.firstRoute = route

    // Adds this account to the authentication procedures for relays.
    RelayAuthSubscription(accountViewModel)

    // Sets up Coil's Image Loader
    ObserveImageLoadingTor(accountViewModel)

    // Sets up the use of Proxy based on this Account's settings
    SetProxyDeterminator(accountViewModel)

    // Loads account information + DMs and Notifications from Relays.
    AccountFilterAssemblerSubscription(accountViewModel)

    // Pre-loads each of the main screens.
    HomeFilterAssemblerSubscription(accountViewModel)
    ChatroomListFilterAssemblerSubscription(accountViewModel)
    VideoFilterAssemblerSubscription(accountViewModel)
    DiscoveryFilterAssemblerSubscription(accountViewModel)

    // Updates local cache of the anti-spam filter choice of this user.
    ObserveAntiSpamFilterSettings(accountViewModel)

    // Pauses relay services when the app pauses
    ManageRelayServices(accountViewModel)

    // Listens to Amber
    ListenToExternalSignerIfNeeded(accountViewModel)

    // Register token with the Push Notification Provider.
    NotificationRegistration(accountViewModel)

    AppNavigation(
        accountViewModel = accountViewModel,
        accountStateViewModel = accountStateViewModel,
        sharedPreferencesViewModel = sharedPreferencesViewModel,
    )
}

@Composable
fun ObserveAntiSpamFilterSettings(accountViewModel: AccountViewModel) {
    val isSpamActive by accountViewModel.account.settings.syncedSettings.security.filterSpamFromStrangers
        .collectAsStateWithLifecycle(true)

    Amethyst.instance.cache.antiSpam.active = isSpamActive
}

@Composable
fun SetProxyDeterminator(accountViewModel: AccountViewModel) {
    LaunchedEffect(accountViewModel) {
        Amethyst.instance.torProxySettingsAnchor.flow
            .tryEmit(accountViewModel.account.torRelayState.flow)
    }
}

@Composable
fun ObserveImageLoadingTor(accountViewModel: AccountViewModel) {
    LaunchedEffect(accountViewModel) {
        Amethyst.instance.setImageLoader(accountViewModel.account.privacyState::shouldUseTorForImageDownload)
    }
}

@Composable
fun ManageRelayServices(accountViewModel: AccountViewModel) {
    val relayServices by Amethyst.instance.relayProxyClientConnector.relayServices
        .collectAsStateWithLifecycle()
    Log.d("ManageRelayServices", "Relay Services changed $relayServices")
}

@Composable
fun NotificationRegistration(accountViewModel: AccountViewModel) {
    val scope = rememberCoroutineScope()
    var job = remember<Job?> { null }

    LifecycleResumeEffect(key1 = accountViewModel) {
        Log.d("RegisterAccounts", "Registering for push notifications")
        job?.cancel()
        job =
            scope.launch {
                PushNotificationUtils.checkAndInit(
                    LocalPreferences.allSavedAccounts(),
                    accountViewModel::okHttpClientForTrustedRelays,
                )
            }

        onPauseOrDispose {
            job.cancel()
        }
    }
}

@Composable
private fun ListenToExternalSignerIfNeeded(accountViewModel: AccountViewModel) {
    if (accountViewModel.account.signer is IActivityLauncher) {
        val launcher =
            rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult(),
                onResult = { result ->
                    if (result.resultCode == Activity.RESULT_OK) {
                        result.data?.let {
                            accountViewModel.runOnIO {
                                accountViewModel.account.signer.newResponse(it)
                            }
                        }
                    }
                },
            )

        DisposableEffect(accountViewModel, accountViewModel.account, launcher) {
            val launcher: (Intent) -> Unit = { intent ->
                try {
                    launcher.launch(intent)
                } catch (e: ActivityNotFoundException) {
                    accountViewModel.toastManager.toast(
                        R.string.error_opening_external_signer,
                        R.string.error_opening_external_signer_description,
                    )
                    throw e
                }
            }

            accountViewModel.account.signer.registerForegroundLauncher(launcher)
            onDispose {
                accountViewModel.account.signer.unregisterForegroundLauncher(launcher)
            }
        }
    }
}
