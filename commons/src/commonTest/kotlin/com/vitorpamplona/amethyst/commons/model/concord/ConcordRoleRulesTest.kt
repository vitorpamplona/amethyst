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

import com.vitorpamplona.quartz.concord.cord04Roles.ConcordPermissions
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

    @Test
    fun aRoleMayOnlyCarryBitsTheEditorHolds() {
        val mine = ConcordPermissions.of(ConcordPermissions.MANAGE_ROLES, ConcordPermissions.KICK)
        assertTrue(ConcordRoleRules.canGive(mine, ConcordPermissions.of(ConcordPermissions.KICK)))
        assertFalse(ConcordRoleRules.canGive(mine, ConcordPermissions.of(ConcordPermissions.KICK, ConcordPermissions.BAN)), "BAN is not ours to hand out")
        assertTrue(ConcordRoleRules.canGive(ConcordPermissions.ALL, ConcordPermissions.of(ConcordPermissions.BAN)), "the owner holds every bit")
        assertFalse(
            ConcordRoleRules.canWrite(myRank = 2, myBits = mine, oldPosition = 3, newPosition = 3, roleBits = ConcordPermissions.of(ConcordPermissions.BAN)),
            "a role beneath us still can't carry a bit we lack",
        )
        assertTrue(ConcordRoleRules.canWrite(myRank = 2, myBits = mine, oldPosition = 3, newPosition = 4, roleBits = ConcordPermissions.of(ConcordPermissions.KICK)))
    }
}
