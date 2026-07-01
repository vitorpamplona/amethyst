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
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Phase 1.1 of the launch-optimization plan: pin the ViewOnly cold-boot
 * state transition behavior the benchmark scenario depends on.
 *
 * Only the ViewOnly path is covered here. Internal + Remote (bunker) are
 * intentionally out of scope — they are exercised by the existing tests in
 * [AccountManagerStateTransitionTest] / [AccountManagerKeyLoginTest] /
 * [AccountManagerBunkerLoginTest].
 *
 * See desktopApp/plans/2026-06-17-feat-app-launch-optimization-plan.md § Phase 1.1.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountManagerLoadStateTransitionsTest {
    private lateinit var storage: SecureKeyStorage
    private lateinit var tempDir: File
    private lateinit var manager: AccountManager

    @BeforeTest
    fun setup() {
        storage = mockk(relaxed = true)
        coEvery { storage.getPrivateKey("account-metadata-key") } returns null
        tempDir = createTempDirectory("acctmgr-load-state").toFile()
        File(tempDir, ".amethyst").mkdirs()
        manager = AccountManager(storage, tempDir)
    }

    @AfterTest
    fun teardown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun viewOnlyAccountTransitionsThroughLoadingToLoggedIn() =
        runTest {
            val states = mutableListOf<AccountState>()
            val collector =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    manager.accountState.toList(states)
                }

            val keyPair = KeyPair()
            val npub = keyPair.pubKey.toNpub()
            manager.accountStorage.saveAccount(
                AccountInfo(npub = npub, signerType = SignerType.ViewOnly),
            )
            manager.accountStorage.setCurrentAccount(npub)

            val result = manager.loadSavedAccount()
            advanceUntilIdle()

            assertTrue(result.isSuccess, "loadSavedAccount should succeed for ViewOnly: $result")
            assertTrue(
                states.size >= 2,
                "Expected at least 2 state transitions, got ${states.size}: $states",
            )
            assertIs<AccountState.Loading>(states.first())
            val terminal = assertIs<AccountState.LoggedIn>(states.last())
            assertEquals(true, terminal.isReadOnly, "ViewOnly account must be flagged read-only")
            assertEquals(SignerType.ViewOnly, terminal.signerType)
            assertEquals(null, terminal.nsec, "ViewOnly account must not expose nsec")
            assertEquals(npub, terminal.npub)

            collector.cancel()
        }

    @Test
    fun corruptViewOnlyAccountFailsWithoutEmittingLoggedIn() =
        runTest {
            val states = mutableListOf<AccountState>()
            val collector =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    manager.accountState.toList(states)
                }

            // Persist a ViewOnly account with an undecodable npub. accounts.json.enc
            // round-trips the string as-is, so loadSavedAccount will hit the
            // decodePublicKeyAsHexOrNull failure branch in loadReadOnlyAccount.
            manager.accountStorage.saveAccount(
                AccountInfo(npub = "npub1notavalidbech32string", signerType = SignerType.ViewOnly),
            )
            manager.accountStorage.setCurrentAccount("npub1notavalidbech32string")

            val result = manager.loadSavedAccount()
            advanceUntilIdle()

            assertTrue(result.isFailure, "Corrupt ViewOnly npub must fail load")
            assertTrue(
                states.none { it is AccountState.LoggedIn },
                "No LoggedIn state must be emitted for corrupt account: $states",
            )

            collector.cancel()
        }
}
