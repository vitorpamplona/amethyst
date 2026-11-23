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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** One step of the plan the agent keeps for itself, as its task tools last left it. */
@Immutable
data class AgentTask(
    /** The CLI's id ("3"), or the creating call's id while its result hasn't come back. */
    val id: String,
    val subject: String,
    /** What the task says while it runs ("Running tests"), when the agent gave one. */
    val activeForm: String?,
    val status: Status,
) {
    enum class Status { PENDING, IN_PROGRESS, COMPLETED }
}

/**
 * The agent's task list, read back from its task tool calls in the order they were made:
 *
 * - `TaskCreate {subject, activeForm}` adds a pending task; its result names the id
 *   ("Task #3 created successfully: …"). Should that wording change, ids are counted from 1 in
 *   creation order, as the CLI numbers them.
 * - `TaskUpdate {taskId, status, subject, activeForm}` changes one; status `deleted` removes it.
 * - `TodoWrite {todos: [{content, status, activeForm}]}` (older CLIs, and the SDK mode of some)
 *   replaces the whole list.
 *
 * Only the main conversation's calls count: a subagent's are inside its own call. A call the CLI
 * refused changes nothing. [inputOf] reads a call's input; a caller that folds the same calls
 * again and again passes one that remembers.
 */
fun List<ChatItem>.agentTasks(inputOf: (ChatItem.ToolCall) -> JsonObject? = { parseToolInput(it.input) }): List<AgentTask> {
    // Insertion order is the order the agent created them, which is the order it shows them.
    val tasks = LinkedHashMap<String, AgentTask>()
    var created = 0
    forEach { item ->
        val call = item as? ChatItem.ToolCall ?: return@forEach
        if (call.name !in TASK_TOOLS || call.isError) return@forEach
        val input = inputOf(call) ?: return@forEach
        when (call.name) {
            TASK_CREATE -> {
                val subject = input.text("subject") ?: return@forEach
                created++
                val result = call.result
                val id =
                    when {
                        // Still running: no id yet, so nothing can update it.
                        result == null -> call.toolUseId
                        else -> CREATED_ID.find(result)?.groupValues?.get(1) ?: created.toString()
                    }
                tasks[id] = AgentTask(id, subject, input.text("activeForm"), AgentTask.Status.PENDING)
            }
            TASK_UPDATE -> {
                val id = input.text("taskId") ?: return@forEach
                val task = tasks[id] ?: return@forEach
                val status = input.text("status")
                if (status == "deleted") {
                    tasks.remove(id)
                } else {
                    tasks[id] =
                        task.copy(
                            subject = input.text("subject") ?: task.subject,
                            activeForm = input.text("activeForm") ?: task.activeForm,
                            status = status?.let(::statusOf) ?: task.status,
                        )
                }
            }
            TODO_WRITE -> {
                val todos = input["todos"] as? JsonArray ?: return@forEach
                tasks.clear()
                todos.forEachIndexed { index, element ->
                    val todo = element as? JsonObject ?: return@forEachIndexed
                    val content = todo.text("content") ?: return@forEachIndexed
                    val id = todo.text("id") ?: "todo-$index"
                    tasks[id] = AgentTask(id, content, todo.text("activeForm"), statusOf(todo.text("status")) ?: AgentTask.Status.PENDING)
                }
            }
        }
    }
    return tasks.values.toList()
}

/** Whether the list still has work in it: once every task is done, the plan is finished. */
val List<AgentTask>.hasOpenWork: Boolean get() = any { it.status != AgentTask.Status.COMPLETED }

private const val TASK_CREATE = "TaskCreate"
private const val TASK_UPDATE = "TaskUpdate"
private const val TODO_WRITE = "TodoWrite"
private val TASK_TOOLS = setOf(TASK_CREATE, TASK_UPDATE, TODO_WRITE)

private val CREATED_ID = Regex("""Task #(\w+)""")

private val lenient = Json { ignoreUnknownKeys = true }

internal fun parseToolInput(input: String): JsonObject? = runCatching { lenient.parseToJsonElement(input) as? JsonObject }.getOrNull()

private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

private fun statusOf(value: String?): AgentTask.Status? =
    when (value) {
        "pending" -> AgentTask.Status.PENDING
        "in_progress" -> AgentTask.Status.IN_PROGRESS
        "completed" -> AgentTask.Status.COMPLETED
        else -> null
    }
