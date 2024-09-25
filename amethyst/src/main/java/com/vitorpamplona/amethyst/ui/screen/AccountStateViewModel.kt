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
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.amethyst.model.AccountSettings
import com.vitorpamplona.amethyst.model.DefaultChannels
import com.vitorpamplona.amethyst.model.DefaultDMRelayList
import com.vitorpamplona.amethyst.model.DefaultNIP65List
import com.vitorpamplona.amethyst.model.DefaultSearchRelayList
import com.vitorpamplona.amethyst.service.Nip05NostrAddressVerifier
import com.vitorpamplona.amethyst.ui.tor.TorSettings
import com.vitorpamplona.amethyst.ui.tor.TorSettingsFlow
import com.vitorpamplona.ammolite.relays.Client
import com.vitorpamplona.ammolite.relays.Constants
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.crypto.KeyPair
import com.vitorpamplona.quartz.encoders.Hex
import com.vitorpamplona.quartz.encoders.Nip19Bech32
import com.vitorpamplona.quartz.encoders.bechToBytes
import com.vitorpamplona.quartz.encoders.hexToByteArray
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.encoders.toNpub
import com.vitorpamplona.quartz.events.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.events.ChatMessageRelayListEvent
import com.vitorpamplona.quartz.events.Contact
import com.vitorpamplona.quartz.events.ContactListEvent
import com.vitorpamplona.quartz.events.MetadataEvent
import com.vitorpamplona.quartz.events.SearchRelayListEvent
import com.vitorpamplona.quartz.signers.NostrSignerSync
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

val EMAIL_PATTERN = Pattern.compile(".+@.+\\.[a-z]+")

@Stable
class AccountStateViewModel : ViewModel() {
    private val _accountContent = MutableStateFlow<AccountState>(AccountState.Loading)
    val accountContent = _accountContent.asStateFlow()

    private var collectorJob: Job? = null

    fun tryLoginExistingAccountAsync() {
        // pulls account from storage.
        viewModelScope.launch { tryLoginExistingAccount() }
    }

    private suspend fun tryLoginExistingAccount() =
        withContext(Dispatchers.IO) {
            LocalPreferences.loadCurrentAccountFromEncryptedStorage()
        }?.let { startUI(it) } ?: run { requestLoginUI() }

    private suspend fun requestLoginUI() {
        _accountContent.update { AccountState.LoggedOff }

        viewModelScope.launch(Dispatchers.IO) { Amethyst.instance.serviceManager.pauseForGoodAndClearAccount() }
    }

    suspend fun loginAndStartUI(
        key: String,
        torSettings: TorSettings,
        transientAccount: Boolean,
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

        if (loginWithExternalSigner && pubKeyParsed == null) {
            throw Exception("Invalid key while trying to login with external signer")
        }

        val account =
            if (loginWithExternalSigner) {
                AccountSettings(
                    keyPair = KeyPair(pubKey = pubKeyParsed),
                    transientAccount = transientAccount,
                    externalSignerPackageName = packageName.ifBlank { "com.greenart7c3.nostrsigner" },
                    torSettings = TorSettingsFlow.build(torSettings),
                )
            } else if (key.startsWith("nsec")) {
                AccountSettings(
                    keyPair = KeyPair(privKey = key.bechToBytes()),
                    transientAccount = transientAccount,
                    torSettings = TorSettingsFlow.build(torSettings),
                )
            } else if (key.contains(" ") && CryptoUtils.isValidMnemonic(key)) {
                AccountSettings(
                    keyPair = KeyPair(privKey = CryptoUtils.privateKeyFromMnemonic(key)),
                    transientAccount = transientAccount,
                    torSettings = TorSettingsFlow.build(torSettings),
                )
            } else if (pubKeyParsed != null) {
                AccountSettings(
                    keyPair = KeyPair(pubKey = pubKeyParsed),
                    transientAccount = transientAccount,
                    torSettings = TorSettingsFlow.build(torSettings),
                )
            } else {
                AccountSettings(
                    keyPair = KeyPair(Hex.decode(key)),
                    transientAccount = transientAccount,
                    torSettings = TorSettingsFlow.build(torSettings),
                )
            }

        LocalPreferences.updatePrefsForLogin(account)

        startUI(account)
    }

    @OptIn(FlowPreview::class)
    suspend fun startUI(accountSettings: AccountSettings) =
        withContext(Dispatchers.Main) {
            if (accountSettings.isWriteable()) {
                _accountContent.update { AccountState.LoggedIn(accountSettings) }
            } else {
                _accountContent.update { AccountState.LoggedInViewOnly(accountSettings) }
            }

            collectorJob?.cancel()
            collectorJob =
                viewModelScope.launch(Dispatchers.IO) {
                    accountSettings.saveable.debounce(1000).collect {
                        LocalPreferences.saveToEncryptedStorage(it.accountSettings)
                    }
                }
        }

    private fun prepareLogoutOrSwitch() =
        when (val state = _accountContent.value) {
            is AccountState.LoggedIn -> {
                collectorJob?.cancel()
                state.currentViewModelStore.viewModelStore.clear()
            }

            is AccountState.LoggedInViewOnly -> {
                collectorJob?.cancel()
                state.currentViewModelStore.viewModelStore.clear()
            }

            else -> {}
        }

    fun login(
        key: String,
        password: String,
        torSettings: TorSettings,
        transientAccount: Boolean,
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
                    loginSync(newKey, torSettings, transientAccount, loginWithExternalSigner, packageName, onError)
                }
            } else if (EMAIL_PATTERN.matcher(key).matches()) {
                Nip05NostrAddressVerifier().verifyNip05(
                    key,
                    forceProxy = { false },
                    onSuccess = { publicKey ->
                        loginSync(Hex.decode(publicKey).toNpub(), torSettings, transientAccount, loginWithExternalSigner, packageName, onError)
                    },
                    onError = {
                        onError(it)
                    },
                )
            } else {
                loginSync(key, torSettings, transientAccount, loginWithExternalSigner, packageName, onError)
            }
        }
    }

    fun login(
        key: String,
        torSettings: TorSettings,
        transientAccount: Boolean,
        loginWithExternalSigner: Boolean = false,
        packageName: String = "",
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            loginSync(key, torSettings, transientAccount, loginWithExternalSigner, packageName, onError)
        }
    }

    suspend fun loginSync(
        key: String,
        torSettings: TorSettings,
        transientAccount: Boolean,
        loginWithExternalSigner: Boolean = false,
        packageName: String = "",
        onError: (String) -> Unit,
    ) {
        try {
            loginAndStartUI(key, torSettings, transientAccount, loginWithExternalSigner, packageName)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("Login", "Could not sign in", e)
            onError("Could not sign in: " + e.message)
        }
    }

    fun newKey(
        torSettings: TorSettings,
        name: String? = null,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _accountContent.update { AccountState.Loading }

            val keyPair = KeyPair()
            val tempSigner = NostrSignerSync(keyPair)

            val accountSettings =
                AccountSettings(
                    keyPair = keyPair,
                    transientAccount = false,
                    backupUserMetadata = MetadataEvent.newUser(name, tempSigner),
                    backupContactList =
                        ContactListEvent.createFromScratch(
                            followUsers = listOf(Contact(keyPair.pubKey.toHexKey(), null)),
                            followEvents = DefaultChannels.toList(),
                            relayUse =
                                Constants.defaultRelays.associate {
                                    it.url to ContactListEvent.ReadWrite(it.read, it.write)
                                },
                            signer = tempSigner,
                        ),
                    backupNIP65RelayList = AdvertisedRelayListEvent.create(DefaultNIP65List, tempSigner),
                    backupDMRelayList = ChatMessageRelayListEvent.create(DefaultDMRelayList, tempSigner),
                    backupSearchRelayList = SearchRelayListEvent.create(DefaultSearchRelayList, tempSigner),
                    torSettings = TorSettingsFlow.build(torSettings),
                )

            // saves to local preferences
            LocalPreferences.updatePrefsForLogin(accountSettings)

            startUI(accountSettings)

            GlobalScope.launch(Dispatchers.IO) {
                delay(2000) // waits for the new user to connect to the new relays.
                accountSettings.backupUserMetadata?.let { Client.send(it) }
                accountSettings.backupContactList?.let { Client.send(it) }
                accountSettings.backupNIP65RelayList?.let { Client.send(it) }
                accountSettings.backupDMRelayList?.let { Client.send(it) }
                accountSettings.backupSearchRelayList?.let { Client.send(it) }
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
