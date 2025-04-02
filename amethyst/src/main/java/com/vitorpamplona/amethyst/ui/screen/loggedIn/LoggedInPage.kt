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
import androidx.compose.ui.platform.LocalContext
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
import com.vitorpamplona.amethyst.service.okhttp.HttpClientManager
import com.vitorpamplona.amethyst.ui.MainActivity
import com.vitorpamplona.amethyst.ui.components.getActivity
import com.vitorpamplona.amethyst.ui.navigation.AppNavigation
import com.vitorpamplona.amethyst.ui.navigation.Route
import com.vitorpamplona.amethyst.ui.screen.AccountStateViewModel
import com.vitorpamplona.amethyst.ui.screen.SharedPreferencesViewModel
import com.vitorpamplona.amethyst.ui.tor.TorManager
import com.vitorpamplona.amethyst.ui.tor.TorStatus
import com.vitorpamplona.amethyst.ui.tor.TorType
import com.vitorpamplona.quartz.nip55AndroidSigner.NostrSignerExternal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
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
                ),
        )

    accountViewModel.firstRoute = route

    ManageRelayServices(accountViewModel, sharedPreferencesViewModel)

    ManageTorInstance(accountViewModel)

    ListenToExternalSignerIfNeeded(accountViewModel)

    NotificationRegistration(accountViewModel)

    AppNavigation(
        accountViewModel = accountViewModel,
        accountStateViewModel = accountStateViewModel,
        sharedPreferencesViewModel = sharedPreferencesViewModel,
    )
}

@Composable
fun ManageRelayServices(
    accountViewModel: AccountViewModel,
    sharedPreferencesViewModel: SharedPreferencesViewModel,
) {
    var job = remember<Job?> { null }

    LaunchedEffect(
        sharedPreferencesViewModel.sharedPrefs.currentNetworkId,
        sharedPreferencesViewModel.sharedPrefs.isOnMobileOrMeteredConnection,
    ) {
        Log.d("ManageRelayServices", "Change Network Id/State ${sharedPreferencesViewModel.sharedPrefs.currentNetworkId}, forcing restart")
        if (sharedPreferencesViewModel.sharedPrefs.isOnMobileOrMeteredConnection) {
            HttpClientManager.setDefaultTimeout(HttpClientManager.DEFAULT_TIMEOUT_ON_MOBILE)
        } else {
            HttpClientManager.setDefaultTimeout(HttpClientManager.DEFAULT_TIMEOUT_ON_WIFI)
        }
        accountViewModel.forceRestartServices()
    }

    LifecycleResumeEffect(sharedPreferencesViewModel, accountViewModel) {
        job?.cancel()
        Log.d("ManageRelayServices", "Starting Relay Services")
        job = accountViewModel.justStart()

        onPauseOrDispose {
            job?.cancel()
            job =
                GlobalScope.launch(Dispatchers.IO) {
                    delay(30000) // 30 seconds
                    Log.d("ManageRelayServices", "Pausing Relay Services")
                    accountViewModel.justPause()
                }
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
                val okHttpClient = HttpClientManager.getHttpClient(accountViewModel.account.shouldUseTorForTrustedRelays())
                PushNotificationUtils.checkAndInit(LocalPreferences.allSavedAccounts(), okHttpClient)
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
        WatchConnection(accountViewModel)
        ManageTorInstanceInner(accountViewModel)
    }
}

@Composable
fun ManageTorInstanceInner(accountViewModel: AccountViewModel) {
    val context = LocalContext.current.applicationContext

    val scope = rememberCoroutineScope()
    var job = remember<Job?> { null }

    LifecycleResumeEffect(key1 = accountViewModel) {
        job?.cancel()
        job = null
        TorManager.startTorIfNotAlreadyOn(context)

        onPauseOrDispose {
            job =
                scope.launch {
                    delay(30000) // 5 seconds
                    TorManager.stopTor(context)
                }
        }
    }
}

@Composable
fun WatchConnection(accountViewModel: AccountViewModel) {
    val status by TorManager.status.collectAsStateWithLifecycle()

    LaunchedEffect(key1 = status, key2 = accountViewModel) {
        if (status is TorStatus.Active) {
            Log.d("ServiceManager", "Tor is Active, force restart connections")
            accountViewModel.changeProxyPort((status as TorStatus.Active).port)
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
