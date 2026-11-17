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

import com.vitorpamplona.agents.PermissionMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import java.io.File
import java.net.URI
import java.nio.file.FileSystems
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
        workspaces = AgentWorkspaces(File(tmp, "agents"))
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
                workspaces.workstreamsDir
                    .listFiles()
                    .orEmpty()
                    .isEmpty(),
            )
            assertTrue(workspaces.list().isEmpty())
        }

    @Test
    fun aCancelledStartLeavesNothingBehind() =
        runBlocking {
            workspaces.delete(workspaces.create(listOf(libUrl), null))
            git(workspaces.mirrorDir(libUrl), "config", "remote.origin.uploadpack", "sleep 2; git-upload-pack")
            val starting = launch(Dispatchers.IO) { workspaces.create(listOf(libUrl), "left") }
            delay(500)
            starting.cancelAndJoin()
            assertTrue(
                workspaces.workstreamsDir
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
        // A GRASP server's owner is an npub: the name alone reads better.
        assertEquals("freiheit-funk", AgentWorkspaces.ownerAndName("https://relay.ngit.dev/npub14rg4vrt2v374q95ezeeydu3hkdhmzglcj950mggacap4x0lv0gyq04wun7/freiheit-funk.git"))
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

    @Test
    fun usageIsCountedAndOutlivesTheWorkspace() =
        runBlocking {
            val first = workspaces.create(listOf(appUrl, libUrl), null)
            workspaces.create(listOf(appUrl), null)
            workspaces.delete(first)
            assertEquals(2, workspaces.usage().getValue(appUrl).count)
            assertEquals(1, workspaces.usage().getValue(libUrl).count)
            // The picker leads with the most used, and knows every mirror's URL.
            assertEquals(listOf(appUrl, libUrl), RepoCatalog.local(workspaces).map { it.url })
            assertEquals(setOf(appUrl, libUrl), workspaces.mirrorUrls().toSet())
        }

    @Test
    fun aNostrRepositorysBranchIsLinkedForProposals() =
        runBlocking {
            val ref = NostrRepoRef("a".repeat(64), "app")
            val ws = workspaces.create(listOf(appUrl, libUrl), null, branch = "agent/n", nostrRepos = mapOf(appUrl to ref))
            // What the Propose screen reads: the repository, and the base the branch is measured against.
            val info = assertNotNull(GitWorkspace.inspect(File(ws.dir, "app")))
            assertEquals(ref, info.repo)
            assertEquals(null, GitWorkspace.inspect(File(ws.dir, "lib"))?.repo)
        }

    /** `gh` answering as GitHub would for a user ("me") who may only read "o/app". */
    private fun fakeGh(
        permission: String,
        calls: MutableList<List<String>>,
    ): (File, List<String>, Long) -> GitWorkspace.Result =
        { _, command, _ ->
            val args = command.drop(1)
            synchronized(calls) { calls.add(args) }
            when {
                args.take(2) == listOf("repo", "view") -> GitWorkspace.Result(0, "$permission\n")
                args.take(2) == listOf("repo", "fork") -> GitWorkspace.Result(0, "Created fork me/app\n")
                args.take(2) == listOf("api", "user") -> GitWorkspace.Result(0, "me\n")
                args.take(2) == listOf("pr", "create") -> GitWorkspace.Result(0, "https://github.com/o/app/pull/9\n")
                else -> GitWorkspace.Result(1, "unexpected: $args")
            }
        }

    @Test
    fun withoutPushAccessTheBranchGoesToAForkAndThePullRequestOpensFromIt() =
        runBlocking {
            val ws = workspaces.create(listOf(appUrl), null, branch = "agent/f")
            val app = File(ws.dir, "app")
            File(app, "fix.txt").writeText("fixed\n")
            git(app, "add", ".")
            git(app, "commit", "--quiet", "-m", "fix")
            // The user's fork, standing in for github.com/me/app.
            val fork = File(tmp, "remotes/fork.git").apply { mkdirs() }
            git(fork, "init", "--quiet", "--bare", "-b", "main")
            val calls = mutableListOf<List<String>>()
            val real = GitHubPullRequests.runGh
            GitHubPullRequests.runGh = fakeGh("READ", calls)
            try {
                val result = GitHubPullRequests.open(app, "main", "agent/f", "Fix", "", draft = false, upstream = "o/app", gh = File("gh"), forkUrl = { fileUrl(fork) })
                assertEquals(GitHubPullRequests.Result.Opened("https://github.com/o/app/pull/9", fork = "me/app"), result)
            } finally {
                GitHubPullRequests.runGh = real
            }
            // The branch reached the fork, not the repository it was cloned from.
            assertTrue(git(fork, "branch", "--list", "agent/f").contains("agent/f"))
            assertFalse(git(File(tmp, "remotes/app.git"), "branch", "--list", "agent/f").contains("agent/f"))
            // The pull request is opened on the repository, from the fork's branch.
            val create = calls.single { it.take(2) == listOf("pr", "create") }
            assertEquals("o/app", create[create.indexOf("--repo") + 1])
            assertEquals("me:agent/f", create[create.indexOf("--head") + 1])
        }

    @Test
    fun withPushAccessTheBranchGoesToTheRepositoryItself() =
        runBlocking {
            val ws = workspaces.create(listOf(appUrl), null, branch = "agent/w")
            val app = File(ws.dir, "app")
            File(app, "fix.txt").writeText("fixed\n")
            git(app, "add", ".")
            git(app, "commit", "--quiet", "-m", "fix")
            val calls = mutableListOf<List<String>>()
            val real = GitHubPullRequests.runGh
            GitHubPullRequests.runGh = fakeGh("WRITE", calls)
            try {
                val result = GitHubPullRequests.open(app, "main", "agent/w", "Fix", "", draft = false, upstream = "o/app", gh = File("gh"))
                assertEquals(GitHubPullRequests.Result.Opened("https://github.com/o/app/pull/9"), result)
            } finally {
                GitHubPullRequests.runGh = real
            }
            assertTrue(git(File(tmp, "remotes/app.git"), "branch", "--list", "agent/w").contains("agent/w"))
            assertTrue(calls.none { it.take(2) == listOf("repo", "fork") })
            val create = calls.single { it.take(2) == listOf("pr", "create") }
            assertEquals("agent/w", create[create.indexOf("--head") + 1])
        }

    @Test
    fun ghsForksAndListsAreRead() {
        assertEquals("me/app", GitHubPullRequests.parseForkName("✓ Created fork me/app"))
        assertEquals("me/app", GitHubPullRequests.parseForkName("! me/app already exists"))
        assertTrue(GitHubPullRequests.needsFork("READ"))
        assertFalse(GitHubPullRequests.needsFork("WRITE"))
        // gh couldn't say: try the repository itself, as before.
        assertFalse(GitHubPullRequests.needsFork(null))
        assertEquals("o/app", GitHubPullRequests.gitHubRepo("git@github.com:o/app.git"))
        assertEquals(null, GitHubPullRequests.gitHubRepo("https://relay.ngit.dev/npub1x/app.git"))
        // `gh pr list` answers with an array.
        assertTrue(assertNotNull(GitHubPullRequests.parseState("""[{"isDraft":false,"state":"OPEN","url":"https://github.com/o/app/pull/9"}]""")).isOpen)
        assertEquals(null, GitHubPullRequests.parseState("[]"))
    }

    @Test
    fun aWorkspaceIsFinishedWhenEveryPullRequestIsMergedOrClosed() =
        runBlocking {
            val ws = workspaces.create(listOf(appUrl, libUrl), null)
            assertEquals(null, ws.finishedAt)
            workspaces.recordPullRequest(ws.id, "app", PullRequestInfo("https://github.com/o/app/pull/1", "MERGED", isDraft = false))
            // One repository has none yet: not finished.
            assertEquals(null, workspaces.list().single().finishedAt)
            workspaces.recordPullRequest(ws.id, "lib", PullRequestInfo("https://github.com/o/lib/pull/2", "OPEN", isDraft = false))
            assertEquals(null, workspaces.list().single().finishedAt)
            val closed = assertNotNull(workspaces.recordPullRequest(ws.id, "lib", PullRequestInfo("https://github.com/o/lib/pull/2", "CLOSED", isDraft = false)))
            val finishedAt = assertNotNull(closed.finishedAt)
            // A later check of the same closed pull request keeps when it closed.
            Thread.sleep(5)
            workspaces.recordPullRequest(ws.id, "lib", PullRequestInfo("https://github.com/o/lib/pull/2", "CLOSED", isDraft = false))
            assertEquals(finishedAt, workspaces.list().single().finishedAt)
            // Reopened, it is open work again.
            workspaces.recordPullRequest(ws.id, "lib", PullRequestInfo("https://github.com/o/lib/pull/2", "OPEN", isDraft = false))
            assertEquals(null, workspaces.list().single().finishedAt)
        }

    @Test
    fun uncommittedOrUnpushedWorkIsUnsaved() =
        runBlocking {
            val ws = workspaces.create(listOf(appUrl), null, branch = "agent/x")
            val dir = File(ws.dir, "app")
            assertFalse(workspaces.hasUnsavedWork(ws))
            File(dir, "new.txt").writeText("x\n")
            assertTrue(workspaces.hasUnsavedWork(ws))
            git(dir, "add", ".")
            git(dir, "commit", "--quiet", "-m", "work")
            // Committed but on no remote.
            assertTrue(workspaces.hasUnsavedWork(ws))
            git(dir, "push", "--quiet", "origin", "agent/x")
            assertFalse(workspaces.hasUnsavedWork(ws))
        }

    @Test
    fun ignoredFilesAreWorkButBuildOutputIsNot() =
        runBlocking {
            val ws = workspaces.create(listOf(appUrl), null, branch = "agent/y")
            val dir = File(ws.dir, "app")
            File(dir, ".gitignore").writeText("build/\n.env\n")
            git(dir, "add", ".")
            git(dir, "commit", "--quiet", "-m", "ignore")
            git(dir, "push", "--quiet", "origin", "agent/y")
            assertFalse(workspaces.hasUnsavedWork(ws))
            // What a build makes again, and what the OS leaves: nothing lost.
            File(dir, "build/classes").mkdirs()
            File(dir, "build/classes/A.class").writeText("x")
            File(dir, ".DS_Store").writeText("x")
            assertFalse(workspaces.hasUnsavedWork(ws))
            // A secret only this folder holds.
            File(dir, ".env").writeText("TOKEN=x\n")
            assertTrue(workspaces.hasUnsavedWork(ws))
        }

    @Test
    fun aWorkspacesSizeLeavesOutWhatItSharesWithTheMirror() {
        if ("unix" !in FileSystems.getDefault().supportedFileAttributeViews()) return
        val dir = File(tmp, "measured").apply { mkdirs() }
        File(dir, "own.bin").writeBytes(ByteArray(1_000))
        val shared = File(tmp, "shared.bin").apply { writeBytes(ByteArray(5_000)) }
        Files.createLink(File(dir, "linked.bin").toPath(), shared.toPath())
        assertEquals(1_000, AgentWorkspaces.diskUse(dir))
        assertEquals(6_000, AgentWorkspaces.diskUse(dir, countShared = true))
        assertEquals(0, AgentWorkspaces.diskUse(File(tmp, "missing")))
    }

    @Test
    fun mirrorsNoWorkspaceUsedForLongAreRemovedWhenTidying() =
        runBlocking {
            val kept = workspaces.create(listOf(appUrl), null)
            val gone = workspaces.create(listOf(libUrl), null)
            // Recently used: nothing to remove yet.
            assertTrue(workspaces.unusedMirrors().isEmpty())
            workspaces.delete(gone)
            assertTrue(workspaces.unusedMirrors().isEmpty())
            // The lib mirror's last use, long ago; app's workspace still uses its own.
            val usage = File(tmp, "agents/usage.json")
            val old = System.currentTimeMillis() - 90 * 24 * 60 * 60_000L
            usage.writeText("""{"$appUrl":{"count":1,"lastUsed":$old},"$libUrl":{"count":1,"lastUsed":$old}}""".replace("\\", "\\\\"))
            assertEquals(listOf(workspaces.mirrorDir(libUrl).absolutePath), workspaces.unusedMirrors().map { it.absolutePath })
            assertEquals(1, workspaces.tidyMirrors())
            assertFalse(workspaces.mirrorDir(libUrl).exists())
            // The other mirror was compacted, and still clones.
            assertTrue(workspaces.mirrorDir(appUrl).isDirectory)
            workspaces.delete(kept)
            val again = workspaces.create(listOf(appUrl, libUrl), null)
            assertEquals(listOf("app", "lib"), again.repos.map { it.name })
        }

    @Test
    fun cleanupLeavesAMirrorANewWorkspaceIsFetchingInto() =
        runBlocking {
            workspaces.delete(workspaces.create(listOf(libUrl), null))
            val usage = File(tmp, "agents/usage.json")
            val old = System.currentTimeMillis() - 90 * 24 * 60 * 60_000L
            usage.writeText("""{"$libUrl":{"count":1,"lastUsed":$old}}""".replace("\\", "\\\\"))
            val mirror = workspaces.mirrorDir(libUrl)
            assertEquals(listOf(mirror.absolutePath), workspaces.unusedMirrors().map { it.absolutePath })
            // The next fetch takes a while, as one over the network does.
            git(mirror, "config", "remote.origin.uploadpack", "sleep 2; git-upload-pack")
            val starting = async(Dispatchers.IO) { workspaces.create(listOf(libUrl), "new") }
            delay(500)
            assertTrue(workspaces.unusedMirrors().isEmpty(), "a mirror being fetched is in use")
            assertEquals(0, workspaces.removeUnusedMirrors())
            assertEquals(listOf("lib"), starting.await().repos.map { it.name })
            assertTrue(mirror.isDirectory)
        }

    /** Gives the remote at [url] a pull request's ref, as GitHub keeps one per pull request. */
    private fun addPullRequestRef(url: String) {
        val bare = File(URI(url))
        val main = git(bare, "rev-parse", "main").trim()
        git(bare, "update-ref", "refs/pull/1/head", main)
    }

    @Test
    fun mirrorsFetchBranchesAndTagsNotPullRequests() =
        runBlocking {
            addPullRequestRef(appUrl)
            workspaces.create(listOf(appUrl), null)
            val mirror = workspaces.mirrorDir(appUrl)
            assertEquals("", git(mirror, "for-each-ref", "refs/pull").trim())
            assertEquals("+refs/heads/*:refs/heads/*\n+refs/tags/*:refs/tags/*", git(mirror, "config", "--get-all", "remote.origin.fetch").trim())
            // A later fetch still brings new branches in, and still no pull requests.
            val seed = File(tmp, "seed-app")
            git(seed, "checkout", "--quiet", "-b", "feature")
            git(seed, "push", "--quiet", File(URI(appUrl)).absolutePath, "feature")
            workspaces.refreshMirror(appUrl)
            assertTrue(git(mirror, "for-each-ref", "--format=%(refname)").lines().contains("refs/heads/feature"))
            assertEquals("", git(mirror, "for-each-ref", "refs/pull").trim())
        }

    @Test
    fun anOldStyleMirrorIsNarrowedOnItsNextFetch() =
        runBlocking {
            addPullRequestRef(appUrl)
            val mirror = workspaces.mirrorDir(appUrl)
            mirror.parentFile.mkdirs()
            git(mirror.parentFile, "clone", "--quiet", "--mirror", appUrl, mirror.absolutePath)
            assertTrue(git(mirror, "for-each-ref", "refs/pull").isNotBlank())

            val made = workspaces.create(listOf(appUrl), null)

            assertEquals("", git(mirror, "for-each-ref", "refs/pull").trim())
            assertTrue(GitWorkspace.git(mirror, "config", "--get", "remote.origin.mirror") == null)
            assertEquals("+refs/heads/*:refs/heads/*\n+refs/tags/*:refs/tags/*", git(mirror, "config", "--get-all", "remote.origin.fetch").trim())
            assertEquals(listOf("app"), made.repos.map { it.name })
        }

    @Test
    fun preferencesAreKept() {
        assertTrue(workspaces.preferences().autoCleanup)
        workspaces.updatePreferences { it.copy(autoCleanup = false, archivedSessions = setOf("s1")) }
        val reread = AgentWorkspaces(File(tmp, "agents")).preferences()
        assertFalse(reread.autoCleanup)
        assertEquals(setOf("s1"), reread.archivedSessions)
    }

    @Test
    fun aWorkstreamIsNamedForItsFolderAndBranch() =
        runBlocking {
            val ws = workspaces.create(listOf(appUrl), "Fix the relay list")
            assertEquals("fix-the-relay-list", ws.id)
            assertEquals(File(workspaces.workstreamsDir, "fix-the-relay-list").absolutePath, ws.dir)
            assertEquals("agent/fix-the-relay-list", ws.repos.single().branch)
            // The agent is told where the clones are, since the workstream's folder is no repository.
            assertTrue(ws.brief.contains("- app/: $appUrl, on branch agent/fix-the-relay-list (from main)"), ws.brief)
            // A second workstream with the same name gets a folder of its own.
            assertEquals("fix-the-relay-list-2", workspaces.create(listOf(libUrl), "Fix the relay list").id)
        }

    @Test
    fun aRemovedWorkstreamsNameIsNotGivenOutAgain() =
        runBlocking {
            // Its conversations stay, filed under its folder: a new workstream there would show them.
            workspaces.delete(workspaces.create(listOf(appUrl), "relays"))
            assertEquals("relays-2", workspaces.create(listOf(appUrl), "relays").id)
        }

    @Test
    fun aBranchTheRepositoryAlreadyHasIsNotReused() =
        runBlocking {
            // The branch of an earlier workstream, pushed and merged long ago.
            val seed = File(tmp, "seed-app")
            git(seed, "push", "--quiet", File(tmp, "remotes/app.git").absolutePath, "main:agent/relays")
            val ws = workspaces.create(listOf(appUrl), "relays")
            assertEquals("agent/relays-2", ws.repos.single().branch)
        }

    @Test
    fun aClaimedFolderIsWhereTheClonesGo() =
        runBlocking {
            val dir = workspaces.claim("later", listOf(appUrl))
            assertTrue(dir.isDirectory)
            val ws = workspaces.create(listOf(appUrl), "later", claimed = dir)
            assertEquals(dir.absolutePath, ws.dir)
            assertTrue(File(dir, "app/.git").isDirectory)
        }

    @Test
    fun aFailedCloneRemovesTheClaimedFolder() =
        runBlocking {
            val dir = workspaces.claim("broken", listOf(appUrl))
            assertTrue(runCatching { workspaces.create(listOf(fileUrl(File(tmp, "missing.git"))), "broken", claimed = dir) }.isFailure)
            assertFalse(dir.exists())
        }

    @Test
    fun aWorkstreamRunsHandsOffInTheAgentsOwnWay() {
        assertEquals(PermissionMode.AUTO, workstreamModeOf(listOf(PermissionMode.DEFAULT, PermissionMode.ACCEPT_EDITS, PermissionMode.PLAN, PermissionMode.AUTO)))
        // Codex has no Auto: it works freely inside its sandbox and asks to leave it.
        assertEquals(PermissionMode.ACCEPT_EDITS, workstreamModeOf(listOf(PermissionMode.DEFAULT, PermissionMode.ACCEPT_EDITS, PermissionMode.PLAN)))
    }

    @Test
    fun aPullRequestFromSomeoneElsesForkIsNotThisWorkstreams() {
        // Workstream names make branch names anyone may pick: `gh pr list --head` matches them all.
        val listed =
            """[{"headRepositoryOwner":{"login":"someone"},"isDraft":false,"state":"MERGED","url":"https://github.com/o/app/pull/1"},
               {"headRepositoryOwner":{"login":"Me"},"isDraft":false,"state":"OPEN","url":"https://github.com/o/app/pull/2"}]"""
        assertEquals("https://github.com/o/app/pull/2", GitHubPullRequests.parseState(listed, setOf("me", "o"))?.url)
        assertEquals(null, GitHubPullRequests.parseState(listed, setOf("o")))
    }

    @Test
    fun aClonesPullRequestHeadsAreTheRepositorysOwnersAndItsForks() =
        runBlocking {
            val ws = workspaces.create(listOf(appUrl), "owners")
            val dir = File(ws.dir, "app")
            assertEquals(setOf("o"), GitHubPullRequests.headOwners(dir, "O/app"))
            git(dir, "remote", "add", "fork", "https://github.com/Me/app.git")
            assertEquals(setOf("o", "me"), GitHubPullRequests.headOwners(dir, "O/app"))
        }

    @Test
    fun anyNameMakesABranchGitTakes() {
        listOf("Fix... relay list", "release 1.0.", "x".repeat(39) + ".y", "drop the lock.lock", "..", "日本語").forEach { name ->
            val branch = "agent/" + AgentWorkspaces.slug(name)
            assertTrue(GitWorkspace.isSafeBranchName(branch), branch)
            assertEquals(0, GitWorkspace.run(tmp, listOf("git", "check-ref-format", "--branch", branch)).code, branch)
        }
    }

    @Test
    fun aCancelledCommandStopsItsProcess() {
        if (System.getProperty("os.name").orEmpty().startsWith("Windows")) return
        val marker = File(tmp, "still-running")
        val started = System.currentTimeMillis()
        runBlocking {
            val search = launch(Dispatchers.IO) { runInterruptible { GitWorkspace.run(tmp, listOf("sh", "-c", "sleep 3; touch ${marker.absolutePath}")) } }
            delay(300)
            search.cancelAndJoin()
        }
        assertTrue(System.currentTimeMillis() - started < 2_000, "the cancel waited for the command")
        Thread.sleep(3_500)
        assertFalse(marker.exists(), "the command kept running after its caller gave up")
    }
}
