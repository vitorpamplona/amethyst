/**
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
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip19Bech32.decodePrivateKeyAsHexOrNull
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip19Bech32.toNsec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

sealed class AccountState {
    data object LoggedOut : AccountState()

    data class LoggedIn(
        val signer: NostrSigner,
        val pubKeyHex: String,
        val npub: String,
        val nsec: String?,
        val isReadOnly: Boolean,
    ) : AccountState()
}

@Stable
class AccountManager private constructor(
    private val secureStorage: SecureKeyStorage,
) {
    companion object {
        /**
         * Creates an AccountManager instance.
         *
         * @param context Platform-specific context (required on Android, ignored on Desktop)
         * @return AccountManager instance
         */
        fun create(context: Any? = null): AccountManager {
            val storage = SecureKeyStorage.create(context)
            return AccountManager(storage)
        }
    }

    private val _accountState = MutableStateFlow<AccountState>(AccountState.LoggedOut)
    val accountState: StateFlow<AccountState> = _accountState.asStateFlow()

    /**
     * Loads the last saved account from secure storage.
     * Call on app startup.
     */
    suspend fun loadSavedAccount(): Result<AccountState.LoggedIn> {
        return try {
            // For simplicity, we'll store the last logged-in npub in a simple file
            // and use SecureKeyStorage to retrieve the private key
            val lastNpub = getLastNpub() ?: return Result.failure(Exception("No saved account"))

            val privKeyHex =
                secureStorage.getPrivateKey(lastNpub)
                    ?: return Result.failure(Exception("Private key not found for $lastNpub"))

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

    /**
     * Saves the current account to secure storage.
     */
    suspend fun saveCurrentAccount(): Result<Unit> {
        val current = currentAccount() ?: return Result.failure(Exception("No account logged in"))
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

        // Try as private key first (nsec or hex)
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

        // Try as public key (npub or hex) - read-only mode
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

        return Result.failure(IllegalArgumentException("Invalid key format. Use nsec1, npub1, or hex format."))
    }

    suspend fun logout(deleteKey: Boolean = false) {
        val current = currentAccount()
        if (deleteKey && current != null) {
            try {
                secureStorage.deletePrivateKey(current.npub)
                clearLastNpub()
            } catch (e: SecureStorageException) {
                // Log error but still logout
            }
        }
        _accountState.value = AccountState.LoggedOut
    }

    fun isLoggedIn(): Boolean = _accountState.value is AccountState.LoggedIn

    fun currentAccount(): AccountState.LoggedIn? = _accountState.value as? AccountState.LoggedIn

    // Simple file-based storage for last npub (non-sensitive data)
    private fun getLastNpub(): String? {
        val file = getPrefsFile()
        return if (file.exists()) file.readText().trim().takeIf { it.isNotEmpty() } else null
    }

    private fun saveLastNpub(npub: String) {
        val file = getPrefsFile()
        file.parentFile?.mkdirs()
        file.writeText(npub)
    }

    private fun clearLastNpub() {
        getPrefsFile().delete()
    }

    private fun getPrefsFile(): File {
        val homeDir = System.getProperty("user.home")
        return File(homeDir, ".amethyst/last_account.txt")
    }
}
