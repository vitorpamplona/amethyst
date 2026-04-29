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
package com.vitorpamplona.amethyst.desktop.account

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.domain.nip46.BunkerLoginUseCase
import com.vitorpamplona.amethyst.commons.domain.nip46.NostrConnectLoginUseCase
import com.vitorpamplona.amethyst.commons.domain.nip46.SignerConnectionState
import com.vitorpamplona.amethyst.commons.keystorage.SecureKeyStorage
import com.vitorpamplona.amethyst.commons.keystorage.SecureStorageException
import com.vitorpamplona.amethyst.commons.model.account.AccountInfo
import com.vitorpamplona.amethyst.commons.model.account.SignerType
import com.vitorpamplona.amethyst.desktop.network.DesktopHttpClient
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.EventCmd
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.BasicOkHttpWebSocket
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nip19Bech32.decodePrivateKeyAsHexOrNull
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip19Bech32.toNsec
import com.vitorpamplona.quartz.nip46RemoteSigner.signer.NostrSignerRemote
import com.vitorpamplona.quartz.nip47WalletConnect.Nip47WalletConnect
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission

sealed class AccountState {
    data object LoggedOut : AccountState()

    data object ConnectingRelays : AccountState()

    data class LoggedIn(
        val signer: NostrSigner,
        val pubKeyHex: String,
        val npub: String,
        val nsec: String?,
        val isReadOnly: Boolean,
        val signerType: SignerType = SignerType.Internal,
    ) : AccountState()
}

@Stable
class AccountManager internal constructor(
    private val secureStorage: SecureKeyStorage,
    private val homeDir: File = File(System.getProperty("user.home")),
) {
    companion object {
        fun create(context: Any? = null): AccountManager {
            val storage = SecureKeyStorage.create(context)
            return AccountManager(storage)
        }

        internal const val HEARTBEAT_INTERVAL_MS = 60_000L
        internal const val MAX_CONSECUTIVE_FAILURES = 3

        internal fun bunkerEphemeralKeyAlias(npub: String) = "bunker_ephemeral_$npub"

        // Legacy alias for migration
        internal const val LEGACY_BUNKER_EPHEMERAL_KEY_ALIAS = "bunker_ephemeral"
        internal const val NIP46_RELAY_CONNECT_TIMEOUT_MS = 15_000L
        internal val NIP46_RELAYS = listOf("wss://relay.nsec.app")
    }

    private val amethystDir: File by lazy {
        File(homeDir, ".amethyst")
    }

    val accountStorage: DesktopAccountStorage by lazy {
        DesktopAccountStorage(secureStorage, homeDir)
    }

    private val _allAccounts = MutableStateFlow<ImmutableList<AccountInfo>>(persistentListOf())
    val allAccounts: StateFlow<ImmutableList<AccountInfo>> = _allAccounts.asStateFlow()

    private val _accountState = MutableStateFlow<AccountState>(AccountState.LoggedOut)
    val accountState: StateFlow<AccountState> = _accountState.asStateFlow()

    private val _nwcConnection = MutableStateFlow<Nip47WalletConnect.Nip47URINorm?>(null)
    val nwcConnection: StateFlow<Nip47WalletConnect.Nip47URINorm?> = _nwcConnection.asStateFlow()

    private val _signerConnectionState = MutableStateFlow<SignerConnectionState>(SignerConnectionState.NotRemote)
    val signerConnectionState: StateFlow<SignerConnectionState> = _signerConnectionState.asStateFlow()

    private val _lastPingTimeSec = MutableStateFlow<Long?>(null)
    val lastPingTimeSec: StateFlow<Long?> = _lastPingTimeSec.asStateFlow()

    private val _forceLogoutReason = MutableStateFlow<String?>(null)
    val forceLogoutReason: StateFlow<String?> = _forceLogoutReason.asStateFlow()

    private val _loginProgress = MutableStateFlow<LoginProgress?>(null)
    val loginProgress: StateFlow<LoginProgress?> = _loginProgress.asStateFlow()

    private var heartbeatJob: Job? = null

    // --- Dedicated NIP-46 client (isolated from general relay pool) ---

    private val nip46ClientMutex = Mutex()
    private var nip46Client: NostrClient? = null

    private suspend fun getOrCreateNip46Client(): INostrClient =
        nip46ClientMutex.withLock {
            nip46Client ?: NostrClient(
                BasicOkHttpWebSocket.Builder { url -> DesktopHttpClient.currentClient() },
            ).also {
                nip46Client = it
                it.connect()
            }
        }

    suspend fun disconnectNip46Client() =
        nip46ClientMutex.withLock {
            nip46Client?.disconnect()
            nip46Client = null
        }

    /**
     * Waits for the NIP-46 client to connect to at least one of the target relays.
     * openSubscription() triggers async relay connection via sendOrConnectAndSync,
     * but we must wait for the websocket to be ready before sending requests.
     */
    private suspend fun awaitNip46RelayConnection(
        client: INostrClient,
        targetRelays: Set<NormalizedRelayUrl>,
    ) {
        withTimeout(NIP46_RELAY_CONNECT_TIMEOUT_MS) {
            client.connectedRelaysFlow().first { connected ->
                targetRelays.any { it in connected }
            }
        }
    }

    private fun updateRelayLoginStatus(
        relay: NormalizedRelayUrl,
        status: RelayLoginStatus,
    ) {
        val current = _loginProgress.value ?: return
        val updated = current.relayStatuses + (relay to status)
        _loginProgress.value =
            when (current) {
                is LoginProgress.ConnectingToRelays -> current.copy(relayStatuses = updated)
                is LoginProgress.WaitingForSigner -> current.copy(relayStatuses = updated)
                is LoginProgress.SendingAck -> current.copy(relayStatuses = updated)
            }
    }

    private fun createLoginRelayListener(): RelayConnectionListener =
        object : RelayConnectionListener {
            override fun onConnected(
                relay: IRelayClient,
                pingMillis: Int,
                compressed: Boolean,
            ) {
                updateRelayLoginStatus(relay.url, RelayLoginStatus.CONNECTED)
            }

            override fun onCannotConnect(
                relay: IRelayClient,
                errorMessage: String,
            ) {
                updateRelayLoginStatus(relay.url, RelayLoginStatus.FAILED)
            }

            override fun onSent(
                relay: IRelayClient,
                cmdStr: String,
                cmd: Command,
                success: Boolean,
            ) {
                if (cmd is EventCmd) {
                    updateRelayLoginStatus(
                        relay.url,
                        if (success) RelayLoginStatus.EVENT_SENT else RelayLoginStatus.SEND_FAILED,
                    )
                }
            }

            override fun onIncomingMessage(
                relay: IRelayClient,
                msgStr: String,
                msg: Message,
            ) {
            }
        }

    // --- Account loading ---

    suspend fun loadSavedAccount(): Result<AccountState.LoggedIn> =
        try {
            val lastNpub = getLastNpub()
            val bunkerUri = getBunkerUri()

            if (bunkerUri != null) {
                loadBunkerAccount(bunkerUri, lastNpub)
            } else if (lastNpub != null) {
                loadInternalAccount(lastNpub)
            } else {
                Result.failure(Exception("No saved account"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }

    private suspend fun loadInternalAccount(npub: String): Result<AccountState.LoggedIn> {
        val privKeyHex = secureStorage.getPrivateKey(npub)

        if (privKeyHex != null) {
            val keyPair = KeyPair(privKey = privKeyHex.hexToByteArray())
            val signer = NostrSignerInternal(keyPair)

            val state =
                AccountState.LoggedIn(
                    signer = signer,
                    pubKeyHex = keyPair.pubKey.toHexKey(),
                    npub = keyPair.pubKey.toNpub(),
                    nsec = keyPair.privKey?.toNsec(),
                    isReadOnly = false,
                )
            _accountState.value = state
            return Result.success(state)
        }

        // No private key — fall back to read-only
        return loadReadOnlyAccount(npub)
    }

    private suspend fun loadBunkerAccount(
        bunkerUri: String,
        npub: String?,
    ): Result<AccountState.LoggedIn> {
        // Try per-account alias first, fall back to legacy shared alias
        val perAccountKey = if (npub != null) secureStorage.getPrivateKey(bunkerEphemeralKeyAlias(npub)) else null
        val ephemeralPrivKeyHex =
            perAccountKey?.takeIf { it.isNotEmpty() }
                ?: secureStorage.getPrivateKey(LEGACY_BUNKER_EPHEMERAL_KEY_ALIAS)?.takeIf { it.isNotEmpty() }
                ?: return Result.failure(Exception("Ephemeral key not found"))

        val ephemeralKeyPair = KeyPair(privKey = ephemeralPrivKeyHex.hexToByteArray())
        val ephemeralSigner = NostrSignerInternal(ephemeralKeyPair)

        val nip46Client = getOrCreateNip46Client()
        val remoteSigner = NostrSignerRemote.fromBunkerUri(bunkerUri, ephemeralSigner, nip46Client)
        remoteSigner.openSubscription()

        val pubKeyHex =
            if (npub != null) {
                decodePublicKeyAsHexOrNull(npub) ?: return Result.failure(Exception("Invalid saved npub"))
            } else {
                // npub missing (e.g. last_account.txt deleted) — must wait for relay
                // before calling getPublicKey() to recover from signer
                awaitNip46RelayConnection(nip46Client, remoteSigner.relays)
                remoteSigner.getPublicKey()
            }

        val resolvedNpub = npub ?: pubKeyHex.hexToByteArray().toNpub()
        if (npub == null) saveLastNpub(resolvedNpub)

        val state =
            AccountState.LoggedIn(
                signer = remoteSigner,
                pubKeyHex = pubKeyHex,
                npub = resolvedNpub,
                nsec = null,
                isReadOnly = false,
                signerType = SignerType.Remote(bunkerUri),
            )
        _accountState.value = state
        _signerConnectionState.value = SignerConnectionState.Connected
        return Result.success(state)
    }

    // --- Bunker login ---

    suspend fun loginWithBunker(bunkerUri: String): Result<AccountState.LoggedIn> {
        val listener = createLoginRelayListener()
        var client: INostrClient? = null
        try {
            val ephemeralKeyPair = KeyPair()
            val ephemeralSigner = NostrSignerInternal(ephemeralKeyPair)

            val nip46Client = getOrCreateNip46Client()
            client = nip46Client

            val relaysFromUri = parseBunkerRelays(bunkerUri)
            _loginProgress.value =
                LoginProgress.ConnectingToRelays(
                    relaysFromUri.associateWith { RelayLoginStatus.CONNECTING },
                )
            nip46Client.addConnectionListener(listener)

            _loginProgress.value =
                LoginProgress.WaitingForSigner(
                    relayStatuses = _loginProgress.value?.relayStatuses.orEmpty(),
                )

            val result = BunkerLoginUseCase.execute(bunkerUri, ephemeralSigner, nip46Client)

            val state =
                AccountState.LoggedIn(
                    signer = result.signer,
                    pubKeyHex = result.pubKeyHex,
                    npub = result.pubKeyHex.hexToByteArray().toNpub(),
                    nsec = null,
                    isReadOnly = false,
                    signerType = SignerType.Remote(bunkerUri),
                )
            _accountState.value = state
            _signerConnectionState.value = SignerConnectionState.Connected

            saveBunkerAccount(
                bunkerUri = stripBunkerSecret(bunkerUri),
                ephemeralPrivKeyHex = ephemeralKeyPair.privKey!!.toHexKey(),
                npub = state.npub,
            )

            return Result.success(state)
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            return Result.failure(Exception("Could not connect to NIP-46 relay. Check your network connection."))
        } catch (e: SignerExceptions.TimedOutException) {
            return Result.failure(Exception("Connection timed out. Ensure remote signer is online and has approved the connection."))
        } catch (e: SignerExceptions.ManuallyUnauthorizedException) {
            return Result.failure(Exception("Connection rejected by remote signer."))
        } catch (e: SignerExceptions.CouldNotPerformException) {
            return Result.failure(Exception("Remote signer error: ${e.message}"))
        } catch (e: Exception) {
            return Result.failure(Exception("Connection failed: ${e.message}"))
        } finally {
            _loginProgress.value = null
            client?.removeConnectionListener(listener)
        }
    }

    // --- Nostrconnect login ---

    suspend fun loginWithNostrConnect(onUriGenerated: (String) -> Unit): Result<AccountState.LoggedIn> {
        val listener = createLoginRelayListener()
        var client: INostrClient? = null
        try {
            val ephemeralKeyPair = KeyPair()
            val uriData = NostrConnectLoginUseCase.generateUri(ephemeralKeyPair, NIP46_RELAYS, "Amethyst%20Desktop")

            val nip46Client = getOrCreateNip46Client()
            client = nip46Client

            _loginProgress.value =
                LoginProgress.ConnectingToRelays(
                    uriData.relays.associateWith { RelayLoginStatus.CONNECTING },
                )
            nip46Client.addConnectionListener(listener)

            onUriGenerated(uriData.uri)

            _loginProgress.value =
                LoginProgress.WaitingForSigner(
                    relayStatuses = _loginProgress.value?.relayStatuses.orEmpty(),
                )

            val result = NostrConnectLoginUseCase.awaitAndLogin(uriData, nip46Client)

            val relayParams = NIP46_RELAYS.joinToString("&") { "relay=$it" }
            val syntheticBunkerUri = "bunker://${result.signer.remotePubkey}?$relayParams"

            val state =
                AccountState.LoggedIn(
                    signer = result.signer,
                    pubKeyHex = result.pubKeyHex,
                    npub = result.pubKeyHex.hexToByteArray().toNpub(),
                    nsec = null,
                    isReadOnly = false,
                    signerType = SignerType.Remote(syntheticBunkerUri),
                )
            _accountState.value = state
            _signerConnectionState.value = SignerConnectionState.Connected

            saveBunkerAccount(
                bunkerUri = syntheticBunkerUri,
                ephemeralPrivKeyHex = ephemeralKeyPair.privKey!!.toHexKey(),
                npub = state.npub,
            )

            return Result.success(state)
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            return Result.failure(Exception("Timed out waiting for signer. Ensure the signer app scanned the QR code."))
        } catch (e: Exception) {
            return Result.failure(Exception("Connection failed: ${e.message}"))
        } finally {
            _loginProgress.value = null
            client?.removeConnectionListener(listener)
        }
    }

    private suspend fun saveBunkerAccount(
        bunkerUri: String,
        ephemeralPrivKeyHex: String,
        npub: String,
    ) {
        saveLastNpub(npub)
        secureStorage.savePrivateKey(bunkerEphemeralKeyAlias(npub), ephemeralPrivKeyHex)
        saveBunkerUri(bunkerUri)

        // Also save to multi-account storage
        val info = AccountInfo(npub = npub, signerType = SignerType.Remote(bunkerUri))
        addAccountToStorage(info)
        accountStorage.setCurrentAccount(npub)
    }

    fun hasBunkerAccount(): Boolean = getBunkerFile().exists()

    fun setConnectingRelays() {
        _accountState.value = AccountState.ConnectingRelays
    }

    // --- Save/generate (existing) ---

    suspend fun saveCurrentAccount(): Result<Unit> {
        val current = currentAccount() ?: return Result.failure(Exception("No account logged in"))

        // Bunker accounts: private key saved during loginWithBunker
        if (current.signerType is SignerType.Remote) {
            // Still ensure multi-account storage is updated
            val info = AccountInfo(npub = current.npub, signerType = current.signerType)
            addAccountToStorage(info)
            accountStorage.setCurrentAccount(current.npub)
            return Result.success(Unit)
        }

        // Save private key if available (skip for read-only)
        if (!current.isReadOnly && current.nsec != null) {
            try {
                val privKeyHex =
                    decodePrivateKeyAsHexOrNull(current.nsec)
                        ?: return Result.failure(Exception("Invalid nsec format"))
                secureStorage.savePrivateKey(current.npub, privKeyHex)
            } catch (e: SecureStorageException) {
                return Result.failure(e)
            }
        }

        saveLastNpub(current.npub)

        // Always save to multi-account storage (including read-only)
        val info = AccountInfo(npub = current.npub, signerType = current.signerType)
        addAccountToStorage(info)
        accountStorage.setCurrentAccount(current.npub)

        return Result.success(Unit)
    }

    fun generateNewAccount(): AccountState.LoggedIn {
        val keyPair = KeyPair()
        val signer = NostrSignerInternal(keyPair)

        val state =
            AccountState.LoggedIn(
                signer = signer,
                pubKeyHex = keyPair.pubKey.toHexKey(),
                npub = keyPair.pubKey.toNpub(),
                nsec = keyPair.privKey?.toNsec(),
                isReadOnly = false,
            )
        _accountState.value = state
        return state
    }

    fun loginWithKey(keyInput: String): Result<AccountState.LoggedIn> {
        val trimmedInput = keyInput.trim()

        val privKeyHex = decodePrivateKeyAsHexOrNull(trimmedInput)
        if (privKeyHex != null) {
            return try {
                val keyPair = KeyPair(privKey = privKeyHex.hexToByteArray())
                val signer = NostrSignerInternal(keyPair)

                val state =
                    AccountState.LoggedIn(
                        signer = signer,
                        pubKeyHex = keyPair.pubKey.toHexKey(),
                        npub = keyPair.pubKey.toNpub(),
                        nsec = keyPair.privKey?.toNsec(),
                        isReadOnly = false,
                    )
                _accountState.value = state
                Result.success(state)
            } catch (e: Exception) {
                Result.failure(IllegalArgumentException("Invalid private key format"))
            }
        }

        val pubKeyHex = decodePublicKeyAsHexOrNull(trimmedInput)
        if (pubKeyHex != null) {
            return try {
                val keyPair = KeyPair(pubKey = pubKeyHex.hexToByteArray())
                val signer = NostrSignerInternal(keyPair)

                val state =
                    AccountState.LoggedIn(
                        signer = signer,
                        pubKeyHex = keyPair.pubKey.toHexKey(),
                        npub = keyPair.pubKey.toNpub(),
                        nsec = null,
                        isReadOnly = true,
                        signerType = SignerType.ViewOnly,
                    )
                _accountState.value = state
                Result.success(state)
            } catch (e: Exception) {
                Result.failure(IllegalArgumentException("Invalid public key format"))
            }
        }

        return Result.failure(IllegalArgumentException("Invalid key format. Use nsec1, npub1, hex, or bunker:// URI."))
    }

    // --- Logout ---

    suspend fun logout(deleteKey: Boolean = false) {
        val current = currentAccount()
        if (current != null) {
            // Clean up remote signer if bunker account
            if (current.signerType is SignerType.Remote) {
                (current.signer as? NostrSignerRemote)?.closeSubscription()
                if (deleteKey) {
                    try {
                        secureStorage.deletePrivateKey(bunkerEphemeralKeyAlias(current.npub))
                    } catch (_: SecureStorageException) {
                    }
                    getBunkerFile().delete()
                }
            }
            if (deleteKey) {
                try {
                    secureStorage.deletePrivateKey(current.npub)
                    clearLastNpub()
                } catch (_: SecureStorageException) {
                }
            }
        }
        disconnectNip46Client()
        _signerConnectionState.value = SignerConnectionState.NotRemote
        _lastPingTimeSec.value = null
        _accountState.value = AccountState.LoggedOut
        // Cancel heartbeat LAST — may be called from within the heartbeat coroutine
        stopHeartbeat()
    }

    suspend fun forceLogoutWithReason(reason: String) {
        _forceLogoutReason.value = reason
        logout(deleteKey = false)
    }

    fun clearForceLogoutReason() {
        _forceLogoutReason.value = null
    }

    // --- Heartbeat ---

    fun startHeartbeat(scope: CoroutineScope) {
        heartbeatJob?.cancel()
        heartbeatJob =
            scope.launch {
                var consecutiveFailures = 0
                while (isActive) {
                    delay(HEARTBEAT_INTERVAL_MS)
                    val current = currentAccount() ?: continue
                    val remoteSigner = current.signer as? NostrSignerRemote ?: continue
                    try {
                        remoteSigner.ping()
                        consecutiveFailures = 0
                        _signerConnectionState.value = SignerConnectionState.Connected
                        _lastPingTimeSec.value = TimeUtils.now()
                    } catch (_: SignerExceptions.ManuallyUnauthorizedException) {
                        forceLogoutWithReason("Remote signer revoked access.")
                        return@launch
                    } catch (_: Exception) {
                        consecutiveFailures++
                        if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                            forceLogoutWithReason(
                                "Lost connection to remote signer after $MAX_CONSECUTIVE_FAILURES failed pings.",
                            )
                            return@launch
                        }
                        _signerConnectionState.value = SignerConnectionState.Disconnected
                    }
                }
            }
    }

    fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    // --- Multi-account management ---

    suspend fun refreshAccountList() {
        _allAccounts.value = accountStorage.loadAccounts().toImmutableList()
    }

    suspend fun addAccountToStorage(info: AccountInfo) {
        accountStorage.saveAccount(info)
        refreshAccountList()
    }

    /**
     * Ensures the currently logged-in account is persisted in multi-account storage.
     * Call before switching to a new account to avoid losing the current one.
     */
    private fun loadReadOnlyAccount(npub: String): Result<AccountState.LoggedIn> {
        val pubKeyHex =
            decodePublicKeyAsHexOrNull(npub)
                ?: return Result.failure(Exception("Invalid npub: $npub"))

        val keyPair = KeyPair(pubKey = pubKeyHex.hexToByteArray())
        val signer = NostrSignerInternal(keyPair)

        val state =
            AccountState.LoggedIn(
                signer = signer,
                pubKeyHex = pubKeyHex,
                npub = npub,
                nsec = null,
                isReadOnly = true,
                signerType = SignerType.ViewOnly,
            )
        _accountState.value = state
        return Result.success(state)
    }

    suspend fun ensureCurrentAccountInStorage(displayName: String? = null) {
        val current = currentAccount() ?: return
        // Merge with existing stored info to preserve display name if not provided
        val existing = accountStorage.loadAccounts().find { it.npub == current.npub }
        val info =
            AccountInfo(
                npub = current.npub,
                signerType = current.signerType,
                displayName = displayName ?: existing?.displayName,
            )
        accountStorage.saveAccount(info)
        accountStorage.setCurrentAccount(current.npub)
    }

    suspend fun updateDisplayName(
        npub: String,
        displayName: String,
    ) {
        val accounts = accountStorage.loadAccounts()
        val existing = accounts.find { it.npub == npub } ?: return
        accountStorage.saveAccount(existing.copy(displayName = displayName))
        refreshAccountList()
    }

    suspend fun removeAccountFromStorage(npub: String) {
        val current = currentAccount()
        accountStorage.deleteAccount(npub)

        // Clean up keys
        try {
            secureStorage.deletePrivateKey(npub)
        } catch (_: SecureStorageException) {
        }

        refreshAccountList()

        // If we removed the active account, load the next one or log out
        if (current?.npub == npub) {
            val next = accountStorage.currentAccount()
            if (next != null) {
                loadSavedAccount()
            } else {
                logout(deleteKey = false)
            }
        }
    }

    /**
     * Switch to a different account.
     * Critical: loads new account BEFORE cancelling old state to prevent
     * unrecoverable partial failure.
     */
    suspend fun switchAccount(targetNpub: String): Result<AccountState.LoggedIn> {
        val accounts = accountStorage.loadAccounts()
        val target =
            accounts.find { it.npub == targetNpub }
                ?: return Result.failure(Exception("Account not found: $targetNpub"))

        // Phase 1: load + validate new account BEFORE touching current state
        val sType = target.signerType
        val newState =
            when (sType) {
                is SignerType.Internal -> loadInternalAccount(target.npub)
                is SignerType.Remote -> loadBunkerAccount(sType.bunkerUri, target.npub)
                is SignerType.ViewOnly -> loadReadOnlyAccount(target.npub)
            }

        if (newState.isFailure) return newState

        // Phase 2: transition succeeded — clean up old account resources
        cleanupOldAccountResources()
        accountStorage.setCurrentAccount(targetNpub)

        // Start heartbeat if new account is a bunker
        val loggedIn = newState.getOrNull()
        if (loggedIn?.signerType is SignerType.Remote) {
            // Heartbeat needs an external scope — will be started by caller
            _signerConnectionState.value = SignerConnectionState.Connected
        } else {
            _signerConnectionState.value = SignerConnectionState.NotRemote
        }

        // Reload NWC for the new account
        loadNwcConnection()

        return newState
    }

    private suspend fun cleanupOldAccountResources() {
        // Close NIP-46 subscription on old remote signer
        val oldAccount = currentAccount()
        if (oldAccount?.signerType is SignerType.Remote) {
            (oldAccount.signer as? NostrSignerRemote)?.closeSubscription()
        }
        // Stop heartbeat for old account
        stopHeartbeat()
        // Disconnect old NIP-46 client
        disconnectNip46Client()
    }

    suspend fun refreshAccountListOnStartup() {
        refreshAccountList()
    }

    // --- Accessors ---

    fun isLoggedIn(): Boolean = _accountState.value is AccountState.LoggedIn

    fun currentAccount(): AccountState.LoggedIn? = _accountState.value as? AccountState.LoggedIn

    // --- NWC ---

    fun hasNwcSetup(): Boolean = _nwcConnection.value != null

    fun setNwcConnection(uri: String): Result<Nip47WalletConnect.Nip47URINorm> =
        try {
            val parsed = Nip47WalletConnect.parse(uri)
            _nwcConnection.value = parsed
            saveNwcUri(uri)
            Result.success(parsed)
        } catch (e: Exception) {
            Result.failure(e)
        }

    fun clearNwcConnection() {
        _nwcConnection.value = null
        getNwcFile().delete()
    }

    fun loadNwcConnection() {
        val uri = getNwcFile().takeIf { it.exists() }?.readText()?.trim()
        if (!uri.isNullOrEmpty()) {
            try {
                _nwcConnection.value = Nip47WalletConnect.parse(uri)
            } catch (_: Exception) {
                getNwcFile().delete()
            }
        }
    }

    // --- File storage helpers ---

    private fun ensureAmethystDir() {
        if (!amethystDir.exists()) {
            amethystDir.mkdirs()
        }
        try {
            Files.setPosixFilePermissions(
                amethystDir.toPath(),
                setOf(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE,
                ),
            )
        } catch (_: UnsupportedOperationException) {
            // Windows — file system ACLs handle this
        } catch (_: Exception) {
        }
    }

    private fun saveNwcUri(uri: String) {
        ensureAmethystDir()
        getNwcFile().writeText(uri)
    }

    private fun getNwcFile(): File = File(amethystDir, "nwc_connection.txt")

    private fun getLastNpub(): String? {
        val file = getPrefsFile()
        return if (file.exists()) file.readText().trim().takeIf { it.isNotEmpty() } else null
    }

    private fun saveLastNpub(npub: String) {
        ensureAmethystDir()
        getPrefsFile().writeText(npub)
    }

    private fun clearLastNpub() {
        getPrefsFile().delete()
    }

    private fun getPrefsFile(): File = File(amethystDir, "last_account.txt")

    private fun getBunkerUri(): String? {
        val file = getBunkerFile()
        return if (file.exists()) file.readText().trim().takeIf { it.isNotEmpty() } else null
    }

    private fun saveBunkerUri(uri: String) {
        ensureAmethystDir()
        getBunkerFile().writeText(uri)
    }

    private fun getBunkerFile(): File = File(amethystDir, "bunker_uri.txt")
}

internal fun parseBunkerRelays(uri: String): Set<NormalizedRelayUrl> {
    val idx = uri.indexOf('?')
    if (idx < 0) return emptySet()
    return uri
        .substring(idx + 1)
        .split("&")
        .filter { it.startsWith("relay=", ignoreCase = true) }
        .map { NormalizedRelayUrl(it.removePrefix("relay=")) }
        .toSet()
}

internal fun stripBunkerSecret(uri: String): String {
    val idx = uri.indexOf('?')
    if (idx < 0) return uri
    val base = uri.substring(0, idx)
    val params =
        uri
            .substring(idx + 1)
            .split("&")
            .filter { !it.startsWith("secret=", ignoreCase = true) }
    return if (params.isEmpty()) base else "$base?${params.joinToString("&")}"
}
