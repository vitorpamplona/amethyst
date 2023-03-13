package com.vitorpamplona.amethyst.ui.screen

import androidx.lifecycle.ViewModel
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.amethyst.ServiceManager
import com.vitorpamplona.amethyst.model.Account
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

class AccountStateViewModel() : ViewModel() {
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
            login(it)
        }
    }

    fun login(key: String) {
        val pattern = Pattern.compile(".+@.+\\.[a-z]+")

        val account =
            if (key.startsWith("nsec")) {
                Account(Persona(privKey = key.bechToBytes()))
            } else if (key.startsWith("npub")) {
                Account(Persona(pubKey = key.bechToBytes()))
            } else if (pattern.matcher(key).matches()) {
                // Evaluate NIP-5
                Account(Persona())
            } else {
                Account(Persona(Hex.decode(key)))
            }

        LocalPreferences.updatePrefsForLogin(account)
        login(account)
    }

    fun switchUser(npub: String) {
        prepareLogoutOrSwitch()
        LocalPreferences.switchToAccount(npub)
        tryLoginExistingAccount()
    }

    fun newKey() {
        val account = Account(Persona())
        LocalPreferences.updatePrefsForLogin(account)
        login(account)
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun login(account: Account) {
        LocalPreferences.updatePrefsForLogin(account)

        if (account.loggedIn.privKey != null) {
            _accountContent.update { AccountState.LoggedIn(account) }
        } else {
            _accountContent.update { AccountState.LoggedInViewOnly(account) }
        }

        val scope = CoroutineScope(Job() + Dispatchers.IO)
        scope.launch {
            ServiceManager.start(account)
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
