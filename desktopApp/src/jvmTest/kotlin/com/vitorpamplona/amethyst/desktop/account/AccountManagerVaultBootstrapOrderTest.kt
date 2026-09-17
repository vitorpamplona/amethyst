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
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * The vault migration deletes the legacy `account-metadata-key` item. Any read of
 * accounts.json.enc that beats the migration therefore finds the key gone, mints a
 * fresh AES key over the one that decrypts the file, and wipes every account.
 *
 * `Main.kt` calls [AccountManager.refreshAccountListOnStartup] *before*
 * [AccountManager.loadSavedAccount], so hanging the migration off loadSavedAccount
 * alone is too late -- this was reproduced on macOS against a real Keychain: the
 * first launch migrated fine, the second launch wiped the account store.
 */
class AccountManagerVaultBootstrapOrderTest {
    private lateinit var storage: SecureKeyStorage
    private lateinit var tempDir: File
    private lateinit var manager: AccountManager
    private val keyStore = mutableMapOf<String, String>()

    @BeforeTest
    fun setup() {
        storage = mockk(relaxed = true)
        coEvery { storage.savePrivateKey(any(), any()) } answers { keyStore[firstArg()] = secondArg() }
        coEvery { storage.getPrivateKey(any()) } answers { keyStore[firstArg<String>()] }
        coEvery { storage.getPrivateKeyOrThrow(any()) } answers { keyStore[firstArg<String>()] }
        tempDir = createTempDirectory("acctmgr-vault-order").toFile()
        File(tempDir, ".amethyst").mkdirs()
        manager = AccountManager(storage, tempDir)
    }

    /**
     * An empty store short-circuits before it ever needs the metadata key, so seed a
     * real accounts.json.enc -- otherwise the ordering under test is never exercised.
     */
    private suspend fun seedAccountStore() {
        DesktopAccountStorage(storage, tempDir)
            .saveAccount(AccountInfo("npub1seeded", SignerType.Internal))
    }

    @AfterTest
    fun teardown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `startup list refresh migrates the vault before touching the account store`() =
        runTest {
            seedAccountStore()

            manager.refreshAccountListOnStartup()

            coVerifyOrder {
                storage.enableConsolidatedVault(listOf("account-metadata-key"))
                storage.getPrivateKeyOrThrow("account-metadata-key")
            }
        }

    @Test
    fun `phase one runs once even across both startup entry points`() =
        runTest {
            seedAccountStore()

            manager.refreshAccountListOnStartup()
            manager.refreshAccountList()
            runCatching { manager.loadSavedAccount() }

            coVerify(exactly = 1) {
                storage.enableConsolidatedVault(listOf("account-metadata-key"))
            }
        }
}
