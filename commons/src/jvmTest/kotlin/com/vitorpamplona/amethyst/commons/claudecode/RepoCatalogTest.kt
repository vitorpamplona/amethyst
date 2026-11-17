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
package com.vitorpamplona.amethyst.commons.claudecode

import com.vitorpamplona.amethyst.commons.claudecode.RepoCandidate.Source
import kotlin.test.Test
import kotlin.test.assertEquals

class RepoCatalogTest {
    private val local =
        listOf(
            RepoCandidate("https://github.com/o/app.git", "o/app", null, Source.LOCAL, uses = 5),
            RepoCandidate("https://github.com/o/lib.git", "o/lib", null, Source.LOCAL, uses = 1),
        )
    private val github =
        listOf(
            RepoCandidate("https://github.com/o/app.git", "o/app", "The app", Source.GITHUB),
            RepoCandidate("https://github.com/o/docs.git", "o/docs", "Documentation", Source.GITHUB),
        )
    private val nostrRef = NostrRepoRef("a".repeat(64), "app")
    private val nostr =
        listOf(
            RepoCandidate("https://github.com/o/app", "Amethyst app", null, Source.NOSTR, nostr = nostrRef),
            RepoCandidate("https://relay.ngit.dev/npub1x/relay.git", "relay", "A relay", Source.NOSTR, nostr = NostrRepoRef("b".repeat(64), "relay")),
        )

    @Test
    fun withNothingTypedOnlyTheOnesUsedHereShowMostUsedFirst() {
        assertEquals(listOf("o/app", "o/lib"), RepoCatalog.merge("", local, github, nostr).map { it.label })
    }

    @Test
    fun aSearchListsEachRepositoryOnceUsedHereFirst() {
        val found = RepoCatalog.merge("app", local, github, nostr)
        // github.com/o/app is cloned here, on GitHub and announced on Nostr: one row, marked as Nostr.
        assertEquals(listOf("o/app"), found.map { it.label })
        assertEquals(Source.LOCAL, found.single().source)
        assertEquals(nostrRef, found.single().nostr)
        // Descriptions and URLs match too.
        assertEquals(listOf("o/docs"), RepoCatalog.merge("documentation", local, github, nostr).map { it.label })
        assertEquals(listOf("relay"), RepoCatalog.merge("ngit.dev", local, github, nostr).map { it.label })
    }

    @Test
    fun gitHubsSearchComesAfterWhatAmethystKnows() {
        val searched = listOf(RepoCandidate("https://github.com/x/relay.git", "x/relay", null, Source.GITHUB))
        assertEquals(listOf("relay", "x/relay"), RepoCatalog.merge("relay", local, github, nostr, searched).map { it.label })
    }

    @Test
    fun ghsRepositoryListsAreRead() {
        val mine = RepoCatalog.parseGitHub("""[{"description":"","nameWithOwner":"o/app","url":"https://github.com/o/app"}]""", "nameWithOwner")
        assertEquals(listOf(RepoCandidate("https://github.com/o/app.git", "o/app", null, Source.GITHUB)), mine)
        val found = RepoCatalog.parseGitHub("""[{"description":"Docs","fullName":"o/docs","url":"https://github.com/o/docs"}]""", "fullName")
        assertEquals("Docs", found.single().description)
        assertEquals(emptyList(), RepoCatalog.parseGitHub("error: not logged in", "fullName"))
    }

    @Test
    fun aNostrRepositoryIsClonedOverHttps() {
        assertEquals(
            "https://relay.ngit.dev/npub1x/app.git",
            RepoCatalog.cloneUrlOf(listOf("nostr://npub1x/relay.ngit.dev/app", "https://relay.ngit.dev/npub1x/app.git", "git@github.com:o/app.git")),
        )
        assertEquals("git@github.com:o/app.git", RepoCatalog.cloneUrlOf(listOf("nostr://npub1x/app", "git@github.com:o/app.git")))
        assertEquals(null, RepoCatalog.cloneUrlOf(listOf("nostr://npub1x/app")))
        // Schemes that need a helper git doesn't have: not offered at all.
        assertEquals(null, RepoCatalog.cloneUrlOf(listOf("htree://npub1x/app", "nostr://npub1x/app")))
    }
}
