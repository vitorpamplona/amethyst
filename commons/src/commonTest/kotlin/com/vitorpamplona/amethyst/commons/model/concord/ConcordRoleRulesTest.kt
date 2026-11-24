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
package com.vitorpamplona.amethyst.commons.model.concord

import com.vitorpamplona.quartz.concord.cord04Roles.AuthorityResolver
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEdition
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEntityKind
import com.vitorpamplona.quartz.concord.crypto.ConcordKeyDerivation
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConcordRoleRulesTest {
    @Test
    fun theOwnerPlacesAnyRoleButPositionZero() {
        assertTrue(ConcordRoleRules.canPlace(myRank = 0, oldPosition = null, newPosition = 1))
        assertFalse(ConcordRoleRules.canPlace(myRank = 0, oldPosition = null, newPosition = 0), "position 0 is the owner's")
        assertEquals(1, ConcordRoleRules.highestPlaceable(0))
    }

    @Test
    fun anAdminOnlyPlacesBelowItself() {
        assertFalse(ConcordRoleRules.canPlace(myRank = 2, oldPosition = null, newPosition = 2), "equal cannot act on equal")
        assertTrue(ConcordRoleRules.canPlace(myRank = 2, oldPosition = null, newPosition = 3))
        assertFalse(ConcordRoleRules.canPlace(myRank = 2, oldPosition = 1, newPosition = 5), "a role above us stays out of reach")
        assertEquals(3, ConcordRoleRules.highestPlaceable(2))
    }

    private val owner = "0f".repeat(32)
    private val communityId = "cc".repeat(32).hexToByteArray()

    private fun ed(
        kind: ControlEntityKind,
        eid: String,
        content: String,
    ) = ControlEdition(kind, eid.hexToByteArray(), 1, null, null, content, owner, "r-$eid", 0)

    private fun role(
        id: String,
        name: String,
        position: Int,
    ) = ed(ControlEntityKind.ROLE, id, """{"name":"$name","position":$position,"permissions":"1"}""")

    private fun grant(
        member: String,
        vararg roles: String,
    ) = ed(
        ControlEntityKind.GRANT,
        ConcordKeyDerivation.grantCoordinate(communityId, member.hexToByteArray()).toHexKey(),
        """{"member":"$member","role_ids":[${roles.joinToString(",") { "\"$it\"" }}]}""",
    )

    @Test
    fun rolesListAndBadgesFollowRankThenId() {
        val admin = "a1".repeat(32)
        val helperB = "b2".repeat(32)
        val helperA = "a2".repeat(32)
        val both = "e1".repeat(32)
        val all = "e2".repeat(32)
        val authority =
            AuthorityResolver.resolve(
                listOf(
                    role(helperB, "Helper B", 2),
                    role(admin, "Admin", 1),
                    role(helperA, "Helper A", 2),
                    grant(both, helperB, helperA),
                    grant(all, helperB, admin, helperA),
                ),
                communityId,
                owner,
            )
        assertEquals(listOf("Admin", "Helper A", "Helper B"), ConcordRoleRules.byRank(authority.roles()).map { it.value.name })
        assertEquals("Admin", ConcordRoleRules.badgeRole(authority, all)?.name)
        // Two roles at one position: the badge is the same on every device, by role id.
        assertEquals("Helper A", ConcordRoleRules.badgeRole(authority, both)?.name)
        assertEquals(null, ConcordRoleRules.badgeRole(authority, owner))
    }
}
