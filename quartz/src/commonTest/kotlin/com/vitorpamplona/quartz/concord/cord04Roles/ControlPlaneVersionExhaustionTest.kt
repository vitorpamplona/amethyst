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

import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityState
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **B1 in `docs/concord-soft-ban-audit.md` — regression guard.** A single Control Plane edition at
 * `version = Long.MAX_VALUE` used to pin its entity to the author's content permanently, for every
 * client that held a floor for it. These tests failed before the fix and pass after it.
 *
 * The chain walk was never the weakness — it advances only to `head.version + 1` citing the head's
 * hash, so an inflated version is unreachable and a fresh joiner was unaffected. The weakness was the
 * **compaction arm** of `EditionFold.foldEntity`: once a client held a floor for an entity and that
 * entity appeared in the epoch snapshot (which `ConcordCommunityState.fold` always builds from the
 * editions handed to it), the head came from the raw-version bootstrap — *highest version at or above
 * the floor*, with no `prev`, no hash, and no contiguity. Version was then the whole contest, and
 * `Long.MAX_VALUE` won it forever:
 *
 *  1. the poison became the head, so the entity showed the attacker's content;
 *  2. `authorizedHeads` raised the entity's floor to `Long.MAX_VALUE`;
 *  3. no honest edition could ever exceed that floor, so the entity could never be repaired;
 *  4. a Refounding that dropped the poison did not help either — nothing was then offered at or above
 *     the floor, so the fold reported a gap and fell back to `EntityFloor.known`, which *was* the poison.
 *
 * Two changes close it, and both are pinned below. The arm now tries the floor-anchored **chain**
 * first and only falls back to the raw-version bootstrap when nothing connects, so a stray never wins
 * a fold where the honest chain is present; and the bootstrap will not follow a jump larger than
 * [EditionFold.MAX_COMPACTION_VERSION_JUMP], so the version space cannot be exhausted in one step.
 * [aGenuineCompactionJumpIsStillFollowed] pins the tolerance the arm exists for, so the bound cannot
 * be tightened into breaking CORD-06 §3.
 *
 * Note who the attacker is. Every test here is authored by **bob, a current and legitimately granted
 * moderator** — not a banned member, not a sockpuppet. Any holder of the entity's permission bit can
 * could do this at any time, and demoting or banning them afterwards changed nothing, because the
 * damage was already in every client's floor. `ConcordRefounding.compactControlPlane` also selected
 * the head per entity by raw highest version, which made an honest rotator the delivery mechanism —
 * it now picks the chain head instead.
 *
 * The banlist was the one entity that survived, and by accident: `AuthorityResolver` folds it with
 * its own floor-less chain walk and then re-heals the union across authorized editions, so an
 * honest ban landed even when the head was poisoned. [aPoisonedBanlistStillAcceptsTheOwnersBan]
 * keeps pinning that, because it was the only thing standing between this bug and a permanently
 * unmoderatable community.
 */
class ControlPlaneVersionExhaustionTest {
    private val owner = "0f".repeat(32)
    private val bob = "b2".repeat(32)

    private val modRole = "22".repeat(32)
    private val metadataEntity = "66".repeat(32)
    private val channelEntity = "55".repeat(32)
    private val banlistEntity = "44".repeat(32)

    private fun edition(
        kind: ControlEntityKind,
        entity: String,
        version: Long,
        prev: ByteArray?,
        content: String,
        author: String,
        rumorId: String,
    ) = ControlEdition(kind, entity.hexToByteArray(), version, prev, null, content, author, rumorId, 0)

    /** bob holds exactly one bit, granted by the owner, entirely legitimately. */
    private fun communityWhereBobHolds(
        permissions: String,
        vararg rest: ControlEdition,
    ) = listOf(
        edition(ControlEntityKind.ROLE, modRole, 0, null, """{"name":"Mod","position":5,"permissions":"$permissions"}""", owner, "role-mod"),
        edition(ControlEntityKind.GRANT, "32".repeat(32), 0, null, """{"member":"$bob","role_ids":["$modRole"]}""", owner, "grant-bob"),
    ) + rest

    @Test
    fun oneEditionAtMaxVersionNoLongerPinsTheMetadata() {
        val metadataV0 = edition(ControlEntityKind.METADATA, metadataEntity, 0, null, """{"name":"My Community"}""", owner, "meta-0")
        val community = communityWhereBobHolds(ConcordPermissions.of(ConcordPermissions.MANAGE_METADATA).toWire(), metadataV0)

        // A client that has folded this community once holds a floor for the metadata entity.
        val floorsBefore = ConcordCommunityState.authorizedHeads(community, owner)
        assertEquals(0, floorsBefore[metadataEntity]?.version, "an ordinary floor at the genesis edition")

        val poison = edition(ControlEntityKind.METADATA, metadataEntity, Long.MAX_VALUE, metadataV0.hash, """{"name":"PWNED"}""", bob, "meta-poison")
        val floorsAfter = ConcordCommunityState.authorizedHeads(community + poison, owner, floorsBefore)
        assertEquals(0, floorsAfter[metadataEntity]?.version, "the floor must not follow a stray to the top of the version space")

        // The owner tries to repair it, chaining honestly onto their own genesis.
        val repair = edition(ControlEntityKind.METADATA, metadataEntity, 1, metadataV0.hash, """{"name":"My Community"}""", owner, "meta-1")
        val pool = community + poison + repair

        assertEquals(
            "My Community",
            ConcordCommunityState.fold(pool, owner).metadata?.name,
            "a fresh joiner walks the chain and is unaffected",
        )
        assertEquals(
            "My Community",
            ConcordCommunityState.fold(pool, owner, floorsAfter).metadata?.name,
            "a client holding a floor follows the honest chain, not the stray",
        )
        assertEquals(
            "My Community",
            ConcordCommunityState.fold(community + repair, owner, floorsAfter).metadata?.name,
            "and a Refounding that drops the poison stays repaired",
        )
    }

    @Test
    fun oneEditionAtMaxVersionNoLongerDeletesAChannel() {
        val channelV0 = edition(ControlEntityKind.CHANNEL, channelEntity, 0, null, """{"name":"general"}""", owner, "chan-0")
        val community = communityWhereBobHolds(ConcordPermissions.of(ConcordPermissions.MANAGE_CHANNELS).toWire(), channelV0)

        val floorsBefore = ConcordCommunityState.authorizedHeads(community, owner)
        val poison = edition(ControlEntityKind.CHANNEL, channelEntity, Long.MAX_VALUE, channelV0.hash, """{"name":"general","deleted":true}""", bob, "chan-poison")
        val floorsAfter = ConcordCommunityState.authorizedHeads(community + poison, owner, floorsBefore)

        val repair = edition(ControlEntityKind.CHANNEL, channelEntity, 1, channelV0.hash, """{"name":"general"}""", owner, "chan-1")
        val pool = community + poison + repair

        assertEquals(1, ConcordCommunityState.fold(pool, owner).channels.size, "a fresh joiner still sees the channel")
        assertEquals(1, ConcordCommunityState.fold(pool, owner, floorsAfter).channels.size, "and so does a client holding a floor")
        assertEquals(
            1,
            ConcordCommunityState.fold(community + repair, owner, floorsAfter).channels.size,
            "the channel survives a Refounding too",
        )
    }

    @Test
    fun aPoisonedBanlistStillAcceptsTheOwnersBan() {
        // This was the saving grace before the fix — the reason the bug was "community with a broken
        // name" rather than "community nobody can moderate". AuthorityResolver folds the banlist on
        // its own floor-less chain walk and re-heals the union across every authorized edition, so
        // the owner's ban landed even while the banlist's floor sat at Long.MAX_VALUE. The floor can
        // no longer be poisoned, but keep this: do not "unify" the banlist onto the floored fold
        // without replacing the protection.
        val banlistV0 = edition(ControlEntityKind.BANLIST, banlistEntity, 0, null, "[]", owner, "ban-0")
        val community = communityWhereBobHolds(ConcordPermissions.of(ConcordPermissions.BAN).toWire(), banlistV0)

        val floorsBefore = ConcordCommunityState.authorizedHeads(community, owner)
        val poison = edition(ControlEntityKind.BANLIST, banlistEntity, Long.MAX_VALUE, banlistV0.hash, "[]", bob, "ban-poison")
        val floorsAfter = ConcordCommunityState.authorizedHeads(community + poison, owner, floorsBefore)
        assertEquals(0, floorsAfter[banlistEntity]?.version, "the banlist floor is no longer poisonable either")

        val ownerBansBob = edition(ControlEntityKind.BANLIST, banlistEntity, 1, banlistV0.hash, """["$bob"]""", owner, "ban-1")
        val pool = community + poison + ownerBansBob

        assertTrue(ConcordCommunityState.fold(pool, owner).authority.isBanned(bob), "a fresh joiner honors the ban")
        assertTrue(
            ConcordCommunityState.fold(pool, owner, floorsAfter).authority.isBanned(bob),
            "the re-heal union must keep the banlist working even with a poisoned floor",
        )
    }

    @Test
    fun aGenuineCompactionJumpIsStillFollowed() {
        // The tolerance the compaction arm exists for, pinned so the bound above cannot be tightened
        // into breaking CORD-06 §3. After a Refounding the compacted head carries the `prev` it had
        // before compaction, citing an edition in the PRIOR epoch that this client no longer holds —
        // so it connects to nothing, and its version is legitimately several ahead of our floor.
        val metadataV0 = edition(ControlEntityKind.METADATA, metadataEntity, 0, null, """{"name":"My Community"}""", owner, "meta-0")
        val community = communityWhereBobHolds(ConcordPermissions.of(ConcordPermissions.MANAGE_METADATA).toWire(), metadataV0)
        val floors = ConcordCommunityState.authorizedHeads(community, owner)

        val danglingPrev = ByteArray(32) { 0x7f }
        val compacted = edition(ControlEntityKind.METADATA, metadataEntity, 4, danglingPrev, """{"name":"Renamed While We Were Away"}""", owner, "meta-compacted")

        assertEquals(
            "Renamed While We Were Away",
            ConcordCommunityState.fold(community.filter { it.entityKind != ControlEntityKind.METADATA } + compacted, owner, floors).metadata?.name,
            "a compacted head whose prev dangles by design must still be adopted",
        )
    }

    @Test
    fun aJumpBeyondTheCapIsRefusedAsAGap() {
        // Same shape as the genuine compaction above, one version past the bound: not a compaction we
        // missed, so the fold reports a gap and keeps what it already had rather than following it.
        val metadataV0 = edition(ControlEntityKind.METADATA, metadataEntity, 0, null, """{"name":"My Community"}""", owner, "meta-0")
        val community = communityWhereBobHolds(ConcordPermissions.of(ConcordPermissions.MANAGE_METADATA).toWire(), metadataV0)
        val floors = ConcordCommunityState.authorizedHeads(community, owner)

        val danglingPrev = ByteArray(32) { 0x7f }
        val tooFar =
            edition(
                ControlEntityKind.METADATA,
                metadataEntity,
                EditionFold.MAX_COMPACTION_VERSION_JUMP + 1,
                danglingPrev,
                """{"name":"PWNED"}""",
                bob,
                "meta-far",
            )

        assertEquals(
            "My Community",
            ConcordCommunityState.fold(community.filter { it.entityKind != ControlEntityKind.METADATA } + tooFar, owner, floors).metadata?.name,
            "a jump past the bound is a gap, not a head",
        )
    }
}
