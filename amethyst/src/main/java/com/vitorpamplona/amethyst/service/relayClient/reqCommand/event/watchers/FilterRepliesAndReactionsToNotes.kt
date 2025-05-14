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
package com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.watchers

import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.ammolite.relays.EVENT_FINDER_TYPES
import com.vitorpamplona.ammolite.relays.TypedFilter
import com.vitorpamplona.ammolite.relays.filters.EOSETime
import com.vitorpamplona.ammolite.relays.filters.SincePerRelayFilter
import com.vitorpamplona.quartz.experimental.edits.TextNoteModificationEvent
import com.vitorpamplona.quartz.experimental.zapPolls.PollNoteEvent
import com.vitorpamplona.quartz.nip03Timestamp.OtsEvent
import com.vitorpamplona.quartz.nip09Deletions.DeletionEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip34Git.reply.GitReplyEvent
import com.vitorpamplona.quartz.nip35Torrents.TorrentCommentEvent
import com.vitorpamplona.quartz.nip56Reports.ReportEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.nip90Dvms.NIP90ContentDiscoveryResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.NIP90StatusEvent

fun filterRepliesAndReactionsToNotes(
    events: List<Note>,
    since: Map<String, EOSETime>?,
): List<TypedFilter>? {
    if (events.isEmpty()) return null

    val eventIds = events.mapTo(mutableSetOf<String>()) { it.idHex }.sorted()

    return listOf(
        TypedFilter(
            types = EVENT_FINDER_TYPES,
            filter =
                SincePerRelayFilter(
                    kinds =
                        listOf(
                            TextNoteEvent.KIND,
                            ReactionEvent.KIND,
                            RepostEvent.KIND,
                            GenericRepostEvent.KIND,
                            ReportEvent.KIND,
                            LnZapEvent.KIND,
                            PollNoteEvent.KIND,
                            OtsEvent.KIND,
                            TextNoteModificationEvent.KIND,
                            GitReplyEvent.KIND,
                        ),
                    tags = mapOf("e" to eventIds),
                    since = since,
                    // Max amount of "replies" to download on a specific event.
                    limit = 1000,
                ),
        ),
        TypedFilter(
            types = EVENT_FINDER_TYPES,
            filter =
                SincePerRelayFilter(
                    kinds =
                        listOf(
                            DeletionEvent.KIND,
                            NIP90ContentDiscoveryResponseEvent.KIND,
                            NIP90StatusEvent.KIND,
                            TorrentCommentEvent.KIND,
                        ),
                    tags = mapOf("e" to eventIds),
                    since = since,
                    limit = 100,
                ),
        ),
    )
}
