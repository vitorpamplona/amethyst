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
package com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.community

import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User


import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.topNavFeeds.IFeedTopNavFilter
import com.vitorpamplona.amethyst.model.topNavFeeds.OutboxRelayLoader
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.tags.aTag.isTaggedAddressableNote
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

@Immutable
class SingleCommunityTopNavFilter(
    val community: String,
    val authors: Set<String>?,
    val relays: Set<NormalizedRelayUrl>,
    val blockedRelays: StateFlow<Set<NormalizedRelayUrl>>,
) : IFeedTopNavFilter {
    override fun matchAuthor(pubkey: HexKey) = authors == null || pubkey in authors

    override fun match(noteEvent: Event): Boolean =
        if (noteEvent is LiveActivitiesEvent) {
            (authors != null && noteEvent.participantsIntersect(authors)) || noteEvent.isTaggedAddressableNote(community)
        } else if (noteEvent is CommentEvent) {
            (authors != null && noteEvent.pubKey in authors) || noteEvent.isTaggedAddressableNote(community)
        } else {
            (authors != null && noteEvent.pubKey in authors) || noteEvent.isTaggedAddressableNote(community)
        }

    override fun toPerRelayFlow(cache: LocalCache): Flow<SingleCommunityTopNavPerRelayFilterSet> {
        // relay field takes priority
        if (relays.isNotEmpty()) {
            return blockedRelays.map { blocked ->
                SingleCommunityTopNavPerRelayFilterSet(
                    relays.minus(blocked).associateWith {
                        SingleCommunityTopNavPerRelayFilter(community, authors)
                    },
                )
            }
        }

        if (authors != null) {
            // go by authors
            val authorsPerRelay = OutboxRelayLoader().toAuthorsPerRelayFlow(authors, cache) { it }

            return combine(authorsPerRelay, blockedRelays) { authorsPerRelay, blocked ->
                SingleCommunityTopNavPerRelayFilterSet(
                    authorsPerRelay.minus(blocked).mapValues {
                        SingleCommunityTopNavPerRelayFilter(community, it.value)
                    },
                )
            }
        }

        // go by hints
        return blockedRelays.map { blocked ->
            SingleCommunityTopNavPerRelayFilterSet(
                cache.relayHints.hintsForAddress(community).minus(blocked).associateWith {
                    SingleCommunityTopNavPerRelayFilter(community, authors)
                },
            )
        }
    }

    override fun startValue(cache: LocalCache): SingleCommunityTopNavPerRelayFilterSet {
        // relay field takes priority
        if (relays.isNotEmpty()) {
            return SingleCommunityTopNavPerRelayFilterSet(
                relays.minus(blockedRelays.value).associateWith {
                    SingleCommunityTopNavPerRelayFilter(community, authors)
                },
            )
        }

        if (authors != null) {
            // go by authors
            val authorsPerRelay = OutboxRelayLoader().authorsPerRelaySnapshot(authors, cache) { it }

            return SingleCommunityTopNavPerRelayFilterSet(
                authorsPerRelay.minus(blockedRelays.value).mapValues {
                    SingleCommunityTopNavPerRelayFilter(community, it.value)
                },
            )
        }

        // go by hints
        return SingleCommunityTopNavPerRelayFilterSet(
            cache.relayHints.hintsForAddress(community).minus(blockedRelays.value).associateWith {
                SingleCommunityTopNavPerRelayFilter(community, authors)
            },
        )
    }
}
