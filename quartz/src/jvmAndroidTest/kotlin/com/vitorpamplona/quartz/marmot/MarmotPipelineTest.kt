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
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
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
    fun testAddMemberCommitIsFramedAsPublicMessage() {
        // Regression: addMember's outbound commit used to carry the raw Commit TLS
        // bytes instead of an MlsMessage(PublicMessage(commit)) envelope, which
        // caused receivers to fail parsing with "Unsupported MLS version: …"
        // when the first two bytes of the Commit struct were read as the
        // MlsMessage version field.
        runBlocking {
            val manager = createGroupManager()
            manager.createGroup(groupId, "alice".encodeToByteArray())
            val group = manager.getGroup(groupId)!!
            val bobBundle = group.createKeyPackage("bob".encodeToByteArray(), ByteArray(0))

            val commitResult = manager.addMember(groupId, bobBundle.keyPackage.toTlsBytes())

            // The framedCommitBytes must decode as an MlsMessage(PublicMessage(commit))
            val framed = commitResult.framedCommitBytes
            val mlsMessage =
                com.vitorpamplona.quartz.marmot.mls.framing.MlsMessage
                    .decodeTls(
                        com.vitorpamplona.quartz.marmot.mls.codec
                            .TlsReader(framed),
                    )
            assertEquals(
                com.vitorpamplona.quartz.marmot.mls.framing.WireFormat.PUBLIC_MESSAGE,
                mlsMessage.wireFormat,
            )

            val publicMessage =
                com.vitorpamplona.quartz.marmot.mls.framing.PublicMessage
                    .decodeTls(
                        com.vitorpamplona.quartz.marmot.mls.codec
                            .TlsReader(mlsMessage.payload),
                    )
            assertEquals(
                com.vitorpamplona.quartz.marmot.mls.framing.ContentType.COMMIT,
                publicMessage.contentType,
            )
            assertEquals(
                com.vitorpamplona.quartz.marmot.mls.framing.SenderType.MEMBER,
                publicMessage.sender.senderType,
            )
            assertNotNull(publicMessage.confirmationTag, "confirmation_tag must be present on a commit")
            // The PublicMessage.content carries the raw Commit struct.
            kotlin.test.assertContentEquals(commitResult.commitBytes, publicMessage.content)
        }
    }

    @Test
    fun testAddMemberCommitEventDecryptsToFramedMlsMessage() {
        // End-to-end variant: the kind:445 content returned by buildCommitEvent,
        // when ChaCha20-Poly1305 decrypted with the current exporter key, must
        // yield an MlsMessage whose wire format is PUBLIC_MESSAGE (not a raw
        // Commit struct).
        runBlocking {
            val manager = createGroupManager()
            manager.createGroup(groupId, "alice".encodeToByteArray())
            val group = manager.getGroup(groupId)!!
            val bobBundle = group.createKeyPackage("bob".encodeToByteArray(), ByteArray(0))

            val commitResult = manager.addMember(groupId, bobBundle.keyPackage.toTlsBytes())

            val outbound = MarmotOutboundProcessor(manager)
            val outboundResult = outbound.buildCommitEvent(groupId, commitResult.framedCommitBytes)
            val event = outboundResult.signedEvent

            val exporterKey = manager.exporterSecret(groupId)
            val mlsBytes = GroupEventEncryption.decrypt(event.content, exporterKey)
            val mlsMessage =
                com.vitorpamplona.quartz.marmot.mls.framing.MlsMessage
                    .decodeTls(
                        com.vitorpamplona.quartz.marmot.mls.codec
                            .TlsReader(mlsBytes),
                    )
            assertEquals(
                com.vitorpamplona.quartz.marmot.mls.framing.WireFormat.PUBLIC_MESSAGE,
                mlsMessage.wireFormat,
            )
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
    fun testCreatorDoesNotDoubleApplyOwnCommitAfterRestart() {
        // Root cause: after David restarts, his own add-member kind:445 that
        // got echoed back from the relay is no longer marked as "already
        // processed" (the dedup set lives in memory and isn't persisted).
        // The inbound path then outer-decrypts it via a retained epoch key
        // and feeds the commit into group.processCommit — which is NOT
        // atomic, so it partially advances David's local state before failing
        // with "Confirmation tag verification failed" (the tag was computed
        // at the original epoch; David is already two epochs past).
        //
        // After this partial apply, David's epoch / tree / exporter_secret
        // drift forward by one epoch while Eden and Fred stay put. Any
        // subsequent message David sends uses a key nobody else has, and
        // they log "Outer decryption failed with current and N retained
        // epoch key(s)".
        runBlocking {
            val davidStore = TestGroupStateStore()
            val davidMgr = MlsGroupManager(davidStore)
            val edenMgr = MlsGroupManager(TestGroupStateStore())
            val fredMgr = MlsGroupManager(TestGroupStateStore())
            val davidId = ByteArray(32) { 0xD1.toByte() }
            val edenId = ByteArray(32) { 0xE2.toByte() }
            val fredId = ByteArray(32) { 0xF3.toByte() }

            davidMgr.createGroup(groupId, davidId)
            davidMgr.updateGroupExtensions(
                groupId,
                listOf(
                    com.vitorpamplona.quartz.marmot.mip01Groups
                        .MarmotGroupData(
                            nostrGroupId = groupId,
                            adminPubkeys = listOf(davidId.toHexKey()),
                        ).toExtension(),
                ),
            )
            val davidGroup = davidMgr.getGroup(groupId)!!
            val edenBundle = davidGroup.createKeyPackage(edenId, ByteArray(0))
            val fredBundle = davidGroup.createKeyPackage(fredId, ByteArray(0))

            val addEden = davidMgr.addMember(groupId, edenBundle.keyPackage.toTlsBytes())
            edenMgr.processWelcome(addEden.welcomeBytes!!, edenBundle)
            val addFred = davidMgr.addMember(groupId, fredBundle.keyPackage.toTlsBytes())
            fredMgr.processWelcome(addFred.welcomeBytes!!, fredBundle)
            // Feed the framed PublicMessage envelope so MlsGroup.processCommit
            // can verify the RFC 9420 §6.1 FramedContentTBS signature (now
            // mandatory on non-external commits).
            edenMgr.processFramedCommit(groupId, addFred.framedCommitBytes)

            val preRestartEpoch = davidMgr.getGroup(groupId)!!.epoch
            val preRestartExporter = davidMgr.exporterSecret(groupId)

            // Simulate David's app restart.
            val davidMgr2 = MlsGroupManager(davidStore)
            davidMgr2.restoreAll()

            // Now replay David's own echoed add-Eden commit that's still on
            // the relay. After restart, the inbound dedup set is empty so
            // the inbound pipeline does the full outer-decrypt via retained
            // keys + processCommit on an already-applied commit.
            val inbound =
                com.vitorpamplona.quartz.marmot
                    .MarmotInboundProcessor(
                        groupManager = davidMgr2,
                        keyPackageRotationManager =
                            com.vitorpamplona.quartz.marmot.mip00KeyPackages
                                .KeyPackageRotationManager(),
                    )
            val outbound = MarmotOutboundProcessor(davidMgr2)

            // Re-encrypt the add-Eden framed commit with the pre-commit key
            // so it looks exactly like what the relay would re-deliver.
            val echoed =
                outbound.buildCommitEvent(
                    nostrGroupId = groupId,
                    commitBytes = addEden.framedCommitBytes,
                    exporterKey = addEden.preCommitExporterSecret,
                )
            val result = inbound.processGroupEvent(echoed.signedEvent)
            assertIs<GroupEventResult.Duplicate>(
                result,
                "echoed already-applied commit must be treated as a no-op Duplicate",
            )

            val postEchoEpoch = davidMgr2.getGroup(groupId)!!.epoch
            val postEchoExporter = davidMgr2.exporterSecret(groupId)

            assertEquals(
                preRestartEpoch,
                postEchoEpoch,
                "processing our own already-applied commit echo must NOT advance the local epoch",
            )
            kotlin.test.assertContentEquals(
                preRestartExporter,
                postEchoExporter,
                "processing our own already-applied commit echo must NOT change the exporter secret",
            )
        }
    }

    @Test
    fun testCreatorCanSendMessageAfterRestart() {
        // Reproduces production symptom: David creates a group, adds Eden and
        // Fred, sends messages — all fine. After David restarts the app
        // (simulated here by save-state + fresh MlsGroupManager + restoreAll),
        // his outbound kind:445 outer ChaCha20 layer suddenly uses a
        // different key than Eden/Fred, and they fail with
        // "Outer decryption failed with current and N retained epoch key(s)".
        runBlocking {
            val davidStore = TestGroupStateStore()
            val davidMgr = MlsGroupManager(davidStore)
            val edenMgr = MlsGroupManager(TestGroupStateStore())
            val fredMgr = MlsGroupManager(TestGroupStateStore())

            val davidId = ByteArray(32) { 0xD1.toByte() }
            val edenId = ByteArray(32) { 0xE2.toByte() }
            val fredId = ByteArray(32) { 0xF3.toByte() }

            davidMgr.createGroup(groupId, davidId)
            davidMgr.updateGroupExtensions(
                nostrGroupId = groupId,
                extensions =
                    listOf(
                        com.vitorpamplona.quartz.marmot.mip01Groups
                            .MarmotGroupData(
                                nostrGroupId = groupId,
                                adminPubkeys = listOf(davidId.toHexKey()),
                            ).toExtension(),
                    ),
            )
            val davidGroup = davidMgr.getGroup(groupId)!!
            val edenBundle = davidGroup.createKeyPackage(edenId, ByteArray(0))
            val fredBundle = davidGroup.createKeyPackage(fredId, ByteArray(0))

            // David adds Eden.
            val addEden = davidMgr.addMember(groupId, edenBundle.keyPackage.toTlsBytes())
            edenMgr.processWelcome(addEden.welcomeBytes!!, edenBundle)

            // David adds Fred; Eden processes Alice's add-Fred commit.
            val addFred = davidMgr.addMember(groupId, fredBundle.keyPackage.toTlsBytes())
            fredMgr.processWelcome(addFred.welcomeBytes!!, fredBundle)
            // Feed the framed PublicMessage envelope so MlsGroup.processCommit
            // can verify the RFC 9420 §6.1 FramedContentTBS signature (now
            // mandatory on non-external commits).
            edenMgr.processFramedCommit(groupId, addFred.framedCommitBytes)

            // Pre-restart sanity: all three share the same outer exporter key.
            val preRestartDavidKey = davidMgr.exporterSecret(groupId)
            kotlin.test.assertContentEquals(
                preRestartDavidKey,
                edenMgr.exporterSecret(groupId),
                "Eden's exporter key should match David's before restart",
            )
            kotlin.test.assertContentEquals(
                preRestartDavidKey,
                fredMgr.exporterSecret(groupId),
                "Fred's exporter key should match David's before restart",
            )

            // Simulate David's app restart: drop his in-memory manager and
            // rebuild it from the persisted store.
            val davidMgr2 = MlsGroupManager(davidStore)
            davidMgr2.restoreAll()

            val postRestartDavidKey = davidMgr2.exporterSecret(groupId)
            kotlin.test.assertContentEquals(
                preRestartDavidKey,
                postRestartDavidKey,
                "David's exporter key must survive a save+restore round-trip; " +
                    "otherwise Eden/Fred can't decrypt his next outer layer.",
            )

            // David sends a message post-restart; Eden and Fred must decrypt.
            val outbound = MarmotOutboundProcessor(davidMgr2)
            val msg = "hi after restart"
            val postRestartEvent =
                outbound.buildGroupEventFromBytes(groupId, msg.encodeToByteArray())

            val edenKey = edenMgr.exporterSecret(groupId)
            val mlsBytesEden = GroupEventEncryption.decrypt(postRestartEvent.signedEvent.content, edenKey)
            val edenDecrypted = edenMgr.decrypt(groupId, mlsBytesEden)
            assertEquals(msg, edenDecrypted.content.decodeToString())
        }
    }

    @Test
    fun testFredEncryptsAfterJoiningThreeMemberGroup() {
        // Reproduces the production StackOverflowError: the last-joined member
        // (Fred at leafIndex=2 in a 3-member group) attempts to encrypt his
        // first message and the SecretTree.getNodeSecret recursion never
        // terminates.
        runBlocking {
            val aliceMgr = createGroupManager()
            val bobMgr = createGroupManager()
            val fredMgr = createGroupManager()

            val aliceIdBytes = ByteArray(32) { 0xA1.toByte() }
            aliceMgr.createGroup(groupId, aliceIdBytes)
            aliceMgr.updateGroupExtensions(
                nostrGroupId = groupId,
                extensions =
                    listOf(
                        com.vitorpamplona.quartz.marmot.mip01Groups
                            .MarmotGroupData(
                                nostrGroupId = groupId,
                                adminPubkeys = listOf(aliceIdBytes.toHexKey()),
                            ).toExtension(),
                    ),
            )
            val aliceGroup = aliceMgr.getGroup(groupId)!!
            val bobBundle = aliceGroup.createKeyPackage(ByteArray(32) { 0xB2.toByte() }, ByteArray(0))
            val fredBundle = aliceGroup.createKeyPackage(ByteArray(32) { 0xF3.toByte() }, ByteArray(0))

            // Alice adds Bob.
            val addBob = aliceMgr.addMember(groupId, bobBundle.keyPackage.toTlsBytes())
            bobMgr.processWelcome(addBob.welcomeBytes!!, bobBundle)

            // Alice adds Fred (he joins at leafIndex=2, in a 3-leaf tree).
            val addFred = aliceMgr.addMember(groupId, fredBundle.keyPackage.toTlsBytes())
            fredMgr.processWelcome(addFred.welcomeBytes!!, fredBundle)
            // Bob (existing member at the pre-add-Fred epoch) receives and
            // applies Alice's add-Fred commit to reach the same epoch as
            // Alice + Fred. Without this, Bob can't decrypt Fred's subsequent
            // application messages. Feed the framed PublicMessage envelope so
            // MlsGroup.processCommit can verify the mandatory RFC 9420 §6.1
            // FramedContentTBS signature.
            bobMgr.processFramedCommit(groupId, addFred.framedCommitBytes)

            // Sanity: Fred is at leafIndex=2, leafCount=3.
            assertEquals(2, fredMgr.getGroup(groupId)!!.leafIndex)

            // This is where production crashed with StackOverflowError.
            val fredMsg = "hi from fred"
            val fredCiphertext = fredMgr.encrypt(groupId, fredMsg.encodeToByteArray())

            // And Alice & Bob must be able to decrypt it.
            val aliceDecrypted = aliceMgr.decrypt(groupId, fredCiphertext)
            assertEquals(fredMsg, aliceDecrypted.content.decodeToString())
            val bobDecrypted = bobMgr.decrypt(groupId, fredCiphertext)
            assertEquals(fredMsg, bobDecrypted.content.decodeToString())
        }
    }

    @Test
    fun testAddMemberExposesPreCommitExporterSecret() {
        // Regression: before the fix, MarmotManager.addMember outer-encrypted
        // the kind:445 commit with the POST-commit (epoch N+1) exporter key,
        // which meant existing members at epoch N couldn't decrypt it. The
        // fix captures the pre-commit key inside MlsGroup.commit() and ships
        // it back via CommitResult.preCommitExporterSecret so MarmotManager
        // can pass it to the outbound builder.
        runBlocking {
            val manager = createGroupManager()
            manager.createGroup(groupId, "alice".encodeToByteArray())

            val preEpochExporter = manager.exporterSecret(groupId)
            val preEpoch = manager.getGroup(groupId)!!.epoch

            val bobBundle =
                manager
                    .getGroup(groupId)!!
                    .createKeyPackage("bob".encodeToByteArray(), ByteArray(0))
            val result = manager.addMember(groupId, bobBundle.keyPackage.toTlsBytes())

            // Local state advanced to N+1.
            assertEquals(preEpoch + 1, manager.getGroup(groupId)!!.epoch)
            // But the returned pre-commit exporter secret matches what the
            // group held BEFORE advancing — the key existing members still at
            // epoch N are holding.
            kotlin.test.assertContentEquals(
                preEpochExporter,
                result.preCommitExporterSecret,
            )
            // The post-commit exporter (what groupManager.exporterSecret now
            // returns) MUST differ — otherwise we haven't actually rotated.
            kotlin.test.assertFalse(
                manager.exporterSecret(groupId).contentEquals(preEpochExporter),
                "post-commit exporter key must differ from the pre-commit one",
            )
            assertNotNull(result.welcomeBytes)
        }
    }

    @Test
    fun testCreatorCanAddTwoMembersAndAllDecryptFollowingMessage() {
        // End-to-end regression for the David/Eden/Fred scenario: a group
        // creator (Alice) adds two members (Bob then Carol) in sequence and
        // then publishes an application message. Both members MUST be able to
        // decrypt the message. Before the stage/merge fix, the add-Carol
        // commit was outer-encrypted with the post-commit (epoch 2) key,
        // leaving Bob stuck at epoch 1 and unable to decrypt any of Alice's
        // subsequent application messages.
        runBlocking {
            val aliceMgr = createGroupManager()
            val bobMgr = createGroupManager()
            val carolMgr = createGroupManager()

            // Use 32-byte identities so they fit the MarmotGroupData
            // admin_pubkeys 32-byte slots.
            val aliceIdBytes = ByteArray(32) { 0xA1.toByte() }
            val bobIdBytes = ByteArray(32) { 0xB2.toByte() }
            val carolIdBytes = ByteArray(32) { 0xC3.toByte() }
            aliceMgr.createGroup(groupId, aliceIdBytes)
            // Install the Marmot group data extension so the Welcome carries
            // the NostrGroupData (MlsGroupManager.processWelcome requires it).
            aliceMgr.updateGroupExtensions(
                nostrGroupId = groupId,
                extensions =
                    listOf(
                        com.vitorpamplona.quartz.marmot.mip01Groups
                            .MarmotGroupData(
                                nostrGroupId = groupId,
                                adminPubkeys = listOf(aliceIdBytes.toHexKey()),
                            ).toExtension(),
                    ),
            )
            val aliceGroup = aliceMgr.getGroup(groupId)!!

            // KeyPackages are just MLS artifacts carrying identity + init key;
            // createKeyPackage on any group instance produces a usable bundle.
            val bobBundle = aliceGroup.createKeyPackage(bobIdBytes, ByteArray(0))
            val carolBundle = aliceGroup.createKeyPackage(carolIdBytes, ByteArray(0))

            val outbound = MarmotOutboundProcessor(aliceMgr)

            // --- Step 1: Alice adds Bob. CommitResult carries the pre-commit
            //     exporter secret so buildCommitEvent can outer-encrypt with
            //     the epoch-N (pre-Bob) key that Bob-less Alice held. ---
            val addBobResult = aliceMgr.addMember(groupId, bobBundle.keyPackage.toTlsBytes())
            val addBobCommitEvent =
                outbound.buildCommitEvent(
                    nostrGroupId = groupId,
                    commitBytes = addBobResult.framedCommitBytes,
                    exporterKey = addBobResult.preCommitExporterSecret,
                )

            // Bob joins via Welcome (emulated: we hand him the Welcome bytes).
            bobMgr.processWelcome(addBobResult.welcomeBytes!!, bobBundle)

            val epochAfterAddBob = aliceMgr.getGroup(groupId)!!.epoch
            assertEquals(epochAfterAddBob, bobMgr.getGroup(groupId)!!.epoch)

            // --- Step 2: Alice adds Carol. addCarolResult.preCommitExporterSecret
            //     is the epoch-N (2-member) key that Bob still holds. ---
            val addCarolResult = aliceMgr.addMember(groupId, carolBundle.keyPackage.toTlsBytes())
            val addCarolCommitEvent =
                outbound.buildCommitEvent(
                    nostrGroupId = groupId,
                    commitBytes = addCarolResult.framedCommitBytes,
                    exporterKey = addCarolResult.preCommitExporterSecret,
                )

            // Carol joins via Welcome at the new epoch.
            carolMgr.processWelcome(addCarolResult.welcomeBytes!!, carolBundle)

            // *** The key assertion ***: Bob (still at epoch 1) receives the
            // add-Carol kind:445 and must be able to:
            //   (a) outer-decrypt using his current (epoch 1) exporter key,
            //   (b) parse the MlsMessage → PublicMessage → COMMIT,
            //   (c) apply the commit and advance to epoch 2.
            val bobExporterAtE1 = bobMgr.exporterSecret(groupId)
            kotlin.test.assertContentEquals(
                bobExporterAtE1,
                addCarolResult.preCommitExporterSecret,
                "Bob's current exporter (pre-add-Carol) must match the " +
                    "pre-commit key used to outer-encrypt the add-Carol commit; " +
                    "otherwise Bob cannot decrypt and will be stuck on the old epoch.",
            )

            val decryptedMlsBytes =
                GroupEventEncryption.decrypt(
                    addCarolCommitEvent.signedEvent.content,
                    bobExporterAtE1,
                )
            // Feed the already-decoded MlsMessage(PublicMessage(Commit)) envelope
            // through processFramedCommit so MlsGroup can verify the RFC 9420 §6.1
            // FramedContentTBS signature from the sender's PublicMessage.
            bobMgr.processFramedCommit(groupId, decryptedMlsBytes)

            val epochAfterAddCarol = aliceMgr.getGroup(groupId)!!.epoch
            assertEquals(epochAfterAddBob + 1, epochAfterAddCarol)
            assertEquals(epochAfterAddCarol, bobMgr.getGroup(groupId)!!.epoch)
            assertEquals(epochAfterAddCarol, carolMgr.getGroup(groupId)!!.epoch)

            // --- Step 3: Alice sends an application message. Both members must decrypt. ---
            val msg = "Hi from Alice"
            val appEvent =
                outbound.buildGroupEventFromBytes(groupId, msg.encodeToByteArray())

            val bobKey = bobMgr.exporterSecret(groupId)
            val carolKey = carolMgr.exporterSecret(groupId)
            val aliceKey = aliceMgr.exporterSecret(groupId)
            kotlin.test.assertContentEquals(aliceKey, bobKey, "Bob must share Alice's epoch-2 key")
            kotlin.test.assertContentEquals(aliceKey, carolKey, "Carol must share Alice's epoch-2 key")

            val bobMlsBytes = GroupEventEncryption.decrypt(appEvent.signedEvent.content, bobKey)
            val bobDecrypted = bobMgr.decrypt(groupId, bobMlsBytes)
            assertEquals(msg, bobDecrypted.content.decodeToString())

            val carolMlsBytes = GroupEventEncryption.decrypt(appEvent.signedEvent.content, carolKey)
            val carolDecrypted = carolMgr.decrypt(groupId, carolMlsBytes)
            assertEquals(msg, carolDecrypted.content.decodeToString())

            // --- Alice also receives her own echo (as the app does via the
            // relay round-trip); this exercises the secretTree-based decrypt
            // path with myLeafIndex != sender or when sentKeys misses.
            val aliceMlsBytes = GroupEventEncryption.decrypt(appEvent.signedEvent.content, aliceKey)
            val aliceDecrypted = aliceMgr.decrypt(groupId, aliceMlsBytes)
            assertEquals(msg, aliceDecrypted.content.decodeToString())

            // Send a second and third message to stress the secretTree ratchet
            // and ensure getNodeSecret stays terminating across generations.
            for (i in 1..3) {
                val m = "msg-$i"
                val ev = outbound.buildGroupEventFromBytes(groupId, m.encodeToByteArray())
                val keyNow = aliceMgr.exporterSecret(groupId)
                val bytes = GroupEventEncryption.decrypt(ev.signedEvent.content, keyNow)
                val dec = bobMgr.decrypt(groupId, bytes)
                assertEquals(m, dec.content.decodeToString())
            }
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
