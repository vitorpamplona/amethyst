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
import com.vitorpamplona.amethyst.commons.keystorage.SecureKeyStorage
import com.vitorpamplona.amethyst.commons.keystorage.SecureStorageException
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.req
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nip19Bech32.decodePrivateKeyAsHexOrNull
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip19Bech32.toNsec
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestConnect
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponseAck
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectEvent
import com.vitorpamplona.quartz.nip46RemoteSigner.signer.NostrSignerRemote
import com.vitorpamplona.quartz.nip47WalletConnect.Nip47WalletConnect
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.io.File
import java.security.SecureRandom

sealed class SignerType {
    data object Internal : SignerType()

    data class Remote(
        val bunkerUri: String,
    ) : SignerType()
}

sealed class SignerConnectionState {
    data object NotRemote : SignerConnectionState()

    data object Connected : SignerConnectionState()

    data class Unstable(
        val failCount: Int,
    ) : SignerConnectionState()
}

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
        internal const val BUNKER_EPHEMERAL_KEY_ALIAS = "bunker_ephemeral"
        internal const val NOSTRCONNECT_TIMEOUT_MS = 120_000L
        internal val NIP46_RELAYS = listOf("wss://relay.nsec.app")
    }

    private val amethystDir: File by lazy {
        File(homeDir, ".amethyst")
    }

    private val _accountState = MutableStateFlow<AccountState>(AccountState.LoggedOut)
    val accountState: StateFlow<AccountState> = _accountState.asStateFlow()

    private val _nwcConnection = MutableStateFlow<Nip47WalletConnect.Nip47URINorm?>(null)
    val nwcConnection: StateFlow<Nip47WalletConnect.Nip47URINorm?> = _nwcConnection.asStateFlow()

    private val _signerConnectionState = MutableStateFlow<SignerConnectionState>(SignerConnectionState.NotRemote)
    val signerConnectionState: StateFlow<SignerConnectionState> = _signerConnectionState.asStateFlow()

    private val _forceLogoutReason = MutableStateFlow<String?>(null)
    val forceLogoutReason: StateFlow<String?> = _forceLogoutReason.asStateFlow()

    private var heartbeatJob: Job? = null

    // --- Account loading ---

    suspend fun loadSavedAccount(client: INostrClient? = null): Result<AccountState.LoggedIn> =
        try {
            val lastNpub = getLastNpub() ?: return Result.failure(Exception("No saved account"))

            // Check for bunker account first
            val bunkerUri = getBunkerUri()
            if (bunkerUri != null && client != null) {
                loadBunkerAccount(bunkerUri, lastNpub, client)
            } else {
                loadInternalAccount(lastNpub)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }

    private suspend fun loadInternalAccount(npub: String): Result<AccountState.LoggedIn> {
        val privKeyHex =
            secureStorage.getPrivateKey(npub)
                ?: return Result.failure(Exception("Private key not found for $npub"))

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

    private suspend fun loadBunkerAccount(
        bunkerUri: String,
        npub: String,
        client: INostrClient,
    ): Result<AccountState.LoggedIn> {
        val ephemeralPrivKeyHex =
            secureStorage.getPrivateKey(BUNKER_EPHEMERAL_KEY_ALIAS)
                ?: return Result.failure(Exception("Ephemeral key not found"))

        val ephemeralKeyPair = KeyPair(privKey = ephemeralPrivKeyHex.hexToByteArray())
        val ephemeralSigner = NostrSignerInternal(ephemeralKeyPair)

        val remoteSigner = NostrSignerRemote.fromBunkerUri(bunkerUri, ephemeralSigner, client)
        remoteSigner.openSubscription()

        val pubKeyHex = decodePublicKeyAsHexOrNull(npub) ?: return Result.failure(Exception("Invalid saved npub"))

        val state =
            AccountState.LoggedIn(
                signer = remoteSigner,
                pubKeyHex = pubKeyHex,
                npub = npub,
                nsec = null,
                isReadOnly = false,
                signerType = SignerType.Remote(bunkerUri),
            )
        _accountState.value = state
        _signerConnectionState.value = SignerConnectionState.Connected
        return Result.success(state)
    }

    // --- Bunker login ---

    suspend fun loginWithBunker(
        bunkerUri: String,
        client: INostrClient,
    ): Result<AccountState.LoggedIn> =
        try {
            val ephemeralKeyPair = KeyPair()
            val ephemeralSigner = NostrSignerInternal(ephemeralKeyPair)

            val remoteSigner = NostrSignerRemote.fromBunkerUri(bunkerUri, ephemeralSigner, client)
            remoteSigner.openSubscription()

            val remotePubkey = remoteSigner.connect()

            val state =
                AccountState.LoggedIn(
                    signer = remoteSigner,
                    pubKeyHex = remotePubkey,
                    npub = remotePubkey.hexToByteArray().toNpub(),
                    nsec = null,
                    isReadOnly = false,
                    signerType = SignerType.Remote(bunkerUri),
                )
            _accountState.value = state
            _signerConnectionState.value = SignerConnectionState.Connected

            // Save bunker account — strip secret param (no longer needed after connect)
            saveBunkerAccount(
                bunkerUri = stripBunkerSecret(bunkerUri),
                ephemeralPrivKeyHex = ephemeralKeyPair.privKey!!.toHexKey(),
                npub = state.npub,
            )

            Result.success(state)
        } catch (e: SignerExceptions.TimedOutException) {
            Result.failure(Exception("Connection timed out. Ensure remote signer is online and has approved the connection."))
        } catch (e: SignerExceptions.ManuallyUnauthorizedException) {
            Result.failure(Exception("Connection rejected by remote signer."))
        } catch (e: SignerExceptions.CouldNotPerformException) {
            Result.failure(Exception("Remote signer error: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(Exception("Connection failed: ${e.message}"))
        }

    // --- Nostrconnect login ---

    suspend fun loginWithNostrConnect(
        client: INostrClient,
        onUriGenerated: (String) -> Unit,
    ): Result<AccountState.LoggedIn> =
        try {
            val ephemeralKeyPair = KeyPair()
            val ephemeralSigner = NostrSignerInternal(ephemeralKeyPair)
            val secret = generateNostrConnectSecret()
            val ephemeralPubKey = ephemeralKeyPair.pubKey.toHexKey()

            val relays = NIP46_RELAYS
            val relayParams = relays.joinToString("&") { "relay=$it" }
            val uri = "nostrconnect://$ephemeralPubKey?$relayParams&secret=$secret&name=Amethyst%20Desktop"
            onUriGenerated(uri)

            val normalizedRelays = relays.map { NormalizedRelayUrl(it) }.toSet()
            val connectData = waitForConnectRequest(ephemeralSigner, ephemeralPubKey, normalizedRelays, secret, client)

            sendAckResponse(ephemeralSigner, connectData, normalizedRelays, client)

            val remoteSigner =
                NostrSignerRemote(
                    signer = ephemeralSigner,
                    remotePubkey = connectData.signerPubkey,
                    relays = normalizedRelays,
                    client = client,
                )
            remoteSigner.openSubscription()

            val syntheticBunkerUri = "bunker://${connectData.signerPubkey}?$relayParams"

            val state =
                AccountState.LoggedIn(
                    signer = remoteSigner,
                    pubKeyHex = connectData.userPubkey,
                    npub = connectData.userPubkey.hexToByteArray().toNpub(),
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

            Result.success(state)
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Result.failure(Exception("Timed out waiting for signer. Ensure the signer app scanned the QR code."))
        } catch (e: Exception) {
            Result.failure(Exception("Connection failed: ${e.message}"))
        }

    private suspend fun waitForConnectRequest(
        ephemeralSigner: NostrSignerInternal,
        ephemeralPubKey: HexKey,
        relays: Set<NormalizedRelayUrl>,
        expectedSecret: String,
        client: INostrClient,
    ): ConnectRequestData {
        val deferred = CompletableDeferred<NostrConnectEvent>()

        val subscription =
            client.req(
                relays = relays.toList(),
                filter =
                    Filter(
                        kinds = listOf(NostrConnectEvent.KIND),
                        tags = mapOf("p" to listOf(ephemeralPubKey)),
                    ),
            ) { event ->
                if (event is NostrConnectEvent && !deferred.isCompleted) {
                    deferred.complete(event)
                }
            }

        try {
            val event =
                withTimeout(NOSTRCONNECT_TIMEOUT_MS) {
                    deferred.await()
                }

            val otherPubkey = event.talkingWith(ephemeralSigner.pubKey)
            val decryptedJson = ephemeralSigner.decrypt(event.content, otherPubkey)
            val message = OptimizedJsonMapper.fromJsonTo<BunkerRequest>(decryptedJson)

            if (message.method != BunkerRequestConnect.METHOD_NAME) {
                throw Exception("Expected 'connect' method, got '${message.method}'")
            }

            val userPubkey =
                message.params.getOrNull(0)
                    ?: throw Exception("Missing user pubkey in connect request")
            val receivedSecret = message.params.getOrNull(1)

            if (receivedSecret != expectedSecret) {
                throw Exception("Secret mismatch in connect request")
            }

            return ConnectRequestData(
                requestId = message.id,
                signerPubkey = event.pubKey,
                userPubkey = userPubkey,
            )
        } finally {
            subscription.close()
        }
    }

    private suspend fun sendAckResponse(
        ephemeralSigner: NostrSignerInternal,
        connectData: ConnectRequestData,
        relays: Set<NormalizedRelayUrl>,
        client: INostrClient,
    ) {
        val ackResponse = BunkerResponseAck(id = connectData.requestId)
        val ackEvent =
            NostrConnectEvent.create(
                message = ackResponse,
                remoteKey = connectData.signerPubkey,
                signer = ephemeralSigner,
            )
        client.send(ackEvent, relays)
    }

    private fun generateNostrConnectSecret(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private data class ConnectRequestData(
        val requestId: String,
        val signerPubkey: HexKey,
        val userPubkey: HexKey,
    )

    private suspend fun saveBunkerAccount(
        bunkerUri: String,
        ephemeralPrivKeyHex: String,
        npub: String,
    ) {
        saveBunkerUri(bunkerUri)
        secureStorage.savePrivateKey(BUNKER_EPHEMERAL_KEY_ALIAS, ephemeralPrivKeyHex)
        saveLastNpub(npub)
    }

    fun hasBunkerAccount(): Boolean = getBunkerFile().exists()

    fun setConnectingRelays() {
        _accountState.value = AccountState.ConnectingRelays
    }

    // --- Save/generate (existing) ---

    suspend fun saveCurrentAccount(): Result<Unit> {
        val current = currentAccount() ?: return Result.failure(Exception("No account logged in"))

        // Bunker accounts are saved during loginWithBunker
        if (current.signerType is SignerType.Remote) return Result.success(Unit)

        if (current.isReadOnly || current.nsec == null) {
            return Result.failure(Exception("Cannot save read-only account"))
        }

        return try {
            val privKeyHex =
                decodePrivateKeyAsHexOrNull(current.nsec)
                    ?: return Result.failure(Exception("Invalid nsec format"))

            secureStorage.savePrivateKey(current.npub, privKeyHex)
            saveLastNpub(current.npub)
            Result.success(Unit)
        } catch (e: SecureStorageException) {
            Result.failure(e)
        }
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
                        secureStorage.deletePrivateKey(BUNKER_EPHEMERAL_KEY_ALIAS)
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
        _signerConnectionState.value = SignerConnectionState.NotRemote
        _accountState.value = AccountState.LoggedOut
        // Cancel heartbeat LAST — may be called from within the heartbeat coroutine
        stopHeartbeat()
    }

    suspend fun forceLogoutWithReason(reason: String) {
        _forceLogoutReason.value = reason
        logout(deleteKey = true)
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
                        _signerConnectionState.value = SignerConnectionState.Unstable(consecutiveFailures)
                    }
                }
            }
    }

    fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
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

    private fun saveNwcUri(uri: String) {
        amethystDir.mkdirs()
        getNwcFile().writeText(uri)
    }

    private fun getNwcFile(): File = File(amethystDir, "nwc_connection.txt")

    private fun getLastNpub(): String? {
        val file = getPrefsFile()
        return if (file.exists()) file.readText().trim().takeIf { it.isNotEmpty() } else null
    }

    private fun saveLastNpub(npub: String) {
        amethystDir.mkdirs()
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
        amethystDir.mkdirs()
        getBunkerFile().writeText(uri)
    }

    private fun getBunkerFile(): File = File(amethystDir, "bunker_uri.txt")
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
