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

import com.vitorpamplona.amethyst.commons.nip34Git.coverNote.GitCoverNotes
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip34Git.coverNote.GitCoverNoteEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class GitCoverNotesTest {
    private val sig = "0".repeat(128)
    private val root = "a".repeat(64)
    private val otherRoot = "b".repeat(64)
    private val author = "1".repeat(64)
    private val owner = "2".repeat(64)
    private val maintainer = "3".repeat(64)
    private val stranger = "4".repeat(64)

    private val authorised = setOf(author, owner, maintainer)

    private fun note(
        id: String,
        pubKey: String,
        createdAt: Long,
        target: String = root,
        marker: Boolean = true,
    ): GitCoverNoteEvent {
        val eTag = if (marker) arrayOf("e", target, "", "root") else arrayOf("e", target)
        return GitCoverNoteEvent(id, pubKey, createdAt, arrayOf(eTag, arrayOf("p", author), arrayOf("k", "1618")), "**note** $id", sig)
    }

    @Test
    fun latestAuthorisedNoteWins() {
        val old = note("1".repeat(64), author, 100)
        val newer = note("2".repeat(64), maintainer, 200)

        assertSame(newer, GitCoverNotes.latestAuthorised(listOf(newer, old), root, authorised))
    }

    @Test
    fun notesByOtherPubkeysAreIgnoredEvenWhenNewer() {
        val mine = note("1".repeat(64), author, 100)
        val spam = note("2".repeat(64), stranger, 900)

        assertSame(mine, GitCoverNotes.latestAuthorised(listOf(spam, mine), root, authorised))
        assertNull(GitCoverNotes.latestAuthorised(listOf(spam), root, authorised))
    }

    @Test
    fun tiesAreBrokenByTheHigherEventId() {
        val low = note("1".repeat(64), author, 100)
        val high = note("f".repeat(64), owner, 100)

        assertSame(high, GitCoverNotes.latestAuthorised(listOf(low, high), root, authorised))
        assertSame(high, GitCoverNotes.latestAuthorised(listOf(high, low), root, authorised))
    }

    @Test
    fun onlyNotesForThisItemCount() {
        val elsewhere = note("1".repeat(64), author, 900, target = otherRoot)
        val here = note("2".repeat(64), author, 100)

        assertSame(here, GitCoverNotes.latestAuthorised(listOf(elsewhere, here), root, authorised))
    }

    @Test
    fun unmarkedRootTagSeenOnRelaysStillResolves() {
        val unmarked = note("1".repeat(64), author, 100, marker = false)

        assertSame(unmarked, GitCoverNotes.latestAuthorised(listOf(unmarked), root, authorised))
    }

    @Test
    fun authorisedAuthorsAreTheItemAuthorTheOwnerAndTheMaintainers() {
        val address = Address(GitRepositoryEvent.KIND, owner, "repo")
        val repo =
            GitRepositoryEvent(
                "9".repeat(64),
                owner,
                1,
                arrayOf(arrayOf("d", "repo"), arrayOf("maintainers", maintainer)),
                "",
                sig,
            )

        assertEquals(setOf(author, owner), GitCoverNotes.authorisedAuthors(author, address, null))
        assertEquals(setOf(author, owner, maintainer), GitCoverNotes.authorisedAuthors(author, address, repo))
        assertEquals(setOf(author), GitCoverNotes.authorisedAuthors(author, null, null))
    }
}
