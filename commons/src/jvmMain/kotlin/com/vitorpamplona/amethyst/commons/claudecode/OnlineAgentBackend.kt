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

import com.vitorpamplona.agents.AgentBackend
import com.vitorpamplona.agents.AgentCapabilities
import com.vitorpamplona.agents.AgentConnection
import com.vitorpamplona.agents.AgentEvent
import com.vitorpamplona.agents.AgentInfo
import com.vitorpamplona.agents.AgentNotInstalledException
import com.vitorpamplona.agents.AgentSessionInfo
import com.vitorpamplona.agents.AgentStartOptions
import com.vitorpamplona.agents.ContentBlock
import com.vitorpamplona.agents.HostToolResult
import com.vitorpamplona.agents.HostToolServer
import com.vitorpamplona.agents.ImageInput
import com.vitorpamplona.agents.ModelChoice
import com.vitorpamplona.agents.PermissionDecision
import com.vitorpamplona.agents.PermissionMode
import com.vitorpamplona.agents.PermissionRequest
import com.vitorpamplona.agents.TranscriptEntry
import com.vitorpamplona.amethyst.commons.agents.AgentProviderPresets
import com.vitorpamplona.amethyst.commons.frontpage.isAnthropic
import com.vitorpamplona.amethyst.commons.frontpage.isOpenAI
import com.vitorpamplona.amethyst.commons.frontpage.listChatModelIds
import com.vitorpamplona.amethyst.commons.frontpage.postChatCompletion
import com.vitorpamplona.amethyst.commons.frontpage.rankModels
import com.vitorpamplona.amethyst.commons.viewmodels.BaseAccountViewModel
import com.vitorpamplona.quartz.nipFAAgentKeys.tags.AgentKeyTag
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * An OpenAI-compatible chat-completions endpoint: what the user's AI key (NIP-FA) reaches.
 * [complete] sends one request body and returns the response's JSON; [models] lists what the key can use.
 */
interface ChatCompletionsApi {
    suspend fun models(key: AgentKeyTag): List<String>

    suspend fun complete(
        key: AgentKeyTag,
        body: String,
    ): String
}

/** A completion the endpoint refused or couldn't give, in words the chat shows. */
class ChatCompletionsException(
    message: String,
) : IOException(message)

/**
 * The assistant on the user's own AI key, when no agent program on this computer can run it: an
 * in-app tool loop over [ChatCompletionsApi], with the host's tools only, the same approvals, and
 * sessions saved in [store]. [access] is bound by the screen that shows the assistant.
 */
class OnlineAgentBackend(
    val store: OnlineSessionStore,
) : AgentBackend {
    /** The key and the endpoint client: whichever account's screen bound them last. */
    class Access(
        val key: () -> AgentKeyTag?,
        val api: ChatCompletionsApi,
    ) {
        companion object {
            /** The account's preferred AI key (NIP-FA), reached through the app's metered HTTP client. */
            fun of(accountViewModel: BaseAccountViewModel): Access =
                Access(
                    key = { accountViewModel.account.agentKeys.preferred() },
                    api =
                        object : ChatCompletionsApi {
                            override suspend fun models(key: AgentKeyTag) = accountViewModel.httpClientBuilder.listChatModelIds(key)

                            override suspend fun complete(
                                key: AgentKeyTag,
                                body: String,
                            ) = accountViewModel.httpClientBuilder.postChatCompletion(key, body)
                        },
                )
        }
    }

    @Volatile
    var access: Access? = null

    /** Whether the user has a key the assistant can use now. */
    fun isAvailable(): Boolean = access?.key?.invoke() != null

    override val id = ID

    override val displayName: String
        get() = access?.key?.invoke()?.let { "${AgentProviderPresets.displayName(it.url)} key" } ?: "AI key"

    override val shortName = "Assistant"

    override val capabilities =
        AgentCapabilities(
            slashCommands = false,
            editFromMessage = false,
            subagents = false,
            hostTools = true,
            modes = listOf(PermissionMode.DEFAULT),
            liveSessions = false,
        )

    /** Not a program: listed with the agents for its sessions, never offered as a coding agent. */
    override fun executable(): File? = null

    override suspend fun start(
        options: AgentStartOptions,
        scope: CoroutineScope,
    ): AgentConnection {
        val bound = access ?: throw AgentNotInstalledException(NO_KEY)
        val key = bound.key() ?: throw AgentNotInstalledException(NO_KEY)
        val models =
            try {
                rankForChat(bound.api.models(key))
            } catch (e: IOException) {
                Log.w(TAG, "Could not list the models of ${AgentProviderPresets.displayName(key.url)}", e)
                emptyList()
            }
        val saved = options.resumeSessionId?.let { store.load(it) }
        val session =
            saved ?: OnlineSession(
                id = UUID.randomUUID().toString(),
                title = null,
                cwd = options.cwd.path,
                createdAt = System.currentTimeMillis(),
                model = options.model ?: models.firstOrNull(),
                messages = emptyList(),
            )
        return OnlineAgentConnection(
            session = session.copy(model = options.model ?: session.model ?: models.firstOrNull()),
            key = key,
            api = bound.api,
            store = store,
            tools = options.hostTools,
            preApproved = options.preApprovedTools.toSet(),
            instructions = options.instructions,
            models = models,
            persist = options.persist && !options.probeOnly,
            scope = scope,
        )
    }

    override fun listSessions(cwd: String?): List<AgentSessionInfo> = store.list().filter { cwd == null || it.cwd == cwd }

    override fun session(sessionId: String): AgentSessionInfo? = store.load(sessionId)?.info()

    override fun transcript(sessionId: String): List<TranscriptEntry> = store.load(sessionId)?.transcript() ?: emptyList()

    override fun rename(
        sessionId: String,
        title: String,
    ) {
        val session = store.load(sessionId) ?: throw IOException("No session $sessionId")
        store.save(session.copy(title = title))
    }

    override fun delete(sessionId: String): Boolean = store.delete(sessionId)

    companion object {
        const val ID = "amethyst-online"
        private const val TAG = "OnlineAgentBackend"
        const val NO_KEY = "No AI key: add one in Settings › AI keys, or install Claude Code on this computer."

        /**
         * The models a chat offers, best first: a mid-size model of a known family before the
         * largest (a chat sends its whole history each turn, so the largest costs the most).
         */
        fun rankForChat(ids: List<String>): List<String> {
            val ranked = rankModels(ids)
            val preferred = ranked.filter { id -> CHAT_DEFAULTS.any { id.contains(it, ignoreCase = true) } }
            return (preferred + ranked).distinct().take(MAX_MODELS)
        }

        private val CHAT_DEFAULTS = listOf("sonnet", "gpt-5-mini", "gemini-2.5-flash", "gemini-flash")
        private const val MAX_MODELS = 40
    }
}

/** One saved conversation: the chat-completions messages as sent, so a resumed session goes on as it was. */
data class OnlineSession(
    val id: String,
    val title: String?,
    val cwd: String,
    val createdAt: Long,
    val model: String?,
    val messages: List<JsonObject>,
    val modifiedAt: Long = System.currentTimeMillis(),
) {
    fun info(): AgentSessionInfo {
        val prompts = messages.filter { it.str("role") == "user" }.mapNotNull { it.str("content") }
        return AgentSessionInfo(
            sessionId = id,
            title =
                title ?: prompts
                    .firstOrNull()
                    ?.lineSequence()
                    ?.firstOrNull()
                    ?.take(80) ?: id,
            customTitle = title,
            firstPrompt = prompts.firstOrNull(),
            lastPrompt = prompts.lastOrNull(),
            cwd = cwd,
            gitBranch = null,
            createdAt = createdAt,
            lastModified = modifiedAt,
        )
    }

    /** As the chat shows history: tool results on the user's side, as the agents write them. */
    fun transcript(): List<TranscriptEntry> =
        messages.mapIndexedNotNull { index, message ->
            val uuid = "$id-$index"
            when (message.str("role")) {
                "user" -> TranscriptEntry(uuid, TranscriptEntry.Role.USER, listOf(ContentBlock.Text(message.str("content") ?: PICTURES_SAVED)), null, null)
                "assistant" -> TranscriptEntry(uuid, TranscriptEntry.Role.ASSISTANT, assistantBlocks(message), null, model)
                "tool" -> TranscriptEntry(uuid, TranscriptEntry.Role.USER, listOf(ContentBlock.ToolResult(message.str("tool_call_id").orEmpty(), message.str("content").orEmpty(), message.str("is_error") == "true")), null, null)
                else -> null
            }
        }
}

/** The sessions of [OnlineAgentBackend]: one JSON file each in [dir]. */
class OnlineSessionStore(
    val dir: File,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private class Listed(
        val length: Long,
        val modified: Long,
        val info: AgentSessionInfo?,
    )

    /** What [list] read of each file, kept while the file stays the same: the list polls every few seconds. */
    private val listed = ConcurrentHashMap<String, Listed>()

    fun list(): List<AgentSessionInfo> {
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(SUFFIX) }.orEmpty()
        listed.keys.retainAll(files.mapTo(HashSet()) { it.name })
        return files
            .mapNotNull { file ->
                val length = file.length()
                val modified = file.lastModified()
                val cached = listed[file.name]?.takeIf { it.length == length && it.modified == modified }
                cached?.info ?: load(file.name.removeSuffix(SUFFIX))?.info().also { listed[file.name] = Listed(length, modified, it) }
            }.sortedByDescending { it.lastModified }
    }

    fun load(id: String): OnlineSession? {
        if (!SAFE_ID.matches(id)) return null
        val file = File(dir, id + SUFFIX)
        if (!file.isFile) return null
        return try {
            val obj = json.parseToJsonElement(file.readText()).jsonObject
            OnlineSession(
                id = id,
                title = obj.str("title"),
                cwd = obj.str("cwd").orEmpty(),
                createdAt = (obj["createdAt"] as? JsonPrimitive)?.longOrNull ?: file.lastModified(),
                model = obj.str("model"),
                messages = (obj["messages"] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList(),
                modifiedAt = file.lastModified(),
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }

    @Synchronized
    fun save(session: OnlineSession) {
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Could not create $dir")
        val body =
            buildJsonObject {
                session.title?.let { put("title", it) }
                put("cwd", session.cwd)
                put("createdAt", session.createdAt)
                session.model?.let { put("model", it) }
                put("messages", JsonArray(session.messages))
            }
        val file = File(dir, session.id + SUFFIX)
        val temp = File(dir, session.id + SUFFIX + ".tmp")
        temp.writeText(body.toString())
        if (!temp.renameTo(file)) {
            file.writeText(body.toString())
            if (!temp.delete()) Log.w("OnlineSessionStore") { "Could not delete $temp" }
        }
    }

    fun delete(id: String): Boolean = SAFE_ID.matches(id) && File(dir, id + SUFFIX).delete()

    companion object {
        private const val SUFFIX = ".json"
        private val SAFE_ID = Regex("^[A-Za-z0-9-]{1,64}$")
    }
}

/**
 * One online session: each turn sends the history, runs the tool calls the model asks for (after
 * the user's approval unless [preApproved]) and goes on until the model answers without one.
 */
class OnlineAgentConnection(
    session: OnlineSession,
    private val key: AgentKeyTag,
    private val api: ChatCompletionsApi,
    private val store: OnlineSessionStore,
    private val tools: List<HostToolServer>,
    private val preApproved: Set<String>,
    private val instructions: String?,
    private val models: List<String>,
    private val persist: Boolean,
    private val scope: CoroutineScope,
) : AgentConnection {
    /**
     * The conversation, changed only through [update]: a turn appends to it while the chat renames
     * the session or switches its model from another thread, and a plain read-copy-write there
     * would put back the history it read, dropping a message the turn had just added.
     */
    private val current = AtomicReference(session)
    private val session: OnlineSession get() = current.get()

    /** Saves one at a time, each the history as it is then: a slower save never writes an older one over a newer. */
    private val saves = Mutex()

    private val out = Channel<AgentEvent>(Channel.UNLIMITED)
    override val events: Flow<AgentEvent> = out.receiveAsFlow()

    @Volatile
    private var running = true
    override val isRunning: Boolean get() = running

    override val info: AgentInfo =
        AgentInfo(
            commands = emptyList(),
            models = models.map { ModelChoice(it, it, "", it) },
            version = null,
        )

    private val turns = Mutex()

    /** The turn running and the ones queued behind it: Stop ends them all. */
    private val jobs: MutableSet<Job> = ConcurrentHashMap.newKeySet()
    private val answers = ConcurrentHashMap<String, CompletableDeferred<PermissionDecision>>()

    /** Set once the running turn has said it ended: a stop that races its own end says so once. */
    private val ended = AtomicBoolean(false)

    @Volatile
    private var lastError = ""

    override suspend fun send(
        text: String,
        images: List<ImageInput>,
    ) {
        val job =
            scope.launch(Dispatchers.IO) {
                turns.withLock { runTurn(text) }
            }
        jobs += job
        job.invokeOnCompletion { jobs -= job }
    }

    private suspend fun endTurn(event: AgentEvent.TurnEnded) {
        if (ended.compareAndSet(false, true)) out.send(event)
    }

    private suspend fun runTurn(text: String) {
        ended.set(false)
        out.send(AgentEvent.SessionStarted(session.id, session.model, PermissionMode.DEFAULT))
        append(
            buildJsonObject {
                put("role", "user")
                put("content", text)
            },
        )
        try {
            repeat(MAX_STEPS) {
                val reply = complete()
                val message = reply.first
                append(message)
                val calls = (message["tool_calls"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
                out.send(AgentEvent.Reply(assistantBlocks(message), reply.second))
                if (calls.isEmpty()) {
                    endTurn(AgentEvent.TurnEnded(interrupted = false, failure = null))
                    return
                }
                val pictures = mutableListOf<ImageInput>()
                for (call in calls) {
                    val result =
                        runCall(call) ?: run {
                            endTurn(AgentEvent.TurnEnded(interrupted = true, failure = null))
                            return
                        }
                    append(
                        buildJsonObject {
                            put("role", "tool")
                            put("tool_call_id", call.str("id").orEmpty())
                            put("content", result.text)
                            if (result.isError) put("is_error", "true")
                        },
                    )
                    pictures += result.images
                    out.send(AgentEvent.ToolResults(listOf(ContentBlock.ToolResult(call.str("id").orEmpty(), result.text, result.isError))))
                }
                // A tool message takes only text: the pictures a tool gave go in a user message after it.
                if (pictures.isNotEmpty()) append(imagesMessage(pictures))
            }
            endTurn(AgentEvent.TurnEnded(interrupted = false, failure = "Stopped after $MAX_STEPS steps without an answer."))
        } catch (e: CancellationException) {
            if (ended.compareAndSet(false, true)) out.trySend(AgentEvent.TurnEnded(interrupted = true, failure = null))
            throw e
        } catch (e: IOException) {
            lastError = e.message.orEmpty()
            out.send(AgentEvent.TurnEnded(interrupted = false, failure = e.message ?: "The AI provider couldn't be reached."))
        }
    }

    /** The model's message and how many tokens the conversation takes now. */
    private suspend fun complete(): Pair<JsonObject, Long?> {
        val model = session.model ?: throw ChatCompletionsException("No model to use: the key's provider listed none.")
        val body =
            buildJsonObject {
                put("model", model)
                put(
                    "messages",
                    buildJsonArray {
                        instructions?.let {
                            add(
                                buildJsonObject {
                                    put("role", "system")
                                    put("content", it)
                                },
                            )
                        }
                        answered(session.messages).forEach { add(wireMessage(it)) }
                    },
                )
                if (tools.isNotEmpty()) {
                    put(
                        "tools",
                        buildJsonArray {
                            tools.forEach { server ->
                                server.tools.forEach { tool ->
                                    add(
                                        buildJsonObject {
                                            put("type", "function")
                                            put(
                                                "function",
                                                buildJsonObject {
                                                    put("name", server.qualifiedName(tool.name))
                                                    put("description", tool.description)
                                                    put("parameters", tool.inputSchema)
                                                },
                                            )
                                        },
                                    )
                                }
                            }
                        },
                    )
                }
                if (isAnthropic(key.url)) put("max_tokens", MAX_OUTPUT_TOKENS)
                if (isOpenAI(key.url)) put("max_completion_tokens", MAX_OUTPUT_TOKENS)
            }
        val response = Json.parseToJsonElement(api.complete(key, body.toString())).jsonObject
        (response["error"] as? JsonObject)?.let { throw ChatCompletionsException(it.str("message") ?: it.toString()) }
        val choice = (response["choices"] as? JsonArray)?.firstOrNull() as? JsonObject ?: throw ChatCompletionsException("The provider sent no answer.")
        val message = choice["message"] as? JsonObject ?: throw ChatCompletionsException("The provider sent no answer.")
        val tokens = ((response["usage"] as? JsonObject)?.get("prompt_tokens") as? JsonPrimitive)?.longOrNull
        // Kept as the provider sent it, minus what a later request must not repeat.
        val kept =
            buildJsonObject {
                put("role", "assistant")
                put("content", message["content"]?.takeIf { it is JsonPrimitive } ?: JsonPrimitive(""))
                message["tool_calls"]?.let { put("tool_calls", it) }
            }
        return kept to tokens
    }

    /** Runs one call, asking first when it must; null when the user stopped the turn. */
    private suspend fun runCall(call: JsonObject): HostToolResult? {
        val id = call.str("id") ?: UUID.randomUUID().toString()
        val function = call["function"] as? JsonObject
        val name = function?.str("name").orEmpty()
        val input =
            try {
                function?.str("arguments")?.let { Json.parseToJsonElement(it) as? JsonObject } ?: JsonObject(emptyMap())
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                return HostToolResult("The arguments weren't valid JSON.", isError = true)
            }
        val (server, tool) =
            tools.firstNotNullOfOrNull { server ->
                val prefix = server.qualifiedName("")
                if (name.startsWith(prefix)) server to name.removePrefix(prefix) else null
            } ?: return HostToolResult("No tool named $name.", isError = true)
        var arguments = input
        if (name !in preApproved || server.alwaysAsks(tool)) {
            val strict = server.alwaysAsks(tool)
            val answer = CompletableDeferred<PermissionDecision>()
            answers[id] = answer
            out.send(
                AgentEvent.ApprovalRequested(
                    PermissionRequest(
                        requestId = id,
                        toolName = name,
                        displayName = null,
                        description = null,
                        title = null,
                        input = input,
                        toolUseId = id,
                        decisionReason = null,
                        blockedPath = null,
                        suggestions = JsonArray(emptyList()),
                        suppressAlwaysAllow = strict,
                        defaultToNo = strict,
                        agentId = null,
                    ),
                ),
            )
            when (val decision = answer.await()) {
                is PermissionDecision.Allow -> decision.updatedInput?.let { arguments = it }
                is PermissionDecision.Deny -> {
                    if (decision.interrupt) return null
                    return HostToolResult("The user declined. ${decision.message}".trim(), isError = true)
                }
                // Answers are for an agent's questions; these requests ask none, so one never approves a call.
                is PermissionDecision.Answer -> return HostToolResult("The call wasn't approved.", isError = true)
            }
        }
        return server.call(tool, arguments)
    }

    private fun update(change: (OnlineSession) -> OnlineSession): OnlineSession = current.updateAndGet(change)

    private suspend fun append(message: JsonObject) {
        update { it.copy(messages = it.messages + message, modifiedAt = System.currentTimeMillis()) }
        save()
    }

    /** Writes the session as it is now, its pictures left out. */
    private suspend fun save() {
        if (!persist) return
        withContext(Dispatchers.IO) {
            saves.withLock {
                val now = session
                if (now.messages.isEmpty()) return@withLock
                try {
                    store.save(now.copy(messages = now.messages.map(::withoutPictures)))
                } catch (e: IOException) {
                    Log.w(TAG, "Could not save the session", e)
                }
            }
        }
    }

    override suspend fun respond(
        request: PermissionRequest,
        decision: PermissionDecision,
    ) {
        answers.remove(request.requestId)?.complete(decision)
    }

    override suspend fun interrupt() {
        answers.values.forEach { it.complete(PermissionDecision.Deny("Stopped by the user.", interrupt = true)) }
        answers.clear()
        jobs.forEach { it.cancel() }
    }

    override suspend fun setPermissionMode(mode: PermissionMode) {}

    override suspend fun setModel(model: String?) {
        update { it.copy(model = model ?: models.firstOrNull()) }
    }

    override suspend fun rename(title: String): Boolean {
        update { it.copy(title = title) }
        save()
        return true
    }

    override suspend fun close(): Int? {
        jobs.toList().forEach { it.join() }
        kill()
        return 0
    }

    override fun kill() {
        running = false
        jobs.forEach { it.cancel() }
        out.close()
    }

    override fun stderrTail(): String = lastError

    companion object {
        /** Tool rounds in one turn before it stops: a loop the model can't get out of ends here. */
        const val MAX_STEPS = 24
        private const val TAG = "OnlineAgentConnection"
        private const val MAX_OUTPUT_TOKENS = 4_096
    }
}

/**
 * [messages] as an endpoint accepts them: a call the user stopped before it ran has no result, and
 * the endpoints refuse a history whose tool calls go unanswered, so each such call gets one.
 */
internal fun answered(messages: List<JsonObject>): List<JsonObject> {
    val out = ArrayList<JsonObject>(messages.size)
    var pending = emptyList<String>()

    fun closePending() {
        pending.forEach { id ->
            out +=
                buildJsonObject {
                    put("role", "tool")
                    put("tool_call_id", id)
                    put("content", STOPPED_CALL)
                }
        }
        pending = emptyList()
    }
    messages.forEach { message ->
        when (message.str("role")) {
            "tool" -> {
                pending = pending - message.str("tool_call_id").orEmpty()
                out += message
            }
            // A tool's pictures sit between its result and the next call; they don't close the calls.
            "user" -> {
                if (PICTURES !in message) closePending()
                out += message
            }
            else -> {
                closePending()
                out += message
                if (message.str("role") == "assistant") {
                    pending = (message["tool_calls"] as? JsonArray)?.mapNotNull { (it as? JsonObject)?.str("id") }.orEmpty()
                }
            }
        }
    }
    closePending()
    return out
}

private const val STOPPED_CALL = "The user stopped this call before it ran."

/** A saved message as the endpoint takes it: the app's own marks stay home. */
private fun wireMessage(message: JsonObject): JsonObject = if ("is_error" in message || PICTURES in message) JsonObject(message - "is_error" - PICTURES) else message

/** Marks the user message that carries a tool's pictures: saved without them, shown as a note. */
private const val PICTURES = "app_pictures"
private const val PICTURES_SAVED = "[A picture from a tool was shown here; it isn't kept.]"

/** The pictures of a tool's result, as the OpenAI-compatible endpoints take images. */
private fun imagesMessage(images: List<ImageInput>): JsonObject =
    buildJsonObject {
        put("role", "user")
        put(PICTURES, true)
        put(
            "content",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("type", "text")
                        put("text", "The picture the tool returned:")
                    },
                )
                images.forEach { image ->
                    add(
                        buildJsonObject {
                            put("type", "image_url")
                            put("image_url", buildJsonObject { put("url", "data:${image.mediaType};base64,${image.base64}") })
                        },
                    )
                }
            },
        )
    }

/** What is saved of [message]: a picture's bytes stay in memory, a line says where it was. */
private fun withoutPictures(message: JsonObject): JsonObject =
    if (PICTURES in message) {
        buildJsonObject {
            put("role", "user")
            put("content", PICTURES_SAVED)
        }
    } else {
        message
    }

/** The assistant message's text and tool calls, as the chat shows them. */
internal fun assistantBlocks(message: JsonObject): List<ContentBlock> {
    val text =
        message
            .str("content")
            ?.takeIf { it.isNotBlank() }
            ?.let { listOf(ContentBlock.Text(it)) }
            .orEmpty()
    val calls =
        (message["tool_calls"] as? JsonArray)
            ?.mapNotNull { element ->
                val call = element as? JsonObject ?: return@mapNotNull null
                val function = call["function"] as? JsonObject ?: return@mapNotNull null
                val input =
                    try {
                        function.str("arguments")?.let { Json.parseToJsonElement(it) as? JsonObject }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        null
                    } ?: JsonObject(emptyMap())
                ContentBlock.ToolUse(call.str("id").orEmpty(), function.str("name").orEmpty(), input)
            }.orEmpty()
    return text + calls
}

private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
