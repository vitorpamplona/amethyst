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

import com.vitorpamplona.quartz.cordn.groups.CordnCredential
import com.vitorpamplona.quartz.cordn.groups.CordnGroupPolicy
import com.vitorpamplona.quartz.cordn.spec01GroupMetadata.CordnGroupMetadata
import com.vitorpamplona.quartz.mls.group.MlsGroup
import com.vitorpamplona.quartz.mls.messages.KeyPackageBundle
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [CordnGroupManager] over a full two-party lifecycle.
 *
 * Alice creates a group, takes Bob's published KeyPackage off the coordinator,
 * adds him, and sends a message; Bob joins from the Welcome the coordinator
 * held for him, catches up, and reads it. Both managers run against one
 * [FakeCoordinator], so the only thing joining them is the wire — which is the
 * point: a cursor or epoch mistake shows up as Bob failing to read, not as an
 * assertion about internals.
 */
@OptIn(ExperimentalEncodingApi::class)
class CordnGroupManagerTest {
    private val gid = "6d1f0f6a-2a3e-4f2c-9a1d-7c6b5e4d3a21"
    private val coordinatorKey = "cc".repeat(32)

    private val aliceSigner = NostrSignerSync(KeyPair())
    private val bobSigner = NostrSignerSync(KeyPair())
    private val alice: HexKey get() = aliceSigner.pubKey
    private val bob: HexKey get() = bobSigner.pubKey

    private val config =
        CoordinatorConfig(
            pubKey = coordinatorKey,
            relays = listOf(RelayUrlNormalizer.normalizeOrNull("wss://relay.example.com")!!),
            origin = CoordinatorConfig.Origin.DEFAULT,
        )

    private fun manager(
        account: HexKey,
        coordinator: FakeCoordinator,
        store: CordnGroupStore = InMemoryCordnGroupStore(),
    ) = CordnGroupManager(
        accountPubKey = account,
        config = config,
        coordinator = coordinator,
        store = store,
        clock = { 1_757_000_000L },
    )

    /**
     * Bob's KeyPackage plus the signed `kp_publish` request event that binds it
     * to him — `spec/00.md` §7's "signed publication payload", which for cordn
     * *is* the ContextVM request event. Built here rather than faked because
     * `invite` verifies it, and a fake would verify nothing.
     */
    private fun bobsPublication(): Pair<KeyPackageBundle, FakeCoordinator.StoredKeyPackage> {
        val scratch = MlsGroup.create(CordnCredential.of(bob).identity, policy = CordnGroupPolicy)
        val bundle = scratch.createKeyPackage(CordnCredential.of(bob).identity, ByteArray(0))
        val bytes = bundle.keyPackage.toTlsBytes()
        val base64 = Base64.encode(bytes)
        val ref =
            bundle.keyPackage
                .toTlsBytes()
                .take(16)
                .joinToString("") { "%02x".format(it) }

        val content =
            """{"jsonrpc":"2.0","id":1,"method":"tools/call",""" +
                """"params":{"name":"kp_publish","arguments":{"kp_ref":"$ref","kp_64":"$base64"}}}"""
        val event: Event =
            bobSigner.sign(
                EventTemplate(
                    createdAt = 1_757_000_000L,
                    kind = 25910,
                    tags = arrayOf(arrayOf("p", coordinatorKey)),
                    content = content,
                ),
            )

        return bundle to FakeCoordinator.StoredKeyPackage(bob, ref, base64, event)
    }

    /** As [bobsPublication], for a third member. */
    private fun carolsPublication(): Pair<KeyPackageBundle, FakeCoordinator.StoredKeyPackage> {
        val carolSigner = NostrSignerSync(KeyPair())
        val carol = carolSigner.pubKey
        val scratch = MlsGroup.create(CordnCredential.of(carol).identity, policy = CordnGroupPolicy)
        val bundle = scratch.createKeyPackage(CordnCredential.of(carol).identity, ByteArray(0))
        val base64 = Base64.encode(bundle.keyPackage.toTlsBytes())
        val ref = "ca".repeat(16)
        val content =
            """{"jsonrpc":"2.0","id":1,"method":"tools/call",""" +
                """"params":{"name":"kp_publish","arguments":{"kp_ref":"$ref","kp_64":"$base64"}}}"""
        val event: Event =
            carolSigner.sign(
                EventTemplate(
                    createdAt = 1_757_000_000L,
                    kind = 25910,
                    tags = arrayOf(arrayOf("p", coordinatorKey)),
                    content = content,
                ),
            )
        return bundle to FakeCoordinator.StoredKeyPackage(carol, ref, base64, event)
    }

    @Test
    fun `alice creates, invites, sends, and bob joins and reads`() =
        runTest {
            val coordinator = FakeCoordinator(callerPubKey = alice)
            val aliceManager = manager(alice, coordinator)
            val (bobBundle, stored) = bobsPublication()
            coordinator.seedKeyPackage(stored)

            aliceManager.createGroup(gid, CordnGroupMetadata(name = "Stage 4", adminPubkeys = listOf(alice)))
            assertEquals(setOf(gid), aliceManager.gids.value)

            val invite = aliceManager.invite(gid, bob)
            assertEquals(bob, invite.invited)
            assertEquals(1L, aliceManager.group(gid)!!.epoch, "adding a member advances the epoch")

            aliceManager.send(gid, "hello bob")

            // Bob's side, from nothing but what the coordinator holds.
            val bobCoordinator = FakeCoordinator(callerPubKey = bob)
            bobCoordinator.welcomes.putAll(coordinator.welcomes)
            bobCoordinator.streams.putAll(coordinator.streams)
            val bobManager = manager(bob, bobCoordinator)

            val results = bobManager.joinPendingWelcomes({ ref -> bobBundle.takeIf { ref == stored.keyPackageRef } })
            assertEquals(listOf(gid), results.joined, "Bob recovers the gid from the group id")
            assertTrue(results.skipped.isEmpty())

            val delivered = mutableListOf<CordnGroupManager.Delivery>()
            bobManager.catchUp { delivered += it }

            val messages = delivered.filterIsInstance<CordnGroupManager.Delivery.Message>()
            assertEquals(1, messages.size, "got ${delivered.map { it::class.simpleName }}")
            assertEquals("hello bob", messages[0].received.envelope.content)
            assertEquals(alice, messages[0].received.sender, "the sender is MLS-authenticated, not claimed")
            assertEquals(CordnGroupManager.CHAT_KIND, messages[0].received.envelope.kind)
        }

    @Test
    fun `the welcome cursor keeps bob from replaying epochs he cannot read`() =
        runTest {
            // Without `after`, catch-up starts at 0 and hands Bob the Commit
            // that created him plus everything before it -- all Undecryptable,
            // and all indistinguishable from real loss.
            val coordinator = FakeCoordinator(callerPubKey = alice)
            val aliceManager = manager(alice, coordinator)
            val (bobBundle, stored) = bobsPublication()
            coordinator.seedKeyPackage(stored)

            aliceManager.createGroup(gid, CordnGroupMetadata(name = "Stage 4"))
            aliceManager.send(gid, "before bob existed")
            aliceManager.invite(gid, bob)
            aliceManager.send(gid, "after bob joined")

            val bobCoordinator = FakeCoordinator(callerPubKey = bob)
            bobCoordinator.welcomes.putAll(coordinator.welcomes)
            bobCoordinator.streams.putAll(coordinator.streams)
            val bobManager = manager(bob, bobCoordinator)
            bobManager.joinPendingWelcomes({ bobBundle.takeIf { _ -> true } })

            val delivered = mutableListOf<CordnGroupManager.Delivery>()
            bobManager.catchUp { delivered += it }

            val messages = delivered.filterIsInstance<CordnGroupManager.Delivery.Message>()
            assertEquals(listOf("after bob joined"), messages.map { it.received.envelope.content })
            assertTrue(
                delivered.none { it is CordnGroupManager.Delivery.Undecryptable },
                "nothing from before Bob's epoch should have been offered: ${delivered.map { it::class.simpleName }}",
            )
        }

    @Test
    fun `an existing member applies a later commit, sealed under the pre-commit epoch`() =
        runTest {
            // `spec/03.md` §5: a Commit is sealed with the exporter of the
            // epoch it LEAVES, because that is the only key its recipients
            // have. Sealing under the epoch it creates produces a payload the
            // sender can read and nobody else can -- and it cannot be caught
            // by the join path, where the Welcome's cursor skips the Commit
            // that created the joiner. It needs a member already in the group
            // when a later Commit lands, which is what this is.
            val coordinator = FakeCoordinator(callerPubKey = alice)
            val aliceManager = manager(alice, coordinator)
            val (bobBundle, stored) = bobsPublication()
            coordinator.seedKeyPackage(stored)

            aliceManager.createGroup(gid, CordnGroupMetadata(name = "Three"))
            aliceManager.invite(gid, bob)

            val bobCoordinator = FakeCoordinator(callerPubKey = bob)
            bobCoordinator.welcomes.putAll(coordinator.welcomes)
            bobCoordinator.streams.putAll(coordinator.streams)
            val bobManager = manager(bob, bobCoordinator)
            bobManager.joinPendingWelcomes({ bobBundle })
            bobManager.catchUp { }
            assertEquals(1L, bobManager.group(gid)!!.epoch)

            // Carol arrives. Bob was already here, so the Commit is his to apply.
            val (_, carolStored) = carolsPublication()
            coordinator.seedKeyPackage(carolStored)
            aliceManager.invite(gid, carolStored.pubKey)
            aliceManager.send(gid, "carol is in")

            bobCoordinator.streams.clear()
            bobCoordinator.streams.putAll(coordinator.streams)
            val delivered = mutableListOf<CordnGroupManager.Delivery>()
            bobManager.catchUp { delivered += it }

            assertTrue(
                delivered.none { it is CordnGroupManager.Delivery.Undecryptable },
                "Bob could not open a Commit addressed to his own epoch: " +
                    delivered.filterIsInstance<CordnGroupManager.Delivery.Undecryptable>().map { it.reason },
            )
            assertEquals(2L, bobManager.group(gid)!!.epoch, "the Commit must have advanced Bob's epoch")
            assertEquals(
                listOf("carol is in"),
                delivered.filterIsInstance<CordnGroupManager.Delivery.Message>().map { it.received.envelope.content },
                "and the message sent at the new epoch must still open",
            )
        }

    @Test
    fun `a group survives a restart`() =
        runTest {
            val coordinator = FakeCoordinator(callerPubKey = alice)
            val store = InMemoryCordnGroupStore()
            val first = manager(alice, coordinator, store)
            first.createGroup(gid, CordnGroupMetadata(name = "Persisted"))
            first.send(gid, "one")

            val restarted = manager(alice, coordinator, store)
            restarted.restore()

            assertEquals(setOf(gid), restarted.gids.value)
            assertEquals("Persisted", CordnGroupMetadata.fromExtensions(restarted.group(gid)!!.extensions)?.name)

            // The cursor came back too, so our own message is not re-delivered
            // as somebody else's on the next catch-up.
            val delivered = mutableListOf<CordnGroupManager.Delivery>()
            restarted.catchUp { delivered += it }
            assertTrue(
                delivered.none { it is CordnGroupManager.Delivery.Message },
                "a restored cursor must not replay our own history: ${delivered.map { it::class.simpleName }}",
            )
        }

    @Test
    fun `inviting someone the coordinator has no KeyPackage for fails loudly`() =
        runTest {
            val coordinator = FakeCoordinator(callerPubKey = alice)
            val aliceManager = manager(alice, coordinator)
            aliceManager.createGroup(gid, CordnGroupMetadata(name = "Stage 4"))

            val failure = assertFailsWith<CordnGroupException> { aliceManager.invite(gid, bob) }
            assertTrue(failure.message!!.contains("holds no KeyPackage"))
            assertEquals(0L, aliceManager.group(gid)!!.epoch, "a failed invite must not advance the group")
        }

    @Test
    fun `a KeyPackage published under someone else's name is refused`() =
        runTest {
            // §9/§10: the coordinator checks this too, and we check it anyway.
            // A coordinator that skipped the check could otherwise hand us any
            // account's name over any account's key material.
            val coordinator = FakeCoordinator(callerPubKey = alice)
            val aliceManager = manager(alice, coordinator)
            aliceManager.createGroup(gid, CordnGroupMetadata(name = "Stage 4"))

            val (_, stored) = bobsPublication()
            val impostor = "ee".repeat(32)
            coordinator.seedKeyPackage(
                FakeCoordinator.StoredKeyPackage(impostor, stored.keyPackageRef, stored.base64, stored.publicationEvent),
            )

            assertFailsWith<Exception> { aliceManager.invite(gid, impostor) }
            assertEquals(0L, aliceManager.group(gid)!!.epoch)
        }

    @Test
    fun `health tracks the coordinator without polling it`() =
        runTest {
            val coordinator = FakeCoordinator(callerPubKey = alice)
            val aliceManager = manager(alice, coordinator)
            aliceManager.createGroup(gid, CordnGroupMetadata(name = "Stage 4"))

            assertTrue(aliceManager.health.state.value.isUnknown, "no call yet is not the same as down")

            aliceManager.send(gid, "works")
            assertEquals(0, aliceManager.health.state.value.consecutiveFailures)

            coordinator.failNext = CoordinatorHealth.DOWN_AFTER
            repeat(CoordinatorHealth.DOWN_AFTER) {
                runCatching { aliceManager.send(gid, "fails") }
            }
            assertTrue(aliceManager.health.state.value.isDown)

            aliceManager.send(gid, "works again")
            assertEquals(0, aliceManager.health.state.value.consecutiveFailures, "a success clears the streak")
            assertTrue(!aliceManager.health.state.value.isDown)
        }

    @Test
    fun `exposure reports the real linked-group count`() =
        runTest {
            // Section 8.2 is the one a user cannot guess: one ephemeral session
            // per coordinator means every group on it is linked to the others.
            val coordinator = FakeCoordinator(callerPubKey = alice)
            val aliceManager = manager(alice, coordinator)
            aliceManager.createGroup(gid, CordnGroupMetadata(name = "One"))

            val alone = aliceManager.exposure(gid)
            assertEquals(1, alone.linkedGroupCount)
            assertTrue(ExposureNote.GROUPS_LINKED_BY_SESSION !in alone.notes())
            assertEquals(ExposureLevel.NONE, alone.content)
            assertEquals(ExposureLevel.IDENTIFIED, alone.membership)
            assertTrue(ExposureNote.ENCRYPTION_NOT_PINNED !in alone.notes(), "our transport pins REQUIRED (§8.6)")

            aliceManager.createGroup("second-gid", CordnGroupMetadata(name = "Two"))
            val linked = aliceManager.exposure(gid)
            assertEquals(2, linked.linkedGroupCount)
            assertTrue(ExposureNote.GROUPS_LINKED_BY_SESSION in linked.notes())

            assertTrue(ExposureNote.PUBLICATION_IS_A_SIGNED_RECORD !in linked.notes())
            aliceManager.publishKeyPackage("ref", "a2s=")
            assertTrue(ExposureNote.PUBLICATION_IS_A_SIGNED_RECORD in aliceManager.exposure(gid).notes(), "§8.4")
        }

    @Test
    fun `a share ref round-trips through this coordinator`() =
        runTest {
            val coordinator = FakeCoordinator(callerPubKey = alice)
            val aliceManager = manager(alice, coordinator)
            aliceManager.createGroup(gid, CordnGroupMetadata(name = "Shared"))

            val ref = aliceManager.shareRef(gid)
            assertEquals(gid, ref.gid)
            assertEquals(coordinatorKey, ref.coordinatorPubKey)
            assertEquals(CoordinatorConfig.from(ref)?.pubKey, coordinatorKey)
            assertNull(aliceManager.group("nope"))
        }

    @Test
    fun `a second drain does not roll a live group back to its welcome epoch`() =
        runTest {
            val coordinator = FakeCoordinator(callerPubKey = alice)
            val aliceManager = manager(alice, coordinator)
            val (bobBundle, stored) = bobsPublication()
            coordinator.seedKeyPackage(stored)

            aliceManager.createGroup(gid, CordnGroupMetadata(name = "Rollback"))
            aliceManager.invite(gid, bob)

            val bobCoordinator = FakeCoordinator(callerPubKey = bob)
            bobCoordinator.welcomes.putAll(coordinator.welcomes)
            bobCoordinator.streams.putAll(coordinator.streams)
            val bobManager = manager(bob, bobCoordinator)
            bobManager.joinPendingWelcomes({ bobBundle })
            bobManager.catchUp { }
            val epochAfterJoin = bobManager.group(gid)!!.epoch

            // Carol arrives; Bob applies the Commit and advances.
            val (_, carolStored) = carolsPublication()
            coordinator.seedKeyPackage(carolStored)
            aliceManager.invite(gid, carolStored.pubKey)
            bobCoordinator.streams.putAll(coordinator.streams)
            bobManager.catchUp { }
            val epochAfterCommit = bobManager.group(gid)!!.epoch
            assertTrue(epochAfterCommit > epochAfterJoin, "precondition: Bob advanced")

            // A second drain, which is what a relaunch or a retry does.
            bobManager.joinPendingWelcomes({ bobBundle })

            // Before the accept/decline split this replaced Bob's live group
            // with the one the Welcome was issued at, and every message after
            // that epoch stopped decrypting -- silently, and for good.
            assertEquals(epochAfterCommit, bobManager.group(gid)!!.epoch, "a second drain rolled Bob's group back")
        }

    @Test
    fun `a welcome can be read before it is answered`() =
        runTest {
            val coordinator = FakeCoordinator(callerPubKey = alice)
            val aliceManager = manager(alice, coordinator)
            val (bobBundle, stored) = bobsPublication()
            coordinator.seedKeyPackage(stored)

            aliceManager.createGroup(gid, CordnGroupMetadata(name = "Book club", description = "Thursdays"))
            aliceManager.invite(gid, bob)

            val bobManager = manager(bob, bobsCoordinatorOver(coordinator))
            val inbox = bobManager.pendingWelcomes({ bobBundle })

            val welcome = inbox.pending.single()
            assertEquals(gid, welcome.gid)
            assertEquals("Book club", welcome.metadata?.name)
            // Who is already in it -- deliberately not "who invited you",
            // which a Welcome does not carry.
            assertEquals(setOf(alice, bob), welcome.members)
            assertTrue(bobManager.gids.value.isEmpty(), "reading an invitation must not join it")
        }

    @Test
    fun `accepting joins the group and stops the coordinator serving the welcome`() =
        runTest {
            val coordinator = FakeCoordinator(callerPubKey = alice)
            val aliceManager = manager(alice, coordinator)
            val (bobBundle, stored) = bobsPublication()
            coordinator.seedKeyPackage(stored)
            aliceManager.createGroup(gid, CordnGroupMetadata(name = "Accepted"))
            aliceManager.invite(gid, bob)

            val bobCoordinator = bobsCoordinatorOver(coordinator)
            val bobManager = manager(bob, bobCoordinator)
            val welcome = bobManager.pendingWelcomes({ bobBundle }).pending.single()
            assertEquals(gid, bobManager.accept(welcome))

            assertEquals(setOf(gid), bobManager.gids.value)
            // Retired at the coordinator, not merely filtered out on the way
            // back: an un-retired welcome is served to every future session of
            // this account forever, and each one has to re-open it to find out
            // it is stale.
            assertTrue(bobCoordinator.welcomes[bob].isNullOrEmpty(), "the welcome was not retired")
        }

    @Test
    fun `declining retires the welcome without joining anything`() =
        runTest {
            val coordinator = FakeCoordinator(callerPubKey = alice)
            val aliceManager = manager(alice, coordinator)
            val (bobBundle, stored) = bobsPublication()
            coordinator.seedKeyPackage(stored)
            aliceManager.createGroup(gid, CordnGroupMetadata(name = "Declined"))
            aliceManager.invite(gid, bob)

            val bobManager = manager(bob, bobsCoordinatorOver(coordinator))
            bobManager.decline(bobManager.pendingWelcomes({ bobBundle }).pending.single())

            assertTrue(bobManager.gids.value.isEmpty())
            assertTrue(
                bobManager.pendingWelcomes({ bobBundle }).pending.isEmpty(),
                "a declined welcome must not be offered again",
            )
        }

    @Test
    fun `a welcome this device has no key package for is left for the device that does`() =
        runTest {
            val coordinator = FakeCoordinator(callerPubKey = alice)
            val aliceManager = manager(alice, coordinator)
            val (_, stored) = bobsPublication()
            coordinator.seedKeyPackage(stored)
            aliceManager.createGroup(gid, CordnGroupMetadata(name = "Other device"))
            aliceManager.invite(gid, bob)

            val bobManager = manager(bob, bobsCoordinatorOver(coordinator))
            val first = bobManager.pendingWelcomes({ null })
            assertEquals(CordnGroupManager.NO_PRIVATE_HALF, first.skipped.single().reason)

            // Still there: retiring it would destroy the other device's only
            // copy of an invitation it can actually open.
            assertEquals(1, bobManager.pendingWelcomes({ null }).skipped.size)
        }

    @Test
    fun `a welcome for a group we are already in is retired rather than offered as a choice`() =
        runTest {
            val coordinator = FakeCoordinator(callerPubKey = alice)
            val aliceManager = manager(alice, coordinator)
            val (bobBundle, stored) = bobsPublication()
            coordinator.seedKeyPackage(stored)
            aliceManager.createGroup(gid, CordnGroupMetadata(name = "Stale"))
            aliceManager.invite(gid, bob)

            val bobCoordinator = bobsCoordinatorOver(coordinator)
            val bobManager = manager(bob, bobCoordinator)
            // Joined, but the acknowledgement never landed -- a dropped
            // connection between accepting and retiring leaves exactly this
            // record still being served.
            val served = bobCoordinator.welcomes[bob]!!.toList()
            bobManager.accept(bobManager.pendingWelcomes({ bobBundle }).pending.single())
            bobCoordinator.welcomes[bob] = served.toMutableList()

            val inbox = bobManager.pendingWelcomes({ bobBundle })

            assertTrue(inbox.pending.isEmpty(), "nobody should be asked about a group they are in")
            assertEquals(CordnGroupManager.ALREADY_A_MEMBER, inbox.skipped.single().reason)
        }

    /** Bob's own view of the coordinator, carrying whatever Alice's has stored. */
    private fun bobsCoordinatorOver(alices: FakeCoordinator): FakeCoordinator {
        val bobCoordinator = FakeCoordinator(callerPubKey = bob)
        bobCoordinator.welcomes.putAll(alices.welcomes)
        bobCoordinator.streams.putAll(alices.streams)
        return bobCoordinator
    }
}
