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
package com.vitorpamplona.quartz.nip85TrustedAssertions.users.index

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.store.IdAndTime
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip09Deletions.DeletionRequestEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.UserAssertionEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.followerCount
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.hops
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.rank
import com.vitorpamplona.quartz.utils.Hex

/**
 * Accumulates one provider's kind 30382 cards (and the removals that retract them) and turns
 * them into a [TrustNetworkIndex] + [TrustNetworkIds] pair.
 *
 * Holds only the columns the index needs, never the events, so a cold sync of 300k cards stays
 * at a few tens of MB however the events arrive.
 *
 * Rules applied by [build], matching how providers publish:
 *  - the newest card per subject wins (addressable supersession);
 *  - a card whose rank is 0, or that has no rank, is a removal: Brainstorm publishes a
 *    rank-0 card before the kind-5 that deletes it, "so that even if the relay rejects kind 5,
 *    the score is effectively zeroed out";
 *  - a kind-5 by the provider removes the subject's card when it is not newer than the
 *    deletion (`a` tag `30382:<provider>:<subject>`), or the exact card (`e` tag);
 *  - [removeEventIds] drops cards a negentropy full check found the relay no longer has;
 *  - a card the relay was seen holding but that does not count (an older version it kept
 *    beside the newest, or a `d` tag that is not a lowercase 64-hex pubkey) stays as a
 *    tombstone, so a reconcile does not fetch it again every time.
 *
 * Not thread-safe: feed it from one coroutine (the verifier's drain does).
 */
class TrustNetworkBuilder(
    val provider: HexKey,
    initialCapacity: Int = 1024,
) {
    private var size = 0
    private var hi = LongArray(initialCapacity)
    private var lo = LongArray(initialCapacity)
    private var rank = ByteArray(initialCapacity)
    private var hops = ByteArray(initialCapacity)
    private var followers = IntArray(initialCapacity)
    private var createdAt = LongArray(initialCapacity)
    private var ids = ByteArray(32 * initialCapacity)

    /** The card came from the relay in this run, or was a tombstone: the relay holds it. */
    private var held = BooleanArray(initialCapacity)

    /** Subject → newest deletion time. */
    private val deletedSubjects = HashMap<Key, Long>()
    private val deletedIds = HashSet<HexKey>()

    /** Newest `created_at` among everything accepted so far: the next update's cursor. */
    var newestCreatedAt: Long = 0
        private set

    /** Cards accepted (before supersession and removals are applied). */
    val cardCount: Int get() = size

    /** A deletion or a removal by id was added: [build] may drop cards. */
    val hasRemovals: Boolean get() = deletedSubjects.isNotEmpty() || deletedIds.isNotEmpty()

    /**
     * Adds a verified event from the provider: a kind 30382 card or a kind 5 deletion.
     * Returns false (and ignores it) when it is neither, is signed by someone else, or names a
     * subject that is not a 64-hex pubkey. Signatures must be checked before calling this.
     */
    fun add(event: Event): Boolean {
        if (event.pubKey != provider) return false
        return when (event.kind) {
            UserAssertionEvent.KIND -> addCard(event)
            DeletionRequestEvent.KIND -> addDeletion(event)
            else -> false
        }
    }

    private fun addCard(event: Event): Boolean {
        if (!Hex.isHex64(event.id)) return false
        val eventId = Hex.decode(event.id)
        val subject = event.tags.dTag()
        if (isSubject(subject)) {
            append(
                hi = Hex.readLong(subject, 0),
                lo = Hex.readLong(subject, 16),
                rank = (event.tags.rank() ?: 0).coerceIn(0, 127).toByte(),
                hops = (event.tags.hops() ?: -1).coerceIn(-1, 127).toByte(),
                followers = (event.tags.followerCount() ?: 0).coerceAtLeast(0),
                createdAt = event.createdAt,
                id = eventId,
                idOffset = 0,
                held = true,
            )
        } else {
            // Not about a pubkey (an uppercase `d` is another address, not the pubkey's card), but
            // the relay serves it for the filter: a rank-0 card under a key of its own (its id's
            // first bits) keeps it a tombstone that can never supersede a real card.
            append(
                hi = Hex.readLong(event.id, 0),
                lo = Hex.readLong(event.id, 16),
                rank = 0,
                hops = -1,
                followers = 0,
                createdAt = event.createdAt,
                id = eventId,
                idOffset = 0,
                held = true,
            )
        }
        if (event.createdAt > newestCreatedAt) newestCreatedAt = event.createdAt
        return true
    }

    /** A card's `d` tag names a pubkey: 64 lowercase hex characters, as NIP-01 writes them. */
    private fun isSubject(subject: String?): Boolean {
        if (subject == null || !Hex.isHex64(subject)) return false
        for (c in subject) if (c in 'A'..'F') return false
        return true
    }

    private fun addDeletion(event: Event): Boolean {
        val prefix = "${UserAssertionEvent.KIND}:$provider:"
        var any = false
        for (tag in event.tags) {
            if (tag.size < 2) continue
            when (tag[0]) {
                "a" -> {
                    val value = tag[1]
                    if (!value.startsWith(prefix)) continue
                    val subject = value.substring(prefix.length)
                    if (!isSubject(subject)) continue
                    removeSubject(subject, event.createdAt)
                    any = true
                }

                "e" -> {
                    if (Hex.isHex64(tag[1])) {
                        deletedIds.add(tag[1].lowercase())
                        any = true
                    }
                }
            }
        }
        if (any && event.createdAt > newestCreatedAt) newestCreatedAt = event.createdAt
        return any
    }

    /** Removes the card about [subject] if it is not newer than [asOf]. */
    fun removeSubject(
        subject: HexKey,
        asOf: Long,
    ) {
        val key = Key(Hex.readLong(subject, 0), Hex.readLong(subject, 16))
        val previous = deletedSubjects[key]
        if (previous == null || previous < asOf) deletedSubjects[key] = asOf
    }

    /** Drops the cards with these event ids (a full check found the relay no longer has them). */
    fun removeEventIds(eventIds: Collection<HexKey>) {
        eventIds.mapTo(deletedIds) { it.lowercase() }
    }

    /** Seeds the builder with an existing index, so an update can merge into it. */
    fun addAll(
        index: TrustNetworkIndex,
        idColumn: TrustNetworkIds,
    ) {
        require(index.size == idColumn.size) { "index and ids disagree" }
        for (i in 0 until index.size) {
            append(
                hi = index.keys[2 * i],
                lo = index.keys[2 * i + 1],
                rank = index.rank[i],
                hops = index.hops[i],
                followers = index.followers[i],
                createdAt = idColumn.createdAt[i],
                id = idColumn.ids,
                idOffset = 32 * i,
                held = false,
            )
            if (idColumn.createdAt[i] > newestCreatedAt) newestCreatedAt = idColumn.createdAt[i]
        }
        // Tombstones come back as the rank-0 cards they are, so supersession still applies.
        for (i in 0 until idColumn.tombstones) {
            append(
                hi = idColumn.tombstoneKeys[2 * i],
                lo = idColumn.tombstoneKeys[2 * i + 1],
                rank = 0,
                hops = -1,
                followers = 0,
                createdAt = idColumn.tombstoneCreatedAt[i],
                id = idColumn.tombstoneIds,
                idOffset = 32 * i,
                held = true,
            )
            if (idColumn.tombstoneCreatedAt[i] > newestCreatedAt) newestCreatedAt = idColumn.tombstoneCreatedAt[i]
        }
    }

    /** (created_at, id) of every card accepted so far, for a negentropy local side. */
    fun idsAndTimes(): List<IdAndTime> =
        List(size) { i ->
            IdAndTime(createdAt[i], Hex.encode(ids.copyOfRange(32 * i, 32 * i + 32)))
        }

    private fun append(
        hi: Long,
        lo: Long,
        rank: Byte,
        hops: Byte,
        followers: Int,
        createdAt: Long,
        id: ByteArray,
        idOffset: Int,
        held: Boolean,
    ) {
        if (size == this.hi.size) grow()
        this.hi[size] = hi
        this.lo[size] = lo
        this.rank[size] = rank
        this.hops[size] = hops
        this.followers[size] = followers
        this.createdAt[size] = createdAt
        id.copyInto(ids, 32 * size, idOffset, idOffset + 32)
        this.held[size] = held
        size++
    }

    private fun grow() {
        val capacity = (hi.size * 2).coerceAtLeast(16)
        hi = hi.copyOf(capacity)
        lo = lo.copyOf(capacity)
        rank = rank.copyOf(capacity)
        hops = hops.copyOf(capacity)
        followers = followers.copyOf(capacity)
        createdAt = createdAt.copyOf(capacity)
        ids = ids.copyOf(32 * capacity)
        held = held.copyOf(capacity)
    }

    /** The first 128 bits of each id in [deletedIds], so [isDeletedId] checks a card without allocating. */
    private fun deletedIdPrefixes(): KeyTable =
        KeyTable.of(
            deletedIds.mapNotNull { id ->
                if (Hex.isHex64(id)) Key(Hex.readLong(id, 0), Hex.readLong(id, 16)) to 0L else null
            },
        )

    private fun isDeletedId(
        i: Int,
        prefixes: KeyTable,
    ): Boolean {
        if (prefixes.isEmpty() || !prefixes.contains(readLong(ids, 32 * i), readLong(ids, 32 * i + 8))) return false
        // Rare: confirm the whole id.
        return Hex.encode(ids.copyOfRange(32 * i, 32 * i + 32)) in deletedIds
    }

    /** Unsigned byte order of the ids at positions [a] and [b]. */
    private fun compareIds(
        a: Int,
        b: Int,
    ): Int {
        for (k in 0 until 32) {
            val c = (ids[32 * a + k].toInt() and 0xFF) - (ids[32 * b + k].toInt() and 0xFF)
            if (c != 0) return c
        }
        return 0
    }

    private fun readLong(
        bytes: ByteArray,
        offset: Int,
    ): Long {
        var v = 0L
        for (b in offset until offset + 8) v = (v shl 8) or (bytes[b].toLong() and 0xFF)
        return v
    }

    /**
     * Applies supersession and removals and returns the sorted index with its id column. A
     * subject whose newest card has rank 0 (or no rank), or was deleted by a kind 5, is left out
     * of the index but kept as a tombstone in the id column; a card the relay no longer has
     * ([removeEventIds]) is dropped entirely.
     */
    fun build(): Pair<TrustNetworkIndex, TrustNetworkIds> {
        // Sort positions by subject, newest card first within a subject; within the same second
        // the lowest id first, the card NIP-01 keeps (as the relay does).
        val order = IntArray(size) { it }
        mergeSort(order) { a, b ->
            val c = TrustNetworkIndex.compareKeys(hi[a], lo[a], hi[b], lo[b])
            when {
                c != 0 -> c
                createdAt[a] != createdAt[b] -> createdAt[b].compareTo(createdAt[a])
                else -> compareIds(a, b)
            }
        }

        val deletedPrefixes = deletedIdPrefixes()
        val deletedAtBySubject = KeyTable.of(deletedSubjects.map { (key, at) -> key to at })
        val duplicate = BooleanArray(size)
        val keep = IntArray(size)
        var kept = 0
        val graves = IntArray(size)
        var buried = 0
        var k = 0
        while (k < size) {
            val first = order[k]
            var next = k + 1
            while (next < size && hi[order[next]] == hi[first] && lo[order[next]] == lo[first]) next++

            // The subject's newest card the relay still has (a card removed by id falls back to
            // the one before it). A kind-5 deletion of the subject makes that card a tombstone:
            // out of the index, but remembered, so a relay that kept the card cannot bring it back.
            val deletedAt = if (deletedAtBySubject.isEmpty()) KeyTable.MISSING else deletedAtBySubject.valueOf(hi[first], lo[first])
            // The same card twice (an update re-fetching a card the index already holds): sorted
            // next to each other, so keep the first and carry over whether the relay holds it.
            // Otherwise the copy would be buried as an "older version" of itself.
            var original = order[k]
            for (g in k + 1 until next) {
                val i = order[g]
                if (createdAt[i] == createdAt[original] && compareIds(i, original) == 0) {
                    duplicate[i] = true
                    if (held[i]) held[original] = true
                } else {
                    original = i
                }
            }
            var chosen = -1
            for (g in k until next) {
                val i = order[g]
                if (duplicate[i]) continue
                if (isDeletedId(i, deletedPrefixes)) continue
                if (chosen < 0) {
                    chosen = i
                } else if (held[i]) {
                    // An older version the relay still serves (it kept both).
                    graves[buried++] = i
                }
            }
            k = next
            if (chosen < 0) continue
            val deleted = deletedAt != KeyTable.MISSING && deletedAt >= createdAt[chosen]

            if (deleted || memberRank(rank[chosen].toInt()) == null) {
                graves[buried++] = chosen
            } else {
                keep[kept++] = chosen
            }
        }

        val keys = LongArray(2 * kept)
        val outRank = ByteArray(kept)
        val outHops = ByteArray(kept)
        val outFollowers = IntArray(kept)
        val outCreatedAt = LongArray(kept)
        val outIds = ByteArray(32 * kept)
        for (j in 0 until kept) {
            val i = keep[j]
            keys[2 * j] = hi[i]
            keys[2 * j + 1] = lo[i]
            outRank[j] = rank[i]
            outHops[j] = hops[i]
            outFollowers[j] = followers[i]
            outCreatedAt[j] = createdAt[i]
            ids.copyInto(outIds, 32 * j, 32 * i, 32 * i + 32)
        }
        val graveKeys = LongArray(2 * buried)
        val graveIds = ByteArray(32 * buried)
        val graveCreatedAt = LongArray(buried)
        for (j in 0 until buried) {
            val i = graves[j]
            graveKeys[2 * j] = hi[i]
            graveKeys[2 * j + 1] = lo[i]
            graveCreatedAt[j] = createdAt[i]
            ids.copyInto(graveIds, 32 * j, 32 * i, 32 * i + 32)
        }
        return TrustNetworkIndex(keys, outRank, outHops, outFollowers) to TrustNetworkIds(outIds, outCreatedAt, graveKeys, graveIds, graveCreatedAt)
    }

    companion object {
        /** Stable merge sort of [array] by [compare], without boxing. */
        private inline fun mergeSort(
            array: IntArray,
            compare: (Int, Int) -> Int,
        ) {
            if (array.size < 2) return
            var src = array
            var dst = IntArray(array.size)
            var width = 1
            while (width < array.size) {
                var left = 0
                while (left < array.size) {
                    val mid = minOf(left + width, array.size)
                    val right = minOf(left + 2 * width, array.size)
                    var i = left
                    var j = mid
                    var out = left
                    while (i < mid && j < right) {
                        dst[out++] = if (compare(src[i], src[j]) <= 0) src[i++] else src[j++]
                    }
                    while (i < mid) dst[out++] = src[i++]
                    while (j < right) dst[out++] = src[j++]
                    left += 2 * width
                }
                val tmp = src
                src = dst
                dst = tmp
                width *= 2
            }
            if (src !== array) src.copyInto(array)
        }
    }
}

/** A subject or id prefix: the first 128 bits. */
private data class Key(
    val hi: Long,
    val lo: Long,
)

/**
 * (hi, lo) keys sorted unsigned, each with a value: membership and lookups by binary search,
 * with no allocation per probe (a `Map<Key, Long>` boxes a key on every lookup).
 */
private class KeyTable(
    private val hi: LongArray,
    private val lo: LongArray,
    private val values: LongArray,
) {
    fun isEmpty() = hi.isEmpty()

    fun contains(
        h: Long,
        l: Long,
    ) = find(h, l) >= 0

    /** The value for (h, l), or [MISSING]. */
    fun valueOf(
        h: Long,
        l: Long,
    ): Long {
        val i = find(h, l)
        return if (i < 0) MISSING else values[i]
    }

    private fun find(
        h: Long,
        l: Long,
    ): Int {
        var low = 0
        var high = hi.size - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            val c = TrustNetworkIndex.compareKeys(hi[mid], lo[mid], h, l)
            when {
                c < 0 -> low = mid + 1
                c > 0 -> high = mid - 1
                else -> return mid
            }
        }
        return -1
    }

    companion object {
        const val MISSING = Long.MIN_VALUE

        fun of(entries: List<Pair<Key, Long>>): KeyTable {
            val sorted = entries.sortedWith { a, b -> TrustNetworkIndex.compareKeys(a.first.hi, a.first.lo, b.first.hi, b.first.lo) }
            return KeyTable(
                LongArray(sorted.size) { sorted[it].first.hi },
                LongArray(sorted.size) { sorted[it].first.lo },
                LongArray(sorted.size) { sorted[it].second },
            )
        }
    }
}
