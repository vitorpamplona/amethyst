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

import com.vitorpamplona.agents.AgentEvent
import com.vitorpamplona.agents.ContentBlock
import com.vitorpamplona.claudecode.protocol.ClaudeEvents
import com.vitorpamplona.claudecode.protocol.ClaudeMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ClaudeEventsTest {
    private fun event(line: String) = ClaudeEvents.of(assertNotNull(ClaudeMessage.parse(line)))

    // Recorded from claude 2.1.322 (`-p --output-format stream-json`) running a project skill.
    private val skillLaunched =
        """{"type":"user","message":{"role":"user","content":[{"type":"tool_result","tool_use_id":"toolu_016k1bLV1bYDUwHGugYkgpzX","content":"Launching skill: hello"}]},"parent_tool_use_id":null,"session_id":"2887091f-a669-4216-b6fc-7a50f7616a44","uuid":"6749ee65-546f-4d95-8117-ecaafb35952b","timestamp":"2026-11-24T00:58:01.982Z","tool_use_result":{"success":true,"commandName":"hello"}}"""
    private val skillBody =
        """{"type":"user","message":{"role":"user","content":[{"type":"text","text":"Base directory for this skill: /work/.claude/skills/hello\n\nBase directory note. Reply with exactly: hi there\n"}]},"parent_tool_use_id":null,"session_id":"2887091f-a669-4216-b6fc-7a50f7616a44","uuid":"a5c9440e-565d-40e9-af78-6cdf8e03de56","timestamp":"2026-11-24T00:58:01.980Z","isSynthetic":true}"""

    @Test
    fun aSkillsInstructionsStayOutOfTheChat() {
        assertNull(event(skillBody))
    }

    @Test
    fun aSkillsInstructionsStayOutOfASubagentToo() {
        assertNull(event(skillBody.replace("\"parent_tool_use_id\":null", "\"parent_tool_use_id\":\"toolu_parent\"")))
    }

    @Test
    fun theSkillCallStillGetsItsResult() {
        val results = assertIs<AgentEvent.ToolResults>(event(skillLaunched))
        val result = assertIs<ContentBlock.ToolResult>(results.content.single())
        assertEquals("Launching skill: hello", result.text)
    }

    @Test
    fun aNoteTheCliWritesForTheUserStillShows() {
        val interrupted =
            """{"type":"user","message":{"role":"user","content":[{"type":"text","text":"[Request interrupted by user]"}]},"parent_tool_use_id":null,"session_id":"s","uuid":"u"}"""
        val results = assertIs<AgentEvent.ToolResults>(event(interrupted))
        assertEquals("[Request interrupted by user]", (results.content.single() as ContentBlock.Text).text)
    }
}
