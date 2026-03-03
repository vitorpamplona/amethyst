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
package com.vitorpamplona.amethyst.desktop.subscriptions

import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter

/**
 * Type-safe builders for common Nostr filter patterns.
 * Provides convenience functions for creating relay subscription filters.
 */
object FilterBuilders {
    /**
     * Creates a filter for text notes (kind 1) from all authors.
     *
     * @param limit Maximum number of events to request
     * @param since Timestamp for events with publication time ≥ this value
     * @param until Timestamp for events with publication time ≤ this value
     * @return Filter for global text notes
     */
    fun textNotesGlobal(
        limit: Int? = null,
        since: Long? = null,
        until: Long? = null,
    ): Filter =
        Filter(
            kinds = listOf(1), // TextNoteEvent.KIND
            limit = limit,
            since = since,
            until = until,
        )

    /**
     * Creates a filter for text notes (kind 1) from specific authors.
     *
     * @param authors List of author public keys (hex-encoded, 64 chars each)
     * @param limit Maximum number of events to request
     * @param since Timestamp for events with publication time ≥ this value
     * @param until Timestamp for events with publication time ≤ this value
     * @return Filter for text notes from specified authors
     */
    fun textNotesFromAuthors(
        authors: List<String>,
        limit: Int? = null,
        since: Long? = null,
        until: Long? = null,
    ): Filter =
        Filter(
            kinds = listOf(1), // TextNoteEvent.KIND
            authors = authors,
            limit = limit,
            since = since,
            until = until,
        )

    /**
     * Creates a filter for user metadata (kind 0) from a specific author.
     *
     * @param pubKeyHex Author public key (hex-encoded, 64 chars)
     * @return Filter for user metadata (limit=1 since only latest is needed)
     */
    fun userMetadata(pubKeyHex: String): Filter =
        Filter(
            kinds = listOf(0), // MetadataEvent.KIND
            authors = listOf(pubKeyHex),
            limit = 1,
        )

    /**
     * Creates a filter for user metadata (kind 0) from multiple authors.
     *
     * @param pubKeyHexList List of author public keys (hex-encoded, 64 chars each)
     * @return Filter for user metadata
     */
    fun userMetadataBatch(pubKeyHexList: List<String>): Filter =
        Filter(
            kinds = listOf(0), // MetadataEvent.KIND
            authors = pubKeyHexList,
        )

    /**
     * Creates a filter for user metadata (kind 0) from multiple authors.
     * Alias for userMetadataBatch for API compatibility.
     *
     * @param pubKeys List of author public keys (hex-encoded, 64 chars each)
     * @return Filter for user metadata
     */
    fun userMetadataMultiple(pubKeys: List<String>): Filter =
        Filter(
            kinds = listOf(0),
            authors = pubKeys,
        )

    /**
     * Creates a filter for contact list (kind 3) from a specific author.
     *
     * @param pubKeyHex Author public key (hex-encoded, 64 chars)
     * @return Filter for contact list (limit=1 since only latest is needed)
     */
    fun contactList(pubKeyHex: String): Filter =
        Filter(
            kinds = listOf(3), // ContactListEvent.KIND
            authors = listOf(pubKeyHex),
            limit = 1,
        )

    /**
     * Creates a filter for notifications (mentions, replies, reactions, reposts, zaps) for a user.
     *
     * Includes:
     * - kind 1 (text notes mentioning user)
     * - kind 7 (reactions)
     * - kind 6 (reposts)
     * - kind 16 (generic reposts)
     * - kind 9735 (zaps)
     *
     * @param pubKeyHex User public key (hex-encoded, 64 chars) to filter notifications for
     * @param limit Maximum number of events to request
     * @param since Timestamp for events with publication time ≥ this value
     * @return Filter for notifications targeting the specified user
     */
    fun notificationsForUser(
        pubKeyHex: String,
        limit: Int? = null,
        since: Long? = null,
    ): Filter =
        Filter(
            kinds =
                listOf(
                    1, // TextNoteEvent.KIND (mentions/replies)
                    7, // ReactionEvent.KIND
                    6, // RepostEvent.KIND
                    16, // GenericRepostEvent.KIND
                    9735, // LnZapEvent.KIND
                ),
            tags = mapOf("p" to listOf(pubKeyHex)),
            limit = limit,
            since = since,
        )

    /**
     * Creates a filter for specific event kinds.
     *
     * @param kinds List of event kinds to filter
     * @param limit Maximum number of events to request
     * @param since Timestamp for events with publication time ≥ this value
     * @param until Timestamp for events with publication time ≤ this value
     * @return Filter for specified event kinds
     */
    fun byKinds(
        kinds: List<Int>,
        limit: Int? = null,
        since: Long? = null,
        until: Long? = null,
    ): Filter =
        Filter(
            kinds = kinds,
            limit = limit,
            since = since,
            until = until,
        )

    /**
     * Creates a filter for events from specific authors.
     *
     * @param authors List of author public keys (hex-encoded, 64 chars each)
     * @param kinds Optional list of event kinds to filter (if null, all kinds)
     * @param limit Maximum number of events to request
     * @param since Timestamp for events with publication time ≥ this value
     * @param until Timestamp for events with publication time ≤ this value
     * @return Filter for events from specified authors
     */
    fun byAuthors(
        authors: List<String>,
        kinds: List<Int>? = null,
        limit: Int? = null,
        since: Long? = null,
        until: Long? = null,
    ): Filter =
        Filter(
            authors = authors,
            kinds = kinds,
            limit = limit,
            since = since,
            until = until,
        )

    /**
     * Creates a filter for events with specific event IDs.
     *
     * @param ids List of event IDs (hex-encoded, 64 chars each)
     * @return Filter for specified event IDs
     */
    fun byIds(ids: List<String>): Filter =
        Filter(
            ids = ids,
        )

    /**
     * Creates a filter for chess game challenges (kind 30064).
     * Includes open challenges (no opponent) and challenges directed at a specific user.
     *
     * @param userPubkey The user's public key to filter challenges for
     * @param limit Maximum number of events to request
     * @param since Timestamp for events with publication time ≥ this value
     * @return Filter for chess challenges
     */
    fun chessChallengesToUser(
        userPubkey: String,
        limit: Int? = 50,
        since: Long? = null,
    ): Filter =
        Filter(
            kinds = listOf(30064), // LiveChessGameChallengeEvent.KIND
            tags = mapOf("p" to listOf(userPubkey)),
            limit = limit,
            since = since,
        )

    /**
     * Creates a filter for open chess challenges (kind 30064, no specific opponent).
     *
     * @param limit Maximum number of events to request
     * @param since Timestamp for events with publication time ≥ this value
     * @return Filter for open chess challenges
     */
    fun chessOpenChallenges(
        limit: Int? = 50,
        since: Long? = null,
    ): Filter =
        Filter(
            kinds = listOf(30064), // LiveChessGameChallengeEvent.KIND
            limit = limit,
            since = since,
        )

    /**
     * Creates a filter for all chess events (challenges, accepts, moves, ends, draw offers).
     * Useful for loading the chess lobby.
     *
     * @param limit Maximum number of events to request
     * @param since Timestamp for events with publication time ≥ this value
     * @return Filter for all chess events
     */
    fun chessAllEvents(
        limit: Int? = 100,
        since: Long? = null,
    ): Filter =
        Filter(
            kinds = listOf(30064, 30065, 30066, 30067, 30068),
            limit = limit,
            since = since,
        )

    /**
     * Creates a filter for chess events involving a specific user.
     * Includes challenges to/from user, moves in their games, draw offers, etc.
     *
     * @param userPubkey The user's public key
     * @param limit Maximum number of events to request
     * @param since Timestamp for events with publication time ≥ this value
     * @return Filter for chess events involving the user
     */
    fun chessEventsForUser(
        userPubkey: String,
        limit: Int? = 100,
        since: Long? = null,
    ): Filter =
        Filter(
            kinds = listOf(30064, 30065, 30066, 30067, 30068),
            tags = mapOf("p" to listOf(userPubkey)),
            limit = limit,
            since = since,
        )

    /**
     * Creates a filter for chess events by the user (their own events).
     *
     * @param userPubkey The user's public key
     * @param limit Maximum number of events to request
     * @param since Timestamp for events with publication time ≥ this value
     * @return Filter for chess events by the user
     */
    fun chessEventsByUser(
        userPubkey: String,
        limit: Int? = 100,
        since: Long? = null,
    ): Filter =
        Filter(
            kinds = listOf(30064, 30065, 30066, 30067, 30068),
            authors = listOf(userPubkey),
            limit = limit,
            since = since,
        )

    /**
     * Creates a filter for events tagged with specific p-tags (mentioning users).
     *
     * @param pubKeys List of public keys (hex-encoded, 64 chars each) to filter by
     * @param kinds Optional list of event kinds to filter
     * @param limit Maximum number of events to request
     * @param since Timestamp for events with publication time ≥ this value
     * @return Filter for events mentioning specified users
     */
    fun byPTags(
        pubKeys: List<String>,
        kinds: List<Int>? = null,
        limit: Int? = null,
        since: Long? = null,
    ): Filter =
        Filter(
            tags = mapOf("p" to pubKeys),
            kinds = kinds,
            limit = limit,
            since = since,
        )

    /**
     * Creates a filter for events tagged with specific e-tags (referencing events).
     *
     * @param eventIds List of event IDs (hex-encoded, 64 chars each) to filter by
     * @param kinds Optional list of event kinds to filter
     * @param limit Maximum number of events to request
     * @param since Timestamp for events with publication time ≥ this value
     * @return Filter for events referencing specified events
     */
    fun byETags(
        eventIds: List<String>,
        kinds: List<Int>? = null,
        limit: Int? = null,
        since: Long? = null,
    ): Filter =
        Filter(
            tags = mapOf("e" to eventIds),
            kinds = kinds,
            limit = limit,
            since = since,
        )

    /**
     * Creates a filter for events with custom tag filters.
     *
     * @param tags Map of tag names to value lists (e.g., {"p": ["pubkey1"], "t": ["bitcoin"]})
     * @param kinds Optional list of event kinds to filter
     * @param limit Maximum number of events to request
     * @param since Timestamp for events with publication time ≥ this value
     * @param until Timestamp for events with publication time ≤ this value
     * @return Filter with custom tag criteria
     */
    fun byTags(
        tags: Map<String, List<String>>,
        kinds: List<Int>? = null,
        limit: Int? = null,
        since: Long? = null,
        until: Long? = null,
    ): Filter =
        Filter(
            tags = tags,
            kinds = kinds,
            limit = limit,
            since = since,
            until = until,
        )

    /**
     * Creates a NIP-50 search filter for user metadata (kind 0).
     * Searches user profiles by name, displayName, about, nip05, etc.
     * Requires a NIP-50 compatible relay (e.g., relay.nostr.band, nostr.wine).
     *
     * @param searchQuery The text to search for in user profiles
     * @param limit Maximum number of results to return
     * @return Filter for NIP-50 search
     */
    fun searchPeople(
        searchQuery: String,
        limit: Int = 50,
    ): Filter =
        Filter(
            kinds = listOf(0), // MetadataEvent.KIND
            search = searchQuery,
            limit = limit,
        )

    /**
     * Creates a NIP-50 search filter for text notes (kind 1).
     * Searches note content.
     * Requires a NIP-50 compatible relay.
     *
     * @param searchQuery The text to search for in notes
     * @param limit Maximum number of results to return
     * @return Filter for NIP-50 search
     */
    fun searchNotes(
        searchQuery: String,
        limit: Int = 50,
    ): Filter =
        Filter(
            kinds = listOf(1), // TextNoteEvent.KIND
            search = searchQuery,
            limit = limit,
        )

    /**
     * Creates a filter for zap receipts (kind 9735) for specific events.
     *
     * @param eventIds List of event IDs to get zaps for
     * @param limit Maximum number of events to request
     * @return Filter for zap receipts
     */
    fun zapsForEvents(
        eventIds: List<String>,
        limit: Int? = null,
    ): Filter =
        Filter(
            kinds = listOf(9735), // LnZapEvent.KIND
            tags = mapOf("e" to eventIds),
            limit = limit,
        )

    /**
     * Creates a filter for reactions (kind 7) for specific events.
     *
     * @param eventIds List of event IDs to get reactions for
     * @param limit Maximum number of events to request
     * @return Filter for reactions
     */
    fun reactionsForEvents(
        eventIds: List<String>,
        limit: Int? = null,
    ): Filter =
        Filter(
            kinds = listOf(7), // ReactionEvent.KIND
            tags = mapOf("e" to eventIds),
            limit = limit,
        )

    /**
     * Creates a filter for replies (kind 1) to specific events.
     *
     * @param eventIds List of event IDs to get replies for
     * @param limit Maximum number of events to request
     * @return Filter for replies
     */
    fun repliesForEvents(
        eventIds: List<String>,
        limit: Int? = null,
    ): Filter =
        Filter(
            kinds = listOf(1), // TextNoteEvent.KIND
            tags = mapOf("e" to eventIds),
            limit = limit,
        )

    /**
     * Creates a filter for reposts (kind 6) of specific events.
     *
     * @param eventIds List of event IDs to get reposts for
     * @param limit Maximum number of events to request
     * @return Filter for reposts
     */
    fun repostsForEvents(
        eventIds: List<String>,
        limit: Int? = null,
    ): Filter =
        Filter(
            kinds = listOf(6), // RepostEvent.KIND
            tags = mapOf("e" to eventIds),
            limit = limit,
        )

    /**
     * Creates a filter for NIP-04 DMs (kind 4) sent to a user.
     *
     * @param pubKeyHex Recipient public key (hex-encoded, 64 chars)
     * @param limit Maximum number of events to request
     * @param since Timestamp for events with publication time >= this value
     * @return Filter for DMs addressed to the specified user
     */
    fun nip04DmsToUser(
        pubKeyHex: String,
        limit: Int? = null,
        since: Long? = null,
    ): Filter =
        Filter(
            kinds = listOf(4), // PrivateDmEvent.KIND
            tags = mapOf("p" to listOf(pubKeyHex)),
            limit = limit,
            since = since,
        )

    /**
     * Creates a filter for NIP-04 DMs (kind 4) sent from a user.
     *
     * @param pubKeyHex Author public key (hex-encoded, 64 chars)
     * @param limit Maximum number of events to request
     * @param since Timestamp for events with publication time >= this value
     * @return Filter for DMs authored by the specified user
     */
    fun nip04DmsFromUser(
        pubKeyHex: String,
        limit: Int? = null,
        since: Long? = null,
    ): Filter =
        Filter(
            kinds = listOf(4), // PrivateDmEvent.KIND
            authors = listOf(pubKeyHex),
            limit = limit,
            since = since,
        )

    /**
     * Creates a filter for NIP-59 gift-wrapped events (kind 1059) to a user.
     * Gift wraps contain encrypted NIP-17 DMs.
     *
     * @param pubKeyHex Recipient public key (hex-encoded, 64 chars)
     * @param since Timestamp (adjusted -2 days due to randomized created_at)
     * @return Filter for gift wraps addressed to the specified user
     */
    fun giftWrapsToUser(
        pubKeyHex: String,
        since: Long? = null,
    ): Filter =
        Filter(
            kinds = listOf(1059), // GiftWrapEvent.KIND
            tags = mapOf("p" to listOf(pubKeyHex)),
            since = since,
        )

    /**
     * Creates a filter for DM relay list events (kind 10050, NIP-17).
     *
     * @param pubKeyHex Author public key (hex-encoded, 64 chars)
     * @return Filter for DM relay list (limit=1 since only latest is needed)
     */
    fun dmRelayList(pubKeyHex: String): Filter =
        Filter(
            kinds = listOf(10050), // ChatMessageRelayListEvent.KIND
            authors = listOf(pubKeyHex),
            limit = 1,
        )

    /**
     * Creates a filter for long-form content (kind 30023, NIP-23).
     *
     * @param limit Maximum number of events to request
     * @param since Timestamp for events with publication time ≥ this value
     * @param until Timestamp for events with publication time ≤ this value
     * @return Filter for long-form content
     */
    fun longFormGlobal(
        limit: Int? = null,
        since: Long? = null,
        until: Long? = null,
    ): Filter =
        Filter(
            kinds = listOf(30023), // LongTextNoteEvent.KIND
            limit = limit,
            since = since,
            until = until,
        )

    /**
     * Creates a filter for long-form content (kind 30023) from specific authors.
     *
     * @param authors List of author public keys (hex-encoded, 64 chars each)
     * @param limit Maximum number of events to request
     * @param since Timestamp for events with publication time ≥ this value
     * @param until Timestamp for events with publication time ≤ this value
     * @return Filter for long-form content from specified authors
     */
    fun longFormFromAuthors(
        authors: List<String>,
        limit: Int? = null,
        since: Long? = null,
        until: Long? = null,
    ): Filter =
        Filter(
            kinds = listOf(30023), // LongTextNoteEvent.KIND
            authors = authors,
            limit = limit,
            since = since,
            until = until,
        )
}

/**
 * DSL builder for creating custom filters with a fluent API.
 *
 * Example:
 * ```kotlin
 * val filter = buildFilter {
 *     kinds(1, 7)
 *     authors("pubkey1", "pubkey2")
 *     limit(50)
 *     since(System.currentTimeMillis() / 1000 - 86400) // Last 24 hours
 * }
 * ```
 */
class FilterBuilder {
    private var ids: List<String>? = null
    private var authors: List<String>? = null
    private var kinds: List<Int>? = null
    private var tags: MutableMap<String, List<String>>? = null
    private var tagsAll: MutableMap<String, List<String>>? = null
    private var since: Long? = null
    private var until: Long? = null
    private var limit: Int? = null
    private var search: String? = null

    fun ids(vararg ids: String) {
        this.ids = ids.toList()
    }

    fun ids(ids: List<String>) {
        this.ids = ids
    }

    fun authors(vararg authors: String) {
        this.authors = authors.toList()
    }

    fun authors(authors: List<String>) {
        this.authors = authors
    }

    fun kinds(vararg kinds: Int) {
        this.kinds = kinds.toList()
    }

    fun kinds(kinds: List<Int>) {
        this.kinds = kinds
    }

    fun tag(
        name: String,
        values: List<String>,
    ) {
        if (tags == null) tags = mutableMapOf()
        tags!![name] = values
    }

    fun tagAll(
        name: String,
        values: List<String>,
    ) {
        if (tagsAll == null) tagsAll = mutableMapOf()
        tagsAll!![name] = values
    }

    fun pTag(vararg pubKeys: String) {
        tag("p", pubKeys.toList())
    }

    fun eTag(vararg eventIds: String) {
        tag("e", eventIds.toList())
    }

    fun since(timestamp: Long) {
        this.since = timestamp
    }

    fun until(timestamp: Long) {
        this.until = timestamp
    }

    fun limit(limit: Int) {
        this.limit = limit
    }

    fun search(search: String) {
        this.search = search
    }

    fun build(): Filter = Filter(ids, authors, kinds, tags, tagsAll, since, until, limit, search)
}

/**
 * Creates a filter using the DSL builder.
 *
 * Example:
 * ```kotlin
 * val filter = buildFilter {
 *     kinds(1)
 *     authors("pubkey1", "pubkey2")
 *     limit(50)
 * }
 * ```
 */
fun buildFilter(block: FilterBuilder.() -> Unit): Filter = FilterBuilder().apply(block).build()
