package com.vitorpamplona.amethyst.ui.screen

import android.util.Log
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.AccountInfo
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.amethyst.ServiceManager
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.service.HttpClient
import com.vitorpamplona.quartz.crypto.KeyPair
import com.vitorpamplona.quartz.encoders.Hex
import com.vitorpamplona.quartz.encoders.Nip19
import com.vitorpamplona.quartz.encoders.bechToBytes
import com.vitorpamplona.quartz.encoders.hexToByteArray
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.encoders.toNpub
import com.vitorpamplona.quartz.signers.ExternalSignerLauncher
import com.vitorpamplona.quartz.signers.NostrSignerExternal
import com.vitorpamplona.quartz.signers.NostrSignerInternal
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

val EMAIL_PATTERN = Pattern.compile(".+@.+\\.[a-z]+")

@Stable
class AccountStateViewModel() : ViewModel() {
    var serviceManager: ServiceManager? = null

    private val _accountContent = MutableStateFlow<AccountState>(AccountState.Loading)
    val accountContent = _accountContent.asStateFlow()

    fun tryLoginExistingAccountAsync() {
        // pulls account from storage.
        viewModelScope.launch(Dispatchers.IO) {
            tryLoginExistingAccount()
        }
    }

    private suspend fun tryLoginExistingAccount() = withContext(Dispatchers.IO) {
        LocalPreferences.loadCurrentAccountFromEncryptedStorage()?.let {
            startUI(it)
        } ?: run {
            requestLoginUI()
        }
    }

    private suspend fun requestLoginUI() {
        _accountContent.update { AccountState.LoggedOff }

        viewModelScope.launch(Dispatchers.IO) {
            serviceManager?.pauseForGood()
        }
    }

    suspend fun loginAndStartUI(
        key: String,
        useProxy: Boolean,
        proxyPort: Int,
        loginWithExternalSigner: Boolean = false,
        packageName: String = ""
    ) = withContext(Dispatchers.IO) {
        val parsed = Nip19.uriToRoute(key)
        val pubKeyParsed = parsed?.hex?.hexToByteArray()
        val proxy = HttpClient.initProxy(useProxy, "127.0.0.1", proxyPort)

        if (loginWithExternalSigner && pubKeyParsed == null) {
            throw Exception("Invalid key while trying to login with external signer")
        }

        val account =
            if (loginWithExternalSigner) {
                val keyPair = KeyPair(pubKey = pubKeyParsed)
                val localPackageName = packageName.ifBlank { "com.greenart7c3.nostrsigner" }
                Account(keyPair, proxy = proxy, proxyPort = proxyPort, signer = NostrSignerExternal(keyPair.pubKey.toHexKey(), ExternalSignerLauncher(keyPair.pubKey.toNpub(), localPackageName)))
            } else if (key.startsWith("nsec")) {
                val keyPair = KeyPair(privKey = key.bechToBytes())
                Account(keyPair, proxy = proxy, proxyPort = proxyPort, signer = NostrSignerInternal(keyPair))
            } else if (pubKeyParsed != null) {
                val keyPair = KeyPair(pubKey = pubKeyParsed)
                Account(keyPair, proxy = proxy, proxyPort = proxyPort, signer = NostrSignerInternal(keyPair))
            } else if (EMAIL_PATTERN.matcher(key).matches()) {
                val keyPair = KeyPair()
                // Evaluate NIP-5
                Account(keyPair, proxy = proxy, proxyPort = proxyPort, signer = NostrSignerInternal(keyPair))
            } else {
                val keyPair = KeyPair(Hex.decode(key))
                Account(keyPair, proxy = proxy, proxyPort = proxyPort, signer = NostrSignerInternal(keyPair))
            }

        LocalPreferences.updatePrefsForLogin(account)

        startUI(account)
    }

    suspend fun startUI(account: Account) = withContext(Dispatchers.Main) {
        if (account.isWriteable()) {
            _accountContent.update { AccountState.LoggedIn(account) }
        } else {
            _accountContent.update { AccountState.LoggedInViewOnly(account) }
        }

        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                // Prepares livedata objects on the main user.
                account.userProfile().live()
            }
            serviceManager?.restartIfDifferentAccount(account)
        }

        account.saveable.observeForever(saveListener)
    }

    @OptIn(DelicateCoroutinesApi::class)
    private val saveListener: (com.vitorpamplona.amethyst.model.AccountState) -> Unit = {
        GlobalScope.launch(Dispatchers.IO) {
            LocalPreferences.saveToEncryptedStorage(it.account)
        }
    }

    private suspend fun prepareLogoutOrSwitch() = withContext(Dispatchers.Main) {
        when (val state = _accountContent.value) {
            is AccountState.LoggedIn -> {
                state.account.saveable.removeObserver(saveListener)
                withContext(Dispatchers.IO) {
                    state.currentViewModelStore.viewModelStore.clear()
                }
            }
            is AccountState.LoggedInViewOnly -> {
                state.account.saveable.removeObserver(saveListener)
                withContext(Dispatchers.IO) {
                    state.currentViewModelStore.viewModelStore.clear()
                }
            }
            else -> {}
        }
    }

    fun login(
        key: String,
        useProxy: Boolean,
        proxyPort: Int,
        loginWithExternalSigner: Boolean = false,
        packageName: String = "",
        onError: () -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                loginAndStartUI(key, useProxy, proxyPort, loginWithExternalSigner, packageName)
            } catch (e: Exception) {
                Log.e("Login", "Could not sign in", e)
                onError()
            }
        }
    }

    fun newKey(useProxy: Boolean, proxyPort: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val proxy = HttpClient.initProxy(useProxy, "127.0.0.1", proxyPort)
            val keyPair = KeyPair()
            val account = Account(keyPair, proxy = proxy, proxyPort = proxyPort, signer = NostrSignerInternal(keyPair))

            account.follow(account.userProfile())

            // saves to local preferences
            LocalPreferences.updatePrefsForLogin(account)
            startUI(account)
        }
    }

    fun switchUser(accountInfo: AccountInfo) {
        viewModelScope.launch(Dispatchers.IO) {
            prepareLogoutOrSwitch()
            LocalPreferences.switchToAccount(accountInfo)
            tryLoginExistingAccount()
        }
    }

    fun logOff(accountInfo: AccountInfo) {
        viewModelScope.launch(Dispatchers.IO) {
            prepareLogoutOrSwitch()
            LocalPreferences.updatePrefsForLogout(accountInfo)
            tryLoginExistingAccount()
        }
    }
}
