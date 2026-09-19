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
package com.vitorpamplona.amethyst.commons.cordn

import com.vitorpamplona.quartz.contextvm.cep04Encryption.CvmGiftWrap
import com.vitorpamplona.quartz.contextvm.fixture.CvmFixtureServer
import com.vitorpamplona.quartz.contextvm.fixture.InMemoryRelayPool
import com.vitorpamplona.quartz.contextvm.mcp.CvmMcpClient
import com.vitorpamplona.quartz.contextvm.transport.CvmTransport
import com.vitorpamplona.quartz.contextvm.transport.DualSigner
import com.vitorpamplona.quartz.cordn.fixture.CordnFixtureCoordinator
import com.vitorpamplona.quartz.cordn.groups.CordnCredential
import com.vitorpamplona.quartz.cordn.groups.CordnGroupPolicy
import com.vitorpamplona.quartz.cordn.spec00Coordinator.CoordinatorClient
import com.vitorpamplona.quartz.cordn.spec01GroupMetadata.CordnGroupMetadata
import com.vitorpamplona.quartz.mls.group.MlsGroup
import com.vitorpamplona.quartz.mls.messages.KeyPackageBundle
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Two accounts, one coordinator, and the **real** ContextVM stack between them.
 *
 * `CordnGroupManagerTest` drives the manager against a plain Kotlin object. That
 * proves its logic and nothing about the wire: no gift wrap, no relay, no
 * JSON-RPC framing, no CEP-16 `_meta`, no stable/ephemeral identity split. Every
 * one of those is a place the whole feature can be broken while that suite stays
 * green.
 *
 * This is the same lifecycle over `CvmTransport` → `InMemoryRelayPool` →
 * `CvmFixtureServer` → `CordnFixtureCoordinator`. The only thing standing in for
 * production is the relay (in memory) and the coordinator (ours, because the
 * reference one is unlicensed — see `quartz/plans/2026-09-17-cordn-interop.md`
 * §7). Everything between the manager and the relay is the shipped code.
 *
 * **Encryption is left at its default.** `CvmGiftWrap` defaults to
 * `EncryptionMode.REQUIRED` and §8.6 of that plan says it must; a test that
 * passed `DISABLED` for convenience would be testing a transport we do not ship.
 */
@OptIn(ExperimentalEncodingApi::class)
class CordnTransportIntegrationTest {
    private val gid = "6d1f0f6a-2a3e-4f2c-9a1d-7c6b5e4d3a21"

    private val relays = InMemoryRelayPool()
    private val serverSigner = NostrSignerInternal(KeyPair())
    private val coordinator = CordnFixtureCoordinator()

    private val config =
        CoordinatorConfig(
            pubKey = serverSigner.pubKey,
            relays = listOf(RelayUrlNormalizer.normalizeOrNull("wss://relay.example.com")!!),
        )

    /** One account: a stable identity, a per-session ephemeral one, and a manager. */
    private inner class Account {
        val stable = NostrSignerInternal(KeyPair())
        val ephemeral = NostrSignerInternal(KeyPair())
        val pubKey: HexKey get() = stable.pubKey

        val manager =
            CordnGroupManager(
                accountPubKey = stable.pubKey,
                config = config,
                coordinator =
                    CoordinatorClient(
                        CvmMcpClient(
                            CvmTransport(
                                relays = relays,
                                signers = DualSigner(stable, ephemeral),
                                serverPubKey = serverSigner.pubKey,
                                // Default mode: REQUIRED. Not overridden.
                                crypto = CvmGiftWrap(),
                            ),
                        ),
                    ),
                store = InMemoryCordnGroupStore(),
                clock = { 1_757_000_000L },
            )
    }

    private fun serve() =
        CvmFixtureServer(
            relays = relays,
            signer = serverSigner,
            crypto = CvmGiftWrap(),
            // CEP-16: the coordinator learns the caller from here, and the
            // identity-split assertions below depend on it being real.
            injectClientPubkey = true,
            handler = coordinator::handle,
        ).also { it.start() }

    /** Runs [block] while pumping the server, since a relay callback cannot suspend. */
    private suspend fun <T> driving(block: suspend () -> T): T =
        coroutineScope {
            val server = serve()
            val work = async { block() }
            var spins = 0
            while (!work.isCompleted) {
                yield()
                server.pump()
                yield()
                // A hung call would otherwise spin forever and look like a slow
                // test rather than a deadlock.
                check(++spins < 100_000) { "the fixture never answered — something is not replying" }
            }
            work.await()
        }

    /** A KeyPackage bundle for [account], published through the real transport. */
    private suspend fun publishKeyPackage(account: Account): Pair<KeyPackageBundle, String> {
        val scratch = MlsGroup.create(CordnCredential.of(account.pubKey).identity, policy = CordnGroupPolicy)
        val bundle = scratch.createKeyPackage(CordnCredential.of(account.pubKey).identity, ByteArray(0))
        val bytes = bundle.keyPackage.toTlsBytes()
        val ref = bytes.take(16).joinToString("") { b -> ((b.toInt() and 0xFF) + 0x100).toString(16).substring(1) }

        driving { account.manager.publishKeyPackage(ref, Base64.encode(bytes)) }
        return bundle to ref
    }

    @Test
    fun `alice invites bob over the wire, and bob joins and reads`() =
        runTest {
            val alice = Account()
            val bob = Account()

            // Bob publishes a KeyPackage. The coordinator keeps the signed
            // kind-25910 request event, because spec/00.md §7 has no KeyPackage
            // event kind and that request IS the publication payload.
            val (bobBundle, bobRef) = publishKeyPackage(bob)

            alice.manager.createGroup(gid, CordnGroupMetadata(name = "Over the wire", adminPubkeys = listOf(alice.pubKey)))

            // invite() takes Bob's KeyPackage back off the coordinator and runs
            // the §9 verification on the publication event before adding him.
            // That check only means something because the event round-tripped.
            val invite = driving { alice.manager.invite(gid, bob.pubKey) }
            assertEquals(bob.pubKey, invite.invited)
            assertEquals(1L, alice.manager.group(gid)!!.epoch)

            driving { alice.manager.send(gid, "hello over the wire") }

            val joined = driving { bob.manager.joinPendingWelcomes({ ref -> bobBundle.takeIf { ref == bobRef } }) }
            assertEquals(listOf(gid), joined.joined, "skipped: ${joined.skipped}")

            val delivered = mutableListOf<CordnGroupManager.Delivery>()
            driving { bob.manager.catchUp { delivered += it } }

            val messages = delivered.filterIsInstance<CordnGroupManager.Delivery.Message>()
            assertEquals(1, messages.size, "got ${delivered.map { it::class.simpleName }}")
            assertEquals("hello over the wire", messages[0].received.envelope.content)
            assertEquals(alice.pubKey, messages[0].received.sender)
        }

    @Test
    fun `nothing readable crosses the relay`() =
        runTest {
            // The claim §8 makes about content, checked against the actual bytes
            // on the actual wire rather than against the manager's intent.
            val alice = Account()
            alice.manager.createGroup(gid, CordnGroupMetadata(name = "Secret"))
            driving { alice.manager.send(gid, "a very distinctive plaintext") }

            assertTrue(relays.published.isNotEmpty(), "nothing was published — this test would pass vacuously")
            relays.published.forEach { event ->
                assertTrue(
                    !event.content.contains("a very distinctive plaintext"),
                    "message text reached the relay in the clear",
                )
                assertTrue(!event.content.contains(gid), "the group id reached the relay in the clear")
            }
        }

    @Test
    fun `the message path never names the account`() =
        runTest {
            // spec/00.md §8: msg_post, msg_fetch_many and kp_take ride a
            // throwaway key. A client that signed them with the account key
            // would work perfectly and tie every message to a real npub on a
            // server that keeps ordered history forever.
            val alice = Account()
            alice.manager.createGroup(gid, CordnGroupMetadata(name = "Quiet"))

            driving {
                alice.manager.send(gid, "one")
                alice.manager.catchUp { }
            }

            val messagePathCalls = coordinator.calls.filter { it.method in setOf("msg_post", "msg_fetch_many") }
            assertTrue(messagePathCalls.isNotEmpty())
            messagePathCalls.forEach {
                assertEquals(alice.ephemeral.pubKey, it.callerPubKey, "${it.method} must not be attributable to the account")
            }

            // ...while publishing a KeyPackage deliberately does name it (§8.4).
            driving { alice.manager.publishKeyPackage("ref", "a2s=") }
            val publish = assertNotNull(coordinator.calls.lastOrNull { it.method == "kp_publish" })
            assertEquals(alice.pubKey, publish.callerPubKey, "kp_publish is a signed record of this account, by design")
        }

    @Test
    fun `a welcome addressed to bob is invisible to alice`() =
        runTest {
            // welcome-delivery.md: the coordinator serves a Welcome to the member
            // it names. If it broadcast them, the join test above would pass for
            // the wrong reason and any account could join any group.
            val alice = Account()
            val bob = Account()
            val (_, bobRef) = publishKeyPackage(bob)

            alice.manager.createGroup(gid, CordnGroupMetadata(name = "Private"))
            driving { alice.manager.invite(gid, bob.pubKey) }

            val aliceSees = driving { alice.manager.joinPendingWelcomes({ null }) }
            assertTrue(aliceSees.joined.isEmpty())
            assertTrue(
                aliceSees.skipped.isEmpty(),
                "alice was offered a Welcome addressed to bob: ${aliceSees.skipped}",
            )

            // Bob is offered it, and only lacks the private half here.
            val bobSees = driving { bob.manager.joinPendingWelcomes({ null }) }
            assertEquals(1, bobSees.skipped.size)
            assertEquals(bobRef, bobSees.skipped.single().keyPackageRef)
        }
}
