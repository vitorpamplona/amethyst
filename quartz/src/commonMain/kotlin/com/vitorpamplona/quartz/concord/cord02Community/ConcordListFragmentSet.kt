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
package com.vitorpamplona.quartz.concord.cord02Community

import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Thrown when a write would need fragments this client does not hold. */
class ConcordListIncompleteException(
    message: String,
) : IllegalStateException(message)

/** Thrown when a fragment would exceed the event ceiling (CORD-02 §8) and cannot be split here. */
class ConcordListTooLargeException(
    message: String,
) : IllegalStateException(message)

/**
 * The fragments of a member's Community List as this client holds them (CORD-02 §8): per index,
 * the newest copy (newest `created_at`, ties to the lowest id, as relays resolve an addressable
 * coordinate), the declared fragment count, whether the set is complete, and their union.
 *
 * "Absence is never a fact": a missing or unreadable fragment is news not yet heard. Reading an
 * incomplete set is safe — every merge is commutative and idempotent — but a **repack** (any
 * write that changes `frags`) needs the complete set, or it silently drops every membership in
 * the fragments it never read. [planWrites] enforces that, falling back to a scoped write that
 * only rewrites the fragments holding the changed memberships.
 */
class ConcordListFragmentSet private constructor(
    /** The fragment count the newest held fragment declares (ties to the larger), 0 when none is held. */
    val declared: Int,
    /** Newest readable copy per index. */
    val held: Map<Int, Held>,
    /** Newest `created_at` per index, readable or not — a write must exceed it. */
    private val createdAtFloor: Map<Int, Long>,
    /** Indices whose newest copy did not decrypt or parse. */
    val unreadable: Set<Int>,
    /** A declared count past [ConcordListFragments.MAX_DECLARED_FRAGS] was clamped. */
    private val overflow: Boolean,
) {
    class Held(
        val createdAt: Long,
        val plaintext: String,
        val fragment: ConcordListFragments.DecodedFragment,
    )

    /** One copy of a fragment as fetched; [plaintext] is null when it did not decrypt. */
    class Copy(
        val index: Int,
        val createdAt: Long,
        val id: String,
        val plaintext: String?,
    )

    /** One fragment to (re)publish. */
    class Write(
        val index: Int,
        val plaintext: String,
        val createdAt: Long,
    )

    /**
     * True when every index below [declared] is held and readable **and** no fragment we saw failed
     * to open. An unreadable copy may declare a larger count than any readable one, or be the only
     * copy of its index, so its presence always means "not the whole List" — never grounds for a
     * repack that would overwrite it. Vacuously true only when no fragment was seen at all.
     */
    val complete: Boolean = !overflow && unreadable.isEmpty() && (0 until declared).all { it in held }

    /** True when no fragment has been seen at all. */
    val isEmpty: Boolean get() = createdAtFloor.isEmpty()

    /**
     * The union of every held fragment below [declared], in the internal shape. Fragments at or
     * past [declared] are out of range — an emptied index's stale memberships stay dormant.
     * Fragment-level unknown keys belong to the List; where two fragments carry the same key the
     * lowest index wins.
     */
    val doc: JsonObject by lazy {
        held.keys
            .filter { it < declared }
            .sortedDescending()
            .fold(EMPTY_DOC) { acc, i -> ConcordListFragments.mergeDocs(acc, held.getValue(i).fragment.doc) }
    }

    /**
     * The fragments to publish so the wire holds [newDoc] (internal shape: this set's [doc] with
     * the caller's change applied — a read-modify-write, never local state alone).
     *
     * With the complete List this repacks: every fragment whose bytes change, plus an emptied
     * copy of each index a shrinking count drops (an abandoned index would come back into range
     * later). Without it, it only rewrites the held fragments that mention a changed membership
     * (or the lowest held one for a new membership), keeping the declared count.
     *
     * Every write carries a `created_at` strictly above that index's previous one.
     */
    fun planWrites(
        newDoc: JsonObject,
        now: Long,
    ): List<Write> {
        val out = ArrayList<Write>()

        fun stamp(index: Int) = maxOf(now, (createdAtFloor[index] ?: 0L) + 1)

        if (complete) {
            val packed = ConcordListFragments.pack(newDoc)
            for ((i, plaintext) in packed.withIndex()) {
                guardSize(i, plaintext)
                if (held[i]?.plaintext != plaintext) out.add(Write(i, plaintext, stamp(i)))
            }
            val empty = ConcordListFragments.emptyFragment(packed.size)
            for (i in packed.size until maxOf(declared, packed.size)) {
                if (held[i]?.plaintext != empty) out.add(Write(i, empty, stamp(i)))
            }
            return out
        }

        val changed = changedIds(newDoc)
        if (changed.isEmpty()) return out
        val inRange = held.keys.filter { it < declared }.sorted()
        if (inRange.isEmpty()) throw ConcordListIncompleteException("no readable Community List fragment is held; refusing to write")
        val targets = HashMap<Int, MutableSet<String>>()
        for (id in changed) {
            val holders = inRange.filter { id in ConcordListFragments.idsIn(held.getValue(it).fragment.doc) }
            for (i in holders.ifEmpty { listOf(inRange.first()) }) targets.getOrPut(i) { HashSet() }.add(id)
        }
        for ((i, ids) in targets.entries.sortedBy { it.key }) {
            val plaintext = ConcordListFragments.rewriteFragment(held.getValue(i).fragment.doc, newDoc, ids, declared)
            guardSize(i, plaintext)
            if (held[i]?.plaintext != plaintext) out.add(Write(i, plaintext, stamp(i)))
        }
        return out
    }

    private fun guardSize(
        index: Int,
        plaintext: String,
    ) {
        val projected = ConcordListFragments.projectedEventBytes(plaintext.encodeToByteArray().size)
        if (projected <= ConcordListFragments.EVENT_CEILING_BYTES) return
        // A write that strictly shrinks the fragment is exempt: leaving must stay possible for a
        // member already over the ceiling (CORD-02 §8).
        val previous = held[index]?.plaintext ?: throw ConcordListTooLargeException("fragment $index would be $projected bytes")
        if (plaintext.encodeToByteArray().size >= previous.encodeToByteArray().size) {
            throw ConcordListTooLargeException("fragment $index would be $projected bytes; a split needs the complete List")
        }
    }

    /** Community ids whose entry or tombstone differs between [doc] and [newDoc]. */
    private fun changedIds(newDoc: JsonObject): Set<String> {
        fun byId(
            d: JsonObject,
            key: String,
        ) = (d[key] as? JsonArray)
            .orEmpty()
            .mapNotNull { it as? JsonObject }
            .associateBy { (it["community_id"] as? JsonPrimitive)?.content.orEmpty() }

        val out = HashSet<String>()
        for (key in listOf("entries", "tombstones")) {
            val before = byId(doc, key)
            val after = byId(newDoc, key)
            for (id in before.keys + after.keys) {
                val a = before[id]?.let { ConcordListFragments.canonicalString(it) }
                val b = after[id]?.let { ConcordListFragments.canonicalString(it) }
                if (a != b && id.isNotEmpty()) out.add(id)
            }
        }
        return out
    }

    companion object {
        private val EMPTY_DOC = JsonObject(mapOf("entries" to JsonArray(emptyList()), "tombstones" to JsonArray(emptyList())))

        /** Resolves already-decrypted [copies]. Pure, for tests and callers that decrypt elsewhere. */
        fun of(copies: Collection<Copy>): ConcordListFragmentSet {
            val newest =
                copies
                    .groupBy { it.index }
                    .mapValues { (_, list) -> list.sortedWith(compareByDescending<Copy> { it.createdAt }.thenBy { it.id }).first() }
            val held = HashMap<Int, Held>()
            val unreadable = HashSet<Int>()
            for ((i, copy) in newest) {
                // Resolve the coordinate BEFORE decrypting: an unreadable head is a missing index,
                // never a cue to fall back to an older copy a write would then be based on.
                val text = copy.plaintext
                val decoded =
                    text?.let {
                        try {
                            ConcordListFragments.decodeFragment(it)
                        } catch (_: Exception) {
                            null
                        }
                    }
                if (text == null || decoded == null) unreadable.add(i) else held[i] = Held(copy.createdAt, text, decoded)
            }
            var best: Held? = null
            for (h in held.values) {
                val b = best
                if (b == null || h.createdAt > b.createdAt || (h.createdAt == b.createdAt && h.fragment.frags > b.fragment.frags)) best = h
            }
            val wireDeclared = best?.fragment?.frags?.coerceAtLeast(1) ?: 0
            return ConcordListFragmentSet(
                declared = minOf(wireDeclared, ConcordListFragments.MAX_DECLARED_FRAGS),
                held = held,
                createdAtFloor = newest.mapValues { it.value.createdAt },
                unreadable = unreadable,
                overflow = wireDeclared > ConcordListFragments.MAX_DECLARED_FRAGS,
            )
        }

        /** Decrypts [events] with [signer] and resolves them. Events at a non-canonical `d` are ignored. */
        suspend fun resolve(
            events: Collection<ConcordCommunityListFragmentEvent>,
            signer: NostrSigner,
        ): ConcordListFragmentSet = resolve(events, signer.pubKey) { it.decryptPlaintext(signer) }

        /**
         * [resolve] with a caller-supplied [decrypt], so a caller can memoize plaintext by event id
         * instead of paying a signer round trip per fragment per read. Each event id is decrypted
         * at most once per call, and only the newest copy per index is decrypted at all.
         */
        suspend fun resolve(
            events: Collection<ConcordCommunityListFragmentEvent>,
            owner: String,
            decrypt: suspend (ConcordCommunityListFragmentEvent) -> String?,
        ): ConcordListFragmentSet {
            val newestPerIndex =
                events
                    .asSequence()
                    .filter { it.pubKey == owner }
                    .mapNotNull { e -> e.index()?.let { it to e } }
                    .groupBy({ it.first }, { it.second })
                    .mapValues { (_, list) -> list.sortedWith(compareByDescending<ConcordCommunityListFragmentEvent> { it.createdAt }.thenBy { it.id }).first() }
            return of(newestPerIndex.map { (index, e) -> Copy(index, e.createdAt, e.id, decrypt(e)) })
        }

        val EMPTY: ConcordListFragmentSet = of(emptyList())
    }
}
