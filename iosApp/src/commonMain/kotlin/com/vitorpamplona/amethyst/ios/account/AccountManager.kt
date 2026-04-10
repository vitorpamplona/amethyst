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
package com.vitorpamplona.amethyst.ios.account

import com.vitorpamplona.amethyst.commons.keystorage.SecureKeyStorage
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip06KeyDerivation.Nip06
import com.vitorpamplona.quartz.nip19Bech32.bech32.bechToBytes
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class AccountState {
    data object LoggedOut : AccountState()

    data class LoggedIn(
        val keyPair: KeyPair,
        val signer: NostrSignerInternal,
        val npub: String,
        val pubKeyHex: String,
        val isReadOnly: Boolean,
    ) : AccountState()
}

/**
 * Metadata for a saved account, used for display in the account switcher.
 */
data class SavedAccountInfo(
    val npub: String,
    val pubKeyHex: String,
    val isReadOnly: Boolean,
    val isActive: Boolean,
)

private const val LAST_NPUB_KEY = "__last_npub__"
private const val SAVED_ACCOUNTS_KEY = "__saved_accounts__"
private const val ACCOUNT_SEPARATOR = ","

class AccountManager {
    private val _accountState = MutableStateFlow<AccountState>(AccountState.LoggedOut)
    val accountState: StateFlow<AccountState> = _accountState.asStateFlow()

    private val _savedAccounts = MutableStateFlow<List<SavedAccountInfo>>(emptyList())
    val savedAccounts: StateFlow<List<SavedAccountInfo>> = _savedAccounts.asStateFlow()

    private val keyStorage = SecureKeyStorage.create(null)
    private val scope =
        CoroutineScope(
            Dispatchers.IO + kotlinx.coroutines.SupervisorJob() +
                kotlinx.coroutines.CoroutineExceptionHandler { _, t ->
                    platform.Foundation.NSLog("AccountManager coroutine error: " + (t.message ?: "unknown"))
                },
        )

    /**
     * Try to restore the last logged-in account from Keychain.
     */
    fun tryRestoreSession() {
        scope.launch {
            @Suppress("TooGenericExceptionCaught")
            try {
                val lastNpub = keyStorage.getPrivateKey(LAST_NPUB_KEY) ?: return@launch
                val privKeyHex = keyStorage.getPrivateKey(lastNpub)

                if (privKeyHex != null) {
                    val keyPair = KeyPair(privKey = privKeyHex.hexToByteArray())
                    val signer = NostrSignerInternal(keyPair)

                    _accountState.value =
                        AccountState.LoggedIn(
                            keyPair = keyPair,
                            signer = signer,
                            npub = keyPair.pubKey.toNpub(),
                            pubKeyHex = keyPair.pubKey.toHexKey(),
                            isReadOnly = false,
                        )
                } else {
                    // Might be a read-only account — check if npub is in saved list
                    val pubKeyBytes = lastNpub.bechToBytes()
                    val keyPair = KeyPair(pubKey = pubKeyBytes)
                    val signer = NostrSignerInternal(keyPair)

                    _accountState.value =
                        AccountState.LoggedIn(
                            keyPair = keyPair,
                            signer = signer,
                            npub = lastNpub,
                            pubKeyHex = keyPair.pubKey.toHexKey(),
                            isReadOnly = true,
                        )
                }

                refreshSavedAccounts()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                // Failed to restore — stay logged out
                println("AccountManager: Failed to restore session: ${e.message}")
                refreshSavedAccounts()
            }
        }
    }

    /**
     * Create a new account with a fresh keypair.
     */
    fun createAccount(): AccountState.LoggedIn {
        val keyPair = KeyPair()
        val signer = NostrSignerInternal(keyPair)
        val npub = keyPair.pubKey.toNpub()
        val pubKeyHex = keyPair.pubKey.toHexKey()

        // Persist to Keychain
        if (keyPair.privKey != null) {
            scope.launch {
                @Suppress("TooGenericExceptionCaught")
                try {
                    keyStorage.savePrivateKey(npub, keyPair.privKey!!.toHexKey())
                    keyStorage.savePrivateKey(LAST_NPUB_KEY, npub)
                    addToSavedAccountsList(npub)
                    refreshSavedAccounts()
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    println("AccountManager: Failed to save to Keychain: ${e.message}")
                }
            }
        }

        val loggedIn =
            AccountState.LoggedIn(
                keyPair = keyPair,
                signer = signer,
                npub = npub,
                pubKeyHex = pubKeyHex,
                isReadOnly = false,
            )
        _accountState.value = loggedIn
        return loggedIn
    }

    fun login(key: String): Result<Unit> =
        runCatching {
            val trimmed = key.trim()
            val keyPair =
                when {
                    trimmed.startsWith("nsec1") -> {
                        KeyPair(privKey = trimmed.bechToBytes())
                    }

                    trimmed.startsWith("npub1") -> {
                        KeyPair(pubKey = trimmed.bechToBytes())
                    }

                    trimmed.contains(" ") -> {
                        KeyPair(privKey = Nip06().privateKeyFromMnemonic(trimmed))
                    }

                    else -> {
                        KeyPair(privKey = trimmed.hexToByteArray())
                    }
                }

            val signer = NostrSignerInternal(keyPair)
            val isReadOnly = keyPair.privKey == null
            val npub = keyPair.pubKey.toNpub()
            val pubKeyHex = keyPair.pubKey.toHexKey()

            // Persist to Keychain (only if we have a private key)
            if (!isReadOnly && keyPair.privKey != null) {
                scope.launch {
                    @Suppress("TooGenericExceptionCaught")
                    try {
                        keyStorage.savePrivateKey(npub, keyPair.privKey!!.toHexKey())
                        keyStorage.savePrivateKey(LAST_NPUB_KEY, npub)
                        addToSavedAccountsList(npub)
                        refreshSavedAccounts()
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        println("AccountManager: Failed to save to Keychain: ${e.message}")
                    }
                }
            } else if (isReadOnly) {
                // Save read-only accounts to the list too (no privkey stored)
                scope.launch {
                    @Suppress("TooGenericExceptionCaught")
                    try {
                        keyStorage.savePrivateKey(LAST_NPUB_KEY, npub)
                        addToSavedAccountsList(npub)
                        refreshSavedAccounts()
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        println("AccountManager: Failed to save to Keychain: ${e.message}")
                    }
                }
            }

            _accountState.value =
                AccountState.LoggedIn(
                    keyPair = keyPair,
                    signer = signer,
                    npub = npub,
                    pubKeyHex = pubKeyHex,
                    isReadOnly = isReadOnly,
                )
        }

    /**
     * Switch to an existing saved account by npub.
     */
    fun switchToAccount(npub: String): Result<Unit> =
        runCatching {
            scope.launch {
                @Suppress("TooGenericExceptionCaught")
                try {
                    val privKeyHex = keyStorage.getPrivateKey(npub)
                    val isReadOnly = privKeyHex == null

                    val keyPair =
                        if (!isReadOnly) {
                            KeyPair(privKey = privKeyHex!!.hexToByteArray())
                        } else {
                            KeyPair(pubKey = npub.bechToBytes())
                        }

                    val signer = NostrSignerInternal(keyPair)
                    val pubKeyHex = keyPair.pubKey.toHexKey()

                    keyStorage.savePrivateKey(LAST_NPUB_KEY, npub)

                    _accountState.value =
                        AccountState.LoggedIn(
                            keyPair = keyPair,
                            signer = signer,
                            npub = npub,
                            pubKeyHex = pubKeyHex,
                            isReadOnly = isReadOnly,
                        )

                    refreshSavedAccounts()
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    println("AccountManager: Failed to switch account: ${e.message}")
                }
            }
        }

    /**
     * Remove a saved account from Keychain. If it's the active account, log out.
     */
    fun removeAccount(npub: String) {
        val currentState = _accountState.value
        val isActive = currentState is AccountState.LoggedIn && currentState.npub == npub

        scope.launch {
            @Suppress("TooGenericExceptionCaught")
            try {
                // Delete private key
                keyStorage.deletePrivateKey(npub)

                // Remove from saved accounts list
                removeFromSavedAccountsList(npub)

                if (isActive) {
                    // Try to switch to another saved account
                    val remaining = loadSavedAccountNpubs()
                    if (remaining.isNotEmpty()) {
                        val nextNpub = remaining.first()
                        keyStorage.savePrivateKey(LAST_NPUB_KEY, nextNpub)

                        val privKeyHex = keyStorage.getPrivateKey(nextNpub)
                        val nextIsReadOnly = privKeyHex == null
                        val nextKeyPair =
                            if (!nextIsReadOnly) {
                                KeyPair(privKey = privKeyHex!!.hexToByteArray())
                            } else {
                                KeyPair(pubKey = nextNpub.bechToBytes())
                            }
                        val nextSigner = NostrSignerInternal(nextKeyPair)

                        _accountState.value =
                            AccountState.LoggedIn(
                                keyPair = nextKeyPair,
                                signer = nextSigner,
                                npub = nextNpub,
                                pubKeyHex = nextKeyPair.pubKey.toHexKey(),
                                isReadOnly = nextIsReadOnly,
                            )
                    } else {
                        keyStorage.deletePrivateKey(LAST_NPUB_KEY)
                        _accountState.value = AccountState.LoggedOut
                    }
                }

                refreshSavedAccounts()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                println("AccountManager: Failed to remove account: ${e.message}")
            }
        }
    }

    fun logout() {
        val currentState = _accountState.value
        if (currentState is AccountState.LoggedIn) {
            scope.launch {
                @Suppress("TooGenericExceptionCaught")
                try {
                    keyStorage.deletePrivateKey(currentState.npub)
                    removeFromSavedAccountsList(currentState.npub)

                    // Try to switch to another saved account
                    val remaining = loadSavedAccountNpubs()
                    if (remaining.isNotEmpty()) {
                        val nextNpub = remaining.first()
                        keyStorage.savePrivateKey(LAST_NPUB_KEY, nextNpub)

                        val privKeyHex = keyStorage.getPrivateKey(nextNpub)
                        val nextIsReadOnly = privKeyHex == null
                        val nextKeyPair =
                            if (!nextIsReadOnly) {
                                KeyPair(privKey = privKeyHex!!.hexToByteArray())
                            } else {
                                KeyPair(pubKey = nextNpub.bechToBytes())
                            }
                        val nextSigner = NostrSignerInternal(nextKeyPair)

                        _accountState.value =
                            AccountState.LoggedIn(
                                keyPair = nextKeyPair,
                                signer = nextSigner,
                                npub = nextNpub,
                                pubKeyHex = nextKeyPair.pubKey.toHexKey(),
                                isReadOnly = nextIsReadOnly,
                            )
                    } else {
                        keyStorage.deletePrivateKey(LAST_NPUB_KEY)
                        _accountState.value = AccountState.LoggedOut
                    }

                    refreshSavedAccounts()
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    println("AccountManager: Failed to clear Keychain: ${e.message}")
                }
            }
        }
    }

    // ── Internal helpers for multi-account list ──

    private suspend fun loadSavedAccountNpubs(): List<String> {
        val raw = keyStorage.getPrivateKey(SAVED_ACCOUNTS_KEY) ?: return emptyList()
        return raw.split(ACCOUNT_SEPARATOR).filter { it.isNotBlank() }
    }

    private suspend fun saveSavedAccountNpubs(npubs: List<String>) {
        if (npubs.isEmpty()) {
            keyStorage.deletePrivateKey(SAVED_ACCOUNTS_KEY)
        } else {
            keyStorage.savePrivateKey(SAVED_ACCOUNTS_KEY, npubs.joinToString(ACCOUNT_SEPARATOR))
        }
    }

    private suspend fun addToSavedAccountsList(npub: String) {
        val current = loadSavedAccountNpubs().toMutableList()
        if (npub !in current) {
            current.add(npub)
            saveSavedAccountNpubs(current)
        }
    }

    private suspend fun removeFromSavedAccountsList(npub: String) {
        val current = loadSavedAccountNpubs().toMutableList()
        if (current.remove(npub)) {
            saveSavedAccountNpubs(current)
        }
    }

    private suspend fun refreshSavedAccounts() {
        val npubs = loadSavedAccountNpubs()
        val activeNpub = (accountState.value as? AccountState.LoggedIn)?.npub

        val accounts =
            npubs.map { npub ->
                val hasPrivKey = keyStorage.hasPrivateKey(npub)
                val pubKeyHex =
                    try {
                        val bytes = npub.bechToBytes()
                        KeyPair(pubKey = bytes).pubKey.toHexKey()
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        npub
                    }

                SavedAccountInfo(
                    npub = npub,
                    pubKeyHex = pubKeyHex,
                    isReadOnly = !hasPrivKey,
                    isActive = npub == activeNpub,
                )
            }

        _savedAccounts.value = accounts
    }
}
