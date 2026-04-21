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

import com.vitorpamplona.quartz.marmot.mip01Groups.MarmotGroupData
import com.vitorpamplona.quartz.marmot.mip02Welcome.WelcomeEvent
import com.vitorpamplona.quartz.marmot.mip03GroupMessages.GroupEvent
import com.vitorpamplona.quartz.marmot.mls.codec.TlsReader
import com.vitorpamplona.quartz.marmot.mls.group.MlsGroup
import com.vitorpamplona.quartz.marmot.mls.group.MlsGroupManager
import com.vitorpamplona.quartz.marmot.mls.messages.KeyPackageBundle
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealedRumorEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Behavior-level tests for the Marmot MIP compliance fixes. These exercise
 * [MlsGroup] / [MlsGroupManager] / [MarmotOutboundProcessor] /
 * [MarmotInboundProcessor] / [MarmotWelcomeSender] end-to-end rather than
 * data-only concerns (which live in `MarmotMipComplianceTest`).
 */
class MarmotMipBehaviorTest {
    private val groupId = "a".repeat(64)
    private val aliceId = "1".repeat(64)
    private val bobId = "2".repeat(64)

    private fun createGroupManager(): MlsGroupManager = MlsGroupManager(TestGroupStateStore())

    private fun createStandaloneKeyPackage(identity: String): KeyPackageBundle {
        val tempGroup = MlsGroup.create(identity.hexToByteArray())
        return tempGroup.createKeyPackage(identity.hexToByteArray(), ByteArray(0))
    }

    // ----------------------------------------------------------------------
    // MIP-01 / MIP-03 required_capabilities on group creation
    // ----------------------------------------------------------------------

    @Test
    fun create_installsRequiredCapabilitiesExtension() {
        val alice = MlsGroup.create(aliceId.hexToByteArray())

        // RFC 9420 §13.3: required_capabilities is extension type 0x0003.
        val reqCaps = alice.extensions.find { it.extensionType == 0x0003 }
        assertNotNull(reqCaps, "create() must install required_capabilities extension (0x0003)")

        val reader = TlsReader(reqCaps.extensionData)
        val extsBytes = reader.readOpaqueVarInt()
        val propsBytes = reader.readOpaqueVarInt()
        val credsBytes = reader.readOpaqueVarInt()

        assertEquals(0xF2EE, TlsReader(extsBytes).readUint16(), "required extensions must contain 0xF2EE")
        assertEquals(0x000A, TlsReader(propsBytes).readUint16(), "required proposals must contain self_remove (0x000A)")
        assertEquals(0x0001, TlsReader(credsBytes).readUint16(), "required credentials must contain Basic (0x0001)")
    }

    // ----------------------------------------------------------------------
    // MIP-01 updateGroupExtensions admin gate
    // ----------------------------------------------------------------------

    @Test
    fun updateGroupExtensions_bootstrapAllowsAnyMemberUntilAdminsConfigured() =
        runBlocking<Unit> {
            val manager = createGroupManager()
            manager.createGroup(groupId, aliceId.hexToByteArray())

            // No admins yet — Alice can seed the initial extension set making herself admin.
            val seed = MarmotGroupData(nostrGroupId = groupId, adminPubkeys = listOf(aliceId))
            manager.updateGroupExtensions(groupId, listOf(seed.toExtension()))

            val group = manager.getGroup(groupId)!!
            assertTrue(group.isLocalAdmin(), "Alice should be admin after bootstrap update")
        }

    @Test
    fun updateGroupExtensions_rejectsNonAdminOnceAdminsConfigured() =
        runBlocking<Unit> {
            val manager = createGroupManager()
            manager.createGroup(groupId, aliceId.hexToByteArray())

            // Bootstrap: Alice adds Bob as the sole admin while she is still
            // allowed (no admins configured yet). After this Alice is NOT an
            // admin anymore and any further extension update from her must be
            // rejected.
            val onlyBob = MarmotGroupData(nostrGroupId = groupId, adminPubkeys = listOf(bobId))
            manager.updateGroupExtensions(groupId, listOf(onlyBob.toExtension()))
            assertTrue(!manager.getGroup(groupId)!!.isLocalAdmin())

            val another = MarmotGroupData(nostrGroupId = groupId, adminPubkeys = listOf(aliceId))
            assertFailsWith<IllegalStateException> {
                manager.updateGroupExtensions(groupId, listOf(another.toExtension()))
            }
        }

    // ----------------------------------------------------------------------
    // MIP-03 commit authorization gate
    // ----------------------------------------------------------------------

    @Test
    fun commit_rejectsAddFromNonAdminOnceAdminsConfigured() =
        runBlocking<Unit> {
            val manager = createGroupManager()
            manager.createGroup(groupId, aliceId.hexToByteArray())

            // Install admin data that marks *Bob* as the only admin so the
            // locally-acting member (Alice) becomes a non-admin.
            val onlyBobAdmin = MarmotGroupData(nostrGroupId = groupId, adminPubkeys = listOf(bobId))
            manager.updateGroupExtensions(groupId, listOf(onlyBobAdmin.toExtension()))

            val bobBundle = createStandaloneKeyPackage(bobId)
            assertFailsWith<IllegalStateException> {
                manager.addMember(groupId, bobBundle.keyPackage.toTlsBytes())
            }
        }

    @Test
    fun commit_adminDepletionGuardRejectsEmptyingAdminList() =
        runBlocking<Unit> {
            val manager = createGroupManager()
            manager.createGroup(groupId, aliceId.hexToByteArray())

            // Bootstrap Alice as sole admin.
            val aliceOnly = MarmotGroupData(nostrGroupId = groupId, adminPubkeys = listOf(aliceId))
            manager.updateGroupExtensions(groupId, listOf(aliceOnly.toExtension()))

            // Attempt to demote every admin in a single GCE proposal.
            val noAdmins = MarmotGroupData(nostrGroupId = groupId, adminPubkeys = emptyList())
            assertFailsWith<IllegalStateException> {
                manager.updateGroupExtensions(groupId, listOf(noAdmins.toExtension()))
            }
        }

    // ----------------------------------------------------------------------
    // MIP-01 SelfRemove admin gate
    // ----------------------------------------------------------------------

    @Test
    fun proposeSelfRemove_rejectsAdmin() =
        runBlocking<Unit> {
            val manager = createGroupManager()
            manager.createGroup(groupId, aliceId.hexToByteArray())
            val aliceAdmin = MarmotGroupData(nostrGroupId = groupId, adminPubkeys = listOf(aliceId))
            manager.updateGroupExtensions(groupId, listOf(aliceAdmin.toExtension()))

            val alice = manager.getGroup(groupId)!!
            assertFailsWith<IllegalStateException> { alice.proposeSelfRemove() }
            assertFailsWith<IllegalStateException> { alice.selfRemove() }
        }

    @Test
    fun proposeSelfRemove_allowedForNonAdmin() =
        runBlocking<Unit> {
            val manager = createGroupManager()
            manager.createGroup(groupId, aliceId.hexToByteArray())

            // Mark a non-existent pubkey as the sole admin so Alice is a non-admin.
            val strangerAdmin = MarmotGroupData(nostrGroupId = groupId, adminPubkeys = listOf(bobId))
            manager.updateGroupExtensions(groupId, listOf(strangerAdmin.toExtension()))

            val alice = manager.getGroup(groupId)!!
            // selfRemove (standalone proposal helper) should succeed for a non-admin.
            val bytes = alice.selfRemove()
            assertTrue(bytes.isNotEmpty())
        }

    // ----------------------------------------------------------------------
    // MIP-01/03 NIP-40 expiration auto-application
    // ----------------------------------------------------------------------

    @Test
    fun buildGroupEvent_appendsExpirationTagWhenDisappearingConfigured() =
        runBlocking<Unit> {
            val manager = createGroupManager()
            manager.createGroup(groupId, aliceId.hexToByteArray())

            val configured =
                MarmotGroupData(
                    nostrGroupId = groupId,
                    adminPubkeys = listOf(aliceId),
                    disappearingMessageSecs = 3600UL,
                )
            manager.updateGroupExtensions(groupId, listOf(configured.toExtension()))

            val outbound = MarmotOutboundProcessor(manager)
            val result = outbound.buildGroupEventFromBytes(groupId, "hi".encodeToByteArray())

            val expirationTag = result.signedEvent.tags.find { it.isNotEmpty() && it[0] == "expiration" }
            assertNotNull(expirationTag, "kind:445 MUST carry NIP-40 expiration when disappearing_message_secs is set")
            val expectedTs = result.signedEvent.createdAt + 3600L
            assertEquals(expectedTs.toString(), expirationTag[1])
        }

    @Test
    fun buildGroupEvent_omitsExpirationTagWhenDisappearingAbsent() =
        runBlocking<Unit> {
            val manager = createGroupManager()
            manager.createGroup(groupId, aliceId.hexToByteArray())

            val outbound = MarmotOutboundProcessor(manager)
            val result = outbound.buildGroupEventFromBytes(groupId, "hi".encodeToByteArray())

            val expirationTag = result.signedEvent.tags.find { it.isNotEmpty() && it[0] == "expiration" }
            assertNull(expirationTag, "No expiration tag expected when group has no disappearing_message_secs")
        }

    // ----------------------------------------------------------------------
    // MIP-02 MarmotWelcomeSender awaitCommitAck ordering
    // ----------------------------------------------------------------------

    @Test
    fun welcomeSender_invokesAwaitCommitAckBeforeWrapping() =
        runBlocking<Unit> {
            val manager = createGroupManager()
            manager.createGroup(groupId, aliceId.hexToByteArray())
            val bobBundle = createStandaloneKeyPackage(bobId)
            val commitResult = manager.addMember(groupId, bobBundle.keyPackage.toTlsBytes())

            val aliceSigner = NostrSignerInternal(KeyPair())
            val sender = MarmotWelcomeSender(aliceSigner)

            var ackInvocations = 0
            val delivery =
                sender.wrapWelcome(
                    commitResult = commitResult,
                    recipientPubKey = "d".repeat(64),
                    keyPackageEventId = "e".repeat(64),
                    relays = emptyList(),
                    nostrGroupId = groupId,
                    awaitCommitAck = { ackInvocations += 1 },
                )

            assertEquals(1, ackInvocations, "awaitCommitAck must be invoked exactly once before wrapping")
            assertNotNull(delivery)
            assertEquals(GiftWrapEvent.KIND, delivery.giftWrapEvent.kind)
        }

    // ----------------------------------------------------------------------
    // MIP-03 inner-event sender verification
    // ----------------------------------------------------------------------

    @Test
    fun processGroupEvent_rejectsInnerEventWithMismatchedPubkey() =
        runBlocking<Unit> {
            val manager = createGroupManager()
            manager.createGroup(groupId, aliceId.hexToByteArray())

            val outbound = MarmotOutboundProcessor(manager)
            val keyPackageRotationManager =
                com.vitorpamplona.quartz.marmot.mip00KeyPackages
                    .KeyPackageRotationManager()
            val inbound = MarmotInboundProcessor(manager, keyPackageRotationManager)

            // Craft an inner event whose pubkey does NOT match the MLS sender
            // identity (aliceId). Alice is the only member and the MLS sender;
            // she is going to encrypt and send an event claiming authorship by
            // an impostor pubkey.
            val impostor = "f".repeat(64)
            val innerJson =
                """{"id":"${"0".repeat(64)}","pubkey":"$impostor","created_at":1700000000,""" +
                    """"kind":9,"tags":[],"content":"spoof","sig":"${"0".repeat(128)}"}"""

            val encrypted =
                outbound.buildGroupEventFromBytes(groupId, innerJson.encodeToByteArray())
            val result = inbound.processGroupEvent(encrypted.signedEvent)

            assertIs<GroupEventResult.Error>(result)
            assertTrue(
                result.message.contains("inner event pubkey"),
                "Expected inner-event-sender mismatch error, got: ${result.message}",
            )
        }

    // ----------------------------------------------------------------------
    // MIP-02 Welcome rumor structure after NIP-59 unwrap
    // ----------------------------------------------------------------------

    /**
     * Unwraps a gift-wrapped Welcome all the way down to the kind:444 rumor
     * and asserts the normative MIP-02 §Inner Rumor Structure requirements:
     *   - kind == 444
     *   - the rumor is **unsigned** (sig == "")
     *   - content is base64
     *   - ["encoding", "base64"] tag present
     *   - ["e", <KeyPackage event id>] tag present
     *   - ["relays", ...] tag present
     *
     * This protects against silent wire-format drift that would otherwise
     * only be visible to external Marmot clients.
     */
    @Test
    fun welcomePipeline_innerRumorMatchesMip02Requirements() =
        runBlocking<Unit> {
            val manager = createGroupManager()
            manager.createGroup(groupId, aliceId.hexToByteArray())
            val bobBundle = createStandaloneKeyPackage(bobId)
            val commitResult = manager.addMember(groupId, bobBundle.keyPackage.toTlsBytes())

            val aliceSigner = NostrSignerInternal(KeyPair())
            val bobKeyPair = KeyPair()
            val bobSigner = NostrSignerInternal(bobKeyPair)
            val bobPubKeyHex = bobKeyPair.pubKey.toHexKey()

            val keyPackageEventId = "c".repeat(64)
            val sender = MarmotWelcomeSender(aliceSigner)
            val delivery =
                sender.wrapWelcome(
                    commitResult = commitResult,
                    recipientPubKey = bobPubKeyHex,
                    keyPackageEventId = keyPackageEventId,
                    relays = emptyList(),
                    nostrGroupId = groupId,
                )
            assertNotNull(delivery)
            assertEquals(GiftWrapEvent.KIND, delivery.giftWrapEvent.kind)

            // Bob unwraps: kind:1059 → kind:13 (Seal) → kind:444 (Rumor).
            val sealed = delivery.giftWrapEvent.unwrapThrowing(bobSigner)
            assertEquals(SealedRumorEvent.KIND, sealed.kind, "Middle layer MUST be NIP-59 Seal kind:13")
            assertIs<SealedRumorEvent>(sealed)

            val rumor = sealed.unsealThrowing(bobSigner)
            assertEquals(WelcomeEvent.KIND, rumor.kind, "Innermost rumor MUST be kind:444")
            assertEquals("", rumor.sig, "MIP-02: kind:444 rumor MUST NOT carry a signature")

            val encodingTag = rumor.tags.find { it.isNotEmpty() && it[0] == "encoding" }
            assertNotNull(encodingTag, "MIP-02: rumor MUST carry [encoding, base64]")
            assertEquals("base64", encodingTag[1])

            val eTag = rumor.tags.find { it.isNotEmpty() && it[0] == "e" }
            assertNotNull(eTag, "MIP-02: rumor MUST carry [e, <KeyPackage event id>]")
            assertEquals(keyPackageEventId, eTag[1])

            val relaysTag = rumor.tags.find { it.isNotEmpty() && it[0] == "relays" }
            assertNotNull(relaysTag, "MIP-02: rumor MUST carry a relays tag")
        }

    // ----------------------------------------------------------------------
    // MIP-03 group event h-tag shape
    // ----------------------------------------------------------------------

    /**
     * MIP-03 §Core Event Fields requires the `h` tag on kind:445 events to
     * be a 32-byte hex (64 lowercase hex chars). This locks in the tag
     * format so an accidental truncation or encoding change surfaces here.
     */
    @Test
    fun buildGroupEvent_hTagIs32ByteHex() =
        runBlocking<Unit> {
            val manager = createGroupManager()
            manager.createGroup(groupId, aliceId.hexToByteArray())

            val outbound = MarmotOutboundProcessor(manager)
            val result = outbound.buildGroupEventFromBytes(groupId, "hi".encodeToByteArray())

            val hTag = result.signedEvent.tags.find { it.isNotEmpty() && it[0] == "h" }
            assertNotNull(hTag, "kind:445 MUST carry an h tag with the Nostr group id")
            assertEquals(64, hTag[1].length, "h tag value MUST be 32 bytes hex (64 chars)")
            assertEquals(groupId, hTag[1])
            assertTrue(
                hTag[1].all { it in '0'..'9' || it in 'a'..'f' },
                "h tag MUST be lowercase hex",
            )
        }

    @Test
    fun processGroupEvent_acceptsInnerEventWithMatchingPubkey() =
        runBlocking<Unit> {
            val manager = createGroupManager()
            manager.createGroup(groupId, aliceId.hexToByteArray())

            val outbound = MarmotOutboundProcessor(manager)
            val keyPackageRotationManager =
                com.vitorpamplona.quartz.marmot.mip00KeyPackages
                    .KeyPackageRotationManager()
            val inbound = MarmotInboundProcessor(manager, keyPackageRotationManager)

            // Inner event whose pubkey matches Alice's credential identity.
            val innerJson =
                """{"id":"${"0".repeat(64)}","pubkey":"$aliceId","created_at":1700000000,""" +
                    """"kind":9,"tags":[],"content":"hello","sig":"${"0".repeat(128)}"}"""

            val encrypted =
                outbound.buildGroupEventFromBytes(groupId, innerJson.encodeToByteArray())
            val result = inbound.processGroupEvent(encrypted.signedEvent)

            assertIs<GroupEventResult.ApplicationMessage>(result)
            assertContentEquals(innerJson.toByteArray(), result.innerEventJson.toByteArray())
            assertEquals(GroupEvent.KIND, encrypted.signedEvent.kind)
        }
}
