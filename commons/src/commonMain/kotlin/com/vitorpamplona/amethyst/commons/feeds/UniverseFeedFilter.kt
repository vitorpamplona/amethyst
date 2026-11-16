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
package com.vitorpamplona.amethyst.commons.feeds

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.observables.CandidateSet
import com.vitorpamplona.amethyst.commons.model.observables.IFilter
import com.vitorpamplona.quartz.utils.Log
import kotlin.time.TimeSource

/**
 * A feed split in two parts:
 *
 * - [universe]: what the feed could ever show, decided by facts of the event itself (its type,
 *   who it tags, its author, its hashtags). It never reads mutes, the selected list, the Web of
 *   Trust or settings, so it does not change once a note is in the cache.
 * - The feed's own check, applied over the universe: everything else, which changes on its own
 *   schedule.
 *
 * A rebuild ([rebuildFeed]) takes the universe from [candidates] when the feed has a
 * [CandidateSet] (feeds whose universe is a small part of the cache: notifications, a profile, a
 * hashtag) and from a walk over the cache otherwise (feeds that take in most of it: Home). Either
 * way the result is the same; only the cost differs.
 */
interface UniverseFeedFilter {
    val universe: IFilter

    /** Where rebuilds take the universe from; null walks the cache. */
    val candidates: CandidateSet?

    /**
     * Whether [universe] reads the relays a note was seen on, which grow without a new event (a
     * relay's feed). Its candidate set then also hears about each new relay of a cached note.
     */
    val universeReadsRelays: Boolean get() = false
}

/**
 * A feed whose universe is a small part of the cache, kept as a [CandidateSet] by whoever owns the
 * feed, held weakly like the cache: a screen's `FeedViewModel` for as long as the screen lives (a
 * profile tab, a hashtag), or the account's feeds for a few rare kinds (pictures, polls, videos).
 * The owner sets [candidates]; the filter only declares [universe].
 */
interface OwnedUniverseFeedFilter : UniverseFeedFilter {
    override var candidates: CandidateSet?
}

/**
 * One rebuild of a [UniverseFeedFilter]: [keep] over the candidates once their first walk is done,
 * else [walkCache] (which applies the same check while walking). Logged at debug level under
 * `FeedRebuild`, so a long session shows what each feed costs and where it read from.
 */
inline fun rebuildFeed(
    feedName: String,
    candidates: CandidateSet?,
    crossinline keep: (Note) -> Boolean,
    walkCache: () -> Set<Note>,
): Set<Note> {
    val start = TimeSource.Monotonic.markNow()
    val universe = candidates?.snapshot()
    val result = universe?.filterTo(HashSet()) { keep(it) } ?: walkCache()
    Log.d("FeedRebuild") {
        val source = if (universe != null) "${universe.size} candidates" else "cache walk"
        "$feedName from $source: ${result.size} kept in ${start.elapsedNow().inWholeMilliseconds} ms"
    }
    return result
}

/** [rebuildFeed] for a feed that always walks (its universe is most of the cache, or one kind's slice). */
inline fun rebuildFeed(
    feedName: String,
    walkCache: () -> Set<Note>,
): Set<Note> = rebuildFeed(feedName, candidates = null, keep = { false }, walkCache)

/**
 * [rebuildFeed] for a feed whose rows are not the candidates themselves (a community's approvals
 * stand for the posts they approve): [rows] maps each candidate to the notes it puts on the feed.
 */
inline fun rebuildFeedMapping(
    feedName: String,
    candidates: CandidateSet?,
    crossinline rows: (Note) -> Collection<Note>?,
    walkCache: () -> Set<Note>,
): Set<Note> {
    val start = TimeSource.Monotonic.markNow()
    val universe = candidates?.snapshot()
    val result = universe?.let { u -> HashSet<Note>().also { set -> u.forEach { rows(it)?.let(set::addAll) } } } ?: walkCache()
    Log.d("FeedRebuild") {
        val source = if (universe != null) "${universe.size} candidates" else "cache walk"
        "$feedName from $source: ${result.size} kept in ${start.elapsedNow().inWholeMilliseconds} ms"
    }
    return result
}
