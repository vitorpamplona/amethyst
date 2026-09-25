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
package com.vitorpamplona.amethyst

import com.vitorpamplona.amethyst.commons.model.preferences.AccountSecrets
import com.vitorpamplona.amethyst.commons.model.preferences.AccountSecretsEncryptedStores
import com.vitorpamplona.amethyst.commons.model.preferences.GeohashIdentitySecrets
import com.vitorpamplona.quartz.utils.Log
import okio.Path.Companion.toOkioPath

/**
 * Moves the per-account secrets — NIP-46 bunker material, wallet connection
 * strings — out of the `secret_keeper_<npub>` EncryptedSharedPreferences file
 * and into an encrypted DataStore, on the same terms as the private key: both
 * stores written, new store preferred on read, nothing deleted.
 *
 * The copy is lazy rather than a DataMigration, and that is not a style
 * choice. A DataMigration writes values as-is, while this store decrypts on
 * read, so plaintext placed there by one cannot be read back — the attempt
 * raises. `EncryptedDataStoreTest` pins that behaviour. Copying through the
 * store's own `save` is what keeps the values readable.
 *
 * Losing these is recoverable — the user re-pairs a signer or re-adds a wallet
 * — but it is not something to spend, so a read that fails falls back to the
 * legacy values rather than reporting the account as having none.
 *
 * The legacy reader stays for good; see [EncryptedStorage] for why a lazy
 * migration cannot have its source deleted.
 */
class AccountSecretsStore(
    private val stores: AccountSecretsEncryptedStores,
) {
    companion object {
        private const val TAG = "AccountSecretsStore"
    }

    /**
     * The account's secrets, migrating out of the legacy file on first use.
     *
     * @param legacy what the legacy encrypted file holds, read by the caller
     *   that already has it open.
     */
    suspend fun read(
        npub: String,
        legacy: AccountSecrets,
    ): AccountSecrets {
        val stored =
            try {
                stores.loadSecrets(npub)
            } catch (e: Exception) {
                Log.w(TAG, "Could not read the secrets store for $npub; using the legacy file", e)
                return legacy
            }

        if (stored != null) return stored

        // Not migrated yet: copy the legacy values across and use them.
        mirror(npub, legacy)
        return legacy
    }

    /** Mirrors a save into the new store. The legacy write stays where it is. */
    suspend fun mirror(
        npub: String,
        value: AccountSecrets,
    ) {
        try {
            stores.saveSecrets(npub, value)
        } catch (e: Exception) {
            // Never fatal: the legacy file still has them, and the next save or
            // load tries again.
            Log.w(TAG, "Could not write the secrets for $npub to the current store", e)
        }
    }

    /**
     * What the current store holds, with no fallback to the legacy file.
     *
     * For [LegacyPreferenceCleanup], which has to tell "migrated" from
     * "falling back and looking migrated" — the read above deliberately cannot.
     */
    suspend fun stored(npub: String): AccountSecrets? = stores.loadSecrets(npub)

    // ── the location-chat identity ────────────────────────────────────

    /**
     * The account's location-chat identity, migrating out of the legacy file on
     * first use, on the same terms as [read].
     *
     * @param legacy what `secret_keeper_<pubkey hex>` holds. Note the *hex*: this
     *   group's legacy file is keyed by the signer's pubkey rather than the npub
     *   every other group uses, so the caller opens a different file for it.
     */
    suspend fun readGeohashIdentity(
        npub: String,
        legacy: GeohashIdentitySecrets,
    ): GeohashIdentitySecrets {
        val stored =
            try {
                stores.loadGeohashIdentity(npub)
            } catch (e: Exception) {
                Log.w(TAG, "Could not read the location-chat identity for $npub; using the legacy file", e)
                return legacy
            }

        if (stored != null) return stored

        mirrorGeohashIdentity(npub, legacy)
        return legacy
    }

    /**
     * What the current store holds for the location-chat identity, with no
     * fallback to the legacy file — the same distinction [stored] draws, and
     * for the same reader: [LegacyPreferenceCleanup] has to tell "migrated"
     * from "falling back and looking migrated".
     */
    suspend fun storedGeohashIdentity(npub: String): GeohashIdentitySecrets? = stores.loadGeohashIdentity(npub)

    /** Mirrors a save into the new store. The legacy write stays where it is. */
    suspend fun mirrorGeohashIdentity(
        npub: String,
        value: GeohashIdentitySecrets,
    ) {
        try {
            stores.saveGeohashIdentity(npub, value)
        } catch (e: Exception) {
            Log.w(TAG, "Could not write the location-chat identity for $npub to the current store", e)
        }
    }

    suspend fun delete(npub: String) {
        try {
            stores.removeAccount(npub)
        } catch (e: Exception) {
            Log.w(TAG, "Could not drop the secrets store for $npub", e)
        }
    }
}

val accountSecretsStore: AccountSecretsStore by lazy {
    AccountSecretsStore(
        AccountSecretsEncryptedStores(
            rootFilesDir = {
                Amethyst.instance.appContext.filesDir
                    .toOkioPath()
            },
            scope = Amethyst.instance.applicationIOScope,
        ),
    )
}
