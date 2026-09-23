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
package com.vitorpamplona.amethyst.commons.model.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vitorpamplona.amethyst.commons.util.platformFileSystem
import com.vitorpamplona.quartz.nip47WalletConnect.Nip47WalletConnect
import com.vitorpamplona.quartz.utils.cache.LargeCache
import kotlinx.coroutines.CoroutineScope
import okio.Path

/**
 * The per-account secret store: one encrypted DataStore per npub, at
 * `<root>/datastore/<npub>.secrets_pb`, for secrets that are not the identity
 * key — wallet connection strings, NIP-46 bunker material.
 *
 * **Private keys do not belong here.** They live in
 * [com.vitorpamplona.amethyst.commons.keystorage.SecureKeyStorage], which backs
 * them with the OS credential manager (Keychain, Credential Manager, Secret
 * Service) rather than a file this process can read, and which desktop already
 * uses. Two stores for one identity key would be one store too many, and the
 * weaker one would win by being convenient.
 *
 * Separate from [AccountPreferenceStores] so ordinary settings stay cheap to
 * read: every value here pays an encrypt/decrypt, which on a StrongBox-backed
 * device runs at roughly 68 KB/s.
 */
class AccountSecretsEncryptedStores(
    val rootFilesDir: () -> Path,
    val scope: CoroutineScope,
    private val encryption: SecretEncryption = SecretEncryption(),
) {
    companion object {
        val nwc = stringPreferencesKey("nwc")
    }

    private val storeCache = LargeCache<String, EncryptedDataStore>()

    fun file(npub: String): Path = rootFilesDir() / "datastore" / "$npub.secrets_pb"

    fun getDataStore(npub: String): EncryptedDataStore =
        storeCache.getOrCreate(npub) {
            EncryptedDataStore(
                PreferenceDataStoreFactory.createWithPath(produceFile = { file(npub) }),
                encryption,
                scope = scope,
            )
        }

    fun nwc(npub: String): UpdatablePropertyFlow<Nip47WalletConnect.Nip47URI> =
        getDataStore(npub).getProperty(
            key = nwc,
            parser = Nip47WalletConnect.Nip47URI::parser,
            serializer = Nip47WalletConnect.Nip47URI::serializer,
        )

    /** See [AccountPreferenceStores.removeAccount] — the cached handle goes first. */
    fun removeAccount(npub: String): Boolean {
        storeCache.remove(npub)
        val path = file(npub)
        if (!platformFileSystem.exists(path)) return false
        platformFileSystem.delete(path)
        return true
    }
}
