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
package com.vitorpamplona.quartz.nipCCGeocaching.comment

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nipCCGeocaching.comment.tags.GeocacheLogType
import com.vitorpamplona.quartz.nipCCGeocaching.listing.GeocacheListingEvent
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * The non-found logs of NIP-CC: did-not-find, notes and maintenance reports.
 *
 * These are not a geocaching kind at all — they are ordinary NIP-22 comments (kind 1111) rooted
 * on the geocache listing, with a `t` tag naming the log type. That is the whole point of the
 * design: a client that already renders comment threads renders a cache's history for free, and
 * the `dnf` pile-up that tells you a cache has gone missing is just a thread.
 *
 * The root and the parent are both the listing, which is what
 * [CommentEvent.replyBuilder] produces for an addressable event: `A`/`K`/`P` and `a`/`k`/`p`
 * pointing at the cache. It also adds `E`/`e` for the listing's event id, which NIP-22 permits
 * and the rest of this codebase relies on.
 */
object GeocacheLogComment {
    fun build(
        message: String,
        cache: EventHintBundle<GeocacheListingEvent>,
        type: GeocacheLogType,
        createdAt: Long = TimeUtils.now(),
        initializer: TagArrayBuilder<CommentEvent>.() -> Unit = {},
    ) = CommentEvent.replyBuilder(
        message,
        // EventHintBundle is invariant, so the listing's bundle is rebuilt as a bundle of Event.
        EventHintBundle<Event>(cache.event, cache.relay, cache.authorHomeRelay),
        createdAt,
    ) {
        geocacheLogType(type)
        initializer()
    }

    fun didNotFind(
        message: String,
        cache: EventHintBundle<GeocacheListingEvent>,
        createdAt: Long = TimeUtils.now(),
        initializer: TagArrayBuilder<CommentEvent>.() -> Unit = {},
    ) = build(message, cache, GeocacheLogType.DNF, createdAt, initializer)

    fun note(
        message: String,
        cache: EventHintBundle<GeocacheListingEvent>,
        createdAt: Long = TimeUtils.now(),
        initializer: TagArrayBuilder<CommentEvent>.() -> Unit = {},
    ) = build(message, cache, GeocacheLogType.NOTE, createdAt, initializer)

    fun needsMaintenance(
        message: String,
        cache: EventHintBundle<GeocacheListingEvent>,
        createdAt: Long = TimeUtils.now(),
        initializer: TagArrayBuilder<CommentEvent>.() -> Unit = {},
    ) = build(message, cache, GeocacheLogType.MAINTENANCE, createdAt, initializer)
}
