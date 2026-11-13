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

import com.vitorpamplona.claudecode.protocol.ContentBlock
import com.vitorpamplona.claudecode.session.ClaudeSessionStore
import com.vitorpamplona.claudecode.session.TranscriptEntry
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClaudeSessionStoreTest {
    private lateinit var config: File
    private lateinit var store: ClaudeSessionStore

    @BeforeTest
    fun setUp() {
        config = Files.createTempDirectory("claude-config").toFile()
        val project = File(config, "projects/-work").apply { mkdirs() }
        File(project, "${Fixtures.SESSION_WRITE}.jsonl").writeText(Fixtures.text("session-write-resumed.jsonl"))
        File(project, "${Fixtures.SESSION_RENAMED}.jsonl").writeText(Fixtures.text("session-renamed.jsonl"))
        File(project, "${Fixtures.SESSION_RENAMED}.jsonl").setLastModified(1_000_000)
        store = ClaudeSessionStore(config)
    }

    @AfterTest
    fun tearDown() {
        config.deleteRecursively()
    }

    @Test
    fun listsSessionsNewestFirstWithTitles() {
        val sessions = store.listSessions()
        assertEquals(listOf(Fixtures.SESSION_WRITE, Fixtures.SESSION_RENAMED), sessions.map { it.sessionId })

        val write = sessions[0]
        assertEquals("Use the Write tool to create a file hello.txt containing exactly: hi. Then reply done.", write.title)
        assertNull(write.customTitle)
        assertEquals("What file did you create earlier? Answer with just the filename.", write.lastPrompt)
        assertEquals("/work", write.cwd)

        val renamed = sessions[1]
        assertEquals("Ping pong test", renamed.title)
        assertEquals("Reply with exactly the word: pong", renamed.firstPrompt)
    }

    @Test
    fun filtersByFolder() {
        assertEquals(2, store.listSessions(cwd = "/work").size)
        assertTrue(store.listSessions(cwd = "/elsewhere").isEmpty())
    }

    @Test
    fun transcriptFollowsTheConversation() {
        val entries = store.transcript(Fixtures.SESSION_WRITE)
        val shape =
            entries.map { entry ->
                val block = entry.content.first()
                entry.role to
                    when (block) {
                        is ContentBlock.Text -> block.text
                        is ContentBlock.ToolUse -> "tool_use:${block.name}"
                        is ContentBlock.ToolResult -> "tool_result"
                        else -> block::class.simpleName
                    }
            }
        assertEquals(
            listOf(
                TranscriptEntry.Role.USER to "Use the Write tool to create a file hello.txt containing exactly: hi. Then reply done.",
                TranscriptEntry.Role.ASSISTANT to "tool_use:Write",
                TranscriptEntry.Role.USER to "tool_result",
                TranscriptEntry.Role.ASSISTANT to "done",
                TranscriptEntry.Role.USER to "What file did you create earlier? Answer with just the filename.",
                TranscriptEntry.Role.ASSISTANT to "hello.txt",
            ),
            shape,
        )
    }

    @Test
    fun transcriptSkipsAnAbandonedBranch() {
        // Rewinding to the first prompt and asking something else leaves the old reply in the file.
        val file = store.sessionFile(Fixtures.SESSION_RENAMED)!!
        val entries = store.transcript(Fixtures.SESSION_RENAMED)
        val firstPrompt = entries.first()
        file.appendText(
            """{"type":"user","uuid":"branch-2","parentUuid":"${firstPrompt.uuid}","isSidechain":false,"message":{"role":"user","content":"a different question"}}""" + "\n" +
                """{"type":"assistant","uuid":"branch-3","parentUuid":"branch-2","isSidechain":false,"message":{"role":"assistant","content":[{"type":"text","text":"a different answer"}]}}""" + "\n",
        )
        val texts = store.transcript(Fixtures.SESSION_RENAMED).map { (it.content.first() as ContentBlock.Text).text }
        assertEquals(listOf("Reply with exactly the word: pong", "a different question", "a different answer"), texts)
    }

    @Test
    fun parallelToolResultsOffTheChainStillReachTheirCalls() {
        // Two calls in one turn: the CLI chains the second call to the first and hangs the first
        // call's result off it, beside the chain, as Claude Code 2.1 wrote it.
        val project = File(config, "projects/-work")
        val id = "11111111-2222-3333-4444-555555555555"
        File(project, "$id.jsonl").writeText(
            listOf(
                """{"type":"user","uuid":"u1","parentUuid":null,"message":{"role":"user","content":"edit then run"}}""",
                """{"type":"assistant","uuid":"a1","parentUuid":"u1","message":{"role":"assistant","content":[{"type":"tool_use","id":"t-edit","name":"Edit","input":{}}]}}""",
                """{"type":"assistant","uuid":"a2","parentUuid":"a1","message":{"role":"assistant","content":[{"type":"tool_use","id":"t-bash","name":"Bash","input":{}}]}}""",
                """{"type":"user","uuid":"r1","parentUuid":"a1","message":{"role":"user","content":[{"type":"tool_result","tool_use_id":"t-edit","content":"edited"}]}}""",
                """{"type":"user","uuid":"r2","parentUuid":"a2","message":{"role":"user","content":[{"type":"tool_result","tool_use_id":"t-bash","content":"ran"}]}}""",
                """{"type":"assistant","uuid":"a3","parentUuid":"r2","message":{"role":"assistant","content":[{"type":"text","text":"done"}]}}""",
            ).joinToString("\n", postfix = "\n"),
        )
        val results =
            store
                .transcript(id)
                .flatMap { it.content }
                .filterIsInstance<ContentBlock.ToolResult>()
                .map { it.toolUseId }
        assertEquals(listOf("t-edit", "t-bash"), results)
    }

    @Test
    fun renameAppendsATitleTheListPicksUp() {
        store.listSessions()
        store.rename(Fixtures.SESSION_WRITE, "Hello file")
        assertEquals("Hello file", store.session(Fixtures.SESSION_WRITE)!!.title)
        // The history is untouched.
        assertEquals(6, store.transcript(Fixtures.SESSION_WRITE).size)
    }

    @Test
    fun deleteRemovesTheSession() {
        assertTrue(store.delete(Fixtures.SESSION_RENAMED))
        assertEquals(listOf(Fixtures.SESSION_WRITE), store.listSessions().map { it.sessionId })
        assertFalse(store.delete(Fixtures.SESSION_RENAMED))
    }

    @Test
    fun idsThatAreNotUuidsNeverBecomePaths() {
        assertNull(store.sessionFile("../../etc/passwd"))
        assertTrue(store.transcript("../x").isEmpty())
    }

    @Test
    fun liveSessionsKeepOnlyRunningProcesses() {
        val dir = File(config, "sessions").apply { mkdirs() }
        val me = ProcessHandle.current().pid()
        File(dir, "$me.json").writeText("""{"pid":$me,"sessionId":"${Fixtures.SESSION_WRITE}","cwd":"/work","status":"busy","kind":"interactive"}""")
        File(dir, "999999999.json").writeText("""{"pid":999999999,"sessionId":"${Fixtures.SESSION_RENAMED}","status":"idle"}""")

        val live = store.liveSessions()
        assertEquals(setOf(Fixtures.SESSION_WRITE), live.keys)
        assertEquals("busy", live.getValue(Fixtures.SESSION_WRITE).status)
    }
}
