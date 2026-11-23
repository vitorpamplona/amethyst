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

/**
 * What a legacy CORD-04 role editor may write. Every reader drops a role edition whose author does
 * not strictly outrank the role (lower position ranks higher; the owner is 0 and no role may take
 * 0), both where it stood and where it moves to, or that hands out a permission its author does not
 * hold itself (quartz `AuthorityResolver`'s role gate), so an editor offers nothing else.
 */
object ConcordRoleRules {
    /** Whether an editor of rank [myRank] may move a role from [oldPosition] (null: a new role) to [newPosition]. */
    fun canPlace(
        myRank: Long,
        oldPosition: Long?,
        newPosition: Long,
    ): Boolean = newPosition >= 1 && myRank < newPosition && (oldPosition == null || myRank < oldPosition)

    /**
     * Whether a role may carry [roleBits] when written by an editor holding [myBits]: only bits the
     * editor holds itself, or the edition would escalate through the role and every reader drops
     * it. The owner holds every bit. A delete is not judged on bits.
     */
    fun canGive(
        myBits: ConcordPermissions,
        roleBits: ConcordPermissions,
    ): Boolean = myBits.hasAll(roleBits)

    /**
     * The fold's whole rule for a role edition (the role's MANAGE_ROLES and Grant-citation checks
     * aside): [canPlace] for the positions and [canGive] for the bits it will carry.
     */
    fun canWrite(
        myRank: Long,
        myBits: ConcordPermissions,
        oldPosition: Long?,
        newPosition: Long,
        roleBits: ConcordPermissions,
    ): Boolean = canPlace(myRank, oldPosition, newPosition) && canGive(myBits, roleBits)

    /** The highest position an editor of rank [myRank] may give a role. */
    fun highestPlaceable(myRank: Long): Long = maxOf(1L, myRank + 1)

    /**
     * The server-wide permissions a legacy role carries, in the order an editor lists them. The
     * Roles v2 channel bits are left out: a legacy community has no use for them.
     */
    val EDITABLE_BITS: List<Int> =
        listOf(
            ConcordPermissions.MANAGE_ROLES,
            ConcordPermissions.MANAGE_CHANNELS,
            ConcordPermissions.MANAGE_METADATA,
            ConcordPermissions.KICK,
            ConcordPermissions.BAN,
            ConcordPermissions.MANAGE_MESSAGES,
            ConcordPermissions.CREATE_INVITE,
            ConcordPermissions.VIEW_AUDIT_LOG,
            ConcordPermissions.MENTION_EVERYONE,
            ConcordPermissions.PIN_MESSAGES,
        )
}
