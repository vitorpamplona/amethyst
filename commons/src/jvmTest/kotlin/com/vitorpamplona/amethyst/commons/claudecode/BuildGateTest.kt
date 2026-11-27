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

    /** [state] as a pass, or a failure that says what the checks printed: a bare type mismatch tells nothing on a CI runner. */
    private fun passed(state: GateState): GateState.Passed =
        state as? GateState.Passed
            ?: throw AssertionError("The checks didn't pass: $state")

    /** An executable file at [path] under [dir], as a Git for Windows install has it. */
    private fun exe(
        dir: File,
        path: String,
    ): File =
        File(dir, path).apply {
            check(parentFile.isDirectory || parentFile.mkdirs()) { "could not create $parentFile" }
            writeText("")
            check(setExecutable(true)) { "could not make $this executable" }
        }

    @Test
    fun onWindowsTheChecksRunInGitsOwnBash() {
        // Git for Windows puts cmd\ on the PATH, with git.exe; its bash is in bin\.
        val git = File(tmp, "Git")
        exe(git, "cmd/git.exe")
        val bash = exe(git, "bin/bash.exe")
        assertEquals(bash.absolutePath, BuildGate.bash(windows = true, path = File(git, "cmd").absolutePath, windowsDir = null))

        // A git with no bash beside it: a bash.exe on the PATH, but never the Windows folder's (WSL's launcher).
        val system32 = File(tmp, "Windows/System32").apply { mkdirs() }
        exe(system32, "bash.exe")
        val other = File(tmp, "tools").apply { mkdirs() }
        val otherBash = exe(other, "bash.exe")
        val path = listOf(system32, other).joinToString(File.pathSeparator) { it.absolutePath }
        assertEquals(otherBash.absolutePath, BuildGate.bash(windows = true, path = path, windowsDir = File(tmp, "Windows").absolutePath))

        // Nothing found, or not Windows: plain bash.
        assertEquals("bash", BuildGate.bash(windows = true, path = system32.absolutePath, windowsDir = File(tmp, "Windows").absolutePath))
        assertEquals("bash", BuildGate.bash(windows = false, path = File(git, "cmd").absolutePath, windowsDir = null))
    }

    @Test
    fun aPassCountsForTheWorkItChecked() =
        runBlocking<Unit> {
            val gate = BuildGate(repo, scope)
            gate.recheck()
            assertSame(GateState.NotRun, gate.stateNow())

            val passed = passed(gate.runToEnd())
            assertTrue(passed.tail.contains("checking"), passed.tail)
            assertTrue(gate.stateNow().allowsPublishing)
        }

    @Test
    fun aCommitAfterThePassOutdatesIt() =
        runBlocking<Unit> {
            val gate = BuildGate(repo, scope)
            passed(gate.runToEnd())

            commit("more.txt", "new work\n")
            gate.recheck()

            assertSame(GateState.Outdated, gate.stateNow())
            assertFalse(gate.stateNow().allowsPublishing)
        }

    @Test
    fun anEditNotCommittedOutdatesItToo() =
        runBlocking<Unit> {
            val gate = BuildGate(repo, scope)
            passed(gate.runToEnd())

            File(repo, "README.md").writeText("app, edited\n")
            gate.recheck()
            assertSame(GateState.Outdated, gate.stateNow())

            // Put back as it was: the work is the one that passed again.
            File(repo, "README.md").writeText("app\n")
            gate.recheck()
            assertIs<GateState.Passed>(gate.stateNow())
        }

    @Test
    fun theRunEndsOnlyOnceItHasLookedAtTheWorkAgain() =
        runBlocking<Unit> {
            // Checks that commit as they run: the pass is for the work before.
            File(repo, ".git-hooks/pre-push").writeText(
                "#!/bin/bash\ngit -c user.email=t@t -c user.name=T commit --quiet --allow-empty -m during\nexit 0\n",
            )
            git("commit", "--quiet", "-am", "committing checks")
            val gate = BuildGate(repo, scope)

            // The end is seen already outdated, never as a pass: the gate looked at the work (git
            // status and diff) before it said the run ended, so no git of its own is still reading
            // the checkout when the caller goes on to edit it. On Windows that git's mapping of a
            // file made the edit fail ("a file with a user-mapped section open").
            assertSame(GateState.Outdated, gate.runToEnd())
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
