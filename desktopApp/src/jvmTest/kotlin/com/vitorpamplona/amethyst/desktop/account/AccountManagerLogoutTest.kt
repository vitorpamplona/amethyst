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
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip19Bech32.toNsec
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class AccountManagerLogoutTest {
    private lateinit var storage: SecureKeyStorage
    private lateinit var tempDir: File
    private lateinit var manager: AccountManager

    @BeforeTest
    fun setup() {
        storage = mockk(relaxed = true)
        tempDir = createTempDirectory("acctmgr-logout-test").toFile()
        manager = AccountManager(storage, tempDir)
    }

    @AfterTest
    fun teardown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun logoutTransitionsToLoggedOut() =
        runTest {
            val nsec = KeyPair().privKey!!.toNsec()
            manager.loginWithKey(nsec)
            manager.logout()
            assertIs<AccountState.LoggedOut>(manager.accountState.value)
        }

    @Test
    fun logoutDeleteKeyCallsDeletePrivateKey() =
        runTest {
            val nsec = KeyPair().privKey!!.toNsec()
            manager.loginWithKey(nsec)
            manager.logout(deleteKey = true)
            coVerify { storage.deletePrivateKey(any()) }
        }

    @Test
    fun logoutWithoutDeleteKeyDoesNotDelete() =
        runTest {
            val nsec = KeyPair().privKey!!.toNsec()
            manager.loginWithKey(nsec)
            manager.logout(deleteKey = false)
            coVerify(exactly = 0) { storage.deletePrivateKey(any()) }
        }

    @Test
    fun forceLogoutWithReasonSetsReason() =
        runTest {
            val nsec = KeyPair().privKey!!.toNsec()
            manager.loginWithKey(nsec)
            manager.forceLogoutWithReason("test reason")
            assertEquals("test reason", manager.forceLogoutReason.value)
        }

    @Test
    fun forceLogoutWithReasonLogsOut() =
        runTest {
            val nsec = KeyPair().privKey!!.toNsec()
            manager.loginWithKey(nsec)
            manager.forceLogoutWithReason("test")
            assertIs<AccountState.LoggedOut>(manager.accountState.value)
        }

    @Test
    fun clearForceLogoutReason() =
        runTest {
            val nsec = KeyPair().privKey!!.toNsec()
            manager.loginWithKey(nsec)
            manager.forceLogoutWithReason("test")
            manager.clearForceLogoutReason()
            assertNull(manager.forceLogoutReason.value)
        }

    @Test
    fun logoutResetsSignerConnectionState() =
        runTest {
            val nsec = KeyPair().privKey!!.toNsec()
            manager.loginWithKey(nsec)
            manager.logout()
            assertIs<SignerConnectionState.NotRemote>(manager.signerConnectionState.value)
        }
}
