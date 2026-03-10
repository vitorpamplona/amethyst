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
package com.vitorpamplona.amethyst.desktop.account

import com.vitorpamplona.amethyst.commons.domain.nip46.SignerConnectionState
import com.vitorpamplona.amethyst.commons.keystorage.SecureKeyStorage
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip46RemoteSigner.signer.NostrSignerRemote
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests verifying NIP-46 relay isolation — the dedicated NIP-46 client
 * is created internally by AccountManager, not passed from callers.
 *
 * These tests reproduce the root cause of Bug 2 (shared client relay
 * contamination) by verifying the API no longer accepts an external client.
 */
class AccountManagerNip46IsolationTest {
    private lateinit var storage: SecureKeyStorage
    private lateinit var tempDir: File
    private lateinit var amethystDir: File
    private lateinit var manager: AccountManager

    @BeforeTest
    fun setup() {
        storage = mockk(relaxed = true)
        tempDir = createTempDirectory("acctmgr-nip46-iso-test").toFile()
        amethystDir = File(tempDir, ".amethyst")
        amethystDir.mkdirs()
        manager = AccountManager(storage, tempDir)
    }

    @AfterTest
    fun teardown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun bunkerLoadCreatesRemoteSignerInternally() =
        runTest {
            val validHex = "a".repeat(64)
            val ephemeralKeyPair = KeyPair()
            File(amethystDir, "bunker_uri.txt").writeText(
                "bunker://$validHex?relay=wss://relay.nsec.app",
            )
            File(amethystDir, "last_account.txt").writeText(
                ephemeralKeyPair.pubKey.toNpub(),
            )
            coEvery {
                storage.getPrivateKey(AccountManager.BUNKER_EPHEMERAL_KEY_ALIAS)
            } returns ephemeralKeyPair.privKey!!.toHexKey()

            val result = manager.loadSavedAccount()
            assertTrue(result.isSuccess)

            val state = manager.currentAccount()
            assertNotNull(state)
            assertIs<SignerType.Remote>(state.signerType)
            assertIs<NostrSignerRemote>(state.signer)
        }

    @Test
    fun loginWithBunkerDoesNotRequireExternalClient() =
        runTest {
            // loginWithBunker() compiles with just bunkerUri — no client param
            try {
                manager.loginWithBunker(
                    "bunker://${"a".repeat(64)}?relay=wss://relay.nsec.app",
                )
            } catch (e: Exception) {
                // Expected — no real relay. Verify it's a connection error, not API error.
                assertTrue(
                    e.message?.contains("Connection") == true ||
                        e.message?.contains("timed out") == true ||
                        e.message?.contains("failed") == true,
                    "Unexpected error type: ${e.message}",
                )
            }
        }

    @Test
    fun loginWithNostrConnectDoesNotRequireExternalClient() =
        runTest {
            var generatedUri: String? = null
            try {
                manager.loginWithNostrConnect { uri -> generatedUri = uri }
            } catch (e: Exception) {
                // Expected — no signer scanning the QR
                assertNotNull(e.message)
            }
            // URI should have been generated even if connection timed out
            assertNotNull(generatedUri, "nostrconnect URI was never generated")
            assertTrue(
                generatedUri!!.startsWith("nostrconnect://"),
                "URI format wrong: $generatedUri",
            )
        }

    @Test
    fun logoutResetsStateAfterBunkerLoad() =
        runTest {
            val validHex = "a".repeat(64)
            val ephemeralKeyPair = KeyPair()
            File(amethystDir, "bunker_uri.txt").writeText(
                "bunker://$validHex?relay=wss://relay.nsec.app",
            )
            File(amethystDir, "last_account.txt").writeText(
                ephemeralKeyPair.pubKey.toNpub(),
            )
            coEvery {
                storage.getPrivateKey(AccountManager.BUNKER_EPHEMERAL_KEY_ALIAS)
            } returns ephemeralKeyPair.privKey!!.toHexKey()

            manager.loadSavedAccount()
            assertIs<AccountState.LoggedIn>(manager.accountState.value)

            manager.logout()
            assertIs<AccountState.LoggedOut>(manager.accountState.value)
            assertIs<SignerConnectionState.NotRemote>(
                manager.signerConnectionState.value,
            )
        }

    @Test
    fun logoutThenReloadCreatesFreshNip46Client() =
        runTest {
            val validHex = "a".repeat(64)
            val ephemeralKeyPair = KeyPair()
            val bunkerUri = "bunker://$validHex?relay=wss://relay.nsec.app"

            fun writeBunkerFiles() {
                File(amethystDir, "bunker_uri.txt").writeText(bunkerUri)
                File(amethystDir, "last_account.txt").writeText(
                    ephemeralKeyPair.pubKey.toNpub(),
                )
            }

            coEvery {
                storage.getPrivateKey(AccountManager.BUNKER_EPHEMERAL_KEY_ALIAS)
            } returns ephemeralKeyPair.privKey!!.toHexKey()

            // First load
            writeBunkerFiles()
            manager.loadSavedAccount()
            val firstSigner = manager.currentAccount()?.signer
            assertNotNull(firstSigner)

            // Logout — disconnects NIP-46 client
            manager.logout()
            assertIs<AccountState.LoggedOut>(manager.accountState.value)

            // Second load — should create a fresh NIP-46 client
            writeBunkerFiles()
            manager.loadSavedAccount()
            val secondSigner = manager.currentAccount()?.signer
            assertNotNull(secondSigner)

            // Both should be remote signers — different instances
            assertIs<NostrSignerRemote>(firstSigner)
            assertIs<NostrSignerRemote>(secondSigner)
            assertTrue(
                firstSigner !== secondSigner,
                "Second load should create a new signer instance",
            )
        }
}
