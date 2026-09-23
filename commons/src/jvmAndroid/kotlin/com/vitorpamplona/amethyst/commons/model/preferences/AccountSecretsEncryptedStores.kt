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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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

    /**
     * One store per account, each on its own child scope.
     *
     * The scope matters: DataStore keeps a process-wide registry keyed by file
     * path and only releases an entry when the owning scope is cancelled. On a
     * shared scope, deleting an account and re-adding it in the same session
     * would throw "multiple DataStores active for the same file".
     */
    private class Entry(
        val scope: CoroutineScope,
        val store: EncryptedDataStore,
    )

    private val storeCache = LargeCache<String, Entry>()

    /**
     * The `.preferences_pb` suffix is required, not decorative: DataStore's
     * Preferences factory rejects any other extension at open time. The
     * `.secrets` part is what keeps this file distinct from the account's
     * plain preference store.
     */
    fun file(npub: String): Path = rootFilesDir() / "datastore" / "$npub.secrets.preferences_pb"

    fun getDataStore(npub: String): EncryptedDataStore =
        storeCache
            .getOrCreate(npub) {
                val child = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]))
                Entry(
                    child,
                    EncryptedDataStore(
                        PreferenceDataStoreFactory.createWithPath(scope = child, produceFile = { file(npub) }),
                        encryption,
                        scope = child,
                    ),
                )
            }.store

    fun nwc(npub: String): UpdatablePropertyFlow<Nip47WalletConnect.Nip47URI> =
        getDataStore(npub).getProperty(
            key = nwc,
            parser = Nip47WalletConnect.Nip47URI::parser,
            serializer = Nip47WalletConnect.Nip47URI::serializer,
        )

    /**
     * Drops the account's secrets.
     *
     * Cancels the store's scope before deleting, so DataStore releases the
     * path and the same account can be added again in this session.
     */
    fun removeAccount(npub: String): Boolean {
        storeCache.get(npub)?.scope?.cancel()
        storeCache.remove(npub)
        val path = file(npub)
        if (!platformFileSystem.exists(path)) return false
        platformFileSystem.delete(path)
        return true
    }

    // ── the per-account secret group ──────────────────────────────────

    /**
     * Reads [AccountSecrets], or null when this account has not been migrated
     * out of the legacy encrypted file yet.
     *
     * Null and "all defaults" are deliberately different answers: the caller
     * uses null to decide whether to run the one-off copy, and an account that
     * genuinely holds no secrets must not trigger it forever.
     */
    suspend fun loadSecrets(npub: String): AccountSecrets? {
        val store = getDataStore(npub)
        if (store.get(AccountSecretKeys.migrated) == null) return null

        return AccountSecrets(
            nip46SignerEnabled = store.get(AccountSecretKeys.nip46SignerEnabled).toBoolean(),
            nip46BunkerSecret = store.get(AccountSecretKeys.nip46BunkerSecret) ?: "",
            nip46TransportKey = store.get(AccountSecretKeys.nip46TransportKey) ?: "",
            nip46SeenRequestIds = decodeSet(store.get(AccountSecretKeys.nip46SeenRequestIds)),
            nwcWalletsJson = store.get(AccountSecretKeys.nwcWallets),
            clinkDebitWalletsJson = store.get(AccountSecretKeys.clinkDebitWallets),
            defaultPaymentSourceId = store.get(AccountSecretKeys.defaultPaymentSourceId),
            legacyDefaultNwcWalletId = store.get(AccountSecretKeys.legacyDefaultNwcWalletId),
            legacyZapPaymentRequestServer = store.get(AccountSecretKeys.legacyZapPaymentRequestServer),
        )
    }

    /**
     * Writes the group, then the marker.
     *
     * Marker last on purpose: a crash midway leaves the account looking
     * unmigrated, so the next load copies from the legacy file again rather
     * than reading a half-written set of secrets as complete.
     */
    suspend fun saveSecrets(
        npub: String,
        value: AccountSecrets,
    ) {
        val store = getDataStore(npub)

        store.save(AccountSecretKeys.nip46SignerEnabled, value.nip46SignerEnabled.toString())
        store.save(AccountSecretKeys.nip46BunkerSecret, value.nip46BunkerSecret)
        store.save(AccountSecretKeys.nip46TransportKey, value.nip46TransportKey)
        store.save(AccountSecretKeys.nip46SeenRequestIds, value.nip46SeenRequestIds.joinToString(AccountSecretKeys.SET_SEPARATOR))
        store.putOrRemove(AccountSecretKeys.nwcWallets, value.nwcWalletsJson)
        store.putOrRemove(AccountSecretKeys.clinkDebitWallets, value.clinkDebitWalletsJson)
        store.putOrRemove(AccountSecretKeys.defaultPaymentSourceId, value.defaultPaymentSourceId)
        store.putOrRemove(AccountSecretKeys.legacyDefaultNwcWalletId, value.legacyDefaultNwcWalletId)
        store.putOrRemove(AccountSecretKeys.legacyZapPaymentRequestServer, value.legacyZapPaymentRequestServer)

        store.save(AccountSecretKeys.migrated, "true")
    }

    private suspend fun EncryptedDataStore.putOrRemove(
        key: androidx.datastore.preferences.core.Preferences.Key<String>,
        value: String?,
    ) {
        if (value != null) save(key, value) else remove(key)
    }

    private fun decodeSet(raw: String?): Set<String> =
        raw
            ?.split(AccountSecretKeys.SET_SEPARATOR)
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            ?: emptySet()
}
