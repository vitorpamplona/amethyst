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

import com.vitorpamplona.amethyst.commons.keystorage.PrivateKeyVault
import com.vitorpamplona.amethyst.commons.model.AccountSettings
import com.vitorpamplona.amethyst.commons.model.preferences.AppPreferenceStores
import com.vitorpamplona.amethyst.commons.model.preferences.SecretEncryption
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toOkioPath
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StoreAccountSessionStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private class MemoryVault : PrivateKeyVault {
        val keys = mutableMapOf<String, String>()

        override suspend fun get(npub: String): String? = keys[npub]

        override suspend fun save(
            npub: String,
            privKeyHex: String,
        ) {
            keys[npub] = privKeyHex
        }

        override suspend fun delete(npub: String) {
            keys.remove(npub)
        }
    }

    private val vault = MemoryVault()

    private fun newStore(): StoreAccountSessionStore {
        val root = folder.root
        return StoreAccountSessionStore(
            rootFilesDir = { root.toOkioPath() },
            appStores = AppPreferenceStores(rootFilesDir = { root.toOkioPath() }),
            keyVault = vault,
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            encryption = SecretEncryption(File(root, "secret.key")),
        )
    }

    @Test
    fun aSavedAccountComesBackFromItsStores() =
        runTest {
            val store = newStore()
            val keyPair = KeyPair()
            val npub = keyPair.pubKey.toNpub()
            val settings =
                AccountSettings(
                    keyPair = keyPair,
                    localRelayServers = MutableStateFlow(setOf("ws://localhost:4869")),
                    hideDeleteRequestDialog = true,
                )
            settings.nip46BunkerSecret.value = "bunker-secret"
            settings.lastReadPerRoute.value = mapOf("Room/abc" to MutableStateFlow(1234L))

            store.setDefaultAccount(settings)
            store.forgetLoadedSettings()

            val loaded = assertNotNull(store.loadAccountConfigFromEncryptedStorage())
            assertEquals(keyPair.pubKey.toHexKey(), loaded.keyPair.pubKey.toHexKey())
            assertEquals(keyPair.privKey?.toHexKey(), loaded.keyPair.privKey?.toHexKey())
            assertEquals(setOf("ws://localhost:4869"), loaded.localRelayServers.value)
            assertTrue(loaded.hideDeleteRequestDialog)
            assertEquals("bunker-secret", loaded.nip46BunkerSecret.value)
            assertEquals(1234L, loaded.lastReadPerRoute.value["Room/abc"]?.value)

            assertEquals(npub, store.currentAccount())
            assertEquals(listOf(AccountInfo(npub, hasPrivKey = true)), store.allSavedAccounts())
        }

    @Test
    fun addingTheReadOnlyNpubOfASigningAccountKeepsTheSigningOne() =
        runTest {
            val store = newStore()
            val keyPair = KeyPair()
            store.setDefaultAccount(AccountSettings(keyPair = keyPair))

            val kept = store.setDefaultAccount(AccountSettings(keyPair = KeyPair(pubKey = keyPair.pubKey)))

            assertEquals(keyPair.privKey?.toHexKey(), kept.keyPair.privKey?.toHexKey())
            assertTrue(store.allSavedAccounts().single().hasPrivKey)
        }

    @Test
    fun deletingTheLastAccountLogsOff() =
        runTest {
            val store = newStore()
            val keyPair = KeyPair()
            val npub = keyPair.pubKey.toNpub()
            store.setDefaultAccount(AccountSettings(keyPair = keyPair))

            store.deleteAccount(AccountInfo(npub, hasPrivKey = true))

            assertTrue(store.allSavedAccounts().isEmpty())
            assertNull(store.currentAccount())
            assertNull(vault.keys[npub])
            assertNull(store.loadAccountConfigFromEncryptedStorage(npub))
        }

    @Test
    fun aTransientLoginLeavesNothingOnDisk() =
        runTest {
            val store = newStore()
            val keyPair = KeyPair()
            store.setDefaultAccount(AccountSettings(keyPair = keyPair, transientAccount = true))
            store.forgetLoadedSettings()

            assertNull(store.loadAccountConfigFromEncryptedStorage(keyPair.pubKey.toNpub()))
            assertTrue(vault.keys.isEmpty())
        }
}
