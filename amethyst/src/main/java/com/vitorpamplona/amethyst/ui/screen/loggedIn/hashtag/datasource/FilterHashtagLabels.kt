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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.hashtag.datasource

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.loaders.filterMissingEvents
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.loaders.potentialRelaysToFindEvent
import com.vitorpamplona.amethyst.service.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtagAlts
import com.vitorpamplona.quartz.nip32Labeling.LabelEvent
import com.vitorpamplona.quartz.utils.mapOfSet

/**
 * Builds the relay filters that power the NIP-32 side of the hashtag feed:
 *
 * 1. A subscription for kind 1985 label events that tag posts with this hashtag
 *    (the `l` tag under the `#t` namespace). We don't restrict authors at the relay
 *    — the feed restricts display to the user's follows locally — but we do bound it
 *    with a limit.
 * 2. Requests for the underlying posts a *followed* user has labeled but that aren't in
 *    cache yet, so they can actually render in the feed. This rotates as new label
 *    events arrive (the sub-assembler re-runs after each EOSE).
 */
fun filterHashtagLabels(
    account: Account,
    hashtag: String,
    relays: Set<NormalizedRelayUrl>,
    since: SincePerRelayMap?,
): List<RelayBasedFilter> {
    val labelValues = hashtagAlts(hashtag).sorted()

    val labelFilters =
        relays.map { relay ->
            RelayBasedFilter(
                relay = relay,
                filter =
                    Filter(
                        kinds = listOf(LabelEvent.KIND),
                        tags = mapOf("l" to labelValues),
                        limit = 200,
                        since = since?.get(relay)?.time,
                    ),
            )
        }

    val follows = account.followingKeySet()

    // Collect the posts that a followed user labeled with this hashtag but that we don't
    // have the full event for yet, and ask their likely relays for them.
    val labelNotes =
        LocalCache.notes.filterIntoSet { _, note ->
            val noteEvent = note.event
            noteEvent is LabelEvent &&
                noteEvent.pubKey in follows &&
                hashtag in noteEvent.hashtagAssociations()
        }

    val missingTargets =
        mapOfSet {
            labelNotes.forEach { labelNote ->
                (labelNote.event as LabelEvent).labeledEvents().forEach { targetId ->
                    val target = LocalCache.getNoteIfExists(targetId)
                    if (target?.event == null) {
                        val targetNote = LocalCache.getOrCreateNote(targetId)
                        potentialRelaysToFindEvent(targetNote).ifEmpty { relays }.forEach { relayUrl ->
                            add(relayUrl, targetId)
                        }
                    }
                }
            }
        }

    return labelFilters + filterMissingEvents(missingTargets)
}
