/*
 * Copyright (c) 2025 Vitor Pamplona
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

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.AccountInfo
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.amethyst.model.AccountSettings
import com.vitorpamplona.amethyst.model.DefaultChannels
import com.vitorpamplona.amethyst.model.DefaultDMRelayList
import com.vitorpamplona.amethyst.model.DefaultIndexerRelayList
import com.vitorpamplona.amethyst.model.DefaultNIP65List
import com.vitorpamplona.amethyst.model.DefaultNIP65RelaySet
import com.vitorpamplona.amethyst.model.DefaultSearchRelayList
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip02FollowList.tags.ContactTag
import com.vitorpamplona.quartz.nip05DnsIdentifiers.Nip05Id
import com.vitorpamplona.quartz.nip06KeyDerivation.Nip06
import com.vitorpamplona.quartz.nip17Dm.settings.ChatMessageRelayListEvent
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.bech32.bechToBytes
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEmbed
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NNote
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip19Bech32.entities.NRelay
import com.vitorpamplona.quartz.nip19Bech32.entities.NSec
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip28PublicChat.list.ChannelListEvent
import com.vitorpamplona.quartz.nip49PrivKeyEnc.Nip49
import com.vitorpamplona.quartz.nip50Search.SearchRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.IndexerRelayListEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.Log
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

val EMAIL_PATTERN: Pattern = Pattern.compile(".+@.+\\.[a-z]+")

@Stable
class AccountStateViewModel : ViewModel() {
    private val _accountContent = MutableStateFlow<AccountState>(AccountState.Loading)
    val accountContent = _accountContent.asStateFlow()

    fun loginWithDefaultAccountIfLoggedOff() {
        // pulls account from storage.
        if (_accountContent.value !is AccountState.LoggedIn) {
            viewModelScope.launch(Dispatchers.IO) {
                loginWithDefaultAccount()
            }
        }
    }

    private suspend fun loginWithDefaultAccount(route: Route? = null) {
        val accountSettings = LocalPreferences.loadAccountConfigFromEncryptedStorage()

        if (accountSettings != null) {
            startUI(accountSettings, route)
        } else {
            requestLoginUI()
        }
    }

    private suspend fun requestLoginUI() = _accountContent.update { AccountState.LoggedOff }

    suspend fun loginAndStartUI(
        key: String,
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
                is NNote -> null
                is NEvent -> null
                is NEmbed -> null
                is NRelay -> null
                is NAddress -> null
                else -> runCatching { if (loginWithExternalSigner) Hex.decode(key) else null }.getOrNull()
            }

        if (loginWithExternalSigner && pubKeyParsed == null) {
            throw Exception("Invalid key while trying to login with external signer")
        }

        val accountSettings =
            if (loginWithExternalSigner) {
                AccountSettings(
                    keyPair = KeyPair(pubKey = pubKeyParsed),
                    transientAccount = transientAccount,
                    externalSignerPackageName = packageName.ifBlank { "com.greenart7c3.nostrsigner" },
                )
            } else if (key.startsWith("nsec")) {
                AccountSettings(
                    keyPair = KeyPair(privKey = key.bechToBytes()),
                    transientAccount = transientAccount,
                )
            } else if (key.contains(" ") && Nip06().isValidMnemonic(key)) {
                AccountSettings(
                    keyPair = KeyPair(privKey = Nip06().privateKeyFromMnemonic(key)),
                    transientAccount = transientAccount,
                )
            } else if (pubKeyParsed != null) {
                AccountSettings(
                    keyPair = KeyPair(pubKey = pubKeyParsed),
                    transientAccount = transientAccount,
                )
            } else {
                AccountSettings(
                    keyPair = KeyPair(Hex.decode(key)),
                    transientAccount = transientAccount,
                )
            }

        LocalPreferences.setDefaultAccount(accountSettings)

        startUI(accountSettings)
    }

    fun startUI(
        accountSettings: AccountSettings,
        route: Route? = null,
    ) {
        val account = Amethyst.instance.accountsCache.loadAccount(accountSettings)
        _accountContent.update {
            AccountState.LoggedIn(account, route)
        }
    }

    private fun prepareLogoutOrSwitch() =
        when (val state = _accountContent.value) {
            is AccountState.LoggedIn -> {
                state.currentViewModelStore.viewModelStore.clear()
            }

            else -> {}
        }

    fun login(
        key: String,
        password: String,
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
                    loginSync(newKey, transientAccount, loginWithExternalSigner, packageName, onError)
                }
            } else if (EMAIL_PATTERN.matcher(key).matches()) {
                val nip05 = Nip05Id.parse(key)
                if (nip05 == null) {
                    onError("Could not parse nip05 address: $nip05")
                } else {
                    try {
                        val pubkeyInfo = Amethyst.instance.nip05Client.get(nip05)
                        if (pubkeyInfo == null) {
                            onError("User not found in the nip05 server: $nip05")
                        } else {
                            loginSync(Hex.decode(pubkeyInfo.pubkey).toNpub(), transientAccount, loginWithExternalSigner, packageName, onError)
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        onError("Could not load nip05 address from the server: $nip05. ${e.message}")
                    }
                }
            } else {
                loginSync(key, transientAccount, loginWithExternalSigner, packageName, onError)
            }
        }
    }

    fun login(
        key: String,
        transientAccount: Boolean,
        loginWithExternalSigner: Boolean = false,
        packageName: String = "",
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            loginSync(key, transientAccount, loginWithExternalSigner, packageName, onError)
        }
    }

    suspend fun loginSync(
        key: String,
        transientAccount: Boolean,
        loginWithExternalSigner: Boolean = false,
        packageName: String = "",
        onError: (String) -> Unit,
    ) {
        try {
            if (_accountContent.value is AccountState.LoggedIn) {
                prepareLogoutOrSwitch()
            }
            loginAndStartUI(key, transientAccount, loginWithExternalSigner, packageName)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("Login", "Could not sign in", e)
            onError("Could not sign in: " + e.message)
        }
    }

    fun newKey(name: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            if (_accountContent.value is AccountState.LoggedIn) {
                prepareLogoutOrSwitch()
            }

            _accountContent.update { AccountState.Loading }

            val accountSettings = createNewAccount(name)

            LocalPreferences.setDefaultAccount(accountSettings)

            startUI(accountSettings)

            @OptIn(DelicateCoroutinesApi::class)
            GlobalScope.launch(Dispatchers.IO) {
                delay(2000) // waits for the new user to connect to the new relays.

                val toPost = accountSettings.backupNIP65RelayList?.writeRelaysNorm()?.toSet() ?: DefaultNIP65RelaySet

                accountSettings.backupUserMetadata?.let { Amethyst.instance.client.send(it, toPost) }
                accountSettings.backupContactList?.let { Amethyst.instance.client.send(it, toPost) }
                accountSettings.backupNIP65RelayList?.let { Amethyst.instance.client.send(it, toPost) }
                accountSettings.backupDMRelayList?.let { Amethyst.instance.client.send(it, toPost) }
                accountSettings.backupSearchRelayList?.let { Amethyst.instance.client.send(it, toPost) }
                accountSettings.backupIndexRelayList?.let { Amethyst.instance.client.send(it, toPost) }
            }
        }
    }

    fun createNewAccount(name: String? = null): AccountSettings {
        val keyPair = KeyPair()
        val tempSigner = NostrSignerSync(keyPair)

        return AccountSettings(
            keyPair = keyPair,
            transientAccount = false,
            backupUserMetadata = tempSigner.sign(MetadataEvent.newUser(name)),
            backupContactList =
                ContactListEvent.createFromScratch(
                    followUsers = listOf(ContactTag(keyPair.pubKey.toHexKey(), null, null)),
                    relayUse = emptyMap(),
                    signer = tempSigner,
                ),
            backupNIP65RelayList = AdvertisedRelayListEvent.create(DefaultNIP65List, tempSigner),
            backupDMRelayList = ChatMessageRelayListEvent.create(DefaultDMRelayList, tempSigner),
            backupSearchRelayList = SearchRelayListEvent.create(DefaultSearchRelayList.toList(), tempSigner),
            backupIndexRelayList = IndexerRelayListEvent.create(DefaultIndexerRelayList.toList(), tempSigner),
            backupChannelList = ChannelListEvent.create(emptyList(), DefaultChannels, tempSigner),
        )
    }

    fun switchUser(accountInfo: AccountInfo) {
        viewModelScope.launch(Dispatchers.IO) {
            switchUserSync(accountInfo)
        }
    }

    suspend fun checkAndSwitchUserSync(
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

    private suspend fun switchUserSync(
        accountInfo: AccountInfo,
        route: Route? = null,
    ) {
        prepareLogoutOrSwitch()
        LocalPreferences.switchToAccount(accountInfo)
        loginWithDefaultAccount(route)
    }

    fun currentAccountNPub() =
        when (val state = _accountContent.value) {
            is AccountState.LoggedIn -> {
                state.account.signer.pubKey
                    .hexToByteArray()
                    .toNpub()
            }

            else -> {
                null
            }
        }

    fun logOff(accountInfo: AccountInfo) {
        viewModelScope.launch(Dispatchers.IO) {
            if (accountInfo.npub == currentAccountNPub()) {
                // log off and relogin with the 0 account
                prepareLogoutOrSwitch()
                LocalPreferences.deleteAccount(accountInfo)
                Amethyst.instance.accountsCache.removeAccount(accountInfo.npub.bechToBytes().toHexKey())
                loginWithDefaultAccount()
            } else {
                // delete without switching logins
                LocalPreferences.deleteAccount(accountInfo)
                Amethyst.instance.accountsCache.removeAccount(accountInfo.npub.bechToBytes().toHexKey())
            }
        }
    }
}
