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

    private fun banlist(vararg banned: String) =
        ControlEdition(
            ControlEntityKind.BANLIST,
            "44".repeat(32).hexToByteArray(),
            0,
            null,
            null,
            "[${banned.joinToString(",") { "\"$it\"" }}]",
            owner,
            "ban",
            0,
        )

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
}
