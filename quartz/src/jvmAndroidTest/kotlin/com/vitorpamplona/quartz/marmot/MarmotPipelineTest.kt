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
package com.vitorpamplona.quartz.marmot

import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageRotationManager
import com.vitorpamplona.quartz.marmot.mip03GroupMessages.GroupEvent
import com.vitorpamplona.quartz.marmot.mip03GroupMessages.GroupEventEncryption
import com.vitorpamplona.quartz.marmot.mls.group.MlsGroupManager
import com.vitorpamplona.quartz.marmot.mls.group.MlsGroupStateStore
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * In-memory implementation of [MlsGroupStateStore] for testing.
 */
class TestGroupStateStore : MlsGroupStateStore {
    private val states = mutableMapOf<String, ByteArray>()
    private val retainedEpochs = mutableMapOf<String, List<ByteArray>>()

    override suspend fun save(
        nostrGroupId: String,
        state: ByteArray,
    ) {
        states[nostrGroupId] = state
    }

    override suspend fun load(nostrGroupId: String): ByteArray? = states[nostrGroupId]

    override suspend fun delete(nostrGroupId: String) {
        states.remove(nostrGroupId)
        retainedEpochs.remove(nostrGroupId)
    }

    override suspend fun listGroups(): List<String> = states.keys.toList()

    override suspend fun saveRetainedEpochs(
        nostrGroupId: String,
        retainedSecrets: List<ByteArray>,
    ) {
        retainedEpochs[nostrGroupId] = retainedSecrets
    }

    override suspend fun loadRetainedEpochs(nostrGroupId: String): List<ByteArray> = retainedEpochs[nostrGroupId] ?: emptyList()
}

/**
 * Integration tests for the Marmot message processing pipeline.
 *
 * Tests the full encrypt → wrap → unwrap → decrypt roundtrip through
 * MarmotOutboundProcessor and MarmotInboundProcessor.
 */
class MarmotPipelineTest {
    private val groupId = "a".repeat(64)

    private fun createGroupManager(): MlsGroupManager = MlsGroupManager(TestGroupStateStore())

    @Test
    fun testOutboundMessageBuildsValidGroupEvent() {
        runBlocking {
            val manager = createGroupManager()
            manager.createGroup(groupId, "alice".encodeToByteArray())

            val outbound = MarmotOutboundProcessor(manager)
            val result = outbound.buildGroupEventFromBytes(groupId, "Hello group!".encodeToByteArray())

            val event = result.signedEvent
            assertEquals(GroupEvent.KIND, event.kind)
            assertEquals(groupId, event.groupId())
            assertTrue(event.content.isNotEmpty())
            assertTrue(event.sig.isNotEmpty())
        }
    }

    @Test
    fun testOutboundUsesEphemeralKey() {
        runBlocking {
            val manager = createGroupManager()
            manager.createGroup(groupId, "alice".encodeToByteArray())

            val outbound = MarmotOutboundProcessor(manager)
            val result1 = outbound.buildGroupEventFromBytes(groupId, "msg1".encodeToByteArray())
            val result2 = outbound.buildGroupEventFromBytes(groupId, "msg2".encodeToByteArray())

            // Each event should have a different ephemeral pubkey
            assertNotEquals(result1.signedEvent.pubKey, result2.signedEvent.pubKey)
        }
    }

    @Test
    fun testOutboundEncryptionRoundtrip() {
        runBlocking {
            val manager = createGroupManager()
            manager.createGroup(groupId, "alice".encodeToByteArray())

            val plaintext = "Hello from Marmot!"
            val outbound = MarmotOutboundProcessor(manager)
            val result = outbound.buildGroupEventFromBytes(groupId, plaintext.encodeToByteArray())

            // Manually decrypt to verify the roundtrip
            val exporterKey = manager.exporterSecret(groupId)
            val mlsBytes = GroupEventEncryption.decrypt(result.signedEvent.content, exporterKey)
            val decrypted = manager.decrypt(groupId, mlsBytes)

            assertEquals(plaintext, decrypted.content.decodeToString())
        }
    }

    @Test
    fun testInboundProcessesApplicationMessage() {
        runBlocking {
            val manager = createGroupManager()
            manager.createGroup(groupId, "alice".encodeToByteArray())

            val keyPackageRotationManager = KeyPackageRotationManager()
            val inbound = MarmotInboundProcessor(manager, keyPackageRotationManager)
            val outbound = MarmotOutboundProcessor(manager)

            // Build an outbound message
            val plaintext = "Hello inbound!"
            val outboundResult =
                outbound.buildGroupEventFromBytes(
                    groupId,
                    plaintext.encodeToByteArray(),
                )

            // Process it as inbound
            val result = inbound.processGroupEvent(outboundResult.signedEvent)

            assertIs<GroupEventResult.ApplicationMessage>(result)
            assertEquals(groupId, result.groupId)
            assertEquals(plaintext, result.innerEventJson)
        }
    }

    @Test
    fun testInboundRejectsNonMemberGroup() {
        runBlocking {
            val manager = createGroupManager()
            val keyPackageRotationManager = KeyPackageRotationManager()
            val inbound = MarmotInboundProcessor(manager, keyPackageRotationManager)

            // Create a fake GroupEvent for a group we're not a member of
            val unknownGroupId = "f".repeat(64)
            val template =
                GroupEvent.build(
                    encryptedContentBase64 = "dGVzdA==",
                    nostrGroupId = unknownGroupId,
                )
            val signer = NostrSignerInternal(KeyPair())
            val fakeEvent: GroupEvent = signer.sign(template)

            val result = inbound.processGroupEvent(fakeEvent)

            assertIs<GroupEventResult.Error>(result)
            assertEquals(unknownGroupId, result.groupId)
            assertTrue(result.message.contains("Not a member"))
        }
    }

    @Test
    fun testInboundRejectsMissingGroupId() {
        runBlocking {
            val manager = createGroupManager()
            val keyPackageRotationManager = KeyPackageRotationManager()
            val inbound = MarmotInboundProcessor(manager, keyPackageRotationManager)

            // Create an event with no h tag
            val signer = NostrSignerInternal(KeyPair())
            val event: GroupEvent =
                signer.sign(
                    createdAt = 1700000000L,
                    kind = GroupEvent.KIND,
                    tags = arrayOf(),
                    content = "dGVzdA==",
                )

            val result = inbound.processGroupEvent(event)

            assertIs<GroupEventResult.Error>(result)
            assertTrue(result.message.contains("missing h tag"))
        }
    }

    @Test
    fun testCommitEventBuildAndStructure() {
        runBlocking {
            val manager = createGroupManager()
            manager.createGroup(groupId, "alice".encodeToByteArray())

            val outbound = MarmotOutboundProcessor(manager)

            // Create a commit
            val commitResult = manager.commit(groupId)
            val outboundResult = outbound.buildCommitEvent(groupId, commitResult.commitBytes)

            val event = outboundResult.signedEvent
            assertEquals(GroupEvent.KIND, event.kind)
            assertEquals(groupId, event.groupId())
            assertTrue(event.content.isNotEmpty())
        }
    }

    @Test
    fun testSubscriptionManagerSyncWithGroupManager() {
        runBlocking {
            val manager = createGroupManager()
            val groupId1 = "1".repeat(64)
            val groupId2 = "2".repeat(64)

            manager.createGroup(groupId1, "alice".encodeToByteArray())
            manager.createGroup(groupId2, "alice".encodeToByteArray())

            val alicePubKey = "a".repeat(64)
            val subscriptionManager = MarmotSubscriptionManager(alicePubKey)
            subscriptionManager.syncWithGroupManager(manager.activeGroupIds())

            assertTrue(subscriptionManager.isSubscribed(groupId1))
            assertTrue(subscriptionManager.isSubscribed(groupId2))
            assertEquals(2, subscriptionManager.activeGroupIds().size)
        }
    }

    @Test
    fun testWelcomeEventDetection() {
        // WelcomeEvent kind detection helper
        val mockEvent =
            object : com.vitorpamplona.quartz.nip01Core.core.Event(
                "a".repeat(64),
                "b".repeat(64),
                1700000000L,
                444,
                arrayOf(),
                "",
                "c".repeat(128),
            ) {}
        assertTrue(MarmotInboundProcessor.isWelcomeEvent(mockEvent))

        val nonWelcome =
            object : com.vitorpamplona.quartz.nip01Core.core.Event(
                "a".repeat(64),
                "b".repeat(64),
                1700000000L,
                1,
                arrayOf(),
                "",
                "c".repeat(128),
            ) {}
        assertTrue(!MarmotInboundProcessor.isWelcomeEvent(nonWelcome))
    }

    @Test
    fun testWelcomeSenderWrapsWelcome() {
        runBlocking {
            val aliceKeyPair = KeyPair()
            val aliceSigner = NostrSignerInternal(aliceKeyPair)

            val manager = createGroupManager()
            manager.createGroup(groupId, "alice".encodeToByteArray())

            // Create a KeyPackage for Bob
            val group = manager.getGroup(groupId)!!
            val bobBundle = group.createKeyPackage("bob".encodeToByteArray(), ByteArray(0))

            // Add Bob to the group
            val commitResult = manager.addMember(groupId, bobBundle.keyPackage.toTlsBytes())
            assertNotNull(commitResult.welcomeBytes)

            // Wrap the Welcome
            val welcomeSender = MarmotWelcomeSender(aliceSigner)
            val bobPubKey = "d".repeat(64)
            val delivery =
                welcomeSender.wrapWelcome(
                    commitResult = commitResult,
                    recipientPubKey = bobPubKey,
                    keyPackageEventId = "e".repeat(64),
                    relays = emptyList(),
                )

            assertNotNull(delivery)
            assertEquals(bobPubKey, delivery.recipientPubKey)
            // The gift wrap event should be kind 1059
            assertEquals(1059, delivery.giftWrapEvent.kind)
        }
    }

    @Test
    fun testCommitOrderingWithProcessor() {
        runBlocking {
            val manager = createGroupManager()
            manager.createGroup(groupId, "alice".encodeToByteArray())

            val keyPackageRotationManager = KeyPackageRotationManager()
            val inbound = MarmotInboundProcessor(manager, keyPackageRotationManager)

            // Initially no pending commits
            assertTrue(inbound.pendingCommitGroupEpochs().isEmpty())

            // Clear works without error
            inbound.clearPendingCommits()
            assertTrue(inbound.pendingCommitGroupEpochs().isEmpty())
        }
    }

    @Test
    fun testMultipleGroupsOutbound() {
        runBlocking {
            val manager = createGroupManager()
            val groupId1 = "1".repeat(64)
            val groupId2 = "2".repeat(64)

            manager.createGroup(groupId1, "alice".encodeToByteArray())
            manager.createGroup(groupId2, "alice".encodeToByteArray())

            val outbound = MarmotOutboundProcessor(manager)

            val result1 = outbound.buildGroupEventFromBytes(groupId1, "msg1".encodeToByteArray())
            val result2 = outbound.buildGroupEventFromBytes(groupId2, "msg2".encodeToByteArray())

            assertEquals(groupId1, result1.signedEvent.groupId())
            assertEquals(groupId2, result2.signedEvent.groupId())
        }
    }

    @Test
    fun testFullRoundtripEncryptDecrypt() {
        runBlocking {
            val manager = createGroupManager()
            manager.createGroup(groupId, "alice".encodeToByteArray())

            val keyPackageRotationManager = KeyPackageRotationManager()
            val outbound = MarmotOutboundProcessor(manager)
            val inbound = MarmotInboundProcessor(manager, keyPackageRotationManager)

            // Send multiple messages and verify roundtrip
            val messages = listOf("Hello!", "How are you?", "Goodbye!")

            for (msg in messages) {
                val outResult = outbound.buildGroupEventFromBytes(groupId, msg.encodeToByteArray())
                val inResult = inbound.processGroupEvent(outResult.signedEvent)

                assertIs<GroupEventResult.ApplicationMessage>(inResult)
                assertEquals(msg, inResult.innerEventJson)
                assertEquals(groupId, inResult.groupId)
            }
        }
    }
}
