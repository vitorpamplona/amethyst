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

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.ui.feeds.AdditiveFeedFilter
import com.vitorpamplona.amethyst.commons.ui.feeds.DefaultFeedOrder
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedFilter
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.tags.people.isTaggedUser
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent

/**
 * Global feed: all kind 1 text notes, sorted by createdAt desc.
 */
class DesktopGlobalFeedFilter(
    private val cache: DesktopLocalCache,
) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String = "global"

    override fun feed(): List<Note> =
        cache.notes
            .filterIntoSet { _, note -> note.event is TextNoteEvent }
            .sortedWith(DefaultFeedOrder)
            .take(limit())

    override fun applyFilter(newItems: Set<Note>): Set<Note> = newItems.filterTo(HashSet()) { it.event is TextNoteEvent }

    override fun sort(items: Set<Note>): List<Note> = items.sortedWith(DefaultFeedOrder)

    override fun limit(): Int = 2500
}

/**
 * Following feed: kind 1 text notes from followed pubkeys.
 */
class DesktopFollowingFeedFilter(
    private val cache: DesktopLocalCache,
    private val followedPubkeys: () -> Set<HexKey>,
) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String = "following-${followedPubkeys().hashCode()}"

    override fun feed(): List<Note> {
        val follows = followedPubkeys()
        return cache.notes
            .filterIntoSet { _, note ->
                note.event is TextNoteEvent && note.author?.pubkeyHex in follows
            }.sortedWith(DefaultFeedOrder)
            .take(limit())
    }

    override fun applyFilter(newItems: Set<Note>): Set<Note> {
        val follows = followedPubkeys()
        return newItems.filterTo(HashSet()) {
            it.event is TextNoteEvent && it.author?.pubkeyHex in follows
        }
    }

    override fun sort(items: Set<Note>): List<Note> = items.sortedWith(DefaultFeedOrder)

    override fun limit(): Int = 2500
}

/**
 * Thread feed: root note + all replies (graph walk via Note.replies).
 */
class DesktopThreadFilter(
    private val noteId: HexKey,
    private val cache: DesktopLocalCache,
) : FeedFilter<Note>() {
    override fun feedKey(): String = "thread-$noteId"

    override fun feed(): List<Note> {
        val root = cache.getNoteIfExists(noteId) ?: return emptyList()
        val result = mutableListOf(root)
        collectReplies(root, result)
        return result.sortedWith(compareBy { it.createdAt() ?: 0 })
    }

    private fun collectReplies(
        note: Note,
        result: MutableList<Note>,
    ) {
        for (reply in note.replies) {
            if (reply !in result) {
                result.add(reply)
                collectReplies(reply, result)
            }
        }
    }

    override fun limit(): Int = Int.MAX_VALUE
}

/**
 * Profile feed: all kind 1 notes by a specific pubkey.
 */
class DesktopProfileFeedFilter(
    private val pubkey: HexKey,
    private val cache: DesktopLocalCache,
) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String = "profile-$pubkey"

    override fun feed(): List<Note> =
        cache.notes
            .filterIntoSet { _, note ->
                note.event is TextNoteEvent && note.author?.pubkeyHex == pubkey
            }.sortedWith(DefaultFeedOrder)
            .take(limit())

    override fun applyFilter(newItems: Set<Note>): Set<Note> =
        newItems.filterTo(HashSet()) {
            it.event is TextNoteEvent && it.author?.pubkeyHex == pubkey
        }

    override fun sort(items: Set<Note>): List<Note> = items.sortedWith(DefaultFeedOrder)

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
) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String = "reads"

    override fun feed(): List<Note> =
        cache.notes
            .filterIntoSet { _, note -> note.event is LongTextNoteEvent }
            .sortedWith(DefaultFeedOrder)
            .take(limit())

    override fun applyFilter(newItems: Set<Note>): Set<Note> = newItems.filterTo(HashSet()) { it.event is LongTextNoteEvent }

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
            event.isTaggedUser(userPubKeyHex)
    }
}

/**
 * Search feed: notes matching a text query (content search).
 * Results are populated by relay search subscriptions that route through cache.
 */
class DesktopSearchFeedFilter(
    private val query: String,
    private val cache: DesktopLocalCache,
) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String = "search-$query"

    override fun feed(): List<Note> {
        val lowerQuery = query.lowercase()
        return cache.notes
            .filterIntoSet { _, note ->
                val event = note.event ?: return@filterIntoSet false
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
