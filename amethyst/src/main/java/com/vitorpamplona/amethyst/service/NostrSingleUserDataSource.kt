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
package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.ammolite.relays.EVENT_FINDER_TYPES
import com.vitorpamplona.ammolite.relays.TypedFilter
import com.vitorpamplona.ammolite.relays.filters.EOSETime
import com.vitorpamplona.ammolite.relays.filters.SincePerRelayFilter
import com.vitorpamplona.quartz.experimental.relationshipStatus.RelationshipStatusEvent
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip17Dm.settings.ChatMessageRelayListEvent
import com.vitorpamplona.quartz.nip38UserStatus.StatusEvent
import com.vitorpamplona.quartz.nip56Reports.ReportEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent

object NostrSingleUserDataSource : AmethystNostrDataSource("SingleUserFeed") {
    private var usersToWatch = setOf<User>()

    fun createUserMetadataFilter(): List<TypedFilter>? {
        if (usersToWatch.isEmpty()) return null

        val firstTimers = usersToWatch.filter { it.latestMetadata == null }.map { it.pubkeyHex }

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

    fun createUserMetadataStatusReportFilter(): List<TypedFilter>? {
        if (usersToWatch.isEmpty()) return null

        val secondTimers = usersToWatch.filter { it.latestMetadata != null }

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
                                    kinds = listOf(MetadataEvent.KIND, StatusEvent.KIND, RelationshipStatusEvent.KIND, AdvertisedRelayListEvent.KIND, ChatMessageRelayListEvent.KIND),
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
        requestNewChannel { time, relayUrl ->
            checkNotInMainThread()

            usersToWatch.forEach {
                val eose = it.latestEOSEs[relayUrl]
                if (eose == null) {
                    it.latestEOSEs = it.latestEOSEs + Pair(relayUrl, EOSETime(time))
                } else {
                    eose.time = time
                }
            }
        }

    override fun updateChannelFilters() {
        checkNotInMainThread()

        userChannel.typedFilters =
            listOfNotNull(
                createUserMetadataFilter(),
                createUserMetadataStatusReportFilter(),
            ).flatten()
                .ifEmpty { null }
    }

    fun add(user: User) {
        if (!usersToWatch.contains(user)) {
            usersToWatch = usersToWatch.plus(user)
            invalidateFilters()
        }
    }

    fun remove(user: User) {
        if (usersToWatch.contains(user)) {
            usersToWatch = usersToWatch.minus(user)
            invalidateFilters()
        }
    }
}
