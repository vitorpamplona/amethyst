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
import kotlin.test.assertTrue

/**
 * Stage C's stated verification: **link → request → accept → first message**.
 *
 * The four steps are one flow and only mean anything together. A share ref is
 * useless if the `gid` it names cannot be requested; a request is useless if
 * nobody can turn it into a Welcome; a Welcome is useless if the joiner cannot
 * then read the room. Each half has its own unit tests elsewhere — this is the
 * seam between them, which is where a protocol built out of four independent
 * coordinator calls actually breaks.
 */
@OptIn(ExperimentalEncodingApi::class)
class CordnJoinRequestTest {
    private val coordinatorKey = "d".repeat(64)
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
    ) = CordnGroupManager(
        accountPubKey = account,
        config = config,
        coordinator = coordinator,
        store = InMemoryCordnGroupStore(),
        clock = { 1_757_000_000L },
    )

    @Test
    fun `a share ref becomes a request, an invite, and a readable room`() =
        runTest {
            // One coordinator, two callers. Each manager sees it as its own
            // account, which is what makes the Welcome routing meaningful.
            val alicesView = FakeCoordinator(callerPubKey = alice)
            val bobsView = alicesView.viewAs(bob)

            val aliceManager = manager(alice, alicesView)
            val bobManager = manager(bob, bobsView)

            // 1. Alice makes a group and shares its ref.
            val gid = "stage-c-room"
            aliceManager.createGroup(gid, CordnGroupMetadata(name = "Stage C"))
            val ref = aliceManager.shareRef(gid)
            val fromLink = CordnLinkInspection.of(ref.encode())
            assertTrue(fromLink is CordnLinkInspection.Valid, "the ref Alice shares must parse as a link")
            assertEquals(gid, fromLink.ref.gid)

            // 2. Bob publishes a KeyPackage to that coordinator and asks in.
            val (bobBundle, bobsKeyPackage) = publicationFor(bobSigner)
            bobsView.seedKeyPackage(bobsKeyPackage)
            bobManager.requestToJoin(fromLink.ref.gid, bobsKeyPackage.keyPackageRef)

            // 3. Alice sees the request and accepts it.
            val request = aliceManager.pendingJoinRequests().single()
            assertEquals(bob, request.pubKey)
            assertEquals(bobsKeyPackage.keyPackageRef, request.keyPackageRef)
            aliceManager.acceptJoinRequest(request)

            assertTrue(
                aliceManager.pendingJoinRequests().isEmpty(),
                "an accepted request must not be offered a second time",
            )

            // 4. Bob opens the Welcome and reads what Alice says next.
            aliceManager.send(gid, "welcome in")
            val welcome = bobManager.pendingWelcomes({ bobBundle }).pending.single()
            assertEquals(gid, welcome.gid)
            assertEquals(gid, bobManager.accept(welcome))

            val delivered = mutableListOf<CordnGroupManager.Delivery>()
            bobManager.catchUp { delivered += it }
            val messages = delivered.filterIsInstance<CordnGroupManager.Delivery.Message>()

            assertEquals(listOf("welcome in"), messages.map { it.received.envelope.content })
            assertEquals(alice, messages.single().received.sender, "the sender is MLS-authenticated, not claimed")
        }

    @Test
    fun `declining a request adds nobody and does not offer it again`() =
        runTest {
            val alicesView = FakeCoordinator(callerPubKey = alice)
            val bobsView = alicesView.viewAs(bob)
            val aliceManager = manager(alice, alicesView)
            val bobManager = manager(bob, bobsView)

            val gid = "closed-room"
            aliceManager.createGroup(gid, CordnGroupMetadata(name = "Closed"))
            val (bobBundle, bobsKeyPackage) = publicationFor(bobSigner)
            bobsView.seedKeyPackage(bobsKeyPackage)
            bobManager.requestToJoin(gid, bobsKeyPackage.keyPackageRef)

            aliceManager.declineJoinRequest(aliceManager.pendingJoinRequests().single())

            assertTrue(aliceManager.pendingJoinRequests().isEmpty())
            assertEquals(0L, aliceManager.group(gid)!!.epoch, "declining must not commit anything")
            assertTrue(
                bobManager.pendingWelcomes({ bobBundle }).pending.isEmpty(),
                "a declined request must not produce a Welcome",
            )
        }

    @Test
    fun `a request is answered with the key package it named`() =
        runTest {
            // Bob has two packages published. The request names one; taking the
            // other would spend a package he meant for a different inviter, and
            // the Welcome would be addressed to a private half he still holds
            // but did not expect to use here.
            val alicesView = FakeCoordinator(callerPubKey = alice)
            val bobsView = alicesView.viewAs(bob)
            val aliceManager = manager(alice, alicesView)
            val bobManager = manager(bob, bobsView)

            val gid = "two-packages"
            aliceManager.createGroup(gid, CordnGroupMetadata(name = "Two"))

            val (_, spare) = publicationFor(bobSigner, ref = "aa".repeat(16))
            val (named, asked) = publicationFor(bobSigner, ref = "bb".repeat(16))
            bobsView.seedKeyPackage(spare)
            bobsView.seedKeyPackage(asked)

            bobManager.requestToJoin(gid, asked.keyPackageRef)
            aliceManager.acceptJoinRequest(aliceManager.pendingJoinRequests().single())

            val welcome = bobManager.pendingWelcomes({ ref -> named.takeIf { ref == asked.keyPackageRef } }).pending.single()
            assertEquals(gid, welcome.gid)
        }

    /** A KeyPackage plus the signed `kp_publish` event that binds it to [signer]. */
    private fun publicationFor(
        signer: NostrSignerSync,
        ref: String? = null,
    ): Pair<KeyPackageBundle, FakeCoordinator.StoredKeyPackage> {
        val identity = CordnCredential.of(signer.pubKey).identity
        val scratch = MlsGroup.create(identity, policy = CordnGroupPolicy)
        val bundle = scratch.createKeyPackage(identity, ByteArray(0))
        val base64 = Base64.encode(bundle.keyPackage.toTlsBytes())
        val keyPackageRef =
            ref ?: bundle.keyPackage
                .toTlsBytes()
                .take(16)
                .joinToString("") { "%02x".format(it) }

        val content =
            """{"jsonrpc":"2.0","id":1,"method":"tools/call",""" +
                """"params":{"name":"kp_publish","arguments":{"kp_ref":"$keyPackageRef","kp_64":"$base64"}}}"""
        val event: Event =
            signer.sign(
                EventTemplate(
                    createdAt = 1_757_000_000L,
                    kind = 25910,
                    tags = arrayOf(arrayOf("p", coordinatorKey)),
                    content = content,
                ),
            )
        return bundle to FakeCoordinator.StoredKeyPackage(signer.pubKey, keyPackageRef, base64, event)
    }
}
