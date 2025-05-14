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
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.AccountSettings
import com.vitorpamplona.amethyst.service.notifications.PushNotificationUtils
import com.vitorpamplona.amethyst.service.relayClient.authCommand.compose.RelayAuthSubscription
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.RelaySubscriptionsCoordinatorSubscription
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.account.AccountFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.MainActivity
import com.vitorpamplona.amethyst.ui.components.getActivity
import com.vitorpamplona.amethyst.ui.navigation.AppNavigation
import com.vitorpamplona.amethyst.ui.navigation.Route
import com.vitorpamplona.amethyst.ui.screen.AccountStateViewModel
import com.vitorpamplona.amethyst.ui.screen.SharedPreferencesViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.datasource.ChatroomListFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.datasource.DiscoveryFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.home.datasource.HomeFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.video.datasource.VideoFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.tor.TorServiceStatus
import com.vitorpamplona.amethyst.ui.tor.TorType
import com.vitorpamplona.quartz.nip55AndroidSigner.NostrSignerExternal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

    // Loads account information + DMs and Notifications from Relays.
    AccountFilterAssemblerSubscription(accountViewModel)

    // Pre-loads each of the main screens.
    HomeFilterAssemblerSubscription(accountViewModel)
    ChatroomListFilterAssemblerSubscription(accountViewModel)
    VideoFilterAssemblerSubscription(accountViewModel)
    DiscoveryFilterAssemblerSubscription(accountViewModel)

    // TODO: Is this needed?
    RelaySubscriptionsCoordinatorSubscription(accountViewModel)

    // Updates local cache of the anti-spam filter choice of this user.
    ObserveAntiSpamFilterSettings(accountViewModel)

    ManageRelayServices(accountViewModel, sharedPreferencesViewModel)

    // Turns Embed Tor on if needed.
    ManageTorInstance(accountViewModel)

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
fun ObserveImageLoadingTor(accountViewModel: AccountViewModel) {
    LaunchedEffect(Unit) {
        Amethyst.instance.setImageLoader(accountViewModel.account::shouldUseTorForImageDownload)
    }
}

@Composable
fun ManageRelayServices(
    accountViewModel: AccountViewModel,
    sharedPreferencesViewModel: SharedPreferencesViewModel,
) {
    LaunchedEffect(
        sharedPreferencesViewModel.sharedPrefs.currentNetworkId,
        sharedPreferencesViewModel.sharedPrefs.isOnMobileOrMeteredConnection,
    ) {
        Log.d("ManageRelayServices", "Loading/Change Network Id/State ${sharedPreferencesViewModel.sharedPrefs.currentNetworkId}, forcing start/restart of the relay services")
        accountViewModel.forceRestartServices()
    }

    val lifeCycleOwner = LocalLifecycleOwner.current

    val scope = rememberCoroutineScope()
    var job = remember<Job?> { null }

    Log.d("ManageRelayServices", "Job $job for $accountViewModel")

    DisposableEffect(key1 = accountViewModel) {
        job?.cancel()
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> {
                        job?.cancel()
                        Log.d("ManageRelayServices", "Resuming Relay Services $accountViewModel")
                        job = accountViewModel.justStart()
                    }
                    Lifecycle.Event.ON_PAUSE -> {
                        Log.d("ManageRelayServices", "Prepare to pause Relay Services $accountViewModel")
                        job?.cancel()
                        job =
                            scope.launch {
                                delay(30000) // 30 seconds
                                Log.d("ManageRelayServices", "Pausing Relay Services $accountViewModel")
                                accountViewModel.justPause()
                            }
                    }
                    else -> {}
                }
            }

        lifeCycleOwner.lifecycle.addObserver(observer)
        onDispose {
            job?.cancel()
            lifeCycleOwner.lifecycle.removeObserver(observer)
            Log.d("ManageRelayServices", "Disposing Relay Services $accountViewModel")
            // immediately stops upon disposal
            accountViewModel.pauseAndLogOff()
        }
    }
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
                PushNotificationUtils.checkAndInit(LocalPreferences.allSavedAccounts(), accountViewModel::okHttpClientForTrustedRelays)
            }

        onPauseOrDispose {
            job.cancel()
        }
    }
}

@Composable
fun ManageTorInstance(accountViewModel: AccountViewModel) {
    val torSettings by accountViewModel.account.settings.torSettings.torType
        .collectAsStateWithLifecycle()
    if (torSettings == TorType.INTERNAL) {
        WatchTorConnection(accountViewModel)
    }
}

@Composable
fun WatchTorConnection(accountViewModel: AccountViewModel) {
    val status by Amethyst.instance.torManager.status
        .collectAsStateWithLifecycle()

    if (status is TorServiceStatus.Active) {
        LaunchedEffect(key1 = status, key2 = accountViewModel) {
            Log.d("TorService", "Tor has just finished connecting, force restart relays $accountViewModel")
            accountViewModel.changeProxyPort((status as TorServiceStatus.Active).port)
        }
    }
}

@Composable
private fun ListenToExternalSignerIfNeeded(accountViewModel: AccountViewModel) {
    if (accountViewModel.account.signer is NostrSignerExternal) {
        val activity = getActivity() as MainActivity

        val lifeCycleOwner = LocalLifecycleOwner.current
        val launcher =
            rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult(),
                onResult = { result ->
                    if (result.resultCode != Activity.RESULT_OK) {
                        accountViewModel.toastManager.toast(
                            R.string.sign_request_rejected,
                            R.string.sign_request_rejected_description,
                        )
                    } else {
                        result.data?.let {
                            accountViewModel.runOnIO {
                                accountViewModel.account.signer.launcher
                                    .newResult(it)
                            }
                        }
                    }
                },
            )

        DisposableEffect(accountViewModel, accountViewModel.account, launcher, activity, lifeCycleOwner) {
            val observer =
                LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        accountViewModel.account.signer.launcher.registerLauncher(
                            launcher = {
                                try {
                                    launcher.launch(it)
                                } catch (e: Exception) {
                                    if (e is CancellationException) throw e
                                    Log.e("Signer", "Error opening Signer app", e)
                                    accountViewModel.toastManager.toast(
                                        R.string.error_opening_external_signer,
                                        R.string.error_opening_external_signer_description,
                                    )
                                }
                            },
                            contentResolver = Amethyst.instance::contentResolverFn,
                        )
                    }
                }

            lifeCycleOwner.lifecycle.addObserver(observer)
            accountViewModel.account.signer.launcher.registerLauncher(
                launcher = {
                    try {
                        launcher.launch(it)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.e("Signer", "Error opening Signer app", e)
                        accountViewModel.toastManager.toast(
                            R.string.error_opening_external_signer,
                            R.string.error_opening_external_signer_description,
                        )
                    }
                },
                contentResolver = Amethyst.instance::contentResolverFn,
            )
            onDispose {
                accountViewModel.account.signer.launcher
                    .clearLauncher()
                lifeCycleOwner.lifecycle.removeObserver(observer)
            }
        }
    }
}
