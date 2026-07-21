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
package com.vitorpamplona.quartz.concord.cord04Roles

import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Anti-rollback: a CORD-06 Refounding re-wraps ONE edition per entity at the new epoch and the
 * *rotator* chooses which one, so it can serve v1 of a chain that had reached v2 — restoring a
 * revoked role, clearing a banlist, reverting metadata — with every signature genuine. Rollback
 * by omission, not forgery. A client that already folded to v2 must refuse to come back down.
 */
class EditionFoldFloorTest {
    private val author = KeyPair().pubKey.toHexKey()
    private val eid = ByteArray(32) { 0xAB.toByte() }
    private val otherEid = ByteArray(32) { 0xCD.toByte() }

    private fun edition(
        version: Long,
        prevHash: ByteArray?,
        content: String,
        entityId: ByteArray = eid,
        rumorId: String = "id-v$version",
    ) = ControlEdition(
        entityKind = ControlEntityKind.GRANT,
        entityId = entityId,
        version = version,
        prevHash = prevHash,
        authorityCitation = null,
        content = content,
        author = author,
        rumorId = rumorId,
        createdAt = 1_700_000_000L + version,
    )

    // A grant chain: v0 grants the role, v1 edits it, v2 revokes it. Rolling back to v1 restores
    // a role the community already revoked.
    private val v0 = edition(0, null, """{"member":"a","role_ids":["mod"]}""")
    private val v1 = edition(1, v0.hash, """{"member":"a","role_ids":["admin"]}""")
    private val v2 = edition(2, v1.hash, """{"member":"a","role_ids":[]}""")
    private val v3 = edition(3, v2.hash, """{"member":"a","role_ids":["mod"]}""")

    private val floorAtV2 = v2.asFloor()

    // ---- foldEntity ----------------------------------------------------------

    /** The attack: the rotator serves only v1, whose prev dangles (v0 was not re-wrapped). */
    @Test
    fun refusesRollbackToAnOmittedLowerVersion() {
        val gaps = mutableListOf<Triple<String, Long, Long>>()
        val head = EditionFold.foldEntity(listOf(v1), floorAtV2) { e, f, o -> gaps += Triple(e, f, o) }

        assertEquals(v2.rumorId, head?.rumorId, "the revoked-at-v2 head must survive the rollback")
        assertEquals(listOf(Triple(v2.entityIdHex, 2L, 1L)), gaps, "the refusal must be reported")
    }

    /** Even an intact prefix (v0→v1) is a downgrade when we already folded v2. */
    @Test
    fun refusesRollbackEvenWhenTheOfferedPrefixIsIntact() {
        val head = EditionFold.foldEntity(listOf(v0, v1), floorAtV2) { _, _, _ -> }
        assertEquals(v2.rumorId, head?.rumorId)
    }

    /** A same-version forgery is a fork, not our chain: the floor matches on hash, not version. */
    @Test
    fun refusesSiblingAtTheFloorVersion() {
        val forgedV2 = edition(2, v1.hash, """{"member":"a","role_ids":["owner-ish"]}""", rumorId = "id-forged")
        val head = EditionFold.foldEntity(listOf(v1, forgedV2), floorAtV2) { _, _, _ -> }
        assertEquals(v2.rumorId, head?.rumorId)
        assertEquals(v2.content, head?.content)
    }

    /** An honest compaction re-wraps the very head we hold: the walk connects, so it is adopted. */
    @Test
    fun acceptsHonestCompactionAtTheFloor() {
        val gaps = mutableListOf<String>()
        val head = EditionFold.foldEntity(listOf(v2), floorAtV2) { e, _, _ -> gaps += e }
        assertEquals(v2.rumorId, head?.rumorId)
        assertTrue(gaps.isEmpty(), "an honest compaction must not report a gap")
    }

    /** And it advances past the floor when the new epoch chains forward from it. */
    @Test
    fun advancesAboveTheFloorWhenTheChainConnects() {
        val head = EditionFold.foldEntity(listOf(v3, v2), floorAtV2) { _, _, _ -> }
        assertEquals(v3.rumorId, head?.rumorId)
    }

    /** A floor whose known edition we no longer hold still refuses to move down — it adopts nothing. */
    @Test
    fun refusesRollbackWithNoKnownEditionToFallBackOn() {
        val hashOnlyFloor = EntityFloor(v2.version, v2.hashHex, known = null)
        assertNull(EditionFold.foldEntity(listOf(v1), hashOnlyFloor) { _, _, _ -> })
    }

    /**
     * A fresh joiner holds no floor and MUST still accept a dangling compacted head as its
     * baseline (CORD-04 §1 / CORD-06 §3) — it legitimately has no history to fail closed on.
     * Regression guard for `ControlEditionTest.foldWithoutGenesisAcceptsCompactedHead`.
     */
    @Test
    fun freshJoinerWithoutAFloorStillAcceptsADanglingCompactedHead() {
        assertEquals(v1.rumorId, EditionFold.foldEntity(listOf(v1), floor = null)?.rumorId)
    }

    // ---- fold (per-entity map) -----------------------------------------------

    @Test
    fun foldAppliesTheFloorPerEntity() {
        val otherV0 = edition(0, null, """{"member":"b","role_ids":["mod"]}""", entityId = otherEid, rumorId = "other-v0")
        val heads = EditionFold.fold(listOf(v1, otherV0), mapOf(v2.entityIdHex to floorAtV2)) { _, _, _ -> }

        assertEquals(v2.rumorId, heads[v2.entityIdHex]?.rumorId, "floored entity refuses the rollback")
        assertEquals(otherV0.rumorId, heads[otherV0.entityIdHex]?.rumorId, "un-floored entity folds normally")
    }

    // ---- admissible (the pre-filter the derived folds share) ------------------

    /** A gapped entity loses every edition at or above the floor, and keeps the head we knew. */
    @Test
    fun admissibleDropsTheRolledBackChainAndReSeatsTheKnownHead() {
        val forgedV2 = edition(2, v1.hash, """{"member":"a","role_ids":["admin"]}""", rumorId = "id-forged")
        val admissible = EditionFold.admissible(listOf(v0, v1, forgedV2), mapOf(v2.entityIdHex to floorAtV2)) { _, _, _ -> }

        assertContentEquals(listOf(v0.rumorId, v1.rumorId, v2.rumorId), admissible.map { it.rumorId })
    }

    /** When the chain connects, nothing is filtered — the new epoch may legitimately advance. */
    @Test
    fun admissiblePassesEverythingThroughWhenTheChainConnects() {
        val admissible = EditionFold.admissible(listOf(v2, v3), mapOf(v2.entityIdHex to floorAtV2)) { _, _, _ -> }
        assertContentEquals(listOf(v2.rumorId, v3.rumorId), admissible.map { it.rumorId })
    }

    /** Omitting an entity outright is the cheapest rollback of all (a dropped banlist is an unban). */
    @Test
    fun admissibleKeepsAnEntityThatWasOmittedEntirely() {
        val otherV0 = edition(0, null, """{"member":"b","role_ids":["mod"]}""", entityId = otherEid, rumorId = "other-v0")
        val admissible = EditionFold.admissible(listOf(otherV0), mapOf(v2.entityIdHex to floorAtV2)) { _, _, _ -> }

        assertContentEquals(listOf(otherV0.rumorId, v2.rumorId), admissible.map { it.rumorId })
    }

    /** No floors at all → the fresh-joiner path, untouched. */
    @Test
    fun admissibleIsAPassThroughWithoutFloors() {
        val admissible = EditionFold.admissible(listOf(v1), emptyMap())
        assertContentEquals(listOf(v1.rumorId), admissible.map { it.rumorId })
    }
}
