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
package com.vitorpamplona.amethyst.model.followOrganizer

import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags

/**
 * Aggregated activity for a single followed pubkey, computed by scanning the
 * locally cached events authored by that key. The grouping algorithms below
 * operate purely on these stats so they can be unit tested without a cache.
 *
 * @param lastSeen the most recent createdAt (unix seconds) seen for this author,
 *   or null when no events authored by them are cached locally.
 */
data class FollowStats(
    val pubkey: HexKey,
    val lastSeen: Long?,
    val totalEvents: Int,
    val kindCounts: Map<Int, Int>,
    val hashtagCounts: Map<String, Int>,
)

/** A list the organizer proposes to create, with the members it would contain. */
data class ProposedGroup(
    val title: String,
    val members: List<HexKey>,
)

enum class OrganizeStrategy {
    LAST_SEEN,
    CONTENT_TYPE,
    TOPICS,
}

/**
 * Pure grouping algorithms over [FollowStats]. The titles produced here become
 * the published NIP-51 people-list names, so they are plain human-readable
 * strings rather than localized resources.
 */
object FollowGrouper {
    private const val DAY = 24L * 60L * 60L

    fun group(
        strategy: OrganizeStrategy,
        stats: Collection<FollowStats>,
        now: Long,
    ): List<ProposedGroup> =
        when (strategy) {
            OrganizeStrategy.LAST_SEEN -> byLastSeen(stats, now)
            OrganizeStrategy.CONTENT_TYPE -> byContentType(stats)
            OrganizeStrategy.TOPICS -> byTopics(stats)
        }

    // ---------------------------------------------------------------
    // Last-seen activity
    // ---------------------------------------------------------------

    private data class Bucket(
        val title: String,
        val maxAgeDays: Long?,
    )

    private val lastSeenBuckets =
        listOf(
            Bucket("Active (last 7 days)", 7),
            Bucket("Recent (last 30 days)", 30),
            Bucket("Slowing down (last 90 days)", 90),
            Bucket("Dormant (last year)", 365),
            Bucket("Silent (over a year)", null),
        )

    fun byLastSeen(
        stats: Collection<FollowStats>,
        now: Long,
    ): List<ProposedGroup> {
        val withActivity = stats.filter { it.lastSeen != null }
        val noActivity = stats.filter { it.lastSeen == null }

        val buckets = lastSeenBuckets.associateWith { mutableListOf<HexKey>() }

        for (stat in withActivity) {
            val ageDays = (now - stat.lastSeen!!) / DAY
            val bucket = lastSeenBuckets.first { it.maxAgeDays == null || ageDays <= it.maxAgeDays }
            buckets.getValue(bucket).add(stat.pubkey)
        }

        val groups =
            lastSeenBuckets.mapNotNull { bucket ->
                val members = buckets.getValue(bucket)
                if (members.isEmpty()) null else ProposedGroup(bucket.title, members)
            }

        return if (noActivity.isEmpty()) {
            groups
        } else {
            groups + ProposedGroup("No recent posts cached", noActivity.map { it.pubkey })
        }
    }

    // ---------------------------------------------------------------
    // Content type
    // ---------------------------------------------------------------

    // Ordered by priority so ties resolve deterministically toward the more
    // distinctive long-form / media content.
    private val contentCategories =
        listOf(
            "Long-form writers" to setOf(30023, 30024),
            "Video creators" to setOf(21, 22, 34235, 34236),
            "Photographers" to setOf(20),
            "Livestreamers" to setOf(30311, 1311),
            "Note posters" to setOf(1, 1111),
        )

    fun byContentType(stats: Collection<FollowStats>): List<ProposedGroup> {
        val buckets = linkedMapOf<String, MutableList<HexKey>>()
        contentCategories.forEach { buckets[it.first] = mutableListOf() }
        val other = mutableListOf<HexKey>()

        for (stat in stats) {
            val best =
                contentCategories.maxByOrNull { (_, kinds) ->
                    kinds.sumOf { stat.kindCounts[it] ?: 0 }
                }
            val bestCount = best?.second?.sumOf { stat.kindCounts[it] ?: 0 } ?: 0
            if (best != null && bestCount > 0) {
                buckets.getValue(best.first).add(stat.pubkey)
            } else {
                other.add(stat.pubkey)
            }
        }

        val groups =
            buckets.mapNotNull { (title, members) ->
                if (members.isEmpty()) null else ProposedGroup(title, members)
            }

        return if (other.isEmpty()) {
            groups
        } else {
            groups + ProposedGroup("Reposters & no cached posts", other)
        }
    }

    // ---------------------------------------------------------------
    // Topics / hashtags
    // ---------------------------------------------------------------

    /**
     * Assigns each follow to the single hashtag they use most, then keeps only
     * topics shared by at least [minGroupSize] follows (so we don't create a
     * list per one-off tag), capped at [maxGroups] of the most popular topics.
     */
    fun byTopics(
        stats: Collection<FollowStats>,
        minGroupSize: Int = 3,
        maxGroups: Int = 25,
    ): List<ProposedGroup> {
        val byTopic = linkedMapOf<String, MutableList<HexKey>>()

        for (stat in stats) {
            val topTag =
                stat.hashtagCounts.entries
                    .maxWithOrNull(compareBy({ it.value }, { it.key }))
                    ?.key ?: continue
            byTopic.getOrPut(topTag.lowercase()) { mutableListOf() }.add(stat.pubkey)
        }

        return byTopic.entries
            .filter { it.value.size >= minGroupSize }
            .sortedByDescending { it.value.size }
            .take(maxGroups)
            .map { ProposedGroup("#${it.key}", it.value) }
    }
}

/** Scans the local event store and aggregates per-author activity for a follow set. */
object FollowOrganizer {
    private class Accumulator {
        var lastSeen: Long = 0
        var total: Int = 0
        val kinds = HashMap<Int, Int>()
        val tags = HashMap<String, Int>()

        fun add(
            kind: Int,
            createdAt: Long,
            hashtags: List<String>,
        ) {
            total++
            if (createdAt > lastSeen) lastSeen = createdAt
            kinds[kind] = (kinds[kind] ?: 0) + 1
            for (tag in hashtags) {
                val key = tag.lowercase()
                tags[key] = (tags[key] ?: 0) + 1
            }
        }
    }

    /**
     * Builds a [FollowStats] for every pubkey in [followPubkeys]. Authors with no
     * cached events still get an entry (with null lastSeen) so the UI can report
     * how many follows have no local data to organize on.
     */
    fun analyze(
        followPubkeys: Set<HexKey>,
        cache: LocalCache,
    ): List<FollowStats> {
        if (followPubkeys.isEmpty()) return emptyList()

        val acc = HashMap<HexKey, Accumulator>(followPubkeys.size)

        cache.notes.forEach { _, note ->
            val event = note.event ?: return@forEach
            val author = event.pubKey
            if (author !in followPubkeys) return@forEach

            acc
                .getOrPut(author) { Accumulator() }
                .add(event.kind, event.createdAt, event.hashtags())
        }

        return followPubkeys.map { pubkey ->
            val a = acc[pubkey]
            if (a == null) {
                FollowStats(pubkey, null, 0, emptyMap(), emptyMap())
            } else {
                FollowStats(pubkey, a.lastSeen, a.total, a.kinds, a.tags)
            }
        }
    }
}
