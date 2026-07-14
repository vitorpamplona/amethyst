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
                    rootEpoch = community.rootEpoch,
                    priorControlWraps = community.genesisWraps,
                    priorControlKey = priorControl,
                    recipientsXOnly = listOf(alice.pubKey, bob.pubKey),
                    createdAt = now,
                )

            assertEquals(community.rootEpoch + 1, build.newEpoch)
            assertContentEquals(newRoot, build.newRoot)

            val baseRekeyKey = ConcordKeyDerivation.baseRekeyAddress(priorRoot, communityId, build.newEpoch)

            // Alice and Bob find the new root; Carol (no blob) does not.
            val aliceRoot = ConcordRefounding.findNewRoot(build.rekeyWraps, baseRekeyKey, alice, priorRoot, community.rootEpoch)
            val bobRoot = ConcordRefounding.findNewRoot(build.rekeyWraps, baseRekeyKey, bob, priorRoot, community.rootEpoch)
            val carolRoot = ConcordRefounding.findNewRoot(build.rekeyWraps, baseRekeyKey, carol, priorRoot, community.rootEpoch)

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
                    rootEpoch = community.rootEpoch,
                    priorControlWraps = community.genesisWraps,
                    priorControlKey = community.controlPlane,
                    recipientsXOnly = listOf(alice.pubKey),
                    createdAt = now,
                )

            val newControl = ConcordKeyDerivation.controlPlaneKey(newRoot, communityId, build.newEpoch)

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
                    rootEpoch = community.rootEpoch,
                    priorControlWraps = priorWraps,
                    priorControlKey = control,
                    recipientsXOnly = listOf(alice.pubKey),
                    createdAt = now,
                )

            val newControl = ConcordKeyDerivation.controlPlaneKey(newRoot, communityId, build.newEpoch)
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
                    rootEpoch = community.rootEpoch,
                    priorControlWraps = community.genesisWraps,
                    priorControlKey = community.controlPlane,
                    recipientsXOnly = listOf(alice.pubKey),
                    createdAt = now,
                )
            val baseRekeyKey = ConcordKeyDerivation.baseRekeyAddress(community.communityRoot, community.communityId, build.newEpoch)

            // Alice claims a different prior root: prevcommit mismatch ⇒ rotation rejected.
            val wrongRoot = ByteArray(32) { 0x11 }
            assertNull(ConcordRefounding.findNewRoot(build.rekeyWraps, baseRekeyKey, alice, wrongRoot, community.rootEpoch))
        }
}
