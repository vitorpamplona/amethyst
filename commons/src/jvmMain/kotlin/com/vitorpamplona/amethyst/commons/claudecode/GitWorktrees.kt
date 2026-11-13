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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * A new branch checked out in its own folder, so a Claude Code session can change a repository
 * without touching the checkout the user (or the running app) works from.
 */
object GitWorktrees {
    /** The repository root [dir] is in, or null when it isn't in a git repository. */
    suspend fun repositoryRoot(dir: File): File? =
        withContext(Dispatchers.IO) {
            val out = git(dir, "rev-parse", "--show-toplevel") ?: return@withContext null
            File(out.trim()).takeIf { it.isDirectory }
        }

    /**
     * Creates branch [branch] from the current HEAD in a new worktree next to the repository,
     * in `<repo>-worktrees/<branch>`, and returns its folder.
     */
    suspend fun create(
        repo: File,
        branch: String = defaultBranchName(),
    ): File =
        withContext(Dispatchers.IO) {
            val root = File(repo.absoluteFile.parentFile, repo.name + "-worktrees")
            if (!root.isDirectory && !root.mkdirs()) throw IOException("Could not create $root")
            val dir = File(root, branch.replace('/', '-'))
            if (dir.exists()) throw IOException("$dir already exists")
            val result = run(repo, listOf("git", "worktree", "add", "-b", branch, dir.absolutePath, "HEAD"))
            if (result.code != 0) throw IOException(result.output.trim().ifEmpty { "git worktree add failed" })
            dir
        }

    /**
     * Removes the worktree at [dir] once its work is merged; the branch stays. Refuses when it has
     * uncommitted changes, as `git worktree remove` does.
     */
    suspend fun remove(dir: File) =
        withContext(Dispatchers.IO) {
            val common = git(dir, "rev-parse", "--path-format=absolute", "--git-common-dir")?.trim() ?: throw IOException("$dir is not a git worktree")
            val main = File(common).parentFile ?: throw IOException("no main checkout for $dir")
            val result = run(main, listOf("git", "worktree", "remove", dir.absolutePath))
            if (result.code != 0) throw IOException(result.output.trim().ifEmpty { "git worktree remove failed" })
        }

    fun defaultBranchName(): String = "claude/" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))

    private fun git(
        dir: File,
        vararg args: String,
    ): String? {
        val result = run(dir, listOf("git") + args)
        return if (result.code == 0) result.output else null
    }

    private class Result(
        val code: Int,
        val output: String,
    )

    private fun run(
        dir: File,
        command: List<String>,
    ): Result =
        try {
            val process =
                ProcessBuilder(command)
                    .directory(dir)
                    .redirectErrorStream(true)
                    .start()
            val output = process.inputStream.bufferedReader().readText()
            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                Result(-1, "git timed out")
            } else {
                Result(process.exitValue(), output)
            }
        } catch (e: IOException) {
            Result(-1, e.message ?: "git could not run")
        }
}

/**
 * Starts the desktop app from an Amethyst checkout (a session's worktree), to try what the
 * session changed. It runs as its own process with its own window; the output goes to
 * `build/claude-code-run.log` in the checkout.
 */
object AmethystCheckout {
    fun isCheckout(dir: File): Boolean = File(dir, "desktopApp").isDirectory && (File(dir, "gradlew").isFile || File(dir, "gradlew.bat").isFile)

    fun logFile(dir: File) = File(dir, "build/claude-code-run.log")

    suspend fun runDesktopApp(dir: File): Process =
        withContext(Dispatchers.IO) {
            val windows = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)
            val gradlew = if (windows) File(dir, "gradlew.bat").absolutePath else "./gradlew"
            val log = logFile(dir)
            if (!log.parentFile.isDirectory && !log.parentFile.mkdirs()) throw IOException("Could not create ${log.parentFile}")
            ProcessBuilder(gradlew, ":desktopApp:run")
                .directory(dir)
                .redirectErrorStream(true)
                .redirectOutput(log)
                .start()
        }
}
