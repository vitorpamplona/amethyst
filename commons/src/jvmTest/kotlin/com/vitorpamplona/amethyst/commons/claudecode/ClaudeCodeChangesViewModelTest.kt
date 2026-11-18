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
import kotlin.test.assertIs
import kotlin.test.assertSame

/** The Changes screen's state against a real git branch. */
class ClaudeCodeChangesViewModelTest {
    private lateinit var tmp: File
    private lateinit var repo: File
    private lateinit var scope: CoroutineScope
    private var viewModel: ClaudeCodeChangesViewModel? = null

    // One thread, as the UI's main thread is.
    private val main: ExecutorCoroutineDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(main)
        tmp = Files.createTempDirectory("changes").toFile()
        repo = File(tmp, "repo").apply { mkdirs() }
        git("init", "--quiet", "-b", "main")
        File(repo, "README.md").writeText("app\n")
        git("add", ".")
        git("commit", "--quiet", "-m", "init")
        git("checkout", "--quiet", "-b", "feature")
        File(repo, "committed.txt").writeText("on the branch\n")
        git("add", ".")
        git("commit", "--quiet", "-m", "branch work")
        File(repo, "README.md").writeText("app, edited\n")
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @AfterTest
    fun tearDown() {
        // Its reads come back to Main: they must end before Main goes, or they fail in a later test.
        viewModel?.let {
            runBlocking {
                it.viewModelScope.coroutineContext.job
                    .cancelAndJoin()
            }
        }
        scope.cancel()
        Dispatchers.resetMain()
        main.close()
        tmp.deleteRecursively()
    }

    private fun git(vararg args: String) {
        val result = GitWorkspace.run(repo, listOf("git", "-c", "user.email=t@t", "-c", "user.name=T") + args)
        assertEquals(0, result.code, result.output)
    }

    private suspend fun ClaudeCodeChangesViewModel.loaded(scope: ChangesScope): ChangesState.Loaded =
        withTimeout(10_000) {
            while (true) {
                val s = state.value
                if (s is ChangesState.Loaded && s.scope == scope) return@withTimeout s
                delay(10)
            }
            @Suppress("UNREACHABLE_CODE")
            error("unreachable")
        }

    @Test
    fun switchingScopesShowsEachOnesChangesAndComesBackToTheSameRead() =
        runBlocking {
            val service =
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
            val chat = service.newChat(repo)
            val viewModel = withContext(main) { ClaudeCodeChangesViewModel(service, chat.key) }
            this@ClaudeCodeChangesViewModelTest.viewModel = viewModel

            // Opens on what isn't committed: there is some.
            val uncommitted = viewModel.loaded(ChangesScope.Uncommitted)
            assertEquals(listOf("README.md"), uncommitted.patch.files.map { it.displayPath })

            withContext(main) { viewModel.show(ChangesScope.Branch) }
            val branch = viewModel.loaded(ChangesScope.Branch)
            assertEquals(setOf("README.md", "committed.txt"), branch.patch.files.mapTo(HashSet()) { it.displayPath })
            assertIs<ChangesState.Loaded>(viewModel.state.value)

            // Back again: no new read, the same patch.
            withContext(main) { viewModel.show(ChangesScope.Uncommitted) }
            assertSame(uncommitted.patch, viewModel.loaded(ChangesScope.Uncommitted).patch)

            // A refresh reads the folder again.
            File(repo, "README.md").writeText("app, edited twice\n")
            File(repo, "other.txt").writeText("x\n")
            git("add", "other.txt")
            withContext(main) { viewModel.reload() }
            withTimeout(10_000) {
                while ((viewModel.state.value as? ChangesState.Loaded)?.patch?.files?.size != 2) delay(10)
            }
        }
}
