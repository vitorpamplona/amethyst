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
package com.vitorpamplona.amethyst.ios.feeds

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.ui.feeds.AdditiveFeedFilter
import com.vitorpamplona.amethyst.commons.ui.feeds.DefaultFeedOrder
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedFilter
import com.vitorpamplona.amethyst.ios.cache.IosLocalCache
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.isTaggedHash
import com.vitorpamplona.quartz.nip01Core.tags.people.isTaggedUser
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.StatusTag
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.nip72ModCommunities.isForCommunity

private fun isFeedNote(event: com.vitorpamplona.quartz.nip01Core.core.Event?): Boolean =
    event is TextNoteEvent ||
        event is RepostEvent ||
        event is GenericRepostEvent ||
        event is LongTextNoteEvent

/**
 * Global feed: kind 1 text notes + kind 6/16 reposts, sorted by createdAt desc.
 */
class IosGlobalFeedFilter(
    private val cache: IosLocalCache,
) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String = "global"

    override fun feed(): List<Note> =
        cache
            .allNotes()
            .filter { isFeedNote(it.event) }
            .sortedWith(DefaultFeedOrder)
            .take(limit())

    override fun applyFilter(newItems: Set<Note>): Set<Note> = newItems.filterTo(HashSet()) { isFeedNote(it.event) }

    override fun sort(items: Set<Note>): List<Note> = items.sortedWith(DefaultFeedOrder)

    override fun limit(): Int = 500
}

/**
 * Following feed: kind 1 text notes + kind 6/16 reposts from followed pubkeys.
 */
class IosFollowingFeedFilter(
    private val cache: IosLocalCache,
    private val followedPubkeys: () -> Set<HexKey>,
) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String = "following-${followedPubkeys().hashCode()}"

    override fun feed(): List<Note> {
        val follows = followedPubkeys()
        return cache
            .allNotes()
            .filter { isFeedNote(it.event) && it.author?.pubkeyHex in follows }
            .sortedWith(DefaultFeedOrder)
            .take(limit())
    }

    override fun applyFilter(newItems: Set<Note>): Set<Note> {
        val follows = followedPubkeys()
        return newItems.filterTo(HashSet()) {
            isFeedNote(it.event) && it.author?.pubkeyHex in follows
        }
    }

    override fun sort(items: Set<Note>): List<Note> = items.sortedWith(DefaultFeedOrder)

    override fun limit(): Int = 500
}

/**
 * Thread feed: root note + all replies (graph walk via Note.replies).
 */
class IosThreadFilter(
    private val noteId: HexKey,
    private val cache: IosLocalCache,
) : FeedFilter<Note>() {
    override fun feedKey(): String = "thread-$noteId"

    override fun feed(): List<Note> {
        val root = cache.getNoteIfExists(noteId) ?: return emptyList()
        val seen = LinkedHashSet<Note>()
        seen.add(root)
        collectReplies(root, seen)
        return seen.sortedWith(compareBy { it.createdAt() ?: 0L })
    }

    private fun collectReplies(
        note: Note,
        seen: LinkedHashSet<Note>,
    ) {
        for (reply in note.replies) {
            if (seen.add(reply)) {
                collectReplies(reply, seen)
            }
        }
    }

    override fun limit(): Int = Int.MAX_VALUE
}

/**
 * Profile feed: text notes + reposts by a specific pubkey.
 */
class IosProfileFeedFilter(
    private val pubkey: HexKey,
    private val cache: IosLocalCache,
) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String = "profile-$pubkey"

    private fun isProfileNote(note: Note): Boolean {
        val event = note.event ?: return false
        return note.author?.pubkeyHex == pubkey && isFeedNote(event)
    }

    override fun feed(): List<Note> =
        cache
            .allNotes()
            .filter { isProfileNote(it) }
            .sortedWith(DefaultFeedOrder)
            .take(limit())

    override fun applyFilter(newItems: Set<Note>): Set<Note> = newItems.filterTo(HashSet()) { isProfileNote(it) }

    override fun sort(items: Set<Note>): List<Note> = items.sortedWith(DefaultFeedOrder)

    override fun limit(): Int = 500
}

/**
 * Notification feed: events that tag the logged-in user.
 */
class IosNotificationFeedFilter(
    private val userPubKeyHex: HexKey,
    private val cache: IosLocalCache,
) : AdditiveFeedFilter<Note>() {
    companion object {
        val NOTIFICATION_KINDS =
            setOf(
                TextNoteEvent.KIND,
                ReactionEvent.KIND,
                RepostEvent.KIND,
                LnZapEvent.KIND,
            )
    }

    override fun feedKey(): String = "notifications-$userPubKeyHex"

    override fun feed(): List<Note> =
        cache
            .allNotes()
            .filter { isNotificationForUser(it) }
            .sortedWith(DefaultFeedOrder)
            .take(limit())

    override fun applyFilter(newItems: Set<Note>): Set<Note> = newItems.filterTo(HashSet()) { isNotificationForUser(it) }

    override fun sort(items: Set<Note>): List<Note> = items.sortedWith(DefaultFeedOrder)

    override fun limit(): Int = 500

    private fun isNotificationForUser(note: Note): Boolean {
        val event = note.event ?: return false
        return event.kind in NOTIFICATION_KINDS &&
            event.pubKey != userPubKeyHex &&
            event.isTaggedUser(userPubKeyHex)
    }
}

/**
 * Search feed: notes matching a text query (content search).
 */
class IosSearchFeedFilter(
    private val query: String,
    private val cache: IosLocalCache,
) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String = "search-$query"

    override fun feed(): List<Note> {
        val lowerQuery = query.lowercase()
        return cache
            .allNotes()
            .filter {
                val event = it.event
                event is TextNoteEvent && event.content.lowercase().contains(lowerQuery)
            }.sortedWith(DefaultFeedOrder)
            .take(limit())
    }

    override fun applyFilter(newItems: Set<Note>): Set<Note> {
        val lowerQuery = query.lowercase()
        return newItems.filterTo(HashSet()) {
            val event = it.event
            event is TextNoteEvent && event.content.lowercase().contains(lowerQuery)
        }
    }

    override fun sort(items: Set<Note>): List<Note> = items.sortedWith(DefaultFeedOrder)

    override fun limit(): Int = 500
}

/**
 * Hashtag feed: text notes tagged with a specific hashtag ("t" tag).
 */
class IosHashtagFeedFilter(
    private val hashtag: String,
    private val cache: IosLocalCache,
) : AdditiveFeedFilter<Note>() {
    private val lowerHashtag = hashtag.lowercase()

    override fun feedKey(): String = "hashtag-$lowerHashtag"

    override fun feed(): List<Note> =
        cache
            .allNotes()
            .filter { isHashtagNote(it) }
            .sortedWith(DefaultFeedOrder)
            .take(limit())

    override fun applyFilter(newItems: Set<Note>): Set<Note> = newItems.filterTo(HashSet()) { isHashtagNote(it) }

    override fun sort(items: Set<Note>): List<Note> = items.sortedWith(DefaultFeedOrder)

    override fun limit(): Int = 500

    private fun isHashtagNote(note: Note): Boolean {
        val event = note.event ?: return false
        return event is TextNoteEvent && event.isTaggedHash(lowerHashtag)
    }
}

/**
 * Followed-hashtags feed: text notes tagged with any of the user's followed hashtags.
 * Uses NIP-51 kind 10015 HashtagListEvent.
 */
class IosFollowedHashtagsFeedFilter(
    private val cache: IosLocalCache,
    private val followedHashtags: () -> Set<String>,
) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String = "followed-hashtags-${followedHashtags().hashCode()}"

    override fun feed(): List<Note> {
        val tags = followedHashtags()
        if (tags.isEmpty()) return emptyList()
        return cache
            .allNotes()
            .filter { isFollowedHashtagNote(it, tags) }
            .sortedWith(DefaultFeedOrder)
            .take(limit())
    }

    override fun applyFilter(newItems: Set<Note>): Set<Note> {
        val tags = followedHashtags()
        if (tags.isEmpty()) return emptySet()
        return newItems.filterTo(HashSet()) { isFollowedHashtagNote(it, tags) }
    }

    override fun sort(items: Set<Note>): List<Note> = items.sortedWith(DefaultFeedOrder)

    override fun limit(): Int = 500

    private fun isFollowedHashtagNote(
        note: Note,
        tags: Set<String>,
    ): Boolean {
        val event = note.event ?: return false
        if (event !is TextNoteEvent) return false
        return tags.any { event.isTaggedHash(it) }
    }
}

/**
 * Trending feed: notes with the most engagement (reactions + reposts + replies + zaps).
 * A simple local heuristic — ranks cached notes by interaction count.
 */
class IosTrendingFeedFilter(
    private val cache: IosLocalCache,
) : FeedFilter<Note>() {
    override fun feedKey(): String = "trending"

    override fun feed(): List<Note> {
        val now =
            com.vitorpamplona.quartz.utils
                .currentTimeSeconds()
        val oneDayAgo = now - 86400L

        return cache
            .allNotes()
            .filter {
                val event = it.event
                event is TextNoteEvent && (event.createdAt) >= oneDayAgo
            }.sortedByDescending { it.countReactions() + it.replies.size + it.boosts.size + it.zaps.size }
            .take(limit())
    }

    override fun limit(): Int = 200
}

/**
 * Community posts feed: text notes tagged with a specific community address (NIP-72).
 */
class IosCommunityPostsFeedFilter(
    private val communityAddressId: String,
    private val cache: IosLocalCache,
) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String = "community-posts-$communityAddressId"

    override fun feed(): List<Note> =
        cache
            .allNotes()
            .filter { isCommunityPost(it) }
            .sortedWith(DefaultFeedOrder)
            .take(limit())

    override fun applyFilter(newItems: Set<Note>): Set<Note> = newItems.filterTo(HashSet()) { isCommunityPost(it) }

    override fun sort(items: Set<Note>): List<Note> = items.sortedWith(DefaultFeedOrder)

    override fun limit(): Int = 500

    private fun isCommunityPost(note: Note): Boolean {
        val event = note.event ?: return false
        return event is TextNoteEvent && event.isForCommunity(communityAddressId)
    }
}

/**
 * Live activities feed: kind 30311 events, sorted by status (live first) then createdAt.
 */
class IosLiveActivitiesFeedFilter(
    private val cache: IosLocalCache,
) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String = "live-activities"

    override fun feed(): List<Note> =
        cache
            .allNotes()
            .filter { it.event is LiveActivitiesEvent }
            .sortedWith(liveFirstOrder)
            .take(limit())

    override fun applyFilter(newItems: Set<Note>): Set<Note> = newItems.filterTo(HashSet()) { it.event is LiveActivitiesEvent }

    override fun sort(items: Set<Note>): List<Note> = items.sortedWith(liveFirstOrder)

    override fun limit(): Int = 200

    companion object {
        /** Sort live streams first, then planned, then ended; within each group by createdAt desc. */
        private val liveFirstOrder =
            compareBy<Note> {
                when ((it.event as? LiveActivitiesEvent)?.status()) {
                    StatusTag.STATUS.LIVE -> 0
                    StatusTag.STATUS.PLANNED -> 1
                    StatusTag.STATUS.ENDED -> 2
                    null -> 3
                }
            }.thenByDescending { it.createdAt() ?: 0L }
    }
}
