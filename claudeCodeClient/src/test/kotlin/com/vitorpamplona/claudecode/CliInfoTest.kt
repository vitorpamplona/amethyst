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
import com.vitorpamplona.claudecode.protocol.ClaudeInfo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CliInfoTest {
    // Trimmed from a real `initialize` answer (CLI 2.1.325).
    private val answer =
        """
        {"commands":[
          {"name":"context","description":"Show context usage","argumentHint":""},
          {"name":"startup-hook-skill","description":"Creating startup hooks","argumentHint":"","aliases":["session-start-hook"]},
          {"description":"no name, skipped"}
        ],
        "models":[
          {"value":"default","resolvedModel":"claude-opus-5-5","displayName":"Default (recommended)","description":"Opus 5.5 · Best for everyday, complex tasks"},
          {"value":"haiku","resolvedModel":"claude-haiku-5-5","displayName":"Haiku 5.5","description":"Fastest for quick answers"}
        ],
        "claude_code_version":"2.1.325","pid":518}
        """.trimIndent()

    @Test
    fun readsCommandsAndModels() {
        val info = ClaudeInfo.parse(Json.parseToJsonElement(answer).jsonObject)!!
        assertEquals(listOf("context", "startup-hook-skill"), info.commands.map { it.name })
        assertEquals(listOf("session-start-hook"), info.commands[1].aliases)
        assertEquals(listOf("default", "haiku"), info.models.map { it.value })
        assertEquals("claude-haiku-5-5", info.models[1].resolvedModel)
        assertEquals("2.1.325", info.version)
        assertNull(ClaudeInfo.parse(null))
    }

    @Test
    fun cutsAResumedSessionAtAReply() {
        val args = ClaudeCodeLauncher(executable = { File("/bin/claude") }).arguments(ClaudeCodeOptions(cwd = File("/w"), resumeSessionId = "s1", forkSession = true, resumeSessionAt = "u9"))
        val at = args.indexOf("--resume-session-at")
        assertEquals("u9", args[at + 1])
        assertEquals(true, "--fork-session" in args)
    }
}
