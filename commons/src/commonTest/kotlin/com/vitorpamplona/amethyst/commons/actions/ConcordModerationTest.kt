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
package com.vitorpamplona.amethyst.commons.actions

import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityFactory
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityState
import com.vitorpamplona.quartz.concord.cord04Roles.ConcordPermissions
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEdition
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEntityKind
import com.vitorpamplona.quartz.concord.cord04Roles.EditionFold
import com.vitorpamplona.quartz.concord.cord04Roles.RoleEntity
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConcordModerationTest {
    private val owner = NostrSignerInternal(KeyPair())
    private val admin = NostrSignerInternal(KeyPair())
    private val troll = NostrSignerInternal(KeyPair())
    private val stranger = NostrSignerInternal(KeyPair())

    @Test
    fun ownerDefinesRoleGrantsItAndBans() =
        runTest {
            val community = ConcordCommunityFactory.create(owner, "Nostrichs", createdAt = 1L, relays = listOf("wss://r.example"))
            val cp = community.controlPlane
            val communityId = community.communityId

            // Accumulate the community's editions as we publish more.
            val editions = ConcordActions.controlEditions(community.genesisWraps, cp).toMutableList()

            fun add(wrap: com.vitorpamplona.quartz.nip01Core.core.Event) {
                editions += ConcordActions.controlEditions(listOf(wrap), cp)
            }

            // Owner defines an "Admin" role (position 1) that can BAN and KICK.
            val roleId = ByteArray(32) { (it + 1).toByte() }
            val roleIdHex = roleId.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
            val adminRole =
                RoleEntity(
                    name = "Admin",
                    position = 1,
                    permissions = ConcordPermissions.of(ConcordPermissions.BAN, ConcordPermissions.KICK).toWire(),
                )
            add(ConcordModeration.defineRole(owner, cp, roleId, adminRole, editions, createdAt = 2L, owner = community.ownerPubKey))

            // Owner grants that role to the admin user.
            add(ConcordModeration.grant(owner, cp, communityId, admin.pubKey, listOf(roleIdHex), editions, createdAt = 3L, owner = community.ownerPubKey))

            // Owner bans the troll.
            add(ConcordModeration.ban(owner, cp, communityId, troll.pubKey, editions, createdAt = 4L, owner = community.ownerPubKey))

            val state: ConcordCommunityState = ConcordCommunityState.fold(editions, community.ownerPubKey)

            // The role exists, the admin holds BAN + the role id, and the troll is banned.
            assertTrue(state.roles.containsKey(roleIdHex))
            assertTrue(state.authority.effectivePermissions(admin.pubKey).has(ConcordPermissions.BAN))
            assertTrue(roleIdHex in state.authority.rolesOf(admin.pubKey))
            assertTrue(state.authority.isBanned(troll.pubKey))
            assertFalse(state.authority.isBanned(admin.pubKey))

            // Revoking (an empty grant, as "Remove admin" does) strips the role and its permissions.
            add(ConcordModeration.grant(owner, cp, communityId, admin.pubKey, emptyList(), editions, createdAt = 7L, owner = community.ownerPubKey))
            val demoted = ConcordCommunityState.fold(editions, community.ownerPubKey)
            assertFalse(demoted.authority.effectivePermissions(admin.pubKey).has(ConcordPermissions.BAN))
            assertTrue(demoted.authority.rolesOf(admin.pubKey).isEmpty())

            // Unbanning the troll clears the flag (version chains onto the ban).
            add(ConcordModeration.unban(owner, cp, communityId, troll.pubKey, editions, createdAt = 5L, owner = community.ownerPubKey))
            val healed = ConcordCommunityState.fold(editions, community.ownerPubKey)
            assertFalse(healed.authority.isBanned(troll.pubKey))

            // A grant forged by the troll (who outranks nobody) is dropped by the fold.
            val forged = ConcordModeration.grant(troll, cp, communityId, troll.pubKey, listOf(roleIdHex), editions, createdAt = 6L, owner = community.ownerPubKey)
            val forgedEditions: List<ControlEdition> = editions + ConcordActions.controlEditions(listOf(forged), cp)
            val afterForgery = ConcordCommunityState.fold(forgedEditions, community.ownerPubKey)
            assertFalse(afterForgery.authority.effectivePermissions(troll.pubKey).has(ConcordPermissions.BAN))
        }

    /**
     * Regression: [ConcordModeration] used to locate an entity's head with
     * `current.firstOrNull { … }`. `current` is the raw edition list in **wrap-arrival**
     * order, not chain order, so once an entity had ≥2 editions the "head" was whichever
     * one a relay delivered first — a stale one. The next edition then chained off it,
     * forking the chain at an already-used version, and [EditionFold] resolved the fork by
     * `minByOrNull { rumorId }` — a coin flip that could silently drop the change. Bans are
     * masked by the down-only healing union, but an unban is not, so an unban simply
     * failed to apply.
     *
     * Here the banlist has two editions (v0 bans [troll], v1 also bans [stranger]) before
     * the unban. The unban must chain off v1 — the folded head — at v2, not off v0.
     */
    @Test
    fun thirdBanlistEditionChainsOffTheFoldedHeadNotTheFirstArrival() =
        runTest {
            val community = ConcordCommunityFactory.create(owner, "Nostrichs", createdAt = 1L, relays = listOf("wss://r.example"))
            val cp = community.controlPlane
            val communityId = community.communityId

            val editions = ConcordActions.controlEditions(community.genesisWraps, cp).toMutableList()

            // v0: ban the troll. v1: ban the stranger too (chains onto v0).
            editions += ConcordActions.controlEditions(listOf(ConcordModeration.ban(owner, cp, communityId, troll.pubKey, editions, createdAt = 2L, owner = community.ownerPubKey)), cp)
            editions += ConcordActions.controlEditions(listOf(ConcordModeration.ban(owner, cp, communityId, stranger.pubKey, editions, createdAt = 3L, owner = community.ownerPubKey)), cp)

            val banlistSoFar = editions.filter { it.entityKind == ControlEntityKind.BANLIST }
            assertEquals(2, banlistSoFar.size)
            val head = EditionFold.foldEntity(banlistSoFar)!!
            assertEquals(1L, head.version)
            // The stale v0 sorts first in arrival order — exactly what the old firstOrNull picked up.
            assertEquals(0L, banlistSoFar.first().version)

            // Now unban the troll. The head must be found regardless of arrival order — in
            // particular in the natural order, where the stale v0 comes first and is exactly
            // what the old firstOrNull latched onto.
            for (arrival in listOf(editions.toList(), editions.reversed())) {
                val unbanWrap = ConcordModeration.unban(owner, cp, communityId, troll.pubKey, arrival, createdAt = 4L, owner = community.ownerPubKey)
                val unban = ConcordActions.controlEditions(listOf(unbanWrap), cp).single()

                // Chains onto the folded head (v1), not the first-arrival v0.
                assertEquals(2L, unban.version)
                assertEquals(head.hashHex, unban.prevHash!!.toHexKey())

                // And the resulting state is the one the moderator asked for: troll freed, stranger still banned.
                val state = ConcordCommunityState.fold(editions + unban, community.ownerPubKey)
                assertFalse(state.authority.isBanned(troll.pubKey))
                assertTrue(state.authority.isBanned(stranger.pubKey))
            }
        }

    /** The same stale-head trap on a versioned entity: a third role edition must be v2. */
    @Test
    fun thirdRoleEditionChainsOffTheFoldedHead() =
        runTest {
            val community = ConcordCommunityFactory.create(owner, "Nostrichs", createdAt = 1L, relays = listOf("wss://r.example"))
            val cp = community.controlPlane
            val editions = ConcordActions.controlEditions(community.genesisWraps, cp).toMutableList()

            val roleId = ByteArray(32) { (it + 1).toByte() }

            fun role(name: String) = RoleEntity(name = name, position = 1, permissions = ConcordPermissions.of(ConcordPermissions.KICK).toWire())

            editions += ConcordActions.controlEditions(listOf(ConcordModeration.defineRole(owner, cp, roleId, role("Mod"), editions, createdAt = 2L, owner = community.ownerPubKey)), cp)
            editions += ConcordActions.controlEditions(listOf(ConcordModeration.defineRole(owner, cp, roleId, role("Admin"), editions, createdAt = 3L, owner = community.ownerPubKey)), cp)

            for (arrival in listOf(editions.toList(), editions.reversed())) {
                val third = ConcordActions.controlEditions(listOf(ConcordModeration.defineRole(owner, cp, roleId, role("Owner"), arrival, createdAt = 4L, owner = community.ownerPubKey)), cp).single()
                assertEquals(2L, third.version)

                val state = ConcordCommunityState.fold(editions + third, community.ownerPubKey)
                assertEquals("Owner", state.roles[roleId.toHexKey()]?.name)
            }
        }

    /**
     * The writer must chain onto the head a READER would honor, not the raw structural tip.
     * Given the community's owner, `headOf` folds the authority-gated head, so a rogue
     * edition sitting at the tip of the banlist chain does not drag the honest moderator's
     * next edition up behind it (version inflation an attacker controls). Armada's writers
     * chain off `folded.heads` — `pickHead`'s gated pick — for the same reason.
     */
    @Test
    fun theWriterChainsOntoTheAuthorityGatedHeadNotTheRogueTip() =
        runTest {
            val community = ConcordCommunityFactory.create(owner, "Nostrichs", createdAt = 1L, relays = listOf("wss://r.example"))
            val cp = community.controlPlane
            val communityId = community.communityId
            val editions = ConcordActions.controlEditions(community.genesisWraps, cp).toMutableList()

            // v0: the owner bans the troll.
            editions += ConcordActions.controlEditions(listOf(ConcordModeration.ban(owner, cp, communityId, troll.pubKey, editions, createdAt = 2L, owner = community.ownerPubKey)), cp)
            // v1: the stranger — who holds nothing — forges an empty banlist at the tip.
            val rogue = ConcordActions.controlEditions(listOf(ConcordModeration.unban(stranger, cp, communityId, troll.pubKey, editions, createdAt = 3L, owner = community.ownerPubKey)), cp).single()
            assertEquals(1L, rogue.version)
            val poisoned = editions + rogue

            // The owner now bans the stranger. It must chain onto v0 — the head the fold
            // honors — not onto the rogue v1, so the version is 1, not 2.
            val next =
                ConcordActions
                    .controlEditions(
                        listOf(ConcordModeration.ban(owner, cp, communityId, stranger.pubKey, poisoned, createdAt = 4L, owner = community.ownerPubKey)),
                        cp,
                    ).single()
            assertEquals(1L, next.version, "the rogue tip must not inflate the honest edition's version")

            // And, critically, the owner's banlist is computed from the GATED head, so it still
            // carries the troll. Reading the rogue's content instead would launder the forged
            // unban into an owner-signed edition and free the troll for good.
            val state = ConcordCommunityState.fold(poisoned + next, community.ownerPubKey)
            assertTrue(state.authority.isBanned(troll.pubKey), "the forged unban must not free the troll")
            assertTrue(state.authority.isBanned(stranger.pubKey))
        }
}
