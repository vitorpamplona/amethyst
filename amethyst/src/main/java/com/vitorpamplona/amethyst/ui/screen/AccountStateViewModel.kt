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
import com.vitorpamplona.amethyst.ui.navigation.Route
import com.vitorpamplona.amethyst.ui.tor.TorSettings
import com.vitorpamplona.amethyst.ui.tor.TorSettingsFlow
import com.vitorpamplona.ammolite.relays.Constants
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip02FollowList.ReadWrite
import com.vitorpamplona.quartz.nip02FollowList.tags.ContactTag
import com.vitorpamplona.quartz.nip06KeyDerivation.Nip06
import com.vitorpamplona.quartz.nip17Dm.settings.ChatMessageRelayListEvent
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.bech32.bechToBytes
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEmbed
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip19Bech32.entities.NRelay
import com.vitorpamplona.quartz.nip19Bech32.entities.NSec
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip49PrivKeyEnc.Nip49
import com.vitorpamplona.quartz.nip50Search.SearchRelayListEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.utils.Hex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.DelicateCoroutinesApi
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

val EMAIL_PATTERN: Pattern = Pattern.compile(".+@.+\\.[a-z]+")

@Stable
class AccountStateViewModel : ViewModel() {
    private val _accountContent = MutableStateFlow<AccountState>(AccountState.Loading)
    val accountContent = _accountContent.asStateFlow()

    private var collectorJob: Job? = null

    fun tryLoginExistingAccountAsync() {
        // pulls account from storage.
        if (_accountContent.value !is AccountState.LoggedIn) {
            viewModelScope.launch {
                tryLoginExistingAccount()
            }
        }
    }

    private suspend fun tryLoginExistingAccount(route: Route? = null) =
        withContext(Dispatchers.IO) {
            LocalPreferences.loadCurrentAccountFromEncryptedStorage()
        }?.let { startUI(it, route) } ?: run { requestLoginUI() }

    private suspend fun requestLoginUI() {
        _accountContent.update { AccountState.LoggedOff }

        viewModelScope.launch(Dispatchers.IO) { Amethyst.instance.serviceManager.pauseAndLogOff() }
    }

    suspend fun loginAndStartUI(
        key: String,
        torSettings: TorSettings,
        transientAccount: Boolean,
        loginWithExternalSigner: Boolean = false,
        packageName: String = "",
    ) = withContext(Dispatchers.IO) {
        val parsed = Nip19Parser.uriToRoute(key)?.entity
        val pubKeyParsed =
            when (parsed) {
                is NSec -> null
                is NPub -> parsed.hex.hexToByteArray()
                is NProfile -> parsed.hex.hexToByteArray()
                is com.vitorpamplona.quartz.nip19Bech32.entities.Note -> null
                is NEvent -> null
                is NEmbed -> null
                is NRelay -> null
                is NAddress -> null
                else ->
                    try {
                        if (loginWithExternalSigner) Hex.decode(key) else null
                    } catch (e: Exception) {
                        null
                    }
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
            } else if (key.contains(" ") && Nip06().isValidMnemonic(key)) {
                AccountSettings(
                    keyPair = KeyPair(privKey = Nip06().privateKeyFromMnemonic(key)),
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
    suspend fun startUI(
        accountSettings: AccountSettings,
        route: Route? = null,
    ) = withContext(Dispatchers.Main) {
        _accountContent.update { AccountState.LoggedIn(accountSettings, route) }

        collectorJob?.cancel()
        collectorJob =
            viewModelScope.launch(Dispatchers.IO) {
                accountSettings.saveable.debounce(1000).collect {
                    if (it.accountSettings != null) {
                        LocalPreferences.saveToEncryptedStorage(it.accountSettings)
                    }
                }
            }
    }

    private fun prepareLogoutOrSwitch() =
        when (val state = _accountContent.value) {
            is AccountState.LoggedIn -> {
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
                        if (key.isEmpty() || password.isEmpty()) {
                            null
                        } else {
                            Nip49().decrypt(key, password)
                        }
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
                    okttpClient = { Amethyst.instance.okHttpClients.getHttpClient(false) },
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
                    backupUserMetadata = tempSigner.sign(MetadataEvent.newUser(name)),
                    backupContactList =
                        ContactListEvent.createFromScratch(
                            followUsers = listOf(ContactTag(keyPair.pubKey.toHexKey(), null, null)),
                            followEvents = DefaultChannels.toList(),
                            relayUse =
                                Constants.defaultRelays.associate {
                                    it.url to ReadWrite(it.read, it.write)
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

            @OptIn(DelicateCoroutinesApi::class)
            GlobalScope.launch(Dispatchers.IO) {
                delay(2000) // waits for the new user to connect to the new relays.
                accountSettings.backupUserMetadata?.let { Amethyst.instance.client.send(it) }
                accountSettings.backupContactList?.let { Amethyst.instance.client.send(it) }
                accountSettings.backupNIP65RelayList?.let { Amethyst.instance.client.send(it) }
                accountSettings.backupDMRelayList?.let { Amethyst.instance.client.send(it) }
                accountSettings.backupSearchRelayList?.let { Amethyst.instance.client.send(it) }
            }
        }
    }

    fun switchUser(accountInfo: AccountInfo) {
        viewModelScope.launch(Dispatchers.IO) {
            switchUserSync(accountInfo)
        }
    }

    suspend fun switchUserSync(
        npub: String,
        route: Route,
    ): Boolean {
        if (npub != LocalPreferences.currentAccount()) {
            val account = LocalPreferences.allSavedAccounts().firstOrNull { it.npub == npub }
            if (account != null) {
                switchUserSync(account, route)
                return true
            }
        }
        return false
    }

    suspend fun switchUserSync(
        accountInfo: AccountInfo,
        route: Route? = null,
    ) {
        prepareLogoutOrSwitch()
        LocalPreferences.switchToAccount(accountInfo)
        tryLoginExistingAccount(route)
    }

    fun currentAccount() =
        when (val state = _accountContent.value) {
            is AccountState.LoggedIn ->
                state.accountSettings.keyPair.pubKey
                    .toNpub()

            else -> null
        }

    fun logOff(accountInfo: AccountInfo) {
        viewModelScope.launch(Dispatchers.IO) {
            if (accountInfo.npub == currentAccount()) {
                // log off and relogin with the 0 account
                prepareLogoutOrSwitch()
                LocalPreferences.updatePrefsForLogout(accountInfo)
                tryLoginExistingAccount()
            } else {
                // delete without login off
                LocalPreferences.updatePrefsForLogout(accountInfo)
            }
        }
    }
}
