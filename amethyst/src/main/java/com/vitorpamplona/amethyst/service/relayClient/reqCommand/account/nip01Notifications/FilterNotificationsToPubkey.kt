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
package com.vitorpamplona.amethyst.service.relayClient.reqCommand.account.nip01Notifications

import com.vitorpamplona.quartz.experimental.ephemChat.chat.EphemeralChatEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStoryPrologueEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStorySceneEvent
import com.vitorpamplona.quartz.experimental.publicMessages.PublicMessageEvent
import com.vitorpamplona.quartz.experimental.zapPolls.PollNoteEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.patch.GitPatchEvent
import com.vitorpamplona.quartz.nip34Git.reply.GitReplyEvent
import com.vitorpamplona.quartz.nip47WalletConnect.LnZapPaymentResponseEvent
import com.vitorpamplona.quartz.nip52Calendar.CalendarDateSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.CalendarRSVPEvent
import com.vitorpamplona.quartz.nip52Calendar.CalendarTimeSlotEvent
import com.vitorpamplona.quartz.nip56Reports.ReportEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.nip58Badges.BadgeAwardEvent
import com.vitorpamplona.quartz.nip84Highlights.HighlightEvent
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.nip88Polls.response.PollResponseEvent

val SummaryKinds =
    listOf(
        TextNoteEvent.KIND,
        ReactionEvent.KIND,
        RepostEvent.KIND,
        GenericRepostEvent.KIND,
        LnZapEvent.KIND,
    )

val NotificationsPerKeyKinds =
    listOf(
        ReportEvent.KIND,
        LnZapPaymentResponseEvent.KIND,
        ChannelMessageEvent.KIND,
        EphemeralChatEvent.KIND,
        BadgeAwardEvent.KIND,
        PollNoteEvent.KIND,
        PollEvent.KIND,
        PollResponseEvent.KIND,
        PublicMessageEvent.KIND,
    )

val NotificationsPerKeyKinds2 =
    listOf(
        GitReplyEvent.KIND,
        GitIssueEvent.KIND,
        GitPatchEvent.KIND,
        HighlightEvent.KIND,
        CommentEvent.KIND,
        CalendarDateSlotEvent.KIND,
        CalendarTimeSlotEvent.KIND,
        CalendarRSVPEvent.KIND,
        InteractiveStoryPrologueEvent.KIND,
        InteractiveStorySceneEvent.KIND,
    )

fun filterSummaryNotificationsToPubkey(
    relay: NormalizedRelayUrl,
    pubkey: HexKey?,
    since: Long?,
): List<RelayBasedFilter> {
    if (pubkey == null || pubkey.isEmpty()) return emptyList()

    return listOf(
        RelayBasedFilter(
            relay = relay,
            filter =
                Filter(
                    kinds = SummaryKinds,
                    tags = mapOf("p" to listOf(pubkey)),
                    limit = 2000,
                    since = since,
                ),
        ),
    )
}

fun filterNotificationsToPubkey(
    relay: NormalizedRelayUrl,
    pubkey: HexKey?,
    since: Long?,
): List<RelayBasedFilter> {
    if (pubkey == null || pubkey.isEmpty()) return emptyList()

    return listOf(
        RelayBasedFilter(
            relay = relay,
            filter =
                Filter(
                    kinds = NotificationsPerKeyKinds,
                    tags = mapOf("p" to listOf(pubkey)),
                    limit = 500,
                    since = since,
                ),
        ),
        RelayBasedFilter(
            relay = relay,
            filter =
                Filter(
                    kinds = NotificationsPerKeyKinds2,
                    tags = mapOf("p" to listOf(pubkey)),
                    limit = 500,
                    since = since,
                ),
        ),
    )
}

fun filterJustTheLatestNotificationsToPubkeyFromRandomRelays(
    relay: NormalizedRelayUrl,
    pubkey: HexKey?,
    since: Long?,
): List<RelayBasedFilter> {
    if (pubkey == null || pubkey.isEmpty()) return emptyList()

    return listOf(
        RelayBasedFilter(
            relay = relay,
            filter =
                Filter(
                    kinds = SummaryKinds,
                    tags = mapOf("p" to listOf(pubkey)),
                    limit = 20,
                    since = since,
                ),
        ),
        RelayBasedFilter(
            relay = relay,
            filter =
                Filter(
                    kinds = NotificationsPerKeyKinds,
                    tags = mapOf("p" to listOf(pubkey)),
                    limit = 20,
                    since = since,
                ),
        ),
        RelayBasedFilter(
            relay = relay,
            filter =
                Filter(
                    kinds = NotificationsPerKeyKinds2,
                    tags = mapOf("p" to listOf(pubkey)),
                    limit = 20,
                    since = since,
                ),
        ),
    )
}
