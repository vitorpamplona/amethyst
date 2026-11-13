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
package com.vitorpamplona.claudecode.process

import com.vitorpamplona.claudecode.protocol.PermissionMode
import kotlinx.coroutines.CoroutineScope
import java.io.File
import java.io.IOException

/** How to start a session. */
data class ClaudeCodeOptions(
    /** The folder Claude Code works in. */
    val cwd: File,
    /** Continue this session instead of starting a new one. */
    val resumeSessionId: String? = null,
    /** With [resumeSessionId]: continue in a new session that starts as a copy of that one. */
    val forkSession: Boolean = false,
    /** A model alias (`opus`, `sonnet`) or full name; null for the user's default. */
    val model: String? = null,
    val permissionMode: PermissionMode? = null,
    /** Stream text as it is written, not only whole messages. */
    val includePartialMessages: Boolean = true,
    /** More folders the tools may read and write. */
    val addDirs: List<File> = emptyList(),
    /** Tool rules the session may never use, e.g. `Bash(git push:*)`; nothing can approve them. */
    val disallowedTools: List<String> = emptyList(),
    /** Any other CLI flags, appended last. */
    val extraArgs: List<String> = emptyList(),
)

/** Starts sessions; [ClaudeCodeLauncher] in the app, a scripted one in tests. */
fun interface ClaudeCodeStarter {
    suspend fun start(
        options: ClaudeCodeOptions,
        scope: CoroutineScope,
    ): ClaudeCodeConnection
}

/** Starts the user's installed `claude` in stream-json mode. */
class ClaudeCodeLauncher(
    private val executable: () -> File? = { ClaudeExecutable.find() },
    private val environment: Map<String, String> = System.getenv(),
) : ClaudeCodeStarter {
    /** The command line [start] runs, without the executable. Public for tests and logs. */
    fun arguments(options: ClaudeCodeOptions): List<String> =
        buildList {
            add("--print")
            add("--input-format")
            add("stream-json")
            add("--output-format")
            add("stream-json")
            add("--verbose")
            // Without this the CLI denies anything that would ask, instead of asking the host.
            add("--permission-prompt-tool")
            add("stdio")
            if (options.includePartialMessages) add("--include-partial-messages")
            options.resumeSessionId?.let {
                add("--resume")
                add(it)
                if (options.forkSession) add("--fork-session")
            }
            options.model?.let {
                add("--model")
                add(it)
            }
            options.permissionMode?.let {
                add("--permission-mode")
                add(it.wire)
            }
            options.addDirs.forEach {
                add("--add-dir")
                add(it.absolutePath)
            }
            if (options.disallowedTools.isNotEmpty()) {
                add("--disallowedTools")
                add(options.disallowedTools.joinToString(","))
            }
            addAll(options.extraArgs)
        }

    /**
     * Starts a session and returns its connection, already initialized. Throws
     * [ClaudeCodeNotFoundException] when no `claude` executable is found, and [IOException] when
     * it can't be started.
     */
    override suspend fun start(
        options: ClaudeCodeOptions,
        scope: CoroutineScope,
    ): ClaudeCodeConnection {
        val exe = executable() ?: throw ClaudeCodeNotFoundException()
        if (!options.cwd.isDirectory) throw IOException("Not a folder: ${options.cwd}")
        val process =
            ProcessBuilder(listOf(exe.absolutePath) + arguments(options))
                .directory(options.cwd)
                .apply {
                    environment().clear()
                    environment().putAll(childEnvironment(exe))
                }.start()
        val connection = ClaudeCodeConnection(ProcessTransport(process), scope)
        connection.initialize()
        return connection
    }

    /**
     * The parent's environment with the folders Claude Code and its tools usually live in added
     * to PATH: an app started from a desktop launcher (macOS Finder, a .desktop file) gets a
     * minimal PATH that misses Homebrew, npm and ~/.local/bin.
     */
    fun childEnvironment(exe: File): Map<String, String> {
        val env = environment.toMutableMap()
        val pathKey = env.keys.firstOrNull { it.equals("PATH", ignoreCase = true) } ?: "PATH"
        val current = env[pathKey].orEmpty().split(File.pathSeparator).filter { it.isNotBlank() }
        val extra = listOfNotNull(exe.parentFile?.absolutePath) + ClaudeExecutable.searchDirs(environment).map { it.absolutePath }
        env[pathKey] = (current + extra).distinct().joinToString(File.pathSeparator)
        return env
    }
}

class ClaudeCodeNotFoundException : IOException("The claude executable was not found")
