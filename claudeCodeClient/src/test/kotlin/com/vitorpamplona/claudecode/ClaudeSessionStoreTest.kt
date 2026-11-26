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

import com.vitorpamplona.agents.ContentBlock
import com.vitorpamplona.agents.TranscriptEntry
import com.vitorpamplona.claudecode.session.ClaudeSessionStore
import org.junit.Assume.assumeTrue
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
    fun readsSubagentsByTheCallThatStartedThem() {
        // As the CLI writes them: <session>/subagents/agent-<id>.jsonl, every line on the side
        // chain, and a .meta.json that names the Agent call.
        val dir = File(config, "projects/-work/${Fixtures.SESSION_WRITE}/subagents").apply { mkdirs() }
        File(dir, "agent-a1.meta.json").writeText("""{"agentType":"general-purpose","description":"Read a.txt","toolUseId":"toolu_parent","finished":true}""")
        File(dir, "agent-a1.jsonl").writeText(
            listOf(
                """{"parentUuid":null,"isSidechain":true,"agentId":"a1","type":"user","message":{"role":"user","content":"Read a.txt"},"uuid":"s1"}""",
                """{"parentUuid":"s1","isSidechain":true,"agentId":"a1","type":"assistant","message":{"role":"assistant","content":[{"type":"tool_use","id":"toolu_child","name":"Read","input":{"file_path":"a.txt"}}]},"uuid":"s2"}""",
                """{"parentUuid":"s2","isSidechain":true,"agentId":"a1","type":"user","message":{"role":"user","content":[{"tool_use_id":"toolu_child","type":"tool_result","content":"hello"}]},"uuid":"s3"}""",
                """{"parentUuid":"s3","isSidechain":true,"agentId":"a1","type":"assistant","message":{"role":"assistant","content":[{"type":"text","text":"It says hello."}]},"uuid":"s4"}""",
            ).joinToString("\n", postfix = "\n"),
        )
        // A log without its meta can't be placed, and is left out.
        File(dir, "agent-orphan.jsonl").writeText("")

        val subagents = store.subagentTranscripts(Fixtures.SESSION_WRITE)
        assertEquals(setOf("toolu_parent"), subagents.keys)
        val entries = subagents.getValue("toolu_parent")
        assertEquals(listOf("s1", "s2", "s3", "s4"), entries.map { it.uuid })
        assertEquals("It says hello.", (entries.last().content.single() as ContentBlock.Text).text)
        assertTrue(store.subagentTranscripts(Fixtures.SESSION_RENAMED).isEmpty())
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
    fun aGrowingSessionIsReadOnlyFromWhereTheLastScanStopped() {
        val project = File(config, "projects/-work")
        val id = "22222222-2222-3333-4444-555555555555"
        val file = File(project, "$id.jsonl")
        file.writeText("""{"type":"user","uuid":"u1","parentUuid":null,"cwd":"/work","message":{"role":"user","content":"first question"}}""" + "\n")
        assertEquals("first question", store.session(id)?.title)

        // A half-written line: read again once it is whole.
        file.appendText("""{"type":"custom-title","customTitle":"Half""")
        file.setLastModified(file.lastModified() + 2_000)
        assertEquals("first question", store.session(id)?.title)
        file.appendText("""way"}""" + "\n")
        file.setLastModified(file.lastModified() + 2_000)
        assertEquals("Halfway", store.session(id)?.title)

        // What was read before stays: the first prompt, and the folder it started in.
        file.appendText("""{"type":"assistant","uuid":"a1","parentUuid":"u1","cwd":"/elsewhere","message":{"role":"assistant","content":[{"type":"text","text":"ok"}]}}""" + "\n")
        file.setLastModified(file.lastModified() + 2_000)
        val info = store.session(id)!!
        assertEquals("first question", info.firstPrompt)
        assertEquals("/work", info.cwd)
        assertEquals("Halfway", info.title)
    }

    @Test
    fun aSessionStaysInTheFolderItStartedInWhenItsShellMovesOn() {
        // Claude Code stamps each message with its shell's folder: an agent that `cd`s into one of
        // a workstream's repositories still has its session (and its project folder) where it began.
        val project = File(config, "projects/-ws").also { check(it.mkdirs()) }
        val id = "33333333-2222-3333-4444-555555555555"
        File(project, "$id.jsonl").writeText(
            listOf(
                """{"type":"user","uuid":"u1","parentUuid":null,"cwd":"/ws","message":{"role":"user","content":"look at the UI"}}""",
                """{"type":"assistant","uuid":"a1","parentUuid":"u1","cwd":"/ws/Brainstorm-UI/client/src","message":{"role":"assistant","content":[{"type":"text","text":"ok"}]}}""",
                """{"type":"user","uuid":"u2","parentUuid":"a1","cwd":"/ws/Brainstorm-UI","message":{"role":"user","content":"and now?"}}""",
            ).joinToString("\n", postfix = "\n"),
        )
        assertEquals("/ws", store.session(id)?.cwd)
        assertEquals(listOf(id), store.listSessions(cwd = "/ws").map { it.sessionId })
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

    @Test
    fun aTranscriptThatCannotBeReadIsAnEmptyHistory() {
        // A session's file can be deleted between the lookup and the open. That open fails the
        // way an unreadable file does, and must read as "nothing written" instead of escaping to
        // the caller: a chat resumes by loading its history in a background coroutine.
        val session = Fixtures.SESSION_WRITE
        val dir = File(config, "projects/-work/$session/subagents").apply { mkdirs() }
        File(dir, "agent-a1.meta.json").writeText("""{"agentType":"general-purpose","description":"Read a.txt","toolUseId":"toolu_parent","finished":true}""")
        File(dir, "agent-a1.jsonl").writeText("")
        val transcript = store.sessionFile(session) ?: error("fixture missing")
        val log = File(dir, "agent-a1.jsonl")
        assertTrue(transcript.setReadable(false, false) && log.setReadable(false, false))
        assumeTrue("needs a file that cannot be read, which Windows and root do not allow", !transcript.canRead() && !log.canRead())

        assertEquals(emptyList(), store.transcript(session))
        assertEquals(mapOf("toolu_parent" to emptyList<TranscriptEntry>()), store.subagentTranscripts(session))
    }
}
