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

import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * Summary posts: several items gathered into one card, so the paper has more
 * than one post per story. Pure, like [ObserverEditor] — the same corpus
 * always makes the same roundups — and in the authors' own words: a roundup
 * line is a quoted first sentence, never a paraphrase.
 *
 * A roundup has to be earned. Each kind needs at least [MIN_LINES] voices, so
 * a conversation of one reply or a "topic" one person posted about twice is
 * left as the single posts it already is.
 */
object ObserverRoundups {
    /** How many conversation and topic roundups run after the top stories. */
    const val CONVERSATIONS = 2
    const val TOPICS = 3

    /** Lines per card. A roundup that needs more is a section, not a card. */
    const val LINES = 4

    /** Fewer than this many different people is not a roundup. */
    const val MIN_LINES = 2

    /** A tag needs this many authors before it is a topic rather than a coincidence. */
    const val MIN_TOPIC_AUTHORS = 3

    const val LINE_CHARS = 160

    /** "+1", "this", "🔥": a reply too short to say anything is not quoted. */
    const val MIN_REPLY_CHARS = 12

    const val UPCOMING_DAYS = 7

    const val MAX_RELEASES_PER_AUTHOR = 2

    /**
     * The stories people argued about: the trusted replies (through the same
     * lens and floor as everything else) to each story, one line per person,
     * most-discussed first. The author's own replies are their thread, not the
     * conversation, so they are left out.
     */
    fun conversations(
        stories: List<ObserverStory>,
        replies: Map<String, List<Event>>,
        names: Map<HexKey, String>,
    ): List<ObserverRoundup> =
        stories
            .distinctBy { it.event.id }
            .mapNotNull { story ->
                val address = (story.event as? AddressableEvent)?.addressTag()
                val voices =
                    (replies[story.event.id].orEmpty() + address?.let { replies[it] }.orEmpty())
                        .distinctBy { it.id }
                        .filter { it.pubKey != story.author }
                val people = voices.map { it.pubKey }.distinct().size
                if (people < MIN_LINES) return@mapNotNull null
                val lines =
                    voices
                        .distinctBy { it.pubKey }
                        .mapNotNull { reply ->
                            val text = ObserverEditor.cleanText(reply.content, names)
                            if (text.length < MIN_REPLY_CHARS) return@mapNotNull null
                            line(reply, ObserverEditor.clip(text, LINE_CHARS), names)
                        }.take(LINES)
                if (lines.size < MIN_LINES) return@mapNotNull null
                ObserverRoundup(ObserverRoundupKind.CONVERSATION, anchor = story, people = people, lines = lines)
            }.sortedWith(compareByDescending<ObserverRoundup> { it.people }.thenByDescending { it.anchor?.score ?: 0 })
            .take(CONVERSATIONS)

    /**
     * The day's topics: each trending tag several people used, with the best
     * line from each of them — best by what the web of trust did with it.
     */
    fun topics(
        trending: List<ObserverTrend>,
        stories: List<ObserverStory>,
    ): List<ObserverRoundup> =
        trending
            .filter { it.authors >= MIN_TOPIC_AUTHORS }
            .mapNotNull { trend ->
                val lines =
                    stories
                        .filter { trend.hashtag in it.event.hashtagList() && it.headline.isNotBlank() }
                        .sortedByDescending { it.score }
                        .distinctBy { it.author }
                        .take(LINES)
                        .map { ObserverRoundupLine(it.event, it.byline, ObserverEditor.clip(it.headline, LINE_CHARS)) }
                if (lines.size < MIN_LINES) return@mapNotNull null
                ObserverRoundup(ObserverRoundupKind.TOPIC, topic = trend.hashtag, people = trend.authors, lines = lines)
            }.take(TOPICS)

    /** Every stream on the air, biggest audience first. */
    fun live(stories: List<ObserverStory>): ObserverRoundup? {
        val watching = { s: ObserverStory ->
            s.details
                .filterIsInstance<ObserverDetail.Watching>()
                .firstOrNull()
                ?.count ?: 0
        }
        val lines =
            stories
                .filter { it.headline.isNotBlank() }
                .sortedByDescending(watching)
                .take(LINES)
                .map { ObserverRoundupLine(it.event, it.byline, it.headline, it.details.filterIsInstance<ObserverDetail.Watching>().firstOrNull()) }
        return roundup(ObserverRoundupKind.LIVE, stories, lines)
    }

    /**
     * Calendar listings that START in the next [UPCOMING_DAYS] days, soonest
     * first. Days are compared as civil dates, which is what an all-day
     * listing (`YYYY-MM-DD`, no zone) is; a timed one is converted the same way.
     */
    fun upcoming(
        stories: List<ObserverStory>,
        now: Long,
    ): ObserverRoundup? {
        val first = ObserverEditor.epochDay(now)
        val last = ObserverEditor.epochDay(now + UPCOMING_DAYS * 86_400L)
        val dated =
            stories.mapNotNull { story ->
                val starts = story.details.filterIsInstance<ObserverDetail.Starts>().firstOrNull() ?: return@mapNotNull null
                val day = starts.epochSeconds?.let { ObserverEditor.epochDay(it) } ?: starts.date ?: return@mapNotNull null
                if (day < first || day > last) return@mapNotNull null
                Triple(day, story, starts)
            }
        val lines =
            dated
                .sortedWith(compareBy({ it.first }, { it.third.epochSeconds ?: 0L }))
                .take(LINES)
                .map { (_, story, starts) -> ObserverRoundupLine(story.event, story.byline, story.headline, starts) }
        return roundup(ObserverRoundupKind.UPCOMING, dated.map { it.second }, lines)
    }

    /**
     * App releases and code repositories, in one card. At most two lines per
     * author: a store publishes listings on behalf of many developers, and on a
     * live edition one store account was all four lines.
     */
    fun releases(stories: List<ObserverStory>): ObserverRoundup? {
        val perAuthor = mutableMapOf<HexKey, Int>()
        val lines =
            stories
                .filter { it.headline.isNotBlank() }
                .sortedByDescending { it.score }
                .distinctBy { it.headline.lowercase() }
                .filter { story ->
                    val n = perAuthor[story.author] ?: 0
                    perAuthor[story.author] = n + 1
                    n < MAX_RELEASES_PER_AUTHOR
                }.take(LINES)
                .map { ObserverRoundupLine(it.event, it.byline, it.headline) }
        return roundup(ObserverRoundupKind.RELEASES, stories, lines)
    }

    private fun roundup(
        kind: ObserverRoundupKind,
        counted: List<ObserverStory>,
        lines: List<ObserverRoundupLine>,
    ): ObserverRoundup? {
        if (lines.size < MIN_LINES) return null
        return ObserverRoundup(kind, people = counted.map { it.author }.distinct().size, lines = lines)
    }

    private fun line(
        event: Event,
        text: String,
        names: Map<HexKey, String>,
    ) = ObserverRoundupLine(event, names[event.pubKey] ?: ObserverEditor.shortNpub(event.pubKey), text)
}
