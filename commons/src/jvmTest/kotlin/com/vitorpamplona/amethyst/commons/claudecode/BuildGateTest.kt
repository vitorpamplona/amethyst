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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The build gate runs the repository's pre-push script, and its pass is for the work it checked. */
class BuildGateTest {
    private lateinit var tmp: File
    private lateinit var repo: File

    /** What the stub checks exit with: outside the repository, so setting it changes no work. */
    private lateinit var exitCode: File
    private lateinit var scope: CoroutineScope

    @BeforeTest
    fun setUp() {
        tmp = Files.createTempDirectory("gate").toFile()
        repo = File(tmp, "repo").apply { mkdirs() }
        exitCode = File(tmp, "exit").apply { writeText("0") }
        git("init", "--quiet", "-b", "main")
        File(repo, ".git-hooks").mkdirs()
        File(repo, ".git-hooks/pre-push").writeText("#!/bin/bash\necho checking\nexit \$(cat '${exitCode.absolutePath}')\n")
        File(repo, "README.md").writeText("app\n")
        git("add", ".")
        git("commit", "--quiet", "-m", "init")
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @AfterTest
    fun tearDown() {
        scope.cancel()
        tmp.deleteRecursively()
    }

    private fun git(vararg args: String) {
        val result = GitWorkspace.run(repo, listOf("git", "-c", "user.email=t@t", "-c", "user.name=T") + args)
        assertEquals(0, result.code, result.output)
    }

    private fun commit(
        file: String,
        text: String,
    ) {
        File(repo, file).writeText(text)
        git("add", ".")
        git("commit", "--quiet", "-m", file)
    }

    /** Runs the checks and waits for them to end. */
    private suspend fun BuildGate.runToEnd(): GateState {
        start()
        return withTimeout(30_000) { state.first { it !is GateState.Running && it !is GateState.NotRun } }
    }

    @Test
    fun aPassCountsForTheWorkItChecked() =
        runBlocking<Unit> {
            val gate = BuildGate(repo, scope)
            gate.recheck()
            assertSame(GateState.NotRun, gate.stateNow())

            val passed = assertIs<GateState.Passed>(gate.runToEnd())
            assertTrue(passed.tail.contains("checking"), passed.tail)
            assertTrue(gate.stateNow().allowsPublishing)
        }

    @Test
    fun aCommitAfterThePassOutdatesIt() =
        runBlocking<Unit> {
            val gate = BuildGate(repo, scope)
            assertIs<GateState.Passed>(gate.runToEnd())

            commit("more.txt", "new work\n")
            gate.recheck()

            assertSame(GateState.Outdated, gate.stateNow())
            assertFalse(gate.stateNow().allowsPublishing)
        }

    @Test
    fun anEditNotCommittedOutdatesItToo() =
        runBlocking<Unit> {
            val gate = BuildGate(repo, scope)
            assertIs<GateState.Passed>(gate.runToEnd())

            File(repo, "README.md").writeText("app, edited\n")
            gate.recheck()
            assertSame(GateState.Outdated, gate.stateNow())

            // Put back as it was: the work is the one that passed again.
            File(repo, "README.md").writeText("app\n")
            gate.recheck()
            assertIs<GateState.Passed>(gate.stateNow())
        }

    @Test
    fun aFailureHoldsPublishingBack() =
        runBlocking<Unit> {
            exitCode.writeText("1")
            val gate = BuildGate(repo, scope)
            assertIs<GateState.Failed>(gate.runToEnd())
            assertFalse(gate.stateNow().allowsPublishing)
        }

    @Test
    fun aRepositoryWithoutChecksPublishesFreely() =
        runBlocking<Unit> {
            git("rm", "--quiet", ".git-hooks/pre-push")
            git("commit", "--quiet", "-m", "no checks")
            val gate = BuildGate(repo, scope)
            gate.recheck()
            assertSame(GateState.None, gate.stateNow())
            assertTrue(gate.stateNow().allowsPublishing)
        }

    @Test
    fun everyScreenGetsTheSameGateForACheckout() =
        runBlocking<Unit> {
            val service = ClaudeCodeService(scope = scope)
            val fromRoot = service.buildGateAt(repo)
            // From a folder inside the checkout, as a chat started there asks.
            val inside = File(repo, "sub").apply { mkdirs() }
            assertSame(fromRoot, service.buildGate(inside))
        }
}
