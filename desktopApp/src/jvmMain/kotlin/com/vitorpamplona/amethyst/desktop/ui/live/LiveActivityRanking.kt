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
package com.vitorpamplona.amethyst.desktop.ui.live

import com.vitorpamplona.amethyst.commons.model.nip53LiveActivities.LiveActivitiesChannel
import com.vitorpamplona.amethyst.commons.nip53LiveActivities.LiveActivitySorting
import com.vitorpamplona.amethyst.commons.nip53LiveActivities.LiveActivitySorting.LiveActivityRank
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.HashtagTag
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.StatusTag
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Ranks + filters cached NIP-53 live-stream channels for the Desktop Discover grid and the
 * per-column live bar, delegating the ordering contract to the shared, unit-tested
 * [LiveActivitySorting] (so Desktop and Android order streams identically).
 *
 * [isOfflineNow] lets the caller feed in the online-probe verdict for a stream's `.m3u8` so a dead
 * `status=live` sinks; pass `{ false }` until the probe is wired in.
 */
object LiveActivityRanking {
    fun rankOf(
        channel: LiveActivitiesChannel,
        followSet: Set<String>,
        isOfflineNow: Boolean,
    ): LiveActivityRank {
        val info = channel.info
        if (info == null) {
            return LiveActivityRank(LiveActivitySorting.ORDER_ENDED, 0, 0, 0L, channel.address.toValue())
        }
        val participants = info.participants()
        val involved =
            buildSet {
                add(info.pubKey)
                participants.forEach { add(it.pubKey) }
            }
        val followParticipants = involved.count { it in followSet }
        val startOrCreated = info.starts() ?: info.createdAt
        return LiveActivityRank(
            statusOrder = LiveActivitySorting.statusOrder(info.status(), isOfflineNow),
            followParticipants = followParticipants,
            totalParticipants = participants.size,
            startOrCreated = startOrCreated,
            idHex = channel.address.toValue(),
        )
    }

    /** Best-first ranked list for the Discover grid (live > planned > ended). */
    fun rankForDiscover(
        channels: Collection<LiveActivitiesChannel>,
        followSet: Set<String>,
        isOfflineNow: (LiveActivitiesChannel) -> Boolean = { false },
    ): List<LiveActivitiesChannel> = LiveActivitySorting.sortDescending(channels) { rankOf(it, followSet, isOfflineNow(it)) }

    /**
     * The set of channels currently live-and-fresh within [followSet] (or globally when [followSet]
     * is null), for the per-column "live now" bar. Ranked by viewer count (`current_participants`)
     * per the design decision, newest first as a tiebreak.
     */
    fun liveNowForBar(
        channels: Collection<LiveActivitiesChannel>,
        followSet: Set<String>?,
        now: Long = TimeUtils.now(),
        isOfflineNow: (LiveActivitiesChannel) -> Boolean = { false },
    ): List<LiveActivitiesChannel> {
        val eligible =
            channels.filter { channel ->
                val info = channel.info ?: return@filter false
                val fresh = LiveActivitySorting.isLiveAndFresh(info.status(), info.createdAt, now, isOfflineNow(channel))
                fresh && (followSet == null || involvesFollow(channel, followSet))
            }
        // Rank by viewers (current_participants), then recency. MUST go through sortDescending so the
        // keys are snapshotted once — channel.info is a var swapped from relay threads, and reading it
        // lazily inside a comparator violates TimSort's contract and crashes the feed column.
        return LiveActivitySorting.sortDescending(eligible) { channel ->
            val info = channel.info
            LiveActivityRank(
                statusOrder = LiveActivitySorting.ORDER_LIVE,
                followParticipants = 0,
                totalParticipants = info?.currentParticipants() ?: 0,
                startOrCreated = info?.createdAt ?: 0L,
                idHex = channel.address.toValue(),
            )
        }
    }

    private fun involvesFollow(
        channel: LiveActivitiesChannel,
        followSet: Set<String>,
    ): Boolean {
        val info = channel.info ?: return false
        if (info.pubKey in followSet) return true
        return info.participants().any { it.pubKey in followSet }
    }

    /** Client-side search over cached streams: title, host name, and hashtags. */
    fun matchesQuery(
        channel: LiveActivitiesChannel,
        query: String,
    ): Boolean {
        if (query.isBlank()) return true
        val q = query.trim()
        val info = channel.info ?: return false
        if (info.title()?.contains(q, ignoreCase = true) == true) return true
        if (channel.creatorName()?.contains(q, ignoreCase = true) == true) return true
        if (info.summary()?.contains(q, ignoreCase = true) == true) return true
        return info.tags.any { tag -> HashtagTag.parse(tag)?.contains(q, ignoreCase = true) == true }
    }

    fun isLive(channel: LiveActivitiesChannel): Boolean = channel.info?.status() == StatusTag.STATUS.LIVE

    fun isPlanned(channel: LiveActivitiesChannel): Boolean = channel.info?.status() == StatusTag.STATUS.PLANNED

    /**
     * True only when the stream is genuinely live right now: `status=live` AND its 30311 is fresh
     * (re-published within the freshness window). Used to keep the "LIVE NOW" surfaces free of
     * ended/planned/zombie streams.
     */
    fun isLiveNow(
        channel: LiveActivitiesChannel,
        now: Long = TimeUtils.now(),
        isOfflineNow: Boolean = false,
    ): Boolean {
        val info = channel.info ?: return false
        return LiveActivitySorting.isLiveAndFresh(info.status(), info.createdAt, now, isOfflineNow)
    }
}
