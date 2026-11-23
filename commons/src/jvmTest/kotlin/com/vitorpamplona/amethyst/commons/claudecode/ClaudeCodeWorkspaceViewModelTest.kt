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

import androidx.lifecycle.viewModelScope
import com.vitorpamplona.claudecode.ClaudeBackend
import com.vitorpamplona.claudecode.process.ClaudeCodeStarter
import com.vitorpamplona.claudecode.session.ClaudeSessionStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.Executors
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The Pull requests screen of a freed workstream: what needs its clones brings them back. */
class ClaudeCodeWorkspaceViewModelTest {
    private lateinit var tmp: File
    private lateinit var scope: CoroutineScope
    private lateinit var service: ClaudeCodeService
    private var viewModel: ClaudeCodeWorkspaceViewModel? = null
    private val realGh = GitHubPullRequests.runGh

    // One thread, as the UI's main thread is.
    private val main: ExecutorCoroutineDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(main)
        // No GitHub here: whatever `gh` is installed is never asked.
        GitHubPullRequests.runGh = { _, _, _ -> GitWorkspace.Result(1, "offline") }
        tmp = Files.createTempDirectory("workspace-vm").toFile()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        service =
            ClaudeCodeService(
                backend =
                    ClaudeBackend(
                        ClaudeSessionStore(File(tmp, "config")),
                        ClaudeCodeStarter { _, _ -> throw IOException("not in tests") },
                        findExecutable = { null },
                    ),
                scope = scope,
                agentWorkspaces = AgentWorkspaces(File(tmp, "agents")),
            )
    }

    @AfterTest
    fun tearDown() {
        viewModel?.let {
            runBlocking {
                it.viewModelScope.coroutineContext.job
                    .cancelAndJoin()
            }
        }
        scope.cancel()
        Dispatchers.resetMain()
        main.close()
        GitHubPullRequests.runGh = realGh
        tmp.deleteRecursively()
    }

    private fun git(
        dir: File,
        vararg args: String,
    ): String {
        val result = GitWorkspace.run(dir, listOf("git", "-c", "user.email=t@t", "-c", "user.name=T") + args)
        assertEquals(0, result.code, result.output)
        return result.output
    }

    private fun remote(): String {
        val bare = File(tmp, "remotes/app.git").apply { mkdirs() }
        git(bare, "init", "--quiet", "--bare", "-b", "main")
        val seed = File(tmp, "seed").apply { mkdirs() }
        git(seed, "init", "--quiet", "-b", "main")
        File(seed, "README.md").writeText("# app\n")
        git(seed, "add", ".")
        git(seed, "commit", "--quiet", "-m", "init")
        git(bare, "fetch", "--quiet", seed.absolutePath, "main:main")
        return fileUrl(bare)
    }

    /** A freed workstream with one commit of its own, and the screen open on it. */
    private suspend fun freedWorkstream(): Pair<AgentWorkspace, ClaudeCodeWorkspaceViewModel> {
        val ws = service.agentWorkspaces.create(listOf(remote()), "Back", branch = "agent/b", agentId = "claude")
        val app = File(ws.dir, "app")
        File(app, "work.txt").writeText("done\n")
        git(app, "add", ".")
        git(app, "commit", "--quiet", "-m", "work")
        service.refresh()
        assertEquals(listOf(ws.id), service.cleanUp(listOf(ws)).map { it.id })
        val chat = service.newChat(File(ws.dir))
        val vm = withContext(main) { ClaudeCodeWorkspaceViewModel(service, chat.key) }
        viewModel = vm
        return ws to vm
    }

    @Test
    fun whatNeedsTheClonesBringsAFreedWorkstreamBackFirst() =
        runBlocking {
            val (ws, vm) = freedWorkstream()
            // Seen without its clones: from what the free kept.
            withTimeout(10_000) { while (vm.loading.value || vm.rows.value.isEmpty()) delay(10) }
            assertEquals(
                1,
                vm.rows.value
                    .single()
                    .status.ahead,
            )
            assertFalse(File(ws.dir, "app").exists())

            val opened = CompletableDeferred<Boolean>()
            withContext(main) { vm.withClones { opened.complete(File(ws.dir, "app/work.txt").isFile) } }
            assertTrue(withTimeout(30_000) { opened.await() }, "the screen opened before the clones were back")
            assertFalse(vm.restoring.value)
            assertFalse(assertNotNull(service.workspaceAt(ws.dir)).isFreed)
            assertTrue(vm.errors.value.isEmpty())
        }

    @Test
    fun clonesThatCannotComeBackSayWhyAndOpenNothing() =
        runBlocking {
            val (ws, vm) = freedWorkstream()
            // The repository and its mirror are gone: nothing to clone from.
            File(tmp, "remotes/app.git").deleteRecursively()
            service.agentWorkspaces.mirrorDir(ws.repos.single().url).deleteRecursively()

            var opened = false
            withContext(main) { vm.withClones { opened = true } }
            withTimeout(30_000) { while (vm.errors.value[""] == null) delay(10) }
            assertFalse(opened)
            assertFalse(vm.restoring.value)
            assertTrue(assertNotNull(service.workspaceAt(ws.dir)).isFreed)
        }
}
