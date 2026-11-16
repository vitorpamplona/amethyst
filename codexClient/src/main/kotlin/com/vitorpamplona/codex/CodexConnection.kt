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
package com.vitorpamplona.codex

import com.vitorpamplona.agents.AgentConnection
import com.vitorpamplona.agents.AgentEvent
import com.vitorpamplona.agents.AgentInfo
import com.vitorpamplona.agents.AgentStartOptions
import com.vitorpamplona.agents.ContentBlock
import com.vitorpamplona.agents.ImageInput
import com.vitorpamplona.agents.LineTransport
import com.vitorpamplona.agents.ModelChoice
import com.vitorpamplona.agents.PermissionDecision
import com.vitorpamplona.agents.PermissionMode
import com.vitorpamplona.agents.PermissionRequest
import com.vitorpamplona.agents.PermissionSuggestion
import com.vitorpamplona.codex.CodexItems.long
import com.vitorpamplona.codex.CodexItems.text
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** How Codex runs commands for each of the chat's permission modes: its approval policy and its sandbox. */
object CodexModes {
    fun approvalPolicy(mode: PermissionMode) =
        when (mode) {
            // Asks before any command it doesn't know to be safe.
            PermissionMode.DEFAULT -> "untrusted"
            else -> "on-request"
        }

    /** For `thread/start`, `thread/resume`, `thread/fork`. */
    fun sandbox(mode: PermissionMode) = if (mode == PermissionMode.PLAN) "read-only" else "workspace-write"

    /** For `turn/start`, which takes the policy as an object. */
    fun sandboxPolicy(mode: PermissionMode) = buildJsonObject { put("type", if (mode == PermissionMode.PLAN) "readOnly" else "workspaceWrite") }

    /**
     * Whether [command] runs one of [blocked] (command prefixes such as `gh pr create`) anywhere
     * in it: inside a shell wrapper (`/bin/zsh -lc 'gh pr create'`), a chain (`a && git push`),
     * by a full path (`/usr/bin/git push`), or past git's own options (`git -C . push`).
     * Matching words anywhere errs toward declining: `echo git push` is declined too.
     */
    fun runsBlocked(
        command: String,
        blocked: List<String>,
    ): Boolean {
        if (blocked.isEmpty()) return false
        val words =
            command
                .split(SEPARATORS)
                .filter { it.isNotEmpty() }
                .map { it.substringAfterLast('/') }
                .let(::withoutGitOptions)
        return blocked.any { rule ->
            val ruleWords = rule.split(' ').filter { it.isNotEmpty() }
            ruleWords.isNotEmpty() && words.windowed(ruleWords.size).any { it == ruleWords }
        }
    }

    /** Shell quoting, chaining and substitution: what can stand between two commands' words. */
    private val SEPARATORS = Regex("[\\s\"'`;&|()${'$'}{}<>]+")

    /** Git's options before its subcommand (`-C dir`, `-c key=value`, `--no-pager`), dropped. */
    private fun withoutGitOptions(words: List<String>): List<String> {
        val out = ArrayList<String>(words.size)
        var i = 0
        while (i < words.size) {
            val word = words[i++]
            out.add(word)
            if (word != "git") continue
            while (i < words.size && words[i].startsWith("-")) {
                val option = words[i++]
                if ((option == "-C" || option == "-c" || option == "--git-dir" || option == "--work-tree" || option == "--namespace") && i < words.size) i++
            }
        }
        return out
    }
}

/**
 * One `codex app-server` process running one thread. [open] does the handshake and starts,
 * resumes or forks the thread; after that, [events] carries everything the thread does.
 */
class CodexConnection(
    private val transport: LineTransport,
    private val scope: CoroutineScope,
) : AgentConnection {
    private val rpc = CodexRpc(transport, scope)
    private val output = Channel<AgentEvent>(Channel.UNLIMITED)

    override val events: Flow<AgentEvent> = output.receiveAsFlow()

    override val isRunning: Boolean get() = rpc.reader.isActive

    @Volatile
    override var info: AgentInfo? = null
        private set

    @Volatile
    var threadId: String? = null
        private set

    @Volatile
    private var turnId: String? = null

    /** Model and mode picked since the last turn: they go with the next `turn/start`. */
    @Volatile
    private var nextModel: String? = null

    @Volatile
    private var nextMode: PermissionMode? = null

    /** Items seen starting, by id: an approval names only the item, and a call's row is shown once. */
    private val items = ConcurrentHashMap<String, JsonObject>()
    private val shownCalls = ConcurrentHashMap.newKeySet<String>()

    /**
     * Commands this thread may not run ([AgentStartOptions.blockedCommands]): asked for one,
     * the connection declines it before the user sees it. In the workspace-write sandbox, which
     * has no network, a command that publishes can only run through such a request.
     */
    @Volatile
    private var blocked: List<String> = emptyList()

    /** Approvals waiting on the user: Codex's request id, by the id the chat knows. */
    private val openApprovals = ConcurrentHashMap<String, JsonElement>()

    init {
        scope.launch {
            try {
                rpc.incoming.collect { message ->
                    when (message) {
                        is CodexRpc.Incoming.Notification -> onNotification(message.method, message.params)
                        is CodexRpc.Incoming.Request -> onRequest(message)
                    }
                }
            } finally {
                output.close()
            }
        }
    }

    /** The handshake, then the thread [options] asks for. Throws [IOException] when Codex refuses. */
    suspend fun open(options: AgentStartOptions) {
        val hello =
            rpc.request(
                "initialize",
                buildJsonObject {
                    put(
                        "clientInfo",
                        buildJsonObject {
                            put("name", "amethyst")
                            put("version", "1")
                        },
                    )
                },
            )
        rpc.notify("initialized")
        info = AgentInfo(commands = emptyList(), models = models(), version = hello.text("userAgent"))
        // Asked only for the models: no thread, nothing saved.
        if (options.probeOnly) return
        blocked = options.blockedCommands
        val mode = options.permissionMode
        val common =
            buildMap<String, JsonElement> {
                put("cwd", JsonPrimitive(options.cwd.absolutePath))
                put("approvalPolicy", JsonPrimitive(CodexModes.approvalPolicy(mode)))
                put("sandbox", JsonPrimitive(CodexModes.sandbox(mode)))
                options.model?.let { put("model", JsonPrimitive(it)) }
                options.instructions?.let { put("developerInstructions", JsonPrimitive(it)) }
            }
        val resume = options.resumeSessionId
        val answer =
            when {
                resume == null -> rpc.request("thread/start", JsonObject(common))
                options.forkSession ->
                    rpc.request(
                        "thread/fork",
                        JsonObject(common + ("threadId" to JsonPrimitive(resume)) + (options.forkAt?.let { mapOf("lastTurnId" to JsonPrimitive(it)) } ?: emptyMap())),
                    )
                else -> rpc.request("thread/resume", JsonObject(common + ("threadId" to JsonPrimitive(resume)) + ("excludeTurns" to JsonPrimitive(true))))
            }
        val thread = answer["thread"] as? JsonObject ?: throw IOException("Codex started no thread")
        val id = thread.text("id") ?: throw IOException("Codex started no thread")
        threadId = id
        output.send(AgentEvent.SessionStarted(id, answer.text("model") ?: thread.text("model"), mode))
    }

    private suspend fun models(): List<ModelChoice> =
        try {
            (rpc.request("model/list")["data"] as? JsonArray).orEmpty().mapNotNull { element ->
                val model = element as? JsonObject ?: return@mapNotNull null
                if ((model["hidden"] as? JsonPrimitive)?.booleanOrNull == true) return@mapNotNull null
                val value = model.text("model") ?: model.text("id") ?: return@mapNotNull null
                ModelChoice(value, model.text("displayName") ?: value, model.text("description").orEmpty(), value)
            }
        } catch (e: IOException) {
            emptyList()
        }

    private suspend fun onNotification(
        method: String,
        params: JsonObject,
    ) {
        if (params.text("threadId")?.let { it != threadId } == true) return
        when (method) {
            "turn/started" -> turnId = (params["turn"] as? JsonObject)?.text("id")
            "item/started" -> {
                val item = params["item"] as? JsonObject ?: return
                items[CodexItems.id(item)] = item
                when {
                    CodexItems.type(item) == "agentMessage" -> output.send(AgentEvent.ReplyStarted)
                    else -> CodexItems.toolUse(item)?.let { showCall(it) }
                }
            }
            "item/agentMessage/delta" -> params.text("delta")?.let { output.send(AgentEvent.ReplyDelta(it)) }
            "item/completed" -> {
                val item = params["item"] as? JsonObject ?: return
                items[CodexItems.id(item)] = item
                CodexItems.agentText(item)?.let {
                    output.send(AgentEvent.Reply(listOf(ContentBlock.Text(it))))
                    return
                }
                CodexItems.toolUse(item)?.let { showCall(it) }
                CodexItems.toolResult(item)?.let { output.send(AgentEvent.ToolResults(listOf(it))) }
            }
            "thread/tokenUsage/updated" -> {
                val usage = params["tokenUsage"] as? JsonObject ?: return
                val last = (usage["last"] as? JsonObject)?.long("totalTokens")
                output.send(AgentEvent.ContextUsage(last, usage.long("modelContextWindow")))
            }
            "turn/completed" -> {
                val turn = params["turn"] as? JsonObject
                val status = turn?.text("status")
                turnId = null
                output.send(
                    AgentEvent.TurnEnded(
                        interrupted = status == "interrupted",
                        failure = if (status == "failed") (turn["error"] as? JsonObject)?.text("message") ?: "failed" else null,
                    ),
                )
            }
            "serverRequest/resolved" -> {
                // Answered elsewhere, or no longer needed.
                val requestId = params["requestId"]?.toString()?.trim('"') ?: return
                if (openApprovals.remove(requestId) != null) output.send(AgentEvent.ApprovalWithdrawn(requestId))
            }
        }
    }

    private suspend fun showCall(call: ContentBlock.ToolUse) {
        if (shownCalls.add(call.id)) output.send(AgentEvent.Reply(listOf(call)))
    }

    private suspend fun onRequest(request: CodexRpc.Incoming.Request) {
        val key = request.id.toString().trim('"')
        val params = request.params
        val itemId = params.text("itemId")
        val asked =
            when (request.method) {
                "item/commandExecution/requestApproval" -> {
                    val command = params.text("command") ?: items[itemId]?.text("command").orEmpty()
                    if (CodexModes.runsBlocked(command, blocked)) {
                        // Publishing is the user's step here: Codex hears no, the user isn't asked.
                        rpc.reply(request.id, buildJsonObject { put("decision", "decline") })
                        return
                    }
                    approval(key, itemId, "Bash", buildJsonObject { put("command", command) }, params.text("reason"), PermissionSuggestion.Rule("Bash", command))
                }
                "item/fileChange/requestApproval" -> {
                    val changes = items[itemId]?.get("changes") ?: JsonArray(emptyList())
                    approval(key, itemId, CodexItems.APPLY_PATCH, buildJsonObject { put("changes", changes) }, params.text("reason"), PermissionSuggestion.Rule(CodexItems.APPLY_PATCH, null))
                }
                else -> null
            }
        if (asked == null) {
            // Codex asks for things this client doesn't offer (user input forms, logins): say so, so it never waits.
            rpc.replyError(request.id, "Not supported by this client: ${request.method}")
            return
        }
        openApprovals[key] = request.id
        output.send(AgentEvent.ApprovalRequested(asked))
    }

    private fun approval(
        key: String,
        itemId: String?,
        tool: String,
        input: JsonObject,
        reason: String?,
        rule: PermissionSuggestion.Rule,
    ) = PermissionRequest(
        requestId = key,
        toolName = tool,
        displayName = null,
        description = reason,
        title = null,
        input = input,
        toolUseId = itemId,
        decisionReason = reason,
        blockedPath = null,
        // Codex's "don't ask again" is for the session: accept-for-session.
        suggestions = buildJsonArray { add(JsonPrimitive("acceptForSession")) },
        suppressAlwaysAllow = false,
        defaultToNo = false,
        agentId = null,
        parsedSuggestions = listOf(PermissionSuggestion.AddRules(listOf(rule), "allow", "session")),
    )

    override suspend fun send(
        text: String,
        images: List<ImageInput>,
    ) {
        val thread = threadId ?: throw IOException("Codex has no thread")
        val input =
            buildJsonArray {
                images.forEach { add(CodexItems.imageInput(it.mediaType, it.base64)) }
                if (text.isNotBlank()) add(CodexItems.textInput(text))
            }
        val params =
            buildMap<String, JsonElement> {
                put("threadId", JsonPrimitive(thread))
                put("input", input)
                nextModel?.let { put("model", JsonPrimitive(it)) }
                nextMode?.let { mode ->
                    put("approvalPolicy", JsonPrimitive(CodexModes.approvalPolicy(mode)))
                    put("sandboxPolicy", CodexModes.sandboxPolicy(mode))
                }
            }
        val turn = rpc.request("turn/start", JsonObject(params))["turn"] as? JsonObject
        turn?.text("id")?.let { turnId = it }
    }

    override suspend fun respond(
        request: PermissionRequest,
        decision: PermissionDecision,
    ) {
        val id = openApprovals.remove(request.requestId) ?: return
        val answer =
            when (decision) {
                is PermissionDecision.Allow -> if (decision.updatedPermissions != null) "acceptForSession" else "accept"
                is PermissionDecision.Deny -> if (decision.interrupt) "cancel" else "decline"
            }
        rpc.reply(id, buildJsonObject { put("decision", answer) })
    }

    override suspend fun interrupt() {
        val thread = threadId ?: return
        val turn = turnId ?: return
        try {
            rpc.request(
                "turn/interrupt",
                buildJsonObject {
                    put("threadId", thread)
                    put("turnId", turn)
                },
            )
        } catch (e: IOException) {
            // The turn already ended.
        }
    }

    override suspend fun setPermissionMode(mode: PermissionMode) {
        nextMode = mode
    }

    override suspend fun setModel(model: String?) {
        nextModel = model
    }

    override suspend fun rename(title: String): Boolean {
        val thread = threadId ?: return false
        return try {
            rpc.request(
                "thread/name/set",
                buildJsonObject {
                    put("threadId", thread)
                    put("name", title)
                },
            )
            true
        } catch (e: IOException) {
            false
        }
    }

    /** A request outside the chat's own (listing and reading threads). */
    suspend fun call(
        method: String,
        params: JsonObject,
    ) = rpc.request(method, params)

    override suspend fun close(): Int? {
        transport.closeInput()
        return transport.awaitExit()
    }

    override fun kill() = transport.kill()

    override fun stderrTail(): String = transport.stderrTail()
}
