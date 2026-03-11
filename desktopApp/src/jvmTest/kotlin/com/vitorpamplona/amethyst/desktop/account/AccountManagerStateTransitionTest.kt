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
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Tests verifying AccountManager state transitions during NIP-46 flows.
 * Reproduces Bug 1 UX issue — user should see ConnectingRelays while
 * the dedicated NIP-46 client is being set up.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountManagerStateTransitionTest {
    private lateinit var storage: SecureKeyStorage
    private lateinit var tempDir: File
    private lateinit var amethystDir: File
    private lateinit var manager: AccountManager

    @BeforeTest
    fun setup() {
        storage = mockk(relaxed = true)
        tempDir = createTempDirectory("acctmgr-state-test").toFile()
        amethystDir = File(tempDir, ".amethyst")
        amethystDir.mkdirs()
        manager = AccountManager(storage, tempDir)
    }

    @AfterTest
    fun teardown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun connectingRelaysThenLoggedInTransition() =
        runTest {
            val states = mutableListOf<AccountState>()
            val collector =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    manager.accountState.toList(states)
                }

            // Simulate Main.kt bunker restore flow
            manager.setConnectingRelays()

            // Then load an internal account (simulating successful load)
            val keyPair = KeyPair()
            val npub = keyPair.pubKey.toNpub()
            File(amethystDir, "last_account.txt").writeText(npub)
            coEvery { storage.getPrivateKey(npub) } returns keyPair.privKey!!.toHexKey()
            manager.loadSavedAccount()

            advanceUntilIdle()

            // Should see: LoggedOut → ConnectingRelays → LoggedIn
            assertTrue(
                states.size >= 3,
                "Expected at least 3 state transitions, got ${states.size}: $states",
            )
            assertIs<AccountState.LoggedOut>(states[0])
            assertIs<AccountState.ConnectingRelays>(states[1])
            assertIs<AccountState.LoggedIn>(states[2])

            collector.cancel()
        }

    @Test
    fun connectingRelaysThenFailureFallsBackToLoggedOut() =
        runTest {
            val states = mutableListOf<AccountState>()
            val collector =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    manager.accountState.toList(states)
                }

            // Simulate bunker restore that fails
            manager.setConnectingRelays()

            // loadSavedAccount fails → caller does logout
            val result = manager.loadSavedAccount() // no saved account
            assertTrue(result.isFailure)
            manager.logout()

            advanceUntilIdle()

            // Should see: LoggedOut → ConnectingRelays → LoggedOut
            assertTrue(
                states.size >= 3,
                "Expected at least 3 state transitions, got ${states.size}: $states",
            )
            assertIs<AccountState.LoggedOut>(states[0])
            assertIs<AccountState.ConnectingRelays>(states[1])
            assertIs<AccountState.LoggedOut>(states[2])

            collector.cancel()
        }
}
