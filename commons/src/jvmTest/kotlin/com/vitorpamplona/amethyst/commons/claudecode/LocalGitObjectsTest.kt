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

import com.vitorpamplona.quartz.nip34Git.git.GitObjectType
import com.vitorpamplona.quartz.nip34Git.git.GitObjectWriter
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Against a real `git` in a temporary repository. */
class LocalGitObjectsTest {
    private lateinit var dir: File

    private fun git(vararg args: String) {
        val p =
            ProcessBuilder(listOf("git", "-c", "user.name=T", "-c", "user.email=t@example.com", "-c", "commit.gpgsign=false") + args)
                .directory(dir)
                .redirectErrorStream(true)
                .start()
        val out = p.inputStream.bufferedReader().readText()
        check(p.waitFor() == 0) { "git ${args.joinToString(" ")}: $out" }
    }

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("local-git").toFile()
        git("init", "-q", "-b", "main")
        File(dir, "a.txt").writeText("one\n")
        git("add", ".")
        git("commit", "-q", "-m", "base")
        git("checkout", "-q", "-b", "pr/feat/x")
        File(dir, "a.txt").writeText("one\ntwo\n")
        File(dir, "b.txt").writeText("new\n")
        git("add", ".")
        git("commit", "-q", "-m", "change")
    }

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun readsWhatTheBranchAddsWithMatchingIds() =
        runBlocking {
            val tip = assertNotNull(LocalGitObjects.resolve(dir, "HEAD"))
            val base = assertNotNull(LocalGitObjects.resolve(dir, "main"))
            val objects = LocalGitObjects.between(dir, base, tip)

            // One commit, its root tree, and the two blobs it changed or added; the base's own objects are left out.
            assertEquals(1, objects.count { it.type == GitObjectType.COMMIT })
            assertEquals(1, objects.count { it.type == GitObjectType.TREE })
            assertEquals(2, objects.count { it.type == GitObjectType.BLOB })
            assertEquals(tip, objects.first { it.type == GitObjectType.COMMIT }.oid)
            objects.forEach { assertEquals(it.oid, GitObjectWriter.oidOf(it.type, it.data)) }
        }

    @Test
    fun workspaceReadsBranchStateAndLinks() =
        runBlocking {
            GitWorkspace.link(dir, "pr/feat/x", repo = NostrRepoRef("ab".repeat(32), "amethyst"), base = "main")
            File(dir, "c.txt").writeText("uncommitted\n")
            val info = assertNotNull(GitWorkspace.inspect(dir))
            assertEquals("pr/feat/x", info.branch)
            assertEquals("main", info.base)
            assertEquals(1, info.ahead)
            assertEquals("change", info.commits.single().subject)
            assertEquals(2, info.filesChanged)
            assertEquals(1, info.uncommitted)
            assertEquals("amethyst", info.repo?.identifier)
            assertTrue(GitWorkspace.diff(dir).contains("+two"))

            GitWorkspace.markReviewed(dir, "pr/feat/x", listOf("c1", "c2"))
            assertEquals(setOf("c1", "c2"), assertNotNull(GitWorkspace.inspect(dir)).reviewed)
        }

    @Test
    fun reviewPromptQuotesTheCommentedLines() =
        runBlocking {
            GitWorkspace.link(dir, "pr/feat/x", base = "main")
            val prompt =
                ReviewPrompt.render(
                    dir,
                    "Reviewers commented.",
                    listOf(
                        ReviewItem("1", "bob wrote:", "Looks good overall.", createdAt = 1),
                        ReviewItem("2", "alice on a.txt, line 2:", "Say three.", ReviewLocation("a.txt", 2, 2, removedSide = false, commit = null), createdAt = 2),
                        ReviewItem("3", "carol on a.txt, removed line 1:", "Why drop it?", ReviewLocation("a.txt", 1, 1, removedSide = true, commit = null), createdAt = 3),
                    ),
                )
            assertTrue(prompt.startsWith("Reviewers commented.\n\nbob wrote:\nLooks good overall."), prompt)
            // The new side, at HEAD: line 2 is "two", marked.
            assertTrue(prompt.contains("alice on a.txt, line 2:\n```\n1  one\n2> two\n```\nSay three."), prompt)
            // The old side, at the merge base: the file had only "one".
            assertTrue(prompt.contains("carol on a.txt, removed line 1:\n```\n1> one\n```\nWhy drop it?"), prompt)
        }

    @Test
    fun ngitRemotesNameTheRepository() {
        val npub = "npub1sg6plzptd64u62a878hep2kev88swjh3tw00gjsfl8f237lmu63q0uf63m"
        val ref = assertNotNull(NostrRepoRef.fromNostrUrl("nostr://$npub/relay.ngit.dev/amethyst"))
        assertEquals("amethyst", ref.identifier)
        assertEquals(64, ref.pubKeyHex.length)
        assertEquals(ref, NostrRepoRef.fromConfig(ref.toConfig()))
    }

    @Test
    fun onlyPlainRemotesCommitsAndBranchesFromEventsReachGit() {
        listOf("https://relay.ngit.dev/npub1x/amethyst.git", "git@github.com:me/repo.git", "ssh://git@host/r.git", "file:///srv/r.git", "git://h/r")
            .forEach { assertTrue(GitWorkspace.isSafeRemoteUrl(it), it) }
        listOf("--upload-pack=sh -c id", "-oProxyCommand=x", "ext::sh -c id", "https://h/r --x", "", "fd::3")
            .forEach { assertTrue(!GitWorkspace.isSafeRemoteUrl(it), it) }
        assertTrue(GitWorkspace.isCommitId("a".repeat(40)))
        assertTrue(!GitWorkspace.isCommitId("--output=/tmp/x"))
        assertTrue(GitWorkspace.isSafeBranchName("pr/feat/x"))
        listOf("-b", "a..b", "x y", "").forEach { assertTrue(!GitWorkspace.isSafeBranchName(it), it) }
    }

    @Test
    fun aRevisionFromACommentIsNeverAnOption() =
        runBlocking {
            assertEquals(null, GitWorkspace.linesAt(dir, "--output=${dir.absolutePath}/pwned", "a.txt", 1, 1))
            assertTrue(!File(dir, "pwned:a.txt").exists())
            assertEquals(listOf(1 to "one"), GitWorkspace.linesAt(dir, "HEAD", "a.txt", 1, 1))
        }

    @Test
    fun aCommandThatHangsIsStoppedAtItsTimeout() {
        val started = System.currentTimeMillis()
        // Keeps its output open while it sleeps: reading to the end first would wait it out.
        val result = GitWorkspace.run(dir, listOf("sh", "-c", "echo started; sleep 30"), timeoutSeconds = 1)
        assertEquals(-1, result.code)
        assertTrue(System.currentTimeMillis() - started < 10_000, "took ${System.currentTimeMillis() - started} ms")
    }

    @Test
    fun aSessionOnANostrCheckoutCannotPushHoweverItTries() {
        val remote = Files.createTempDirectory("remote").toFile()
        try {
            git("init", "-q", "--bare", remote.absolutePath)
            git("remote", "add", "origin", remote.absolutePath)

            fun asTheSession(command: String): Int {
                val p =
                    ProcessBuilder("sh", "-c", command)
                        .directory(dir)
                        .redirectErrorStream(true)
                        .apply { environment().putAll(ClaudeChat.noPushEnvironment()) }
                        .start()
                p.inputStream.readBytes()
                return p.waitFor()
            }
            listOf(
                "git push origin HEAD",
                "git -C . push origin HEAD",
                "git push --no-verify ${remote.absolutePath} HEAD",
                "git push file://${remote.absolutePath} HEAD",
                "sh -c 'git push origin HEAD:refs/heads/x'",
            ).forEach { assertTrue(asTheSession(it) != 0, "pushed with: $it") }
            assertEquals(0, asTheSession("git fetch -q origin"), "fetching must still work")
            // Nothing reached the remote.
            assertTrue(GitWorkspace.git(remote, "for-each-ref").orEmpty().isBlank())
        } finally {
            remote.deleteRecursively()
        }
    }

    @Test
    fun theNoPushConfigKeepsConfigTheEnvironmentAlreadyPasses() {
        val env = ClaudeChat.noPushEnvironment(mapOf("GIT_CONFIG_COUNT" to "2", "GIT_CONFIG_KEY_0" to "a.b", "GIT_CONFIG_KEY_1" to "c.d"))
        assertEquals("3", env["GIT_CONFIG_COUNT"])
        assertEquals("url.publishing-is-the-users-step-in-amethyst://.pushInsteadOf", env["GIT_CONFIG_KEY_2"])
        assertTrue("GIT_CONFIG_KEY_0" !in env && "GIT_CONFIG_KEY_1" !in env)
        assertEquals("1", ClaudeChat.noPushEnvironment(emptyMap())["GIT_CONFIG_COUNT"])
    }

    @Test
    fun onlyHostsFromEventsGoWithoutALoginWindow() {
        val probe = listOf("sh", "-c", "echo \"[${'$'}GCM_INTERACTIVE]\"")
        // By default the variable is whatever the app was started with, not set by the app.
        assertEquals("[${System.getenv("GCM_INTERACTIVE").orEmpty()}]", GitWorkspace.run(dir, probe).output.trim())
        assertEquals("[never]", GitWorkspace.run(dir, probe, noLoginWindow = true).output.trim())
    }
}
