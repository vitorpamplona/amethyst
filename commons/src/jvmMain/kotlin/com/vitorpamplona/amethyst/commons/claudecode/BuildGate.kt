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

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** The build gate: the repository's own pre-push checks, run in the checkout before publishing. */
@Immutable
sealed interface GateState {
    /** The checkout has no gate script. */
    data object None : GateState

    data object NotRun : GateState

    /** The last run checked other work: the branch has changed since. */
    data object Outdated : GateState

    data class Running(
        val tail: String,
    ) : GateState

    data class Passed(
        val tail: String,
    ) : GateState

    data class Failed(
        val tail: String,
    ) : GateState

    /** The user stopped it; publishing waits for a run that passes. */
    data class Cancelled(
        val tail: String,
    ) : GateState

    /** Whether the work may be published: the checks passed on it, or the repository has none. */
    val allowsPublishing: Boolean get() = this is Passed || this == None
}

/**
 * The repository's pre-push checks (`.git-hooks/pre-push`) for one checkout, whichever way its
 * branch is published: a proposal, a pull request. One per checkout ([ClaudeCodeService.buildGate]),
 * so a run outlives the screen that started it, and a pass on one screen counts on the other.
 *
 * A pass is for the work it checked: the commit and any change not yet committed. Once either
 * changes, the gate is [GateState.Outdated] and publishing waits for another run.
 */
class BuildGate(
    /** The checkout's top folder. */
    val root: File,
    private val scope: CoroutineScope,
) {
    private val script = File(root, ".git-hooks/pre-push")

    /** The last run, and the work it checked ([workKey]). */
    private data class Run(
        val state: GateState,
        val work: String?,
    )

    private val run = MutableStateFlow(Run(GateState.NotRun, null))

    /** The work as last looked at ([recheck]); null before the first look. */
    private val workNow = MutableStateFlow<String?>(null)

    /** The checks' state for the work as it is now. */
    val state: StateFlow<GateState> = combine(run, workNow, ::stateOf).stateIn(scope, SharingStarted.Eagerly, GateState.NotRun)

    /** [state] from what is known this moment, without waiting for [state] to catch up. */
    fun stateNow(): GateState = stateOf(run.value, workNow.value)

    private fun stateOf(
        run: Run,
        work: String?,
    ): GateState =
        when {
            !script.isFile -> GateState.None
            // A finished run counts only for the work it checked.
            (run.state is GateState.Passed || run.state is GateState.Failed) && (work == null || run.work != work) -> GateState.Outdated
            else -> run.state
        }

    /** Looks at the work again: a commit or an edit since the last run outdates it. */
    suspend fun recheck() {
        workNow.value = workKey(root)
    }

    private var job: Job? = null
    private var process: Process? = null

    /**
     * The last [LOG_MAX] characters of the run, as lines. Old lines drop off the front one at a
     * time, where trimming one big string moved all of it on every line.
     */
    private val log = ArrayDeque<String>()
    private var logChars = 0

    private fun appendLine(line: String) =
        synchronized(log) {
            log.addLast(line)
            logChars += line.length + 1
            while (logChars > LOG_MAX && log.size > 1) logChars -= log.removeFirst().length + 1
        }

    /** The last [TAIL] characters, for the screen. */
    private fun tail(): String =
        synchronized(log) {
            val lines = ArrayList<String>()
            var chars = 0
            for (i in log.indices.reversed()) {
                val line = log[i]
                if (chars + line.length + 1 > TAIL && lines.isNotEmpty()) break
                lines.add(line)
                chars += line.length + 1
            }
            lines.asReversed().joinToString("\n", postfix = "\n")
        }

    private val _logFile = MutableStateFlow<File?>(null)

    /** Where the last run's whole output is, for an editor or a terminal. */
    val logFile: StateFlow<File?> = _logFile.asStateFlow()

    /** The last run's output, up to the last [LOG_MAX] characters. */
    fun fullLog(): String = synchronized(log) { log.joinToString("\n", postfix = "\n") }

    /** Runs the checks on the work as it is now; a run already going goes on. */
    @Synchronized
    fun start() {
        if (job?.isActive == true || !script.isFile) return
        run.value = Run(GateState.Running(""), null)
        synchronized(log) {
            log.clear()
            logChars = 0
        }
        job =
            scope.launch(Dispatchers.IO) {
                // The work as the run starts: what it checks. A commit made while it runs isn't.
                val work = workKey(root)
                workNow.value = work
                // In the git dir: a build/ folder the repository does not ignore would show as a change.
                val file = File(GitWorkspace.privateDir(root) ?: File(root, "build"), "claude-code-gate.log")
                if (!file.parentFile.isDirectory && !file.parentFile.mkdirs()) Log.w(TAG) { "could not create ${file.parentFile}" }
                _logFile.value = file
                val code =
                    try {
                        val started =
                            ProcessBuilder(bash(), script.absolutePath)
                                .directory(root)
                                .redirectErrorStream(true)
                                .apply {
                                    environment().putAll(AndroidSdk.environment())
                                    environment()["PREPUSH_KMP_METADATA"] = "1"
                                }.start()
                        process = started
                        // A pre-push hook reads the pushed refs on stdin; there are none.
                        started.outputStream.close()
                        file.bufferedWriter().use { out ->
                            started.inputStream.bufferedReader().useLines { lines ->
                                // A build prints thousands of lines a second: the screen gets the tail every so often.
                                var shownAt = 0L
                                lines.forEach { line ->
                                    out.write(line)
                                    out.newLine()
                                    appendLine(line)
                                    val now = System.currentTimeMillis()
                                    if (now - shownAt >= SHOW_MS && run.value.state is GateState.Running) {
                                        shownAt = now
                                        run.value = Run(GateState.Running(tail()), null)
                                    }
                                }
                            }
                        }
                        runInterruptible { started.waitFor() }
                    } catch (e: IOException) {
                        appendLine(e.message ?: "the checks could not run")
                        -1
                    } finally {
                        process = null
                    }
                // Committed to while it ran: the pass is for the work before, and shows as outdated.
                // Looked at before the run's end is published, so whoever sees it end finds no git of
                // the gate's still reading the checkout: on Windows, git's open mapping of a file
                // makes a write to it fail ("a file with a user-mapped section open").
                recheck()
                val tail = tail()
                run.value =
                    when {
                        run.value.state is GateState.Cancelled -> Run(GateState.Cancelled(tail), null)
                        code == 0 -> Run(GateState.Passed(tail), work)
                        else -> Run(GateState.Failed(tail), work)
                    }
            }
    }

    /** Stops the running checks and everything they started (Gradle and its workers included). */
    fun cancel() {
        val running = process ?: return
        run.value = Run(GateState.Cancelled((run.value.state as? GateState.Running)?.tail.orEmpty()), null)
        // The script first, listing its children while they are still its own: stopped after them,
        // it would see its Gradle end and go on to its next step.
        val children = running.descendants().toList()
        running.destroy()
        children.forEach { it.destroy() }
    }

    companion object {
        private const val TAG = "BuildGate"

        /**
         * The bash that runs the checks. On Windows a bare `bash` is looked up in System32 before
         * the PATH, where it can be WSL's launcher, which can't run a script at a Windows path:
         * there it is Git for Windows' own bash, found from the `git` on [path] (`cmd\git.exe`,
         * `bin\git.exe` or `mingw64\bin\git.exe`, with bash in `bin\` or `usr\bin\`), else a
         * `bash.exe` on [path] outside the Windows folder. Elsewhere, and when none is found, `bash`.
         */
        internal fun bash(
            windows: Boolean = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true),
            path: String? = System.getenv("PATH"),
            windowsDir: String? = System.getenv("SystemRoot"),
        ): String {
            if (!windows) return "bash"
            val git = PushGuard.gitOnPath(path)
            val besideGit =
                generateSequence(git?.parentFile) { it.parentFile }
                    .take(3)
                    .flatMap { dir -> sequenceOf(File(dir, "bin/bash.exe"), File(dir, "usr/bin/bash.exe")) }
            val onPath =
                path
                    .orEmpty()
                    .split(File.pathSeparator)
                    .filter { it.isNotBlank() && (windowsDir == null || !it.startsWith(windowsDir, ignoreCase = true)) }
                    .asSequence()
                    .map { File(it, "bash.exe") }
            return (besideGit + onPath).firstOrNull { it.isFile }?.absolutePath ?: "bash"
        }

        private const val NO_LOCKS = "--no-optional-locks"
        private const val TAIL = 6_000
        private const val LOG_MAX = 2_000_000
        private const val SHOW_MS = 100L

        /**
         * What the checks see in [root]: its commit, and a digest of what isn't committed (the
         * diff and the untracked files' names). Null when git can't tell.
         */
        suspend fun workKey(root: File): String? =
            withContext(Dispatchers.IO) {
                val head = GitWorkspace.git(root, "rev-parse", "HEAD")?.trim() ?: return@withContext null
                // Only looks: without --no-optional-locks, status and diff refresh the index under
                // index.lock, and a `git add` or commit running beside them (the user's, an agent's)
                // fails with "index.lock exists".
                val status = GitWorkspace.git(root, NO_LOCKS, "status", "--porcelain", "--untracked-files=all") ?: return@withContext null
                if (status.isBlank()) return@withContext head
                val digest = MessageDigest.getInstance("SHA-256")
                digest.update(status.toByteArray())
                GitWorkspace.git(root, NO_LOCKS, "diff", "HEAD", "--binary")?.let { digest.update(it.toByteArray()) }
                head + "+" + digest.digest().joinToString("") { "%02x".format(it) }
            }
    }
}
