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
import com.vitorpamplona.amethyst.commons.keystorage.SecureStorageException
import com.vitorpamplona.amethyst.commons.model.account.AccountInfo
import com.vitorpamplona.amethyst.commons.model.account.SignerType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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
        coEvery { secureStorage.getPrivateKeyOrThrow(capture(keySlot)) } answers {
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

    // --- Bug 1: silent AES key rotation ---

    @Test
    fun `getOrCreateKey keyring throws ambiguous error does not rotate key or touch file`() =
        runTest {
            // First launch: seed a real metadata key + an existing accounts.json.enc
            storage.saveAccount(AccountInfo("npub1existing", SignerType.Internal))
            val file = File(File(tempDir, ".amethyst"), "accounts.json.enc")
            val originalBytes = file.readBytes()
            val originalMetadataKey = keyStore["account-metadata-key"]
            assertNotNull(originalMetadataKey)

            // Fresh storage instance simulating a relaunch: the keychain now
            // returns an ambiguous error (user cancelled the Keychain dialog).
            val throwingStorage: SecureKeyStorage = mockk()
            coEvery { throwingStorage.getPrivateKeyOrThrow("account-metadata-key") } throws
                SecureStorageException("user cancelled Keychain dialog")
            // Legacy permissive read should still return the key; production
            // must not fall back to it on the getOrCreate path.
            coEvery { throwingStorage.getPrivateKey(any()) } answers { keyStore[firstArg()] }
            coEvery { throwingStorage.hasPrivateKey(any()) } answers { keyStore.containsKey(firstArg()) }

            val relaunched = DesktopAccountStorage(throwingStorage, tempDir)

            // Any operation that needs the metadata key must fail loudly, not
            // rotate the key or write a fresh empty file.
            assertFails { runBlocking { relaunched.loadAccounts() } }

            // Bug 1 invariant: no new savePrivateKey call for the metadata key.
            coVerify(exactly = 0) {
                throwingStorage.savePrivateKey("account-metadata-key", any())
            }
            // Bug 1 + Bug 2 invariant: on-disk ciphertext untouched.
            assertTrue(file.exists())
            assertContentEquals(originalBytes, file.readBytes())
            // Bug 1 invariant: keyStore metadata key unchanged.
            assertEquals(originalMetadataKey, keyStore["account-metadata-key"])
        }

    @Test
    fun `getOrCreateKey keyring returns definitive not-found creates and persists new key`() =
        runTest {
            // Happy path first launch: getPrivateKeyOrThrow returns null,
            // storage generates + persists a fresh AES key exactly once.
            assertNull(keyStore["account-metadata-key"])

            storage.saveAccount(AccountInfo("npub1first", SignerType.Internal))

            assertNotNull(keyStore["account-metadata-key"])
            coVerify(exactly = 1) {
                secureStorage.savePrivateKey("account-metadata-key", any())
            }
        }

    @Test
    fun `getOrCreateKey ambiguous error with no accounts file bootstraps a fresh key`() =
        runTest {
            // Every non-macOS backend java-keyring ships (Windows Credential Store,
            // Freedesktop Secret Service, KWallet) throws PasswordAccessException for a
            // *genuinely absent* credential, which the strict lookup surfaces as
            // SecureStorageException. With no accounts.json.enc there is no ciphertext
            // a new key could orphan, so a fresh install must still be able to mint one
            // -- otherwise Linux/Windows can never persist an account at all.
            val saved = mutableMapOf<String, String>()
            val throwingStorage: SecureKeyStorage = mockk()
            coEvery { throwingStorage.getPrivateKeyOrThrow("account-metadata-key") } throws
                SecureStorageException("Keyring backend refused access or returned ambiguous not-found")
            coEvery { throwingStorage.savePrivateKey(any(), any()) } answers {
                saved[firstArg()] = secondArg()
            }
            coEvery { throwingStorage.getPrivateKey(any()) } answers { saved[firstArg()] }
            coEvery { throwingStorage.hasPrivateKey(any()) } answers { saved.containsKey(firstArg()) }

            val file = File(File(tempDir, ".amethyst"), "accounts.json.enc")
            assertFalse(file.exists())

            val fresh = DesktopAccountStorage(throwingStorage, tempDir)
            fresh.saveAccount(AccountInfo("npub1freshinstall", SignerType.Internal))

            assertNotNull(saved["account-metadata-key"])
            assertTrue(file.exists())
            assertEquals(listOf("npub1freshinstall"), fresh.loadAccounts().map { it.npub })
            // The escape is bootstrap-only: once the file exists the strict contract
            // applies again -- pinned by `getOrCreateKey keyring throws ambiguous error
            // does not rotate key or touch file` above.
        }

    // --- Cache must never claim a state that was not persisted ---

    @Test
    fun `failed disk write does not poison the in-memory cache`() =
        runTest {
            storage.saveAccount(AccountInfo("npub1persisted", SignerType.Internal))
            assertEquals(listOf("npub1persisted"), storage.loadAccounts().map { it.npub })

            // Block the atomic-write temp path so writeMetadataToDisk fails.
            val temp = File(File(tempDir, ".amethyst"), "accounts.json.enc.tmp")
            assertTrue(temp.mkdirs())

            assertFails {
                runBlocking {
                    storage.saveAccount(AccountInfo("npub1phantom", SignerType.Internal))
                }
            }

            // Same instance: the cache must still reflect only what reached the disk,
            // not the account the failed save handed it.
            assertEquals(listOf("npub1persisted"), storage.loadAccounts().map { it.npub })

            // And the on-disk file agrees.
            temp.delete()
            val relaunched = DesktopAccountStorage(secureStorage, tempDir)
            assertEquals(listOf("npub1persisted"), relaunched.loadAccounts().map { it.npub })
        }

    // --- Bug 2: read failure must not silently reset the file ---

    @Test
    fun `readMetadataFromDisk transient IO error does not backup file`() =
        runTest {
            // Seed a real file we can inspect.
            storage.saveAccount(AccountInfo("npub1existing", SignerType.Internal))
            val amethystDir = File(tempDir, ".amethyst")
            val file = File(amethystDir, "accounts.json.enc")
            val originalBytes = file.readBytes()
            val originalName = file.name

            // Fresh storage that surfaces a transient error from the keychain.
            val throwingStorage: SecureKeyStorage = mockk()
            coEvery { throwingStorage.getPrivateKeyOrThrow("account-metadata-key") } throws
                SecureStorageException("transient keychain error")
            coEvery { throwingStorage.getPrivateKey(any()) } returns null
            coEvery { throwingStorage.hasPrivateKey(any()) } returns false

            val corruptions = mutableListOf<StorageCorruption>()
            val relaunched =
                DesktopAccountStorage(throwingStorage, tempDir, onCorruption = { corruptions += it })

            assertFails { runBlocking { relaunched.loadAccounts() } }

            // File preserved, no .corrupt.* or .jsonerror.* sibling created.
            assertTrue(file.exists())
            assertContentEquals(originalBytes, file.readBytes())
            val siblings = amethystDir.listFiles().orEmpty().map { it.name }
            assertFalse(siblings.any { it != originalName && it.startsWith("accounts.json.enc") && (it.contains(".corrupt.") || it.contains(".jsonerror.")) })

            // The callback fired with the transient subtype so the app can retry.
            assertTrue(corruptions.any { it is StorageCorruption.TransientError })
        }

    @Test
    fun `readMetadataFromDisk gcm tag mismatch backs up and resets`() =
        runTest {
            // Seed a valid file so we have a real metadata key in the mock keystore.
            storage.saveAccount(AccountInfo("npub1a", SignerType.Internal))
            val amethystDir = File(tempDir, ".amethyst")
            val file = File(amethystDir, "accounts.json.enc")
            assertTrue(file.exists())

            // Overwrite with random bytes that pass the length check but fail
            // GCM auth tag verification. Prefix with a fresh IV, then garbage.
            val garbage = ByteArray(64) { it.toByte() }
            file.writeBytes(garbage)

            val corruptions = mutableListOf<StorageCorruption>()
            val relaunched =
                DesktopAccountStorage(secureStorage, tempDir, onCorruption = { corruptions += it })

            val loaded = relaunched.loadAccounts()
            assertTrue(loaded.isEmpty())

            // Backup exists with the .corrupt.<ts> suffix; original file was
            // removed (and will be re-created on next save).
            val siblings = amethystDir.listFiles().orEmpty().map { it.name }
            assertTrue(siblings.any { it.startsWith("accounts.json.enc.corrupt.") })
            assertTrue(corruptions.any { it is StorageCorruption.FileCorrupted })
        }

    @Test
    fun `readMetadataFromDisk json malformed uses jsonerror suffix`() =
        runTest {
            // Build an accounts.json.enc whose plaintext decrypts fine but is
            // not the expected AccountMetadata shape. Easiest path: reuse the
            // production encrypt via a lightweight helper storage that lets us
            // control the plaintext.
            storage.saveAccount(AccountInfo("npub1a", SignerType.Internal))
            val amethystDir = File(tempDir, ".amethyst")
            val file = File(amethystDir, "accounts.json.enc")

            // Encrypt an unrelated JSON payload with the same AES key the mock
            // keystore holds so decryption succeeds but Jackson rejects the shape.
            val key =
                java.util.Base64
                    .getDecoder()
                    .decode(keyStore["account-metadata-key"]!!)
            val iv = ByteArray(12) { 7 }
            val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                javax.crypto.Cipher.ENCRYPT_MODE,
                javax.crypto.spec.SecretKeySpec(key, "AES"),
                javax.crypto.spec.GCMParameterSpec(128, iv),
            )
            val badPayload = cipher.doFinal("\"not an object\"".toByteArray())
            file.writeBytes(iv + badPayload)

            val corruptions = mutableListOf<StorageCorruption>()
            val relaunched =
                DesktopAccountStorage(secureStorage, tempDir, onCorruption = { corruptions += it })

            val loaded = relaunched.loadAccounts()
            assertTrue(loaded.isEmpty())

            val siblings = amethystDir.listFiles().orEmpty().map { it.name }
            assertTrue(siblings.any { it.startsWith("accounts.json.enc.jsonerror.") })
            assertTrue(corruptions.any { it is StorageCorruption.JsonMalformed })
        }

    // --- Bug 3: cross-process file lock ---

    @Test
    fun `writeMetadataToDisk concurrent saves serialize under file lock`() {
        val executor = Executors.newFixedThreadPool(4)
        try {
            runBlocking {
                withContext(executor.asCoroutineDispatcher()) {
                    val jobs =
                        (1..8).map { idx ->
                            async {
                                storage.saveAccount(
                                    AccountInfo(
                                        npub = "npub1parallel$idx",
                                        signerType = SignerType.Internal,
                                    ),
                                )
                            }
                        }
                    jobs.awaitAll()
                }
            }
            // All eight accounts present, file not truncated.
            val loaded = runBlocking { storage.loadAccounts() }
            assertEquals(8, loaded.size)
            val npubs = loaded.map { it.npub }.toSet()
            assertEquals((1..8).map { "npub1parallel$it" }.toSet(), npubs)

            // Lock sidecar exists and is respected.
            val lockFile = File(File(tempDir, ".amethyst"), "accounts.json.enc.lock")
            assertTrue(lockFile.exists())
        } finally {
            executor.shutdownNow()
        }
    }

    private fun assertContentEquals(
        expected: ByteArray,
        actual: ByteArray,
    ) {
        assertEquals(expected.size, actual.size, "byte size mismatch")
        for (i in expected.indices) {
            if (expected[i] != actual[i]) {
                throw AssertionError("byte differs at index $i: expected=${expected[i]} actual=${actual[i]}")
            }
        }
    }
}
