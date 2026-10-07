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
import com.vitorpamplona.quartz.utils.Hex
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrustNetworkIndexTest {
    private val random = Random(42)
    private val provider = hex()
    private val sig = "0".repeat(128)

    private fun hex(): String = Hex.encode(random.nextBytes(32))

    private fun card(
        subject: String,
        rank: Int?,
        createdAt: Long = 1000,
        author: String = provider,
        hops: Int? = 2,
        followers: Int? = 10,
    ): Event {
        val tags =
            buildList {
                add(arrayOf("d", subject))
                rank?.let { add(arrayOf("rank", it.toString())) }
                hops?.let { add(arrayOf("hops", it.toString())) }
                followers?.let { add(arrayOf("followers", it.toString())) }
            }.toTypedArray()
        return Event(hex(), author, createdAt, 30382, tags, "", sig)
    }

    private fun deletion(
        createdAt: Long,
        vararg subjects: String,
    ): Event {
        val tags = subjects.map { arrayOf("a", "30382:$provider:$it") }.toTypedArray()
        return Event(hex(), provider, createdAt, 5, tags, "", sig)
    }

    @Test
    fun findsEverySubjectAndRejectsStrangers() {
        val subjects = List(2000) { hex() }
        val builder = TrustNetworkBuilder(provider, initialCapacity = 16)
        subjects.forEachIndexed { i, s -> assertTrue(builder.add(card(s, rank = 1 + i % 100))) }
        val (index, ids) = builder.build()

        assertEquals(2000, index.size)
        assertEquals(2000, ids.size)
        subjects.forEachIndexed { i, s -> assertEquals(1 + i % 100, index.rankOf(s)) }
        repeat(2000) { assertNull(index.rankOf(hex())) }
        assertNull(index.rankOf("not hex"))
        assertNull(index.rankOf(""))
    }

    @Test
    fun keysAreSortedUnsigned() {
        val builder = TrustNetworkBuilder(provider)
        // top bit set, so a signed comparison would put this first
        val high = "f" + "0".repeat(63)
        val low = "0".repeat(63) + "1"
        builder.add(card(high, rank = 5))
        builder.add(card(low, rank = 6))
        val (index, _) = builder.build()
        assertEquals(6, index.rank[0].toInt())
        assertEquals(5, index.rankOf(high))
        assertEquals(6, index.rankOf(low))
    }

    @Test
    fun newestCardWins() {
        val s = hex()
        val builder = TrustNetworkBuilder(provider)
        builder.add(card(s, rank = 40, createdAt = 2000))
        builder.add(card(s, rank = 10, createdAt = 1000))
        val (index, ids) = builder.build()
        assertEquals(1, index.size)
        assertEquals(40, index.rankOf(s))
        assertEquals(2000, ids.createdAt[0])
    }

    @Test
    fun rankZeroAndMissingRankAreRemovals() {
        val s = hex()
        val t = hex()
        val builder = TrustNetworkBuilder(provider)
        builder.add(card(s, rank = 40, createdAt = 1000))
        builder.add(card(s, rank = 0, createdAt = 2000))
        builder.add(card(t, rank = null))
        val (index, _) = builder.build()
        assertFalse(s in index)
        assertFalse(t in index)
        assertEquals(0, index.size)
    }

    @Test
    fun deletionRemovesOnlyOlderCards() {
        val gone = hex()
        val back = hex()
        val builder = TrustNetworkBuilder(provider)
        builder.add(card(gone, rank = 40, createdAt = 1000))
        builder.add(card(back, rank = 40, createdAt = 3000))
        assertTrue(builder.add(deletion(2000, gone, back)))
        val (index, _) = builder.build()
        assertFalse(gone in index)
        assertTrue(back in index)
        assertEquals(2000, builder.newestCreatedAt.coerceAtMost(2000))
    }

    @Test
    fun ignoresOtherAuthorsKindsAndBadSubjects() {
        val builder = TrustNetworkBuilder(provider)
        assertFalse(builder.add(card(hex(), rank = 50, author = hex())))
        assertFalse(builder.add(card("abc", rank = 50)))
        assertFalse(builder.add(Event(hex(), provider, 1, 1, emptyArray(), "", sig)))
        assertEquals(0, builder.build().first.size)
    }

    @Test
    fun updateMergesIntoAnExistingIndex() {
        val keep = hex()
        val change = hex()
        val drop = hex()
        val first = TrustNetworkBuilder(provider)
        first.add(card(keep, rank = 10, createdAt = 1000))
        first.add(card(change, rank = 10, createdAt = 1000))
        first.add(card(drop, rank = 10, createdAt = 1000))
        val (index, ids) = first.build()

        val update = TrustNetworkBuilder(provider)
        update.addAll(index, ids)
        update.add(card(change, rank = 70, createdAt = 5000))
        update.add(deletion(5000, drop))
        val added = hex()
        update.add(card(added, rank = 3, createdAt = 5000))
        val (merged, mergedIds) = update.build()

        assertEquals(10, merged.rankOf(keep))
        assertEquals(70, merged.rankOf(change))
        assertNull(merged.rankOf(drop))
        assertEquals(3, merged.rankOf(added))
        assertEquals(3, mergedIds.size)
        assertEquals(5000, update.newestCreatedAt)
    }

    @Test
    fun removeEventIdsDropsExactCards() {
        val a = hex()
        val b = hex()
        val cardA = card(a, rank = 10)
        val builder = TrustNetworkBuilder(provider)
        builder.add(cardA)
        builder.add(card(b, rank = 10))
        builder.removeEventIds(listOf(cardA.id))
        val (index, _) = builder.build()
        assertFalse(a in index)
        assertTrue(b in index)
    }

    @Test
    fun countAtLeastMatchesAScan() {
        val builder = TrustNetworkBuilder(provider)
        repeat(500) { builder.add(card(hex(), rank = 1 + random.nextInt(100))) }
        val (index, _) = builder.build()
        for (min in listOf(0, 1, 5, 50, 100, 101)) {
            assertEquals(index.rank.count { it >= min }, index.countAtLeast(min))
        }
    }

    @Test
    fun indexAndIdsRoundTrip() {
        val builder = TrustNetworkBuilder(provider)
        val subjects = List(300) { hex() }
        subjects.forEach { builder.add(card(it, rank = 1 + random.nextInt(100), hops = random.nextInt(7), followers = random.nextInt(100000))) }
        val (index, ids) = builder.build()
        val header = TrustNetworkHeader(provider, "wss://scores.brainstorm.world/", syncCursor = 123, lastFullCheck = 456, lastUpdate = 789)

        val decoded = assertNotNull(TrustNetworkCodec.decodeIndex(TrustNetworkCodec.encodeIndex(header, index)))
        assertEquals(header, decoded.first)
        assertTrue(index.keys.contentEquals(decoded.second.keys))
        assertTrue(index.rank.contentEquals(decoded.second.rank))
        assertTrue(index.hops.contentEquals(decoded.second.hops))
        assertTrue(index.followers.contentEquals(decoded.second.followers))
        subjects.forEach { assertEquals(index.rankOf(it), decoded.second.rankOf(it)) }

        val decodedIds = assertNotNull(TrustNetworkCodec.decodeIds(TrustNetworkCodec.encodeIds(ids)))
        assertTrue(ids.ids.contentEquals(decodedIds.ids))
        assertTrue(ids.createdAt.contentEquals(decodedIds.createdAt))
        assertEquals(header, TrustNetworkCodec.decodeHeader(TrustNetworkCodec.encodeIndex(header, index)))
    }

    @Test
    fun truncatedOrForeignFilesAreRejected() {
        val builder = TrustNetworkBuilder(provider)
        repeat(10) { builder.add(card(hex(), rank = 9)) }
        val (index, ids) = builder.build()
        val bytes = TrustNetworkCodec.encodeIndex(TrustNetworkHeader(provider, "wss://r/", 1, 2, 3), index)
        assertNull(TrustNetworkCodec.decodeIndex(bytes.copyOf(bytes.size - 1)))
        assertNull(TrustNetworkCodec.decodeIndex(ByteArray(0)))
        assertNull(TrustNetworkCodec.decodeIds(bytes))
        val idBytes = TrustNetworkCodec.encodeIds(ids)
        assertNull(TrustNetworkCodec.decodeIndex(idBytes))
        assertNull(TrustNetworkCodec.decodeIds(idBytes.copyOf(idBytes.size - 8)))
    }
}
