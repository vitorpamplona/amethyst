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
    private val FEED_KINDS_WITH_ARTICLES = listOf(1, 6, 16, 30023)
    private val FEED_KINDS_ALL = listOf(1, 6, 16, 30023, 6969, 31922, 31923, 30402)

    fun textNotesGlobal(limit: Int? = null): Filter = Filter(kinds = FEED_KINDS, limit = limit)

    fun textNotesWithArticlesGlobal(limit: Int? = null): Filter = Filter(kinds = FEED_KINDS_ALL, limit = limit)

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

    /**
     * Long-form articles (NIP-23, kind 30023).
     */
    fun longFormArticles(limit: Int? = null): Filter = Filter(kinds = listOf(30023), limit = limit)

    fun longFormArticlesByAuthors(
        authors: List<String>,
        limit: Int? = null,
    ): Filter = Filter(kinds = listOf(30023), authors = authors, limit = limit)

    /**
     * User status (NIP-38, kind 30315).
     */
    fun userStatus(pubKeyHex: String): Filter = Filter(kinds = listOf(30315), authors = listOf(pubKeyHex), limit = 2)

    fun userStatusMultiple(pubKeys: List<String>): Filter = Filter(kinds = listOf(30315), authors = pubKeys)

    // ── NIP-72 Communities ──

    /**
     * Discover community definitions (kind 34550).
     */
    fun communityDefinitions(limit: Int? = null): Filter = Filter(kinds = listOf(34550), limit = limit)

    /**
     * Community definitions by specific authors.
     */
    fun communityDefinitionsByAuthors(
        authors: List<String>,
        limit: Int? = null,
    ): Filter = Filter(kinds = listOf(34550), authors = authors, limit = limit)

    /**
     * Approved posts in a community (kind 4550 tagged with the community address).
     */
    fun communityApprovedPosts(
        communityAddressId: String,
        limit: Int? = null,
    ): Filter = Filter(kinds = listOf(4550), tags = mapOf("a" to listOf(communityAddressId)), limit = limit)

    /**
     * Text notes that tag a specific community (posted to community).
     */
    fun postsTaggingCommunity(
        communityAddressId: String,
        limit: Int? = null,
    ): Filter = Filter(kinds = listOf(1), tags = mapOf("a" to listOf(communityAddressId)), limit = limit)

    /**
     * User's community list (NIP-72 kind 10004).
     */
    fun communityList(pubKeyHex: String): Filter = Filter(kinds = listOf(10004), authors = listOf(pubKeyHex), limit = 1)

    // ── NIP-88 Polls (ZapPoll kind 6969) ──

    /**
     * Discover polls (kind 6969).
     */
    fun polls(limit: Int? = null): Filter = Filter(kinds = listOf(6969), limit = limit)

    /**
     * Polls by specific authors.
     */
    fun pollsByAuthors(
        authors: List<String>,
        limit: Int? = null,
    ): Filter = Filter(kinds = listOf(6969), authors = authors, limit = limit)

    // ── NIP-52 Calendar Events ──

    /**
     * Discover calendar events: kind 31922 (date-slot) + kind 31923 (time-slot).
     */
    fun calendarEvents(limit: Int? = null): Filter = Filter(kinds = listOf(31922, 31923), limit = limit)

    /**
     * Calendar events by specific authors.
     */
    fun calendarEventsByAuthors(
        authors: List<String>,
        limit: Int? = null,
    ): Filter = Filter(kinds = listOf(31922, 31923), authors = authors, limit = limit)

    /**
     * RSVP responses for calendar events (kind 31925).
     */
    fun calendarRsvps(
        calendarEventAddressIds: List<String>,
        limit: Int? = null,
    ): Filter = Filter(kinds = listOf(31925), tags = mapOf("a" to calendarEventAddressIds), limit = limit)

    // ── NIP-53 Live Activities ──

    /**
     * Discover live activities (kind 30311).
     */
    fun liveActivities(limit: Int? = null): Filter = Filter(kinds = listOf(30311), limit = limit)

    /**
     * Live activities that are currently live.
     */
    fun liveActivitiesLive(limit: Int? = null): Filter = Filter(kinds = listOf(30311), tags = mapOf("status" to listOf("live")), limit = limit)

    /**
     * Live activities by specific authors.
     */
    fun liveActivitiesByAuthors(
        authors: List<String>,
        limit: Int? = null,
    ): Filter = Filter(kinds = listOf(30311), authors = authors, limit = limit)

    // ── NIP-99 Classifieds ──

    /**
     * Discover classified listings (kind 30402).
     */
    fun classifiedListings(limit: Int? = null): Filter = Filter(kinds = listOf(30402), limit = limit)

    /**
     * Classified listings by specific authors.
     */
    fun classifiedListingsByAuthors(
        authors: List<String>,
        limit: Int? = null,
    ): Filter = Filter(kinds = listOf(30402), authors = authors, limit = limit)

    /**
     * Search classified listings.
     */
    fun searchClassifiedListings(
        searchQuery: String,
        limit: Int = 50,
    ): Filter = Filter(kinds = listOf(30402), search = searchQuery, limit = limit)

    // ── NIP-58 Badges ──

    /**
     * Badge definitions (kind 30009) by specific issuers.
     */
    fun badgeDefinitions(
        authors: List<String>,
        limit: Int? = null,
    ): Filter = Filter(kinds = listOf(30009), authors = authors, limit = limit)

    /**
     * Badge awards (kind 8) targeting a specific user.
     */
    fun badgeAwardsForUser(
        pubKeyHex: String,
        limit: Int? = null,
    ): Filter = Filter(kinds = listOf(8), tags = mapOf("p" to listOf(pubKeyHex)), limit = limit)

    /**
     * Profile badges (kind 10008) for a specific user.
     */
    fun profileBadges(pubKeyHex: String): Filter = Filter(kinds = listOf(10008), authors = listOf(pubKeyHex), limit = 1)

    /**
     * Badge definitions (kind 30009) globally.
     */
    fun badgeDefinitionsGlobal(limit: Int? = null): Filter = Filter(kinds = listOf(30009), limit = limit)
}

/**
 * Generates a unique subscription ID with timestamp.
 */
fun generateSubId(prefix: String): String = "$prefix-${com.vitorpamplona.quartz.utils.currentTimeSeconds()}"
