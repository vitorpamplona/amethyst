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
package com.vitorpamplona.amethyst.ios.subscriptions

import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter

/**
 * Type-safe builders for common Nostr filter patterns.
 * iOS version — mirrors desktop FilterBuilders.
 */
object FilterBuilders {
    private val FEED_KINDS = listOf(1, 6, 16)

    fun textNotesGlobal(limit: Int? = null): Filter = Filter(kinds = FEED_KINDS, limit = limit)

    fun textNotesFromAuthors(
        authors: List<String>,
        limit: Int? = null,
    ): Filter = Filter(kinds = FEED_KINDS, authors = authors, limit = limit)

    fun userMetadata(pubKeyHex: String): Filter = Filter(kinds = listOf(0), authors = listOf(pubKeyHex), limit = 1)

    fun userMetadataMultiple(pubKeys: List<String>): Filter = Filter(kinds = listOf(0), authors = pubKeys)

    fun contactList(pubKeyHex: String): Filter = Filter(kinds = listOf(3), authors = listOf(pubKeyHex), limit = 1)

    fun notificationsForUser(
        pubKeyHex: String,
        limit: Int? = null,
        since: Long? = null,
    ): Filter =
        Filter(
            kinds = listOf(1, 7, 6, 16, 9735),
            tags = mapOf("p" to listOf(pubKeyHex)),
            limit = limit,
            since = since,
        )

    fun byIds(ids: List<String>): Filter = Filter(ids = ids)

    fun byETags(
        eventIds: List<String>,
        kinds: List<Int>? = null,
        limit: Int? = null,
    ): Filter = Filter(tags = mapOf("e" to eventIds), kinds = kinds, limit = limit)

    fun byTags(
        tags: Map<String, List<String>>,
        kinds: List<Int>? = null,
        limit: Int? = null,
    ): Filter = Filter(tags = tags, kinds = kinds, limit = limit)

    fun byPTags(
        pubKeys: List<String>,
        kinds: List<Int>? = null,
        limit: Int? = null,
    ): Filter = Filter(tags = mapOf("p" to pubKeys), kinds = kinds, limit = limit)

    fun byAuthors(
        authors: List<String>,
        kinds: List<Int>? = null,
        limit: Int? = null,
    ): Filter = Filter(authors = authors, kinds = kinds, limit = limit)

    fun searchPeople(
        searchQuery: String,
        limit: Int = 50,
    ): Filter = Filter(kinds = listOf(0), search = searchQuery, limit = limit)

    fun searchNotes(
        searchQuery: String,
        limit: Int = 50,
    ): Filter = Filter(kinds = listOf(1), search = searchQuery, limit = limit)

    fun nip04DmsToUser(
        pubKeyHex: String,
        limit: Int? = null,
        since: Long? = null,
    ): Filter = Filter(kinds = listOf(4), tags = mapOf("p" to listOf(pubKeyHex)), limit = limit, since = since)

    fun nip04DmsFromUser(
        pubKeyHex: String,
        limit: Int? = null,
        since: Long? = null,
    ): Filter = Filter(kinds = listOf(4), authors = listOf(pubKeyHex), limit = limit, since = since)

    fun reactionsForEvents(
        eventIds: List<String>,
        limit: Int? = null,
    ): Filter = Filter(kinds = listOf(7), tags = mapOf("e" to eventIds), limit = limit)

    fun zapsForEvents(
        eventIds: List<String>,
        limit: Int? = null,
    ): Filter = Filter(kinds = listOf(9735), tags = mapOf("e" to eventIds), limit = limit)

    fun repliesForEvents(
        eventIds: List<String>,
        limit: Int? = null,
    ): Filter = Filter(kinds = listOf(1), tags = mapOf("e" to eventIds), limit = limit)

    fun repostsForEvents(
        eventIds: List<String>,
        limit: Int? = null,
    ): Filter = Filter(kinds = listOf(6), tags = mapOf("e" to eventIds), limit = limit)

    /**
     * Text notes tagged with specific hashtags (NIP-12 "t" tag filter).
     */
    fun textNotesByHashtags(
        hashtags: List<String>,
        limit: Int? = null,
    ): Filter = Filter(kinds = listOf(1), tags = mapOf("t" to hashtags), limit = limit)

    /**
     * Fetch the user's NIP-51 hashtag list (kind 10015).
     */
    fun hashtagList(pubKeyHex: String): Filter = Filter(kinds = listOf(10015), authors = listOf(pubKeyHex), limit = 1)
}

/**
 * Generates a unique subscription ID with timestamp.
 */
fun generateSubId(prefix: String): String = "$prefix-${com.vitorpamplona.quartz.utils.currentTimeSeconds()}"
