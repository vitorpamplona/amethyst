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

import com.vitorpamplona.claudecode.protocol.ContentBlock
import com.vitorpamplona.claudecode.session.TranscriptEntry
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChatTranscriptTest {
    private fun entry(
        role: TranscriptEntry.Role,
        vararg blocks: ContentBlock,
    ) = TranscriptEntry("u", role, blocks.toList(), null, null)

    @Test
    fun historyPairsToolResultsWithTheirCalls() {
        val transcript = ChatTranscript()
        transcript.addHistory(
            listOf(
                entry(TranscriptEntry.Role.USER, ContentBlock.Text("list files")),
                entry(TranscriptEntry.Role.ASSISTANT, ContentBlock.Thinking(""), ContentBlock.ToolUse("t1", "Bash", JsonObject(mapOf("command" to JsonPrimitive("ls"))))),
                entry(TranscriptEntry.Role.USER, ContentBlock.ToolResult("t1", "a.txt", false)),
                entry(TranscriptEntry.Role.ASSISTANT, ContentBlock.Text("One file.")),
            ),
        )
        val items = transcript.snapshot()
        assertEquals(3, items.size)
        assertEquals("list files", (items[0] as ChatItem.UserText).text)
        val call = items[1] as ChatItem.ToolCall
        assertEquals("ls", call.summary)
        assertEquals("a.txt", call.result)
        assertEquals("One file.", (items[2] as ChatItem.AssistantText).text)
    }

    @Test
    fun cliNotesAndWrappersAreNotUserMessages() {
        val transcript = ChatTranscript()
        transcript.addUserBlocks(listOf(ContentBlock.Text("[Request interrupted by user]")), typedByUser = false)
        transcript.addNotice(NoticeKind.INTERRUPTED)
        transcript.addUserBlocks(listOf(ContentBlock.Text("<command-name>/clear</command-name>")), typedByUser = true)

        val items = transcript.snapshot()
        assertEquals(1, items.size)
        assertEquals(NoticeKind.INTERRUPTED, (items.single() as ChatItem.Notice).kind)
    }

    @Test
    fun snapshotIsStableUntilSomethingChanges() {
        val transcript = ChatTranscript()
        transcript.addPrompt("hi")
        val first = transcript.snapshot()
        assertTrue(first === transcript.snapshot())
        transcript.addPrompt("again")
        assertEquals(2, transcript.snapshot().size)
    }
}
