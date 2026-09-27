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
package com.vitorpamplona.quartz.cordn.sync

import com.vitorpamplona.quartz.contextvm.cep04Encryption.CvmGiftWrap
import com.vitorpamplona.quartz.contextvm.cep04Encryption.EncryptionMode
import com.vitorpamplona.quartz.contextvm.fixture.CvmFixtureServer
import com.vitorpamplona.quartz.contextvm.fixture.InMemoryRelayPool
import com.vitorpamplona.quartz.contextvm.mcp.CvmMcpClient
import com.vitorpamplona.quartz.contextvm.transport.CvmTransport
import com.vitorpamplona.quartz.contextvm.transport.DualSigner
import com.vitorpamplona.quartz.cordn.fixture.CordnFixtureCoordinator
import com.vitorpamplona.quartz.cordn.spec00Coordinator.CoordinatorClient
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Catch-up against a coordinator holding real history. */
class CordnGroupSyncTest {
    private val relays = InMemoryRelayPool()
    private val serverSigner = NostrSignerInternal(KeyPair())
    private val plaintext = CvmGiftWrap(encryptionMode = EncryptionMode.DISABLED)

    private fun sync(coordinator: CordnFixtureCoordinator): Pair<CordnGroupSync, CvmFixtureServer> {
        val signer = NostrSignerInternal(KeyPair())
        val client =
            CoordinatorClient(
                CvmMcpClient(
                    CvmTransport(
                        relays = relays,
                        signers = DualSigner(signer, signer),
                        serverPubKey = serverSigner.pubKey,
                        crypto = plaintext,
                    ),
                ),
            )
        val server =
            CvmFixtureServer(
                relays = relays,
                signer = serverSigner,
                crypto = plaintext,
                injectClientPubkey = true,
                handler = coordinator::handle,
            ).also { it.start() }
        return CordnGroupSync(client) to server
    }

    private suspend fun <T> driving(
        server: CvmFixtureServer,
        block: suspend () -> T,
    ): T =
        coroutineScope {
            val work = async { block() }
            while (!work.isCompleted) {
                yield()
                server.pump()
                yield()
            }
            work.await()
        }

    @Test
    fun `catch-up drains a group and stops when the page comes back empty`() =
        runTest {
            val coordinator = CordnFixtureCoordinator()
            repeat(5) { coordinator.seed("g1", "bXNnJGl0") }
            val (sync, server) = sync(coordinator)

            val seen = mutableListOf<Ingestion>()
            val drained = driving(server) { sync.catchUp(listOf("g1")) { _, i -> seen += i } }

            assertEquals(5, drained)
            assertEquals(5, seen.size)
            assertEquals(5L, sync.inbox("g1").cursor.lastCursor)
        }

    @Test
    fun `catch-up resumes from a persisted cursor`() =
        runTest {
            val coordinator = CordnFixtureCoordinator()
            coordinator.seed("g1", "b25l")
            val second = coordinator.seed("g1", "dHdv")
            coordinator.seed("g1", "dGhyZWU=")
            val (sync, server) = sync(coordinator)
            sync.restore("g1", GroupCursor(fetchCursor = second, lastCursor = second))

            val drained = driving(server) { sync.catchUp(listOf("g1")) { _, _ -> } }

            assertEquals(1, drained, "only the message after the persisted cursor")
        }

    @Test
    fun `catch-up drains several groups independently`() =
        runTest {
            // spec/00.md §4: cursors are per group. One shared cursor would
            // skip a quiet group's history the moment a busy one moved past it.
            val coordinator = CordnFixtureCoordinator()
            coordinator.seed("g1", "YQ==")
            coordinator.seed("g2", "Yg==")
            coordinator.seed("g1", "Yw==")
            val (sync, server) = sync(coordinator)

            val perGroup = mutableMapOf<String, Int>()
            driving(server) {
                sync.catchUp(listOf("g1", "g2")) { gid, _ -> perGroup[gid] = (perGroup[gid] ?: 0) + 1 }
            }

            assertEquals(2, perGroup["g1"])
            assertEquals(1, perGroup["g2"])
        }

    @Test
    fun `a posted commit is recognised as our own when it comes back`() =
        runTest {
            // The whole point of the pending-operation table, end to end: the
            // Commit we posted must come back as confirmation, never as work.
            val coordinator = CordnFixtureCoordinator()
            val (sync, server) = sync(coordinator)

            val seen = mutableListOf<Ingestion>()
            driving(server) {
                sync.postCommit("g1", "Y29tbWl0")
                sync.catchUp(listOf("g1")) { _, i -> seen += i }
            }

            assertIs<Ingestion.SelfEchoConfirmed>(seen.single())
            assertTrue(sync.unconfirmed().isEmpty(), "the pending operation is retired once confirmed")
        }

    @Test
    fun `a posted application message is not re-ingested as someone else's`() =
        runTest {
            val coordinator = CordnFixtureCoordinator()
            val (sync, server) = sync(coordinator)

            val seen = mutableListOf<Ingestion>()
            driving(server) {
                sync.postMessage("g1", "aGVsbG8=")
                sync.catchUp(listOf("g1")) { _, i -> seen += i }
            }

            assertIs<Ingestion.OwnMessage>(seen.single())
        }

    @Test
    fun `a skipped message does not come back on the next catch-up`() =
        runTest {
            // The stall this prevents is invisible in a single pass: every
            // outcome looks right, and only the SECOND catch-up reveals that
            // the cursor never moved past the messages we chose not to process.
            // In production that is a group that re-delivers its own echoes
            // forever and never reaches the traffic behind them.
            val coordinator = CordnFixtureCoordinator()
            val (sync, server) = sync(coordinator)

            driving(server) {
                sync.postCommit("g1", "Y29tbWl0")
                sync.postMessage("g1", "aGVsbG8=")
                sync.catchUp(listOf("g1")) { _, _ -> }
            }

            val second = driving(server) { sync.catchUp(listOf("g1")) { _, _ -> } }
            assertEquals(0, second, "both skipped messages must be behind the cursor")
        }

    @Test
    fun `an unconfirmed commit stays listed until its echo arrives`() =
        runTest {
            val coordinator = CordnFixtureCoordinator()
            val (sync, server) = sync(coordinator)

            driving(server) { sync.postCommit("g1", "Y29tbWl0") }

            assertEquals(listOf("Y29tbWl0"), sync.unconfirmed()["g1"]?.map { it.sealedBase64 })
        }

    @Test
    fun `cursors survive a round trip through persistence`() =
        runTest {
            val coordinator = CordnFixtureCoordinator()
            repeat(3) { coordinator.seed("g1", "eA==") }
            val (sync, server) = sync(coordinator)
            driving(server) { sync.catchUp(listOf("g1")) { _, _ -> } }

            val saved = sync.cursors()
            val (resumed, server2) = sync(coordinator)
            saved.forEach { (gid, cursor) -> resumed.restore(gid, cursor) }

            val drained = driving(server2) { resumed.catchUp(listOf("g1")) { _, _ -> } }
            assertEquals(0, drained, "a resumed client re-reads nothing")
        }
}
