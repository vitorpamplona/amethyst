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

import com.vitorpamplona.quartz.cordn.spec01GroupMetadata.CordnGroupMetadata
import com.vitorpamplona.quartz.mls.messages.KeyPackageBundle
import kotlinx.coroutines.test.runTest
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
class CordnTransportIntegrationTest : CordnTransportHarness() {
    private val gid = "6d1f0f6a-2a3e-4f2c-9a1d-7c6b5e4d3a21"

    /** A KeyPackage bundle for [account], published through the real transport. */
    private suspend fun publishKeyPackage(account: Account): Pair<KeyPackageBundle, String> {
        val published = driving { account.keyPackages.publishNew() }
        val bundle = account.keyPackages.bundleFor(published.keyPackageRef)!!
        return bundle to published.keyPackageRef
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
