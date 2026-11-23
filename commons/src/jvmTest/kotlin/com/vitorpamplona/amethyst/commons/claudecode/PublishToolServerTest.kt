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

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The agent's request to publish, against real workspaces. */
class PublishToolServerTest {
    private lateinit var tmp: File
    private lateinit var workspaces: AgentWorkspaces
    private val asked = mutableListOf<Pair<String, PublishRequest>>()

    private fun git(
        dir: File,
        vararg args: String,
    ): String {
        val result = GitWorkspace.run(dir, listOf("git", "-c", "user.email=t@t", "-c", "user.name=T") + args)
        assertEquals(0, result.code, result.output)
        return result.output
    }

    private fun remote(name: String): String {
        val bare = File(tmp, "remotes/$name.git").apply { mkdirs() }
        git(bare, "init", "--quiet", "--bare", "-b", "main")
        val seed = File(tmp, "seed-$name").apply { mkdirs() }
        git(seed, "init", "--quiet", "-b", "main")
        File(seed, "README.md").writeText("# $name\n")
        git(seed, "add", ".")
        git(seed, "commit", "--quiet", "-m", "init")
        git(bare, "fetch", "--quiet", seed.absolutePath, "main:main")
        return fileUrl(bare)
    }

    private fun tool(folder: File) =
        PublishToolServer(
            folder = folder,
            workspaceOf = { workspaces.of(it) },
            status = { workspaces.status(it) },
            request = { _, repo, request -> asked.add(repo.name to request) },
        )

    private fun args(vararg pairs: Pair<String, Any>) =
        JsonObject(
            pairs.associate { (k, v) -> k to if (v is Boolean) JsonPrimitive(v) else JsonPrimitive(v.toString()) },
        )

    @BeforeTest
    fun setUp() {
        tmp = Files.createTempDirectory("publish-tool").toFile()
        workspaces = AgentWorkspaces(File(tmp, "agents"))
    }

    @AfterTest
    fun tearDown() {
        tmp.deleteRecursively()
    }

    @Test
    fun committedWorkIsHandedToTheUserAsARequest() =
        runBlocking {
            val ws = workspaces.create(listOf(remote("app")), null, branch = "agent/p")
            val app = File(ws.dir, "app")
            val publish = tool(File(ws.dir))

            // Nothing committed yet: nothing to ask for.
            assertTrue(publish.call(PublishToolServer.REQUEST, args("title" to "Fix")).isError)
            File(app, "fix.txt").writeText("fixed\n")
            val uncommitted = publish.call(PublishToolServer.REQUEST, args("title" to "Fix"))
            assertTrue(uncommitted.isError)
            assertTrue(uncommitted.text.contains("not committed"))
            git(app, "add", ".")
            git(app, "commit", "--quiet", "-m", "fix")
            // A title is the least a pull request needs.
            assertTrue(publish.call(PublishToolServer.REQUEST, args("title" to " ")).isError)

            val done = publish.call(PublishToolServer.REQUEST, args("title" to "Fix the relay list", "body" to "Why and how", "draft" to true))
            assertFalse(done.isError, done.text)
            assertTrue(done.text.contains("don't push"))
            val (repo, request) = asked.single()
            assertEquals("app", repo)
            assertEquals(Triple("Fix the relay list", "Why and how", true), Triple(request.title, request.body, request.draft))
        }

    @Test
    fun withSeveralRepositoriesTheAgentSaysWhich() =
        runBlocking {
            val ws = workspaces.create(listOf(remote("app"), remote("lib")), null, branch = "agent/q")
            val lib = File(ws.dir, "lib")
            File(lib, "x.txt").writeText("x\n")
            git(lib, "add", ".")
            git(lib, "commit", "--quiet", "-m", "x")
            val publish = tool(File(ws.dir, "lib"))
            val which = publish.call(PublishToolServer.REQUEST, args("title" to "X"))
            assertTrue(which.isError)
            assertTrue(which.text.contains("app") && which.text.contains("lib"))
            assertFalse(publish.call(PublishToolServer.REQUEST, args("title" to "X", "repo" to "lib/")).isError)
            assertEquals("lib", asked.single().first)
        }

    @Test
    fun outsideAWorkstreamThereIsNothingToPublish() =
        runBlocking {
            val result = tool(tmp).call(PublishToolServer.REQUEST, args("title" to "X"))
            assertTrue(result.isError)
            assertTrue(asked.isEmpty())
        }

    @Test
    fun aRequestIsKeptWithItsWorkspaceUntilAnswered() =
        runBlocking {
            val ws = workspaces.create(listOf(remote("app")), null, branch = "agent/r")
            val request = PublishRequest("Title", "Body", draft = false, requestedAt = 5)
            workspaces.recordPublishRequest(ws.id, "app", request)
            assertEquals(mapOf("app" to request), workspaces.list().single().publishRequests)
            // Through a free and a restore too.
            val back = workspaces.restore(workspaces.free(workspaces.list().single()))
            assertEquals(mapOf("app" to request), back.publishRequests)
            workspaces.recordPublishRequest(ws.id, "app", null)
            assertTrue(
                workspaces
                    .list()
                    .single()
                    .publishRequests
                    .isEmpty(),
            )
            assertNull(workspaces.recordPublishRequest("gone", "app", request))
        }

    @Test
    fun anOpenPullRequestTakesTheNewCommitsSoTheAgentIsNotAskedToRequestAnother() =
        runBlocking {
            val ws = workspaces.create(listOf(remote("app")), null, branch = "agent/o")
            val app = File(ws.dir, "app")
            File(app, "more.txt").writeText("more\n")
            git(app, "add", ".")
            git(app, "commit", "--quiet", "-m", "more")
            workspaces.recordPullRequest(ws.id, "app", PullRequestInfo("https://github.com/o/app/pull/4", "OPEN", isDraft = false))
            val refused = tool(File(ws.dir)).call(PublishToolServer.REQUEST, args("title" to "More"))
            assertTrue(refused.isError)
            assertTrue(refused.text.contains("https://github.com/o/app/pull/4"), refused.text)
            assertTrue(asked.isEmpty())
            // Merged since: new work needs a new pull request, and the agent may ask for it.
            workspaces.recordPullRequest(ws.id, "app", PullRequestInfo("https://github.com/o/app/pull/4", "MERGED", isDraft = false))
            assertFalse(tool(File(ws.dir)).call(PublishToolServer.REQUEST, args("title" to "More")).isError)
            assertEquals("app", asked.single().first)
        }

    @Test
    fun aRequestWaitsUntilAPullRequestIsOpenOrOpenedAfterIt() {
        val request = PublishRequest("Next", requestedAt = 1_000)
        assertEquals(request, ClaudeCodeWorkspaceViewModel.pendingRequest(request, null))
        // An earlier pull request of the branch, merged or closed before the agent asked: still waiting.
        assertEquals(request, ClaudeCodeWorkspaceViewModel.pendingRequest(request, PullRequestInfo("u", "MERGED", isDraft = false, createdAt = 500)))
        assertEquals(request, ClaudeCodeWorkspaceViewModel.pendingRequest(request, PullRequestInfo("u", "CLOSED", isDraft = false)))
        // Answered: one is open, or one was opened after the request (and merged since).
        assertNull(ClaudeCodeWorkspaceViewModel.pendingRequest(request, PullRequestInfo("u", "OPEN", isDraft = false, createdAt = 500)))
        assertNull(ClaudeCodeWorkspaceViewModel.pendingRequest(request, PullRequestInfo("u", "MERGED", isDraft = false, createdAt = 2_000)))
        assertNull(ClaudeCodeWorkspaceViewModel.pendingRequest(null, null))
    }

    @Test
    fun aPullRequestsCreationTimeIsRead() {
        val pr = GitHubPullRequests.parseState("""{"url":"https://github.com/o/app/pull/1","state":"MERGED","isDraft":false,"createdAt":"2026-01-02T03:04:05Z"}""")
        assertEquals(
            java.time.Instant
                .parse("2026-01-02T03:04:05Z")
                .toEpochMilli(),
            pr?.createdAt,
        )
    }
}
