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

import com.vitorpamplona.amethyst.commons.domain.nip46.BunkerLoginUseCase
import com.vitorpamplona.amethyst.commons.keystorage.SecureKeyStorage
import com.vitorpamplona.amethyst.commons.keystorage.SecureStorageException
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nip19Bech32.decodePrivateKeyAsHexOrNull
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip19Bech32.toNsec
import com.vitorpamplona.quartz.nip46RemoteSigner.signer.NostrSignerRemote
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout

/**
 * Shared, platform-agnostic account lifecycle manager.
 *
 * Handles login (nsec, npub, bunker), key generation, logout, and
 * persistence of account metadata via [SecureKeyStorage] and
 * [AccountPreferencesStorage].
 *
 * Platform-specific relay connectivity (WebSocket creation) is injected
 * via the [nostrClient] parameter.
 */
class AccountManager(
    private val secureStorage: SecureKeyStorage,
    private val prefsStorage: AccountPreferencesStorage,
    private val nostrClient: INostrClient,
) {
    companion object {
        internal const val BUNKER_EPHEMERAL_KEY_ALIAS = "bunker_ephemeral"
        internal const val NIP46_RELAY_CONNECT_TIMEOUT_MS = 15_000L
    }

    private val _accountState = MutableStateFlow<AccountState>(AccountState.LoggedOut)
    val accountState: StateFlow<AccountState> = _accountState.asStateFlow()

    // --- Login with nsec / hex private key ---

    fun loginWithNsec(keyInput: String): Result<AccountState.LoggedIn> {
        val privKeyHex =
            decodePrivateKeyAsHexOrNull(keyInput.trim())
                ?: return Result.failure(IllegalArgumentException("Invalid private key format"))

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
            Result.failure(e)
        }
    }

    // --- Login with npub / hex public key (read-only) ---

    fun loginWithNpub(keyInput: String): Result<AccountState.LoggedIn> {
        val pubKeyHex =
            decodePublicKeyAsHexOrNull(keyInput.trim())
                ?: return Result.failure(IllegalArgumentException("Invalid public key format"))

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
            Result.failure(e)
        }
    }

    // --- Login with key (auto-detect nsec vs npub) ---

    fun loginWithKey(keyInput: String): Result<AccountState.LoggedIn> {
        val trimmed = keyInput.trim()
        val nsecResult = loginWithNsec(trimmed)
        if (nsecResult.isSuccess) return nsecResult
        val npubResult = loginWithNpub(trimmed)
        if (npubResult.isSuccess) return npubResult
        return Result.failure(
            IllegalArgumentException("Invalid key format. Use nsec1, npub1, or hex."),
        )
    }

    // --- Login with bunker URI (NIP-46 remote signer) ---

    suspend fun loginWithBunker(bunkerUri: String): Result<AccountState.LoggedIn> =
        try {
            val ephemeralKeyPair = KeyPair()
            val ephemeralSigner = NostrSignerInternal(ephemeralKeyPair)

            val result = BunkerLoginUseCase.execute(bunkerUri, ephemeralSigner, nostrClient)

            val state =
                AccountState.LoggedIn(
                    signer = result.signer,
                    pubKeyHex = result.pubKeyHex,
                    npub = result.pubKeyHex.hexToByteArray().toNpub(),
                    nsec = null,
                    isReadOnly = false,
                    signerType = SignerType.Remote(stripBunkerSecret(bunkerUri)),
                )
            _accountState.value = state

            // Persist ephemeral key and bunker URI
            secureStorage.savePrivateKey(
                BUNKER_EPHEMERAL_KEY_ALIAS,
                ephemeralKeyPair.privKey!!.toHexKey(),
            )
            prefsStorage.saveBunkerUri(stripBunkerSecret(bunkerUri))
            prefsStorage.saveLastNpub(state.npub)

            Result.success(state)
        } catch (e: SignerExceptions.TimedOutException) {
            Result.failure(Exception("Connection timed out. Ensure remote signer is online."))
        } catch (e: SignerExceptions.ManuallyUnauthorizedException) {
            Result.failure(Exception("Connection rejected by remote signer."))
        } catch (e: Exception) {
            Result.failure(Exception("Bunker login failed: ${e.message}"))
        }

    // --- Load saved account ---

    suspend fun loadSavedAccount(): Result<AccountState.LoggedIn> =
        try {
            val lastNpub = prefsStorage.getLastNpub()
            val bunkerUri = prefsStorage.getBunkerUri()

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
        npub: String?,
    ): Result<AccountState.LoggedIn> {
        val ephemeralPrivKeyHex =
            secureStorage.getPrivateKey(BUNKER_EPHEMERAL_KEY_ALIAS)
                ?: return Result.failure(Exception("Ephemeral key not found"))

        val ephemeralKeyPair = KeyPair(privKey = ephemeralPrivKeyHex.hexToByteArray())
        val ephemeralSigner = NostrSignerInternal(ephemeralKeyPair)

        val remoteSigner = NostrSignerRemote.fromBunkerUri(bunkerUri, ephemeralSigner, nostrClient)
        remoteSigner.openSubscription()

        val pubKeyHex =
            if (npub != null) {
                decodePublicKeyAsHexOrNull(npub)
                    ?: return Result.failure(Exception("Invalid saved npub"))
            } else {
                withTimeout(NIP46_RELAY_CONNECT_TIMEOUT_MS) {
                    nostrClient.connectedRelaysFlow().first { it.isNotEmpty() }
                }
                remoteSigner.getPublicKey()
            }

        val resolvedNpub = npub ?: pubKeyHex.hexToByteArray().toNpub()
        if (npub == null) prefsStorage.saveLastNpub(resolvedNpub)

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
        return Result.success(state)
    }

    // --- Generate new keypair ---

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

    // --- Save current account ---

    suspend fun saveCurrentAccount(): Result<Unit> {
        val current = currentAccount() ?: return Result.failure(Exception("No account logged in"))
        if (current.signerType is SignerType.Remote) return Result.success(Unit)
        if (current.isReadOnly || current.nsec == null) {
            return Result.failure(Exception("Cannot save read-only account"))
        }

        return try {
            val privKeyHex =
                decodePrivateKeyAsHexOrNull(current.nsec)
                    ?: return Result.failure(Exception("Invalid nsec format"))
            secureStorage.savePrivateKey(current.npub, privKeyHex)
            prefsStorage.saveLastNpub(current.npub)
            Result.success(Unit)
        } catch (e: SecureStorageException) {
            Result.failure(e)
        }
    }

    // --- Logout ---

    suspend fun logout(deleteKey: Boolean = false) {
        val current = currentAccount()
        if (current != null) {
            if (current.signerType is SignerType.Remote) {
                (current.signer as? NostrSignerRemote)?.closeSubscription()
                if (deleteKey) {
                    try {
                        secureStorage.deletePrivateKey(BUNKER_EPHEMERAL_KEY_ALIAS)
                    } catch (_: SecureStorageException) {
                    }
                    prefsStorage.clearBunkerUri()
                }
            }
            if (deleteKey) {
                try {
                    secureStorage.deletePrivateKey(current.npub)
                    prefsStorage.clearLastNpub()
                } catch (_: SecureStorageException) {
                }
            }
        }
        _accountState.value = AccountState.LoggedOut
    }

    // --- State transitions ---

    fun setConnectingRelays() {
        _accountState.value = AccountState.ConnectingRelays
    }

    // --- Accessors ---

    fun isLoggedIn(): Boolean = _accountState.value is AccountState.LoggedIn

    fun currentAccount(): AccountState.LoggedIn? = _accountState.value as? AccountState.LoggedIn
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
