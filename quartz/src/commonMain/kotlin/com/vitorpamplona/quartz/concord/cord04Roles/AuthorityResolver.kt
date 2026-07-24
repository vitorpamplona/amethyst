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

import com.vitorpamplona.quartz.nip01Core.core.toHexKey

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
@ConsistentCopyVisibility
data class AuthorityResolver private constructor(
    private val ownerLower: String,
    private val roles: Map<String, RoleEntity>,
    private val memberRoles: Map<String, Set<String>>,
    private val banned: Set<String>,
) {
    /** The resolved role definitions (authority-gated), keyed by role id. Safe for display. */
    fun roles(): Map<String, RoleEntity> = roles

    /** The role definitions [pubKey] currently holds (empty for the owner and plain members). */
    fun rolesFor(pubKey: String): List<RoleEntity> = rolesOf(pubKey).mapNotNull { roles[it] }

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
            editions: Collection<ControlEdition>,
            ownerPubKey: String,
        ): AuthorityResolver {
            val ownerLower = ownerPubKey.lowercase()

            // Chains grouped by entity: one role chain per role id, one grant chain per member
            // coordinate. We fold each chain through AUTHORIZED editions only, so a rogue cannot
            // supersede a legit edition by minting a higher version from an unprivileged key
            // (CORD-04 §1: "an edition whose signer isn't authorized is dropped").
            val roleChains = editions.filter { it.entityKind == ControlEntityKind.ROLE }.groupBy { it.entityIdHex }
            val grantChains = editions.filter { it.entityKind == ControlEntityKind.GRANT }.groupBy { it.entityIdHex }

            var roles: Map<String, RoleEntity> = emptyMap()
            var memberRoles: Map<String, Set<String>> = emptyMap()

            // Authority helpers read the CURRENT (previous-pass) roster, so within a pass a granter's
            // rank is judged by the chain already settled behind it — the owner-rooted resolution the
            // spec requires ("the fold starts at the owner ... and resolves outward").
            fun rankOf(member: String): Long? {
                if (member == ownerLower) return OWNER_RANK
                val held = memberRoles[member] ?: return null
                return held.mapNotNull { roles[it]?.position }.minOrNull()
            }

            // The bits a member currently holds, evaluated against the chain settled so far — the same
            // owner-rooted basis as rankOf. Needed inside the fixpoint; effectivePermissionsOf below is
            // the post-settlement view.
            fun bitsOf(member: String): ConcordPermissions {
                if (member == ownerLower) return ConcordPermissions.ALL
                val held = memberRoles[member] ?: return ConcordPermissions.NONE
                var acc = ConcordPermissions.NONE
                for (id in held) roles[id]?.let { acc = acc union it.permissionBits() }
                return acc
            }

            fun holdsManageRoles(member: String): Boolean {
                if (member == ownerLower) return true
                val held = memberRoles[member] ?: return false
                return held.any { roles[it]?.permissionBits()?.has(ConcordPermissions.MANAGE_ROLES) == true }
            }

            // Owner-rooted fixpoint: each pass only ever empowers members reachable from the owner, so
            // the roster grows monotonically and settles. Bounded by the edition count as a backstop.
            val maxPasses = editions.size + 1
            var pass = 0
            while (pass++ <= maxPasses) {
                // Roles: a role edition is authorized when its author is the owner or holds MANAGE_ROLES.
                // The gate is applied to the chain's ORDERED CANDIDATES, never used to pre-filter the
                // chain — see EditionFold.candidates for why filtering first orphans honest editions.
                fun roleGate(
                    entity: String,
                    e: ControlEdition,
                ): Boolean {
                    val author = e.author.lowercase()
                    if (author == ownerLower) return true
                    if (!holdsManageRoles(author)) return false
                    val authorRank = rankOf(author) ?: return false
                    val r = ConcordJson.decodeOrNull<RoleEntity>(e.content) ?: return false
                    // MANAGE_ROLES alone was the whole test, which let any holder rewrite the
                    // role they hold — position 1 with every bit — and then demote the real
                    // admins beneath them. Grants are gated on rank (a granter must outrank
                    // what it hands out); role editions must be too, in both directions:
                    //   - it may not claim a position at or above the author's own rank, and
                    //   - it may not touch a role that already sits at or above them.
                    // A delete keeps only the second rule: you may retire a role beneath you.
                    val currentPosition = roles[entity]?.position
                    if (currentPosition != null && currentPosition <= authorRank) return false
                    if (!r.deleted && r.position <= authorRank) return false
                    // Nor may it grant bits the author does not itself hold, which would
                    // otherwise escalate through a role rather than through a grant.
                    return r.deleted || bitsOf(author).hasAll(r.permissionBits())
                }

                val newRoles = HashMap<String, RoleEntity>()
                for ((entity, chain) in roleChains) {
                    val head = EditionFold.foldEntityGated(chain) { roleGate(entity, it) } ?: continue
                    val r = ConcordJson.decodeOrNull<RoleEntity>(head.content) ?: continue
                    if (r.deleted || r.position < 1) continue // no role may claim the owner's position 0
                    newRoles[entity] = r
                }

                // Grants: an edition is authorized when its granter is the owner, or holds MANAGE_ROLES
                // AND strictly outranks every role it hands out. Same candidate-then-gate shape, so a
                // rogue grant is dropped without orphaning the honest grants chained above it.
                fun grantGate(e: ControlEdition): Boolean {
                    val granter = e.author.lowercase()
                    if (granter == ownerLower) return true
                    if (!holdsManageRoles(granter)) return false
                    val granterRank = rankOf(granter) ?: return false
                    val g = ConcordJson.decodeOrNull<GrantEntity>(e.content) ?: return false
                    // Must strictly outrank each assigned role that actually exists...
                    if (!g.roleIds.all { rid -> newRoles[rid]?.let { granterRank < it.position } ?: true }) return false
                    // ...and outrank the member being edited. A grant is an action ON that
                    // member, and a REVOKE carries no role ids at all — `all {}` over an
                    // empty list is vacuously true, so without this any MANAGE_ROLES holder
                    // could strip anyone's roles, the owner's admins included. Demotion has
                    // to be at least as hard as promotion.
                    val targetRank = rankOf(g.member.lowercase())
                    return targetRank == null || granterRank < targetRank
                }

                val newMemberRoles = HashMap<String, Set<String>>()
                for ((_, chain) in grantChains) {
                    val head = EditionFold.foldEntityGated(chain, gate = ::grantGate) ?: continue
                    val g = ConcordJson.decodeOrNull<GrantEntity>(head.content) ?: continue
                    newMemberRoles[g.member.lowercase()] = g.roleIds.filter { newRoles.containsKey(it) }.toSet()
                }

                if (newRoles == roles && newMemberRoles == memberRoles) break
                roles = newRoles
                memberRoles = newMemberRoles
            }

            // The union of a member's roles' permission bits (owner holds every bit).
            fun effectivePermissionsOf(member: String): ConcordPermissions {
                if (member == ownerLower) return ConcordPermissions.ALL
                val held = memberRoles[member] ?: return ConcordPermissions.NONE
                var acc = ConcordPermissions.NONE
                for (id in held) roles[id]?.let { acc = acc union it.permissionBits() }
                return acc
            }

            // Banlist: honored only from a signer holding BAN (or the owner). The banlist is a single
            // replaced doc, so fold its chain to the head first — that honors a legitimate unban, which
            // is a *chained* edition replacing the previous set (e.g. ban→unban). Then heal concurrent
            // forks: two moderators who ban different abusers at the same chain version fork the doc, and
            // folding to one head would silently drop the other's ban. Union in every authorized edition
            // that is NOT an ancestor of the head — those are the parallel bans the chain never absorbed.
            // Ancestors (superseded by the chain, including an unban's now-cleared target) are already
            // reflected by the head and must not be resurrected. This is CORD-06's "down-only healing":
            // a concurrent ban is never lost, while an on-chain unban still takes effect.
            val allBanlist = editions.filter { it.entityKind == ControlEntityKind.BANLIST }

            fun banGate(e: ControlEdition): Boolean = e.author.lowercase() == ownerLower || effectivePermissionsOf(e.author.lowercase()).has(ConcordPermissions.BAN)
            val authorizedBanlist = allBanlist.filter(::banGate)

            // CORD-04 §3's rank rule binds "every action", and it names banning as its example ("an
            // admin cannot ban a peer admin"); §5 step 3 restates it. Only §4, which defines the
            // Banlist, states the BAN-bit half alone — which is why every implementation (ours and
            // Armada's) shipped the bit check without the rank check, letting the most junior BAN
            // holder ban the admins above them and the owner. See
            // docs/concord-banlist-rank-conformance.md.
            //
            // The rule is stated per TARGET, but the Banlist is one whole-list document, so it is
            // enforced as a DELTA rule: an edition may only add or remove npubs its signer strictly
            // outranks. Entries it may not act on are ignored and the rest of the edition applies —
            // rejecting the whole edition would discard the bulk-ban §4 recommends as the collision
            // remedy, and would let a rogue grief the list by forcing rejections.
            fun canBanTarget(
                author: String,
                target: String,
            ): Boolean {
                // Position 0 is "supreme and unremovable" (§2) and nothing may outrank it, so the
                // owner is never a valid target — not even for themselves.
                if (target == ownerLower) return false
                if (author == ownerLower) return true
                if (!effectivePermissionsOf(author).has(ConcordPermissions.BAN)) return false
                val authorRank = rankOf(author) ?: return false
                val targetRank = rankOf(target) ?: Long.MAX_VALUE // no roles ⇒ lowest authority
                return authorRank < targetRank
            }

            val byHash = allBanlist.associateBy { it.hashHex }
            val effective = HashMap<String, Set<String>>()

            // The list an edition actually establishes: its parent's effective list, plus only the
            // additions its signer may make and minus only the removals its signer may make. Walks
            // the parent chain, so it is memoized; `visiting` also terminates a prevHash cycle.
            fun effectiveList(
                edition: ControlEdition,
                visiting: MutableSet<String>,
            ): Set<String> {
                effective[edition.hashHex]?.let { return it }
                if (!visiting.add(edition.hashHex)) return emptySet()

                val parent = edition.prevHash?.toHexKey()?.let { byHash[it] }
                val base = parent?.let { effectiveList(it, visiting) } ?: emptySet()
                val author = edition.author.lowercase()
                val claimed = ConcordJson.decodeBanlist(edition.content)?.mapTo(HashSet()) { it.lowercase() }

                // A malformed body changes nothing rather than clearing the list.
                val result =
                    if (claimed == null) {
                        base
                    } else {
                        val out = HashSet(base)
                        for (added in claimed - base) if (canBanTarget(author, added)) out.add(added)
                        for (removed in base - claimed) if (canBanTarget(author, removed)) out.remove(removed)
                        out
                    }

                visiting.remove(edition.hashHex)
                effective[edition.hashHex] = result
                return result
            }

            val banned = HashSet<String>()
            // Candidate-then-gate, like roles and grants: an unauthorized banlist edition in the
            // middle of the chain must not orphan the authorized ones chained above it (which, on
            // a banlist, would silently resurrect every ban a later unban had cleared).
            val banHead = EditionFold.foldEntityGated(allBanlist, gate = ::banGate)
            if (banHead != null) {
                banned.addAll(effectiveList(banHead, HashSet()))
                // Ancestry is a STRUCTURAL fact, so it is walked over the full pool: an unauthorized
                // edition on the head's back-chain still supersedes what is beneath it, and walking
                // only the authorized subset would stop there and mis-read those genuine ancestors as
                // concurrent forks — un-doing the unban the chain already recorded.
                val ancestry = banlistAncestry(banHead, allBanlist)
                for (edition in authorizedBanlist) {
                    if (edition.hashHex !in ancestry) {
                        banned.addAll(effectiveList(edition, HashSet()))
                    }
                }
            }

            return AuthorityResolver(ownerLower, roles, memberRoles.toMap(), banned)
        }

        /**
         * The set of edition hashes on [head]'s back-chain (head itself plus every edition it chains
         * from via `prevHash`), among [pool]. Used to tell a superseded ancestor (already reflected by
         * the head) from a concurrent fork (a parallel ban to heal). The `add`-guarded walk also
         * terminates on any cycle.
         */
        private fun banlistAncestry(
            head: ControlEdition,
            pool: List<ControlEdition>,
        ): Set<String> {
            val byHash = pool.associateBy { it.hashHex }
            val acc = HashSet<String>()
            var cur: ControlEdition? = head
            while (cur != null && acc.add(cur.hashHex)) {
                val prev = cur.prevHash?.toHexKey()
                cur = if (prev != null) byHash[prev] else null
            }
            return acc
        }
    }
}
