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
import com.vitorpamplona.quartz.concord.cord02Community.HeldRoot
import com.vitorpamplona.quartz.concord.cord02Community.NewConcordCommunity
import com.vitorpamplona.quartz.concord.cord04Roles.AuthorityCitation
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEdition
import com.vitorpamplona.quartz.concord.cord04Roles.EntityFloor
import com.vitorpamplona.quartz.concord.cord04Roles.control.tags.VacTag
import com.vitorpamplona.quartz.concord.crypto.ConcordKeyDerivation
import com.vitorpamplona.quartz.concord.crypto.ControlPlaneKeys
import com.vitorpamplona.quartz.concord.crypto.GroupKey
import com.vitorpamplona.quartz.concord.envelope.ConcordStreamEnvelope
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.RumorAssembler
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * CORD-06 wire conformance against the spec and the reference client: 1-based chunks (I8),
 * byte-budgeted chunks under the NIP-44 cap (I10), the `vac` citation on rotations (I9),
 * race convergence + idempotent retry (I12), fold-all-or-abort compaction (S12), and the
 * encrypted rekey seal.
 */
class ConcordRekeyConformanceTest {
    private val owner = NostrSignerInternal(KeyPair())
    private val admin = NostrSignerInternal(KeyPair())
    private val member = NostrSignerInternal(KeyPair())
    private val now = 1_700_000_000L
    private val controlRoot = ByteArray(32) { 0x6B }

    private suspend fun rotation(
        community: NewConcordCommunity,
        rotator: NostrSigner,
        newRoot: ByteArray,
        recipients: List<String>,
        staff: Set<String> = emptySet(),
        authority: AuthorityCitation? = null,
    ): List<Event> {
        val newEpoch = community.rootEpoch + 1
        return ConcordRefounding.buildBaseRekeyWraps(
            rotatorSigner = rotator,
            baseRekeyKey = baseRekey(community),
            recipientsXOnly = recipients,
            staffXOnly = staff,
            newRoot = newRoot,
            newControlPk = ConcordKeyDerivation.controlSignerKey(controlRoot, community.communityId, newEpoch).publicKey,
            newControlRoot = controlRoot,
            newEpoch = newEpoch,
            prevEpoch = community.rootEpoch,
            prevCommit = ConcordKeyDerivation.epochKeyCommitment(community.rootEpoch, community.communityRoot).toHexKey(),
            createdAt = now,
            authority = authority,
        )
    }

    private fun baseRekey(community: NewConcordCommunity): GroupKey = ConcordKeyDerivation.baseRekeyAddress(community.communityRoot, community.communityId, community.rootEpoch + 1)

    private fun rumorsOf(
        wraps: List<Event>,
        key: GroupKey,
    ) = wraps.map { assertNotNull(ConcordStreamEnvelope.openOrNull(it, key)) }

    private fun members(n: Int) = List(n) { KeyPair().pubKey.toHexKey() }

    // ---- I8: chunk indices are 1-based --------------------------------------------------

    @Test
    fun chunksAreNumberedFromOne() =
        runTest {
            val community = ConcordCommunityFactory.create(owner, "Test", now)
            val wraps = rotation(community, owner, ByteArray(32) { 1 }, members(250) + member.pubKey)
            val opened = rumorsOf(wraps, baseRekey(community))
            val chunks = opened.map { o -> o.rumor.tags.first { it[0] == ConcordRekey.TAG_CHUNK } }
            assertEquals((1..wraps.size).map { it.toString() }, chunks.map { it[1] })
            assertTrue(chunks.all { it[2] == wraps.size.toString() })
            assertTrue(opened.all { ConcordRekey.chunkOf(it.rumor.tags) != null })
        }

    @Test
    fun chunkTagParsingIsStrict() {
        fun chunk(vararg v: String) = arrayOf(arrayOf(ConcordRekey.TAG_CHUNK, *v))
        assertEquals(1 to 1, ConcordRekey.chunkOf(emptyArray()), "absent reads as the only chunk")
        assertEquals(2 to 3, ConcordRekey.chunkOf(chunk("2", "3")))
        assertNull(ConcordRekey.chunkOf(chunk("0", "2")), "0-based is malformed")
        assertNull(ConcordRekey.chunkOf(chunk("3", "2")))
        assertNull(ConcordRekey.chunkOf(chunk("1", "0")))
        assertNull(ConcordRekey.chunkOf(chunk("01", "2")))
        assertNull(ConcordRekey.chunkOf(chunk("+1", "2")))
        assertNull(ConcordRekey.chunkOf(chunk("1")))
        assertFailsWith<IllegalArgumentException> { ConcordRekey.tags(ConcordRekey.ROOT_SCOPE, 1, 0, "ab".repeat(32), 0, 1) }
    }

    @Test
    fun aZeroBasedChunkIsDropped() =
        runTest {
            val community = ConcordCommunityFactory.create(owner, "Test", now)
            val newEpoch = community.rootEpoch + 1
            val blob = ConcordRekey.blobForSigner(owner, member.pubKey.hexToByteArray(), ConcordRekey.ROOT_SCOPE, newEpoch, ByteArray(32) { 1 })
            val prevCommit = ConcordKeyDerivation.epochKeyCommitment(community.rootEpoch, community.communityRoot).toHexKey()
            val tags =
                arrayOf(
                    arrayOf(ConcordRekey.TAG_SCOPE, ConcordRekey.ROOT_SCOPE.toHexKey()),
                    arrayOf(ConcordRekey.TAG_NEWEPOCH, newEpoch.toString()),
                    arrayOf(ConcordRekey.TAG_PREVEPOCH, community.rootEpoch.toString()),
                    arrayOf(ConcordRekey.TAG_PREVCOMMIT, prevCommit),
                    arrayOf(ConcordRekey.TAG_CHUNK, "0", "1"),
                )
            val rumor = RumorAssembler.assembleRumor<Event>(owner.pubKey, now, ConcordRekey.KIND, tags, ConcordRekey.encodeContent(listOf(blob)))
            val wrap = ConcordStreamEnvelope.wrap(rumor, baseRekey(community), owner, encrypted = true, createdAt = now)
            assertNull(ConcordRefounding.findNewRoot(listOf(wrap), baseRekey(community), member, community.communityId, community.communityRoot, community.rootEpoch))
        }

    // ---- rekey seals must be encrypted (CORD-02 §5) ------------------------------------

    @Test
    fun aPlaintextSealedRekeyIsIgnored() =
        runTest {
            val community = ConcordCommunityFactory.create(owner, "Test", now)
            val encrypted = rotation(community, owner, ByteArray(32) { 1 }, listOf(member.pubKey))
            val rumor = rumorsOf(encrypted, baseRekey(community)).single().rumor
            val plaintext = ConcordStreamEnvelope.wrap(rumor, baseRekey(community), owner, encrypted = false, createdAt = now)

            assertNotNull(ConcordRefounding.findNewRoot(encrypted, baseRekey(community), member, community.communityId, community.communityRoot, community.rootEpoch))
            assertNull(ConcordRefounding.findNewRoot(listOf(plaintext), baseRekey(community), member, community.communityId, community.communityRoot, community.rootEpoch))
        }

    // ---- I10: chunk by bytes so the wrap stays under NIP-44's 65,535-byte plaintext ---------

    @Test
    fun staffChunksStayUnderTheNip44Cap() =
        runTest {
            val community = ConcordCommunityFactory.create(owner, "Test", now)
            val recipients = members(300) + member.pubKey
            // Every recipient staff: the widest (136-byte) blob. 120 of these overflowed the cap.
            val wraps = rotation(community, owner, ByteArray(32) { 1 }, recipients, staff = recipients.toSet())
            val opened = rumorsOf(wraps, baseRekey(community))

            for (o in opened) {
                assertTrue(
                    o.rumor
                        .toJson()
                        .encodeToByteArray()
                        .size <= ConcordRekey.REKEY_RUMOR_MAX_BYTES,
                    "rumor over the byte budget",
                )
                assertTrue(
                    o.seal
                        .toJson()
                        .encodeToByteArray()
                        .size <= 65_535,
                    "the wrap's NIP-44 plaintext (the seal) must fit the cap",
                )
                assertTrue(ConcordRekey.decodeContent(o.rumor.content).size <= 90, "the reference client fits 90 staff blobs per chunk")
            }
            assertEquals(recipients.size, opened.sumOf { ConcordRekey.decodeContent(it.rumor.content).size })
            // And everyone still finds their key across the chunks.
            assertNotNull(ConcordRefounding.findNewRoot(wraps, baseRekey(community), member, community.communityId, community.communityRoot, community.rootEpoch))
        }

    @Test
    fun memberChunksStayUnderTheNip44Cap() =
        runTest {
            val community = ConcordCommunityFactory.create(owner, "Test", now)
            val wraps = rotation(community, owner, ByteArray(32) { 1 }, members(250))
            for (o in rumorsOf(wraps, baseRekey(community))) {
                assertTrue(
                    o.seal
                        .toJson()
                        .encodeToByteArray()
                        .size <= 65_535,
                )
                // ~99 per chunk (the reference client's figure; our slimmer rumor envelope fits a few more).
                assertTrue(
                    o.rumor
                        .toJson()
                        .encodeToByteArray()
                        .size <= ConcordRekey.REKEY_RUMOR_MAX_BYTES,
                )
                assertTrue(ConcordRekey.decodeContent(o.rumor.content).size < ConcordRekey.MAX_BLOBS_PER_CHUNK, "the byte budget, not the count cap, binds for 104-byte blobs")
            }
        }

    @Test
    fun chunkingKeepsTheCountCap() {
        val tiny = List(300) { RekeyBlob("a", "b") }
        val chunks = ConcordRekey.chunkBlobs(tiny, envelopeBytes = 300)
        assertEquals(listOf(120, 120, 60), chunks.map { it.size })
        assertEquals(listOf(0), ConcordRekey.chunkBlobs(emptyList(), 300).map { it.size })
    }

    // ---- I9: the vac citation --------------------------------------------------------------

    @Test
    fun everyChunkCarriesTheRotatorsCitation() =
        runTest {
            val community = ConcordCommunityFactory.create(owner, "Test", now)
            val citation = AuthorityCitation(ByteArray(32) { 3 }, 4, ByteArray(32) { 5 })
            val wraps = rotation(community, admin, ByteArray(32) { 1 }, members(250) + member.pubKey, authority = citation)
            for (o in rumorsOf(wraps, baseRekey(community))) {
                assertEquals(
                    VacTag.assemble(citation).toList(),
                    o.rumor.tags
                        .first { it[0] == VacTag.TAG_NAME }
                        .toList(),
                )
            }
            val got = ConcordRefounding.findNewRoot(wraps, baseRekey(community), member, community.communityId, community.communityRoot, community.rootEpoch)
            assertNotNull(got)
            assertEquals(admin.pubKey, got.rotator)
            assertContentEquals(citation.grantId, got.authority?.grantId)
            assertEquals(4L, got.authority?.grantVersion)

            // The owner cites nothing.
            val byOwner = rotation(community, owner, ByteArray(32) { 1 }, listOf(member.pubKey))
            assertTrue(rumorsOf(byOwner, baseRekey(community)).all { o -> o.rumor.tags.none { it[0] == VacTag.TAG_NAME } })
        }

    @Test
    fun citationsAreVerifiedAgainstTheFoldedGrantHead() {
        val cid = "11".repeat(32)
        val ownerHex = owner.pubKey
        val grantEid = ConcordKeyDerivation.grantCoordinate(cid.hexToByteArray(), admin.pubKey.hexToByteArray()).toHexKey()
        val hash = "ab".repeat(32)
        val heads = mapOf(grantEid to EntityFloor(3, hash))

        val minted = ConcordRotationAuthority.citationFor(cid, admin.pubKey, ownerHex, heads)
        assertNotNull(minted)
        assertEquals(grantEid, minted.grantId.toHexKey())
        assertEquals(3L, minted.grantVersion)
        assertNull(ConcordRotationAuthority.citationFor(cid, ownerHex, ownerHex, heads), "the owner cites nothing")

        fun ok(c: AuthorityCitation?) = ConcordRotationAuthority.citationSatisfied(cid, admin.pubKey, ownerHex, c, heads)
        assertTrue(ok(minted))
        assertTrue(ConcordRotationAuthority.citationSatisfied(cid, ownerHex, ownerHex, null, heads), "the owner needs no citation")
        assertFalse(ok(null), "a delegated rotator must cite")
        assertTrue(ok(AuthorityCitation(minted.grantId, 2, ByteArray(32))), "our head is newer than the cited Grant: rank decides")
        assertFalse(ok(AuthorityCitation(minted.grantId, 4, ByteArray(32))), "cites a Grant we have not synced: park")
        assertFalse(ok(AuthorityCitation(minted.grantId, 3, ByteArray(32) { 9 })), "same version, different hash: a fork")
        val otherEid = ConcordKeyDerivation.grantCoordinate(cid.hexToByteArray(), member.pubKey.hexToByteArray())
        assertFalse(ok(AuthorityCitation(otherEid, 3, hash.hexToByteArray())), "must cite the rotator's OWN Grant")
    }

    // ---- I12: race convergence + idempotent retry --------------------------------------------

    @Test
    fun racingRotationsConvergeOnTheLowestAuthorizedRoot() =
        runTest {
            val community = ConcordCommunityFactory.create(owner, "Test", now)
            val high = ByteArray(32) { 0x5A }
            val low = ByteArray(32) { 0x10 }
            val wraps = rotation(community, owner, high, listOf(member.pubKey)) + rotation(community, admin, low, listOf(member.pubKey))

            val all = ConcordRefounding.findNewRoots(wraps, baseRekey(community), member, community.communityId, community.communityRoot, community.rootEpoch)
            assertEquals(2, all.size)

            // Fetch order must not matter: every client picks the same winner.
            for (order in listOf(wraps, wraps.reversed())) {
                val won = ConcordRefounding.findNewRoot(order, baseRekey(community), member, community.communityId, community.communityRoot, community.rootEpoch)
                assertNotNull(won)
                assertContentEquals(low, won.newRoot)
            }

            // Authorize before converging: an unauthorized lower root never wins.
            val onlyOwner = ConcordRefounding.findNewRoot(wraps, baseRekey(community), member, community.communityId, community.communityRoot, community.rootEpoch) { it.rotator == owner.pubKey }
            assertNotNull(onlyOwner)
            assertContentEquals(high, onlyOwner.newRoot)
        }

    @Test
    fun theSameEpochHealIsDownOnly() {
        val low = ByteArray(32) { 0x10 }
        val high = ByteArray(32) { 0x5A }
        assertTrue(ConcordRefounding.healsTo(held = high, candidate = low))
        assertFalse(ConcordRefounding.healsTo(held = low, candidate = high), "a flaky fetch of the higher sibling must not re-fork")
        assertFalse(ConcordRefounding.healsTo(held = low, candidate = low))
        // Unsigned order: 0x80 sorts above 0x7F.
        assertTrue(ConcordRefounding.compareKeys(byteArrayOf(0x7F), byteArrayOf(0x80.toByte())) < 0)
    }

    @Test
    fun losingForkRootsAreKeptButNotFoldedFrom() {
        val held =
            listOf(
                HeldRoot(1, "5a".repeat(32), controlPk = null),
                HeldRoot(1, "10".repeat(32), controlPk = "cc".repeat(32)),
                HeldRoot(0, "aa".repeat(32)),
            )
        val canonical = ConcordRefounding.canonicalHeldRoots(held)
        assertEquals(setOf(0L to "aa".repeat(32), 1L to "10".repeat(32)), canonical.map { it.epoch to it.key }.toSet())
    }

    @Test
    fun aRetriedRefoundingReusesItsReservedKeys() {
        val cid = "11".repeat(32)
        val priorRoot = ByteArray(32) { 2 }
        val first = ConcordRefounding.reserveKeys(null, cid, 3, priorRoot)
        val retry = ConcordRefounding.reserveKeys(first, cid, 3, priorRoot)
        assertSame(first, retry, "a retry must re-deliver the same root, never mint a sibling")

        // A different rotation (another epoch, or another prior root) gets fresh keys.
        val nextEpoch = ConcordRefounding.reserveKeys(first, cid, 4, priorRoot)
        assertFalse(nextEpoch.newRoot.contentEquals(first.newRoot))
        val otherRoot = ConcordRefounding.reserveKeys(first, cid, 3, ByteArray(32) { 9 })
        assertFalse(otherRoot.newRoot.contentEquals(first.newRoot))
    }

    // ---- S12: fold-all-or-abort compaction ---------------------------------------------------

    @Test
    fun compactionAbortsWhenAnHonoredHeadIsMissing() =
        runTest {
            val community = ConcordCommunityFactory.create(owner, "Test", now, description = "A place")
            val newControl = ControlPlaneKeys.forStaff(ByteArray(32) { 7 }, community.communityId, community.rootEpoch + 1, controlRoot)
            val heads =
                community.genesisWraps
                    .mapNotNull { ConcordStreamEnvelope.openOrNull(it, community.controlPlane)?.let { o -> ControlEdition.fromRumor(o.rumor) } }
                    .associate { it.entityIdHex to it.version }

            // Everything we honor is present: the compaction goes ahead.
            val compacted = ConcordRefounding.compactControlPlane(community.genesisWraps, community.controlPlane, newControl, community.communityId, owner.pubKey, mustCarry = heads)
            assertEquals(heads.size, compacted.size)

            // An entity we fold (or a newer version of one) that the fetched plane lacks: abort.
            val missing =
                assertFailsWith<IncompleteControlPlaneException> {
                    ConcordRefounding.compactControlPlane(community.genesisWraps, community.controlPlane, newControl, community.communityId, owner.pubKey, mustCarry = heads + ("ff".repeat(32) to 0L))
                }
            assertEquals(listOf("ff".repeat(32)), missing.missing)
            val first = heads.keys.first()
            assertFailsWith<IncompleteControlPlaneException> {
                ConcordRefounding.compactControlPlane(community.genesisWraps, community.controlPlane, newControl, community.communityId, owner.pubKey, mustCarry = mapOf(first to heads.getValue(first) + 1))
            }
        }
}
