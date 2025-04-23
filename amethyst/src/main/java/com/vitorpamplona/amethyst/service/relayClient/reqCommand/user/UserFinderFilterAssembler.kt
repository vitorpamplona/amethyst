/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.amethyst.service.relayClient.reqCommand.user

import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.QueryBasedSubscriptionOrchestrator
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.findMinimumEOSEsForUsers
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.groupByEOSEPresence
import com.vitorpamplona.ammolite.relays.EVENT_FINDER_TYPES
import com.vitorpamplona.ammolite.relays.NostrClient
import com.vitorpamplona.ammolite.relays.TypedFilter
import com.vitorpamplona.ammolite.relays.filters.EOSETime
import com.vitorpamplona.ammolite.relays.filters.SincePerRelayFilter
import com.vitorpamplona.quartz.experimental.relationshipStatus.RelationshipStatusEvent
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip17Dm.settings.ChatMessageRelayListEvent
import com.vitorpamplona.quartz.nip38UserStatus.StatusEvent
import com.vitorpamplona.quartz.nip56Reports.ReportEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent

// This allows multiple screen to be listening to tags, even the same tag
class UserFinderQueryState(
    val user: User,
)

class UserFinderFilterAssembler(
    client: NostrClient,
) : QueryBasedSubscriptionOrchestrator<UserFinderQueryState>(client) {
    fun createUserMetadataFilter(keys: Set<UserFinderQueryState>): List<TypedFilter>? {
        if (keys.isEmpty()) return null

        val firstTimers = keys.filter { it.user.latestMetadata == null }.map { it.user.pubkeyHex }.distinct()

        if (firstTimers.isEmpty()) return null

        return listOf(
            TypedFilter(
                types = EVENT_FINDER_TYPES,
                filter =
                    SincePerRelayFilter(
                        kinds = listOf(MetadataEvent.KIND, AdvertisedRelayListEvent.KIND),
                        authors = firstTimers,
                    ),
            ),
        )
    }

    fun createUserMetadataStatusReportFilter(keys: Set<UserFinderQueryState>): List<TypedFilter>? {
        if (keys.isEmpty()) return null

        val secondTimers = keys.filter { it.user.latestMetadata != null }.map { it.user }.toSet()

        if (secondTimers.isEmpty()) return null

        return groupByEOSEPresence(secondTimers)
            .map { group ->
                val groupIds = group.map { it.pubkeyHex }
                val minEOSEs = findMinimumEOSEsForUsers(group)

                if (groupIds.isNotEmpty()) {
                    listOf(
                        TypedFilter(
                            types = EVENT_FINDER_TYPES,
                            filter =
                                SincePerRelayFilter(
                                    kinds =
                                        listOf(
                                            MetadataEvent.KIND,
                                            StatusEvent.KIND,
                                            RelationshipStatusEvent.KIND,
                                            AdvertisedRelayListEvent.KIND,
                                            ChatMessageRelayListEvent.KIND,
                                        ),
                                    authors = groupIds,
                                    since = minEOSEs,
                                ),
                        ),
                        TypedFilter(
                            types = EVENT_FINDER_TYPES,
                            filter =
                                SincePerRelayFilter(
                                    kinds = listOf(ReportEvent.KIND),
                                    tags = mapOf("p" to groupIds),
                                    since = minEOSEs,
                                ),
                        ),
                    )
                } else {
                    listOf()
                }
            }.flatten()
    }

    val userChannel =
        requestNewSubscription { time, relayUrl ->
            forEachSubscriber {
                val eose = it.user.latestEOSEs[relayUrl]
                if (eose == null) {
                    it.user.latestEOSEs = it.user.latestEOSEs + Pair(relayUrl, EOSETime(time))
                } else {
                    eose.time = time
                }
            }
        }

    override fun updateSubscriptions(keys: Set<UserFinderQueryState>) {
        userChannel.typedFilters =
            listOfNotNull(
                createUserMetadataFilter(keys),
                createUserMetadataStatusReportFilter(keys),
            ).flatten()
                .ifEmpty { null }
    }
}
