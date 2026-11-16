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

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Workspaces against real git, with local bare repositories standing in for GitHub. */
class AgentWorkspacesTest {
    private lateinit var tmp: File
    private lateinit var workspaces: AgentWorkspaces
    private lateinit var appUrl: String
    private lateinit var libUrl: String

    private fun git(
        dir: File,
        vararg args: String,
    ): String {
        val result = GitWorkspace.run(dir, listOf("git", "-c", "user.email=t@t", "-c", "user.name=T") + args)
        assertEquals(0, result.code, result.output)
        return result.output
    }

    /** A "remote" with a commit on main, as a bare repository reached by file:// URL. */
    private fun remote(name: String): String {
        val bare = File(tmp, "remotes/$name.git").apply { mkdirs() }
        git(bare, "init", "--quiet", "--bare", "-b", "main")
        val seed = File(tmp, "seed-$name").apply { mkdirs() }
        git(seed, "init", "--quiet", "-b", "main")
        File(seed, "README.md").writeText("# $name\n")
        git(seed, "add", ".")
        git(seed, "commit", "--quiet", "-m", "init")
        git(seed, "push", "--quiet", bare.absolutePath, "main")
        return fileUrl(bare)
    }

    @BeforeTest
    fun setUp() {
        tmp = Files.createTempDirectory("agent-workspaces").toFile()
        workspaces = AgentWorkspaces(File(tmp, "AmethystAgents"))
        appUrl = remote("app")
        libUrl = remote("lib")
    }

    @AfterTest
    fun tearDown() {
        tmp.deleteRecursively()
    }

    @Test
    fun aWorkspaceIsAFreshCloneOfEachRepositoryOnItsOwnBranch() =
        runBlocking {
            val ws = workspaces.create(listOf(appUrl, libUrl), "Fix the relay list", branch = "agent/relays")
            assertEquals(listOf("app", "lib"), ws.repos.map { it.name })
            ws.repos.forEach { repo ->
                val dir = File(ws.dir, repo.name)
                assertEquals("agent/relays", git(dir, "rev-parse", "--abbrev-ref", "HEAD").trim())
                assertEquals("main", repo.baseBranch)
                // Pushes and pull requests go to the real remote, not the mirror.
                assertEquals(repo.url, git(dir, "remote", "get-url", "origin").trim())
                // A whole repository of its own, not a worktree sharing another's .git.
                assertTrue(File(dir, ".git").isDirectory)
            }
            assertEquals(ws, workspaces.of(File(ws.dir, "app/src")))
            assertEquals(null, workspaces.of(tmp))
        }

    @Test
    fun clonesShareTheMirrorsObjectsInsteadOfCopyingThem() =
        runBlocking {
            val ws = workspaces.create(listOf(appUrl), null)
            val objects = File(ws.dir, "app/.git/objects")
            // The mirror's objects come packed: the clone's packs must be the mirror's own files.
            val files = File(objects, "pack").listFiles { f -> f.name.endsWith(".pack") || f.name.endsWith(".idx") }.orEmpty().toList()
            assertTrue(files.isNotEmpty())
            // The same file on disk (inode on Unix, file index on NTFS), not a copy of it.
            val mirrorPacks = File(workspaces.mirrorDir(appUrl), "objects/pack")
            files.forEach { file ->
                assertTrue(Files.isSameFile(file.toPath(), File(mirrorPacks, file.name).toPath()), "$file is a copy, not a hardlink")
            }
        }

    @Test
    fun workReachesTheRemoteAndTheNextWorkspaceSeesIt() =
        runBlocking {
            val first = workspaces.create(listOf(appUrl), null, branch = "agent/one")
            val app = File(first.dir, "app")
            File(app, "fix.txt").writeText("fixed\n")
            assertEquals(1, workspaces.status(first).single().uncommitted)
            git(app, "add", ".")
            git(app, "commit", "--quiet", "-m", "fix")
            assertEquals(WorkspaceRepoStatus(first.repos.single(), ahead = 1, uncommitted = 0), workspaces.status(first).single())
            // The user's push goes to the remote; the branch lands there.
            git(app, "push", "--quiet", "--set-upstream", "origin", "agent/one")
            // A new session fetches the mirror first, so it starts from what the remote has now.
            val second = workspaces.create(listOf(appUrl), null, branch = "agent/two")
            assertTrue(git(File(second.dir, "app"), "branch", "-r").contains("origin/agent/one"))

            workspaces.delete(first)
            assertFalse(File(first.dir).exists())
            assertTrue(workspaces.mirrorDir(appUrl).isDirectory, "the mirror stays for the next session")
            assertEquals(listOf(second.id), workspaces.list().map { it.id })
        }

    @Test
    fun manySessionsStartingTogetherShareOneMirror() =
        runBlocking {
            val made = (1..6).map { i -> async { workspaces.create(listOf(appUrl, libUrl), "s$i", branch = "agent/s$i") } }.awaitAll()
            assertEquals(6, made.map { it.dir }.distinct().size)
            assertEquals(2, workspaces.mirrorsDir.listFiles()!!.size)
        }

    @Test
    fun aBadRepositoryLeavesNothingBehind() =
        runBlocking {
            val failed = runCatching { workspaces.create(listOf(appUrl, fileUrl(File(tmp, "missing.git"))), "broken") }
            assertTrue(failed.isFailure)
            assertTrue(
                workspaces.sessionsDir
                    .listFiles()
                    .orEmpty()
                    .isEmpty(),
            )
            assertTrue(workspaces.list().isEmpty())
        }

    @Test
    fun groupsAreRememberedMostRecentFirst() {
        workspaces.saveGroup(RepoGroup("stack", listOf(appUrl, libUrl)))
        workspaces.saveGroup(RepoGroup("app only", listOf(appUrl)))
        workspaces.saveGroup(RepoGroup("stack", listOf(libUrl)))
        assertEquals(listOf("stack", "app only"), workspaces.groups().map { it.name })
        assertEquals(listOf(libUrl), workspaces.groups().first().urls)
    }

    @Test
    fun mirrorsAreNamedByHostOwnerAndRepository() {
        assertEquals("github.com-vitorpamplona-amethyst.git", workspaces.mirrorDir("https://github.com/vitorpamplona/amethyst.git").name)
        assertEquals("github.com-vitorpamplona-amethyst.git", workspaces.mirrorDir("git@github.com:vitorpamplona/amethyst.git").name)
        assertEquals("github.com-someone-amethyst.git", workspaces.mirrorDir("https://github.com/someone/amethyst").name)
        assertEquals("amethyst", AgentWorkspaces.repoName("git@github.com:vitorpamplona/amethyst.git"))
        // Local repositories on Windows.
        assertEquals("amethyst", AgentWorkspaces.repoName("C:\\repos\\amethyst"))
        assertEquals("amethyst", AgentWorkspaces.repoName("file:///C:/repos/amethyst.git"))
        assertEquals("repos/amethyst", AgentWorkspaces.ownerAndName("C:\\repos\\amethyst"))
    }

    @Test
    fun ghsAnswersAreRead() {
        assertEquals(
            GitHubPullRequests.Result.Opened("https://github.com/o/r/pull/7"),
            GitHubPullRequests.parseCreated(0, "Creating pull request for agent/x into main in o/r\n\nhttps://github.com/o/r/pull/7\n"),
        )
        assertIs<GitHubPullRequests.Result.Failed>(GitHubPullRequests.parseCreated(1, "pull request create failed: GraphQL: No commits between main and agent/x"))
        val info = assertNotNull(GitHubPullRequests.parseState("""{"isDraft":false,"state":"MERGED","url":"https://github.com/o/r/pull/7"}"""))
        assertTrue(info.isMerged)
    }

    @Test
    fun repositoriesAreShownAsOwnerAndName() {
        assertEquals("vitorpamplona/amethyst", AgentWorkspaces.ownerAndName("https://github.com/vitorpamplona/amethyst.git"))
        assertEquals("vitorpamplona/amethyst", AgentWorkspaces.ownerAndName("git@github.com:vitorpamplona/amethyst.git"))
        assertEquals("vitorpamplona/amethyst", AgentWorkspaces.ownerAndName("ssh://git@github.com/vitorpamplona/amethyst"))
        assertEquals("https://github.com/vitorpamplona/amethyst", AgentWorkspaces.webUrl("git@github.com:vitorpamplona/amethyst.git"))
        assertEquals("https://github.com/vitorpamplona/amethyst", AgentWorkspaces.webUrl("https://github.com/vitorpamplona/amethyst.git"))
        assertEquals("https://github.com/o/r", AgentWorkspaces.webUrl("ssh://git@github.com:22/o/r.git"))
        // A local remote has no page to open.
        assertEquals(null, AgentWorkspaces.webUrl("/srv/git/app.git"))
    }

    @Test
    fun aPullRequestIsKeptWithItsWorkspace() =
        runBlocking {
            val ws = workspaces.create(listOf(appUrl, libUrl), null)
            val saved = assertNotNull(workspaces.recordPullRequest(ws.id, "app", PullRequestInfo("https://github.com/o/app/pull/1", "OPEN", isDraft = true)))
            assertTrue(saved.pullRequests.getValue("app").isOpen)
            // It survives a re-read from disk, and a state change replaces it.
            assertEquals(
                "https://github.com/o/app/pull/1",
                workspaces
                    .list()
                    .single()
                    .pullRequests["app"]
                    ?.url,
            )
            workspaces.recordPullRequest(ws.id, "app", PullRequestInfo("https://github.com/o/app/pull/1", "MERGED", isDraft = false))
            assertTrue(
                workspaces
                    .list()
                    .single()
                    .pullRequests
                    .getValue("app")
                    .isMerged,
            )
            assertEquals(null, workspaces.recordPullRequest("gone", "app", null))
        }

    @Test
    fun theLastAgentAndGroupsAreRemembered() {
        assertEquals(null, workspaces.lastAgent)
        workspaces.lastAgent = "codex"
        assertEquals("codex", AgentWorkspaces(workspaces.root).lastAgent)
        workspaces.saveGroup(RepoGroup("stack", listOf(appUrl, libUrl)))
        workspaces.saveGroup(RepoGroup("app only", listOf(appUrl)))
        workspaces.deleteGroup("stack")
        assertEquals(listOf("app only"), workspaces.groups().map { it.name })
        // The agent's record is not a workspace.
        assertTrue(workspaces.list().isEmpty())
    }

    @Test
    fun ghsErrorsShowTheirFirstLine() {
        val error = PullRequestError("\nfatal: could not read Username for 'https://github.com'\nhint: set up a credential helper\n", pushed = false)
        assertEquals("fatal: could not read Username for 'https://github.com'", error.summary)
        assertTrue(error.hasDetails)
        assertFalse(PullRequestError("one line", pushed = true).hasDetails)
    }
}
