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

import com.vitorpamplona.claudecode.process.ClaudeCodeConnection
import com.vitorpamplona.claudecode.process.ClaudeCodeStarter
import com.vitorpamplona.claudecode.process.ClaudeTransport
import com.vitorpamplona.claudecode.protocol.PermissionMode
import com.vitorpamplona.claudecode.session.ClaudeSessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Drives a chat against a scripted CLI that answers like Claude Code 2.1.323 did in the
 * recordings under claudeCodeClient's test fixtures: a Write that asks for permission, then a
 * reply once it ran.
 */
class ClaudeChatTest {
    private class ScriptedCli : ClaudeTransport {
        private val out = Channel<String>(Channel.UNLIMITED)
        val written = CopyOnWriteArrayList<String>()
        override val lines: Flow<String> = out.receiveAsFlow()

        override suspend fun write(line: String) {
            written.add(line)
            when {
                line.contains("\"subtype\":\"initialize\"") -> reply(line, "{}")
                line.contains("\"subtype\":\"rename_session\"") -> reply(line, "{}")
                line.contains("\"type\":\"user\"") -> {
                    emit("""{"type":"system","subtype":"init","cwd":"/work","session_id":"s-1","model":"claude-haiku-5-5","permissionMode":"default","tools":["Write"]}""")
                    emit("""{"type":"stream_event","event":{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Writ"}},"session_id":"s-1","parent_tool_use_id":null}""")
                    emit("""{"type":"assistant","message":{"id":"m1","model":"claude-haiku-5-5","role":"assistant","content":[{"type":"tool_use","id":"t1","name":"Write","input":{"file_path":"/work/hello.txt","content":"hi"}}]},"parent_tool_use_id":null,"session_id":"s-1","uuid":"a1"}""")
                    emit("""{"type":"control_request","request_id":"p1","request":{"subtype":"can_use_tool","tool_name":"Write","input":{"file_path":"/work/hello.txt","content":"hi"},"permission_suggestions":[{"type":"setMode","mode":"acceptEdits","destination":"session"}],"tool_use_id":"t1"}}""")
                }
                line.contains("\"behavior\":\"allow\"") -> {
                    emit("""{"type":"user","message":{"role":"user","content":[{"tool_use_id":"t1","type":"tool_result","content":"File created successfully at: /work/hello.txt"}]},"parent_tool_use_id":null,"session_id":"s-1","uuid":"u1"}""")
                    emit("""{"type":"assistant","message":{"id":"m2","role":"assistant","content":[{"type":"text","text":"done"}]},"parent_tool_use_id":null,"session_id":"s-1","uuid":"a2"}""")
                    emit("""{"type":"result","subtype":"success","is_error":false,"result":"done","session_id":"s-1","total_cost_usd":0.0083}""")
                }
            }
        }

        private fun reply(
            request: String,
            body: String,
        ) {
            val id = Regex("\"request_id\":\"([^\"]+)\"").find(request)!!.groupValues[1]
            emit("""{"type":"control_response","response":{"subtype":"success","request_id":"$id","response":$body}}""")
        }

        private fun emit(line: String) {
            out.trySend(line)
        }

        override fun closeInput() {
            out.close()
        }

        override fun kill() {
            out.close()
        }

        override suspend fun awaitExit(): Int = 0

        override fun stderrTail(): String = ""
    }

    // Not backgroundScope: advanceUntilIdle() stops once only background work is left, and the
    // chat's work is what these tests advance. Cancelled at the end of each test.
    private lateinit var chatScope: CoroutineScope

    private fun TestScope.newChat(
        cli: ScriptedCli,
        title: String? = null,
    ): ClaudeChat {
        chatScope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val starter =
            ClaudeCodeStarter { _, scope ->
                ClaudeCodeConnection(cli, scope).also { it.initialize() }
            }
        val config = Files.createTempDirectory("claude-config").toFile()
        return ClaudeChat(
            key = "new-1",
            resumeSessionId = null,
            cwd = File("."),
            title = title,
            permissionMode = PermissionMode.DEFAULT,
            model = null,
            store = ClaudeSessionStore(config),
            launcher = starter,
            scope = chatScope,
            onSessionChanged = {},
            inspectGit = { null },
        )
    }

    @Test
    fun aToolCallWaitsForApprovalThenFinishes() =
        runTest {
            val cli = ScriptedCli()
            val chat = newChat(cli)

            chat.send("Create hello.txt")
            advanceUntilIdle()

            val asking = chat.state.value
            assertEquals(ChatStatus.NEEDS_APPROVAL, asking.status)
            assertEquals("s-1", asking.sessionId)
            val prompt = asking.pendingPermissions.single()
            assertEquals("Write", prompt.toolName)
            val pendingCall = asking.items.filterIsInstance<ChatItem.ToolCall>().single()
            assertEquals("/work/hello.txt", pendingCall.summary)
            assertTrue(!pendingCall.isDone)

            chat.approve(prompt, always = false)
            advanceUntilIdle()

            val done = chat.state.value
            assertEquals(ChatStatus.READY, done.status)
            assertTrue(done.pendingPermissions.isEmpty())
            assertEquals("", done.streamingText)
            assertEquals(0.0083, done.costUsd)

            val items = done.items
            assertEquals("Create hello.txt", (items[0] as ChatItem.UserText).text)
            val call = items[1] as ChatItem.ToolCall
            assertTrue(call.isDone)
            assertEquals("File created successfully at: /work/hello.txt", call.result)
            assertEquals("done", (items[2] as ChatItem.AssistantText).text)
            assertEquals(3, items.size)

            // The approval went back for the request the CLI asked.
            assertNotNull(cli.written.firstOrNull { it.contains("\"request_id\":\"p1\"") && it.contains("\"behavior\":\"allow\"") })
            chatScope.cancel()
        }

    @Test
    fun alwaysAllowSendsTheCliSuggestion() =
        runTest {
            val cli = ScriptedCli()
            val chat = newChat(cli)
            chat.send("Create hello.txt")
            advanceUntilIdle()

            chat.approve(
                chat.state.value.pendingPermissions
                    .single(),
                always = true,
            )
            advanceUntilIdle()

            assertNotNull(cli.written.firstOrNull { it.contains("\"updatedPermissions\":[{\"type\":\"setMode\",\"mode\":\"acceptEdits\"") })
            chatScope.cancel()
        }

    @Test
    fun aNewChatsTitleNamesTheSessionOnceItExists() =
        runTest {
            val cli = ScriptedCli()
            val chat = newChat(cli, title = "Fix the relay list")
            assertEquals("Fix the relay list", chat.state.value.title)

            chat.send("A long first message from the issue template")
            advanceUntilIdle()

            val renames = cli.written.filter { it.contains("\"subtype\":\"rename_session\"") }
            assertEquals(1, renames.size)
            assertTrue(renames.single().contains("Fix the relay list"), renames.single())
            assertEquals("Fix the relay list", chat.state.value.title)
            chatScope.cancel()
        }

    @Test
    fun aSessionAnotherClaudeCodeHoldsTakesNoMessage() =
        runTest {
            val cli = ScriptedCli()
            chatScope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
            val config = Files.createTempDirectory("claude-config").toFile()
            val chat =
                ClaudeChat(
                    key = "s-held",
                    resumeSessionId = "s-held",
                    cwd = File("."),
                    title = null,
                    permissionMode = PermissionMode.DEFAULT,
                    model = null,
                    store = ClaudeSessionStore(config),
                    launcher = ClaudeCodeStarter { _, scope -> ClaudeCodeConnection(cli, scope).also { it.initialize() } },
                    scope = chatScope,
                    onSessionChanged = {},
                    inspectGit = { null },
                    heldElsewhere = { it == "s-held" },
                )
            // The history loads on the IO dispatcher, which the test clock doesn't drive.
            repeat(200) {
                advanceUntilIdle()
                if (chat.state.value.historyLoaded) return@repeat
                Thread.sleep(10)
            }

            // From anywhere that sends: here, as the Changes screen's question would.
            chat.send("why this line?")
            repeat(50) {
                advanceUntilIdle()
                Thread.sleep(5)
            }

            assertEquals(ChatError.HeldElsewhere, chat.state.value.error)
            assertTrue(
                chat.state.value.items
                    .none { it is ChatItem.UserText },
            )
            assertTrue(cli.written.isEmpty(), "a second process was started: ${cli.written}")
            chatScope.cancel()
        }
}
