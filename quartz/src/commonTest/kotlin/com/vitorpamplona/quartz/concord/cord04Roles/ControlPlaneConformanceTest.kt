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
import com.vitorpamplona.quartz.concord.cord04Roles.ConcordPermissions.Companion.BAN
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * CORD-04 §1/§2/§5 fold rules the reference client (Armada `control.ts`) enforces and this client
 * did not: Grant coordinates bound to their member (S5), authority-first equal-version ties (S7),
 * the `vac` authority citation (I4), `role_id` in Role content (I5) and the Role caps (I17).
 */
class ControlPlaneConformanceTest {
    private val owner = "0f".repeat(32)
    private val alice = "a1".repeat(32)
    private val bob = "b2".repeat(32)
    private val carol = "c3".repeat(32)

    private val adminRole = "11".repeat(32)
    private val modRole = "22".repeat(32)

    private val cid = ControlFixtures.communityId

    private fun ed(
        kind: ControlEntityKind,
        eid: String,
        version: Long,
        prev: ControlEdition?,
        content: String,
        author: String,
        rumorId: String,
        vac: AuthorityCitation? = null,
    ) = ControlEdition(kind, eid.hexToByteArray(), version, prev?.hash, vac, content, author, rumorId, version)

    private fun roleJson(
        name: String,
        position: Int,
        permissions: String,
        roleId: String? = null,
    ) = if (roleId == null) {
        """{"name":"$name","position":$position,"permissions":"$permissions"}"""
    } else {
        """{"role_id":"$roleId","name":"$name","position":$position,"permissions":"$permissions"}"""
    }

    private fun grantJson(
        member: String,
        roleIds: List<String>,
    ) = """{"member":"$member","role_ids":[${roleIds.joinToString(",") { "\"$it\"" }}]}"""

    // Admin: position 1, MANAGE_ROLES|KICK|BAN = 25. Mod: position 5, KICK|BAN = 24.
    private val adminDef = ed(ControlEntityKind.ROLE, adminRole, 1, null, roleJson("Admin", 1, "25"), owner, "role-admin")
    private val modDef = ed(ControlEntityKind.ROLE, modRole, 1, null, roleJson("Mod", 5, "24"), owner, "role-mod")
    private val aliceIsAdmin = ed(ControlEntityKind.GRANT, ControlFixtures.grantEid(alice), 1, null, grantJson(alice, listOf(adminRole)), owner, "grant-alice")

    private fun banlist(
        version: Long,
        prev: ControlEdition?,
        author: String,
        rumorId: String,
        vararg banned: String,
        vac: AuthorityCitation? = null,
    ) = ed(ControlEntityKind.BANLIST, ControlFixtures.banlistEid(), version, prev, "[${banned.joinToString(",") { "\"$it\"" }}]", author, rumorId, vac)

    private fun citation(
        grant: ControlEdition,
        version: Long = grant.version,
        hash: ByteArray = grant.hash,
    ) = AuthorityCitation(grant.entityId, version, hash)

    // ---- S5: a Grant only counts at its member's own coordinate --------------------------------

    @Test
    fun aGrantChainAtAForeignCoordinateCannotOverrideTheMembersOwn() {
        // grant_locator(community_id, member) is THE Grant coordinate (CORD-04 §1). A second chain for
        // the same member anywhere else used to be grouped as its own entity, and whichever chain the
        // map iterated last set the member's roles — so a MANAGE_ROLES holder could override the
        // owner's revoke, or the owner's grant, by arrival order.
        val foreignRevoke = ed(ControlEntityKind.GRANT, "99".repeat(32), 7, null, grantJson(alice, emptyList()), owner, "foreign")
        for (order in listOf(listOf(adminDef, aliceIsAdmin, foreignRevoke), listOf(foreignRevoke, adminDef, aliceIsAdmin))) {
            val r = AuthorityResolver.resolve(order, cid, owner)
            assertEquals(setOf(adminRole), r.rolesOf(alice), "a Grant at a foreign coordinate is not alice's Grant")
        }

        // And a foreign-coordinate grant alone confers nothing, however well signed.
        val foreignGrant = ed(ControlEntityKind.GRANT, "98".repeat(32), 1, null, grantJson(bob, listOf(adminRole)), owner, "foreign-bob")
        assertNull(AuthorityResolver.resolve(listOf(adminDef, foreignGrant), cid, owner).rank(bob))
    }

    // ---- S7: equal-version ties go to authority first ------------------------------------------

    @Test
    fun anEqualVersionRoleForkGoesToTheOwnerOverALowerRumorId() {
        // Alice (Admin, MANAGE_ROLES at position 1) and the owner both edit the Mod role at v2. Alice's
        // rumor id sorts first — rumor ids are grindable — but authority decides (CORD-04 §1).
        val aliceV2 = ed(ControlEntityKind.ROLE, modRole, 2, modDef, roleJson("Alice's", 5, "8"), alice, "0000")
        val ownerV2 = ed(ControlEntityKind.ROLE, modRole, 2, modDef, roleJson("Owner's", 5, "8"), owner, "ffff")
        val r = ControlFixtures.resolve(listOf(adminDef, modDef, aliceIsAdmin, aliceV2, ownerV2), owner)
        assertEquals("Owner's", r.roles()[modRole]?.name)

        // Among peers of equal rank the lower rumor id still settles it.
        val ownerV2b = ed(ControlEntityKind.ROLE, modRole, 2, modDef, roleJson("Owner's other", 5, "8"), owner, "0001")
        assertEquals("Owner's other", ControlFixtures.resolve(listOf(adminDef, modDef, aliceIsAdmin, ownerV2, ownerV2b), owner).roles()[modRole]?.name)
    }

    @Test
    fun anEqualVersionGrantForkGoesToTheHigherRankedGranter() {
        // Bob's Grant at v2: alice (rank 1) makes him Mod, the owner (rank 0) revokes him, same version.
        val bobV1 = ed(ControlEntityKind.GRANT, ControlFixtures.grantEid(bob), 1, null, grantJson(bob, emptyList()), owner, "bob-1")
        val aliceV2 = ed(ControlEntityKind.GRANT, ControlFixtures.grantEid(bob), 2, bobV1, grantJson(bob, listOf(modRole)), alice, "0000")
        val ownerV2 = ed(ControlEntityKind.GRANT, ControlFixtures.grantEid(bob), 2, bobV1, grantJson(bob, emptyList()), owner, "ffff")
        val r = ControlFixtures.resolve(listOf(adminDef, modDef, aliceIsAdmin, bobV1, aliceV2, ownerV2), owner)
        assertNull(r.rank(bob), "the owner's revoke wins the tie")
    }

    @Test
    fun pickHeadBreaksAnEqualVersionTieOnRankBeforeRumorId() {
        val low = ed(ControlEntityKind.CHANNEL, "cc".repeat(32), 3, null, "{}", alice, "0000")
        val high = ed(ControlEntityKind.CHANNEL, "cc".repeat(32), 3, null, "{}", owner, "ffff")
        val older = ed(ControlEntityKind.CHANNEL, "cc".repeat(32), 2, null, "{}", owner, "0001")
        val rank: AuthorRank = { if (it == owner) 0L else 1L }

        assertEquals(high, EditionFold.pickHead(listOf(low, high, older), rank) { true })
        assertEquals(low, EditionFold.pickHead(listOf(low, high, older), null) { true }, "without a rank: first passing candidate")
        assertEquals(low, EditionFold.pickHead(listOf(low, high, older), rank) { it !== high }, "a gated-out sibling never wins")
        assertEquals(older, EditionFold.pickHead(listOf(low, high, older), rank) { it === older }, "a lower version only when nothing above passes")
    }

    // ---- I4: the vac authority citation --------------------------------------------------------

    @Test
    fun aNonOwnerEditionWithoutACitationIsDropped() {
        // Alice holds BAN, but her banlist edition does not cite the Grant she acts under.
        val uncited = banlist(1, null, alice, "b1", carol)
        assertFalse(AuthorityResolver.resolve(listOf(adminDef, aliceIsAdmin, uncited), cid, owner).isBanned(carol))

        // The identical edition citing her Grant head is honored.
        val cited = banlist(1, null, alice, "b1", carol, vac = citation(aliceIsAdmin))
        assertTrue(AuthorityResolver.resolve(listOf(adminDef, aliceIsAdmin, cited), cid, owner).isBanned(carol))

        // The owner cites nothing.
        assertTrue(AuthorityResolver.resolve(listOf(banlist(1, null, owner, "b1", carol)), cid, owner).isBanned(carol))
    }

    @Test
    fun aChannelEditionWithoutACitationIsDroppedToo() {
        // Channels fold under their own gate (the name rule), which must still require the vac.
        val channelsRole = "33".repeat(32)
        val channelsDef = ed(ControlEntityKind.ROLE, channelsRole, 1, null, roleJson("Chans", 3, ConcordPermissions.of(ConcordPermissions.MANAGE_CHANNELS).toWire()), owner, "role-chans")
        val aliceChans = ed(ControlEntityKind.GRANT, ControlFixtures.grantEid(alice), 1, null, grantJson(alice, listOf(channelsRole)), owner, "grant-alice-chans")
        val channelId = "ce".repeat(32)

        fun channel(vac: AuthorityCitation?) = ed(ControlEntityKind.CHANNEL, channelId, 1, null, """{"name":"news"}""", alice, "chan-1", vac)

        val uncited = ConcordCommunityState.fold(listOf(channelsDef, aliceChans, channel(null)), cid, owner)
        assertTrue(uncited.channels.isEmpty(), "an uncited channel edition is not honored")
        val cited = ConcordCommunityState.fold(listOf(channelsDef, aliceChans, channel(citation(aliceChans))), cid, owner)
        assertEquals("news", cited.channels[channelId]?.definition?.name)
    }

    @Test
    fun aCitationMustNameTheActorsOwnGrantAtAVersionAndHashTheVerifierHolds() {
        val bobIsMod = ed(ControlEntityKind.GRANT, ControlFixtures.grantEid(bob), 1, null, grantJson(bob, listOf(modRole)), owner, "grant-bob")
        val base = listOf(adminDef, modDef, aliceIsAdmin, bobIsMod)

        fun bansCarolCiting(vac: AuthorityCitation) = AuthorityResolver.resolve(base + banlist(1, null, alice, "b1", carol, vac = vac), cid, owner).isBanned(carol)

        assertTrue(bansCarolCiting(citation(aliceIsAdmin)))
        assertFalse(bansCarolCiting(citation(bobIsMod)), "someone else's Grant is not the actor's authority")
        assertFalse(bansCarolCiting(citation(aliceIsAdmin, version = 2)), "a version the verifier has not synced parks the action")
        assertFalse(bansCarolCiting(citation(aliceIsAdmin, hash = ByteArray(32) { 7 })), "a forked or forged hash parks it too")
    }

    @Test
    fun aCitationOfASupersededGrantPassesTheSyncFloorButRankStillDecides() {
        // The head moved on (compaction discards superseded versions), so an older citation is
        // satisfied — the verdict is the CURRENT roster's. Promoted: honored. Demoted: dropped.
        val aliceV2 = ed(ControlEntityKind.GRANT, ControlFixtures.grantEid(alice), 2, aliceIsAdmin, grantJson(alice, listOf(adminRole)) + " ", owner, "grant-alice-2")
        val ban = banlist(1, null, alice, "b1", carol, vac = citation(aliceIsAdmin))
        assertTrue(AuthorityResolver.resolve(listOf(adminDef, aliceIsAdmin, aliceV2, ban), cid, owner).isBanned(carol))

        val demoted = ed(ControlEntityKind.GRANT, ControlFixtures.grantEid(alice), 2, aliceIsAdmin, grantJson(alice, emptyList()), owner, "grant-alice-revoke")
        assertFalse(
            AuthorityResolver.resolve(listOf(adminDef, aliceIsAdmin, demoted, ban), cid, owner).isBanned(carol),
            "citing the old valid Grant grandfathers nothing",
        )
    }

    @Test
    fun delegatedGrantsNeedTheGranterToCiteTheirOwnGrant() {
        // Alice (Admin) makes bob a Mod. Without her citation the grant does not stand.
        val bobByAlice = ed(ControlEntityKind.GRANT, ControlFixtures.grantEid(bob), 1, null, grantJson(bob, listOf(modRole)), alice, "grant-bob")
        assertNull(AuthorityResolver.resolve(listOf(adminDef, modDef, aliceIsAdmin, bobByAlice), cid, owner).rank(bob))
        val cited = bobByAlice.withCitation(citation(aliceIsAdmin))
        assertEquals(5L, AuthorityResolver.resolve(listOf(adminDef, modDef, aliceIsAdmin, cited), cid, owner).rank(bob))
    }

    @Test
    fun authorityCitationsCiteTheActorsFoldedGrantHead() {
        val r = AuthorityResolver.resolve(listOf(adminDef, aliceIsAdmin), cid, owner)

        assertNull(AuthorityCitations.forActor(r, owner), "the owner cites nothing")
        assertNull(AuthorityCitations.forActor(r, carol), "nor does someone holding no Grant")

        val vac = AuthorityCitations.forActor(r, alice)
        assertNotNull(vac)
        assertEquals(ControlFixtures.grantEid(alice), vac.grantId.toHexKey())
        assertEquals(aliceIsAdmin.version, vac.grantVersion)
        assertContentEquals(aliceIsAdmin.hash, vac.grantHash)
        assertTrue(AuthorityCitations.isSatisfied(r, alice, vac))

        // The editions overload folds the same roster.
        assertContentEquals(vac.grantHash, AuthorityCitations.forActor(listOf(adminDef, aliceIsAdmin), cid, owner, alice)?.grantHash)
    }

    // ---- I5: role_id ---------------------------------------------------------------------------

    @Test
    fun aRoleWhoseRoleIdNamesAnotherCoordinateIsRefused() {
        val grant = ed(ControlEntityKind.GRANT, ControlFixtures.grantEid(alice), 1, null, grantJson(alice, listOf(modRole)), owner, "g")

        val matching = ed(ControlEntityKind.ROLE, modRole, 1, null, roleJson("Mod", 5, "24", roleId = modRole), owner, "r")
        assertEquals(5L, AuthorityResolver.resolve(listOf(matching, grant), cid, owner).rank(alice))

        val legacy = ed(ControlEntityKind.ROLE, modRole, 1, null, roleJson("Mod", 5, "24"), owner, "r")
        assertEquals(5L, AuthorityResolver.resolve(listOf(legacy, grant), cid, owner).rank(alice), "a legacy role without role_id still reads")

        val mismatched = ed(ControlEntityKind.ROLE, modRole, 1, null, roleJson("Mod", 5, "24", roleId = adminRole), owner, "r")
        assertNull(AuthorityResolver.resolve(listOf(mismatched, grant), cid, owner).rank(alice), "role_id must equal the eid")
    }

    // ---- I17: caps -----------------------------------------------------------------------------

    @Test
    fun aRoleNamePastSixtyFourBytesFallsBackToThePreviousEdition() {
        val renamed = ed(ControlEntityKind.ROLE, modRole, 2, modDef, roleJson("m".repeat(65), 5, "24"), owner, "r2")
        assertEquals("Mod", ControlFixtures.resolve(listOf(modDef, renamed), owner).roles()[modRole]?.name)
        val atCap = ed(ControlEntityKind.ROLE, modRole, 2, modDef, roleJson("m".repeat(64), 5, "24"), owner, "r2")
        assertEquals("m".repeat(64), ControlFixtures.resolve(listOf(modDef, atCap), owner).roles()[modRole]?.name)
    }

    private fun roleIdOf(i: Int) = i.toString(16).padStart(64, '0')

    @Test
    fun aMemberHoldsAtMostSixtyFourRoles() {
        val roles = (1..70).map { ed(ControlEntityKind.ROLE, roleIdOf(it), 1, null, roleJson("R$it", 10 + it, "8"), owner, "r$it") }
        val everything = (1..70).map { roleIdOf(it) }
        val grant = ed(ControlEntityKind.GRANT, ControlFixtures.grantEid(alice), 1, null, grantJson(alice, everything), owner, "g")

        val held = AuthorityResolver.resolve(roles + grant, cid, owner).rolesOf(alice)
        assertEquals(everything.take(64).toSet(), held, "the first 64 role_ids, the rest ignored")
    }

    @Test
    fun aCommunityFoldsAtMostOneHundredRolesTheLowestRoleIds() {
        val roles = (1..101).map { ed(ControlEntityKind.ROLE, roleIdOf(it), 1, null, roleJson("R$it", 10, "16"), owner, "r$it") }
        val lowest = ed(ControlEntityKind.GRANT, ControlFixtures.grantEid(alice), 1, null, grantJson(alice, listOf(roleIdOf(1))), owner, "ga")
        val highest = ed(ControlEntityKind.GRANT, ControlFixtures.grantEid(bob), 1, null, grantJson(bob, listOf(roleIdOf(101))), owner, "gb")

        val r = AuthorityResolver.resolve(roles.reversed() + lowest + highest, cid, owner)
        assertEquals(100, r.roles().size)
        assertTrue(roleIdOf(101) !in r.roles(), "the highest role_id is the one ignored")
        assertTrue(r.hasPermission(alice, BAN))
        assertFalse(r.hasPermission(bob, BAN), "a grant of an ignored role confers nothing")
    }
}
