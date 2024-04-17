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
package com.vitorpamplona.amethyst.ui.screen

import android.app.Activity
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.ui.MainActivity
import com.vitorpamplona.amethyst.ui.components.getActivity
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.MainScreen
import com.vitorpamplona.amethyst.ui.screen.loggedOff.LoginOrSignupScreen
import com.vitorpamplona.quartz.signers.NostrSignerExternal
import kotlinx.coroutines.CancellationException

@Composable
fun AccountScreen(
    accountStateViewModel: AccountStateViewModel,
    sharedPreferencesViewModel: SharedPreferencesViewModel,
) {
    val accountState by accountStateViewModel.accountContent.collectAsStateWithLifecycle()

    Crossfade(
        targetState = accountState,
        animationSpec = tween(durationMillis = 100),
        label = "AccountState",
    ) { state ->
        when (state) {
            is AccountState.Loading -> {
                LoadingAccounts()
            }
            is AccountState.LoggedOff -> {
                LoginOrSignupScreen(accountStateViewModel, isFirstLogin = true)
            }
            is AccountState.LoggedIn -> {
                CompositionLocalProvider(
                    LocalViewModelStoreOwner provides state.currentViewModelStore,
                ) {
                    LoggedInPage(
                        state.account,
                        accountStateViewModel,
                        sharedPreferencesViewModel,
                    )
                }
            }
            is AccountState.LoggedInViewOnly -> {
                CompositionLocalProvider(
                    LocalViewModelStoreOwner provides state.currentViewModelStore,
                ) {
                    LoggedInPage(
                        state.account,
                        accountStateViewModel,
                        sharedPreferencesViewModel,
                    )
                }
            }
        }
    }
}

@Composable
fun LoggedInPage(
    account: Account,
    accountStateViewModel: AccountStateViewModel,
    sharedPreferencesViewModel: SharedPreferencesViewModel,
) {
    val accountViewModel: AccountViewModel =
        viewModel(
            key = "AccountViewModel",
            factory =
                AccountViewModel.Factory(
                    account,
                    sharedPreferencesViewModel.sharedPrefs,
                ),
        )

    val activity = getActivity() as MainActivity
    // Find a better way to associate these two.
    accountViewModel.serviceManager = activity.serviceManager

    if (accountViewModel.account.signer is NostrSignerExternal) {
        val lifeCycleOwner = LocalLifecycleOwner.current
        val launcher =
            rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult(),
                onResult = { result ->
                    if (result.resultCode != Activity.RESULT_OK) {
                        accountViewModel.toast(
                            R.string.sign_request_rejected,
                            R.string.sign_request_rejected_description,
                        )
                    } else {
                        result.data?.let {
                            accountViewModel.runOnIO { accountViewModel.account.signer.launcher.newResult(it) }
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
                                    activity.prepareToLaunchSigner()
                                    launcher.launch(it)
                                } catch (e: Exception) {
                                    if (e is CancellationException) throw e
                                    Log.e("Signer", "Error opening Signer app", e)
                                    accountViewModel.toast(
                                        R.string.error_opening_external_signer,
                                        R.string.error_opening_external_signer_description,
                                    )
                                }
                            },
                            contentResolver = { Amethyst.instance.contentResolver },
                        )
                    }
                }

            lifeCycleOwner.lifecycle.addObserver(observer)
            accountViewModel.account.signer.launcher.registerLauncher(
                launcher = {
                    try {
                        activity.prepareToLaunchSigner()
                        launcher.launch(it)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.e("Signer", "Error opening Signer app", e)
                        accountViewModel.toast(
                            R.string.error_opening_external_signer,
                            R.string.error_opening_external_signer_description,
                        )
                    }
                },
                contentResolver = { Amethyst.instance.contentResolver },
            )
            onDispose {
                accountViewModel.account.signer.launcher.clearLauncher()
                lifeCycleOwner.lifecycle.removeObserver(observer)
            }
        }
    }

    MainScreen(accountViewModel, accountStateViewModel, sharedPreferencesViewModel)
}

class AccountCentricViewModelStore(val account: Account) : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
}

@Composable
fun LoadingAccounts() {
    Column(
        Modifier.fillMaxHeight().fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.loading_account))
    }
}
