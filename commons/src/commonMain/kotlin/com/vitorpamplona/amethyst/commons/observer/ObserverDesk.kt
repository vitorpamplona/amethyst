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

import com.vitorpamplona.quartz.nip01Core.core.Event

/**
 * The desks a front page is made of — ported from the Nostr Observer's generator
 * (`NosFabrica/the-nostr-observer`, `nostr/Pull.kt`).
 *
 * Not "every kind the relay holds": the kinds that turned out to carry a story.
 * Each desk is asked on its own subscription so its answer arrives already
 * attributed; two desks sharing one REQ would have to be recovered by kind, and
 * a desk spanning several kinds (video) would collide with any other that
 * shares one.
 *
 * [limit] is the lens's cutoff, not pagination: `observer:` ranks the whole
 * 24-hour window and `limit` is what turns that into a top-N.
 *
 * [perAuthor] is the prune the Observer learned from real windows — one bot
 * filing its archive can own a desk without it.
 */
enum class ObserverDesk(
    val kinds: List<Int>,
    val limit: Int,
    val perAuthor: Int,
) {
    NOTES(listOf(1), 400, 20),
    PICTURES(listOf(20), 60, 8),

    /**
     * Streams on the air right now, and only those. A 30311 is replaceable and
     * carries a `status`, so a finished stream sits in the window looking
     * exactly like a running one — measured by the Observer, 7 of 18 had ended.
     */
    LIVE(listOf(30311), 30, 8) {
        override fun keeps(event: Event) = event.tagValue("status").equals("live", ignoreCase = true)
    },
    POLLS(listOf(1068), 20, 8),

    /**
     * Current and deprecated NIP-71 kinds together: the Observer measured zero
     * kind 21/22 and dozens of 34235/34236 in the same window, so the
     * deprecated kinds are where the video actually is.
     */
    VIDEOS(listOf(21, 34235), 40, 8),
    SHORTS(listOf(22, 34236), 40, 5),
    HIGHLIGHTS(listOf(9802), 50, 8),
    ARTICLES(listOf(30023), 100, 4),
    CLASSIFIEDS(listOf(30402), 30, 8),
    WIKI(listOf(30818), 30, 8),

    /** 31922 is the all-day half of NIP-52, 31923 the timed half. Reading only one drops the other silently. */
    CALENDAR(listOf(31922, 31923), 100, 6),
    APPS(listOf(32267), 30, 8),
    GIT(listOf(30617), 30, 8),
    ;

    /** A filter cannot say "and the `status` tag is live", so the relay returns both and this drops the rest. */
    open fun keeps(event: Event): Boolean = true
}

internal fun Event.tagValue(name: String): String? = tags.firstOrNull { it.size > 1 && it[0] == name }?.get(1)

internal fun Event.tagValues(name: String): List<Array<String>> = tags.filter { it.isNotEmpty() && it[0] == name }

/** Hashtags, lowercased and deduplicated — `t` tags are inconsistently cased in the wild. */
internal fun Event.hashtagList(): List<String> =
    tags
        .mapNotNull { if (it.size > 1 && it[0] == "t") it[1].trim().lowercase().removePrefix("#") else null }
        .filter { it.isNotBlank() }
        .distinct()
