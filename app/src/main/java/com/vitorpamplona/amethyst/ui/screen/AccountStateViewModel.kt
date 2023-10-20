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
    private val _accountContent = MutableStateFlow<AccountState>(AccountState.Loading)
    val accountContent = _accountContent.asStateFlow()

    fun tryLoginExistingAccountAsync() {
        // pulls account from storage.
        viewModelScope.launch {
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
    }

    suspend fun loginAndStartUI(key: String, useProxy: Boolean, proxyPort: Int, loginWithExternalSigner: Boolean = false) = withContext(Dispatchers.IO) {
        val parsed = Nip19.uriToRoute(key)
        val pubKeyParsed = parsed?.hex?.hexToByteArray()
        val proxy = HttpClient.initProxy(useProxy, "127.0.0.1", proxyPort)

        if (loginWithExternalSigner && pubKeyParsed == null) {
            throw Exception("Invalid key while trying to login with external signer")
        }

        val account =
            if (loginWithExternalSigner) {
                Account(KeyPair(pubKey = pubKeyParsed), proxy = proxy, proxyPort = proxyPort, loginWithExternalSigner = true)
            } else if (key.startsWith("nsec")) {
                Account(KeyPair(privKey = key.bechToBytes()), proxy = proxy, proxyPort = proxyPort)
            } else if (pubKeyParsed != null) {
                Account(KeyPair(pubKey = pubKeyParsed), proxy = proxy, proxyPort = proxyPort)
            } else if (EMAIL_PATTERN.matcher(key).matches()) {
                // Evaluate NIP-5
                Account(KeyPair(), proxy = proxy, proxyPort = proxyPort)
            } else {
                Account(KeyPair(Hex.decode(key)), proxy = proxy, proxyPort = proxyPort)
            }

        LocalPreferences.updatePrefsForLogin(account)

        startUI(account)
    }

    @OptIn(DelicateCoroutinesApi::class)
    suspend fun startUI(account: Account) = withContext(Dispatchers.Main) {
        if (account.keyPair.privKey != null) {
            _accountContent.update { AccountState.LoggedIn(account) }
        } else {
            _accountContent.update { AccountState.LoggedInViewOnly(account) }
        }
        GlobalScope.launch(Dispatchers.IO) {
            ServiceManager.restartIfDifferentAccount(account)
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
            }
            is AccountState.LoggedInViewOnly -> {
                state.account.saveable.removeObserver(saveListener)
            }
            else -> {}
        }
    }

    fun login(
        key: String,
        useProxy: Boolean,
        proxyPort: Int,
        loginWithExternalSigner: Boolean = false,
        onError: () -> Unit
    ) {
        viewModelScope.launch {
            try {
                loginAndStartUI(key, useProxy, proxyPort, loginWithExternalSigner)
            } catch (e: Exception) {
                Log.e("Login", "Could not sign in", e)
                onError()
            }
        }
    }

    fun newKey(useProxy: Boolean, proxyPort: Int) {
        viewModelScope.launch {
            val proxy = HttpClient.initProxy(useProxy, "127.0.0.1", proxyPort)
            val account = Account(KeyPair(), proxy = proxy, proxyPort = proxyPort)
            // saves to local preferences
            LocalPreferences.updatePrefsForLogin(account)
            startUI(account)
        }
    }

    fun switchUser(accountInfo: AccountInfo) {
        viewModelScope.launch {
            prepareLogoutOrSwitch()
            LocalPreferences.switchToAccount(accountInfo)
            tryLoginExistingAccount()
        }
    }

    fun logOff(accountInfo: AccountInfo) {
        viewModelScope.launch {
            prepareLogoutOrSwitch()
            LocalPreferences.updatePrefsForLogout(accountInfo)
            tryLoginExistingAccount()
        }
    }
}
