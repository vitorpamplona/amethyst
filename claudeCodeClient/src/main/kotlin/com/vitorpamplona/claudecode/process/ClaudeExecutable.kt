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

import java.io.File

/** Finds the user's `claude` executable. */
object ClaudeExecutable {
    private val isWindows = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

    private val names = if (isWindows) listOf("claude.exe", "claude.cmd", "claude") else listOf("claude")

    /**
     * `CLAUDE_CODE_EXECUTABLE` if set, then PATH, then the folders the installers use. The same
     * search the user's shell would do, plus the places a desktop app's short PATH misses.
     */
    fun find(environment: Map<String, String> = System.getenv()): File? {
        environment["CLAUDE_CODE_EXECUTABLE"]?.let { File(it) }?.takeIf { it.canExecute() }?.let { return it }
        val pathDirs =
            environment.entries
                .firstOrNull { it.key.equals("PATH", ignoreCase = true) }
                ?.value
                .orEmpty()
                .split(File.pathSeparator)
                .filter { it.isNotBlank() }
                .map { File(it) }
        return (pathDirs + searchDirs(environment)).firstNotNullOfOrNull { dir ->
            names.map { File(dir, it) }.firstOrNull { it.isFile && it.canExecute() }
        }
    }

    /** Where the native installer, npm and Homebrew put `claude`. */
    fun searchDirs(environment: Map<String, String> = System.getenv()): List<File> {
        val home = environment["HOME"] ?: environment["USERPROFILE"] ?: System.getProperty("user.home")
        return buildList {
            add(File(home, ".local/bin"))
            add(File(home, ".claude/local"))
            add(File(home, ".npm-global/bin"))
            add(File(home, ".bun/bin"))
            add(File("/opt/homebrew/bin"))
            add(File("/usr/local/bin"))
            add(File("/usr/bin"))
            environment["APPDATA"]?.let { add(File(it, "npm")) }
            environment["LOCALAPPDATA"]?.let { add(File(it, "Programs/claude")) }
        }
    }
}
