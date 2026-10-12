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

import com.vitorpamplona.amethyst.commons.keystorage.KeyNotKeptException
import com.vitorpamplona.amethyst.commons.keystorage.KeyPrompt
import com.vitorpamplona.amethyst.commons.keystorage.KeyStoragePrompts
import com.vitorpamplona.amethyst.commons.keystorage.PrivateKeyVault
import com.vitorpamplona.amethyst.commons.keystorage.ScriptedPrompts
import com.vitorpamplona.amethyst.commons.model.AccountSettings
import com.vitorpamplona.amethyst.commons.model.preferences.AppPreferenceStores
import com.vitorpamplona.amethyst.commons.model.preferences.SecretEncryption
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip19Bech32.toNsec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toOkioPath
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StoreAccountSessionStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private class MemoryVault : PrivateKeyVault {
        val keys = mutableMapOf<String, String>()
        var saves = 0

        // Windows and Linux backends report a missing item as an error, which the strict lookup rethrows.
        var throwOnMissing = false

        override suspend fun get(npub: String): String? = keys[npub] ?: if (throwOnMissing) throw IllegalStateException("no item") else null

        override suspend fun save(
            npub: String,
            privKeyHex: String,
        ) {
            saves++
            keys[npub] = privKeyHex
        }

        override suspend fun delete(npub: String) {
            keys.remove(npub)
        }
    }

    private val vault = MemoryVault()

    private fun newStore(keyPrompts: KeyStoragePrompts? = null): StoreAccountSessionStore {
        val root = folder.root
        return StoreAccountSessionStore(
            rootFilesDir = { root.toOkioPath() },
            appStores = AppPreferenceStores(rootFilesDir = { root.toOkioPath() }),
            keyVault = vault,
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            encryption = SecretEncryption(File(root, "secret.key")),
            keyPrompts = keyPrompts,
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
    fun aRemoteSignerLoginKeepsItsSignerAcrossARestart() =
        runTest {
            val store = newStore()
            val user = KeyPair()
            val transport = KeyPair()
            val npub = user.pubKey.toNpub()
            val bunkerUri = "bunker://${KeyPair().pubKey.toHexKey()}?relay=wss://relay.nsec.app"

            store.setDefaultAccount(
                AccountSettings(
                    keyPair = KeyPair(pubKey = user.pubKey),
                    remoteSignerBunkerUri = bunkerUri,
                    remoteSignerTransportKey = transport.privKey!!.toHexKey(),
                ),
            )
            store.forgetLoadedSettings()

            val loaded = assertNotNull(store.loadAccountConfigFromEncryptedStorage(npub))
            assertNull(loaded.keyPair.privKey)
            assertEquals(bunkerUri, loaded.remoteSignerBunkerUri)
            assertEquals(transport.privKey!!.toHexKey(), loaded.remoteSignerTransportKey)
            assertTrue(loaded.isWriteable())
            // The transport key is a secret: it lives in the encrypted secrets, never in the key vault.
            assertTrue(vault.keys.isEmpty())
            // hasPrivKey reads "can sign" (it does for signer-app logins too).
            assertEquals(listOf(AccountInfo(npub, hasPrivKey = true, loggedInWithRemoteSigner = true)), store.allSavedAccounts())
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

    @Test
    fun savingSettingsAgainDoesNotRewriteAnUnchangedKey() =
        runTest {
            val store = newStore()
            val settings = AccountSettings(keyPair = KeyPair())

            store.setDefaultAccount(settings)
            assertEquals(1, vault.saves)

            // Every last-read marker saves the settings; none of them may reach the OS keychain.
            repeat(5) { store.saveAccountSettings(settings) }
            assertEquals(1, vault.saves)

            // A changed key is still written.
            store.saveAccountSettings(AccountSettings(keyPair = KeyPair(privKey = KeyPair().privKey, pubKey = settings.keyPair.pubKey, forceReplacePubkey = false)))
            assertEquals(2, vault.saves)
        }

    @Test
    fun aViewOnlyLoginLoadsWhenTheKeychainThrowsOnAMissingKey() =
        runTest {
            val store = newStore()
            val pubKey = KeyPair().pubKey
            store.setDefaultAccount(AccountSettings(keyPair = KeyPair(pubKey = pubKey)))
            store.forgetLoadedSettings()
            vault.throwOnMissing = true

            val loaded = assertNotNull(store.loadAccountConfigFromEncryptedStorage(pubKey.toNpub()))
            assertNull(loaded.keyPair.privKey)
        }

    @Test
    fun aKeyNotRememberedIsNotKeptAndIsAskedForOnTheNextLoad() =
        runBlocking<Unit> {
            val keyPair = KeyPair()
            val npub = keyPair.pubKey.toNpub()
            val nsec = keyPair.privKey!!.toNsec()
            // A key left in the vault from before the user turned "Remember" off is deleted.
            vault.keys[npub] = keyPair.privKey!!.toHexKey()

            val script = ScriptedPrompts(KeyPair().privKey!!.toNsec(), nsec)
            val job = script.start(this)
            val store = newStore(script.prompts)

            store.setDefaultAccount(AccountSettings(keyPair = keyPair, rememberKey = false))
            assertTrue(vault.keys.isEmpty())
            assertEquals(false, store.allSavedAccounts().single().keyRemembered)

            store.forgetLoadedSettings()
            val loaded = assertNotNull(store.loadAccountConfigFromEncryptedStorage(npub))
            assertEquals(keyPair.privKey?.toHexKey(), loaded.keyPair.privKey?.toHexKey())
            assertFalse(loaded.rememberKey)
            // The first key belonged to someone else, so it asked again.
            assertEquals(listOf(false, true), script.asked.map { (it as KeyPrompt.EnterKey).wrongKey })

            store.saveAccountSettings(loaded)
            assertTrue(vault.keys.isEmpty())
            job.cancel()
        }

    @Test
    fun skippingTheKeyLoadsTheAccountWithoutSigning() =
        runBlocking<Unit> {
            val keyPair = KeyPair()
            val script = ScriptedPrompts(null)
            val job = script.start(this)
            val store = newStore(script.prompts)

            store.setDefaultAccount(AccountSettings(keyPair = keyPair, rememberKey = false))
            store.forgetLoadedSettings()
            val loaded = assertNotNull(store.loadAccountConfigFromEncryptedStorage(keyPair.pubKey.toNpub()))
            assertNull(loaded.keyPair.privKey)
            job.cancel()
        }

    @Test
    fun aKeyTheUserChoseNotToKeepIsNoFailure() =
        runTest {
            val declining =
                object : PrivateKeyVault {
                    var saves = 0

                    override suspend fun get(npub: String): String? = null

                    override suspend fun save(
                        npub: String,
                        privKeyHex: String,
                    ) {
                        saves++
                        throw KeyNotKeptException("Keys are not kept on this device")
                    }

                    override suspend fun delete(npub: String) {}
                }
            val root = folder.root
            val store =
                StoreAccountSessionStore(
                    rootFilesDir = { root.toOkioPath() },
                    appStores = AppPreferenceStores(rootFilesDir = { root.toOkioPath() }),
                    keyVault = declining,
                    scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
                    encryption = SecretEncryption(File(root, "secret.key")),
                )
            val settings = AccountSettings(keyPair = KeyPair())

            store.setDefaultAccount(settings)
            store.saveAccountSettings(settings)

            assertNull(store.keyVaultFailure.value)
            assertEquals(false, store.allSavedAccounts().single().keyRemembered)
            assertEquals(1, declining.saves)
        }
}
