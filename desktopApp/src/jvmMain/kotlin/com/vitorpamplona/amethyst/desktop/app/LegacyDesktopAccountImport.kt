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
package com.vitorpamplona.amethyst.desktop.app

import com.vitorpamplona.amethyst.commons.account.StoreAccountSessionStore
import com.vitorpamplona.amethyst.commons.keystorage.SecureKeyStorage
import com.vitorpamplona.amethyst.commons.model.AccountSettings
import com.vitorpamplona.amethyst.commons.model.account.SignerType
import com.vitorpamplona.amethyst.desktop.account.DesktopAccountStorage
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip19Bech32.bech32.bechToBytes
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import java.io.File

/**
 * Brings the legacy desktop app's saved logins (`~/.amethyst/accounts.json.enc`, keys in the OS
 * keyring) into the shared stores, once, on the first start that finds no shared logins.
 *
 * The keyring entries are reused as they are: both apps key a private key by its npub, and a
 * bunker login's transport key by `bunker_ephemeral_<npub>`. Nothing in the legacy files is
 * changed, so the legacy app keeps working until it is retired.
 */
class LegacyDesktopAccountImport(
    private val sessionStore: StoreAccountSessionStore,
    private val keyStorage: SecureKeyStorage,
    private val filesDir: File,
    private val homeDir: File = File(System.getProperty("user.home")),
) {
    private val marker get() = File(filesDir, MARKER_FILE)

    // Present while an import runs: one cut short (a keyring error on the second of three logins)
    // leaves the store no longer empty, which alone would skip the rest for good.
    private val partialMarker get() = File(filesDir, "$MARKER_FILE.partial")

    /**
     * Runs on every start, before the first login: opens the keyring's consolidated vault (the one
     * item the legacy app moved every key into, so macOS asks once), imports the legacy logins the
     * first time, and extends the vault to every saved login so their keys are read from it.
     */
    suspend fun start() {
        enableVault(listOf(DesktopAccountStorage.METADATA_KEY_ALIAS))
        importIfNeeded()
        enableVault(sessionStore.allSavedAccounts().flatMap { keyAliases(it.npub) } + LEGACY_SHARED_TRANSPORT_KEY_ALIAS)
    }

    private suspend fun enableVault(aliases: List<String>) {
        try {
            keyStorage.enableConsolidatedVault(aliases)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // Non-fatal: reads fall back to one keyring item per alias.
            Log.w(TAG, "Could not open the consolidated keyring vault", e)
        }
    }

    private suspend fun importIfNeeded() {
        if (marker.exists()) return
        try {
            if (partialMarker.exists() || sessionStore.allSavedAccounts().isEmpty()) {
                partialMarker.createNewFile()
                import()
                partialMarker.delete()
            }
            marker.createNewFile()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // Left unmarked, so the next start tries again.
            Log.w(TAG, "Could not import the legacy desktop accounts", e)
        }
    }

    private suspend fun import() {
        val legacy = DesktopAccountStorage(keyStorage, homeDir)
        // A resumed import skips the logins that already made it.
        val alreadyImported = sessionStore.allSavedAccounts().mapTo(HashSet()) { it.npub }
        val accounts = legacy.loadAccounts().filter { !it.isTransient && it.npub !in alreadyImported }
        if (accounts.isEmpty()) return

        // The keys are in the vault only once it covers their aliases.
        enableVault(accounts.flatMap { keyAliases(it.npub) } + LEGACY_SHARED_TRANSPORT_KEY_ALIAS)

        accounts.forEach { info ->
            val pubKey = info.npub.bechToBytes()
            val settings =
                when (val signer = info.signerType) {
                    is SignerType.Internal -> {
                        val privKey = keyStorage.getPrivateKey(info.npub)?.takeIf { it.isNotBlank() }
                        if (privKey != null) {
                            AccountSettings(keyPair = KeyPair(privKey = privKey.hexToByteArray()))
                        } else {
                            AccountSettings(keyPair = KeyPair(pubKey = pubKey))
                        }
                    }

                    is SignerType.Remote -> {
                        // Per account first; the oldest builds kept one shared transport key.
                        val transportKey =
                            keyStorage.getPrivateKey(bunkerTransportKeyAlias(info.npub))?.takeIf { it.isNotBlank() }
                                ?: keyStorage.getPrivateKey(LEGACY_SHARED_TRANSPORT_KEY_ALIAS)?.takeIf { it.isNotBlank() }
                        AccountSettings(
                            keyPair = KeyPair(pubKey = pubKey),
                            remoteSignerBunkerUri = signer.bunkerUri.takeIf { transportKey != null },
                            remoteSignerTransportKey = transportKey,
                        )
                    }

                    is SignerType.ViewOnly -> {
                        AccountSettings(keyPair = KeyPair(pubKey = pubKey))
                    }
                }
            sessionStore.setDefaultAccount(settings)
            Log.d(TAG) { "Imported ${info.npub.take(12)}… (${info.signerType::class.simpleName})" }
        }

        // setDefaultAccount made the last one current; the legacy app's choice wins.
        val active = legacy.currentAccount()
        sessionStore.allSavedAccounts().firstOrNull { it.npub == active }?.let { sessionStore.switchToAccount(it) }
    }

    companion object {
        private const val TAG = "LegacyDesktopAccountImport"
        private const val MARKER_FILE = "legacy-desktop-accounts.imported"

        /** The keyring alias the legacy app kept a bunker login's transport key under. */
        fun bunkerTransportKeyAlias(npub: String) = "bunker_ephemeral_$npub"

        /** The keyring aliases one login may own: its private key and its bunker transport key. */
        private fun keyAliases(npub: String) = listOf(npub, bunkerTransportKeyAlias(npub))

        private const val LEGACY_SHARED_TRANSPORT_KEY_ALIAS = "bunker_ephemeral"
    }
}
