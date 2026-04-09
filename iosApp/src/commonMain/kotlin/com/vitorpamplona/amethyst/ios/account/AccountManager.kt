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

private const val LAST_NPUB_KEY = "__last_npub__"

class AccountManager {
    private val _accountState = MutableStateFlow<AccountState>(AccountState.LoggedOut)
    val accountState: StateFlow<AccountState> = _accountState.asStateFlow()

    private val keyStorage = SecureKeyStorage.create(null)
    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Try to restore the last logged-in account from Keychain.
     */
    fun tryRestoreSession() {
        scope.launch {
            try {
                val lastNpub = keyStorage.getPrivateKey(LAST_NPUB_KEY) ?: return@launch
                val privKeyHex = keyStorage.getPrivateKey(lastNpub) ?: return@launch

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
            } catch (e: Exception) {
                // Failed to restore — stay logged out
                println("AccountManager: Failed to restore session: ${e.message}")
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
                try {
                    keyStorage.savePrivateKey(npub, keyPair.privKey!!.toHexKey())
                    keyStorage.savePrivateKey(LAST_NPUB_KEY, npub)
                } catch (e: Exception) {
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
                    try {
                        keyStorage.savePrivateKey(npub, keyPair.privKey!!.toHexKey())
                        keyStorage.savePrivateKey(LAST_NPUB_KEY, npub)
                    } catch (e: Exception) {
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

    fun logout() {
        val currentState = _accountState.value
        if (currentState is AccountState.LoggedIn) {
            scope.launch {
                try {
                    keyStorage.deletePrivateKey(currentState.npub)
                    keyStorage.deletePrivateKey(LAST_NPUB_KEY)
                } catch (e: Exception) {
                    println("AccountManager: Failed to clear Keychain: ${e.message}")
                }
            }
        }
        _accountState.value = AccountState.LoggedOut
    }
}
