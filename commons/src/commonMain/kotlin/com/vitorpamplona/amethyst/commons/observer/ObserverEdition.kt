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
package com.vitorpamplona.amethyst.commons.observer

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * One day's paper, laid out on the device from what the reader's lens surfaced.
 *
 * Everything a reader sees is plain text taken from the source events — the
 * headline is the author's own first sentence, never a summary somebody wrote
 * for them — so there is nothing here to sanitize and nothing that can be
 * invented. Section names are fixed ids the UI localizes.
 */
@Immutable
data class ObserverEdition(
    val reader: HexKey,
    val readerName: String?,
    val since: Long,
    val until: Long,
    /** A short print-run code: who it was read for, the window, and every event id that came back. */
    val code: String,
    val stats: ObserverStats,
    val lead: ObserverStory?,
    val topStories: List<ObserverStory>,
    val sections: List<ObserverSection>,
    val trending: List<ObserverTrend>,
) {
    val isEmpty: Boolean get() = lead == null && topStories.isEmpty() && sections.isEmpty()
}

@Immutable
data class ObserverStats(
    /** Stories printed on the page, after pruning. */
    val printed: Int,
    /** Distinct authors among them. */
    val authors: Int,
    /** Notes the lens surfaced above the trust floor in the window, or null when the relay would not count. */
    val dayNotes: Long?,
    /** Reactions, reposts and replies from the reader's web of trust used to rank the page. */
    val signals: Int,
)

@Immutable
data class ObserverTrend(
    val hashtag: String,
    val authors: Int,
)

enum class ObserverSectionKind {
    PHOTOS,
    LONG_READS,
    LIVE,
    VIDEO,
    HIGHLIGHTS,
    POLLS,
    CALENDAR,
    CLASSIFIEDS,
    APPS,
    CODE,
    WIKI,
    WIRE,
}

@Immutable
data class ObserverSection(
    val kind: ObserverSectionKind,
    val stories: List<ObserverStory>,
)

@Immutable
data class ObserverStory(
    /** The source event. Tapping a story opens it, so the paper is a front page onto the real thread. */
    val event: Event,
    val desk: ObserverDesk,
    val byline: String,
    val headline: String,
    val body: String,
    val imageUrl: String?,
    val reactions: Int,
    val reposts: Int,
    val replies: Int,
    val details: List<ObserverDetail>,
) {
    val author: HexKey get() = event.pubKey
    val createdAt: Long get() = event.createdAt
    val score: Int get() = ObserverEngagement.score(reactions, reposts, replies)
}

/** Typed facts a story carries beyond its text. The UI owns their wording. */
@Immutable
sealed interface ObserverDetail {
    data class Price(
        val amount: String,
        val currency: String?,
        val frequency: String?,
    ) : ObserverDetail

    /** [epochSeconds] for a timed event, [date] (`YYYY-MM-DD`) for an all-day one. */
    data class Starts(
        val epochSeconds: Long?,
        val date: String?,
        val zoneId: String?,
    ) : ObserverDetail

    data class Location(
        val text: String,
    ) : ObserverDetail

    data class Duration(
        val seconds: Int,
    ) : ObserverDetail

    data class PollOptions(
        val options: List<String>,
    ) : ObserverDetail

    /** A highlight is somebody ELSE's sentence; this names who wrote it, when the highlight says. */
    data class QuotedAuthor(
        val name: String,
    ) : ObserverDetail

    data class Source(
        val text: String,
    ) : ObserverDetail

    data class Watching(
        val count: Int,
    ) : ObserverDetail

    data class ItemStatus(
        val text: String,
    ) : ObserverDetail
}

/** What the reader's web of trust did with an event in the window. */
@Immutable
data class ObserverEngagement(
    val reactions: Int = 0,
    val reposts: Int = 0,
    val replies: Int = 0,
) {
    val score: Int get() = score(reactions, reposts, replies)

    companion object {
        val NONE = ObserverEngagement()

        /** A reply costs more than a like, and a repost puts your name behind it. */
        fun score(
            reactions: Int,
            reposts: Int,
            replies: Int,
        ) = reactions + 2 * reposts + 3 * replies
    }
}

/** Everything one edition is laid out from. */
class ObserverCorpus(
    val reader: HexKey,
    val since: Long,
    val until: Long,
    /** Chosen by the lens, one list per desk, reader's own events already removed. */
    val ranked: Map<ObserverDesk, List<Event>>,
    /** Keyed by event id, and by `kind:pubkey:d` address for addressable events. */
    val engagement: Map<String, ObserverEngagement>,
    /** Display names from kind 0, by pubkey. */
    val names: Map<HexKey, String>,
    val dayNotes: Long?,
) {
    fun all(): List<Event> = ranked.values.flatten()
}
