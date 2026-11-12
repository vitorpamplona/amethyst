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
import com.vitorpamplona.claudecode.protocol.ContentBlock
import com.vitorpamplona.claudecode.session.TranscriptEntry

/** One row of a Claude Code conversation as the chat screen shows it. */
@Immutable
sealed interface ChatItem {
    /** Stable for the life of the chat, for list keys. */
    val id: Long

    @Immutable
    data class UserText(
        override val id: Long,
        val text: String,
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

    // Copied once per change, not once per read: the chat republishes on every streamed delta.
    private var cached: List<ChatItem>? = null

    @Synchronized
    fun snapshot(): List<ChatItem> = cached ?: items.toList().also { cached = it }

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

    /** A prompt the user just typed in this app. */
    @Synchronized
    fun addPrompt(text: String) {
        add(ChatItem.UserText(nextId++, text))
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
                        typedByUser -> add(ChatItem.UserText(nextId++, text))
                        else -> add(ChatItem.Notice(nextId++, NoticeKind.CLI_NOTE, text))
                    }
                }
                else -> {}
            }
        }
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

    private fun attachResult(block: ContentBlock.ToolResult) {
        val index = toolRows[block.toolUseId] ?: return
        val call = items[index] as? ChatItem.ToolCall ?: return
        items[index] = call.copy(result = block.text, isError = block.isError)
        cached = null
    }
}
