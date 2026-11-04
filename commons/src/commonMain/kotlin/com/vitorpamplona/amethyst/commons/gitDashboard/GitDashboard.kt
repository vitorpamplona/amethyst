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
package com.vitorpamplona.amethyst.commons.gitDashboard

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.gitRepo.dal.isFiledUnder
import com.vitorpamplona.amethyst.commons.gitRepo.dal.isSeriesRoot
import com.vitorpamplona.amethyst.commons.gitRepo.dal.repositoryCoordinateSet
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.EventCache
import com.vitorpamplona.amethyst.commons.model.nip34Git.GitRepository
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.tags.people.isTaggedUser
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.patch.GitPatchEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestEvent
import com.vitorpamplona.quartz.nip34Git.reply.GitReplyEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent

/** A repository row of the dashboard: the announcement it opens and when anything last happened in it. */
@Immutable
class GitDashboardRepository(
    val note: AddressableNote,
    val lastActivity: Long,
)

/** Why an item is in the inbox, checked in this order. */
enum class GitInvolvement { AUTHOR, PARTICIPANT, WATCHING, MENTIONED }

/**
 * One issue, patch or PR in the inbox: everything that happened to it, folded into one row, as a
 * forge's inbox groups a thread. [unread] counts what others did since the reader last opened it.
 */
@Immutable
class GitInboxEntry(
    val item: Note,
    val lastActivity: Long,
    val unread: Int,
    val involvement: GitInvolvement,
    /** When the reader last authored or commented on it; 0 when never. */
    val myLastActivity: Long,
)

/**
 * The git dashboard's sections, computed from what [cache] holds: no relay work here. Its
 * subscriptions load the reader's repositories and the items in them; this only sorts them.
 */
object GitDashboard {
    /**
     * Repositories [me] announced or maintains: an announcement of theirs, or one that lists them
     * as a maintainer (role tags or the legacy list). One row per repository, opening the reader's
     * own announcement when they have one.
     */
    fun myRepositories(
        me: HexKey,
        cache: EventCache,
    ): List<GitDashboardRepository> =
        cache.gitRepositories
            .mapNotNull { _, repository ->
                val announcements = repository.announcements()
                val mine = announcements[me]
                val shown =
                    mine ?: announcements.values.firstOrNull { me in it.maintainers() || it.roleRecords().any { record -> record.subject == me && record.isActive } }
                shown?.let { GitDashboardRepository(cache.getOrCreateAddressableNote(it.address()), lastActivity(repository, it)) }
            }.sortedByDescending { it.lastActivity }

    /** The repositories in the reader's follow list (kind 10018), most recently active first. */
    fun followedRepositories(
        addresses: Set<Address>,
        cache: EventCache,
    ): List<GitDashboardRepository> =
        addresses
            .map { address ->
                val note = cache.getOrCreateAddressableNote(address)
                val event = note.event as? GitRepositoryEvent
                val repository = cache.getGitRepositoryIfExists(address.dTag)
                GitDashboardRepository(note, if (repository != null && event != null) lastActivity(repository, event) else 0L)
            }.sortedByDescending { it.lastActivity }

    /**
     * The issues, patches and PRs the reader should hear about, one row each, most recently active
     * first: everything filed in [watched] repositories, and anywhere else what they wrote, took part
     * in or were tagged on. [lastRead] gives when the reader last opened an item.
     */
    fun inbox(
        me: HexKey,
        watched: Collection<Address>,
        cache: EventCache,
        lastRead: (Note) -> Long,
        limit: Int = 100,
    ): List<GitInboxEntry> {
        val coordinates = watched.flatMapTo(HashSet()) { repositoryCoordinateSet(it) }
        val entries = HashMap<HexKey, GitInboxEntry>()

        cache.gitRepositories.forEach { _, repository ->
            repository.items.forEach { _, item ->
                if (entries.containsKey(item.idHex)) return@forEach
                val event = item.event ?: return@forEach
                if (!isListedItem(event)) return@forEach

                val involvement =
                    when {
                        event.pubKey == me -> GitInvolvement.AUTHOR
                        item.replies.any { it.author?.pubkeyHex == me } -> GitInvolvement.PARTICIPANT
                        // Before the tag: an item p-tags its repository's maintainers, so in a watched
                        // repository the tag says only that it was filed there.
                        isFiledUnder(event, coordinates) -> GitInvolvement.WATCHING
                        event.isTaggedUser(me) -> GitInvolvement.MENTIONED
                        else -> null
                    } ?: return@forEach

                entries[item.idHex] = entryOf(item, me, involvement, lastRead(item))
            }
        }

        return entries.values.sortedByDescending { it.lastActivity }.take(limit)
    }

    /** What the reader wrote or answered, by when they last did: where to pick the work back up. */
    fun recentWork(
        inbox: List<GitInboxEntry>,
        limit: Int = 10,
    ): List<GitInboxEntry> =
        inbox
            .filter { it.myLastActivity > 0 }
            .sortedByDescending { it.myLastActivity }
            .take(limit)

    private fun entryOf(
        item: Note,
        me: HexKey,
        involvement: GitInvolvement,
        readAt: Long,
    ): GitInboxEntry {
        val created = item.createdAt() ?: 0L
        var last = created
        var unread = if (created > readAt && item.author?.pubkeyHex != me) 1 else 0
        var mine = if (item.author?.pubkeyHex == me) created else 0L
        item.replies.forEach { reply ->
            val at = reply.createdAt() ?: return@forEach
            if (at > last) last = at
            val byMe = reply.author?.pubkeyHex == me
            if (byMe && isConversation(reply) && at > mine) mine = at
            if (!byMe && at > readAt) unread++
        }
        return GitInboxEntry(item, last, unread, involvement, mine)
    }

    /** Issues, PRs and series-root patches: the items a repository lists. */
    private fun isListedItem(event: Event): Boolean = event is GitIssueEvent || event is GitPullRequestEvent || (event is GitPatchEvent && event.isSeriesRoot())

    private fun isConversation(reply: Note): Boolean = reply.event is CommentEvent || reply.event is GitReplyEvent

    private fun lastActivity(
        repository: GitRepository,
        announcement: GitRepositoryEvent,
    ): Long {
        val coordinates = repositoryCoordinateSet(announcement.address())
        var last = announcement.createdAt
        repository.items.forEach { _, item ->
            val event = item.event ?: return@forEach
            if (event.createdAt > last && isFiledUnder(event, coordinates)) last = event.createdAt
        }
        return last
    }
}
