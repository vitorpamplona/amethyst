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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.gitRepositories

import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the ngit-repository-relevant search matcher used by
 * `GitRepositoriesScreen`. Every field the matcher promises to index is
 * exercised once here so a future refactor that drops one (e.g. relays)
 * shows up as a red test.
 */
class GitRepositorySearchMatcherTest {
    private val ownerHex = "aa".repeat(32)
    private val maintainerHex = "bb".repeat(32)
    private val ownerNpub = NPub.create(ownerHex)
    private val maintainerNpub = NPub.create(maintainerHex)

    private fun repo(
        name: String = "amethyst",
        dTag: String = "amethyst",
        description: String? = "A Nostr client for Android",
        clones: List<String> = listOf("https://github.com/vitorpamplona/amethyst.git"),
        webs: List<String> = listOf("https://amethyst.social"),
        relays: List<String> = listOf("wss://relay.ngit.dev"),
        maintainers: List<String> = listOf(maintainerHex),
        hashtags: List<String> = listOf("nostr", "android"),
        euc: String? = "99614f07e4ffa99dff4143d7457be8923690bbba",
        pubKey: String = ownerHex,
    ): GitRepositoryEvent {
        val tags = mutableListOf<Array<String>>()
        tags += arrayOf("d", dTag)
        tags += arrayOf("name", name)
        description?.let { tags += arrayOf("description", it) }
        clones.forEach { tags += arrayOf("clone", it) }
        webs.forEach { tags += arrayOf("web", it) }
        if (relays.isNotEmpty()) tags += arrayOf("relays", *relays.toTypedArray())
        if (maintainers.isNotEmpty()) tags += arrayOf("maintainers", *maintainers.toTypedArray())
        hashtags.forEach { tags += arrayOf("t", it) }
        euc?.let { tags += arrayOf("r", it, "euc") }
        return GitRepositoryEvent(
            id = "00".repeat(32),
            pubKey = pubKey,
            createdAt = 0L,
            tags = tags.toTypedArray(),
            content = "",
            sig = "00",
        )
    }

    @Test
    fun emptyQueryMatchesNothing() {
        // Callers must skip the filter path themselves — the matcher is
        // conservative and refuses to accept an empty term list.
        assertFalse(GitRepositorySearchMatcher.matches(repo(), ""))
        assertFalse(GitRepositorySearchMatcher.matches(repo(), "   "))
    }

    @Test
    fun matchesRepoNameCaseInsensitive() {
        assertTrue(GitRepositorySearchMatcher.matches(repo(name = "Amethyst"), "amethyst"))
        assertTrue(GitRepositorySearchMatcher.matches(repo(name = "Amethyst"), "AMET"))
    }

    @Test
    fun matchesRepoIdentifierDTag() {
        assertTrue(GitRepositorySearchMatcher.matches(repo(dTag = "ngit-cli"), "ngit-cli"))
    }

    @Test
    fun matchesDescription() {
        assertTrue(
            GitRepositorySearchMatcher.matches(
                repo(description = "A private Nostr messenger"),
                "messenger",
            ),
        )
    }

    @Test
    fun matchesHashtag() {
        assertTrue(GitRepositorySearchMatcher.matches(repo(hashtags = listOf("kotlin", "mobile")), "kotlin"))
    }

    @Test
    fun matchesCloneUrl() {
        assertTrue(
            GitRepositorySearchMatcher.matches(
                repo(clones = listOf("https://relay.ngit.dev/npub1abc/foo.git")),
                "relay.ngit.dev",
            ),
        )
    }

    @Test
    fun matchesWebUrl() {
        assertTrue(GitRepositorySearchMatcher.matches(repo(webs = listOf("https://gitworkshop.dev/x")), "gitworkshop"))
    }

    @Test
    fun matchesRelayHost() {
        assertTrue(
            GitRepositorySearchMatcher.matches(
                repo(relays = listOf("wss://relay.damus.io")),
                "damus.io",
            ),
        )
    }

    @Test
    fun matchesMaintainerHex() {
        assertTrue(GitRepositorySearchMatcher.matches(repo(), maintainerHex))
    }

    @Test
    fun matchesMaintainerNpub() {
        // The `bb…` npub is a valid bech32 pubkey; supplying it as a
        // query must resolve to the same hex the tag carries.
        assertTrue(GitRepositorySearchMatcher.matches(repo(), maintainerNpub))
    }

    @Test
    fun matchesAuthorHex() {
        assertTrue(GitRepositorySearchMatcher.matches(repo(), ownerHex))
    }

    @Test
    fun matchesAuthorNpub() {
        assertTrue(GitRepositorySearchMatcher.matches(repo(), ownerNpub))
    }

    @Test
    fun matchesEarliestUniqueCommit() {
        // Full euc must match; a prefix that lives inside it should too.
        assertTrue(GitRepositorySearchMatcher.matches(repo(euc = "99614f07e4ff"), "99614f07"))
    }

    @Test
    fun multipleTermsMustAllMatch() {
        val target = repo(name = "amethyst", hashtags = listOf("nostr", "android"))
        assertTrue(GitRepositorySearchMatcher.matches(target, "amethyst android"))
        // "kotlin" isn't in this repo's fields, so the AND fails.
        assertFalse(GitRepositorySearchMatcher.matches(target, "amethyst kotlin"))
    }

    @Test
    fun invalidNpubTreatedAsRawText() {
        // "npub1notreallybech32" is not a decodable npub; the matcher
        // should still let it match as a raw substring of e.g. the
        // description, without throwing.
        val target = repo(description = "npub1notreallybech32 is a placeholder")
        assertTrue(GitRepositorySearchMatcher.matches(target, "npub1notreallybech32"))
    }

    @Test
    fun filterReturnsAllMatches() {
        val a = repo(name = "amethyst")
        val b = repo(name = "ngit-cli", dTag = "ngit-cli", hashtags = listOf("rust", "git"))
        val c = repo(name = "shakespeare", dTag = "shakespeare")
        val hits = GitRepositorySearchMatcher.filter(sequenceOf(a, b, c), "rust")
        assertEquals(listOf(b), hits)
    }

    @Test
    fun filterBlankQueryReturnsEverything() {
        val a = repo(name = "amethyst")
        val b = repo(name = "ngit-cli")
        val hits = GitRepositorySearchMatcher.filter(sequenceOf(a, b), "   ")
        assertEquals(listOf(a, b), hits)
    }
}
