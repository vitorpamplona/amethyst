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

import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.patch.GitPatchEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Locks in byte-level interoperability with ngit (github.com/DanConwayDev/ngit-cli)
 * and the NIP-34 spec for the tag shapes that previously diverged:
 *
 *  1. `clone` / `web` are ONE multi-value tag, not repeated single-value tags —
 *     ngit's parser keeps only the last of repeated known tags, so the repeated
 *     form silently dropped URLs across implementations.
 *  2. Issues carry the repository owner as a `p` tag (maintainer routing).
 *  3. Patch/PR `r` tags are plain `["r", <commit>]`; the `"euc"` marker lives
 *     only on the kind-30617 announcement.
 */
class GitNip34InteropTest {
    private val owner = "aa".repeat(32)

    private fun repo(tags: Array<Array<String>>) = GitRepositoryEvent("00", owner, 0, tags, "", "00")

    @Test
    fun announcementEmitsSingleMultiValueCloneAndWeb() {
        val tmpl =
            GitRepositoryEvent.build(
                name = "demo",
                description = null,
                webUrls = listOf("https://a.com", "https://b.com"),
                cloneUrls = listOf("https://a.git", "https://b.git"),
                relays = emptyList(),
                maintainers = emptyList(),
                hashtags = emptyList(),
                earliestUniqueCommit = null,
                dTag = "demo",
            )
        assertEquals(1, tmpl.tags.count { it[0] == "clone" }, "clone must be a single tag")
        assertEquals(1, tmpl.tags.count { it[0] == "web" }, "web must be a single tag")
        assertEquals(listOf("clone", "https://a.git", "https://b.git"), tmpl.tags.first { it[0] == "clone" }.toList())
        assertEquals(listOf("web", "https://a.com", "https://b.com"), tmpl.tags.first { it[0] == "web" }.toList())
    }

    @Test
    fun readsSpecMultiValueForm() {
        val r =
            repo(
                arrayOf(
                    arrayOf("d", "x"),
                    arrayOf("clone", "https://a.git", "https://b.git"),
                    arrayOf("web", "https://a.com", "https://b.com"),
                ),
            )
        assertEquals(listOf("https://a.git", "https://b.git"), r.clones())
        assertEquals(listOf("https://a.com", "https://b.com"), r.webs())
    }

    @Test
    fun readsLegacyRepeatedForm() {
        val r =
            repo(
                arrayOf(
                    arrayOf("d", "x"),
                    arrayOf("clone", "https://a.git"),
                    arrayOf("clone", "https://b.git"),
                    arrayOf("web", "https://a.com"),
                    arrayOf("web", "https://b.com"),
                ),
            )
        assertEquals(listOf("https://a.git", "https://b.git"), r.clones())
        assertEquals(listOf("https://a.com", "https://b.com"), r.webs())
    }

    @Test
    fun issueCarriesRepositoryOwnerPTag() {
        val repoEvent = repo(arrayOf(arrayOf("d", "x"), arrayOf("name", "x")))
        val tmpl = GitIssueEvent.build("subject", "body", EventHintBundle(repoEvent), emptyList(), emptyList())
        val pTags = tmpl.tags.filter { it[0] == "p" }.map { it[1] }
        assertTrue(owner in pTags, "issue must p-tag the repository owner for maintainer routing")
    }

    @Test
    fun patchRTagIsPlainWithoutEucMarker() {
        val repoEvent = repo(arrayOf(arrayOf("d", "x"), arrayOf("r", "rootcommit", "euc")))
        val tmpl =
            GitPatchEvent.build(
                patch = "diff",
                repository = EventHintBundle(repoEvent),
                earliestUniqueCommit = "rootcommit",
                commit = "c1",
            )
        assertEquals(listOf("r", "rootcommit"), tmpl.tags.first { it[0] == "r" }.toList(), "patch r tag must be plain (no euc marker)")
    }
}
