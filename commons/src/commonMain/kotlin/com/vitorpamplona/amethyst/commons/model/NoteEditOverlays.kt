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
package com.vitorpamplona.amethyst.commons.model

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.quartz.buzz.stream.StreamMessageEditEvent
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordChatEditEvent
import com.vitorpamplona.quartz.experimental.edits.TextNoteModificationEvent
import com.vitorpamplona.quartz.nip40Expiration.isExpirationBefore
import com.vitorpamplona.quartz.utils.TimeUtils

/*
 * Per-kind resolution of a message's edit overlay from its own `Note.edits`. Every edit that
 * targets a note is held there as a hard-referenced child (like a reaction), so these are cheap
 * in-memory folds — no cache scan, no LocalCache state involved, which is why they live on the
 * note rather than the cache.
 *
 * All three kinds apply ONLY edits authored by the edited note's own author: the send side gates
 * editing to your own messages, and neither the relay (Buzz) nor an encrypted-plane peer (Concord)
 * is trusted to enforce that, so a foreign-authored edit never rewrites your message.
 */

/**
 * Every kind-1010 post modification of this note, oldest first (author-only, dropping NIP-40
 * expired ones). A list because the post's EditState cycles through the original + each version.
 */
fun Note.textNoteModifications(): List<Note> {
    val noteAuthor = author ?: return emptyList()
    val now = TimeUtils.now()
    return edits
        .filter { item ->
            val e = item.event
            e is TextNoteModificationEvent && noteAuthor == item.author && !e.isExpirationBefore(now)
        }.sortedWith(compareBy({ it.createdAt() }, { it.idHex }))
}

/** The kind-40003 Buzz edit overlaying this message, or null — author-only, newest by created_at. */
fun Note.latestBuzzEdit(): Note? {
    val authorHex = author?.pubkeyHex ?: return null
    return edits
        .filter { it.event is StreamMessageEditEvent && it.author?.pubkeyHex == authorHex }
        // idHex tie-break so a same-second pair resolves identically on every client.
        .maxWithOrNull(compareBy({ it.createdAt() ?: 0L }, { it.idHex }))
}

/** The kind-3302 Concord edit overlaying this message, or null — author-only, newest by CORD-02 §4 send time. */
fun Note.latestConcordEdit(): Note? {
    val authorHex = author?.pubkeyHex ?: return null
    return edits
        .filter { it.author?.pubkeyHex == authorHex && it.event is ConcordChatEditEvent }
        .maxWithOrNull(compareBy({ (it.event as ConcordChatEditEvent).orderingMs() }, { it.idHex }))
}
