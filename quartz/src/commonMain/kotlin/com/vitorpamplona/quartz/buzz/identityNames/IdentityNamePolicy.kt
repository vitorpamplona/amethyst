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
package com.vitorpamplona.quartz.buzz.identityNames

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub

/**
 * One naming fact for an identity: its [pubkey], the [name] a client would show, whether it
 * [isAgent], and the [ownerPubkey] an agent declares (a verified NIP-OA owner). Keys are 64-char hex,
 * either case.
 */
data class NamingIdentity(
    val pubkey: HexKey,
    val name: String,
    val isAgent: Boolean = false,
    val ownerPubkey: HexKey? = null,
)

/** A resolved display label. [qualifier] is the key suffix, when one was added. */
data class ResolvedIdentityName(
    val name: String,
    val qualifier: String? = null,
)

/**
 * Buzz's "contextual identity names" v1: distinct display labels for the identities one view shows
 * (a channel's members, a mention picker's choices), so two identities with the same name never look
 * identical. A label only grows when a real collision exists in that context:
 *
 * 1. Within a collision, the highest-priority identity keeps its plain name: the viewer, then other
 *    people, then the viewer's own agents, then everyone else's agents.
 * 2. A colliding agent first becomes readable: `Alice’s Honey` (its owner's name) when it is not the
 *    viewer's, else `Honey (agent)` when a person shares the name.
 * 3. Whoever still collides gets a growing suffix of their npub: `Honey · 7xk2`.
 *
 * Display policy only; a label grants no authority, so keep the key for every action. Ported from
 * Buzz's `mobile/lib/shared/identity_names/identity_name_policy.dart` and checked against its
 * vendored portable fixtures (`IdentityNamePolicyTest`).
 */
object IdentityNamePolicy {
    const val VERSION = 1

    private val HEX_KEY = Regex("^[0-9a-fA-F]{64}$")

    // ECMAScript String.prototype.trim code points, as the contract requires (U+0085 is kept).
    private fun isContractWhitespace(c: Char): Boolean {
        val u = c.code
        return (u in 0x09..0x0D) ||
            u == 0x20 ||
            u == 0xA0 ||
            u == 0x1680 ||
            (u in 0x2000..0x200A) ||
            u == 0x2028 ||
            u == 0x2029 ||
            u == 0x202F ||
            u == 0x205F ||
            u == 0x3000 ||
            u == 0xFEFF
    }

    /** Trims [value] exactly as the contract specifies. */
    fun trim(value: String): String {
        var start = 0
        var end = value.length
        while (start < end && isContractWhitespace(value[start])) start++
        while (end > start && isContractWhitespace(value[end - 1])) end--
        return value.substring(start, end)
    }

    private fun requireKey(
        key: String,
        what: String,
    ) = require(HEX_KEY.matches(key)) { "$what is not a 64-character hex public key: $key" }

    private class Row(
        val key: String,
        val identity: NamingIdentity,
        val original: String,
        val priority: Int,
        val mine: Boolean,
    ) {
        var base: String = original
        var label: String = original
        var length: Int = 0
        var suffix: String? = null
    }

    /**
     * Resolves distinct labels for the [candidates] among [identities] (all of them when
     * [candidates] is null; none for an empty set), as seen by [viewer]. Facts outside the
     * candidates still serve as owner-name lookups. Throws on any invalid key.
     */
    fun resolve(
        identities: List<NamingIdentity>,
        viewer: HexKey? = null,
        candidates: Iterable<HexKey>? = null,
    ): Map<HexKey, ResolvedIdentityName> {
        identities.forEach { identity ->
            requireKey(identity.pubkey, "pubkey")
            identity.ownerPubkey?.let { requireKey(it, "ownerPubkey") }
        }
        viewer?.let { requireKey(it, "viewer") }
        val selected =
            candidates
                ?.map {
                    requireKey(it, "candidates")
                    it.lowercase()
                }?.toSet()
        val normalizedViewer = viewer?.lowercase()

        // Last fact per key: the preferred alias and the owner-lookup name.
        val preferred = LinkedHashMap<String, NamingIdentity>()
        identities.forEach { preferred[it.pubkey.lowercase()] = it }

        // One working row per (key, trimmed name); the last fact supplies metadata.
        val aliases = LinkedHashMap<Pair<String, String>, NamingIdentity>()
        identities.forEach { aliases[it.pubkey.lowercase() to trim(it.name)] = it }

        val rows =
            aliases.mapNotNull { (keyAndName, identity) ->
                val (key, name) = keyAndName
                if (selected != null && key !in selected) return@mapNotNull null
                val owner = identity.ownerPubkey?.lowercase()
                val mine = normalizedViewer != null && (key == normalizedViewer || owner == normalizedViewer)
                Row(
                    key = key,
                    identity = identity,
                    original = name,
                    mine = mine,
                    priority =
                        if (identity.isAgent) {
                            if (mine) 2 else 3
                        } else {
                            if (key == normalizedViewer) 0 else 1
                        },
                )
            }

        val npubs = HashMap<String, String>()

        fun npubFor(key: String) = npubs.getOrPut(key) { NPub.create(key) }

        while (true) {
            val groups = LinkedHashMap<String, MutableList<Row>>()
            rows.forEach { groups.getOrPut(it.label) { mutableListOf() }.add(it) }
            val collisions = groups.values.filter { group -> group.map { it.key }.toSet().size > 1 }
            if (collisions.isEmpty()) break

            for (group in collisions) {
                val best = group.minOf { it.priority }
                val winners = group.filter { it.priority == best }.map { it.key }.toSet()
                val changing = group.filter { winners.size != 1 || it.key !in winners }
                val groupHasHuman = group.any { !it.identity.isAgent }

                var qualified = false
                for (row in changing) {
                    if (!row.identity.isAgent || row.length != 0) continue
                    val ownerFact =
                        row.identity.ownerPubkey
                            ?.lowercase()
                            ?.let { preferred[it] }
                    val owner = ownerFact?.let { trim(it.name) } ?: ""
                    val readable =
                        when {
                            !row.mine && owner.isNotEmpty() -> "$owner’s ${row.original}"
                            groupHasHuman -> "${row.original} (agent)"
                            else -> row.base
                        }
                    if (readable != row.base) {
                        row.base = readable
                        row.label = readable
                        qualified = true
                    }
                }
                if (qualified) continue

                for (row in changing) {
                    row.length = if (row.length == 0) 4 else row.length + 1
                    val npub = npubFor(row.key)
                    val suffix = if (row.length <= npub.length) npub.substring(npub.length - row.length) else "$npub · ${row.length - npub.length}"
                    row.suffix = suffix
                    row.label = "${row.base} · $suffix"
                }
            }
        }

        val result = LinkedHashMap<HexKey, ResolvedIdentityName>()
        rows.forEach { row ->
            if (row.original == trim(preferred.getValue(row.key).name)) {
                result[row.key] = ResolvedIdentityName(row.label, row.suffix)
            }
        }
        return result
    }
}
