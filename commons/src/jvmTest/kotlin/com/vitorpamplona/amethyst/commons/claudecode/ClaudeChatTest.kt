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

import com.vitorpamplona.agents.LineTransport
import com.vitorpamplona.agents.PermissionMode
import com.vitorpamplona.claudecode.ClaudeBackend
import com.vitorpamplona.claudecode.process.ClaudeCodeConnection
import com.vitorpamplona.claudecode.process.ClaudeCodeOptions
import com.vitorpamplona.claudecode.process.ClaudeCodeStarter
import com.vitorpamplona.claudecode.session.ClaudeSessionStore
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.Assume.assumeTrue
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Drives a chat against a scripted CLI that answers like Claude Code 2.1.323 did in the
 * recordings under claudeCodeClient's test fixtures: a Write that asks for permission, then a
 * reply once it ran.
 */
class ClaudeChatTest {
    private class ScriptedCli : LineTransport {
        private val out = Channel<String>(Channel.UNLIMITED)
        val written = CopyOnWriteArrayList<String>()
        override val lines: Flow<String> = out.receiveAsFlow()

        /** Turns end in an API error, the way Claude Code reports one (a success subtype, is_error). */
        @Volatile var failTurns = false

        override suspend fun write(line: String) {
            written.add(line)
            when {
                line.contains("\"subtype\":\"initialize\"") -> reply(line, "{}")
                line.contains("\"subtype\":\"rename_session\"") -> reply(line, "{}")
                line.contains("\"type\":\"user\"") && failTurns -> {
                    emit("""{"type":"system","subtype":"init","cwd":"/work","session_id":"s-1","model":"claude-haiku-5-5","permissionMode":"default","tools":["Write"]}""")
                    emit("""{"type":"result","subtype":"success","is_error":true,"result":"API Error: 529 Overloaded","session_id":"s-1"}""")
                }
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
        /** How many starts fail, as a CLI that can't run would, before one works. */
        failedStarts: Int = 0,
    ): ClaudeChat {
        chatScope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        var failuresLeft = failedStarts
        val starter =
            ClaudeCodeStarter { _, scope ->
                if (failuresLeft-- > 0) throw IOException("cannot run claude")
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
            backend = ClaudeBackend(ClaudeSessionStore(config), starter),
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
    fun aTurnThatEndsUnwatchedWaitsToBeSeen() =
        runTest {
            val cli = ScriptedCli()
            val chat = newChat(cli)

            // Nobody looks while the turn runs: its end waits for the user.
            chat.send("Create hello.txt")
            advanceUntilIdle()
            chat.approve(
                chat.state.value.pendingPermissions
                    .single(),
                always = false,
            )
            advanceUntilIdle()
            val ended = chat.state.value
            assertEquals(ChatStatus.READY, ended.status)
            assertTrue(ended.unseenFinish)
            assertNotNull(ended.turnEndedAt)
            assertNotNull(ended.lastTurnMillis)

            // Opening the chat sees it.
            val release = chat.viewing()
            assertFalse(chat.state.value.unseenFinish)

            // A turn that ends on screen needs no flag; one after the screen closed does.
            chat.send("Again")
            advanceUntilIdle()
            chat.approve(
                chat.state.value.pendingPermissions
                    .single(),
                always = false,
            )
            advanceUntilIdle()
            assertFalse(chat.state.value.unseenFinish)

            release()
            release() // twice is once
            chat.send("Once more")
            advanceUntilIdle()
            assertFalse(chat.state.value.unseenFinish, "a turn still running has not finished")
            chat.approve(
                chat.state.value.pendingPermissions
                    .single(),
                always = false,
            )
            advanceUntilIdle()
            assertTrue(chat.state.value.unseenFinish)
            chatScope.cancel()
        }

    @Test
    fun aFailedTurnSaysSoAndTryAgainSendsTheMessageAgain() =
        runTest {
            val cli = ScriptedCli().apply { failTurns = true }
            val chat = newChat(cli)

            chat.send("Create hello.txt")
            advanceUntilIdle()
            val failed = chat.state.value
            assertEquals(ChatStatus.READY, failed.status)
            assertTrue(failed.lastTurnFailed)
            val notice = failed.items.last() as ChatItem.Notice
            assertEquals(NoticeKind.TURN_FAILED, notice.kind)
            assertEquals("API Error: 529 Overloaded", notice.detail)

            cli.failTurns = false
            chat.retry()
            advanceUntilIdle()
            val again = chat.state.value
            assertFalse(again.lastTurnFailed)
            assertEquals(ChatStatus.NEEDS_APPROVAL, again.status)
            assertEquals(listOf("Create hello.txt", "Create hello.txt"), again.items.filterIsInstance<ChatItem.UserText>().map { it.text })
            chatScope.cancel()
        }

    @Test
    fun aStartThatFailsIsAFailedTurnAndDoesNotTimeTheNextOne() =
        runTest {
            val cli = ScriptedCli()
            val chat = newChat(cli, failedStarts = 1)

            chat.send("Create hello.txt")
            advanceUntilIdle()
            val failed = chat.state.value
            assertEquals(ChatStatus.ENDED, failed.status)
            assertTrue(failed.error is ChatError.StartFailed)
            assertTrue(failed.lastTurnFailed)
            assertTrue(failed.unseenFinish, "nobody watched it fail")
            assertEquals(null, failed.turnStartedAt)

            // The next turn is timed from its own start, not the failed one's.
            assertTrue(chat.retry())
            advanceUntilIdle()
            assertEquals(ChatStatus.NEEDS_APPROVAL, chat.state.value.status)
            assertFalse(chat.state.value.lastTurnFailed)
            assertNotNull(chat.state.value.turnStartedAt)
            assertTrue(chat.state.value.turnStartedAt!! >= failed.turnEndedAt!!)
            chatScope.cancel()
        }

    @Test
    fun tryAgainTwiceSendsOnce() =
        runTest {
            val cli = ScriptedCli().apply { failTurns = true }
            val chat = newChat(cli)
            chat.send("Create hello.txt")
            advanceUntilIdle()
            cli.failTurns = false

            chat.retry()
            chat.retry()
            advanceUntilIdle()
            assertEquals(
                2,
                chat.state.value.items
                    .count { it is ChatItem.UserText },
            )
            assertEquals(2, cli.written.count { it.contains("\"type\":\"user\"") })
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
    fun aNameGivenBeforeTheFirstMessageNamesTheSession() =
        runTest {
            val cli = ScriptedCli()
            val chat = newChat(cli)
            chat.nameBeforeStart("Relay list cleanup")
            assertEquals("Relay list cleanup", chat.state.value.title)
            // Nothing runs yet: the name waits for the session.
            assertTrue(cli.written.isEmpty())

            chat.send("Remove the dead relays")
            advanceUntilIdle()

            val renames = cli.written.filter { it.contains("\"subtype\":\"rename_session\"") }
            assertEquals(1, renames.size)
            assertTrue(renames.single().contains("Relay list cleanup"), renames.single())
            chatScope.cancel()
        }

    @Test
    fun aSlashCommandDoesNotNameAChat() {
        val items =
            listOf(
                ChatItem.UserText(0, "/context"),
                ChatItem.AssistantText(1, "usage"),
                ChatItem.UserText(2, "\nFix the flaky relay test\nIt fails on CI"),
            )
        assertEquals("Fix the flaky relay test", items.firstPromptLine())
        assertEquals(null, listOf<ChatItem>(ChatItem.UserText(0, "/compact")).firstPromptLine())
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
                    backend = ClaudeBackend(ClaudeSessionStore(config), ClaudeCodeStarter { _, scope -> ClaudeCodeConnection(cli, scope).also { it.initialize() } }),
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

    @Test
    fun aSessionThatNamedNoFolderRefusesToStartInTheOneShown() =
        runTest {
            val cli = ScriptedCli()
            chatScope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
            val config = Files.createTempDirectory("claude-config").toFile()
            val chat =
                ClaudeChat(
                    key = "s-nowhere",
                    resumeSessionId = "s-nowhere",
                    // The folder shown, and a real one: the chat still must not run there.
                    cwd = File("."),
                    title = null,
                    permissionMode = PermissionMode.DEFAULT,
                    model = null,
                    backend = ClaudeBackend(ClaudeSessionStore(config), ClaudeCodeStarter { _, scope -> ClaudeCodeConnection(cli, scope).also { it.initialize() } }),
                    scope = chatScope,
                    onSessionChanged = {},
                    inspectGit = { null },
                    folderKnown = false,
                )
            repeat(200) {
                advanceUntilIdle()
                if (chat.state.value.historyLoaded) return@repeat
                Thread.sleep(10)
            }

            chat.send("go on")
            repeat(50) {
                advanceUntilIdle()
                Thread.sleep(5)
            }

            assertEquals(ChatError.FolderGone, chat.state.value.error)
            assertTrue(cli.written.isEmpty(), "the agent was started: ${cli.written}")
            chatScope.cancel()
        }

    @Test
    fun aMessageSentWhileTheJobClonesWaitsForTheClones() =
        runTest {
            val cli = ScriptedCli()
            val chat = newChat(cli)
            val cloning = CompletableDeferred<Unit>()
            chat.waitFor(cloning, repositories = 2)
            advanceUntilIdle()
            assertEquals(2, chat.state.value.cloning)

            chat.send("Create hello.txt")
            advanceUntilIdle()
            // The prompt shows at once; the agent isn't started before its folder has the clones.
            assertEquals(
                "Create hello.txt",
                (
                    chat.state.value.items
                        .first() as ChatItem.UserText
                ).text,
            )
            assertTrue(cli.written.isEmpty(), "the agent started before the clones: ${cli.written}")

            cloning.complete(Unit)
            advanceUntilIdle()
            assertEquals(null, chat.state.value.cloning)
            assertEquals(ChatStatus.NEEDS_APPROVAL, chat.state.value.status)
            chatScope.cancel()
        }

    @Test
    fun aFailedCloneEndsTheChatWithGitsWords() =
        runTest {
            val cli = ScriptedCli()
            val chat = newChat(cli)
            val cloning = CompletableDeferred<Unit>()
            chat.waitFor(cloning, repositories = 1)
            chat.send("Create hello.txt")
            cloning.completeExceptionally(IOException("repository not found"))
            advanceUntilIdle()
            assertEquals(ChatError.CloneFailed("repository not found"), chat.state.value.error)
            assertEquals(ChatStatus.ENDED, chat.state.value.status)
            assertTrue(cli.written.isEmpty())
            chatScope.cancel()
        }

    @Test
    fun aGuardedSessionKeepsTheScopeItFirstStartedWith() =
        runTest {
            val repo = Files.createTempDirectory("guarded").toFile()
            try {
                fun git(vararg args: String) = check(GitWorkspace.run(repo, listOf("git", "-c", "user.name=T", "-c", "user.email=t@example.com", "-c", "commit.gpgsign=false") + args).code == 0) { "git ${args.toList()}" }
                git("init", "-q", "-b", "main")
                git("commit", "-q", "--allow-empty", "-m", "base")
                git("remote", "add", "origin", File(repo, "nowhere.git").absolutePath)
                git("checkout", "-q", "-b", "agent/w")
                val saved = ConcurrentHashMap<String, PushScope>()
                val store =
                    object : PushScopeStore {
                        override fun load(sessionId: String) = saved[sessionId]

                        override fun save(
                            sessionId: String,
                            scope: PushScope,
                        ) {
                            saved[sessionId] = scope
                        }
                    }
                val started = CopyOnWriteArrayList<ClaudeCodeOptions>()

                fun chat(
                    sessionId: String?,
                    workspace: AgentWorkspace?,
                ): ClaudeChat {
                    val cli = ScriptedCli()
                    return ClaudeChat(
                        key = sessionId ?: "new-1",
                        resumeSessionId = sessionId,
                        cwd = repo,
                        title = null,
                        permissionMode = PermissionMode.DEFAULT,
                        model = null,
                        backend =
                            ClaudeBackend(
                                ClaudeSessionStore(Files.createTempDirectory("claude-config").toFile()),
                                ClaudeCodeStarter { options, scope ->
                                    started.add(options)
                                    ClaudeCodeConnection(cli, scope).also { it.initialize() }
                                },
                            ),
                        scope = chatScope,
                        onSessionChanged = {},
                        inspectGit = { null },
                        workstreamOf = { workspace },
                        pushScopes = store,
                    )
                }

                suspend fun TestScope.settle(done: () -> Boolean) {
                    // Git and the store run on the IO dispatcher, which the test clock doesn't drive.
                    repeat(400) {
                        advanceUntilIdle()
                        if (done()) return
                        Thread.sleep(10)
                    }
                }
                chatScope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
                val workspace = AgentWorkspace("w", "w", repo.absolutePath, listOf(WorkspaceRepo("https://example.com/o/r", "work", "main", "agent/w")), 0)
                chat(null, workspace).send("go")
                settle { saved.containsKey("s-1") }
                val first = assertNotNull(saved["s-1"], "the new session's scope was not kept")
                assertEquals(setOf("agent/w"), first.ownBranches)
                assertTrue(started.single().commandGuard != null)

                // The app restarts; the folder no longer looks like a workstream, and the session is on another branch.
                git("checkout", "-q", "-b", "release/1.x")
                chatScope.cancel()
                chatScope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
                val resumed = chat("s-1", null)
                settle { resumed.state.value.historyLoaded }
                resumed.send("go on")
                settle { started.size == 2 }
                val again = started[1]
                assertTrue(again.commandGuard != null, "a resumed guarded session started unguarded")
                // An older git (before 2.54) has no config hooks: the session gets the full block instead.
                assumeTrue("git has no config hooks", again.environment.containsValue("hook.${PushGuard.GUARD_NAME}.command"))
                val hook =
                    again.environment.entries
                        .single { it.value == "hook.${PushGuard.GUARD_NAME}.command" }
                        .key
                        .replace("KEY", "VALUE")
                assertTrue(again.environment.getValue(hook).contains("own='agent/w'"), "the resumed session got a new scope")
                assertEquals(first, saved["s-1"])
                chatScope.cancel()
            } finally {
                repo.deleteRecursively()
            }
        }
}
