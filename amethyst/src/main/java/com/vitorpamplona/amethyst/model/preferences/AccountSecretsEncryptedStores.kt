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
package com.vitorpamplona.amethyst.model.preferences

import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User


import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip47WalletConnect.Nip47WalletConnect
import com.vitorpamplona.quartz.utils.cache.LargeCache
import kotlinx.coroutines.CoroutineScope
import java.io.File

class AccountSecretsEncryptedStores(
    val rootFilesDir: () -> File,
    val scope: CoroutineScope,
) {
    companion object Companion {
        val encryption = KeyStoreEncryption()
        val key = stringPreferencesKey("privKey")
        val nwc = stringPreferencesKey("nwc")
    }

    private val storeCache = LargeCache<String, EncryptedDataStore>()

    fun file(npub: String) = File(rootFilesDir(), "datastore/$npub.secrets")

    private fun getDataStore(npub: String): EncryptedDataStore =
        storeCache.getOrCreate(npub) {
            EncryptedDataStore(
                PreferenceDataStoreFactory.create(
                    produceFile = { file(npub) },
                ),
                encryption,
                scope = scope,
            )
        }

    suspend fun getPrivateKey(npub: String): String? = getDataStore(npub).get(key)

    suspend fun savePrivateKey(
        npub: String,
        value: HexKey,
    ) {
        getDataStore(npub).save(key, value)
    }

    suspend fun nwc(npub: String): UpdatablePropertyFlow<Nip47WalletConnect.Nip47URI> =
        getDataStore(npub).getProperty(
            key = nwc,
            parser = Nip47WalletConnect.Nip47URI::parser,
            serializer = Nip47WalletConnect.Nip47URI::serializer,
        )

    fun removeAccount(npub: String): Boolean {
        val deleted = file(npub).delete()
        storeCache.remove(npub)
        return deleted
    }
}
