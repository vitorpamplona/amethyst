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
package com.vitorpamplona.amethyst.commons.nip34Git

import com.vitorpamplona.amethyst.commons.model.GitPullRequestUpdateIndex
import com.vitorpamplona.amethyst.commons.model.GitStatusIndex
import com.vitorpamplona.amethyst.commons.nip34Git.authority.GitAuthority
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestUpdateEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryMembership
import com.vitorpamplona.quartz.nip34Git.status.GitStatusClosedEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusOpenEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GitAuthorityTest {
    private val sig = "0".repeat(128)
    private val owner = "1".repeat(64)
    private val coMaintainer = "2".repeat(64)
    private val invitee = "3".repeat(64)
    private val author = "4".repeat(64)
    private val stranger = "5".repeat(64)
    private val itemId = "a".repeat(64)

    private fun repo(
        pubKey: HexKey,
        vararg maintainers: HexKey,
    ) = GitRepositoryEvent(pubKey.take(2).repeat(32), pubKey, 1, arrayOf(arrayOf("d", "din"), arrayOf("maintainers", *maintainers)), "", sig)

    private val announcements =
        listOf(
            repo(owner, coMaintainer, invitee),
            repo(coMaintainer, owner),
            // The invitee never lists anyone back: invited, not confirmed.
            repo(invitee),
        )

    private val authority = GitAuthority(mapOf("din" to GitRepositoryMembership.latestByAuthor(announcements, "din")))

    private val issue = GitIssueEvent(itemId, author, 10, arrayOf(arrayOf("a", "30617:$owner:din")), "bug", sig)

    private fun status(
        id: String,
        pubKey: HexKey,
        createdAt: Long,
        closed: Boolean,
    ): GitStatusEvent {
        val tags = arrayOf(arrayOf("e", itemId, "", "root"))
        return if (closed) GitStatusClosedEvent(id, pubKey, createdAt, tags, "", sig) else GitStatusOpenEvent(id, pubKey, createdAt, tags, "", sig)
    }

    @Test
    fun membersAreTheAuthorAndConfirmedMaintainersOnly() {
        assertEquals(setOf(author, owner, coMaintainer), authority.memberActorsOf(issue))
    }

    @Test
    fun anUnloadedRepositoryFallsBackToItsOwner() {
        assertEquals(setOf(author, owner), GitAuthority.EMPTY.memberActorsOf(issue))
    }

    @Test
    fun aStrangersCloseDoesNotCloseTheIssue() {
        val items = mapOf<HexKey, Event>(itemId to issue)
        val open = status("b".repeat(64), coMaintainer, 20, closed = false)
        val strangerClose = status("c".repeat(64), stranger, 30, closed = true)
        val inviteeClose = status("d".repeat(64), invitee, 40, closed = true)

        val latest = GitStatusIndex.reduceLatestByTarget(listOf(open, strangerClose, inviteeClose), items, authority)
        assertEquals(open, latest[itemId])

        val authorClose = status("e".repeat(64), author, 50, closed = true)
        assertEquals(authorClose, GitStatusIndex.reduceLatestByTarget(listOf(open, strangerClose, authorClose), items, authority)[itemId])
    }

    @Test
    fun statusesWaitForTheirItem() {
        val close = status("b".repeat(64), owner, 20, closed = true)
        assertNull(GitStatusIndex.reduceLatestByTarget(listOf(close), emptyMap(), authority)[itemId])
    }

    @Test
    fun onlyTheAuthorAndMaintainersCanMoveAPullRequest() {
        val pr = GitPullRequestEvent(itemId, author, 10, arrayOf(arrayOf("a", "30617:$owner:din"), arrayOf("c", "aaaaaaa")), "", sig)

        fun update(
            id: String,
            pubKey: HexKey,
            commit: String,
        ) = GitPullRequestUpdateEvent(id, pubKey, 20, arrayOf(arrayOf("E", itemId), arrayOf("c", commit)), "", sig)

        val fromAuthor = update("b".repeat(64), author, "bbbbbbb")
        val fromMaintainer = update("c".repeat(64), coMaintainer, "ccccccc")
        val fromStranger = update("d".repeat(64), stranger, "ddddddd")

        val grouped = GitPullRequestUpdateIndex.groupByParent(listOf(fromStranger, fromAuthor, fromMaintainer), mapOf(itemId to pr), authority)
        val kept = grouped[itemId].orEmpty()
        assertEquals(setOf(fromAuthor, fromMaintainer), kept.toSet())
        assertTrue(fromStranger !in kept)
        assertTrue(GitPullRequestUpdateIndex.groupByParent(listOf(fromAuthor), emptyMap(), authority).isEmpty())
    }
}
