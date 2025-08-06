/**
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

import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip10Notes.BaseThreadedEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent

fun prepareETagsAsReplyTo(
    replyingTo: EventHintBundle<TextNoteEvent>? = null,
    forkingFrom: EventHintBundle<TextNoteEvent>? = null,
): List<MarkedETag> {
    if (replyingTo == null && forkingFrom == null) return emptyList()

    val forkTag = forkingFrom?.toMarkedETag(MarkedETag.MARKER.FORK)

    if (replyingTo == null) {
        return listOfNotNull(forkTag)
    }

    val rootTag = replyingTo.event.markedRoot() ?: replyingTo.event.unmarkedRoot()

    if (rootTag == null) {
        return listOfNotNull(replyingTo.toMarkedETag(MarkedETag.MARKER.ROOT), forkTag)
    }

    val replyTag = replyingTo.event.markedReply() ?: replyingTo.event.unmarkedReply()

    if (replyTag == null || replyTag.eventId == rootTag.eventId) {
        return listOfNotNull(
            rootTag,
            forkTag,
            replyingTo.toMarkedETag(MarkedETag.MARKER.REPLY),
        )
    }

    val branchTags =
        replyingTo.event.threadTags().filter { it.eventId != rootTag.eventId }.map {
            MarkedETag(it.eventId, it.relay, null, it.author)
        }

    val branch = mutableListOf<MarkedETag>()
    branch.add(rootTag)
    branch.addAll(branchTags)
    forkTag?.let { branch.add(forkTag) }
    branch.add(replyingTo.toMarkedETag(MarkedETag.MARKER.REPLY))
    return branch
}

fun <T : BaseThreadedEvent> prepareMarkedETagsAsReplyTo(replyingTo: EventHintBundle<T>): List<MarkedETag> {
    val rootTag = replyingTo.event.markedRoot() ?: replyingTo.event.unmarkedRoot()

    if (rootTag == null) {
        return listOfNotNull(replyingTo.toMarkedETag(MarkedETag.MARKER.ROOT))
    }

    val replyTag = replyingTo.event.markedReply() ?: replyingTo.event.unmarkedReply()

    if (replyTag == null || replyTag.eventId == rootTag.eventId) {
        return listOfNotNull(
            rootTag,
            replyingTo.toMarkedETag(MarkedETag.MARKER.REPLY),
        )
    }

    val branchTags =
        replyingTo.event.threadTags().filter { it.eventId != rootTag.eventId }.map {
            MarkedETag(it.eventId, it.relay, null, it.author)
        }

    val branch = mutableListOf<MarkedETag>()
    branch.add(rootTag)
    branch.addAll(branchTags)
    branch.add(replyingTo.toMarkedETag(MarkedETag.MARKER.REPLY))
    return branch
}
