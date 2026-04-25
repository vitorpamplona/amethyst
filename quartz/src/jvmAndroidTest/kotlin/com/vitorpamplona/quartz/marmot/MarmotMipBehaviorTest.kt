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
            assertFailsWith<IllegalStateException> { alice.buildSelfRemoveProposalMessage() }
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
            // Standalone SelfRemove proposal helper should succeed for a non-admin.
            val (bytes, _) = alice.buildSelfRemoveProposalMessage()
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

            // disappearing_message_secs is a v3+ field. CURRENT_VERSION is held at 2 for
            // MDK interop, so the encoder only emits the field for version ≥ 3 — pin
            // version=3 here so the outbound processor actually sees the setting.
            val configured =
                MarmotGroupData(
                    version = 3,
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
    // MIP-03 inbound authorization gates
    // ----------------------------------------------------------------------
    //
    // The local-commit path has always run the authorization-set + admin-
    // depletion guards (see `commit_adminDepletionGuardRejectsEmptyingAdminList`
    // above). The inbound counterpart was missing — `processCommitInner`
    // didn't call them, so a peer could send a commit our local code would
    // never have produced and we'd accept it. These tests exercise the
    // refactored guard functions directly with a `committerLeafIndex`
    // parameter, which is the shape `processCommitInner` calls them in.

    @Test
    fun enforceAuthorizedProposalSet_rejectsNonAdminCommitterRemove() =
        runBlocking<Unit> {
            // Group with Alice as the only configured admin.
            val manager = createGroupManager()
            manager.createGroup(groupId, aliceId.hexToByteArray())
            manager.updateGroupExtensions(
                groupId,
                listOf(MarmotGroupData(nostrGroupId = groupId, adminPubkeys = listOf(aliceId)).toExtension()),
            )
            // Add Bob (non-admin) so leaf index 1 is occupied.
            val bobBundle = createStandaloneKeyPackage(bobId)
            manager.addMember(groupId, bobBundle.keyPackage.toTlsBytes())

            val alice = manager.getGroup(groupId)!!
            // Bob (leaf 1) is NOT an admin. Pretend he committed a Remove of
            // himself authored by himself — `enforceAuthorizedProposalSet`
            // must reject because Remove is admin-only.
            val proposals =
                listOf(
                    com.vitorpamplona.quartz.marmot.mls.group
                        .PendingProposal(
                            proposal =
                                com.vitorpamplona.quartz.marmot.mls.messages
                                    .Proposal
                                    .Remove(removedLeafIndex = 0),
                            senderLeafIndex = 1,
                        ),
                )
            val ex =
                assertFailsWith<IllegalStateException> {
                    alice.enforceAuthorizedProposalSet(proposals, committerLeafIndex = 1)
                }
            assertTrue(
                ex.message!!.contains("non-admin members may only commit"),
                "expected MIP-03 violation message, got: ${ex.message}",
            )
        }

    @Test
    fun enforceAuthorizedProposalSet_acceptsAdminCommitterFoldingAnotherMembersSelfRemove() =
        runBlocking<Unit> {
            // Mirrors marmot-interop test 15: Bob (admin) commits a
            // SelfRemove proposal authored by Carol. Inline-as-fold flows
            // tag the proposal with the committer's leaf index, so Bob's
            // Remove-style fold of Carol's leaf still authenticates.
            val manager = createGroupManager()
            manager.createGroup(groupId, aliceId.hexToByteArray())
            manager.updateGroupExtensions(
                groupId,
                listOf(MarmotGroupData(nostrGroupId = groupId, adminPubkeys = listOf(aliceId)).toExtension()),
            )

            val alice = manager.getGroup(groupId)!!
            // Alice (leaf 0) is admin. The committer-is-admin shortcut fires
            // before the per-proposal author check, so even a heterogeneous
            // proposal list passes.
            val proposals =
                listOf(
                    com.vitorpamplona.quartz.marmot.mls.group
                        .PendingProposal(
                            proposal =
                                com.vitorpamplona.quartz.marmot.mls.messages
                                    .Proposal
                                    .SelfRemove(),
                            senderLeafIndex = 99,
                        ),
                )
            // Should not throw.
            alice.enforceAuthorizedProposalSet(proposals, committerLeafIndex = 0)
        }

    @Test
    fun enforceNoAdminDepletion_rejectsCommitThatEmptiesAdminList() =
        runBlocking<Unit> {
            val manager = createGroupManager()
            manager.createGroup(groupId, aliceId.hexToByteArray())
            manager.updateGroupExtensions(
                groupId,
                listOf(MarmotGroupData(nostrGroupId = groupId, adminPubkeys = listOf(aliceId)).toExtension()),
            )

            val alice = manager.getGroup(groupId)!!
            val proposals =
                listOf(
                    com.vitorpamplona.quartz.marmot.mls.group
                        .PendingProposal(
                            proposal =
                                com.vitorpamplona.quartz.marmot.mls.messages
                                    .Proposal
                                    .GroupContextExtensions(
                                        extensions =
                                            listOf(
                                                MarmotGroupData(
                                                    nostrGroupId = groupId,
                                                    adminPubkeys = emptyList(),
                                                ).toExtension(),
                                            ),
                                    ),
                            senderLeafIndex = 0,
                        ),
                )
            assertFailsWith<IllegalStateException> {
                alice.enforceNoAdminDepletion(proposals)
            }
        }

    // ----------------------------------------------------------------------
    // RFC 9420 §5.2 ProposalRef hashing — local standalone proposals
    // ----------------------------------------------------------------------

    @Test
    fun buildSelfRemoveProposalMessage_stagesPendingWithAuthenticatedContentBytes() =
        runBlocking<Unit> {
            // Setup: Alice creates a group where she is NOT admin (a
            // throwaway bobId is the sole configured admin). Without that,
            // `buildSelfRemoveProposalMessage` rejects per MIP-01.
            val manager = createGroupManager()
            manager.createGroup(groupId, aliceId.hexToByteArray())
            manager.updateGroupExtensions(
                groupId,
                listOf(MarmotGroupData(nostrGroupId = groupId, adminPubkeys = listOf(bobId)).toExtension()),
            )

            val alice = manager.getGroup(groupId)!!
            assertEquals(0, alice.pendingProposalsSnapshot().size)

            val (_, _) = alice.buildSelfRemoveProposalMessage()

            val staged = alice.pendingProposalsSnapshot()
            assertEquals(1, staged.size, "buildSelfRemoveProposalMessage must also stage to pending pool")
            val entry = staged.single()
            assertIs<com.vitorpamplona.quartz.marmot.mls.messages.Proposal.SelfRemove>(entry.proposal)
            assertEquals(alice.leafIndex, entry.senderLeafIndex)
            // The captured AC bytes are what RFC 9420 §5.2's MakeProposalRef
            // hashes — must be present so a peer's commit referencing this
            // proposal by hash resolves against our pool.
            assertNotNull(
                entry.authenticatedContentBytes,
                "RFC 9420 §5.2: standalone-published proposals must carry the encoded AuthenticatedContent",
            )
            assertTrue(entry.authenticatedContentBytes.isNotEmpty())
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

    // ----------------------------------------------------------------------
    // RFC 9420 §7.9 parent_hash chain validation on Welcome
    // ----------------------------------------------------------------------

    /**
     * A freshly-created single-member tree has no COMMIT-source leaves
     * (the seed leaf is KEY_PACKAGE source) and no parents — the
     * static-tree validator must accept it as trivially valid.
     */
    @Test
    fun verifyTreeParentHashesForJoin_acceptsSingleMemberTree() {
        val alice = MlsGroup.create(aliceId.hexToByteArray())
        val tree =
            com.vitorpamplona.quartz.marmot.mls.tree.RatchetTree
                .decodeTls(
                    com.vitorpamplona.quartz.marmot.mls.codec
                        .TlsReader(alice.exportTreeBytes()),
                )
        assertNull(MlsGroup.verifyTreeParentHashesForJoin(tree))
    }

    /**
     * Tampering a COMMIT-source leaf's parent_hash produces a clear
     * rejection from the validator. This is the exact attack a
     * misconfigured GroupInfo signer (or a malicious one) could mount —
     * the tree_hash check would still pass, but parent_hash chain
     * validation must catch it.
     */
    @Test
    fun verifyTreeParentHashesForJoin_rejectsTamperedLeafParentHash() =
        runBlocking<Unit> {
            // Build a 3-member group so we have at least one
            // COMMIT-source leaf with a real parent_hash chain.
            val manager = createGroupManager()
            manager.createGroup(groupId, aliceId.hexToByteArray())
            manager.addMember(groupId, createStandaloneKeyPackage(bobId).keyPackage.toTlsBytes())
            // Trigger a self-update on Alice so her leaf becomes
            // COMMIT-source with a non-empty parent_hash chain. (Using
            // updateGroupExtensions as a stand-in to force a commit
            // that touches the path.)
            val seed = MarmotGroupData(nostrGroupId = groupId, adminPubkeys = listOf(aliceId))
            manager.updateGroupExtensions(groupId, listOf(seed.toExtension()))

            val alice = manager.getGroup(groupId)!!
            val originalTree =
                com.vitorpamplona.quartz.marmot.mls.tree.RatchetTree
                    .decodeTls(
                        com.vitorpamplona.quartz.marmot.mls.codec
                            .TlsReader(alice.exportTreeBytes()),
                    )

            // Sanity: the original tree validates.
            assertNull(MlsGroup.verifyTreeParentHashesForJoin(originalTree))

            // Find the first COMMIT-source leaf and tamper its parent_hash.
            var tamperedLeafIdx = -1
            for (i in 0 until originalTree.leafCount) {
                val leaf = originalTree.getLeaf(i) ?: continue
                if (leaf.leafNodeSource ==
                    com.vitorpamplona.quartz.marmot.mls.tree.LeafNodeSource.COMMIT
                ) {
                    val tampered = leaf.copy(parentHash = ByteArray(32) { 0x99.toByte() })
                    originalTree.setLeaf(i, tampered)
                    tamperedLeafIdx = i
                    break
                }
            }
            assertTrue(tamperedLeafIdx >= 0, "test setup must produce a COMMIT-source leaf")

            val reason = MlsGroup.verifyTreeParentHashesForJoin(originalTree)
            assertNotNull(reason)
            assertTrue(
                reason.contains("leaf $tamperedLeafIdx parent_hash mismatch"),
                "rejection message must name the tampered leaf: $reason",
            )
        }

    // ----------------------------------------------------------------------
    // RFC 9420 §7.2 / §12.4.2 required_capabilities enforcement
    // ----------------------------------------------------------------------

    /**
     * Round-trip: the `required_capabilities` extension Marmot installs on
     * every fresh group decodes back to its declared (extensions, proposals,
     * credentials) triple.
     */
    @Test
    fun findRequiredCapabilities_decodesMarmotExtensionInstalledByCreate() {
        val alice = MlsGroup.create(aliceId.hexToByteArray())
        val req =
            MlsGroup.findRequiredCapabilities(alice.extensions)
                ?: error("required_capabilities must be present after create()")
        assertEquals(listOf(0xF2EE), req.extensions, "MarmotGroupData (0xF2EE) must be required")
        assertEquals(listOf(0x000A), req.proposals, "SelfRemove (0x000A) must be required")
        assertEquals(listOf(0x0001), req.credentials, "Basic credential must be required")
    }

    /**
     * `requireCapabilitiesMeetRequirements` must throw when ANY required
     * type is missing — extension OR proposal OR credential — and the
     * error must name the missing types so an interop debugger can
     * diagnose without grepping.
     */
    @Test
    fun requireCapabilitiesMeetRequirements_rejectsMissingExtension() {
        val req =
            MlsGroup.Companion.RequiredCapabilities(
                extensions = listOf(0xF2EE),
                proposals = listOf(0x000A),
                credentials = listOf(0x0001),
            )
        // Missing 0xF2EE.
        val caps =
            com.vitorpamplona.quartz.marmot.mls.tree.Capabilities(
                extensions = emptyList(),
                proposals = listOf(0x000A),
                credentials = listOf(0x0001),
            )
        val ex =
            assertFailsWith<IllegalStateException> {
                MlsGroup.requireCapabilitiesMeetRequirements(caps, req, "test")
            }
        assertTrue(
            ex.message!!.contains("extensions=[62190]") || ex.message!!.contains("0xF2EE"),
            "error must name the missing extension type: ${ex.message}",
        )
    }

    @Test
    fun requireCapabilitiesMeetRequirements_rejectsMissingProposal() {
        val req =
            MlsGroup.Companion.RequiredCapabilities(
                extensions = emptyList(),
                proposals = listOf(0x000A),
                credentials = emptyList(),
            )
        val caps =
            com.vitorpamplona.quartz.marmot.mls.tree.Capabilities(
                extensions = emptyList(),
                proposals = emptyList(),
                credentials = listOf(0x0001),
            )
        assertFailsWith<IllegalStateException> {
            MlsGroup.requireCapabilitiesMeetRequirements(caps, req, "test")
        }
    }

    @Test
    fun requireCapabilitiesMeetRequirements_passesWhenCapsAreSuperset() {
        val req =
            MlsGroup.Companion.RequiredCapabilities(
                extensions = listOf(0xF2EE),
                proposals = listOf(0x000A),
                credentials = listOf(0x0001),
            )
        val caps =
            com.vitorpamplona.quartz.marmot.mls.tree.Capabilities(
                extensions = listOf(0xF2EE, 0x1234),
                proposals = listOf(0x000A, 0x000B),
                credentials = listOf(0x0001, 0x0002),
            )
        // Should not throw.
        MlsGroup.requireCapabilitiesMeetRequirements(caps, req, "test")
    }

    /**
     * End-to-end gate: `addMember` MUST reject a KeyPackage whose leaf
     * doesn't advertise the group's `required_capabilities`. We tamper the
     * KP's leaf capabilities to drop SelfRemove, then re-encode and re-sign
     * to keep the KP signature valid (RFC 9420 §10.1) so the rejection is
     * coming from the capability gate and not the signature check.
     */
    @Test
    fun addMember_rejectsKeyPackageMissingRequiredProposal() =
        runBlocking<Unit> {
            // Setup: standard Marmot group (required_capabilities lists
            // SelfRemove + MarmotGroupData + Basic).
            val manager = createGroupManager()
            manager.createGroup(groupId, aliceId.hexToByteArray())

            // Bob's KP, but with SelfRemove stripped from his leaf
            // capabilities. He's still announcing himself as a Marmot peer
            // — just lying about supporting SelfRemove.
            val tampered = createKeyPackageWithoutSelfRemove(bobId)

            assertFailsWith<IllegalStateException> {
                manager.addMember(groupId, tampered.toTlsBytes())
            }
        }

    /**
     * Build a KeyPackage whose leaf [Capabilities] does NOT list 0x000A
     * (SelfRemove), then re-sign so the KP's outer signature still
     * validates. Useful for testing the §7.2 gate in isolation.
     */
    private fun createKeyPackageWithoutSelfRemove(identity: String): com.vitorpamplona.quartz.marmot.mls.messages.MlsKeyPackage {
        val tempGroup = MlsGroup.create(identity.hexToByteArray())
        val bundle = tempGroup.createKeyPackage(identity.hexToByteArray(), ByteArray(0))
        val original = bundle.keyPackage
        val originalLeaf = original.leafNode

        // Strip SelfRemove (0x000A) from the leaf's advertised proposals.
        val tamperedCaps =
            originalLeaf.capabilities.copy(
                proposals = originalLeaf.capabilities.proposals.filter { it != 0x000A },
            )
        // Re-build the leaf node and re-sign its TBS so the leaf signature
        // still verifies (otherwise we'd hit the LeafNode signature check
        // before the capability gate fires).
        val tamperedLeaf =
            originalLeaf.copy(capabilities = tamperedCaps).let { lf ->
                val tbs = lf.encodeTbs()
                val sig =
                    com.vitorpamplona.quartz.marmot.mls.crypto.MlsCryptoProvider
                        .signWithLabel(bundle.signaturePrivateKey, "LeafNodeTBS", tbs)
                lf.copy(signature = sig)
            }
        // Re-sign the KeyPackage TBS over the new leaf.
        val unsigned = original.copy(leafNode = tamperedLeaf, signature = ByteArray(0))
        return unsigned.copy(
            signature =
                com.vitorpamplona.quartz.marmot.mls.crypto.MlsCryptoProvider
                    .signWithLabel(bundle.signaturePrivateKey, "KeyPackageTBS", unsigned.encodeTbs()),
        )
    }

    // ----------------------------------------------------------------------
    // RFC 9420 §5.3 psk_secret derivation
    // ----------------------------------------------------------------------

    /**
     * Empty PSK list MUST collapse to the all-zero `default_psk_secret`
     * (RFC 9420 §8.1) — every epoch where no PSKs are proposed feeds zeros
     * into the joiner_secret extract step.
     */
    @Test
    fun computePskSecret_emptyListReturnsAllZeros() {
        val alice = MlsGroup.create(aliceId.hexToByteArray())
        val out = alice.computePskSecret(emptyList())
        assertEquals(32, out.size, "psk_secret length must be Nh = 32 for SHA-256")
        assertTrue(out.all { it == 0.toByte() }, "default_psk_secret is all zeros")
    }

    /**
     * Single external PSK case — verify the derived `psk_secret` matches the
     * RFC 9420 §5.3 reference computation:
     *
     * ```
     * psk_extracted_0 = HKDF.Extract(salt = 0, ikm = psk_0)
     * psk_input_0     = ExpandWithLabel(psk_extracted_0, "derived psk",
     *                                    PSKLabel(id_0, 0, 1), 32)
     * psk_secret_0    = HKDF.Extract(salt = 0, ikm = psk_input_0)
     * ```
     *
     * The previous implementation HKDF-Extracted the bare PSK value with the
     * running pskSecret as salt and never built a PSKLabel — its output
     * would not match this expected value.
     */
    @Test
    fun computePskSecret_singleExternalPsk_matchesSpecDerivation() {
        val alice = MlsGroup.create(aliceId.hexToByteArray())
        val pskId = ByteArray(16) { (it + 1).toByte() }
        val pskNonce = ByteArray(16) { (0x80 or it).toByte() }
        val pskValue = ByteArray(32) { (0xA0 or (it and 0x0F)).toByte() }
        alice.registerPsk(pskId, pskValue)

        val proposal =
            com.vitorpamplona.quartz.marmot.mls.messages
                .Proposal
                .Psk(pskType = 1, pskId = pskId, pskNonce = pskNonce)

        val actual = alice.computePskSecret(listOf(proposal))

        // Reference computation per §5.3 (PSKType=1, no usage/group/epoch).
        val zero = ByteArray(32)
        val crypto = com.vitorpamplona.quartz.marmot.mls.crypto.MlsCryptoProvider
        val pskExtracted = crypto.hkdfExtract(salt = zero, ikm = pskValue)

        val labelWriter =
            com.vitorpamplona.quartz.marmot.mls.codec
                .TlsWriter()
        labelWriter.putUint8(1) // PSKType external
        labelWriter.putOpaqueVarInt(pskId)
        labelWriter.putOpaqueVarInt(pskNonce)
        labelWriter.putUint16(0) // index
        labelWriter.putUint16(1) // count

        val pskInput =
            crypto.expandWithLabel(
                secret = pskExtracted,
                label = "derived psk",
                context = labelWriter.toByteArray(),
                length = 32,
            )
        val expected = crypto.hkdfExtract(salt = zero, ikm = pskInput)

        assertContentEquals(expected, actual, "psk_secret must match RFC 9420 §5.3 derivation")
    }

    /**
     * Resumption PSK (psktype = 2) carries usage/psk_group_id/psk_epoch
     * fields that aren't representable on `Proposal.Psk` today. Encoding a
     * PSKLabel without them would silently diverge from spec-conformant
     * peers — we reject loudly until the proposal type is widened.
     */
    @Test
    fun computePskSecret_resumptionPskRejectsUntilProposalWidened() {
        val alice = MlsGroup.create(aliceId.hexToByteArray())
        val pskId = ByteArray(16) { it.toByte() }
        alice.registerPsk(pskId, ByteArray(32))

        val proposal =
            com.vitorpamplona.quartz.marmot.mls.messages
                .Proposal
                .Psk(pskType = 2, pskId = pskId, pskNonce = ByteArray(16))

        assertFailsWith<IllegalStateException> {
            alice.computePskSecret(listOf(proposal))
        }
    }

    /**
     * The `(index, count)` tail of PSKLabel ensures peers that resolve the
     * SAME PSK in different list positions derive DIFFERENT psk_secret —
     * the previous implementation ignored ordering entirely.
     */
    @Test
    fun computePskSecret_orderingChangesOutput() {
        val alice = MlsGroup.create(aliceId.hexToByteArray())
        val idA = ByteArray(16) { 0x11 }
        val idB = ByteArray(16) { 0x22 }
        alice.registerPsk(idA, ByteArray(32) { 0x33 })
        alice.registerPsk(idB, ByteArray(32) { 0x44 })

        val pskA =
            com.vitorpamplona.quartz.marmot.mls.messages
                .Proposal
                .Psk(pskType = 1, pskId = idA, pskNonce = ByteArray(8))
        val pskB =
            com.vitorpamplona.quartz.marmot.mls.messages
                .Proposal
                .Psk(pskType = 1, pskId = idB, pskNonce = ByteArray(8))

        val ab = alice.computePskSecret(listOf(pskA, pskB))
        val ba = alice.computePskSecret(listOf(pskB, pskA))
        assertTrue(
            !ab.contentEquals(ba),
            "PSKLabel index/count means [A,B] and [B,A] must derive distinct psk_secret",
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
