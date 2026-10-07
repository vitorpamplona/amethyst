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

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vitorpamplona.amethyst.commons.cashu.CashuKeysetCounterStore
import com.vitorpamplona.amethyst.commons.cashu.UnavailableCashuKeysetCounterStore
import com.vitorpamplona.amethyst.commons.keystorage.PrivateKeyVault
import com.vitorpamplona.amethyst.commons.model.AccountSettings
import com.vitorpamplona.amethyst.commons.model.preferences.AccountPreferenceStores
import com.vitorpamplona.amethyst.commons.model.preferences.AccountRosterStore
import com.vitorpamplona.amethyst.commons.model.preferences.AccountSecrets
import com.vitorpamplona.amethyst.commons.model.preferences.AccountSecretsEncryptedStores
import com.vitorpamplona.amethyst.commons.model.preferences.AccountSettingsSource
import com.vitorpamplona.amethyst.commons.model.preferences.AccountSettingsStores
import com.vitorpamplona.amethyst.commons.model.preferences.AppPreferenceStores
import com.vitorpamplona.amethyst.commons.model.preferences.SecretEncryption
import com.vitorpamplona.amethyst.commons.model.preferences.toAccountSecrets
import com.vitorpamplona.amethyst.commons.model.preferences.toAccountSettings
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.Path

/**
 * The saved logins of a front end with no legacy storage to migrate from: the encrypted roster, each
 * account's DataStore records, its encrypted secrets and its private key in [keyVault]. These are the
 * stores Android writes too; Android adds its legacy fallbacks on top of them.
 */
class StoreAccountSessionStore(
    rootFilesDir: () -> Path,
    appStores: AppPreferenceStores,
    private val keyVault: PrivateKeyVault,
    scope: CoroutineScope,
    private val cashuCounters: (npub: String) -> CashuKeysetCounterStore = { UnavailableCashuKeysetCounterStore },
    encryption: SecretEncryption = SecretEncryption(),
) : AccountSessionStore {
    private val accountStores = AccountPreferenceStores(rootFilesDir)
    private val settingsStores = AccountSettingsStores(accountStores)
    private val secretsStores = AccountSecretsEncryptedStores(rootFilesDir, scope, encryption)
    private val roster = AccountRosterStore(appStores.getDataStore(ROSTER_FILE_NAME), encryption, scope)

    private val savedAccounts = MutableStateFlow<List<AccountInfo>?>(null)
    private val savedAccountsMutex = Mutex()

    private val loadMutex = Mutex()
    private val cachedAccounts = mutableMapOf<String, AccountSettings>()

    @Volatile
    private var currentAccount: String? = null

    override suspend fun currentAccount(): String? = currentAccount ?: roster.currentAccount().also { currentAccount = it }

    override fun accountsFlow(): StateFlow<List<AccountInfo>?> = savedAccounts

    override suspend fun allSavedAccounts(): List<AccountInfo> {
        savedAccounts.value?.let { return it }
        return savedAccountsMutex.withLock {
            savedAccounts.value ?: readRoster().also { savedAccounts.value = it }
        }
    }

    private suspend fun readRoster(): List<AccountInfo> =
        try {
            roster.allAccountInfoJson()?.let { JsonMapper.fromJson<List<AccountInfo>>(it) } ?: emptyList()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Could not read the saved accounts", e)
            emptyList()
        }

    private suspend fun updateSavedAccounts(accounts: List<AccountInfo>) {
        if (savedAccounts.value == accounts) return
        savedAccounts.value = accounts
        roster.setAllAccountInfoJson(JsonMapper.toJson(accounts.filter { !it.isTransient }))
    }

    private suspend fun updateCurrentAccount(info: AccountInfo?) {
        if (info == null) {
            currentAccount = null
            roster.setCurrentAccount(null)
        } else if (currentAccount != info.npub) {
            currentAccount = info.npub
            if (!info.isTransient) roster.setCurrentAccount(info.npub)
        }
    }

    override suspend fun switchToAccount(accountInfo: AccountInfo) = updateCurrentAccount(accountInfo)

    override suspend fun setDefaultAccount(accountSettings: AccountSettings): AccountSettings {
        val npub = accountSettings.keyPair.pubKey.toNpub()

        // Adding a read-only npub for a pubkey this device already signs for keeps the signing
        // account: saving the read-only one over it would drop its key and every cached list.
        if (!accountSettings.isWriteable()) {
            val existing = loadAccountConfigFromEncryptedStorage(npub)
            if (existing != null && existing.isWriteable()) {
                setCurrentAccount(existing)
                return existing
            }
        }

        // Saved before the roster changes, so a collector of the roster never finds an account
        // whose settings are not on disk yet.
        saveAccountSettings(accountSettings)
        loadMutex.withLock { cachedAccounts[npub] = accountSettings }
        setCurrentAccount(accountSettings)
        return accountSettings
    }

    private suspend fun setCurrentAccount(settings: AccountSettings) {
        val info =
            AccountInfo(
                npub = settings.keyPair.pubKey.toNpub(),
                hasPrivKey = settings.isWriteable(),
                loggedInWithExternalSigner = settings.externalSignerPackageName != null,
                isTransient = settings.transientAccount,
            )
        updateCurrentAccount(info)
        updateSavedAccounts(allSavedAccounts().filter { it.npub != info.npub } + info)
    }

    /** Persists [settings]: the account's records, its secrets and its key. Transient logins keep nothing. */
    suspend fun saveAccountSettings(settings: AccountSettings) {
        if (settings.transientAccount) return
        val npub = settings.keyPair.pubKey.toNpub()
        settingsStores.save(settings)
        secretsStores.saveSecrets(npub, settings.toAccountSecrets())
        settings.keyPair.privKey?.let { keyVault.save(npub, it.toHexKey()) }
        accountStores.getDataStore(npub).edit {
            it[lastReadPerRouteKey] = JsonMapper.toJson(settings.lastReadPerRoute.value.mapValues { entry -> entry.value.value })
            it[pendingAttestationsKey] = JsonMapper.toJson(settings.pendingAttestations.value)
        }
    }

    override suspend fun loadAccountConfigFromEncryptedStorage(): AccountSettings? = currentAccount()?.let { loadAccountConfigFromEncryptedStorage(it) }

    override suspend fun loadAccountConfigFromEncryptedStorage(npub: String): AccountSettings? =
        loadMutex.withLock {
            cachedAccounts[npub] ?: load(npub)?.also { cachedAccounts[npub] = it }
        }

    private suspend fun load(npub: String): AccountSettings? {
        val records = settingsStores.load(npub)
        val pubKey = records.identity.pubKeyHex ?: return null
        val privKey = keyVault.get(npub)
        val keyPair = KeyPair(privKey = privKey?.hexToByteArray(), pubKey = pubKey.hexToByteArray())
        val state = accountStores.getDataStore(npub).data.first()
        return AccountSettingsSource(
            keyPair = keyPair,
            externalSignerPackageName = records.identity.externalSignerPackageName,
            stores = records,
            secrets = secretsStores.loadSecrets(npub) ?: AccountSecrets(),
            pendingAttestationsJson = state[pendingAttestationsKey],
            lastReadPerRouteJson = state[lastReadPerRouteKey],
            cashuCounters = cashuCounters(npub),
        ).toAccountSettings()
    }

    /** Drops the in-memory copies, so the next load reads the stores again. */
    internal suspend fun forgetLoadedSettings() = loadMutex.withLock { cachedAccounts.clear() }

    override suspend fun setHasBackedUpKeys(
        value: Boolean,
        npub: String,
    ) {
        settingsStores.identity(npub).setHasBackedUpKeys(value)
    }

    /** Whether [npub]'s key was backed up; true unless a freshly generated key is still waiting on it. */
    suspend fun hasBackedUpKeys(npub: String): Boolean = settingsStores.identity(npub).hasBackedUpKeys()

    override suspend fun deleteAccount(accountInfo: AccountInfo) {
        val npub = accountInfo.npub
        loadMutex.withLock { cachedAccounts.remove(npub) }
        keyVault.delete(npub)
        secretsStores.removeAccount(npub)
        accountStores.removeAccount(npub)
        updateSavedAccounts(allSavedAccounts().filter { it.npub != npub })

        val remaining = allSavedAccounts()
        if (remaining.isEmpty()) {
            updateCurrentAccount(null)
        } else if (currentAccount() == npub) {
            updateCurrentAccount(remaining.first())
        }
    }

    companion object {
        private const val TAG = "StoreAccountSessionStore"
        private const val ROSTER_FILE_NAME = "roster"

        // The two values Android still keeps in its legacy file; here they sit in the account's DataStore.
        private val lastReadPerRouteKey = stringPreferencesKey("last_read_per_route")
        private val pendingAttestationsKey = stringPreferencesKey("pending_attestations")
    }
}
