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
class BanStore {
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
     * list are rejected.
     */
    private val allowedKinds = ConcurrentHashMap.newKeySet<Int>()

    /** Disallowed kinds. Always blocks regardless of [allowedKinds]. */
    private val disallowedKinds = ConcurrentHashMap.newKeySet<Int>()

    // -- Pubkey ban list -----------------------------------------------------

    fun banPubkey(
        pubkey: HexKey,
        reason: String? = null,
    ) {
        bannedPubkeys[pubkey.lowercase()] = reasonOrEmpty(reason)
    }

    fun unbanPubkey(pubkey: HexKey) {
        bannedPubkeys.remove(pubkey.lowercase())
    }

    fun isBanned(pubkey: HexKey): Boolean = bannedPubkeys.containsKey(pubkey.lowercase())

    fun listBannedPubkeys(): List<Pair<HexKey, String?>> = bannedPubkeys.entries.map { it.key to nullIfEmpty(it.value) }

    // -- Pubkey allow list ---------------------------------------------------

    fun allowPubkey(
        pubkey: HexKey,
        reason: String? = null,
    ) {
        allowedPubkeys[pubkey.lowercase()] = reasonOrEmpty(reason)
    }

    fun unallowPubkey(pubkey: HexKey) {
        allowedPubkeys.remove(pubkey.lowercase())
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
    }

    /** Removes an event id from the ban list. Mirrors NIP-86 `allowevent`. */
    fun allowEvent(eventId: HexKey) {
        bannedEventIds.remove(eventId.lowercase())
    }

    fun isBannedEvent(eventId: HexKey): Boolean = bannedEventIds.containsKey(eventId.lowercase())

    fun listBannedEvents(): List<Pair<HexKey, String?>> = bannedEventIds.entries.map { it.key to nullIfEmpty(it.value) }

    // -- Kind allow / deny --------------------------------------------------

    fun allowKind(kind: Int) {
        allowedKinds.add(kind)
    }

    fun disallowKind(kind: Int) {
        disallowedKinds.add(kind)
        // Disallowing a kind implicitly removes it from the allow list.
        allowedKinds.remove(kind)
    }

    fun listAllowedKinds(): List<Int> = allowedKinds.sorted()

    fun listDisallowedKinds(): List<Int> = disallowedKinds.sorted()

    fun isKindAllowed(kind: Int): Boolean {
        if (kind in disallowedKinds) return false
        if (allowedKinds.isEmpty()) return true
        return kind in allowedKinds
    }
}
