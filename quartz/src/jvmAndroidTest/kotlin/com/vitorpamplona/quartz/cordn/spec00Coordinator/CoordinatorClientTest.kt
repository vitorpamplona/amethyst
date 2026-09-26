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
package com.vitorpamplona.quartz.cordn.spec00Coordinator

import com.vitorpamplona.quartz.contextvm.cep04Encryption.CvmGiftWrap
import com.vitorpamplona.quartz.contextvm.cep04Encryption.EncryptionMode
import com.vitorpamplona.quartz.contextvm.fixture.CvmFixtureServer
import com.vitorpamplona.quartz.contextvm.fixture.InMemoryRelayPool
import com.vitorpamplona.quartz.contextvm.mcp.CvmMcpClient
import com.vitorpamplona.quartz.contextvm.transport.CvmTransport
import com.vitorpamplona.quartz.contextvm.transport.DualSigner
import com.vitorpamplona.quartz.cordn.fixture.CordnFixtureCoordinator
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The coordinator client against an in-memory coordinator.
 *
 * The interesting assertions are not "the call returned" but "the coordinator
 * learned only what `spec/00.md` §8 says it learns" — which is checkable only
 * by standing on the coordinator's side of the wire, which is what
 * [CordnFixtureCoordinator] is for.
 */
class CoordinatorClientTest {
    private val relays = InMemoryRelayPool()
    private val serverSigner = NostrSignerInternal(KeyPair())
    private val stableSigner = NostrSignerInternal(KeyPair())
    private val ephemeralSigner = NostrSignerInternal(KeyPair())
    private val plaintext = CvmGiftWrap(encryptionMode = EncryptionMode.DISABLED)

    private fun client() =
        CoordinatorClient(
            CvmMcpClient(
                CvmTransport(
                    relays = relays,
                    signers = DualSigner(stableSigner, ephemeralSigner),
                    serverPubKey = serverSigner.pubKey,
                    crypto = plaintext,
                ),
            ),
        )

    private fun serve(coordinator: CordnFixtureCoordinator) =
        CvmFixtureServer(
            relays = relays,
            signer = serverSigner,
            crypto = plaintext,
            injectClientPubkey = true,
            handler = coordinator::handle,
        ).also { it.start() }

    /** Runs [block] while pumping the fixture, since the relay callback cannot suspend. */
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
    fun `the message path never touches the stable identity`() =
        runTest {
            // The privacy claim of spec/00.md §8, checked from the coordinator's
            // side. A client that signed msg_post with the account key would
            // still work perfectly -- and would tie every message to a real
            // npub on a server that keeps ordered history forever.
            val coordinator = CordnFixtureCoordinator()
            val server = serve(coordinator)
            val client = client()

            driving(server) {
                client.postMessage("group-1", "c2VhbGVk")
                client.fetchMessages(mapOf("group-1" to null))
                client.listKeyPackages()
            }

            assertTrue(coordinator.calls.isNotEmpty())
            coordinator.calls.forEach {
                assertEquals(
                    ephemeralSigner.pubKey,
                    it.callerPubKey,
                    "${it.method} must not be attributable to the account identity",
                )
            }
        }

    @Test
    fun `publishing and joining are attributable, because they name you by design`() =
        runTest {
            // The other half. These are not leaks to be fixed: a KeyPackage
            // publication that did not name its owner would bind nothing, and a
            // join request that did not name the asker could not be acted on.
            val coordinator = CordnFixtureCoordinator()
            val server = serve(coordinator)
            val client = client()

            driving(server) {
                client.publishKeyPackage("ref-1", "a2V5")
                client.storeJoinRequest("group-1", "ref-1")
                client.takeWelcomes()
            }

            coordinator.calls.forEach {
                assertEquals(stableSigner.pubKey, it.callerPubKey, "${it.method} must name the account")
            }
        }

    @Test
    fun `a coordinator rejection surfaces as an exception, not an empty result`() =
        runTest {
            val coordinator = CordnFixtureCoordinator(rejectPublication = true)
            val server = serve(coordinator)
            val client = client()

            assertFailsWith<CoordinatorException> {
                driving(server) { client.publishKeyPackage("ref-1", "a2V5") }
            }
        }

    @Test
    fun `fetch returns a group's messages after its cursor`() =
        runTest {
            val coordinator = CordnFixtureCoordinator()
            coordinator.seed("group-1", "b25l")
            val second = coordinator.seed("group-1", "dHdv")
            coordinator.seed("group-1", "dGhyZWU=")
            coordinator.seed("group-2", "b3RoZXI=")
            val server = serve(coordinator)
            val client = client()

            val page = driving(server) { client.fetchMessages(mapOf("group-1" to second)) }

            assertEquals(listOf("dGhyZWU="), page.map { it.sealedBase64 })
            assertEquals("group-1", page.single().gid, "another group's stream must not leak into this one")
        }

    @Test
    fun `a first fetch omits the cursor rather than sending zero`() =
        runTest {
            // The schema types `after` as a positive int, so 0 is not "from the
            // beginning" -- it is out of range, and a coordinator validating its
            // own contract would reject the call.
            val coordinator = CordnFixtureCoordinator()
            coordinator.seed("group-1", "b25l")
            val server = serve(coordinator)
            val client = client()

            val page = driving(server) { client.fetchMessages(mapOf("group-1" to null)) }
            assertEquals(1, page.size)
        }

    @Test
    fun `taking a key package nobody published returns null`() =
        runTest {
            val coordinator = CordnFixtureCoordinator()
            val server = serve(coordinator)
            val client = client()

            assertNull(driving(server) { client.takeKeyPackage("nobody") })
        }

    @Test
    fun `posting reports the cursor the coordinator assigned`() =
        runTest {
            val coordinator = CordnFixtureCoordinator()
            val server = serve(coordinator)
            val client = client()

            val posted = driving(server) { client.postMessage("group-1", "c2VhbGVk") }

            assertEquals("group-1", posted.gid)
            assertTrue(posted.cursor > 0)
            assertEquals(listOf("c2VhbGVk"), coordinator.posted("group-1"))
        }

    @Test
    fun `welcomes carry the resume cursor when the inviter set one`() =
        runTest {
            // Without it a joiner replays a group's whole history, all of it
            // sealed under epochs it has no key for.
            val coordinator = CordnFixtureCoordinator()
            coordinator.seedWelcome("ref-1", "d2VsY29tZQ==", after = 42, targetPubKey = stableSigner.pubKey)
            val server = serve(coordinator)
            val client = client()

            val welcomes = driving(server) { client.takeWelcomes() }
            assertEquals(42L, welcomes.single().after)
        }
}
