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
import androidx.compose.ui.res.stringResource
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
import com.vitorpamplona.amethyst.ui.screen.loggedOff.LoginPage
import com.vitorpamplona.quartz.signers.NostrSignerExternal
import kotlinx.coroutines.launch

@Composable
fun AccountScreen(
    accountStateViewModel: AccountStateViewModel,
    sharedPreferencesViewModel: SharedPreferencesViewModel
) {
    val accountState by accountStateViewModel.accountContent.collectAsStateWithLifecycle()

    Crossfade(
        targetState = accountState,
        animationSpec = tween(durationMillis = 100),
        label = "AccountState"
    ) { state ->
        when (state) {
            is AccountState.Loading -> {
                LoadingAccounts()
            }
            is AccountState.LoggedOff -> {
                LoginPage(accountStateViewModel, isFirstLogin = true)
            }
            is AccountState.LoggedIn -> {
                CompositionLocalProvider(
                    LocalViewModelStoreOwner provides state.currentViewModelStore
                ) {
                    LoggedInPage(
                        state.account,
                        accountStateViewModel,
                        sharedPreferencesViewModel
                    )
                }
            }
            is AccountState.LoggedInViewOnly -> {
                CompositionLocalProvider(
                    LocalViewModelStoreOwner provides state.currentViewModelStore
                ) {
                    LoggedInPage(
                        state.account,
                        accountStateViewModel,
                        sharedPreferencesViewModel
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
    sharedPreferencesViewModel: SharedPreferencesViewModel
) {
    val accountViewModel: AccountViewModel = viewModel(
        key = "AccountViewModel",
        factory = AccountViewModel.Factory(
            account,
            sharedPreferencesViewModel.sharedPrefs
        )
    )

    val activity = getActivity() as MainActivity
    // Find a better way to associate these two.
    accountViewModel.serviceManager = activity.serviceManager

    if (accountViewModel.account.signer is NostrSignerExternal) {
        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult(),
            onResult = { result ->
                if (result.resultCode != Activity.RESULT_OK) {
                    accountViewModel.toast(
                        R.string.sign_request_rejected,
                        R.string.sign_request_rejected_description
                    )
                } else {
                    result.data?.let {
                        accountViewModel.runOnIO {
                            accountViewModel.account.signer.launcher.newResult(it)
                        }
                    }
                }
            }
        )

        DisposableEffect(accountViewModel, accountViewModel.account, launcher, activity) {
            accountViewModel.account.signer.launcher.registerLauncher(
                launcher = {
                    try {
                        activity.prepareToLaunchSigner()
                        launcher.launch(it)
                    } catch (e: Exception) {
                        Log.e("Signer", "Error opening Signer app", e)
                        accountViewModel.toast(
                            R.string.error_opening_external_signer,
                            R.string.error_opening_external_signer_description
                        )
                    }
                },
                contentResolver = { Amethyst.instance.contentResolver }
            )
            onDispose {
                accountViewModel.account.signer.launcher.clearLauncher()
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
        Modifier
            .fillMaxHeight()
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(R.string.loading_account))
    }
}
