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
package com.vitorpamplona.amethyst.commons.account

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.defaults.DefaultNIP65RelaySet
import com.vitorpamplona.amethyst.commons.domain.nip46.BunkerLoginUseCase
import com.vitorpamplona.amethyst.commons.domain.nip46.NostrConnectLoginUseCase
import com.vitorpamplona.amethyst.commons.domain.nip46.stripBunkerSecret
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.AccountSettings
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip05DnsIdentifiers.Nip05Client
import com.vitorpamplona.quartz.nip05DnsIdentifiers.resolveUserHexOrNull
import com.vitorpamplona.quartz.nip06KeyDerivation.Nip06
import com.vitorpamplona.quartz.nip19Bech32.Bech32Transcription
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.decodePrivateKeyAsHexOrNull
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEmbed
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NNote
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip19Bech32.entities.NRelay
import com.vitorpamplona.quartz.nip19Bech32.entities.NSec
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerClientMetadata
import com.vitorpamplona.quartz.nip49PrivKeyEnc.Nip49
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

val EMAIL_PATTERN = Regex(".+@.+\\.[a-z]+")

private const val BUNKER_URI_PREFIX = "bunker://"

sealed class AccountState {
    object Loading : AccountState()

    object LoggedOff : AccountState()

    @Stable
    class LoggedIn(
        val account: Account,
        var route: Route? = null,
    ) : AccountState()
}

@Stable
class AccountSessionManager(
    val accountsCache: AccountCache,
    val nip05ClientBuilder: () -> Nip05Client,
    val clientBuilder: () -> INostrClient,
    val localPreferences: AccountSessionStore,
    val scope: CoroutineScope,
    val hooks: AccountSessionHooks = object : AccountSessionHooks {},
    /** Who this app says it is to a NIP-46 remote signer it logs in with. */
    val remoteSignerMetadata: BunkerClientMetadata? = null,
) {
    private val _accountContent = MutableStateFlow<AccountState>(AccountState.Loading)
    val accountContent = _accountContent.asStateFlow()

    fun loggedInAccount() = (_accountContent.value as? AccountState.LoggedIn)?.account

    fun loginWithDefaultAccountIfLoggedOff() {
        // pulls account from storage.
        if (_accountContent.value !is AccountState.LoggedIn) {
            scope.launch(Dispatchers.IO) {
                loginWithDefaultAccount()
            }
        }
    }

    private suspend fun loginWithDefaultAccount(routeBuilder: ((account: Account) -> Route?)? = null) {
        val accountSettings = localPreferences.loadAccountConfigFromEncryptedStorage()

        if (accountSettings != null) {
            startUI(accountSettings, routeBuilder)
        } else {
            requestLoginUI()
        }
    }

    private fun requestLoginUI() = _accountContent.update { AccountState.LoggedOff }

    suspend fun loginAndStartUI(
        key: String,
        transientAccount: Boolean,
        loginWithExternalSigner: Boolean = false,
        packageName: String = "",
    ) = withContext(Dispatchers.IO) {
        if (key.startsWith(BUNKER_URI_PREFIX)) {
            return@withContext startUI(localPreferences.setDefaultAccount(remoteSignerLogin(key, transientAccount)))
        }

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
            when {
                loginWithExternalSigner -> {
                    AccountSettings(
                        keyPair = KeyPair(pubKey = pubKeyParsed),
                        transientAccount = transientAccount,
                        externalSignerPackageName = packageName.ifBlank { "com.greenart7c3.nostrsigner" },
                    )
                }

                key.startsWith("nsec") -> {
                    val privHex =
                        decodePrivateKeyAsHexOrNull(key)
                            ?: throw Exception("Invalid nsec key")
                    AccountSettings(
                        keyPair = KeyPair(privKey = privHex.hexToByteArray()),
                        transientAccount = transientAccount,
                    )
                }

                key.contains(" ") && Nip06().isValidMnemonic(key) -> {
                    AccountSettings(
                        keyPair = KeyPair(privKey = Nip06().privateKeyFromMnemonic(key)),
                        transientAccount = transientAccount,
                    )
                }

                pubKeyParsed != null -> {
                    AccountSettings(
                        keyPair = KeyPair(pubKey = pubKeyParsed),
                        transientAccount = transientAccount,
                    )
                }

                else -> {
                    AccountSettings(
                        keyPair = KeyPair(Hex.decode(key)),
                        transientAccount = transientAccount,
                    )
                }
            }

        // setDefaultAccount may keep an existing signing account instead of this one when a
        // read-only npub is added for a pubkey we already sign for — show whichever became current.
        val current = localPreferences.setDefaultAccount(accountSettings)

        startUI(current)
    }

    /**
     * Connects to the NIP-46 signer at [bunkerUri] with a fresh transport key and returns the settings
     * of the account it signs for. The account cache opens its own signer from those settings, so
     * the one used to connect is closed here.
     */
    private suspend fun remoteSignerLogin(
        bunkerUri: String,
        transientAccount: Boolean,
    ): AccountSettings {
        val transport = KeyPair()
        val result = BunkerLoginUseCase.execute(bunkerUri, NostrSignerInternal(transport), clientBuilder(), remoteSignerMetadata)
        result.signer.closeSubscription()
        return remoteSignerSettings(result.pubKeyHex, stripBunkerSecret(bunkerUri), transport, transientAccount)
    }

    /**
     * Logs in through a NIP-46 signer that scans a `nostrconnect://` address: [onUri] receives the
     * address to show, and this returns once the signer answered (or throws when it never does).
     */
    suspend fun loginWithNostrConnect(
        relays: List<String>,
        appName: String,
        transientAccount: Boolean,
        onUri: (String) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val transport = KeyPair()
        val uriData = NostrConnectLoginUseCase.generateUri(transport, relays, appName)
        onUri(uriData.uri)

        val result =
            withTimeout(NostrConnectLoginUseCase.NOSTRCONNECT_TIMEOUT_MS) {
                NostrConnectLoginUseCase.awaitAndLogin(uriData, clientBuilder())
            }
        result.signer.closeSubscription()

        val bunkerUri = BUNKER_URI_PREFIX + result.signer.remotePubkey + "?" + uriData.relays.joinToString("&") { "relay=${it.url}" }
        startUI(localPreferences.setDefaultAccount(remoteSignerSettings(result.pubKeyHex, bunkerUri, transport, transientAccount)))
    }

    private fun remoteSignerSettings(
        userPubKeyHex: HexKey,
        bunkerUri: String,
        transport: KeyPair,
        transientAccount: Boolean,
    ) = AccountSettings(
        keyPair = KeyPair(pubKey = userPubKeyHex.hexToByteArray()),
        transientAccount = transientAccount,
        remoteSignerBunkerUri = bunkerUri,
        remoteSignerTransportKey = transport.privKey!!.toHexKey(),
    )

    fun startUI(
        accountSettings: AccountSettings,
        routeBuilder: ((account: Account) -> Route?)? = null,
    ) {
        val account = accountsCache.loadAccount(accountSettings)
        _accountContent.update {
            AccountState.LoggedIn(account, routeBuilder?.invoke(account))
        }
    }

    fun login(
        key: String,
        password: String,
        transientAccount: Boolean,
        loginWithExternalSigner: Boolean = false,
        packageName: String = "",
        onError: (String?) -> Unit,
    ) {
        // Accepts keys copied by hand from the backup screen: UPPERCASE, and split
        // into groups by spaces, dashes or line breaks.
        val cleanKey = Bech32Transcription.normalize(key)
        scope.launch(Dispatchers.IO) {
            if (cleanKey.startsWith("ncryptsec")) {
                val newKey =
                    try {
                        if (cleanKey.isEmpty() || password.isEmpty()) {
                            null
                        } else {
                            Nip49().decrypt(cleanKey, password)
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
            } else if (EMAIL_PATTERN.matches(cleanKey)) {
                // Delegate to the shared quartz resolver so NIP-05 handling stays in
                // lockstep with the CLI and anywhere else we accept user identifiers.
                try {
                    val hex =
                        resolveUserHexOrNull(cleanKey, nip05ClientBuilder())
                    if (hex == null) {
                        onError("User not found in the nip05 server: $cleanKey")
                    } else {
                        loginSync(Hex.decode(hex).toNpub(), transientAccount, loginWithExternalSigner, packageName, onError)
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    onError("Could not load nip05 address from the server: $cleanKey. ${e.message}")
                }
            } else {
                loginSync(cleanKey, transientAccount, loginWithExternalSigner, packageName, onError)
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
        scope.launch(Dispatchers.IO) {
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
            loginAndStartUI(key, transientAccount, loginWithExternalSigner, packageName)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("Login", "Could not sign in", e)
            onError("Could not sign in: " + e.message)
        }
    }

    fun newKey(name: String? = null) {
        scope.launch(Dispatchers.IO) {
            _accountContent.update { AccountState.Loading }

            val accountSettings = createNewAccount(name)

            localPreferences.setDefaultAccount(accountSettings)

            // Freshly-generated key: mark it as not-yet-backed-up so the home screen
            // nudges the user to save their secret key. Accounts logged in via an
            // existing nsec/bunker/external signer never get this false flag (the
            // pref defaults to true), so only brand-new accounts are nudged.
            localPreferences.setHasBackedUpKeys(false, accountSettings.keyPair.pubKey.toNpub())

            startUI(accountSettings, routeBuilder = { Route.ImportFollowsSelectUser })

            scope.launch(Dispatchers.IO) {
                delay(2000) // waits for the new user to connect to the new relays.

                val toPost = accountSettings.backupNIP65RelayList?.writeRelaysNorm()?.toSet() ?: DefaultNIP65RelaySet

                val client = clientBuilder()

                accountSettings.backupUserMetadata?.let { client.publish(it, toPost) }
                accountSettings.backupContactList?.let { client.publish(it, toPost) }
                accountSettings.backupNIP65RelayList?.let { client.publish(it, toPost) }
                accountSettings.backupDMRelayList?.let { client.publish(it, toPost) }
                accountSettings.backupKeyPackageRelayList?.let { client.publish(it, toPost) }
                accountSettings.backupSearchRelayList?.let { client.publish(it, toPost) }
                accountSettings.backupIndexRelayList?.let { client.publish(it, toPost) }
                accountSettings.backupFavoriteRelayList?.let { client.publish(it, toPost) }
                accountSettings.backupPublicChatList?.let { client.publish(it, toPost) }
                accountSettings.backupCashuWallet?.let { client.publish(it, toPost) }
                accountSettings.backupNutzapInfo?.let { client.publish(it, toPost) }
            }
        }
    }

    fun createNewAccount(name: String? = null): AccountSettings {
        val keyPair = KeyPair()
        val bootstrap =
            bootstrapAccountEvents(
                signer = NostrSignerSync(keyPair),
                name = name,
            )
        return AccountSettings(
            keyPair = keyPair,
            transientAccount = false,
            cashuCounters = hooks.cashuCountersFor(keyPair.pubKey.toNpub()),
            backupUserMetadata = bootstrap.userMetadata,
            backupContactList = bootstrap.contactList,
            backupNIP65RelayList = bootstrap.nip65RelayList,
            backupDMRelayList = bootstrap.dmRelayList,
            backupKeyPackageRelayList = bootstrap.keyPackageRelayList,
            backupSearchRelayList = bootstrap.searchRelayList,
            backupIndexRelayList = bootstrap.indexerRelayList,
            backupPublicChatList = bootstrap.publicChatList,
            backupFavoriteRelayList = bootstrap.favoriteRelayList,
        )
    }

    fun switchUser(accountInfo: AccountInfo) {
        scope.launch(Dispatchers.IO) {
            switchUserSync(accountInfo)
        }
    }

    suspend fun checkAndSwitchUserSync(
        npub: String,
        routeBuilder: ((account: Account) -> Route?)? = null,
    ): Boolean {
        if (npub != localPreferences.currentAccount()) {
            val account = localPreferences.allSavedAccounts().firstOrNull { it.npub == npub }
            if (account != null) {
                switchUserSync(account, routeBuilder)
                return true
            }
        }
        return false
    }

    private suspend fun switchUserSync(
        accountInfo: AccountInfo,
        routeBuilder: ((account: Account) -> Route?)? = null,
    ) {
        // Whatever the previous user had running (an audio room, a call) belongs to them, so it ends
        // before the swap. This is the real "account switch" hook; the Activity being destroyed is not.
        hooks.onSessionEnding()
        localPreferences.switchToAccount(accountInfo)
        loginWithDefaultAccount(routeBuilder)
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
        scope.launch(Dispatchers.IO) {
            val hex = decodePublicKeyAsHexOrNull(accountInfo.npub)
            if (hex == null) {
                Log.e("Logoff", "Cannot decode npub for account being logged off; aborting cleanup")
                return@launch
            }
            // Runs before anything else, whether or not this is the account currently on screen: the
            // platform may keep the account's contacts outside our own storage (Android's launcher
            // shortcuts).
            hooks.onAccountRemoving(accountInfo.npub)

            if (accountInfo.npub == currentAccountNPub()) {
                // End what the account had running before its state is torn down.
                hooks.onSessionEnding()
                // log off and relogin with the 0 account
                localPreferences.deleteAccount(accountInfo)
                accountsCache.removeAccount(hex)
                accountsCache.deleteAccountFiles(hex)
                hooks.onAccountRemoved(hex)
                loginWithDefaultAccount()
            } else {
                // delete without switching logins
                localPreferences.deleteAccount(accountInfo)
                accountsCache.removeAccount(hex)
                accountsCache.deleteAccountFiles(hex)
                hooks.onAccountRemoved(hex)
            }
        }
    }
}
