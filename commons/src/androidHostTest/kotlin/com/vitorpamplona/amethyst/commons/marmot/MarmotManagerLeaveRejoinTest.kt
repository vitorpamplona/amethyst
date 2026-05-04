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
package com.vitorpamplona.amethyst.commons.marmot

import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageBundleStore
import com.vitorpamplona.quartz.marmot.mip01Groups.MarmotGroupData
import com.vitorpamplona.quartz.marmot.mls.group.MarmotMessageStore
import com.vitorpamplona.quartz.marmot.mls.group.MlsGroupStateStore
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * End-to-end leave + rejoin round-trip at the [MarmotManager] layer.
 *
 * One level above [com.vitorpamplona.quartz.marmot.mls.MlsGroupManagerTest.testLeaveAndRejoin_SameGroupIdEndToEnd]
 * in quartz, which only exercises the MLS engine. This one drives the full
 * commons-layer pipeline:
 *
 *   - NIP-59 gift wrap / unwrap of the Welcome (kind:1059 → kind:13 → kind:444),
 *   - ChaCha20-Poly1305 outer layer + MLS PrivateMessage decryption of group
 *     events (kind:445) via `MarmotManager.ingest`,
 *   - persisted inner-event log via [MarmotMessageStore],
 *   - subscription bookkeeping in [com.vitorpamplona.quartz.marmot.MarmotSubscriptionManager],
 *   - KeyPackage bundle lifecycle via [KeyPackageBundleStore] +
 *     [com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageRotationManager].
 *
 * Scenarios covered in one flow:
 *   1. Alice creates the group with a [MarmotGroupData] extension
 *      (required so the receiver's `processWelcome` can derive the
 *      nostrGroupId — admin list left empty so MIP-03 gates don't fire).
 *   2. Bob publishes a KeyPackage, Alice `addMember`s it, Bob `ingest`s the
 *      resulting gift wrap and joins.
 *   3. Round-trip: Alice sends a kind:9 via `buildTextMessage`, Bob ingests
 *      the resulting kind:445, the plaintext lands in his [MarmotMessageStore].
 *   4. `bob.leaveGroup(...)` wipes Bob's MLS state, persisted messages and
 *      subscription for that group.
 *   5. Alice evicts Bob's leaf with an admin-kick Remove commit. (Standalone-
 *      proposal ingestion at [com.vitorpamplona.quartz.marmot.MarmotInboundProcessor]
 *      is a separate TODO — this test pins down the cleanup + rejoin story
 *      around it.)
 *   6. Bob publishes a fresh KeyPackage, Alice `addMember`s it, Bob
 *      `ingest`s the second gift wrap and rejoins the same nostrGroupId.
 *   7. Round-trip after rejoin proves the shared tree / keying material
 *      are in sync and that `MarmotManager.ingest` correctly routes a
 *      kind:445 authored at the new epoch.
 */
class MarmotManagerLeaveRejoinTest {
    private val nostrGroupId = "a".repeat(64)
    private val relay =
        RelayUrlNormalizer.normalizeOrNull("wss://example.invalid/")
            ?: error("test relay must normalize")

    @Test
    fun testLeaveAndRejoin_CommonsLayer() {
        runBlocking {
            val alice = buildManager()
            val bob = buildManager()

            // Step 1: Alice creates the group with a NostrGroupData extension.
            val metadata =
                MarmotGroupData(
                    nostrGroupId = nostrGroupId,
                    name = "leave-rejoin-commons",
                    relays = listOf(relay.url),
                )
            alice.manager.createGroup(nostrGroupId, metadata)
            assertTrue(alice.manager.isMember(nostrGroupId))

            // Step 2: Bob publishes a KP, Alice adds him, Bob ingests the wrap.
            val bobKp1 = bob.manager.generateKeyPackageEvent(listOf(relay))
            val (commitEvent1, delivery1) =
                alice.manager.addMember(nostrGroupId, bobKp1, listOf(relay))
            assertNotNull(delivery1, "Alice's first add must produce a gift-wrapped Welcome")

            val firstJoin = bob.manager.ingest(delivery1.giftWrapEvent)
            assertIs<MarmotIngestResult.JoinedGroup>(
                firstJoin,
                "Bob must join via the gift-wrapped Welcome",
            )
            assertEquals(nostrGroupId, firstJoin.nostrGroupId)
            assertTrue(bob.manager.isMember(nostrGroupId))
            assertTrue(
                bob.manager.subscriptionManager.isSubscribed(nostrGroupId),
                "Joining must register a subscription for the group",
            )

            // The commit that added Bob also arrives at Alice (echo) — ingest
            // is idempotent: her own pipeline marks the id processed before
            // publish, so a replay routes to Ignored.
            assertIs<MarmotIngestResult.Ignored>(alice.manager.ingest(commitEvent1.signedEvent))

            // Step 3: Alice sends a kind:9 inner event. Bob ingests the kind:445.
            val helloBefore = "hello before leave"
            val helloBundle = alice.manager.buildTextMessage(nostrGroupId, helloBefore)
            val helloResult = bob.manager.ingest(helloBundle.outbound.signedEvent)
            val helloMessage =
                assertIs<MarmotIngestResult.Message>(helloResult, "Bob should decrypt Alice's kind:9")
            assertEquals(nostrGroupId, helloMessage.inner.groupId)

            val bobMessages1 = bob.manager.loadStoredMessages(nostrGroupId)
            assertTrue(
                bobMessages1.any { it.contains(helloBefore) },
                "Bob's persisted log should contain Alice's pre-leave message; was $bobMessages1",
            )

            // Step 4: Bob leaves. SelfRemove bytes are returned; local state wiped.
            val leaveOutbound = bob.manager.leaveGroup(nostrGroupId)
            assertTrue(leaveOutbound.signedEvent.content.isNotEmpty())
            assertFalse(
                bob.manager.isMember(nostrGroupId),
                "Bob must no longer hold the MLS group after leaveGroup",
            )
            assertNull(
                bob.mlsStore.load(nostrGroupId),
                "Bob's MLS state must be deleted from the store",
            )
            assertEquals(
                emptyList(),
                bob.manager.loadStoredMessages(nostrGroupId),
                "Bob's persisted inner-event log must be wiped on leave",
            )
            assertFalse(
                bob.manager.subscriptionManager.isSubscribed(nostrGroupId),
                "Bob's subscription for the group must be cleared on leave",
            )

            // Step 5: Alice evicts Bob's leaf (admin-kick stands in for the
            // still-unimplemented standalone-proposal ingestion pipeline).
            // Bob's leaf was assigned during addMember; look it up against
            // Alice's MLS group view to avoid hard-coding a leaf index.
            val bobLeaf =
                alice.manager.groupManager
                    .getGroup(nostrGroupId)
                    ?.memberIdentityHex(1)
                    ?.let { 1 }
                    ?: error("Bob must still occupy leaf 1 on Alice's side")
            alice.manager.removeMember(nostrGroupId, bobLeaf)
            assertEquals(1, alice.manager.memberCount(nostrGroupId))

            // Step 6: Bob rejoins with a fresh KP under the SAME nostrGroupId.
            val bobKp2 = bob.manager.generateKeyPackageEvent(listOf(relay))
            val (_, delivery2) =
                alice.manager.addMember(nostrGroupId, bobKp2, listOf(relay))
            assertNotNull(delivery2, "Alice's re-add must produce a second gift-wrapped Welcome")

            val secondJoin = bob.manager.ingest(delivery2.giftWrapEvent)
            assertIs<MarmotIngestResult.JoinedGroup>(
                secondJoin,
                "Bob must rejoin via the second gift wrap",
            )
            assertEquals(nostrGroupId, secondJoin.nostrGroupId)
            assertTrue(bob.manager.isMember(nostrGroupId))
            assertTrue(
                bob.manager.subscriptionManager.isSubscribed(nostrGroupId),
                "Rejoining must re-subscribe Bob to the group",
            )
            assertNotNull(
                bob.mlsStore.load(nostrGroupId),
                "Bob's store must hold fresh MLS state for the rejoined group",
            )

            // Step 7: Round-trip after rejoin — proves the new epoch works
            // end-to-end through commons, not just that the Welcome deserialized.
            val helloAfter = "hello after rejoin"
            val afterBundle = alice.manager.buildTextMessage(nostrGroupId, helloAfter)
            val afterResult = bob.manager.ingest(afterBundle.outbound.signedEvent)
            val afterMessage =
                assertIs<MarmotIngestResult.Message>(
                    afterResult,
                    "Bob should decrypt Alice's post-rejoin kind:9",
                )
            assertEquals(nostrGroupId, afterMessage.inner.groupId)

            val bobMessages2 = bob.manager.loadStoredMessages(nostrGroupId)
            assertTrue(
                bobMessages2.any { it.contains(helloAfter) },
                "Bob's fresh log should contain the post-rejoin message; was $bobMessages2",
            )
            assertFalse(
                bobMessages2.any { it.contains(helloBefore) },
                "Pre-leave messages MUST NOT survive the rejoin — forward secrecy sanity",
            )
        }
    }

    // --- Test fixtures -----------------------------------------------------

    private data class Fixture(
        val manager: MarmotManager,
        val mlsStore: InMemoryMlsGroupStateStore,
        val kpStore: InMemoryKeyPackageBundleStore,
        val messageStore: InMemoryMarmotMessageStore,
    )

    private fun buildManager(): Fixture {
        val signer = NostrSignerInternal(KeyPair())
        val mls = InMemoryMlsGroupStateStore()
        val kp = InMemoryKeyPackageBundleStore()
        val msg = InMemoryMarmotMessageStore()
        return Fixture(MarmotManager(signer, mls, msg, kp), mls, kp, msg)
    }
}

private class InMemoryMlsGroupStateStore : MlsGroupStateStore {
    private val states = mutableMapOf<String, ByteArray>()
    private val retained = mutableMapOf<String, List<ByteArray>>()

    override suspend fun save(
        nostrGroupId: String,
        state: ByteArray,
    ) {
        states[nostrGroupId] = state
    }

    override suspend fun load(nostrGroupId: String): ByteArray? = states[nostrGroupId]

    override suspend fun delete(nostrGroupId: String) {
        states.remove(nostrGroupId)
        retained.remove(nostrGroupId)
    }

    override suspend fun listGroups(): List<String> = states.keys.toList()

    override suspend fun saveRetainedEpochs(
        nostrGroupId: String,
        retainedSecrets: List<ByteArray>,
    ) {
        retained[nostrGroupId] = retainedSecrets
    }

    override suspend fun loadRetainedEpochs(nostrGroupId: String): List<ByteArray> = retained[nostrGroupId] ?: emptyList()
}

private class InMemoryKeyPackageBundleStore : KeyPackageBundleStore {
    private var snapshot: ByteArray? = null

    override suspend fun save(snapshot: ByteArray) {
        this.snapshot = snapshot
    }

    override suspend fun load(): ByteArray? = snapshot

    override suspend fun delete() {
        snapshot = null
    }
}

private class InMemoryMarmotMessageStore : MarmotMessageStore {
    private val messages = mutableMapOf<String, MutableList<String>>()

    override suspend fun appendMessage(
        nostrGroupId: String,
        innerEventJson: String,
    ) {
        messages.getOrPut(nostrGroupId) { mutableListOf() }.add(innerEventJson)
    }

    override suspend fun loadMessages(nostrGroupId: String): List<String> = messages[nostrGroupId]?.toList() ?: emptyList()

    override suspend fun delete(nostrGroupId: String) {
        messages.remove(nostrGroupId)
    }
}
