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

import android.util.Log
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.AccountInfo
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.amethyst.ServiceManager
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.service.HttpClientManager
import com.vitorpamplona.amethyst.service.Nip05NostrAddressVerifier
import com.vitorpamplona.amethyst.service.relays.Client
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.crypto.KeyPair
import com.vitorpamplona.quartz.crypto.nip06.Nip06
import com.vitorpamplona.quartz.encoders.Hex
import com.vitorpamplona.quartz.encoders.Nip19Bech32
import com.vitorpamplona.quartz.encoders.bechToBytes
import com.vitorpamplona.quartz.encoders.hexToByteArray
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.encoders.toNpub
import com.vitorpamplona.quartz.signers.ExternalSignerLauncher
import com.vitorpamplona.quartz.signers.NostrSignerExternal
import com.vitorpamplona.quartz.signers.NostrSignerInternal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

val EMAIL_PATTERN = Pattern.compile(".+@.+\\.[a-z]+")

@Stable
class AccountStateViewModel : ViewModel() {
    var serviceManager: ServiceManager? = null

    private val _accountContent = MutableStateFlow<AccountState>(AccountState.Loading)
    val accountContent = _accountContent.asStateFlow()

    fun tryLoginExistingAccountAsync() {
        // pulls account from storage.
        viewModelScope.launch(Dispatchers.IO) { tryLoginExistingAccount() }
    }

    private suspend fun tryLoginExistingAccount() =
        withContext(Dispatchers.IO) {
            LocalPreferences.loadCurrentAccountFromEncryptedStorage()?.let { startUI(it) } ?: run { requestLoginUI() }
        }

    private suspend fun requestLoginUI() {
        _accountContent.update { AccountState.LoggedOff }

        viewModelScope.launch(Dispatchers.IO) { serviceManager?.pauseForGoodAndClearAccount() }
    }

    suspend fun loginAndStartUI(
        key: String,
        useProxy: Boolean,
        proxyPort: Int,
        loginWithExternalSigner: Boolean = false,
        packageName: String = "",
    ) = withContext(Dispatchers.IO) {
        val parsed = Nip19Bech32.uriToRoute(key)?.entity
        val pubKeyParsed =
            when (parsed) {
                is Nip19Bech32.NSec -> null
                is Nip19Bech32.NPub -> parsed.hex.hexToByteArray()
                is Nip19Bech32.NProfile -> parsed.hex.hexToByteArray()
                is Nip19Bech32.Note -> null
                is Nip19Bech32.NEvent -> null
                is Nip19Bech32.NEmbed -> null
                is Nip19Bech32.NRelay -> null
                is Nip19Bech32.NAddress -> null
                else -> null
            }

        val proxy = HttpClientManager.initProxy(useProxy, "127.0.0.1", proxyPort)

        if (loginWithExternalSigner && pubKeyParsed == null) {
            throw Exception("Invalid key while trying to login with external signer")
        }

        val account =
            if (loginWithExternalSigner) {
                val keyPair = KeyPair(pubKey = pubKeyParsed)
                val localPackageName = packageName.ifBlank { "com.greenart7c3.nostrsigner" }
                Account(
                    keyPair,
                    proxy = proxy,
                    proxyPort = proxyPort,
                    signer =
                        NostrSignerExternal(
                            keyPair.pubKey.toHexKey(),
                            ExternalSignerLauncher(keyPair.pubKey.toNpub(), localPackageName),
                        ),
                )
            } else if (key.startsWith("nsec")) {
                val keyPair = KeyPair(privKey = key.bechToBytes())
                Account(
                    keyPair,
                    proxy = proxy,
                    proxyPort = proxyPort,
                    signer = NostrSignerInternal(keyPair),
                )
            } else if (key.contains(" ") && Nip06().isValidMnemonic(key)) {
                val keyPair = KeyPair(privKey = Nip06().privateKeyFromMnemonic(key))
                Account(
                    keyPair,
                    proxy = proxy,
                    proxyPort = proxyPort,
                    signer = NostrSignerInternal(keyPair),
                )
            } else if (pubKeyParsed != null) {
                val keyPair = KeyPair(pubKey = pubKeyParsed)
                Account(
                    keyPair,
                    proxy = proxy,
                    proxyPort = proxyPort,
                    signer = NostrSignerInternal(keyPair),
                )
            } else {
                val keyPair = KeyPair(Hex.decode(key))
                Account(
                    keyPair,
                    proxy = proxy,
                    proxyPort = proxyPort,
                    signer = NostrSignerInternal(keyPair),
                )
            }

        LocalPreferences.updatePrefsForLogin(account)

        startUI(account)
    }

    suspend fun startUI(
        account: Account,
        onServicesReady: (() -> Unit)? = null,
    ) = withContext(Dispatchers.Main) {
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

            if (onServicesReady != null) {
                // waits for the connection to go through
                delay(1000)
                onServicesReady()
            }
        }

        account.saveable.observeForever(saveListener)
    }

    @OptIn(DelicateCoroutinesApi::class)
    private val saveListener: (com.vitorpamplona.amethyst.model.AccountState) -> Unit = {
        GlobalScope.launch(Dispatchers.IO) { LocalPreferences.saveToEncryptedStorage(it.account) }
    }

    private suspend fun prepareLogoutOrSwitch() =
        withContext(Dispatchers.Main) {
            when (val state = _accountContent.value) {
                is AccountState.LoggedIn -> {
                    state.account.saveable.removeObserver(saveListener)
                    withContext(Dispatchers.IO) { state.currentViewModelStore.viewModelStore.clear() }
                }
                is AccountState.LoggedInViewOnly -> {
                    state.account.saveable.removeObserver(saveListener)
                    withContext(Dispatchers.IO) { state.currentViewModelStore.viewModelStore.clear() }
                }
                else -> {}
            }
        }

    fun login(
        key: String,
        password: String,
        useProxy: Boolean,
        proxyPort: Int,
        loginWithExternalSigner: Boolean = false,
        packageName: String = "",
        onError: (String?) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            if (key.startsWith("ncryptsec")) {
                val newKey =
                    try {
                        CryptoUtils.decryptNIP49(key, password)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        onError(e.message)
                        return@launch
                    }

                if (newKey == null) {
                    onError("Could not decrypt key with provided password")
                    Log.e("Login", "Could not decrypt ncryptsec")
                } else {
                    loginSync(newKey, useProxy, proxyPort, loginWithExternalSigner, packageName) {
                        onError(null)
                    }
                }
            } else if (EMAIL_PATTERN.matcher(key).matches()) {
                Nip05NostrAddressVerifier().verifyNip05(
                    key,
                    onSuccess = { publicKey ->
                        loginSync(Hex.decode(publicKey).toNpub(), useProxy, proxyPort, loginWithExternalSigner, packageName) {
                            onError(null)
                        }
                    },
                    onError = {
                        onError(it)
                    },
                )
            } else {
                loginSync(key, useProxy, proxyPort, loginWithExternalSigner, packageName) {
                    onError(null)
                }
            }
        }
    }

    fun login(
        key: String,
        useProxy: Boolean,
        proxyPort: Int,
        loginWithExternalSigner: Boolean = false,
        packageName: String = "",
        onError: () -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            loginSync(key, useProxy, proxyPort, loginWithExternalSigner, packageName, onError)
        }
    }

    suspend fun loginSync(
        key: String,
        useProxy: Boolean,
        proxyPort: Int,
        loginWithExternalSigner: Boolean = false,
        packageName: String = "",
        onError: () -> Unit,
    ) {
        try {
            loginAndStartUI(key, useProxy, proxyPort, loginWithExternalSigner, packageName)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("Login", "Could not sign in", e)
            onError()
        }
    }

    fun newKey(
        useProxy: Boolean,
        proxyPort: Int,
        name: String? = null,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val proxy = HttpClientManager.initProxy(useProxy, "127.0.0.1", proxyPort)
            val keyPair = KeyPair()
            val account =
                Account(
                    keyPair,
                    proxy = proxy,
                    proxyPort = proxyPort,
                    signer = NostrSignerInternal(keyPair),
                )

            account.follow(account.userProfile())

            // saves to local preferences
            LocalPreferences.updatePrefsForLogin(account)
            startUI(account) {
                account.userProfile().latestContactList?.let { Client.send(it) }
                account.sendNewUserMetadata(name = name)
            }
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
