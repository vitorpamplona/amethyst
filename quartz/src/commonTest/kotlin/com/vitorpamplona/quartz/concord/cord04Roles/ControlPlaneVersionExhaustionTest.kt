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
 * **V1 in `docs/concord-soft-ban-audit.md` — reproduction.** A single Control Plane edition at
 * `version = Long.MAX_VALUE` pins its entity to the author's content permanently, for every client
 * that holds a floor for it.
 *
 * The chain walk is not the weakness — it advances only to `head.version + 1` citing the head's
 * hash, so an inflated version is unreachable and a fresh joiner is unaffected. The weakness is the
 * **compaction arm** of [EditionFold.foldEntity]: once a client holds a floor for an entity and that
 * entity appears in the epoch snapshot (which `ConcordCommunityState.fold` always builds from the
 * editions handed to it), the head is chosen by [EditionFold.bootstrapHead] — *highest version at or
 * above the floor*, with no `prev`, no hash, and no contiguity. Version is then the whole contest,
 * and `Long.MAX_VALUE` wins it forever:
 *
 *  1. the poison becomes the head, so the entity shows the attacker's content;
 *  2. `authorizedHeads` raises the entity's floor to `Long.MAX_VALUE`;
 *  3. no honest edition can ever exceed that floor, so the entity can never be repaired;
 *  4. a Refounding that drops the poison does not help either — nothing is offered at or above the
 *     floor, so the fold reports a gap and falls back to [EntityFloor.known], which *is* the poison.
 *
 * Note who the attacker is. Every test here is authored by **bob, a current and legitimately granted
 * moderator** — not a banned member, not a sockpuppet. Any holder of the entity's permission bit can
 * do this at any time, and demoting or banning them afterwards changes nothing, because the damage
 * is already in every client's floor. It is also carried into every future epoch by
 * `ConcordRefounding.compactControlPlane`, which selects the head per entity by raw highest version.
 *
 * The banlist is the one entity that survives, and by accident: `AuthorityResolver` folds it with
 * its own floor-less chain walk and then re-heals the union across authorized editions, so an
 * honest ban lands even when the head is poisoned. [aPoisonedBanlistStillAcceptsTheOwnersBan] pins
 * that, because it is the only thing standing between this bug and a permanently unmoderatable
 * community.
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
    fun oneEditionAtMaxVersionPinsTheMetadataForever() {
        val metadataV0 = edition(ControlEntityKind.METADATA, metadataEntity, 0, null, """{"name":"My Community"}""", owner, "meta-0")
        val community = communityWhereBobHolds(ConcordPermissions.of(ConcordPermissions.MANAGE_METADATA).toWire(), metadataV0)

        // A client that has folded this community once holds a floor for the metadata entity.
        val floorsBefore = ConcordCommunityState.authorizedHeads(community, owner)
        assertEquals(0, floorsBefore[metadataEntity]?.version, "an ordinary floor at the genesis edition")

        val poison = edition(ControlEntityKind.METADATA, metadataEntity, Long.MAX_VALUE, metadataV0.hash, """{"name":"PWNED"}""", bob, "meta-poison")
        val floorsAfter = ConcordCommunityState.authorizedHeads(community + poison, owner, floorsBefore)
        assertEquals(Long.MAX_VALUE, floorsAfter[metadataEntity]?.version, "VULNERABLE: the floor is now at the top of the version space")

        // The owner tries to repair it, chaining honestly onto their own genesis.
        val repair = edition(ControlEntityKind.METADATA, metadataEntity, 1, metadataV0.hash, """{"name":"My Community"}""", owner, "meta-1")
        val pool = community + poison + repair

        assertEquals(
            "My Community",
            ConcordCommunityState.fold(pool, owner).metadata?.name,
            "a fresh joiner walks the chain and is unaffected",
        )
        assertEquals(
            "PWNED",
            ConcordCommunityState.fold(pool, owner, floorsAfter).metadata?.name,
            "VULNERABLE: every client holding a floor is pinned to the attacker's content",
        )
        assertEquals(
            "PWNED",
            ConcordCommunityState.fold(community + repair, owner, floorsAfter).metadata?.name,
            "VULNERABLE: even a Refounding that drops the poison falls back to it as EntityFloor.known",
        )
    }

    @Test
    fun oneEditionAtMaxVersionDeletesAChannelForever() {
        val channelV0 = edition(ControlEntityKind.CHANNEL, channelEntity, 0, null, """{"name":"general"}""", owner, "chan-0")
        val community = communityWhereBobHolds(ConcordPermissions.of(ConcordPermissions.MANAGE_CHANNELS).toWire(), channelV0)

        val floorsBefore = ConcordCommunityState.authorizedHeads(community, owner)
        val poison = edition(ControlEntityKind.CHANNEL, channelEntity, Long.MAX_VALUE, channelV0.hash, """{"name":"general","deleted":true}""", bob, "chan-poison")
        val floorsAfter = ConcordCommunityState.authorizedHeads(community + poison, owner, floorsBefore)

        val repair = edition(ControlEntityKind.CHANNEL, channelEntity, 1, channelV0.hash, """{"name":"general"}""", owner, "chan-1")
        val pool = community + poison + repair

        assertEquals(1, ConcordCommunityState.fold(pool, owner).channels.size, "a fresh joiner still sees the channel")
        assertEquals(0, ConcordCommunityState.fold(pool, owner, floorsAfter).channels.size, "VULNERABLE: the channel is gone and cannot be restored")
        assertEquals(
            0,
            ConcordCommunityState.fold(community + repair, owner, floorsAfter).channels.size,
            "VULNERABLE: dropping the poison does not bring the channel back",
        )
    }

    @Test
    fun aPoisonedBanlistStillAcceptsTheOwnersBan() {
        // The saving grace, and the reason this bug is "unmoderatable community" rather than
        // "community with a broken name". AuthorityResolver folds the banlist on its own floor-less
        // chain walk and re-heals the union across every authorized edition, so the owner's ban lands
        // even while the banlist's own floor sits at Long.MAX_VALUE. Do not "unify" the banlist onto
        // the floored fold without replacing this protection.
        val banlistV0 = edition(ControlEntityKind.BANLIST, banlistEntity, 0, null, "[]", owner, "ban-0")
        val community = communityWhereBobHolds(ConcordPermissions.of(ConcordPermissions.BAN).toWire(), banlistV0)

        val floorsBefore = ConcordCommunityState.authorizedHeads(community, owner)
        val poison = edition(ControlEntityKind.BANLIST, banlistEntity, Long.MAX_VALUE, banlistV0.hash, "[]", bob, "ban-poison")
        val floorsAfter = ConcordCommunityState.authorizedHeads(community + poison, owner, floorsBefore)
        assertEquals(Long.MAX_VALUE, floorsAfter[banlistEntity]?.version, "the banlist floor is poisoned like any other")

        val ownerBansBob = edition(ControlEntityKind.BANLIST, banlistEntity, 1, banlistV0.hash, """["$bob"]""", owner, "ban-1")
        val pool = community + poison + ownerBansBob

        assertTrue(ConcordCommunityState.fold(pool, owner).authority.isBanned(bob), "a fresh joiner honors the ban")
        assertTrue(
            ConcordCommunityState.fold(pool, owner, floorsAfter).authority.isBanned(bob),
            "the re-heal union must keep the banlist working even with a poisoned floor",
        )
    }
}
