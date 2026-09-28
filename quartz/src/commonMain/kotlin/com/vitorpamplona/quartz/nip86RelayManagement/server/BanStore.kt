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
package com.vitorpamplona.quartz.nip86RelayManagement.server

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip43RelayMembers.roles.RelayRole
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Lock-free runtime state for the NIP-86 management API. Holds the
 * ban/allow lists that [BanListPolicy] consults on every accept call,
 * the NIP-43 role definitions / assignments and invite codes (claims)
 * that the role and claim RPCs manage, plus an [onMutation] hook so the
 * relay can persist the latest snapshot whenever an admin RPC mutates
 * state.
 *
 * Each list entry carries an optional reason string so list-* RPCs can
 * echo back why an admin took the action — useful for audit trails.
 *
 * Ban / allow lists follow NIP-86: `ban*` removes the entry from the
 * matching allow list and `allow*` removes it from the ban list, while
 * `unban*` / `unallow*` only clear their own list — they never add the
 * entry to the opposite one. So a pubkey or event id is never on both
 * lists at once.
 *
 * Persistence is intentionally NOT inside this class; supply
 * [onMutation] to flush to disk (or wherever) and use [seedFromSnapshot]
 * at boot to load. `null` keeps the store in-memory only.
 *
 * Concurrency: state is held in a single [AtomicReference] and mutated
 * via copy-on-write CAS loops. Reads are wait-free single-load atomic.
 * The data structures are tiny (operator-controlled) so the per-write
 * map copy is negligible.
 */
@OptIn(ExperimentalAtomicApi::class)
class BanStore(
    private val onMutation: (() -> Unit)? = null,
) {
    /**
     * Single immutable snapshot of all state. Combined into one object so
     * the lock-step moves (ban removes from allow, allow removes from
     * ban, deleting a role unassigns it) are naturally atomic — no
     * interleaved reader can observe an entry on both lists.
     */
    private data class State(
        val bannedPubkeys: Map<HexKey, String?> = emptyMap(),
        val allowedPubkeys: Map<HexKey, String?> = emptyMap(),
        val bannedEventIds: Map<HexKey, String?> = emptyMap(),
        val allowedEventIds: Map<HexKey, String?> = emptyMap(),
        val allowedKinds: Set<Int> = emptySet(),
        val disallowedKinds: Set<Int> = emptySet(),
        val roles: Map<String, RelayRole> = emptyMap(),
        val memberRoles: Map<HexKey, List<String>> = emptyMap(),
        val claims: Set<String> = emptySet(),
    )

    private val state = AtomicReference(State())

    private inline fun mutate(transform: (State) -> State) {
        while (true) {
            val current = state.load()
            if (state.compareAndSet(current, transform(current))) break
        }
        onMutation?.invoke()
    }

    /**
     * Like [mutate], but [transform] may return `null` to leave the state
     * untouched (e.g. editing a role that doesn't exist). Returns whether
     * the state changed; [onMutation] only fires when it did.
     */
    private inline fun mutateIf(transform: (State) -> State?): Boolean {
        while (true) {
            val current = state.load()
            val next = transform(current) ?: return false
            if (state.compareAndSet(current, next)) break
        }
        onMutation?.invoke()
        return true
    }

    // -- Pubkey ban list -----------------------------------------------------

    /** Bans [pubkey] and, per NIP-86, drops it from the allow list. */
    fun banPubkey(
        pubkey: HexKey,
        reason: String? = null,
    ) = mutate {
        val pk = pubkey.lowercase()
        it.copy(bannedPubkeys = it.bannedPubkeys + (pk to reason), allowedPubkeys = it.allowedPubkeys - pk)
    }

    /** Lifts a ban. Does not add [pubkey] to the allow list. */
    fun unbanPubkey(pubkey: HexKey) = mutate { it.copy(bannedPubkeys = it.bannedPubkeys - pubkey.lowercase()) }

    fun isBanned(pubkey: HexKey): Boolean = pubkey.lowercase() in state.load().bannedPubkeys

    fun listBannedPubkeys(): List<Pair<HexKey, String?>> =
        state
            .load()
            .bannedPubkeys.entries
            .map { it.key to it.value }

    // -- Pubkey allow list ---------------------------------------------------

    /** Allow-lists [pubkey] and, per NIP-86, drops it from the ban list. */
    fun allowPubkey(
        pubkey: HexKey,
        reason: String? = null,
    ) = mutate {
        val pk = pubkey.lowercase()
        it.copy(allowedPubkeys = it.allowedPubkeys + (pk to reason), bannedPubkeys = it.bannedPubkeys - pk)
    }

    /** Removes [pubkey] from the allow list. Does not ban it. */
    fun unallowPubkey(pubkey: HexKey) = mutate { it.copy(allowedPubkeys = it.allowedPubkeys - pubkey.lowercase()) }

    fun isAllowedPubkey(pubkey: HexKey): Boolean = pubkey.lowercase() in state.load().allowedPubkeys

    fun listAllowedPubkeys(): List<Pair<HexKey, String?>> =
        state
            .load()
            .allowedPubkeys.entries
            .map { it.key to it.value }

    fun hasAllowList(): Boolean = state.load().allowedPubkeys.isNotEmpty()

    // -- Event id ban / allow lists -----------------------------------------

    /** Bans [eventId] and, per NIP-86, drops it from the event allow list. */
    fun banEvent(
        eventId: HexKey,
        reason: String? = null,
    ) = mutate {
        val id = eventId.lowercase()
        it.copy(bannedEventIds = it.bannedEventIds + (id to reason), allowedEventIds = it.allowedEventIds - id)
    }

    /** NIP-86 `unbanevent`: removes [eventId] from the ban list without allow-listing it. */
    fun unbanEvent(eventId: HexKey) = mutate { it.copy(bannedEventIds = it.bannedEventIds - eventId.lowercase()) }

    /**
     * NIP-86 `allowevent`: adds [eventId] to the event allow list and drops
     * it from the ban list. See [BanListPolicy] for what an allow-listed
     * event is exempt from.
     */
    fun allowEvent(
        eventId: HexKey,
        reason: String? = null,
    ) = mutate {
        val id = eventId.lowercase()
        it.copy(allowedEventIds = it.allowedEventIds + (id to reason), bannedEventIds = it.bannedEventIds - id)
    }

    /** NIP-86 `unallowevent`: removes [eventId] from the allow list without banning it. */
    fun unallowEvent(eventId: HexKey) = mutate { it.copy(allowedEventIds = it.allowedEventIds - eventId.lowercase()) }

    fun isBannedEvent(eventId: HexKey): Boolean = eventId.lowercase() in state.load().bannedEventIds

    fun isAllowedEvent(eventId: HexKey): Boolean = eventId.lowercase() in state.load().allowedEventIds

    fun listBannedEvents(): List<Pair<HexKey, String?>> =
        state
            .load()
            .bannedEventIds.entries
            .map { it.key to it.value }

    fun listAllowedEvents(): List<Pair<HexKey, String?>> =
        state
            .load()
            .allowedEventIds.entries
            .map { it.key to it.value }

    // -- Kind allow / deny --------------------------------------------------

    /**
     * `allowKind` and `disallowKind` are symmetric: each adds to its
     * own set AND removes the kind from the opposite set. Otherwise
     * an `allowKind(K)` after a `disallowKind(K)` would leave K in
     * both sets and stay blocked, surprising operators.
     */
    fun allowKind(kind: Int) =
        mutate {
            it.copy(
                allowedKinds = it.allowedKinds + kind,
                disallowedKinds = it.disallowedKinds - kind,
            )
        }

    fun disallowKind(kind: Int) =
        mutate {
            it.copy(
                allowedKinds = it.allowedKinds - kind,
                disallowedKinds = it.disallowedKinds + kind,
            )
        }

    fun listAllowedKinds(): List<Int> = state.load().allowedKinds.sorted()

    fun listDisallowedKinds(): List<Int> = state.load().disallowedKinds.sorted()

    fun isKindAllowed(kind: Int): Boolean {
        val s = state.load()
        if (kind in s.disallowedKinds) return false
        if (s.allowedKinds.isEmpty()) return true
        return kind in s.allowedKinds
    }

    // -- NIP-43 roles -------------------------------------------------------

    /** NIP-86 `createrole`. Returns false (and changes nothing) if a role with that id exists. */
    fun createRole(role: RelayRole): Boolean =
        mutateIf {
            if (role.id in it.roles) null else it.copy(roles = it.roles + (role.id to role))
        }

    /** NIP-86 `editrole`. Returns false (and changes nothing) if no role has that id. */
    fun editRole(role: RelayRole): Boolean =
        mutateIf {
            if (role.id !in it.roles) null else it.copy(roles = it.roles + (role.id to role))
        }

    /** NIP-86 `deleterole`. Also unassigns the role from every member. Idempotent. */
    fun deleteRole(roleId: String) =
        mutate { s ->
            s.copy(
                roles = s.roles - roleId,
                memberRoles =
                    s.memberRoles
                        .mapValues { (_, roles) -> roles - roleId }
                        .filterValues { it.isNotEmpty() },
            )
        }

    fun getRole(roleId: String): RelayRole? = state.load().roles[roleId]

    /** Roles sorted by their display [RelayRole.order] (unordered last), then id. */
    fun listRoles(): List<RelayRole> =
        state
            .load()
            .roles.values
            .sortedWith(compareBy<RelayRole>({ it.order ?: Int.MAX_VALUE }, { it.id }))

    /** NIP-86 `assignrole`. Returns false (and changes nothing) if the role doesn't exist. */
    fun assignRole(
        pubkey: HexKey,
        roleId: String,
    ): Boolean =
        mutateIf { s ->
            if (roleId !in s.roles) return@mutateIf null
            val pk = pubkey.lowercase()
            val current = s.memberRoles[pk].orEmpty()
            if (roleId in current) s else s.copy(memberRoles = s.memberRoles + (pk to current + roleId))
        }

    /** NIP-86 `unassignrole`. Idempotent. */
    fun unassignRole(
        pubkey: HexKey,
        roleId: String,
    ) = mutate { s ->
        val pk = pubkey.lowercase()
        val remaining = s.memberRoles[pk].orEmpty() - roleId
        s.copy(memberRoles = if (remaining.isEmpty()) s.memberRoles - pk else s.memberRoles + (pk to remaining))
    }

    fun rolesOf(pubkey: HexKey): List<String> = state.load().memberRoles[pubkey.lowercase()].orEmpty()

    /** Every pubkey with at least one role, with its role ids in assignment order. */
    fun listRoleAssignments(): List<Pair<HexKey, List<String>>> =
        state
            .load()
            .memberRoles.entries
            .map { it.key to it.value }

    // -- NIP-43 invite codes (claims) ---------------------------------------

    /** NIP-86 `createclaim`. Idempotent. */
    fun createClaim(claim: String) = mutate { it.copy(claims = it.claims + claim) }

    /** NIP-86 `deleteclaim`. Idempotent. */
    fun deleteClaim(claim: String) = mutate { it.copy(claims = it.claims - claim) }

    /** True when [claim] is an invite code the relay currently accepts. */
    fun isValidClaim(claim: String): Boolean = claim in state.load().claims

    fun listClaims(): List<String> = state.load().claims.toList()

    /**
     * Bulk-load state without firing [onMutation]. Used at startup to
     * seed the in-memory state from a persisted snapshot — we don't
     * want every individual `put` to trigger another disk write. After
     * this call the store behaves exactly as if every entry had been
     * mutated through the public API.
     *
     * A snapshot that lists an id on both a ban and an allow list (only
     * possible from a hand-edited file) keeps the ban and drops the allow.
     */
    fun seedFromSnapshot(
        bannedPubkeys: List<Pair<HexKey, String?>> = emptyList(),
        allowedPubkeys: List<Pair<HexKey, String?>> = emptyList(),
        bannedEvents: List<Pair<HexKey, String?>> = emptyList(),
        allowedKinds: List<Int> = emptyList(),
        disallowedKinds: List<Int> = emptyList(),
        allowedEvents: List<Pair<HexKey, String?>> = emptyList(),
        roles: List<RelayRole> = emptyList(),
        roleAssignments: List<Pair<HexKey, List<String>>> = emptyList(),
        claims: List<String> = emptyList(),
    ) {
        val banned = bannedPubkeys.associate { (k, r) -> k.lowercase() to r }
        val bannedEv = bannedEvents.associate { (k, r) -> k.lowercase() to r }
        val roleMap = roles.associateBy { it.id }
        state.store(
            State(
                bannedPubkeys = banned,
                allowedPubkeys = allowedPubkeys.associate { (k, r) -> k.lowercase() to r } - banned.keys,
                bannedEventIds = bannedEv,
                allowedEventIds = allowedEvents.associate { (k, r) -> k.lowercase() to r } - bannedEv.keys,
                allowedKinds = allowedKinds.toSet(),
                disallowedKinds = disallowedKinds.toSet(),
                roles = roleMap,
                memberRoles =
                    roleAssignments
                        .associate { (pk, ids) -> pk.lowercase() to ids.filter { it in roleMap }.distinct() }
                        .filterValues { it.isNotEmpty() },
                claims = claims.toSet(),
            ),
        )
    }
}
