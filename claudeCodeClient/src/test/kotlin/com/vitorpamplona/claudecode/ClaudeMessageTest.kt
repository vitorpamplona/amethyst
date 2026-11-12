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

import com.vitorpamplona.claudecode.protocol.ClaudeMessage
import com.vitorpamplona.claudecode.protocol.ContentBlock
import com.vitorpamplona.claudecode.protocol.PermissionMode
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClaudeMessageTest {
    private fun parse(name: String) = Fixtures.lines(name).mapNotNull { ClaudeMessage.parse(it) }

    @Test
    fun everyRecordedLineParses() {
        listOf("turn-approve.jsonl", "turn-deny.jsonl", "turn-interrupt-partial.jsonl", "turn-resume.jsonl").forEach { name ->
            val lines = Fixtures.lines(name)
            assertEquals(lines.size, parse(name).size, name)
        }
    }

    @Test
    fun approveTurnHasInitPromptAndResult() {
        val messages = parse("turn-approve.jsonl")

        val init = messages.filterIsInstance<ClaudeMessage.Init>().single()
        assertEquals("717459ae-7826-499a-990f-fa51269cdd6b", init.sessionId)
        assertEquals("/work", init.cwd)
        assertEquals(PermissionMode.DEFAULT, init.permissionMode)

        val prompt = messages.filterIsInstance<ClaudeMessage.ControlRequest>().single()
        val permission = assertNotNull(prompt.permission)
        assertEquals("Write", permission.toolName)
        assertEquals("/work/hello.txt", permission.summary)
        assertEquals("hi", (permission.input["content"] as JsonPrimitive).content)
        assertEquals(1, permission.suggestions.size)
        assertNotNull(permission.toolUseId)

        val toolUse =
            messages
                .filterIsInstance<ClaudeMessage.Assistant>()
                .flatMap { it.content }
                .filterIsInstance<ContentBlock.ToolUse>()
                .single()
        assertEquals(permission.toolUseId, toolUse.id)

        val toolResult =
            messages
                .filterIsInstance<ClaudeMessage.User>()
                .flatMap { it.content }
                .filterIsInstance<ContentBlock.ToolResult>()
                .single()
        assertFalse(toolResult.isError)
        assertTrue(toolResult.text.startsWith("File created successfully"))

        val result = messages.last() as ClaudeMessage.Result
        assertEquals("success", result.subtype)
        assertFalse(result.isError)
        assertNotNull(result.totalCostUsd)
    }

    @Test
    fun deniedToolComesBackAsAnErrorResult() {
        val messages = parse("turn-deny.jsonl")
        val permission = messages.filterIsInstance<ClaudeMessage.ControlRequest>().single().permission!!
        assertEquals("Bash", permission.toolName)
        assertEquals("echo hi > x.txt", permission.summary)

        val toolResult =
            messages
                .filterIsInstance<ClaudeMessage.User>()
                .flatMap { it.content }
                .filterIsInstance<ContentBlock.ToolResult>()
                .single()
        assertTrue(toolResult.isError)
        assertEquals("User declined", toolResult.text)
    }

    @Test
    fun interruptedTurnStreamsTextThenEndsInError() {
        val messages = parse("turn-interrupt-partial.jsonl")
        val streamed =
            messages
                .filterIsInstance<ClaudeMessage.StreamEvent>()
                .mapNotNull { it.textDelta }
                .joinToString("")
        assertTrue(streamed.startsWith("1\n2\n3\n"), streamed.take(20))

        val interruptAnswer = messages.filterIsInstance<ClaudeMessage.ControlResponse>().single { it.requestId == "int-1" }
        assertTrue(interruptAnswer.isSuccess)

        val result = messages.last() as ClaudeMessage.Result
        assertTrue(result.isError)
        assertEquals("error_during_execution", result.subtype)
        assertNull(result.result)
    }

    @Test
    fun strayOutputIsIgnored() {
        assertNull(ClaudeMessage.parse("Warning: something"))
        assertNull(ClaudeMessage.parse("{not json"))
        assertTrue(ClaudeMessage.parse("{\"type\":\"brand_new_type\"}") is ClaudeMessage.Other)
    }
}
