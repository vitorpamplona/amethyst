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
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Lock-free runtime state for the NIP-86 management API. Holds the
 * ban/allow lists that [BanListPolicy] consults on every accept call,
 * plus an [onMutation] hook so the relay can persist the latest
 * snapshot whenever an admin RPC mutates state.
 *
 * Each entry carries an optional reason string so list-* RPCs can echo
 * back why an admin took the action — useful for audit trails.
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
     * Single immutable snapshot of all ban/allow state. Combined into
     * one object so kind allow/disallow lock-step (allow adds to
     * allowedKinds AND removes from disallowedKinds) is naturally
     * atomic — no possibility of an interleaved reader observing a
     * kind in both sets.
     */
    private data class State(
        val bannedPubkeys: Map<HexKey, String?> = emptyMap(),
        val allowedPubkeys: Map<HexKey, String?> = emptyMap(),
        val bannedEventIds: Map<HexKey, String?> = emptyMap(),
        val allowedKinds: Set<Int> = emptySet(),
        val disallowedKinds: Set<Int> = emptySet(),
    )

    private val state = AtomicReference(State())

    private inline fun mutate(transform: (State) -> State) {
        while (true) {
            val current = state.load()
            if (state.compareAndSet(current, transform(current))) break
        }
        onMutation?.invoke()
    }

    // -- Pubkey ban list -----------------------------------------------------

    fun banPubkey(
        pubkey: HexKey,
        reason: String? = null,
    ) = mutate { it.copy(bannedPubkeys = it.bannedPubkeys + (pubkey.lowercase() to reason)) }

    fun unbanPubkey(pubkey: HexKey) = mutate { it.copy(bannedPubkeys = it.bannedPubkeys - pubkey.lowercase()) }

    fun isBanned(pubkey: HexKey): Boolean = pubkey.lowercase() in state.load().bannedPubkeys

    fun listBannedPubkeys(): List<Pair<HexKey, String?>> =
        state
            .load()
            .bannedPubkeys.entries
            .map { it.key to it.value }

    // -- Pubkey allow list ---------------------------------------------------

    fun allowPubkey(
        pubkey: HexKey,
        reason: String? = null,
    ) = mutate { it.copy(allowedPubkeys = it.allowedPubkeys + (pubkey.lowercase() to reason)) }

    fun unallowPubkey(pubkey: HexKey) = mutate { it.copy(allowedPubkeys = it.allowedPubkeys - pubkey.lowercase()) }

    fun isAllowedPubkey(pubkey: HexKey): Boolean = pubkey.lowercase() in state.load().allowedPubkeys

    fun listAllowedPubkeys(): List<Pair<HexKey, String?>> =
        state
            .load()
            .allowedPubkeys.entries
            .map { it.key to it.value }

    fun hasAllowList(): Boolean = state.load().allowedPubkeys.isNotEmpty()

    // -- Event id ban list ---------------------------------------------------

    fun banEvent(
        eventId: HexKey,
        reason: String? = null,
    ) = mutate { it.copy(bannedEventIds = it.bannedEventIds + (eventId.lowercase() to reason)) }

    /** Removes an event id from the ban list. Mirrors NIP-86 `allowevent`. */
    fun allowEvent(eventId: HexKey) = mutate { it.copy(bannedEventIds = it.bannedEventIds - eventId.lowercase()) }

    fun isBannedEvent(eventId: HexKey): Boolean = eventId.lowercase() in state.load().bannedEventIds

    fun listBannedEvents(): List<Pair<HexKey, String?>> =
        state
            .load()
            .bannedEventIds.entries
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

    /**
     * Bulk-load state without firing [onMutation]. Used at startup to
     * seed the in-memory state from a persisted snapshot — we don't
     * want every individual `put` to trigger another disk write. After
     * this call the store behaves exactly as if every entry had been
     * mutated through the public API.
     */
    fun seedFromSnapshot(
        bannedPubkeys: List<Pair<HexKey, String?>> = emptyList(),
        allowedPubkeys: List<Pair<HexKey, String?>> = emptyList(),
        bannedEvents: List<Pair<HexKey, String?>> = emptyList(),
        allowedKinds: List<Int> = emptyList(),
        disallowedKinds: List<Int> = emptyList(),
    ) {
        state.store(
            State(
                bannedPubkeys = bannedPubkeys.associate { (k, r) -> k.lowercase() to r },
                allowedPubkeys = allowedPubkeys.associate { (k, r) -> k.lowercase() to r },
                bannedEventIds = bannedEvents.associate { (k, r) -> k.lowercase() to r },
                allowedKinds = allowedKinds.toSet(),
                disallowedKinds = disallowedKinds.toSet(),
            ),
        )
    }
}
