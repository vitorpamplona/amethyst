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

import com.vitorpamplona.agents.ContentBlock
import com.vitorpamplona.agents.PlanStep
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgentTasksTest {
    private fun call(
        id: String,
        name: String,
        input: String,
    ) = ContentBlock.ToolUse(id, name, Json.parseToJsonElement(input) as JsonObject)

    private fun ChatTranscript.run(
        id: String,
        name: String,
        input: String,
        result: String,
        isError: Boolean = false,
    ) {
        addAssistantBlocks(listOf(call(id, name, input)))
        addUserBlocks(listOf(ContentBlock.ToolResult(id, result, isError)), typedByUser = false)
    }

    // Inputs and results as Claude Code 2.1.330 writes them.
    @Test
    fun taskCreateAndUpdateBuildTheList() {
        val transcript = ChatTranscript()
        transcript.run("t1", "TaskCreate", """{"subject":"Parse tools","description":"d","activeForm":"Parsing tools"}""", "Task #1 created successfully: Parse tools")
        transcript.run("t2", "TaskCreate", """{"subject":"Show the card","description":"d"}""", "Task #2 created successfully: Show the card")
        transcript.run("t3", "TaskCreate", """{"subject":"Push","description":"d"}""", "Task #3 created successfully: Push")
        transcript.run("t4", "TaskUpdate", """{"taskId":"1","status":"completed"}""", "Updated task #1 status")
        transcript.run("t5", "TaskUpdate", """{"taskId":"2","status":"in_progress","activeForm":"Drawing the card"}""", "Updated task #2 status")
        transcript.run("t6", "TaskUpdate", """{"taskId":"3","status":"deleted"}""", "Updated task #3 deleted")

        val tasks = transcript.tasks()
        assertEquals(listOf("1", "2"), tasks.map { it.id })
        assertEquals(AgentTask.Status.COMPLETED, tasks[0].status)
        assertEquals("Parsing tools", tasks[0].activeForm)
        assertEquals(AgentTask.Status.IN_PROGRESS, tasks[1].status)
        assertEquals("Drawing the card", tasks[1].activeForm)
        assertTrue(tasks.hasOpenWork)
    }

    @Test
    fun aCreateStillRunningShowsAsPending() {
        val transcript = ChatTranscript()
        transcript.addAssistantBlocks(listOf(call("t1", "TaskCreate", """{"subject":"Plan","description":"d"}""")))
        val task = transcript.tasks().single()
        assertEquals("Plan", task.subject)
        assertEquals(AgentTask.Status.PENDING, task.status)
    }

    @Test
    fun todoWriteReplacesTheList() {
        val transcript = ChatTranscript()
        transcript.run("t1", "TodoWrite", """{"todos":[{"content":"A","status":"completed","activeForm":"Doing A"},{"content":"B","status":"pending","activeForm":"Doing B"}]}""", "ok")
        transcript.run("t2", "TodoWrite", """{"todos":[{"content":"A","status":"completed","activeForm":"Doing A"},{"content":"B","status":"completed","activeForm":"Doing B"}]}""", "ok")
        val tasks = transcript.tasks()
        assertEquals(listOf("A", "B"), tasks.map { it.subject })
        assertFalse(tasks.hasOpenWork)
    }

    @Test
    fun refusedCallsAndOtherToolsChangeNothing() {
        val transcript = ChatTranscript()
        transcript.run("t1", "TaskCreate", """{"subject":"Plan","description":"d"}""", "Task #1 created successfully: Plan")
        transcript.run("t2", "TaskUpdate", """{"taskId":"1","status":"completed"}""", "The user declined", isError = true)
        transcript.run("t3", "Bash", """{"command":"ls"}""", "a.txt")
        assertEquals(AgentTask.Status.PENDING, transcript.tasks().single().status)
    }

    @Test
    fun aReportedPlanStandsInForTaskTools() {
        val transcript = ChatTranscript()
        transcript.run("t1", "TaskCreate", """{"subject":"From a tool","description":"d"}""", "Task #1 created successfully: From a tool")
        transcript.setPlan(listOf(PlanStep("Read", PlanStep.Status.COMPLETED), PlanStep("Fix", PlanStep.Status.IN_PROGRESS)))
        transcript.setPlan(listOf(PlanStep("Read", PlanStep.Status.COMPLETED), PlanStep("Fix", PlanStep.Status.COMPLETED)))
        val tasks = transcript.tasks()
        assertEquals(listOf("Read", "Fix"), tasks.map { it.subject })
        assertFalse(tasks.hasOpenWork)
    }

    @Test
    fun taskToolCallsStayOutOfTheChatRows() {
        val transcript = ChatTranscript()
        transcript.run("b1", "Bash", """{"command":"ls"}""", "a.txt")
        transcript.run("t1", "TaskCreate", """{"subject":"Plan","description":"d"}""", "Task #1 created successfully: Plan")
        transcript.run("b2", "Bash", """{"command":"pwd"}""", "/w")
        transcript.run("t2", "TaskUpdate", """{"taskId":"1","status":"completed"}""", "Updated task #1 status")
        val rows = transcript.snapshot().toChatRows()
        // The two commands fold into one group, with nothing of the plan between or in it.
        val group = rows.single() as ChatRow.ToolGroup
        assertEquals(listOf("b1", "b2"), group.calls.map { it.toolUseId })
    }
}
