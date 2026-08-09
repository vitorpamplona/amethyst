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
package com.vitorpamplona.quartz.concord.cord06Rekey

import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityFactory
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityState
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEdition
import com.vitorpamplona.quartz.concord.crypto.ConcordKeyDerivation
import com.vitorpamplona.quartz.concord.crypto.ControlPlaneKeys
import com.vitorpamplona.quartz.concord.envelope.ConcordStreamEnvelope
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.RumorAssembler
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The `control_root` rolling with the `community_root` at every Refounding
 * (CORD-02 §2, CORD-06 §1/§3): base blobs carry the new pk to members and the new
 * secret to staff, and the blob's width declares which form it is.
 */
class ControlRootRotationTest {
    private val owner = NostrSignerInternal(KeyPair()) // rotator, and staff by definition
    private val moderator = NostrSignerInternal(KeyPair()) // staff
    private val member = NostrSignerInternal(KeyPair()) // plain member
    private val removed = NostrSignerInternal(KeyPair())

    private val newRoot = ByteArray(32) { 0x5A }
    private val newControlRoot = ByteArray(32) { 0x6B }
    private val now = 1_700_000_000L

    @Test
    fun theBlobWidthDeclaresItsForm() {
        val scope = ByteArray(32) { 0x01 }
        val key = ByteArray(32) { 0x02 }
        val pk = ByteArray(32) { 0x03 }
        val secret = ByteArray(32) { 0x04 }

        assertEquals(RekeyPayload.SIZE_CHANNEL, RekeyPayload(scope, 1, key).encode().size)
        assertEquals(RekeyPayload.SIZE_BASE_MEMBER, RekeyPayload(scope, 1, key, pk).encode().size)
        assertEquals(RekeyPayload.SIZE_BASE_STAFF, RekeyPayload(scope, 1, key, pk, secret).encode().size)
        assertEquals(72, RekeyPayload.SIZE_CHANNEL)
        assertEquals(104, RekeyPayload.SIZE_BASE_MEMBER)
        assertEquals(136, RekeyPayload.SIZE_BASE_STAFF)

        // Round-trips keep exactly what each form carries, and nothing it doesn't.
        val channel = RekeyPayload.decode(RekeyPayload(scope, 1, key).encode())
        assertNotNull(channel)
        assertNull(channel.newControlPk)
        assertNull(channel.newControlRoot)

        val memberBlob = RekeyPayload.decode(RekeyPayload(scope, 1, key, pk).encode())
        assertNotNull(memberBlob)
        assertContentEquals(pk, memberBlob.newControlPk)
        assertNull(memberBlob.newControlRoot, "a member's blob must never carry the write key")

        val staffBlob = RekeyPayload.decode(RekeyPayload(scope, 1, key, pk, secret).encode())
        assertNotNull(staffBlob)
        assertContentEquals(pk, staffBlob.newControlPk)
        assertContentEquals(secret, staffBlob.newControlRoot)

        // Any other width is malformed and the blob is dropped.
        assertNull(RekeyPayload.decode(ByteArray(71)))
        assertNull(RekeyPayload.decode(ByteArray(103)))
        assertNull(RekeyPayload.decode(ByteArray(137)))
    }

    @Test
    fun staffGetTheSecretMembersOnlyThePubkeyAndRemovedNothing() =
        runTest {
            val community = ConcordCommunityFactory.create(owner, "Test", now)
            val build =
                ConcordRefounding.build(
                    rotatorSigner = owner,
                    communityId = community.communityId,
                    priorRoot = community.communityRoot,
                    newRoot = newRoot,
                    newControlRoot = newControlRoot,
                    rootEpoch = community.rootEpoch,
                    priorControlWraps = community.genesisWraps,
                    priorControlKeys = community.controlPlane,
                    recipientsXOnly = listOf(owner.pubKey, moderator.pubKey, member.pubKey),
                    staffXOnly = setOf(owner.pubKey, moderator.pubKey),
                    createdAt = now,
                    ownerPubKey = owner.pubKey,
                )

            val baseRekey = ConcordKeyDerivation.baseRekeyAddress(community.communityRoot, community.communityId, build.newEpoch)

            suspend fun received(who: NostrSignerInternal) = ConcordRefounding.findNewRoot(build.rekeyWraps, baseRekey, who, community.communityId, community.communityRoot, community.rootEpoch)

            val expectedPk = ConcordKeyDerivation.controlSignerKey(newControlRoot, community.communityId, build.newEpoch).publicKey

            val asModerator = received(moderator)
            assertNotNull(asModerator)
            assertContentEquals(newRoot, asModerator.newRoot)
            assertContentEquals(expectedPk, asModerator.newControlPk)
            assertContentEquals(newControlRoot, asModerator.newControlRoot, "staff must receive the new write key")

            val asMember = received(member)
            assertNotNull(asMember)
            assertContentEquals(newRoot, asMember.newRoot)
            assertContentEquals(expectedPk, asMember.newControlPk, "every member must receive the new address")
            assertNull(asMember.newControlRoot, "a plain member must never receive the write key")

            assertNull(received(removed), "a removed member receives no blob at all")
        }

    @Test
    fun theRotatedPlaneIsWritableByStaffAndReadableByEveryMember() =
        runTest {
            val community = ConcordCommunityFactory.create(owner, "Test", now, description = "A place")
            val build =
                ConcordRefounding.build(
                    rotatorSigner = owner,
                    communityId = community.communityId,
                    priorRoot = community.communityRoot,
                    newRoot = newRoot,
                    newControlRoot = newControlRoot,
                    rootEpoch = community.rootEpoch,
                    priorControlWraps = community.genesisWraps,
                    priorControlKeys = community.controlPlane,
                    recipientsXOnly = listOf(owner.pubKey, member.pubKey),
                    staffXOnly = setOf(owner.pubKey),
                    createdAt = now,
                    ownerPubKey = owner.pubKey,
                )

            // The rotator's own view writes; a member's view of the same epoch only reads.
            assertTrue(build.newControlKeys.canWrite)
            val memberView =
                ControlPlaneKeys.forMember(newRoot, community.communityId, build.newEpoch, build.newControlKeys.address)
            assertFalse(memberView.canWrite)

            // Every compacted wrap sits at the new signer's address and opens for the member.
            assertTrue(build.controlWraps.isNotEmpty())
            build.controlWraps.forEach { assertEquals(build.newControlKeys.address, it.pubKey) }

            val editions =
                build.controlWraps.mapNotNull { wrap ->
                    ConcordStreamEnvelope.openOrNull(wrap, memberView)?.let { ControlEdition.fromRumor(it.rumor) }
                }
            val folded = ConcordCommunityState.fold(editions, owner.pubKey)
            assertEquals("Test", folded.metadata?.name)
            assertTrue(folded.channels.isNotEmpty())
        }

    @Test
    fun aStaffBlobWhoseSecretDoesNotDeriveToItsPubkeyIsRefused() =
        runTest {
            val community = ConcordCommunityFactory.create(owner, "Test", now)
            val newEpoch = community.rootEpoch + 1

            // A rotator that splits the plane from its own readers: the delivered secret derives
            // to a DIFFERENT address than the one every member was handed (CORD-06 §1).
            val mismatchedPk = ConcordKeyDerivation.controlSignerKey(ByteArray(32) { 0x7C }, community.communityId, newEpoch).publicKey
            val blob =
                ConcordRekey.blobForSigner(
                    rotatorSigner = owner,
                    recipientXOnly = moderator.pubKey.hexToByteArray(),
                    scopeId = ConcordRekey.ROOT_SCOPE,
                    newEpoch = newEpoch,
                    newKey = newRoot,
                    newControlPk = mismatchedPk,
                    newControlRoot = newControlRoot,
                )

            val baseRekey = ConcordKeyDerivation.baseRekeyAddress(community.communityRoot, community.communityId, newEpoch)
            val prevCommit = ConcordKeyDerivation.epochKeyCommitment(community.rootEpoch, community.communityRoot).toHexKey()
            val tags = ConcordRekey.tags(ConcordRekey.ROOT_SCOPE, newEpoch, community.rootEpoch, prevCommit, 0, 1)
            val rumor =
                RumorAssembler.assembleRumor<Event>(owner.pubKey, now, ConcordRekey.KIND, tags, ConcordRekey.encodeContent(listOf(blob)))
            val wrap = ConcordStreamEnvelope.wrap(rumor, baseRekey, owner, encrypted = true, createdAt = now)

            assertNull(
                ConcordRefounding.findNewRoot(listOf(wrap), baseRekey, moderator, community.communityId, community.communityRoot, community.rootEpoch),
                "a mismatched control pair must be refused rather than adopted",
            )
        }

    @Test
    fun aLegacySeventyTwoByteBaseBlobStillDeliversItsRoot() =
        runTest {
            // A pre-split rotation carries no control material; it is honored when reading old
            // epochs (CORD-06 §3) and its acceptor keeps folding at the legacy address.
            val community = ConcordCommunityFactory.create(owner, "Test", now)
            val newEpoch = community.rootEpoch + 1

            val blob =
                ConcordRekey.blobForSigner(
                    rotatorSigner = owner,
                    recipientXOnly = member.pubKey.hexToByteArray(),
                    scopeId = ConcordRekey.ROOT_SCOPE,
                    newEpoch = newEpoch,
                    newKey = newRoot,
                )

            val baseRekey = ConcordKeyDerivation.baseRekeyAddress(community.communityRoot, community.communityId, newEpoch)
            val prevCommit = ConcordKeyDerivation.epochKeyCommitment(community.rootEpoch, community.communityRoot).toHexKey()
            val tags = ConcordRekey.tags(ConcordRekey.ROOT_SCOPE, newEpoch, community.rootEpoch, prevCommit, 0, 1)
            val rumor =
                RumorAssembler.assembleRumor<Event>(owner.pubKey, now, ConcordRekey.KIND, tags, ConcordRekey.encodeContent(listOf(blob)))
            val wrap = ConcordStreamEnvelope.wrap(rumor, baseRekey, owner, encrypted = true, createdAt = now)

            val got = ConcordRefounding.findNewRoot(listOf(wrap), baseRekey, member, community.communityId, community.communityRoot, community.rootEpoch)
            assertNotNull(got)
            assertContentEquals(newRoot, got.newRoot)
            assertNull(got.newControlPk, "a legacy base blob announces a pre-split epoch")
            assertNull(got.newControlRoot)
        }
}
