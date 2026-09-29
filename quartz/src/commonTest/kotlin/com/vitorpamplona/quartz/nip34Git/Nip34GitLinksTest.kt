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
package com.vitorpamplona.quartz.nip34Git

import com.vitorpamplona.quartz.nip01Core.links.Link
import com.vitorpamplona.quartz.nip01Core.links.LinkTarget
import com.vitorpamplona.quartz.nip01Core.links.Relation
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.patch.GitPatchEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestUpdateEvent
import com.vitorpamplona.quartz.nip34Git.reply.GitReplyEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusAppliedEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusOpenEvent
import com.vitorpamplona.quartz.utils.Hex
import kotlin.test.Test
import kotlin.test.assertEquals

class Nip34GitLinksTest {
    private val id = "0".repeat(64)
    private val author = "1".repeat(64)
    private val sig = "0".repeat(128)
    private val owner = "a".repeat(64)
    private val other = "b".repeat(64)
    private val rootAuthor = "c".repeat(64)
    private val revisionAuthor = "d".repeat(64)
    private val target = "2".repeat(64)
    private val revision = "3".repeat(64)
    private val previous = "4".repeat(64)
    private val quoted = "5".repeat(64)
    private val repo = "30617:$owner:amethyst"

    @Test
    fun patchTellsTheRepositoryOwnerFromOtherPeople() {
        val event =
            GitPatchEvent(
                id,
                author,
                1,
                arrayOf(
                    arrayOf("a", repo),
                    arrayOf("r", "euc-commit"),
                    arrayOf("p", owner),
                    arrayOf("p", other),
                    arrayOf("p", "not-a-key"),
                    arrayOf("e", previous, "", "reply", other),
                    arrayOf("e", target, "", "root"),
                    arrayOf("t", "Root-Revision"),
                ),
                "From 123 Mon Sep 17 00:00:00 2001",
                sig,
            )
        assertEquals(
            listOf(
                Link(Relation.REPOSITORY, LinkTarget.Address(repo), "a"),
                Link(Relation.REPOSITORY_OWNER, LinkTarget.User(owner), "p"),
                Link(Relation.MENTION, LinkTarget.User(other), "p"),
                Link(Relation.PARENT, LinkTarget.Event(previous), "e"),
                Link(Relation.ROOT, LinkTarget.Event(target), "e"),
                Link(Relation.HASHTAG, LinkTarget.Tag("t", "root-revision"), "t"),
                Link(Relation.TAG, LinkTarget.Tag("r", "euc-commit"), "r"),
            ),
            event.links(),
        )
    }

    @Test
    fun pullRequestRevisesItsRootPatch() {
        val event =
            GitPullRequestEvent(
                id,
                author,
                1,
                arrayOf(
                    arrayOf("a", repo),
                    arrayOf("p", owner),
                    arrayOf("e", target),
                    arrayOf("t", "bug"),
                    arrayOf("r", "euc-commit"),
                    arrayOf("c", "tip-commit"),
                ),
                "",
                sig,
            )
        assertEquals(
            listOf(
                Link(Relation.REPOSITORY, LinkTarget.Address(repo), "a"),
                Link(Relation.REPOSITORY_OWNER, LinkTarget.User(owner), "p"),
                Link(Relation.REVISED, LinkTarget.Event(target), "e"),
                Link(Relation.HASHTAG, LinkTarget.Tag("t", "bug"), "t"),
                Link(Relation.TAG, LinkTarget.Tag("r", "euc-commit"), "r"),
            ),
            event.links(),
        )
    }

    @Test
    fun pullRequestUpdateRootsAtThePullRequest() {
        val event =
            GitPullRequestUpdateEvent(
                id,
                author,
                1,
                arrayOf(
                    arrayOf("E", target),
                    arrayOf("P", rootAuthor),
                    arrayOf("a", repo),
                    arrayOf("p", owner),
                    arrayOf("p", other),
                    arrayOf("r", "euc-commit"),
                ),
                "",
                sig,
            )
        assertEquals(
            listOf(
                Link(Relation.ROOT, LinkTarget.Event(target), "E"),
                Link(Relation.ROOT_AUTHOR, LinkTarget.User(rootAuthor), "P"),
                Link(Relation.REPOSITORY, LinkTarget.Address(repo), "a"),
                Link(Relation.REPOSITORY_OWNER, LinkTarget.User(owner), "p"),
                Link(Relation.MENTION, LinkTarget.User(other), "p"),
                Link(Relation.TAG, LinkTarget.Tag("r", "euc-commit"), "r"),
            ),
            event.links(),
        )
    }

    @Test
    fun issueQuotesAndCites() {
        val npub = Hex.decode(other).toNpub()
        val event =
            GitIssueEvent(
                id,
                author,
                1,
                arrayOf(arrayOf("subject", "Crash"), arrayOf("a", repo), arrayOf("p", owner), arrayOf("q", quoted), arrayOf("t", "Bug")),
                "as nostr:$npub said",
                sig,
            )
        assertEquals(
            listOf(
                Link(Relation.REPOSITORY, LinkTarget.Address(repo), "a"),
                Link(Relation.REPOSITORY_OWNER, LinkTarget.User(owner), "p"),
                Link(Relation.QUOTE, LinkTarget.Event(quoted), "q"),
                Link(Relation.HASHTAG, LinkTarget.Tag("t", "bug"), "t"),
                Link(Relation.MENTION, LinkTarget.User(other), Link.VIA_CONTENT),
            ),
            event.links(),
        )
    }

    @Suppress("DEPRECATION")
    @Test
    fun replyWithOnlyARootRepliesToTheRoot() {
        val event = GitReplyEvent(id, author, 1, arrayOf(arrayOf("a", repo), arrayOf("e", target, "", "root"), arrayOf("p", other)), "lgtm", sig)
        assertEquals(
            listOf(
                Link(Relation.REPOSITORY, LinkTarget.Address(repo), "a"),
                Link(Relation.ROOT, LinkTarget.Event(target), "e"),
                Link(Relation.PARENT, LinkTarget.Event(target), "e"),
                Link(Relation.MENTION, LinkTarget.User(other), "p"),
            ),
            event.links(),
        )
    }

    @Test
    fun repositoryNamesMaintainersAndTheRepositoryItForks() {
        val upstream = "30617:$other:upstream"
        val event =
            GitRepositoryEvent(
                id,
                owner,
                1,
                arrayOf(
                    arrayOf("d", "amethyst"),
                    arrayOf("maintainers", other, "short"),
                    arrayOf("t", "personal-fork"),
                    arrayOf("r", "euc-commit", "euc"),
                    arrayOf("u", upstream),
                    arrayOf("u", "https://git.example/amethyst.git"),
                ),
                "",
                sig,
            )
        assertEquals(
            listOf(
                Link(Relation.MAINTAINER, LinkTarget.User(other), "maintainers"),
                Link(Relation.HASHTAG, LinkTarget.Tag("t", "personal-fork"), "t"),
                Link(Relation.TAG, LinkTarget.Tag("r", "euc-commit"), "r"),
                Link(Relation.FORK, LinkTarget.Address(upstream), "u"),
            ),
            event.links(),
        )
    }

    @Test
    fun statusTellsItsPeopleApartByTheAuthorsItsTagsName() {
        val event =
            GitStatusOpenEvent(
                id,
                author,
                1,
                arrayOf(
                    arrayOf("e", target, "", "root", rootAuthor),
                    arrayOf("e", revision, "", "reply", revisionAuthor),
                    arrayOf("a", repo),
                    arrayOf("p", owner),
                    arrayOf("p", rootAuthor),
                    arrayOf("p", revisionAuthor),
                    arrayOf("p", other),
                    arrayOf("r", "euc-commit"),
                ),
                "",
                sig,
            )
        assertEquals(
            listOf(
                Link(Relation.ROOT, LinkTarget.Event(target), "e"),
                Link(Relation.PARENT, LinkTarget.Event(revision), "e"),
                Link(Relation.REPOSITORY, LinkTarget.Address(repo), "a"),
                Link(Relation.REPOSITORY_OWNER, LinkTarget.User(owner), "p"),
                Link(Relation.ROOT_AUTHOR, LinkTarget.User(rootAuthor), "p"),
                Link(Relation.PARENT_AUTHOR, LinkTarget.User(revisionAuthor), "p"),
                Link(Relation.MENTION, LinkTarget.User(other), "p"),
                Link(Relation.TAG, LinkTarget.Tag("r", "euc-commit"), "r"),
            ),
            event.links(),
        )
    }

    @Test
    fun appliedStatusNamesTheAppliedPatchesAndAKeyCanHoldTwoRoles() {
        val event =
            GitStatusAppliedEvent(
                id,
                owner,
                1,
                arrayOf(
                    arrayOf("e", target, "", "root", owner),
                    arrayOf("a", repo),
                    arrayOf("p", owner),
                    arrayOf("q", previous, "", owner),
                ),
                "",
                sig,
            )
        assertEquals(
            listOf(
                Link(Relation.ROOT, LinkTarget.Event(target), "e"),
                Link(Relation.REPOSITORY, LinkTarget.Address(repo), "a"),
                Link(Relation.REPOSITORY_OWNER, LinkTarget.User(owner), "p"),
                Link(Relation.ROOT_AUTHOR, LinkTarget.User(owner), "p"),
                Link(Relation.APPLIED, LinkTarget.Event(previous), "q"),
            ),
            event.links(),
        )
    }
}
