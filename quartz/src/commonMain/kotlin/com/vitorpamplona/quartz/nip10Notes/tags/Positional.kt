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
package com.vitorpamplona.quartz.nip10Notes.tags

import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.events.GenericETag

/**
 * Returns a list of NIP-10 marked tags that are also ordered at best effort to support the
 * deprecated method of positional tags to maximize backwards compatibility with clients that
 * support replies but have not been updated to understand tag markers.
 *
 * https://github.com/nostr-protocol/nips/blob/master/10.md
 *
 * The tag to the root of the reply chain goes first. The tag to the reply event being responded
 * to goes last. The order for any other tag does not matter, so keep the relative order.
 */
fun List<ETag>.positionalMarkedTags(
    root: ETag?,
    replyingTo: ETag?,
    forkedFrom: ETag?,
): List<GenericETag> =
    sortedWith { o1, o2 ->
        when {
            o1.eventId == o2.eventId -> 0
            o1.eventId == root?.eventId -> -1 // root goes first
            o2.eventId == root?.eventId -> 1 // root goes first
            o1.eventId == replyingTo?.eventId -> 1 // reply event being responded to goes last
            o2.eventId == replyingTo?.eventId -> -1 // reply event being responded to goes last
            else -> 0 // keep the relative order for any other tag
        }
    }.map {
        when (it.eventId) {
            root?.eventId -> MarkedETag(it.eventId, it.relay, MarkedETag.MARKER.ROOT, it.author)
            replyingTo?.eventId -> MarkedETag(it.eventId, it.relay, MarkedETag.MARKER.REPLY, it.author)
            forkedFrom?.eventId -> MarkedETag(it.eventId, it.relay, MarkedETag.MARKER.FORK, it.author)
            else -> it
        }
    }
