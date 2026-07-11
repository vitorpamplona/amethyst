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

/**
 * Resolves the owner-rooted authority state of a Concord community from its
 * folded Control Plane (CORD-04).
 *
 * "The Roster is owner-rooted: every Grant and Role is signed by an npub the
 * Roster ranks strictly above it, and the chain terminates at the owner."
 *
 * Build one with [resolve] from the current entity heads (the values of
 * [EditionFold.fold]) plus the community's known owner pubkey. It then answers:
 *  - [rank] — a member's authority (lower is higher; owner is [OWNER_RANK]; a
 *    member with no validly-granted role has no rank).
 *  - [effectivePermissions] — the union of a member's roles' bits (owner: all).
 *  - [isBanned] — membership in the healed banlist union.
 *  - [canActOn] — whether an actor may take a permissioned action on a target:
 *    the actor must hold the bit, must strictly outrank the target (equal cannot
 *    act on equal), and the owner is unremovable.
 *
 * Grants are validated by a fixpoint that only ever empowers members reachable
 * from the owner: a Grant is honored when its signer already outranks every
 * assigned Role and holds [ConcordPermissions.MANAGE_ROLES]. Cycles that never
 * touch the owner can never bootstrap themselves.
 */
class AuthorityResolver private constructor(
    private val ownerLower: String,
    private val roles: Map<String, RoleEntity>,
    private val memberRoles: Map<String, Set<String>>,
    private val banned: Set<String>,
) {
    fun isOwner(pubKey: String): Boolean = pubKey.lowercase() == ownerLower

    fun isBanned(pubKey: String): Boolean = pubKey.lowercase() in banned

    /** The role ids a member currently holds (empty for the owner and for plain members). */
    fun rolesOf(pubKey: String): Set<String> = memberRoles[pubKey.lowercase()] ?: emptySet()

    /**
     * The set of pubkeys that hold at least one validly-granted role (lowercase
     * hex). This is the *privileged* roster — admins/moderators and any other
     * role-holders — and excludes the owner and silent key-holding members, since
     * plain membership is key possession and leaves no Control-Plane trace.
     */
    fun roleHolders(): Set<String> = memberRoles.keys

    /** The healed banlist union (lowercase hex). */
    fun bannedMembers(): Set<String> = banned

    /** The member's rank, lower being higher authority; null = no authority. Owner = [OWNER_RANK]. */
    fun rank(pubKey: String): Long? {
        val m = pubKey.lowercase()
        if (m == ownerLower) return OWNER_RANK
        val held = memberRoles[m] ?: return null
        return held.mapNotNull { roles[it]?.position }.minOrNull()
    }

    /** The union of a member's roles' permission bits (owner holds every bit). */
    fun effectivePermissions(pubKey: String): ConcordPermissions {
        val m = pubKey.lowercase()
        if (m == ownerLower) return ConcordPermissions.ALL
        val held = memberRoles[m] ?: return ConcordPermissions.NONE
        var acc = ConcordPermissions.NONE
        for (id in held) roles[id]?.let { acc = acc union it.permissionBits() }
        return acc
    }

    /** True if the member holds [bit] and is not banned. */
    fun hasPermission(
        pubKey: String,
        bit: Int,
    ): Boolean = !isBanned(pubKey) && effectivePermissions(pubKey).has(bit)

    /**
     * Whether [actor] may take the action guarded by permission [bit] against
     * [target]. Requires: actor not banned, actor holds [bit], the owner is never
     * a valid target (unremovable), and actor strictly outranks target — "equal
     * cannot act on equal".
     */
    fun canActOn(
        actor: String,
        target: String,
        bit: Int,
    ): Boolean {
        if (!hasPermission(actor, bit)) return false
        if (isOwner(target)) return false
        val actorRank = rank(actor) ?: return false
        val targetRank = rank(target) ?: Long.MAX_VALUE // no roles ⇒ lowest authority
        return actorRank < targetRank
    }

    companion object {
        /** The owner's rank — supreme and unremovable. No Role may claim it. */
        const val OWNER_RANK = 0L

        fun resolve(
            heads: Collection<ControlEdition>,
            ownerPubKey: String,
        ): AuthorityResolver {
            val ownerLower = ownerPubKey.lowercase()

            // Roles: keyed by role id (the edition entity id). Drop deleted roles
            // and any that illegally claim position 0 (owner-peer).
            val roles = HashMap<String, RoleEntity>()
            for (e in heads) {
                if (e.entityKind != ControlEntityKind.ROLE) continue
                val r = ConcordJson.decodeOrNull<RoleEntity>(e.content) ?: continue
                if (r.deleted || r.position < 1) continue
                roles[e.entityIdHex] = r
            }

            // Grants: one head per member; remember the signing actor (granter).
            val grants =
                heads.mapNotNull { e ->
                    if (e.entityKind != ControlEntityKind.GRANT) return@mapNotNull null
                    val g = ConcordJson.decodeOrNull<GrantEntity>(e.content) ?: return@mapNotNull null
                    ParsedGrant(g.member.lowercase(), g.roleIds, e.author.lowercase())
                }

            // Banlist: heal to the union of every banlist head.
            val banned = HashSet<String>()
            for (e in heads) {
                if (e.entityKind != ControlEntityKind.BANLIST) continue
                ConcordJson.decodeBanlist(e.content)?.forEach { banned.add(it.lowercase()) }
            }

            val memberRoles = HashMap<String, Set<String>>()

            fun rankOf(member: String): Long? {
                if (member == ownerLower) return OWNER_RANK
                val held = memberRoles[member] ?: return null
                return held.mapNotNull { roles[it]?.position }.minOrNull()
            }

            fun holdsManageRoles(member: String): Boolean {
                if (member == ownerLower) return true
                val held = memberRoles[member] ?: return false
                return held.any { roles[it]?.permissionBits()?.has(ConcordPermissions.MANAGE_ROLES) == true }
            }

            // Fixpoint: apply grants whose signer outranks every assigned role and
            // holds MANAGE_ROLES. Only the owner is empowered a priori, so the
            // roster grows strictly outward from the owner.
            var changed = true
            while (changed) {
                changed = false
                for (g in grants) {
                    val granterRank = rankOf(g.granter) ?: continue
                    if (!holdsManageRoles(g.granter)) continue
                    val assigned = g.roleIds.filter { roles.containsKey(it) }
                    if (assigned.any { granterRank >= roles.getValue(it).position }) continue // must strictly outrank each
                    val newSet = assigned.toSet()
                    if (memberRoles[g.member] != newSet) {
                        memberRoles[g.member] = newSet
                        changed = true
                    }
                }
            }

            return AuthorityResolver(ownerLower, roles, memberRoles, banned)
        }
    }

    private class ParsedGrant(
        val member: String,
        val roleIds: List<String>,
        val granter: String,
    )
}
