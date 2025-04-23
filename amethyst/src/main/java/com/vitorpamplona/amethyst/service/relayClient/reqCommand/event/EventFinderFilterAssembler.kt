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
package com.vitorpamplona.amethyst.service.relayClient.reqCommand.event

import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.QueryBasedSubscriptionOrchestrator
import com.vitorpamplona.ammolite.relays.EVENT_FINDER_TYPES
import com.vitorpamplona.ammolite.relays.NostrClient
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
import com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.nip56Reports.ReportEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.nip72ModCommunities.approval.CommunityPostApprovalEvent
import com.vitorpamplona.quartz.nip90Dvms.NIP90ContentDiscoveryResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.NIP90StatusEvent
import kotlin.collections.mapNotNullTo

// This allows multiple screen to be listening to tags, even the same tag
class EventFinderQueryState(
    val note: Note,
)

class EventFinderFilterAssembler(
    client: NostrClient,
) : QueryBasedSubscriptionOrchestrator<EventFinderQueryState>(client) {
    private fun createReactionsToWatchInAddressFilter(keys: Set<AddressableNote>): List<TypedFilter>? {
        if (keys.isEmpty()) {
            return null
        }

        return groupByEOSEPresence(keys)
            .map {
                listOf(
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
                                        CommunityPostApprovalEvent.KIND,
                                        LiveActivitiesChatMessageEvent.KIND,
                                    ),
                                tags = mapOf("a" to it.mapNotNull { it.address()?.toValue() }),
                                since = findMinimumEOSEs(it),
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
                                    ),
                                tags = mapOf("a" to it.mapNotNull { it.address()?.toValue() }),
                                since = findMinimumEOSEs(it),
                                // Max amount of "replies" to download on a specific event.
                                limit = 10,
                            ),
                    ),
                )
            }.flatten()
    }

    private fun createAddressFilter(keys: Set<AddressableNote>): List<TypedFilter>? {
        val myAddressesToWatch = keys.filter { it.event == null }

        if (myAddressesToWatch.isEmpty()) {
            return null
        }

        return myAddressesToWatch.map {
            it.address().let { aTag ->
                if (aTag.kind < 25000 && aTag.dTag.isBlank()) {
                    TypedFilter(
                        types = EVENT_FINDER_TYPES,
                        filter =
                            SincePerRelayFilter(
                                kinds = listOf(aTag.kind),
                                authors = listOf(aTag.pubKeyHex),
                                limit = 5,
                            ),
                    )
                } else {
                    TypedFilter(
                        types = EVENT_FINDER_TYPES,
                        filter =
                            SincePerRelayFilter(
                                kinds = listOf(aTag.kind),
                                tags = mapOf("d" to listOf(aTag.dTag)),
                                authors = listOf(aTag.pubKeyHex),
                                limit = 5,
                            ),
                    )
                }
            }
        }
    }

    private fun createRepliesAndReactionsFilter(keys: Set<Note>): List<TypedFilter>? {
        if (keys.isEmpty()) {
            return null
        }

        return groupByEOSEPresence(keys)
            .map {
                listOf(
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
                                tags = mapOf("e" to it.map { it.idHex }),
                                since = findMinimumEOSEs(it),
                                // Max amount of "replies" to download on a specific event.
                                limit = 10000,
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
                                tags = mapOf("e" to it.map { it.idHex }),
                                since = findMinimumEOSEs(it),
                                limit = 100,
                            ),
                    ),
                )
            }.flatten()
    }

    private fun createQuotesFilter(keys: Set<Note>): List<TypedFilter>? =
        groupByEOSEPresence(keys)
            .map {
                listOf(
                    TypedFilter(
                        types = EVENT_FINDER_TYPES,
                        filter =
                            SincePerRelayFilter(
                                kinds = listOf(TextNoteEvent.KIND),
                                tags = mapOf("q" to it.map { it.idHex }),
                                since = findMinimumEOSEs(it),
                                // Max amount of "replies" to download on a specific event.
                                limit = 1000,
                            ),
                    ),
                )
            }.flatten()

    fun createLoadEventsIfNotLoadedFilter(keys: Set<Note>): List<TypedFilter>? {
        val directEventsToLoad = keys.filter { it.event == null }

        val threadingEventsToLoad =
            keys
                .mapNotNull { it.replyTo }
                .flatten()
                .filter { it !is AddressableNote && it.event == null }

        val interestedEvents = (directEventsToLoad + threadingEventsToLoad).map { it.idHex }.toSet().toList()

        if (interestedEvents.isEmpty()) {
            return null
        }

        // downloads linked events to this event.
        return listOf(
            TypedFilter(
                types = EVENT_FINDER_TYPES,
                filter = SincePerRelayFilter(ids = interestedEvents),
            ),
        )
    }

    val singleEventChannel =
        requestNewSubscription { time, relayUrl ->
            // Ignores EOSE if it is in the middle of a filter change.
            if (isUpdatingFilters()) return@requestNewSubscription

            checkNotInMainThread()

            forEachSubscriber {
                val eose = it.note.lastReactionsDownloadTime[relayUrl]
                if (eose == null) {
                    it.note.lastReactionsDownloadTime += Pair(relayUrl, EOSETime(time))
                } else {
                    eose.time = time
                }
            }

            // Many relays operate with limits in the amount of filters.
            // As information comes, the filters will be rotated to get more data.
            invalidateFilters()
        }

    override fun updateSubscriptions(keys: Set<EventFinderQueryState>) {
        val addressables =
            keys
                .mapNotNullTo(mutableSetOf()) {
                    it.note as? AddressableNote
                }.toSet()

        val events =
            keys
                .mapNotNullTo(mutableSetOf()) {
                    if (it.note !is AddressableNote) {
                        it.note
                    } else {
                        null
                    }
                }.toSet()

        val eventReactions = createRepliesAndReactionsFilter(events)
        val addressReactions = createReactionsToWatchInAddressFilter(addressables)

        val missingEvents = createLoadEventsIfNotLoadedFilter(events)
        val missingAddresses = createAddressFilter(addressables)

        val quotes = createQuotesFilter(events + addressables)

        singleEventChannel.typedFilters =
            listOfNotNull(missingEvents, missingAddresses, eventReactions, addressReactions, quotes).flatten().ifEmpty { null }
    }
}

fun groupByEOSEPresence(notes: Set<Note>): Collection<List<Note>> =
    notes
        .groupBy {
            it.lastReactionsDownloadTime.keys
                .sorted()
                .joinToString(",")
        }.values
        .map {
            it.sortedBy { it.idHex } // important to keep in order otherwise the Relay thinks the filter has changed and we REQ again
        }

fun groupByEOSEPresence(users: Iterable<User>): Collection<List<User>> =
    users
        .groupBy {
            it.latestEOSEs.keys
                .sorted()
                .joinToString(",")
        }.values
        .map {
            it.sortedBy { it.pubkeyHex } // important to keep in order otherwise the Relay thinks the filter has changed and we REQ again
        }

fun findMinimumEOSEs(notes: List<Note>): Map<String, EOSETime> {
    val minLatestEOSEs = mutableMapOf<String, EOSETime>()

    notes.forEach { note ->
        note.lastReactionsDownloadTime.forEach {
            val minEose = minLatestEOSEs[it.key]
            if (minEose == null) {
                minLatestEOSEs.put(it.key, EOSETime(it.value.time))
            } else if (it.value.time < minEose.time) {
                minEose.time = it.value.time
            }
        }
    }

    return minLatestEOSEs
}

fun findMinimumEOSEsForUsers(users: List<User>): Map<String, EOSETime> {
    val minLatestEOSEs = mutableMapOf<String, EOSETime>()

    users.forEach {
        it.latestEOSEs.forEach {
            val minEose = minLatestEOSEs[it.key]
            if (minEose == null) {
                minLatestEOSEs.put(it.key, EOSETime(it.value.time))
            } else if (it.value.time < minEose.time) {
                minEose.time = it.value.time
            }
        }
    }

    return minLatestEOSEs
}
