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
package com.vitorpamplona.amethyst.desktop.feeds

import com.vitorpamplona.amethyst.commons.feeds.custom.FeedSource
import com.vitorpamplona.amethyst.commons.model.LiveHiddenUsers
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.ui.feeds.AdditiveFeedFilter
import com.vitorpamplona.amethyst.commons.ui.feeds.DefaultFeedOrder
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedFilter
import com.vitorpamplona.amethyst.commons.ui.feeds.isRenderableRepost
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.tags.people.isTaggedUser
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent

private fun isFeedNote(event: Event?): Boolean =
    event is TextNoteEvent ||
        event is PollEvent ||
        event.isRenderableRepost()

private fun List<Note>.deduplicateReposts(): List<Note> =
    distinctBy { note ->
        val event = note.event
        if (event is RepostEvent || event is GenericRepostEvent) {
            note.replyTo?.lastOrNull()?.idHex ?: note.idHex
        } else {
            note.idHex
        }
    }

/**
 * Global feed: kind 1 text notes + kind 6/16 reposts, sorted by createdAt desc.
 */
class DesktopGlobalFeedFilter(
    private val cache: DesktopLocalCache,
    private val hidden: () -> LiveHiddenUsers = { LiveHiddenUsers.EMPTY },
) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String = "global"

    override fun feed(): List<Note> =
        cache.notes
            .filterIntoSet { _, note -> isFeedNote(note.event) && !note.isHiddenFor(hidden()) }
            .sortedWith(DefaultFeedOrder)
            .deduplicateReposts()
            .take(limit())

    override fun applyFilter(newItems: Set<Note>): Set<Note> = newItems.filterTo(HashSet()) { isFeedNote(it.event) && !it.isHiddenFor(hidden()) }

    override fun sort(items: Set<Note>): List<Note> = items.sortedWith(DefaultFeedOrder).deduplicateReposts()

    override fun limit(): Int = 2500
}

/**
 * Following feed: kind 1 text notes + kind 6/16 reposts from followed pubkeys.
 */
class DesktopFollowingFeedFilter(
    private val cache: DesktopLocalCache,
    private val hidden: () -> LiveHiddenUsers = { LiveHiddenUsers.EMPTY },
    private val followedPubkeys: () -> Set<HexKey>,
) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String = "following-${followedPubkeys().hashCode()}"

    override fun feed(): List<Note> {
        val follows = followedPubkeys()
        return cache.notes
            .filterIntoSet { _, note ->
                isFeedNote(note.event) && note.author?.pubkeyHex in follows && !note.isHiddenFor(hidden())
            }.sortedWith(DefaultFeedOrder)
            .deduplicateReposts()
            .take(limit())
    }

    override fun applyFilter(newItems: Set<Note>): Set<Note> {
        val follows = followedPubkeys()
        return newItems.filterTo(HashSet()) {
            isFeedNote(it.event) && it.author?.pubkeyHex in follows && !it.isHiddenFor(hidden())
        }
    }

    override fun sort(items: Set<Note>): List<Note> = items.sortedWith(DefaultFeedOrder).deduplicateReposts()

    override fun limit(): Int = 2500
}

/**
 * Custom feed filter: matches events based on FeedSource.Filter criteria.
 * Excludes are applied client-side (relay can't express NOT filters).
 */
class DesktopCustomFeedFilter(
    private val cache: DesktopLocalCache,
    private val feedId: String,
    private val source: FeedSource.Filter,
    private val hidden: () -> LiveHiddenUsers = { LiveHiddenUsers.EMPTY },
) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String = "custom-$feedId"

    private fun matchesSource(note: Note): Boolean {
        val event = note.event ?: return false
        if (!isFeedNote(event)) return false
        if (note.isHiddenFor(hidden())) return false

        // Kind filter
        if (source.kinds.isNotEmpty() && event.kind !in source.kinds) return false

        // Author filter (if specified, note must be from one of these authors)
        if (source.authors.isNotEmpty() && note.author?.pubkeyHex !in source.authors) return false

        // Hashtag filter (if specified, event must contain at least one)
        if (source.hashtags.isNotEmpty()) {
            val eventTags =
                event.tags
                    .filter { it.size >= 2 && it[0] == "t" }
                    .map { it[1].lowercase() }
            if (source.hashtags.none { it.lowercase() in eventTags }) return false
        }

        // Exclusions
        if (source.excludeAuthors.isNotEmpty() && note.author?.pubkeyHex in source.excludeAuthors) return false
        if (source.excludeKeywords.isNotEmpty()) {
            val content = event.content.lowercase()
            if (source.excludeKeywords.any { content.contains(it.lowercase()) }) return false
        }

        return true
    }

    override fun feed(): List<Note> =
        cache.notes
            .filterIntoSet { _, note -> matchesSource(note) }
            .sortedWith(DefaultFeedOrder)
            .deduplicateReposts()
            .take(limit())

    override fun applyFilter(newItems: Set<Note>): Set<Note> = newItems.filterTo(HashSet()) { matchesSource(it) }

    override fun sort(items: Set<Note>): List<Note> = items.sortedWith(DefaultFeedOrder).deduplicateReposts()

    override fun limit(): Int = 2500
}

/**
 * Thread feed: root note + all replies (graph walk via Note.replies).
 */
class DesktopThreadFilter(
    private val noteId: HexKey,
    private val cache: DesktopLocalCache,
    private val hidden: () -> LiveHiddenUsers = { LiveHiddenUsers.EMPTY },
) : FeedFilter<Note>() {
    override fun feedKey(): String = "thread-$noteId"

    override fun feed(): List<Note> {
        val root = cache.getNoteIfExists(noteId) ?: return emptyList()
        // Use LinkedHashSet for O(1) containment checks (was O(R) with MutableList)
        val seen = LinkedHashSet<Note>()
        // The thread root is always shown even if muted — the user explicitly
        // navigated into it. Replies by muted/blocked authors are still hidden.
        seen.add(root)
        collectReplies(root, seen)
        return seen.sortedWith(compareBy { it.createdAt() ?: 0L })
    }

    private fun collectReplies(
        note: Note,
        seen: LinkedHashSet<Note>,
    ) {
        val choices = hidden()
        for (reply in note.replies) {
            if (reply.isHiddenFor(choices)) continue
            if (seen.add(reply)) {
                collectReplies(reply, seen)
            }
        }
    }

    override fun limit(): Int = Int.MAX_VALUE
}

/**
 * Profile feed: text notes + reposts by a specific pubkey.
 *
 * When [repliesOnly] is true, the filter switches to "Replies" mode:
 * only the pubkey's reply posts — NIP-22 [CommentEvent]s and NIP-10
 * [TextNoteEvent]s carrying an explicit `reply`/`root` marker. Plain
 * unmarked e-tags don't count: modern clients use those for quotes
 * and mentions, and `Note.isNewThread()` (which is what Android's
 * conversations feed checks) would let those through as "replies".
 */
class DesktopProfileFeedFilter(
    private val pubkey: HexKey,
    private val cache: DesktopLocalCache,
    private val repliesOnly: Boolean = false,
    private val hidden: () -> LiveHiddenUsers = { LiveHiddenUsers.EMPTY },
) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String = if (repliesOnly) "profile-$pubkey-replies" else "profile-$pubkey"

    private fun isReply(event: Event): Boolean =
        when (event) {
            is CommentEvent -> true
            is TextNoteEvent -> event.markedReply() != null || event.markedRoot() != null
            else -> false
        }

    private fun isProfileNote(note: Note): Boolean {
        val event = note.event ?: return false
        if (note.author?.pubkeyHex != pubkey) return false
        if (note.isHiddenFor(hidden())) return false
        return if (repliesOnly) {
            isReply(event)
        } else {
            isFeedNote(event)
        }
    }

    override fun feed(): List<Note> =
        cache.notes
            .filterIntoSet { _, note -> isProfileNote(note) }
            .sortedWith(DefaultFeedOrder)
            .deduplicateReposts()
            .take(limit())

    override fun applyFilter(newItems: Set<Note>): Set<Note> = newItems.filterTo(HashSet()) { isProfileNote(it) }

    override fun sort(items: Set<Note>): List<Note> = items.sortedWith(DefaultFeedOrder).deduplicateReposts()

    override fun limit(): Int = 1000
}

/**
 * Bookmark feed: notes by ID set (from BookmarkListEvent).
 */
class DesktopBookmarkFeedFilter(
    private val bookmarkedIds: () -> Set<HexKey>,
    private val cache: DesktopLocalCache,
) : FeedFilter<Note>() {
    override fun feedKey(): String = "bookmarks-${bookmarkedIds().hashCode()}"

    override fun feed(): List<Note> =
        bookmarkedIds()
            .mapNotNull { cache.getNoteIfExists(it) }
            .filter { it.event != null }
            .sortedWith(DefaultFeedOrder)
            .take(limit())

    override fun limit(): Int = 2500
}

/**
 * Reads feed: kind 30023 long-form content.
 */
class DesktopReadsFeedFilter(
    private val cache: DesktopLocalCache,
    private val hidden: () -> LiveHiddenUsers = { LiveHiddenUsers.EMPTY },
) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String = "reads"

    override fun feed(): List<Note> =
        cache.notes
            .filterIntoSet { _, note -> note.event is LongTextNoteEvent && !note.isHiddenFor(hidden()) }
            .sortedWith(DefaultFeedOrder)
            .take(limit())

    override fun applyFilter(newItems: Set<Note>): Set<Note> = newItems.filterTo(HashSet()) { it.event is LongTextNoteEvent && !it.isHiddenFor(hidden()) }

    override fun sort(items: Set<Note>): List<Note> = items.sortedWith(DefaultFeedOrder)

    override fun limit(): Int = 500
}

/**
 * Notification feed: events that tag the logged-in user.
 * Includes reactions, zaps, replies, reposts targeting the user's notes.
 */
class DesktopNotificationFeedFilter(
    private val userPubKeyHex: HexKey,
    private val cache: DesktopLocalCache,
    private val hidden: () -> LiveHiddenUsers = { LiveHiddenUsers.EMPTY },
) : AdditiveFeedFilter<Note>() {
    companion object {
        val NOTIFICATION_KINDS =
            setOf(
                TextNoteEvent.KIND,
                ReactionEvent.KIND,
                LnZapEvent.KIND,
            )
    }

    override fun feedKey(): String = "notifications-$userPubKeyHex"

    override fun feed(): List<Note> =
        cache.notes
            .filterIntoSet { _, note -> isNotificationForUser(note) }
            .sortedWith(DefaultFeedOrder)
            .take(limit())

    override fun applyFilter(newItems: Set<Note>): Set<Note> = newItems.filterTo(HashSet()) { isNotificationForUser(it) }

    override fun sort(items: Set<Note>): List<Note> = items.sortedWith(DefaultFeedOrder)

    override fun limit(): Int = 2500

    private fun isNotificationForUser(note: Note): Boolean {
        val event = note.event ?: return false
        return event.kind in NOTIFICATION_KINDS &&
            event.pubKey != userPubKeyHex &&
            event.isTaggedUser(userPubKeyHex) &&
            !note.isHiddenFor(hidden())
    }
}

/**
 * Search feed: notes matching a text query (content search).
 * Results are populated by relay search subscriptions that route through cache.
 */
class DesktopSearchFeedFilter(
    private val query: String,
    private val cache: DesktopLocalCache,
    private val hidden: () -> LiveHiddenUsers = { LiveHiddenUsers.EMPTY },
) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String = "search-$query"

    override fun feed(): List<Note> {
        val lowerQuery = query.lowercase()
        return cache.notes
            .filterIntoSet { _, note ->
                val event = note.event ?: return@filterIntoSet false
                event is TextNoteEvent && event.content.lowercase().contains(lowerQuery) && !note.isHiddenFor(hidden())
            }.sortedWith(DefaultFeedOrder)
            .take(limit())
    }

    override fun applyFilter(newItems: Set<Note>): Set<Note> {
        val lowerQuery = query.lowercase()
        return newItems.filterTo(HashSet()) {
            val event = it.event
            event is TextNoteEvent && event.content.lowercase().contains(lowerQuery) && !it.isHiddenFor(hidden())
        }
    }

    override fun sort(items: Set<Note>): List<Note> = items.sortedWith(DefaultFeedOrder)

    override fun limit(): Int = 500
}
