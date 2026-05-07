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
package com.vitorpamplona.quartz.relay.admin

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import java.util.concurrent.ConcurrentHashMap

/**
 * Mutable, thread-safe runtime state for the NIP-86 management API.
 *
 * Each entry carries an optional reason string so list-* RPCs can echo
 * back why an admin took the action — useful for audit trails.
 *
 * Today the state is in-memory only; a process restart wipes the bans.
 * Wiring a persistent backend is a separate concern (a JSON file
 * snapshot on each mutation, or a small SQLite table) and can be
 * layered on by replacing this class behind the [DynamicBanPolicy]
 * interface.
 */
class BanStore(
    /**
     * Called after every mutation. The relay uses this to snapshot the
     * full state to disk so admin actions survive a restart. `null`
     * disables persistence (in-memory only — fine for tests).
     */
    private val onMutation: (() -> Unit)? = null,
) {
    /**
     * Pubkeys whose events the relay rejects. Compared case-insensitive
     * (lowercased on insert / lookup) so an admin pasting a hex pubkey
     * with mixed case still works. Empty-string value means "no
     * reason given" — `ConcurrentHashMap` rejects nulls.
     */
    private val bannedPubkeys = ConcurrentHashMap<HexKey, String>()

    /**
     * Pubkeys explicitly allowed. When non-empty, this acts as a
     * whitelist: events from any pubkey not on the list are rejected.
     */
    private val allowedPubkeys = ConcurrentHashMap<HexKey, String>()

    /** Event ids the relay refuses to store/replay. */
    private val bannedEventIds = ConcurrentHashMap<HexKey, String>()

    private fun reasonOrEmpty(s: String?): String = s ?: ""

    private fun nullIfEmpty(s: String): String? = s.ifEmpty { null }

    /**
     * Allowed kinds. When non-empty, events whose kind is not in the
     * list are rejected. Kind ops mutate two related sets (allow +
     * disallow) and need to look symmetric to readers, so we serialise
     * all kind reads/writes through [kindLock] rather than rely on
     * the per-set thread safety of `ConcurrentHashMap.newKeySet`.
     */
    private val allowedKinds = HashSet<Int>()

    /** Disallowed kinds. Always blocks regardless of [allowedKinds]. */
    private val disallowedKinds = HashSet<Int>()

    private val kindLock = Any()

    // -- Pubkey ban list -----------------------------------------------------

    fun banPubkey(
        pubkey: HexKey,
        reason: String? = null,
    ) {
        bannedPubkeys[pubkey.lowercase()] = reasonOrEmpty(reason)
        fireMutation()
    }

    fun unbanPubkey(pubkey: HexKey) {
        bannedPubkeys.remove(pubkey.lowercase())
        fireMutation()
    }

    fun isBanned(pubkey: HexKey): Boolean = bannedPubkeys.containsKey(pubkey.lowercase())

    fun listBannedPubkeys(): List<Pair<HexKey, String?>> = bannedPubkeys.entries.map { it.key to nullIfEmpty(it.value) }

    // -- Pubkey allow list ---------------------------------------------------

    fun allowPubkey(
        pubkey: HexKey,
        reason: String? = null,
    ) {
        allowedPubkeys[pubkey.lowercase()] = reasonOrEmpty(reason)
        fireMutation()
    }

    fun unallowPubkey(pubkey: HexKey) {
        allowedPubkeys.remove(pubkey.lowercase())
        fireMutation()
    }

    fun isAllowedPubkey(pubkey: HexKey): Boolean = allowedPubkeys.containsKey(pubkey.lowercase())

    fun listAllowedPubkeys(): List<Pair<HexKey, String?>> = allowedPubkeys.entries.map { it.key to nullIfEmpty(it.value) }

    fun hasAllowList(): Boolean = allowedPubkeys.isNotEmpty()

    // -- Event id ban list ---------------------------------------------------

    fun banEvent(
        eventId: HexKey,
        reason: String? = null,
    ) {
        bannedEventIds[eventId.lowercase()] = reasonOrEmpty(reason)
        fireMutation()
    }

    /** Removes an event id from the ban list. Mirrors NIP-86 `allowevent`. */
    fun allowEvent(eventId: HexKey) {
        bannedEventIds.remove(eventId.lowercase())
        fireMutation()
    }

    fun isBannedEvent(eventId: HexKey): Boolean = bannedEventIds.containsKey(eventId.lowercase())

    fun listBannedEvents(): List<Pair<HexKey, String?>> = bannedEventIds.entries.map { it.key to nullIfEmpty(it.value) }

    // -- Kind allow / deny --------------------------------------------------

    /**
     * `allowKind` and `disallowKind` are symmetric: each adds to its
     * own set AND removes the kind from the opposite set. Otherwise
     * an `allowKind(K)` after a `disallowKind(K)` would leave K in
     * both sets and stay blocked, surprising operators.
     */
    fun allowKind(kind: Int) {
        synchronized(kindLock) {
            disallowedKinds.remove(kind)
            allowedKinds.add(kind)
        }
        fireMutation()
    }

    fun disallowKind(kind: Int) {
        synchronized(kindLock) {
            allowedKinds.remove(kind)
            disallowedKinds.add(kind)
        }
        fireMutation()
    }

    fun listAllowedKinds(): List<Int> = synchronized(kindLock) { allowedKinds.sorted() }

    fun listDisallowedKinds(): List<Int> = synchronized(kindLock) { disallowedKinds.sorted() }

    fun isKindAllowed(kind: Int): Boolean =
        synchronized(kindLock) {
            if (kind in disallowedKinds) return false
            if (allowedKinds.isEmpty()) return true
            return kind in allowedKinds
        }

    /**
     * Bulk-load state without firing [onMutation]. Used at startup to
     * seed the in-memory state from a persisted snapshot — we don't
     * want every individual `put` to trigger another disk write. After
     * this call the store behaves exactly as if every entry had been
     * mutated through the public API.
     */
    internal fun seedFromSnapshot(
        bannedPubkeys: List<Pair<HexKey, String?>>,
        allowedPubkeys: List<Pair<HexKey, String?>>,
        bannedEvents: List<Pair<HexKey, String?>>,
        allowedKinds: List<Int>,
        disallowedKinds: List<Int>,
    ) {
        bannedPubkeys.forEach { (k, r) -> this.bannedPubkeys[k.lowercase()] = reasonOrEmpty(r) }
        allowedPubkeys.forEach { (k, r) -> this.allowedPubkeys[k.lowercase()] = reasonOrEmpty(r) }
        bannedEvents.forEach { (k, r) -> this.bannedEventIds[k.lowercase()] = reasonOrEmpty(r) }
        synchronized(kindLock) {
            this.allowedKinds.addAll(allowedKinds)
            this.disallowedKinds.addAll(disallowedKinds)
        }
    }

    private fun fireMutation() {
        onMutation?.invoke()
    }
}
