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

import com.vitorpamplona.amethyst.commons.keystorage.SecureKeyStorage
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AccountManagerLoadAccountTest {
    private lateinit var storage: SecureKeyStorage
    private lateinit var tempDir: File
    private lateinit var amethystDir: File
    private lateinit var manager: AccountManager

    @BeforeTest
    fun setup() {
        storage = mockk(relaxed = true)
        tempDir = createTempDirectory("acctmgr-load-test").toFile()
        amethystDir = File(tempDir, ".amethyst")
        amethystDir.mkdirs()
        manager = AccountManager(storage, tempDir)
    }

    @AfterTest
    fun teardown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun loadSavedAccountNoNpubReturnsFailure() =
        runTest {
            // No last_account.txt file
            val result = manager.loadSavedAccount()
            assertTrue(result.isFailure)
        }

    @Test
    fun loadSavedAccountInternalSuccess() =
        runTest {
            val keyPair = KeyPair()
            val npub = keyPair.pubKey.toNpub()
            val privKeyHex = keyPair.privKey!!.toHexKey()

            // Write last_account.txt
            File(amethystDir, "last_account.txt").writeText(npub)

            // Mock storage to return the private key
            coEvery { storage.getPrivateKey(npub) } returns privKeyHex

            val result = manager.loadSavedAccount()
            assertTrue(result.isSuccess)
            val state = result.getOrThrow()
            assertIs<AccountState.LoggedIn>(state)
            assertIs<SignerType.Internal>(state.signerType)
        }

    @Test
    fun loadSavedAccountInternalNoPrivkeyReturnsFailure() =
        runTest {
            val keyPair = KeyPair()
            val npub = keyPair.pubKey.toNpub()

            File(amethystDir, "last_account.txt").writeText(npub)
            coEvery { storage.getPrivateKey(npub) } returns null

            val result = manager.loadSavedAccount()
            assertTrue(result.isFailure)
        }

    @Test
    fun loadSavedAccountBunkerNoEphemeralReturnsFailure() =
        runTest {
            val validHex = "a".repeat(64)
            val keyPair = KeyPair()
            val npub = keyPair.pubKey.toNpub()

            File(amethystDir, "last_account.txt").writeText(npub)
            File(amethystDir, "bunker_uri.txt").writeText(
                "bunker://$validHex?relay=wss://r.com",
            )
            coEvery {
                storage.getPrivateKey(AccountManager.BUNKER_EPHEMERAL_KEY_ALIAS)
            } returns null

            val result = manager.loadSavedAccount(client = com.vitorpamplona.quartz.nip01Core.relay.client.EmptyNostrClient)
            assertTrue(result.isFailure)
        }

    @Test
    fun loadSavedAccountBunkerNoClientFallsBackToInternal() =
        runTest {
            val validHex = "a".repeat(64)
            val keyPair = KeyPair()
            val npub = keyPair.pubKey.toNpub()
            val privKeyHex = keyPair.privKey!!.toHexKey()

            File(amethystDir, "last_account.txt").writeText(npub)
            File(amethystDir, "bunker_uri.txt").writeText(
                "bunker://$validHex?relay=wss://r.com",
            )
            coEvery { storage.getPrivateKey(npub) } returns privKeyHex

            // client=null → bunkerUri is found but ignored, falls back to internal
            val result = manager.loadSavedAccount(client = null)
            assertTrue(result.isSuccess)
            assertIs<SignerType.Internal>(result.getOrThrow().signerType)
        }

    @Test
    fun loadSavedAccountBunkerSuccess() =
        runTest {
            val keyPair = KeyPair()
            val npub = keyPair.pubKey.toNpub()
            val ephemeralKeyPair = KeyPair()
            val ephemeralPrivKeyHex = ephemeralKeyPair.privKey!!.toHexKey()
            val validHex = keyPair.pubKey.toHexKey()

            File(amethystDir, "last_account.txt").writeText(npub)
            File(amethystDir, "bunker_uri.txt").writeText(
                "bunker://$validHex?relay=wss://r.com",
            )
            coEvery {
                storage.getPrivateKey(AccountManager.BUNKER_EPHEMERAL_KEY_ALIAS)
            } returns ephemeralPrivKeyHex

            val result =
                manager.loadSavedAccount(
                    client = com.vitorpamplona.quartz.nip01Core.relay.client.EmptyNostrClient,
                )
            assertTrue(result.isSuccess)
            val state = result.getOrThrow()
            assertIs<SignerType.Remote>(state.signerType)
        }
}
