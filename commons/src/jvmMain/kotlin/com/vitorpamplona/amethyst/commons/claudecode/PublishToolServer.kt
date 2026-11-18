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

import com.vitorpamplona.agents.HostTool
import com.vitorpamplona.agents.HostToolResult
import com.vitorpamplona.agents.HostToolServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import java.io.File

/**
 * How an agent in a workstream asks to publish its work: [REQUEST] records a pull request (title,
 * description, draft or not) for the user, who reviews it on the Pull requests screen and opens it,
 * or dismisses it. The agent's pushes stay blocked: this only asks.
 *
 * Reads the workstream of [folder] at each call, so a workstream still cloning when the chat
 * started is found once it is made.
 */
class PublishToolServer(
    private val folder: File,
    private val workspaceOf: (File) -> AgentWorkspace?,
    /** Each repository's commits beyond its base and files not committed, now. */
    private val status: suspend (AgentWorkspace) -> List<WorkspaceRepoStatus>,
    /** Saves the request and tells the user. */
    private val request: (AgentWorkspace, WorkspaceRepo, PublishRequest) -> Unit,
) : HostToolServer {
    override val name = NAME

    override val tools =
        listOf(
            HostTool(
                REQUEST,
                "Asks the user to open a pull request with your work in this workstream. You can't push or open pull requests " +
                    "yourself; this hands them a ready pull request (title and description) to review and open. Commit everything first. " +
                    "Call it once per repository when the work is ready, then tell the user it is waiting on the Pull requests screen. " +
                    "Calling it again replaces the earlier request.",
                Json
                    .parseToJsonElement(
                        """{"type":"object","properties":{""" +
                            """"repo":{"type":"string","description":"The repository's folder in the workstream; may be left out when there is only one"},""" +
                            """"title":{"type":"string","description":"The pull request's title"},""" +
                            """"body":{"type":"string","description":"What changed and why, and how it was tested, in Markdown"},""" +
                            """"draft":{"type":"boolean","description":"Open it as a draft"}},""" +
                            """"required":["title"]}""",
                    ).jsonObject,
            ),
        )

    override suspend fun call(
        tool: String,
        arguments: JsonObject,
    ): HostToolResult {
        if (tool != REQUEST) return HostToolResult("No tool named $tool", isError = true)
        val workspace = workspaceOf(folder) ?: return HostToolResult("This folder is not a workstream: there is nothing to publish from here.", isError = true)
        val title = arguments.string("title")?.trim().orEmpty()
        if (title.isEmpty()) return HostToolResult("Give the pull request a title.", isError = true)
        val wanted =
            arguments
                .string("repo")
                ?.trim()
                ?.removeSuffix("/")
                ?.ifEmpty { null }
        val repo =
            when {
                wanted != null -> workspace.repos.firstOrNull { it.name == wanted }
                workspace.repos.size == 1 -> workspace.repos.single()
                else -> null
            } ?: return HostToolResult("Say which repository: ${workspace.repos.joinToString { it.name }}.", isError = true)
        val now = status(workspace).firstOrNull { it.repo.name == repo.name }
        if (now != null && now.uncommitted > 0) {
            return HostToolResult("${repo.name} has ${now.uncommitted} files not committed. Commit them (or discard them), then ask again.", isError = true)
        }
        if (now == null || now.ahead == 0) {
            return HostToolResult("${repo.branch} has no commits beyond ${repo.baseBranch} in ${repo.name}: there is nothing to publish yet.", isError = true)
        }
        val draft = (arguments["draft"] as? JsonPrimitive)?.booleanOrNull ?: false
        request(workspace, repo, PublishRequest(title, arguments.string("body")?.trim().orEmpty(), draft, System.currentTimeMillis()))
        return HostToolResult(
            "Asked the user to open a ${if (draft) "draft " else ""}pull request from ${repo.branch} into ${repo.baseBranch} for ${repo.name} " +
                "(${now.ahead} commits). They review it on the Pull requests screen and decide; don't push or open it yourself. " +
                "Tell them it is waiting there.",
        )
    }

    override fun describe(
        tool: String,
        arguments: JsonObject,
    ): String? = if (tool == REQUEST) arguments.string("title")?.let { "Ask you to open a pull request: $it" } else null

    private fun JsonObject.string(key: String) = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    companion object {
        const val NAME = "amethyst-publish"
        const val REQUEST = "request_publish"

        /** Added to the workstream's brief when the agent has the tool. */
        const val BRIEF = "When the work is committed and ready, call request_publish: it hands the user a pull request to review and open."
    }
}
