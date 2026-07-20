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

import com.vitorpamplona.quartz.concord.cord04Roles.ConcordPermissions.Companion.BAN
import com.vitorpamplona.quartz.concord.cord04Roles.ConcordPermissions.Companion.KICK
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthorityResolverTest {
    private val owner = "0f".repeat(32)
    private val alice = "a1".repeat(32)
    private val bob = "b2".repeat(32)
    private val carol = "c3".repeat(32)
    private val dave = "d4".repeat(32)

    private val adminRole = "11".repeat(32)
    private val modRole = "22".repeat(32)

    // admin: position 1, KICK|BAN|MANAGE_ROLES = 8|16|1 = 25
    private val adminJson = """{"name":"Admin","position":1,"permissions":"25"}"""

    // mod: position 5, KICK only = 8 (no MANAGE_ROLES)
    private val modJson = """{"name":"Mod","position":5,"permissions":"8"}"""

    private fun role(
        roleId: String,
        json: String,
    ) = ControlEdition(ControlEntityKind.ROLE, roleId.hexToByteArray(), 0, null, null, json, owner, "role-$roleId", 0)

    private fun grant(
        grantId: String,
        member: String,
        roleIds: List<String>,
        granter: String,
    ) = ControlEdition(
        ControlEntityKind.GRANT,
        grantId.hexToByteArray(),
        0,
        null,
        null,
        """{"member":"$member","role_ids":[${roleIds.joinToString(",") { "\"$it\"" }}]}""",
        granter,
        "grant-$grantId",
        0,
    )

    private fun banlist(vararg banned: String) = banlistBy(owner, "ban", *banned)

    private fun banlistBy(
        author: String,
        rumorId: String,
        vararg banned: String,
    ) = ControlEdition(
        ControlEntityKind.BANLIST,
        "44".repeat(32).hexToByteArray(),
        0,
        null,
        null,
        "[${banned.joinToString(",") { "\"$it\"" }}]",
        author,
        rumorId,
        0,
    )

    @Test
    fun aManageRolesHolderCannotPromoteItsOwnRoleAboveItself() {
        // Grants are rank-gated but role editions were not: holding MANAGE_ROLES was the whole test. So
        // a mid-tier moderator could rewrite the very role they hold — claiming position 1 and every
        // permission bit — and in one more edition demote the real admins below them.
        val modWithManageRoles = """{"name":"Mod","position":5,"permissions":"9"}""" // KICK|MANAGE_ROLES
        val modV0 = role(modRole, modWithManageRoles)
        val selfPromotion =
            ControlEdition(
                ControlEntityKind.ROLE,
                modRole.hexToByteArray(),
                1,
                modV0.hash, // chains correctly, so only the rank gate can stop it
                null,
                """{"name":"Mod","position":1,"permissions":"18446744073709551615"}""",
                bob, // holds the role being edited
                "role-$modRole-selfpromo",
                1,
            )

        val r =
            AuthorityResolver.resolve(
                listOf(
                    role(adminRole, adminJson), // position 1, owner-authored
                    modV0, // position 5, owner-authored
                    grant("31".repeat(32), alice, listOf(adminRole), granter = owner),
                    grant("32".repeat(32), bob, listOf(modRole), granter = owner),
                    selfPromotion,
                ),
                owner,
            )

        assertEquals(5, r.roles()[modRole]?.position, "Bob's self-promotion must not take effect")
        assertFalse(r.effectivePermissions(bob).has(BAN), "Bob must not gain bits his role never held")
        assertTrue(r.canActOn(alice, bob, KICK), "the real admin must still outrank the moderator")
        assertFalse(r.canActOn(bob, alice, KICK), "the moderator must not gain authority over the admin")
    }

    @Test
    fun aRevokeCannotStripAMemberWhoOutranksTheGranter() {
        // A revoke carries no role ids, and "outrank every assigned role" is vacuously true over an
        // empty list — so without a check on the TARGET's standing rank, any MANAGE_ROLES holder could
        // strip anyone, the owner's admins included. Armada gates this the same way: a grant is an
        // action ON the member, so demotion must be at least as hard as promotion.
        val modWithManageRoles = """{"name":"Mod","position":5,"permissions":"9"}""" // KICK|MANAGE_ROLES
        val adminGrantId = "31".repeat(32)
        val adminGrant = grant(adminGrantId, alice, listOf(adminRole), granter = owner)
        val revokeByMod =
            ControlEdition(
                ControlEntityKind.GRANT,
                adminGrantId.hexToByteArray(),
                1,
                adminGrant.hash,
                null,
                """{"member":"$alice","role_ids":[]}""", // strip the admin
                bob, // position 5, holds MANAGE_ROLES but is outranked by Alice
                "grant-$adminGrantId-revoke",
                1,
            )

        val r =
            AuthorityResolver.resolve(
                listOf(
                    role(adminRole, adminJson), // position 1
                    role(modRole, modWithManageRoles), // position 5
                    adminGrant,
                    grant("32".repeat(32), bob, listOf(modRole), granter = owner),
                    revokeByMod,
                ),
                owner,
            )

        assertEquals(1L, r.rank(alice), "a moderator must not be able to demote an admin above it")
    }

    @Test
    fun anAdminCanStillRevokeAMemberBeneathIt() {
        // The gate must not block legitimate moderation: an admin may revoke a moderator's roles.
        val modGrantId = "32".repeat(32)
        val modGrant = grant(modGrantId, bob, listOf(modRole), granter = owner)
        val revokeByAdmin =
            ControlEdition(
                ControlEntityKind.GRANT,
                modGrantId.hexToByteArray(),
                1,
                modGrant.hash,
                null,
                """{"member":"$bob","role_ids":[]}""",
                alice, // admin at position 1, outranks Bob at 5
                "grant-$modGrantId-revoke",
                1,
            )

        val r =
            AuthorityResolver.resolve(
                listOf(
                    role(adminRole, adminJson),
                    role(modRole, modJson),
                    grant("31".repeat(32), alice, listOf(adminRole), granter = owner),
                    modGrant,
                    revokeByAdmin,
                ),
                owner,
            )

        assertNull(r.rank(bob), "an admin may still revoke a moderator beneath it")
    }

    @Test
    fun aManageRolesHolderCanStillManageRolesBeneathIt() {
        // The gate must not break legitimate delegation: an admin may still edit a role below its rank.
        val modV0 = role(modRole, modJson)
        val renamed =
            ControlEdition(
                ControlEntityKind.ROLE,
                modRole.hexToByteArray(),
                1,
                modV0.hash,
                null,
                """{"name":"Moderator","position":5,"permissions":"8"}""",
                alice, // admin at position 1, outranks the role being edited
                "role-$modRole-rename",
                1,
            )

        val r =
            AuthorityResolver.resolve(
                listOf(
                    role(adminRole, adminJson),
                    modV0,
                    grant("31".repeat(32), alice, listOf(adminRole), granter = owner),
                    renamed,
                ),
                owner,
            )

        assertEquals("Moderator", r.roles()[modRole]?.name, "an admin may edit a role beneath it")
        assertEquals(5, r.roles()[modRole]?.position)
    }

    @Test
    fun ranksPermissionsAndActionAuthorityAreOwnerRooted() {
        val heads =
            listOf(
                role(adminRole, adminJson),
                role(modRole, modJson),
                // alice's grant appears BEFORE the owner's grant to prove fixpoint order-independence
                grant("ba".repeat(32), bob, listOf(modRole), granter = alice),
                grant("ab".repeat(32), alice, listOf(adminRole), granter = owner),
            )
        val r = AuthorityResolver.resolve(heads, owner)

        assertTrue(r.isOwner(owner))
        assertEquals(0L, r.rank(owner))
        assertEquals(1L, r.rank(alice))
        assertEquals(5L, r.rank(bob))
        assertNull(r.rank(carol)) // no grant ⇒ no authority

        assertTrue(r.effectivePermissions(alice).has(BAN))
        assertFalse(r.effectivePermissions(bob).has(BAN))
        assertTrue(r.effectivePermissions(bob).has(KICK))

        // Higher rank can act on lower; equal cannot act on equal; nobody on owner.
        assertTrue(r.canActOn(alice, bob, BAN))
        assertFalse(r.canActOn(bob, alice, KICK)) // lower cannot act on higher
        assertFalse(r.canActOn(alice, alice, KICK)) // equal-on-equal
        assertFalse(r.canActOn(alice, owner, BAN)) // owner unremovable
        assertTrue(r.canActOn(owner, alice, BAN)) // owner supreme
    }

    @Test
    fun grantsFromUnauthorizedSignersAreIgnored() {
        val heads =
            listOf(
                role(adminRole, adminJson),
                // carol has no authority, so her grant to dave is dropped
                grant("cd".repeat(32), dave, listOf(adminRole), granter = carol),
            )
        val r = AuthorityResolver.resolve(heads, owner)
        assertNull(r.rank(dave))
        assertEquals(ConcordPermissions.NONE.bits, r.effectivePermissions(dave).bits)
    }

    @Test
    fun granterMustHoldManageRolesAndOutrankAssignedRole() {
        val heads =
            listOf(
                role(adminRole, adminJson),
                role(modRole, modJson),
                grant("ab".repeat(32), alice, listOf(modRole), granter = owner), // alice is a mod (no MANAGE_ROLES)
                grant("ae".repeat(32), dave, listOf(adminRole), granter = alice), // mod cannot grant admin
            )
        val r = AuthorityResolver.resolve(heads, owner)
        assertEquals(5L, r.rank(alice))
        assertNull(r.rank(dave)) // rejected: alice lacks MANAGE_ROLES and doesn't outrank admin
    }

    @Test
    fun bannedMembersVanishFromAuthority() {
        val heads =
            listOf(
                role(adminRole, adminJson),
                grant("ab".repeat(32), alice, listOf(adminRole), granter = owner),
                banlist(alice),
            )
        val r = AuthorityResolver.resolve(heads, owner)
        assertTrue(r.isBanned(alice))
        // A banned actor can take no action even though the role bit is present.
        assertFalse(r.hasPermission(alice, BAN))
        assertFalse(r.canActOn(alice, bob, BAN))
    }

    @Test
    fun concurrentBansHealIntoAUnionAndAreNeverDropped() {
        // Two authorized moderators ban different abusers at the same banlist version — a
        // fork of the single banlist doc. Folding to one chain tip would silently drop the
        // loser's ban and let that abuser back in; the union keeps both (M1 / CORD-06
        // down-only healing).
        val heads =
            listOf(
                role(adminRole, adminJson),
                grant("ab".repeat(32), alice, listOf(adminRole), granter = owner), // alice gains BAN
                banlistBy(owner, "ban-owner", bob), // owner bans bob
                banlistBy(alice, "ban-alice", carol), // alice concurrently bans carol
            )
        val r = AuthorityResolver.resolve(heads, owner)
        assertTrue(r.isBanned(bob))
        assertTrue(r.isBanned(carol))
    }

    @Test
    fun banlistEditionsFromUnauthorizedSignersAreIgnored() {
        // carol holds no BAN permission, so her ban of dave must not take effect.
        val heads =
            listOf(
                role(adminRole, adminJson),
                banlistBy(carol, "ban-carol", dave),
            )
        val r = AuthorityResolver.resolve(heads, owner)
        assertFalse(r.isBanned(dave))
    }

    @Test
    fun deletedRolesAndPositionZeroAreDropped() {
        val heads =
            listOf(
                role(adminRole, """{"name":"Admin","position":1,"permissions":"25","deleted":true}"""),
                role(modRole, """{"name":"Peer","position":0,"permissions":"25"}"""), // illegal position 0
                grant("ab".repeat(32), alice, listOf(adminRole, modRole), granter = owner),
            )
        val r = AuthorityResolver.resolve(heads, owner)
        assertNull(r.rank(alice)) // both assigned roles are invalid
    }

    /**
     * The reference client (Armada) writes a role's `scope` as an object
     * (`{"kind":"server"}`), NOT a bare string. Typing the field as `String` made the whole
     * RoleEntity fail to decode, dropping the role and every grant that depended on it — the
     * community then had no resolvable admins, so authority-gated metadata/channels vanished.
     */
    @Test
    fun objectScopedRoleDecodesAndItsGrantResolves() {
        val heads =
            listOf(
                role(adminRole, """{"name":"Admin","position":1,"permissions":"25","scope":{"kind":"server"},"color":0}"""),
                grant("ab".repeat(32), alice, listOf(adminRole), granter = owner),
            )
        val r = AuthorityResolver.resolve(heads, owner)
        assertEquals(1L, r.rank(alice))
        assertTrue(r.effectivePermissions(alice).has(BAN))
    }

    /**
     * The owner grants alice Admin (v0); an UNAUTHORIZED key mints a higher-version grant on the
     * same coordinate stripping her roles. The structural head is the rogue v1, but an edition
     * whose signer isn't authorized is dropped (CORD-04 §1) — so the fold must NOT let the rogue
     * supersede the owner's grant. alice keeps Admin. (This was the live Soapbox failure.)
     */
    @Test
    fun rogueHigherVersionGrantCannotSupersedeALegitGrant() {
        val grantId = "ab".repeat(32)
        val ownerGrant = grant(grantId, alice, listOf(adminRole), granter = owner) // v0, prev null
        val rogueV1 =
            ControlEdition(
                ControlEntityKind.GRANT,
                grantId.hexToByteArray(),
                1,
                ownerGrant.hash, // chains onto the owner's grant, so it wins the STRUCTURAL fold
                null,
                """{"member":"$alice","role_ids":[]}""",
                carol, // an unauthorized signer
                "grant-$grantId-rogue",
                1,
            )
        val r = AuthorityResolver.resolve(listOf(role(adminRole, adminJson), ownerGrant, rogueV1), owner)
        assertEquals(1L, r.rank(alice)) // rogue v1 dropped; the owner's v0 grant stands
    }
}
