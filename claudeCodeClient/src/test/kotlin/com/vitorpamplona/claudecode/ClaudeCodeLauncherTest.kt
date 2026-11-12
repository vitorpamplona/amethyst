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
package com.vitorpamplona.claudecode

import com.vitorpamplona.claudecode.process.ClaudeCodeLauncher
import com.vitorpamplona.claudecode.process.ClaudeCodeOptions
import com.vitorpamplona.claudecode.protocol.PermissionMode
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClaudeCodeLauncherTest {
    @Test
    fun argumentsAskTheHostForPermissions() {
        val args = ClaudeCodeLauncher().arguments(ClaudeCodeOptions(cwd = File("/work")))
        assertEquals(
            listOf(
                "--print",
                "--input-format",
                "stream-json",
                "--output-format",
                "stream-json",
                "--verbose",
                "--permission-prompt-tool",
                "stdio",
                "--include-partial-messages",
            ),
            args,
        )
    }

    @Test
    fun resumeForkModelAndMode() {
        val args =
            ClaudeCodeLauncher().arguments(
                ClaudeCodeOptions(
                    cwd = File("/work"),
                    resumeSessionId = "abc",
                    forkSession = true,
                    model = "sonnet",
                    permissionMode = PermissionMode.ACCEPT_EDITS,
                    includePartialMessages = false,
                ),
            )
        val tail = args.drop(8)
        assertEquals(listOf("--resume", "abc", "--fork-session", "--model", "sonnet", "--permission-mode", "acceptEdits"), tail)
    }

    @Test
    fun childPathGetsTheExecutablesFolder() {
        val launcher = ClaudeCodeLauncher(environment = mapOf("PATH" to "/usr/bin", "HOME" to "/home/u"))
        val env = launcher.childEnvironment(File("/opt/claude/bin/claude"))
        val path = env.getValue("PATH").split(File.pathSeparator)
        assertEquals("/usr/bin", path.first())
        assertTrue("/opt/claude/bin" in path)
        assertTrue("/home/u/.local/bin" in path)
    }
}
