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
import com.vitorpamplona.quartz.concord.cord02Community.ImagePointer
import com.vitorpamplona.quartz.concord.cord04Roles.ChannelEntity
import com.vitorpamplona.quartz.concord.cord04Roles.ConcordJson
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEdition
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEditionBuilder
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEntityKind
import com.vitorpamplona.quartz.concord.cord04Roles.MetadataEntity
import com.vitorpamplona.quartz.concord.crypto.ConcordKeyDerivation
import com.vitorpamplona.quartz.concord.envelope.ConcordStreamEnvelope
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConcordRefoundingTest {
    private val owner = NostrSignerInternal(KeyPair())
    private val alice = NostrSignerInternal(KeyPair()) // retained
    private val bob = NostrSignerInternal(KeyPair()) // retained
    private val carol = NostrSignerInternal(KeyPair()) // removed

    private val newRoot = ByteArray(32) { 0x5A }

    /** The fresh staff write key minted beside [newRoot] at every Refounding (CORD-02 §2). */
    private val newControlRoot = ByteArray(32) { 0x6B }
    private val now = 1_700_000_000L

    @Test
    fun retainedMembersGetNewRootRemovedDoesNot() =
        runTest {
            val community = ConcordCommunityFactory.create(owner, "Test", now)
            val communityId = community.communityId
            val priorRoot = community.communityRoot
            val priorControl = community.controlPlane

            val build =
                ConcordRefounding.build(
                    rotatorSigner = owner,
                    communityId = communityId,
                    priorRoot = priorRoot,
                    newRoot = newRoot,
                    newControlRoot = newControlRoot,
                    rootEpoch = community.rootEpoch,
                    priorControlWraps = community.genesisWraps,
                    priorControlKeys = priorControl,
                    recipientsXOnly = listOf(alice.pubKey, bob.pubKey),
                    staffXOnly = setOf(owner.pubKey),
                    createdAt = now,
                    ownerPubKey = owner.pubKey,
                )

            assertEquals(community.rootEpoch + 1, build.newEpoch)
            assertContentEquals(newRoot, build.newRoot)

            val baseRekeyKey = ConcordKeyDerivation.baseRekeyAddress(priorRoot, communityId, build.newEpoch)

            // Alice and Bob find the new root; Carol (no blob) does not.
            val aliceRoot = ConcordRefounding.findNewRoot(build.rekeyWraps, baseRekeyKey, alice, communityId, priorRoot, community.rootEpoch)
            val bobRoot = ConcordRefounding.findNewRoot(build.rekeyWraps, baseRekeyKey, bob, communityId, priorRoot, community.rootEpoch)
            val carolRoot = ConcordRefounding.findNewRoot(build.rekeyWraps, baseRekeyKey, carol, communityId, priorRoot, community.rootEpoch)

            assertNotNull(aliceRoot)
            assertContentEquals(newRoot, aliceRoot.newRoot)
            assertEquals(owner.pubKey, aliceRoot.rotator)
            assertNotNull(bobRoot)
            assertContentEquals(newRoot, bobRoot.newRoot)
            assertNull(carolRoot) // removed member receives no blob
        }

    @Test
    fun compactedControlPlaneFoldsIdenticallyUnderNewRoot() =
        runTest {
            val community = ConcordCommunityFactory.create(owner, "Test", now, description = "A place")
            val communityId = community.communityId

            val build =
                ConcordRefounding.build(
                    rotatorSigner = owner,
                    communityId = communityId,
                    priorRoot = community.communityRoot,
                    newRoot = newRoot,
                    newControlRoot = newControlRoot,
                    rootEpoch = community.rootEpoch,
                    priorControlWraps = community.genesisWraps,
                    priorControlKeys = community.controlPlane,
                    recipientsXOnly = listOf(alice.pubKey),
                    staffXOnly = setOf(owner.pubKey),
                    createdAt = now,
                    ownerPubKey = owner.pubKey,
                )

            val newControl = build.newControlKeys

            // Re-open the compacted wraps under the NEW control key and fold: same authority + metadata.
            val editions =
                build.controlWraps.mapNotNull { wrap ->
                    ConcordStreamEnvelope.openOrNull(wrap, newControl)?.let {
                        com.vitorpamplona.quartz.concord.cord04Roles.ControlEdition
                            .fromRumor(it.rumor)
                    }
                }
            val folded = ConcordCommunityState.fold(editions, owner.pubKey)

            assertEquals("Test", folded.metadata?.name)
            assertTrue(folded.authority.isOwner(owner.pubKey))
            // #general survives compaction (its head channel edition is re-sealed).
            assertTrue(folded.channels.isNotEmpty())

            // The re-sealed editions still verify as owner-signed (signature preserved across re-encryption).
            build.controlWraps.forEach { wrap ->
                val opened = ConcordStreamEnvelope.openOrNull(wrap, newControl)
                assertNotNull(opened)
                assertEquals(owner.pubKey, opened.author)
            }
        }

    @Test
    fun freshJoinerSeesEntitiesEditedAfterGenesisThenRefounded() =
        runTest {
            // A real, long-lived community edits its metadata (adds an icon) and renames
            // #general AFTER genesis, THEN gets refounded. Those edits produce version-1
            // editions whose `ep` chains onto the genesis edition. Compaction keeps only
            // each entity's head — so the re-wrapped heads still carry a `prev` pointing at
            // the (now absent) prior-epoch edition. A fresh joiner fetching only the
            // compacted heads must still see them (CORD-04 §1 "Folding across a Refounding",
            // CORD-06 §3): the signature + current-authority check is the whole test.
            val community = ConcordCommunityFactory.create(owner, "NosFabrica", now)
            val communityId = community.communityId
            val control = community.controlPlane

            val genesisMeta = community.genesisEditions.first { it.entityKind == ControlEntityKind.METADATA }
            val genesisChannel = community.genesisEditions.first { it.entityKind == ControlEntityKind.CHANNEL }

            val icon = ImagePointer(url = "https://media/icon.enc", key = "1a".repeat(32), nonce = "2b".repeat(16), hash = "3c".repeat(32))

            // v1 metadata: add the icon, chained onto genesis.
            val metaV1Json = ConcordJson.instance.encodeToString(MetadataEntity.serializer(), MetadataEntity(name = "NosFabrica", icon = icon))
            val metaV1Rumor = ControlEditionBuilder.rumor(owner.pubKey, ControlEntityKind.METADATA, communityId, 1, genesisMeta.hash, metaV1Json, now + 1)
            val metaV1Wrap = ConcordStreamEnvelope.wrap(metaV1Rumor, control, owner, encrypted = false, createdAt = now + 1)

            // v1 channel: rename #general, chained onto genesis.
            val chanV1Json = ConcordJson.instance.encodeToString(ChannelEntity.serializer(), ChannelEntity(name = "lobby", private = false))
            val chanV1Rumor = ControlEditionBuilder.rumor(owner.pubKey, ControlEntityKind.CHANNEL, community.generalChannelId, 1, genesisChannel.hash, chanV1Json, now + 1)
            val chanV1Wrap = ConcordStreamEnvelope.wrap(chanV1Rumor, control, owner, encrypted = false, createdAt = now + 1)

            val priorWraps = community.genesisWraps + metaV1Wrap + chanV1Wrap

            val build =
                ConcordRefounding.build(
                    rotatorSigner = owner,
                    communityId = communityId,
                    priorRoot = community.communityRoot,
                    newRoot = newRoot,
                    newControlRoot = newControlRoot,
                    rootEpoch = community.rootEpoch,
                    priorControlWraps = priorWraps,
                    priorControlKeys = control,
                    recipientsXOnly = listOf(alice.pubKey),
                    staffXOnly = setOf(owner.pubKey),
                    createdAt = now,
                    ownerPubKey = owner.pubKey,
                )

            val newControl = build.newControlKeys
            val editions =
                build.controlWraps.mapNotNull { wrap ->
                    ConcordStreamEnvelope.openOrNull(wrap, newControl)?.let { ControlEdition.fromRumor(it.rumor) }
                }
            val folded = ConcordCommunityState.fold(editions, owner.pubKey)

            // A fresh joiner MUST see the compacted heads — name, icon, and the renamed channel.
            assertEquals("NosFabrica", folded.metadata?.name, "fresh joiner lost the community name after refounding")
            assertEquals(icon, folded.metadata?.icon, "fresh joiner lost the community icon after refounding")
            assertEquals(
                "lobby",
                folded.channels.values
                    .firstOrNull()
                    ?.definition
                    ?.name,
                "fresh joiner lost the (edited) channel after refounding",
            )
        }

    @Test
    fun wrongPriorRootFailsContinuity() =
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
                    recipientsXOnly = listOf(alice.pubKey),
                    staffXOnly = setOf(owner.pubKey),
                    createdAt = now,
                    ownerPubKey = owner.pubKey,
                )
            val baseRekeyKey = ConcordKeyDerivation.baseRekeyAddress(community.communityRoot, community.communityId, build.newEpoch)

            // Alice claims a different prior root: prevcommit mismatch ⇒ rotation rejected.
            val wrongRoot = ByteArray(32) { 0x11 }
            assertNull(ConcordRefounding.findNewRoot(build.rekeyWraps, baseRekeyKey, alice, community.communityId, wrongRoot, community.rootEpoch))
        }

    @Test
    fun compactionRefusesAForgedGenesisAndCarriesTheAuthorizedHead() =
        runTest {
            // A compaction re-wraps ONE edition per entity and nothing downstream re-checks the
            // choice, so how that edition is picked is a security decision, not a detail.
            //
            // Raw highest-version lets a stray at an arbitrary version through. But the bare
            // structural chain walk is worse: with no floor it anchors at the lowest-version edition
            // carrying no `prev`, and after a PRIOR compaction the real head's `prev` dangles into a
            // trimmed epoch by design — so a forged `version = 1, prev = null` decoy outranks a
            // genuine v50→v52 chain, and becomes the entity's entire carried-forward state. A forged
            // empty banlist would erase every ban that way. Only the owner-rooted gate is safe.
            val community = ConcordCommunityFactory.create(owner, "Test", now)
            val communityId = community.communityId
            val control = community.controlPlane

            // The metadata entity, already compacted once: its head chains from an epoch we no longer hold.
            val danglingPrev = ByteArray(32) { 0x7F }
            val realHead =
                ConcordStreamEnvelope.wrap(
                    ControlEditionBuilder.rumor(
                        owner.pubKey,
                        ControlEntityKind.METADATA,
                        communityId,
                        50,
                        danglingPrev,
                        ConcordJson.instance.encodeToString(MetadataEntity.serializer(), MetadataEntity(name = "Real")),
                        now,
                        null,
                    ),
                    control,
                    owner,
                    encrypted = false,
                    createdAt = now,
                )

            // carol holds nothing at all and mints a genesis-shaped decoy at version 1.
            val forged =
                ConcordStreamEnvelope.wrap(
                    ControlEditionBuilder.rumor(
                        carol.pubKey,
                        ControlEntityKind.METADATA,
                        communityId,
                        1,
                        null,
                        ConcordJson.instance.encodeToString(MetadataEntity.serializer(), MetadataEntity(name = "PWNED")),
                        now,
                        null,
                    ),
                    control,
                    carol,
                    encrypted = false,
                    createdAt = now,
                )

            val newEpoch = community.rootEpoch + 1
            val newControl =
                com.vitorpamplona.quartz.concord.crypto.ControlPlaneKeys
                    .forStaff(newRoot, communityId, newEpoch, newControlRoot)
            val compacted = ConcordRefounding.compactControlPlane(listOf(realHead, forged), control, newControl, owner.pubKey)

            val carried =
                compacted
                    .mapNotNull { ConcordStreamEnvelope.openOrNull(it, newControl) }
                    .mapNotNull { ControlEdition.fromRumor(it.rumor) }
                    .filter { it.entityKind == ControlEntityKind.METADATA }

            assertEquals(1, carried.size, "one metadata edition carried forward")
            assertEquals(50, carried.single().version, "the owner's real head, not the forged genesis")
            assertEquals("Real", ConcordJson.decodeOrNull<MetadataEntity>(carried.single().content)?.name)
        }
}
