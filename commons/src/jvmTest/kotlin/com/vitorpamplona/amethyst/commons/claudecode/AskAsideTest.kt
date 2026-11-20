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
import com.vitorpamplona.agents.AgentSessionInfo
import com.vitorpamplona.agents.AgentStartOptions
import com.vitorpamplona.agents.ContentBlock
import com.vitorpamplona.agents.ImageInput
import com.vitorpamplona.agents.PermissionDecision
import com.vitorpamplona.agents.PermissionMode
import com.vitorpamplona.agents.PermissionRequest
import com.vitorpamplona.agents.TranscriptEntry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [ClaudeCodeService.askAside] against an agent that answers as scripted. */
class AskAsideTest {
    private lateinit var tmp: File
    private lateinit var scope: CoroutineScope

    @BeforeTest
    fun setUp() {
        tmp = Files.createTempDirectory("aside").toFile()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @AfterTest
    fun tearDown() {
        scope.cancel()
        tmp.deleteRecursively()
    }

    /** An agent with one saved session, `source`, whose copies answer [reply] (or never, when null). */
    private inner class FakeAgent(
        val reply: String?,
    ) : AgentBackend {
        val started = mutableListOf<AgentStartOptions>()
        val deleted = mutableListOf<String>()
        val decisions = mutableListOf<PermissionDecision>()
        var killed = false
        val sent = CompletableDeferred<String>()

        override val id = "fake"
        override val displayName = "Fake"
        override val capabilities =
            AgentCapabilities(
                slashCommands = false,
                editFromMessage = false,
                subagents = false,
                hostTools = false,
                modes = listOf(PermissionMode.DEFAULT, PermissionMode.PLAN),
                liveSessions = false,
            )

        override fun executable() = File(tmp, "fake")

        override suspend fun start(
            options: AgentStartOptions,
            scope: CoroutineScope,
        ): AgentConnection {
            started += options
            val events = Channel<AgentEvent>(Channel.UNLIMITED)
            return object : AgentConnection {
                override val events: Flow<AgentEvent> = events.receiveAsFlow()
                override val isRunning get() = !killed
                override val info: AgentInfo? = null

                override suspend fun send(
                    text: String,
                    images: List<ImageInput>,
                ) {
                    sent.complete(text)
                    events.send(AgentEvent.SessionStarted("copy", null, options.permissionMode))
                    events.send(AgentEvent.ApprovalRequested(request()))
                    if (reply != null) {
                        events.send(AgentEvent.Reply(listOf(ContentBlock.Text(reply))))
                        events.send(AgentEvent.TurnEnded(interrupted = false, failure = null))
                    }
                }

                override suspend fun respond(
                    request: PermissionRequest,
                    decision: PermissionDecision,
                ) {
                    decisions += decision
                }

                override suspend fun interrupt() {}

                override suspend fun setPermissionMode(mode: PermissionMode) {}

                override suspend fun setModel(model: String?) {}

                override suspend fun rename(title: String) = false

                override suspend fun close(): Int? = 0

                override fun kill() {
                    killed = true
                    events.close()
                }

                override fun stderrTail() = ""
            }
        }

        override fun listSessions(cwd: String?) = listOf(session())

        override fun session(sessionId: String) = if (sessionId == "source") session() else null

        private fun session() =
            AgentSessionInfo(
                sessionId = "source",
                title = "Work",
                customTitle = null,
                firstPrompt = "do the work",
                lastPrompt = "do the work",
                cwd = tmp.absolutePath,
                gitBranch = null,
                createdAt = null,
                lastModified = 0,
            )

        override fun transcript(sessionId: String) = emptyList<TranscriptEntry>()

        override fun rename(
            sessionId: String,
            title: String,
        ) {}

        override fun delete(sessionId: String): Boolean {
            deleted += sessionId
            return true
        }
    }

    private fun request() =
        PermissionRequest(
            requestId = "r1",
            toolName = "Bash",
            displayName = null,
            description = null,
            title = null,
            input = JsonObject(emptyMap()),
            toolUseId = null,
            decisionReason = null,
            blockedPath = null,
            suggestions = JsonArray(emptyList()),
            suppressAlwaysAllow = false,
            defaultToNo = false,
            agentId = null,
        )

    private fun service(agent: AgentBackend) = ClaudeCodeService(agent, scope, AgentWorkspaces(File(tmp, "agents")))

    @Test
    fun anAnswerComesFromAToolLessCopyThatIsThenDeleted() =
        runBlocking {
            val agent = FakeAgent("""{"title": "fix: it", "description": "Why."}""")
            val service = service(agent)
            service.refresh()

            val answer = service.askAside("source", "describe the work")

            assertEquals("""{"title": "fix: it", "description": "Why."}""", answer)
            val options = agent.started.single()
            assertEquals("source", options.resumeSessionId)
            assertTrue(options.forkSession)
            assertTrue(options.onlyHostTools)
            assertEquals(PermissionMode.PLAN, options.permissionMode)
            assertEquals("describe the work", agent.sent.await())
            assertIs<PermissionDecision.Deny>(agent.decisions.single())
            // The copy goes; the session it came from stays.
            assertEquals(listOf("copy"), agent.deleted)
            assertTrue(agent.killed)
        }

    @Test
    fun cancellingStopsTheAgentAndStillDeletesTheCopy() =
        runBlocking {
            val agent = FakeAgent(reply = null)
            val service = service(agent)
            service.refresh()

            val asking = async(Dispatchers.Default) { service.askAside("source", "describe the work") }
            withTimeout(5_000) { agent.sent.await() }
            // The screen closed while the agent was still writing.
            withTimeout(5_000) {
                while (agent.decisions.isEmpty()) delay(10)
            }
            asking.cancelAndJoin()

            assertTrue(agent.killed)
            assertEquals(listOf("copy"), agent.deleted)
        }

    @Test
    fun theDraftIsReadFromWordsAroundIt() {
        val draft = ClaudeCodeProposeViewModel.parseDraftAnswer("Here it is:\n{\"title\": \" fix: it \", \"description\": \"Why.\"}\nDone.")
        assertEquals(ProposalDraft("fix: it", "Why."), draft)
        assertNull(ClaudeCodeProposeViewModel.parseDraftAnswer("no json here"))
        assertNull(ClaudeCodeProposeViewModel.parseDraftAnswer("{\"title\": \"\"}"))
    }

    @Test
    fun theRepositorysTemplateIsFound() {
        assertNull(ClaudeCodeProposeViewModel.pullRequestTemplate(tmp))
        File(tmp, ".github").mkdirs()
        File(tmp, ".github/PULL_REQUEST_TEMPLATE.md").writeText("## Summary\n")
        assertEquals("## Summary\n", ClaudeCodeProposeViewModel.pullRequestTemplate(tmp))
    }
}
