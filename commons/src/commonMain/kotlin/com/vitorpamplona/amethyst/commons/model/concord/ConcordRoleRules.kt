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
import com.vitorpamplona.quartz.concord.cord04Roles.ConcordPermissions
import com.vitorpamplona.quartz.concord.cord04Roles.RoleEntity

/**
 * What a legacy CORD-04 role editor may write. Every reader drops a role edition whose author does
 * not strictly outrank the role (lower position ranks higher; the owner is 0 and no role may take
 * 0), both where it stood and where it moves to, or that hands out a permission its author does not
 * hold itself (quartz `AuthorityResolver`'s role gate), so an editor offers nothing else. The whole
 * gate for one edition is `AuthorityResolver.canWriteRole`; this holds what a form needs before a
 * role exists to ask about: the positions it may offer and the bits it lists.
 */
object ConcordRoleRules {
    /**
     * Rank order (CORD-04 §3): the lower position ranks higher; peers at one position (a Roles v2
     * rank key two roles share) by role id, so every screen and every device lists them alike.
     */
    val rankOrder: Comparator<Map.Entry<String, RoleEntity>> =
        compareBy<Map.Entry<String, RoleEntity>> { it.value.position }.thenBy { it.key.lowercase() }

    /** The live (not deleted) [roles], highest-ranked first ([rankOrder]). */
    fun byRank(roles: Map<String, RoleEntity>): List<Map.Entry<String, RoleEntity>> = roles.entries.filter { !it.value.deleted }.sortedWith(rankOrder)

    /** The role a member's badge names: the highest-ranked one [member] holds ([rankOrder]), or null for none. */
    fun badgeRole(
        authority: AuthorityResolver,
        member: String,
    ): RoleEntity? {
        val held = authority.rolesOf(member)
        if (held.isEmpty()) return null
        return byRank(authority.roles().filterKeys { it in held }).firstOrNull()?.value
    }

    /** Whether an editor of rank [myRank] may move a role from [oldPosition] (null: a new role) to [newPosition]. */
    fun canPlace(
        myRank: Long,
        oldPosition: Long?,
        newPosition: Long,
    ): Boolean = newPosition >= 1 && myRank < newPosition && (oldPosition == null || myRank < oldPosition)

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
