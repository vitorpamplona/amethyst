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
import com.vitorpamplona.amethyst.commons.model.account.AccountInfo
import com.vitorpamplona.amethyst.commons.model.account.SignerType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopAccountStorageTest {
    private lateinit var tempDir: File
    private lateinit var secureStorage: SecureKeyStorage
    private lateinit var storage: DesktopAccountStorage

    // In-memory key store for tests
    private val keyStore = mutableMapOf<String, String>()

    @BeforeTest
    fun setup() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "amethyst-test-${System.nanoTime()}")
        tempDir.mkdirs()

        secureStorage = mockk()
        val keySlot = slot<String>()
        val valueSlot = slot<String>()

        coEvery { secureStorage.getPrivateKey(capture(keySlot)) } answers {
            keyStore[keySlot.captured]
        }
        coEvery { secureStorage.savePrivateKey(capture(keySlot), capture(valueSlot)) } answers {
            keyStore[keySlot.captured] = valueSlot.captured
        }
        coEvery { secureStorage.hasPrivateKey(any()) } answers {
            keyStore.containsKey(firstArg())
        }

        storage = DesktopAccountStorage(secureStorage, tempDir)
    }

    @AfterTest
    fun teardown() {
        tempDir.deleteRecursively()
        keyStore.clear()
    }

    @Test
    fun `empty storage returns no accounts`() =
        runTest {
            val accounts = storage.loadAccounts()
            assertTrue(accounts.isEmpty())
            assertNull(storage.currentAccount())
        }

    @Test
    fun `save and load account roundtrip`() =
        runTest {
            val info = AccountInfo(npub = "npub1test123", signerType = SignerType.Internal)
            storage.saveAccount(info)

            val loaded = storage.loadAccounts()
            assertEquals(1, loaded.size)
            assertEquals("npub1test123", loaded[0].npub)
            assertEquals(SignerType.Internal, loaded[0].signerType)
        }

    @Test
    fun `save remote account preserves bunker URI`() =
        runTest {
            val bunkerUri = "bunker://abc123?relay=wss://relay.example.com"
            val info = AccountInfo(npub = "npub1remote", signerType = SignerType.Remote(bunkerUri))
            storage.saveAccount(info)

            val loaded = storage.loadAccounts()
            assertEquals(1, loaded.size)
            val loadedType = loaded[0].signerType
            assertTrue(loadedType is SignerType.Remote)
            assertEquals(bunkerUri, loadedType.bunkerUri)
        }

    @Test
    fun `save view-only account roundtrip`() =
        runTest {
            val info = AccountInfo(npub = "npub1viewonly", signerType = SignerType.ViewOnly)
            storage.saveAccount(info)

            val loaded = storage.loadAccounts()
            assertEquals(1, loaded.size)
            assertEquals(SignerType.ViewOnly, loaded[0].signerType)
        }

    @Test
    fun `save multiple accounts`() =
        runTest {
            storage.saveAccount(AccountInfo("npub1a", SignerType.Internal))
            storage.saveAccount(AccountInfo("npub1b", SignerType.ViewOnly))
            storage.saveAccount(AccountInfo("npub1c", SignerType.Remote("bunker://x")))

            val loaded = storage.loadAccounts()
            assertEquals(3, loaded.size)
        }

    @Test
    fun `update existing account replaces it`() =
        runTest {
            storage.saveAccount(AccountInfo("npub1a", SignerType.Internal))
            storage.saveAccount(AccountInfo("npub1a", SignerType.ViewOnly))

            val loaded = storage.loadAccounts()
            assertEquals(1, loaded.size)
            assertEquals(SignerType.ViewOnly, loaded[0].signerType)
        }

    @Test
    fun `delete account removes it`() =
        runTest {
            storage.saveAccount(AccountInfo("npub1a", SignerType.Internal))
            storage.saveAccount(AccountInfo("npub1b", SignerType.ViewOnly))

            storage.deleteAccount("npub1a")

            val loaded = storage.loadAccounts()
            assertEquals(1, loaded.size)
            assertEquals("npub1b", loaded[0].npub)
        }

    @Test
    fun `delete active account falls back to first remaining`() =
        runTest {
            storage.saveAccount(AccountInfo("npub1a", SignerType.Internal))
            storage.saveAccount(AccountInfo("npub1b", SignerType.ViewOnly))
            storage.setCurrentAccount("npub1a")

            storage.deleteAccount("npub1a")

            assertEquals("npub1b", storage.currentAccount())
        }

    @Test
    fun `set and get current account`() =
        runTest {
            storage.saveAccount(AccountInfo("npub1a", SignerType.Internal))
            storage.setCurrentAccount("npub1a")

            assertEquals("npub1a", storage.currentAccount())
        }

    @Test
    fun `encryption key is generated and reused`() =
        runTest {
            storage.saveAccount(AccountInfo("npub1a", SignerType.Internal))
            storage.saveAccount(AccountInfo("npub1b", SignerType.ViewOnly))

            // Key should be saved once and reused
            coVerify(atMost = 1) {
                secureStorage.savePrivateKey("account-metadata-key", any())
            }
        }

    @Test
    fun `encrypted file is not readable as plaintext`() =
        runTest {
            storage.saveAccount(AccountInfo("npub1secret", SignerType.Internal))

            val file = File(File(tempDir, ".amethyst"), "accounts.json.enc")
            assertTrue(file.exists())

            val content = file.readText()
            // Encrypted content should NOT contain the npub in plaintext
            assertTrue(!content.contains("npub1secret"))
        }
}
