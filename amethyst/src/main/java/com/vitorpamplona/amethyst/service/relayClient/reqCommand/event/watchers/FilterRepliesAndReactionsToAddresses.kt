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

import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.service.relays.SincePerRelayMap
import com.vitorpamplona.quartz.experimental.zapPolls.PollNoteEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip09Deletions.DeletionEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.nip56Reports.ReportEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.nip72ModCommunities.approval.CommunityPostApprovalEvent
import com.vitorpamplona.quartz.utils.mapOfSet

val RepliesAndReactiionsToAddressesKinds1 =
    listOf(
        TextNoteEvent.KIND,
        ReactionEvent.KIND,
        RepostEvent.KIND,
        GenericRepostEvent.KIND,
        ReportEvent.KIND,
        LnZapEvent.KIND,
        PollNoteEvent.KIND,
        CommunityPostApprovalEvent.KIND,
        LiveActivitiesChatMessageEvent.KIND,
    )

val DeletionKindList =
    listOf(
        DeletionEvent.KIND,
    )

val TextNoteKindList = listOf(TextNoteEvent.KIND)

fun filterRepliesAndReactionsToAddresses(
    keys: List<AddressableNote>,
    since: SincePerRelayMap?,
): List<RelayBasedFilter>? {
    if (keys.isEmpty()) return null

    val notesPerRelay =
        mapOfSet {
            keys.forEach {
                it.relayUrlsForReactions().forEach { relay ->
                    add(relay, it.address().toValue())
                }
            }
        }

    return notesPerRelay.flatMap {
        val since = since?.get(it.key)?.time
        val sortedList = it.value.sorted()
        val relay = it.key
        listOf(
            RelayBasedFilter(
                relay = relay,
                filter =
                    Filter(
                        kinds = RepliesAndReactiionsToAddressesKinds1,
                        tags = mapOf("a" to sortedList),
                        since = since,
                        // Max amount of "replies" to download on a specific event.
                        limit = 1000,
                    ),
            ),
            RelayBasedFilter(
                relay = relay,
                filter =
                    Filter(
                        kinds = DeletionKindList,
                        tags = mapOf("a" to sortedList),
                        since = since,
                        // Max amount of "replies" to download on a specific event.
                        limit = 10,
                    ),
            ),
            RelayBasedFilter(
                relay = relay,
                filter =
                    Filter(
                        kinds = TextNoteKindList,
                        tags = mapOf("q" to sortedList),
                        since = since,
                        limit = 1000,
                    ),
            ),
        )
    }
}
