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
import com.vitorpamplona.agents.ContentBlock
import com.vitorpamplona.agents.PlanStep
import com.vitorpamplona.agents.TranscriptEntry
import kotlinx.serialization.json.JsonObject

/** One row of a Claude Code conversation as the chat screen shows it. */
@Immutable
sealed interface ChatItem {
    /** Stable for the life of the chat, for list keys. */
    val id: Long

    /** What the user sent: text, and the images that went with it. */
    @Immutable
    data class UserText(
        override val id: Long,
        val text: String,
        val images: List<SentImage> = emptyList(),
    ) : ChatItem

    @Immutable
    data class AssistantText(
        override val id: Long,
        val text: String,
    ) : ChatItem

    /** A tool call and, once it ran, its result. */
    @Immutable
    data class ToolCall(
        override val id: Long,
        val toolUseId: String,
        val name: String,
        val summary: String?,
        val input: String,
        val result: String?,
        val isError: Boolean,
        /** What a subagent this call started did: its own messages and tool calls. */
        val subagent: List<ChatItem> = emptyList(),
        /** A subagent running in the background after its call returned. */
        val subagentRunning: Boolean = false,
    ) : ChatItem {
        val isDone: Boolean get() = result != null
    }

    /** Something that happened to the conversation rather than a message in it. */
    @Immutable
    data class Notice(
        override val id: Long,
        val kind: NoticeKind,
        /** The CLI's own words, when it gave any; shown as-is. */
        val detail: String? = null,
    ) : ChatItem
}

enum class NoticeKind {
    /** The user stopped the turn. */
    INTERRUPTED,

    /** The turn ended in an error; [ChatItem.Notice.detail] says which. */
    TURN_FAILED,

    /** The CLI process ended while the chat was open. */
    PROCESS_ENDED,

    /** A note the CLI wrote into the conversation (a user-side text it generated). */
    CLI_NOTE,
}

/**
 * Builds the chat rows from a saved session's history and then from the live stream, the same
 * way for both: text blocks become messages, tool calls become one row each, and a tool result
 * fills in the row of the call it answers.
 */
class ChatTranscript {
    private val items = ArrayList<ChatItem>()
    private val toolRows = HashMap<String, Int>()
    private var nextId = 0L

    /** Each subagent's own rows, by the call that started it. */
    private val subagents = HashMap<String, ChatTranscript>()

    // Copied once per change, not once per read: the chat republishes on every streamed delta.
    private var cached: List<ChatItem>? = null

    @Synchronized
    fun snapshot(): List<ChatItem> = cached ?: items.toList().also { cached = it }

    // Read back from the rows only when they changed, not on every streamed delta.
    private var cachedTasks: Pair<List<ChatItem>, List<AgentTask>>? = null

    /** The plan an agent reported on its own ([setPlan]), which stands in for its task tool calls. */
    private var reportedPlan: List<AgentTask>? = null

    /**
     * The agent's task list: the plan it last reported, for an agent that reports one (Codex),
     * else what its task tool calls in [snapshot] leave (Claude Code).
     */
    @Synchronized
    fun tasks(): List<AgentTask> {
        reportedPlan?.let { return it }
        val rows = snapshot()
        cachedTasks?.let { (from, tasks) -> if (from === rows) return tasks }
        // A plan the agent hasn't touched since the user last wrote is an old one: a new turn that
        // leaves it alone hides it, rather than keep a stale card above the composer for good.
        val lastPrompt = rows.indexOfLast { it is ChatItem.UserText }
        val lastPlanCall = rows.indexOfLast { it is ChatItem.ToolCall && ToolKind.of(it.name) == ToolKind.PLAN }
        val tasks = if (lastPlanCall < lastPrompt) emptyList() else rows.agentTasks { call -> parsedInputs.getOrPut(call.toolUseId) { parseToolInput(call.input) } }
        return tasks.also { cachedTasks = rows to it }
    }

    /** Each task-tool call's input, read once: the list is folded again on every change. */
    private val parsedInputs = HashMap<String, JsonObject?>()

    val size: Int @Synchronized get() = items.size

    @Synchronized
    fun addHistory(entries: List<TranscriptEntry>) {
        entries.forEach { entry ->
            when (entry.role) {
                TranscriptEntry.Role.USER -> addUserBlocks(entry.content, typedByUser = true)
                TranscriptEntry.Role.ASSISTANT -> addAssistantBlocks(entry.content)
            }
        }
    }

    /** A saved session's subagents, by the call that started each. */
    @Synchronized
    fun addSubagentHistory(byCall: Map<String, List<TranscriptEntry>>) {
        byCall.forEach { (toolUseId, entries) ->
            entries.forEach { entry ->
                when (entry.role) {
                    TranscriptEntry.Role.USER -> addSubagentUser(toolUseId, entry.content)
                    TranscriptEntry.Role.ASSISTANT -> addSubagentAssistant(toolUseId, entry.content)
                }
            }
        }
    }

    /** The agent's whole plan, as it just reported it: replaces the last one. */
    @Synchronized
    fun setPlan(steps: List<PlanStep>) {
        reportedPlan =
            steps.mapIndexed { index, step ->
                val status =
                    when (step.status) {
                        PlanStep.Status.PENDING -> AgentTask.Status.PENDING
                        PlanStep.Status.IN_PROGRESS -> AgentTask.Status.IN_PROGRESS
                        PlanStep.Status.COMPLETED -> AgentTask.Status.COMPLETED
                    }
                AgentTask("plan-$index", step.text, activeForm = null, status = status)
            }
    }

    /** A message a subagent wrote, live or saved: it goes inside the call that started it. */
    @Synchronized
    fun addSubagentAssistant(
        toolUseId: String,
        blocks: List<ContentBlock>,
    ) {
        subagents.getOrPut(toolUseId) { ChatTranscript() }.addAssistantBlocks(blocks)
        refreshSubagent(toolUseId)
    }

    /** A subagent's tool results. Its prompt is the call's own input, so text is left out. */
    @Synchronized
    fun addSubagentUser(
        toolUseId: String,
        blocks: List<ContentBlock>,
    ) {
        val results = blocks.filterIsInstance<ContentBlock.ToolResult>()
        if (results.isEmpty()) return
        subagents.getOrPut(toolUseId) { ChatTranscript() }.addUserBlocks(results, typedByUser = false)
        refreshSubagent(toolUseId)
    }

    /** Whether the subagent [toolUseId] started still runs (the CLI's task events say). */
    @Synchronized
    fun setSubagentRunning(
        toolUseId: String,
        running: Boolean,
    ) {
        updateCall(toolUseId) { it.copy(subagentRunning = running) }
    }

    private fun refreshSubagent(toolUseId: String) {
        val rows = subagents[toolUseId]?.snapshot() ?: return
        updateCall(toolUseId) { it.copy(subagent = rows) }
    }

    private fun updateCall(
        toolUseId: String,
        change: (ChatItem.ToolCall) -> ChatItem.ToolCall,
    ) {
        val index = toolRows[toolUseId] ?: return
        val call = items[index] as? ChatItem.ToolCall ?: return
        items[index] = change(call)
        cached = null
    }

    /** A prompt the user just typed in this app. A new turn: a plan an agent reported is the last turn's. */
    @Synchronized
    fun addPrompt(
        text: String,
        images: List<SentImage> = emptyList(),
    ) {
        reportedPlan = null
        add(ChatItem.UserText(nextId++, text, images))
    }

    @Synchronized
    fun addAssistantBlocks(blocks: List<ContentBlock>) {
        blocks.forEach { block ->
            when (block) {
                is ContentBlock.Text -> if (block.text.isNotBlank()) add(ChatItem.AssistantText(nextId++, block.text))
                is ContentBlock.ToolUse -> {
                    toolRows[block.id] = items.size
                    add(
                        ChatItem.ToolCall(
                            id = nextId++,
                            toolUseId = block.id,
                            name = block.name,
                            summary = block.summary,
                            input = block.input.toString(),
                            result = null,
                            isError = false,
                        ),
                    )
                }
                // Reasoning comes back empty by default, and a tool's own blocks need no row.
                else -> {}
            }
        }
    }

    /**
     * User-side blocks: tool results fill in their calls. Text is a typed prompt in a saved
     * history ([typedByUser]); in the live stream the app already showed what the user typed,
     * so text there is the CLI's own note ("[Request interrupted by user]").
     */
    @Synchronized
    fun addUserBlocks(
        blocks: List<ContentBlock>,
        typedByUser: Boolean,
    ) {
        // A saved prompt's images: the app doesn't keep them, so they show as images were sent.
        val images = if (typedByUser) blocks.filterIsInstance<ContentBlock.Image>().map { SentImage(name = null, file = null) } else emptyList()
        var imagesShown = images.isEmpty()
        blocks.forEach { block ->
            when (block) {
                is ContentBlock.ToolResult -> attachResult(block)
                is ContentBlock.Text -> {
                    val text = block.text.trim()
                    when {
                        text.isEmpty() -> {}
                        text.startsWith("[Request interrupted") -> add(ChatItem.Notice(nextId++, NoticeKind.INTERRUPTED))
                        // Slash-command wrappers and reminders the CLI stores as user text.
                        text.startsWith("<") -> {}
                        typedByUser -> {
                            add(ChatItem.UserText(nextId++, text, if (imagesShown) emptyList() else images))
                            imagesShown = true
                        }
                        else -> add(ChatItem.Notice(nextId++, NoticeKind.CLI_NOTE, text))
                    }
                }
                else -> {}
            }
        }
        if (!imagesShown) add(ChatItem.UserText(nextId++, "", images))
    }

    @Synchronized
    fun addNotice(
        kind: NoticeKind,
        detail: String? = null,
    ) {
        // An interrupt shows up both as a CLI note and as the turn's end; keep one.
        if (kind == NoticeKind.INTERRUPTED && (items.lastOrNull() as? ChatItem.Notice)?.kind == NoticeKind.INTERRUPTED) return
        add(ChatItem.Notice(nextId++, kind, detail))
    }

    private fun add(item: ChatItem) {
        items.add(item)
        cached = null
    }

    private fun attachResult(block: ContentBlock.ToolResult) = updateCall(block.toolUseId) { it.copy(result = block.text, isError = block.isError) }
}

/**
 * What a chat with no name of its own is called: the first line of the first message the user
 * wrote. A slash command (`/context`) says nothing about the conversation, so it is passed over.
 */
fun List<ChatItem>.firstPromptLine(): String? =
    firstNotNullOfOrNull { item ->
        (item as? ChatItem.UserText)
            ?.text
            ?.takeIf { it.isNotBlank() && !it.startsWith("/") }
            ?.lineSequence()
            ?.first { it.isNotBlank() }
    }

/** A row of the chat screen: a message, or a run of tool calls folded into one line. */
@Immutable
sealed interface ChatRow {
    val key: Long

    @Immutable
    data class Single(
        val item: ChatItem,
    ) : ChatRow {
        override val key: Long get() = item.id
    }

    /**
     * A call that put something on claude.ai (a page, a deck, a doc): a card of its own, never
     * folded into a run of tool calls. [repeat] is a later publish or opening of one an earlier
     * card already shows, drawn as one line.
     */
    @Immutable
    data class Published(
        val call: ChatItem.ToolCall,
        val artifact: PublishedArtifact,
        val repeat: Boolean,
    ) : ChatRow {
        override val key: Long get() = call.id
    }

    /**
     * Tool calls made one after another with nothing said between them. Shown as one line that
     * counts them by kind ("Ran 6 commands · read 2 files"), opened on demand.
     */
    @Immutable
    data class ToolGroup(
        val calls: List<ChatItem.ToolCall>,
    ) : ChatRow {
        override val key: Long get() = calls.first().id

        val running: ChatItem.ToolCall? get() = calls.lastOrNull { !it.isDone }
        val failed: Int get() = calls.count { it.isError }

        /** How many calls of each [ToolKind], in the order the kinds first appear. */
        val counts: List<Pair<ToolKind, Int>>
            get() =
                calls
                    .groupingBy { ToolKind.of(it.name) }
                    .eachCount()
                    .toList()
                    .sortedBy { (kind, _) -> calls.indexOfFirst { ToolKind.of(it.name) == kind } }
    }
}

/** What a tool does, for counting calls in a folded group. */
enum class ToolKind {
    COMMAND,
    READ,
    EDIT,
    SEARCH,
    WEB,
    AGENT,

    /** Looking up the app's own settings (the settings assistant). */
    SETTINGS,

    /** Keeping the agent's own task list: shown as the task card, not as rows of the chat. */
    PLAN,

    /** Questions the agent asked the user: each on a row of its own, with the answers. */
    QUESTION,
    OTHER,
    ;

    companion object {
        fun of(toolName: String): ToolKind =
            when {
                // The settings assistant's change is its edit: on a row of its own, like a file's.
                toolName == SETTINGS_CHANGE -> EDIT
                toolName.startsWith(SETTINGS_PREFIX) -> SETTINGS
                else -> ofBuiltIn(toolName)
            }

        private val SETTINGS_PREFIX = "mcp__${SettingsToolServer.NAME}__"
        private val SETTINGS_CHANGE = SETTINGS_PREFIX + SettingsToolServer.CHANGE

        private fun ofBuiltIn(toolName: String): ToolKind =
            when (toolName) {
                "Bash", "PowerShell", "BashOutput", "KillShell", "Monitor" -> COMMAND
                "Read", "NotebookRead" -> READ
                "Edit", "Write", "MultiEdit", "NotebookEdit", "ApplyPatch" -> EDIT
                "Grep", "Glob", "LS", "ToolSearch" -> SEARCH
                "WebFetch", "WebSearch" -> WEB
                "Task", "Agent", "Workflow" -> AGENT
                "TaskCreate", "TaskUpdate", "TaskList", "TaskGet", "TodoWrite" -> PLAN
                // Claude Code's, and the row Codex's client keeps for its own.
                "AskUserQuestion", "request_user_input" -> QUESTION
                else -> OTHER
            }
    }
}

/**
 * Folds every run of two or more tool calls into a [ChatRow.ToolGroup]. A single call stays on
 * its own row, so a turn that runs one command still shows it. Edits and questions are never
 * folded: what a turn changed, and what the user was asked, is what the chat is read for. Calls that keep the
 * agent's task list are left out, unless refused: the task card shows what they did.
 */
fun List<ChatItem>.toChatRows(): List<ChatRow> {
    val rows = ArrayList<ChatRow>()
    val run = ArrayList<ChatItem.ToolCall>()

    fun flush() {
        when (run.size) {
            0 -> {}
            1 -> rows.add(ChatRow.Single(run[0]))
            else -> rows.add(ChatRow.ToolGroup(run.toList()))
        }
        run.clear()
    }
    // Links already on a card: the same page published again, or opened, is a line under it,
    // called what the card calls it. A page published again from the same file keeps its link,
    // so the file says it is a repeat before the result does: no card flashes up while it runs.
    val shown = HashMap<String, PublishedArtifact>()
    val shownFiles = HashMap<String, PublishedArtifact>()
    // Where each link's card is in [rows], so a doc started in it can rename it.
    val cardAt = HashMap<String, Int>()
    forEach { item ->
        // A refused one stays: it did nothing to the card, and the user should see that it failed.
        if (item is ChatItem.ToolCall && !item.isError && ToolKind.of(item.name) == ToolKind.PLAN) return@forEach
        val published = (item as? ChatItem.ToolCall)?.let { PublishedArtifacts.of(it) }
        if (item is ChatItem.ToolCall && published != null) {
            flush()
            if (item.isError) {
                // What it tried and why it failed, on its own row: nothing was published.
                rows.add(ChatRow.Single(item))
                return@forEach
            }
            val sameFile = published.file?.takeIf { published.kind == PublishedArtifact.Kind.PAGE && published.change != PublishedArtifact.Change.OPENED }
            // A call that names its link is about that one, whatever file it sends.
            val earlier = published.url?.let { shown[it] } ?: sameFile?.takeIf { published.url == null }?.let { shownFiles[it] }
            val url = published.url ?: earlier?.url
            val at = url?.let { cardAt[it] }
            if (earlier != null && at != null && PublishedArtifacts.startsDocIn(published, earlier)) {
                // A doc started in a blank Docs artifact made just before: that card is the doc's.
                val card = rows[at] as ChatRow.Published
                val doc = card.artifact.copy(kind = PublishedArtifact.Kind.DOCUMENT, title = published.title ?: card.artifact.title)
                rows[at] = card.copy(artifact = doc)
                shown[doc.url ?: return@forEach] = doc
                return@forEach
            }
            val artifact =
                if (earlier == null) {
                    published
                } else {
                    published.copy(
                        // Opening one says nothing of what it is.
                        kind = if (published.change == PublishedArtifact.Change.OPENED) earlier.kind else published.kind,
                        // Running, a republish of the same file has no link of its own yet.
                        change = if (published.change == PublishedArtifact.Change.CREATED) PublishedArtifact.Change.UPDATED else published.change,
                        title = published.title ?: earlier.title,
                        url = url,
                        file = published.file ?: earlier.file,
                    )
                }
            if (earlier == null && url != null) cardAt[url] = rows.size
            rows.add(ChatRow.Published(item, artifact, repeat = earlier != null))
            artifact.url?.let { shown[it] = artifact }
            if (sameFile != null && artifact.url != null) shownFiles[sameFile] = artifact
        } else if (item is ChatItem.ToolCall && (ToolKind.of(item.name) == ToolKind.EDIT || ToolKind.of(item.name) == ToolKind.QUESTION)) {
            flush()
            rows.add(ChatRow.Single(item))
        } else if (item is ChatItem.ToolCall) {
            run.add(item)
        } else {
            flush()
            rows.add(ChatRow.Single(item))
        }
    }
    flush()
    return rows
}

/**
 * An image sent with a prompt: its file's [name] and, while it is still there, the [file] to show
 * it from. A saved history keeps neither, only that an image was sent.
 */
@Immutable
data class SentImage(
    val name: String?,
    val file: String?,
)
