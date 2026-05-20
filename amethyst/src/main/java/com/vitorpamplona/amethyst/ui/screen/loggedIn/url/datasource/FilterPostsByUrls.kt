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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.url.datasource

import com.vitorpamplona.amethyst.service.relays.SincePerRelayMap
import com.vitorpamplona.amethyst.ui.screen.loggedIn.home.datasource.nip22Comments.CommentKinds
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.tags.references.HttpUrlFormatter
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import com.vitorpamplona.quartz.nip54Wiki.WikiNoteEvent
import com.vitorpamplona.quartz.nip73ExternalIds.urls.UrlId
import com.vitorpamplona.quartz.nip84Highlights.HighlightEvent

val PostsByUrlReferenceKinds =
    listOf(
        TextNoteEvent.KIND,
        RepostEvent.KIND,
        GenericRepostEvent.KIND,
        ChannelMessageEvent.KIND,
        LongTextNoteEvent.KIND,
        WikiNoteEvent.KIND,
        CommentEvent.KIND,
        HighlightEvent.KIND,
    )

fun filterPostsByUrls(
    url: String,
    relays: Set<NormalizedRelayUrl>,
    since: SincePerRelayMap?,
): List<RelayBasedFilter> {
    val normalizedUrl = UrlId.toScope(url)
    val referenceUrls = listOf(normalizedUrl, HttpUrlFormatter.normalize(url)).distinct()

    return relays.flatMap { relay ->
        val since = since?.get(relay)?.time
        listOf(
            RelayBasedFilter(
                relay = relay,
                filter =
                    Filter(
                        tags = mapOf("r" to referenceUrls),
                        kinds = PostsByUrlReferenceKinds,
                        limit = 200,
                        since = since,
                    ),
            ),
            RelayBasedFilter(
                relay = relay,
                filter =
                    Filter(
                        tags = mapOf("I" to listOf(normalizedUrl)),
                        kinds = CommentKinds,
                        limit = 200,
                        since = since,
                    ),
            ),
        )
    }
}
