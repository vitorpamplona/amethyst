package com.vitorpamplona.amethyst.ui.screen

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.amethyst.ServiceManager
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.hexToByteArray
import com.vitorpamplona.amethyst.service.HttpClient
import com.vitorpamplona.amethyst.service.nip19.Nip19
import fr.acinq.secp256k1.Hex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nostr.postr.Persona
import nostr.postr.bechToBytes
import java.util.regex.Pattern

@Stable
class AccountStateViewModel(val context: Context) : ViewModel() {
    private val _accountContent = MutableStateFlow<AccountState>(AccountState.LoggedOff)
    val accountContent = _accountContent.asStateFlow()

    init {
        // pulls account from storage.

        // Keeps it in the the UI thread to void blinking the login page.
        // viewModelScope.launch(Dispatchers.IO) {
        tryLoginExistingAccount()
        // }
    }

    private fun tryLoginExistingAccount() {
        LocalPreferences.loadFromEncryptedStorage()?.let {
            startUI(it)
        }
    }

    fun startUI(key: String, useProxy: Boolean, proxyPort: Int) {
        val pattern = Pattern.compile(".+@.+\\.[a-z]+")
        val parsed = Nip19.uriToRoute(key)
        val pubKeyParsed = parsed?.hex?.hexToByteArray()
        val proxy = HttpClient.initProxy(useProxy, "127.0.0.1", proxyPort)

        val account =
            if (key.startsWith("nsec")) {
                Account(Persona(privKey = key.bechToBytes()), proxy = proxy, proxyPort = proxyPort)
            } else if (pubKeyParsed != null) {
                Account(Persona(pubKey = pubKeyParsed), proxy = proxy, proxyPort = proxyPort)
            } else if (pattern.matcher(key).matches()) {
                // Evaluate NIP-5
                Account(Persona(), proxy = proxy, proxyPort = proxyPort)
            } else {
                Account(Persona(Hex.decode(key)), proxy = proxy, proxyPort = proxyPort)
            }

        LocalPreferences.updatePrefsForLogin(account)
        startUI(account)
    }

    fun switchUser(npub: String) {
        prepareLogoutOrSwitch()
        LocalPreferences.switchToAccount(npub)
        tryLoginExistingAccount()
    }

    fun newKey(useProxy: Boolean, proxyPort: Int) {
        val proxy = HttpClient.initProxy(useProxy, "127.0.0.1", proxyPort)
        val account = Account(Persona(), proxy = proxy, proxyPort = proxyPort)
        // saves to local preferences
        LocalPreferences.updatePrefsForLogin(account)
        startUI(account)
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun startUI(account: Account) {
        if (account.loggedIn.privKey != null) {
            _accountContent.update { AccountState.LoggedIn(account) }
        } else {
            _accountContent.update { AccountState.LoggedInViewOnly(account) }
        }
        val scope = CoroutineScope(Job() + Dispatchers.IO)
        scope.launch {
            ServiceManager.start(account, context)
        }
        GlobalScope.launch(Dispatchers.Main) {
            account.saveable.observeForever(saveListener)
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private val saveListener: (com.vitorpamplona.amethyst.model.AccountState) -> Unit = {
        GlobalScope.launch(Dispatchers.IO) {
            LocalPreferences.saveToEncryptedStorage(it.account)
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun prepareLogoutOrSwitch() {
        when (val state = accountContent.value) {
            is AccountState.LoggedIn -> {
                GlobalScope.launch(Dispatchers.Main) {
                    state.account.saveable.removeObserver(saveListener)
                }
            }
            is AccountState.LoggedInViewOnly -> {
                GlobalScope.launch(Dispatchers.Main) {
                    state.account.saveable.removeObserver(saveListener)
                }
            }
            else -> {}
        }

        _accountContent.update { AccountState.LoggedOff }
    }

    fun logOff(npub: String) {
        prepareLogoutOrSwitch()
        LocalPreferences.updatePrefsForLogout(npub)
        tryLoginExistingAccount()
    }
}
