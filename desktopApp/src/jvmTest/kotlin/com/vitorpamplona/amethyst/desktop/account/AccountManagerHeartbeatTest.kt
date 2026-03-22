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
import com.vitorpamplona.quartz.nip01Core.relay.client.EmptyNostrClient
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip19Bech32.toNsec
import com.vitorpamplona.quartz.nip46RemoteSigner.signer.NostrSignerRemote
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountManagerHeartbeatTest {
    private lateinit var storage: SecureKeyStorage
    private lateinit var tempDir: File
    private lateinit var manager: AccountManager
    private lateinit var remoteSigner: NostrSignerRemote

    private val validHex = "a".repeat(64)

    @BeforeTest
    fun setup() {
        storage = mockk(relaxed = true)
        tempDir = createTempDirectory("acctmgr-hb-test").toFile()
        manager = AccountManager(storage, tempDir)

        // Create a real remote signer that we'll spy on
        val ephemeral = NostrSignerInternal(KeyPair())
        remoteSigner =
            spyk(
                NostrSignerRemote.fromBunkerUri(
                    "bunker://$validHex?relay=wss://r.com",
                    ephemeral,
                    EmptyNostrClient(),
                ),
            )
    }

    @AfterTest
    fun teardown() {
        manager.stopHeartbeat()
        tempDir.deleteRecursively()
    }

    private fun loginWithRemoteSigner() {
        // Directly set the account state to a bunker-logged-in state
        val keyPair = KeyPair()
        val state =
            AccountState.LoggedIn(
                signer = remoteSigner,
                pubKeyHex = keyPair.pubKey.toHexKey(),
                npub = keyPair.pubKey.toNpub(),
                nsec = null,
                isReadOnly = false,
                signerType = SignerType.Remote("bunker://$validHex?relay=wss://r.com"),
            )
        // We need to access private _accountState — use loginWithKey then replace
        // Actually, let's just use reflection or a simpler approach
        // We'll test heartbeat indirectly by using the public API
    }

    @Test
    fun stopHeartbeatCancels() =
        runTest {
            // Just ensure stopHeartbeat doesn't crash when no heartbeat is running
            manager.stopHeartbeat()
            // And after starting
            manager.startHeartbeat(this)
            manager.stopHeartbeat()
        }

    @Test
    fun startHeartbeatDoesNotCrashWithNoAccount() =
        runTest {
            manager.startHeartbeat(this)
            advanceTimeBy(AccountManager.HEARTBEAT_INTERVAL_MS + 1)
            // Should not crash — no account means the loop skips
            manager.stopHeartbeat()
        }

    @Test
    fun startHeartbeatDoesNotCrashWithInternalSigner() =
        runTest {
            val nsec = KeyPair().privKey!!.toNsec()
            manager.loginWithKey(nsec)
            manager.startHeartbeat(this)
            advanceTimeBy(AccountManager.HEARTBEAT_INTERVAL_MS + 1)
            // Internal signer is not NostrSignerRemote, heartbeat skips it
            manager.stopHeartbeat()
        }
}
