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
package com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.muted

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.topNavFeeds.IFeedTopNavFilter
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

@Immutable
class MutedAuthorsByProxyTopNavFilter(
    val authors: Set<String>,
    val proxyRelays: Set<NormalizedRelayUrl>,
) : IFeedTopNavFilter {
    override fun matchAuthor(pubkey: HexKey) = pubkey in authors

    override fun match(noteEvent: Event): Boolean =
        if (noteEvent is LiveActivitiesEvent) {
            noteEvent.participantsIntersect(authors)
        } else {
            noteEvent.pubKey in authors
        }

    override fun toPerRelayFlow(cache: LocalCache): Flow<MutedAuthorsTopNavPerRelayFilterSet> =
        MutableStateFlow(
            MutedAuthorsTopNavPerRelayFilterSet(
                proxyRelays.associateWith { MutedAuthorsTopNavPerRelayFilter(authors) },
            ),
        )

    override fun startValue(cache: LocalCache): MutedAuthorsTopNavPerRelayFilterSet =
        MutedAuthorsTopNavPerRelayFilterSet(
            proxyRelays.associateWith { MutedAuthorsTopNavPerRelayFilter(authors) },
        )
}
