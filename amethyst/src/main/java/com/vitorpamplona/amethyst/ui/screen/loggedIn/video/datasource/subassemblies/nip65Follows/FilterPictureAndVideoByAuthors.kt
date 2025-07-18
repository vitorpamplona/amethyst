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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.video.datasource.subassemblies.nip65Follows

import com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.author.AuthorsByOutboxTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.muted.MutedAuthorsByOutboxTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.service.relays.SincePerRelayMap
import com.vitorpamplona.amethyst.ui.screen.loggedIn.video.datasource.LegacyMimeTypeMap
import com.vitorpamplona.amethyst.ui.screen.loggedIn.video.datasource.PictureAndVideoKinds
import com.vitorpamplona.amethyst.ui.screen.loggedIn.video.datasource.PictureAndVideoLegacyKinds
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

fun filterPictureAndVideoAuthors(
    relay: NormalizedRelayUrl,
    authors: Set<HexKey>,
    since: Long? = null,
): List<RelayBasedFilter> {
    val authorList = authors.sorted()
    return listOf(
        RelayBasedFilter(
            relay = relay,
            filter =
                Filter(
                    authors = authorList,
                    kinds = PictureAndVideoKinds,
                    limit = 100,
                    since = since,
                ),
        ),
        RelayBasedFilter(
            relay = relay,
            filter =
                Filter(
                    authors = authorList,
                    kinds = PictureAndVideoLegacyKinds,
                    tags = LegacyMimeTypeMap,
                    limit = 100,
                    since = since,
                ),
        ),
    )
}

fun filterPictureAndVideoByAuthors(
    authorSet: AuthorsByOutboxTopNavPerRelayFilterSet,
    since: SincePerRelayMap?,
): List<RelayBasedFilter> {
    if (authorSet.set.isEmpty()) return emptyList()

    return authorSet.set
        .mapNotNull {
            if (it.value.authors.isEmpty()) {
                null
            } else {
                filterPictureAndVideoAuthors(
                    relay = it.key,
                    authors = it.value.authors,
                    since = since?.get(it.key)?.time,
                )
            }
        }.flatten()
}

fun filterPictureAndVideoByAuthors(
    authorSet: MutedAuthorsByOutboxTopNavPerRelayFilterSet,
    since: SincePerRelayMap?,
): List<RelayBasedFilter> {
    if (authorSet.set.isEmpty()) return emptyList()

    return authorSet.set
        .mapNotNull {
            if (it.value.authors.isEmpty()) {
                null
            } else {
                filterPictureAndVideoAuthors(
                    relay = it.key,
                    authors = it.value.authors,
                    since = since?.get(it.key)?.time,
                )
            }
        }.flatten()
}
