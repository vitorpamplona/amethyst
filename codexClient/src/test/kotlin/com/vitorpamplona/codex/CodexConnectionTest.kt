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

import com.vitorpamplona.agents.AgentEvent
import com.vitorpamplona.agents.AgentStartOptions
import com.vitorpamplona.agents.ContentBlock
import com.vitorpamplona.agents.LineTransport
import com.vitorpamplona.agents.PermissionDecision
import com.vitorpamplona.agents.PermissionMode
import com.vitorpamplona.agents.TranscriptEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** A `codex app-server` that answers from a script: each request by method, and lines the test pushes. */
private class ScriptedServer(
    private val answers: Map<String, String>,
) : LineTransport {
    private val out = Channel<String>(Channel.UNLIMITED)
    val written = mutableListOf<JsonObject>()
    override val lines: Flow<String> = out.receiveAsFlow()

    override suspend fun write(line: String) {
        val obj = Json.parseToJsonElement(line).jsonObject
        synchronized(written) { written.add(obj) }
        val method = obj["method"]?.jsonPrimitive?.content ?: return
        val id = obj["id"] ?: return
        out.send("""{"id":$id,"result":${answers[method] ?: "{}"}}""")
    }

    fun push(line: String) = out.trySend(line)

    override fun closeInput() = out.close().let { }

    override fun kill() = out.close().let { }

    override suspend fun awaitExit(): Int? = 0

    override fun stderrTail() = ""
}

class CodexConnectionTest {
    private val thread = """{"thread":{"id":"t1","preview":"","turns":[]},"model":"gpt-6.1-sol"}"""

    private fun server() =
        ScriptedServer(
            mapOf(
                "initialize" to """{"userAgent":"amethyst/0.169.0"}""",
                "model/list" to """{"data":[{"id":"gpt-6.1-sol","model":"gpt-6.1-sol","displayName":"GPT-6.1-Sol","description":"Workhorse","hidden":false},{"id":"x","model":"x","displayName":"X","description":"","hidden":true}]}""",
                "thread/start" to thread,
                "thread/fork" to thread.replace("t1", "t2"),
                "turn/start" to """{"turn":{"id":"turn-1","status":"inProgress","items":[]}}""",
            ),
        )

    @Test
    fun aTurnBecomesTheChatsEvents() =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val server = server()
            val codex = CodexConnection(server, scope)
            codex.open(AgentStartOptions(cwd = File("/w"), permissionMode = PermissionMode.ACCEPT_EDITS, instructions = "Be brief."))
            // The handshake, then a thread with the mode's policy and the instructions.
            val start = server.written.first { it["method"]?.jsonPrimitive?.content == "thread/start" }["params"]!!.jsonObject
            assertEquals("on-request", start["approvalPolicy"]!!.jsonPrimitive.content)
            assertEquals("workspace-write", start["sandbox"]!!.jsonPrimitive.content)
            assertEquals("Be brief.", start["developerInstructions"]!!.jsonPrimitive.content)
            assertEquals(listOf("gpt-6.1-sol"), codex.info!!.models.map { it.value })

            codex.send("list files")
            server.push("""{"method":"turn/started","params":{"threadId":"t1","turn":{"id":"turn-1"}}}""")
            server.push("""{"method":"item/started","params":{"threadId":"t1","turnId":"turn-1","item":{"type":"commandExecution","id":"c1","command":"ls","cwd":"/w","status":"inProgress"}}}""")
            server.push("""{"method":"item/completed","params":{"threadId":"t1","turnId":"turn-1","item":{"type":"commandExecution","id":"c1","command":"ls","status":"completed","aggregatedOutput":"a.txt","exitCode":0}}}""")
            server.push("""{"method":"item/started","params":{"threadId":"t1","turnId":"turn-1","item":{"type":"agentMessage","id":"m1","text":""}}}""")
            server.push("""{"method":"item/agentMessage/delta","params":{"threadId":"t1","turnId":"turn-1","itemId":"m1","delta":"One "}}""")
            server.push("""{"method":"item/completed","params":{"threadId":"t1","turnId":"turn-1","item":{"type":"agentMessage","id":"m1","text":"One file."}}}""")
            server.push("""{"method":"turn/completed","params":{"threadId":"t1","turn":{"id":"turn-1","status":"completed"}}}""")

            val events = withTimeout(5_000) { codex.events.take(7).toList() }
            assertEquals(AgentEvent.SessionStarted("t1", "gpt-6.1-sol", PermissionMode.ACCEPT_EDITS), events[0])
            val call = ((events[1] as AgentEvent.Reply).content.single() as ContentBlock.ToolUse)
            assertEquals("Bash", call.name)
            assertEquals("ls", call.input["command"]!!.jsonPrimitive.content)
            assertEquals(ContentBlock.ToolResult("c1", "a.txt", false), (events[2] as AgentEvent.ToolResults).content.single())
            assertEquals(AgentEvent.ReplyStarted, events[3])
            assertEquals(AgentEvent.ReplyDelta("One "), events[4])
            assertEquals(ContentBlock.Text("One file."), (events[5] as AgentEvent.Reply).content.single())
            assertEquals(AgentEvent.TurnEnded(interrupted = false, failure = null), events[6])
            scope.cancel()
        }

    @Test
    fun aBlockedCommandIsDeclinedWithoutAskingTheUser() =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val server = server()
            val codex = CodexConnection(server, scope)
            codex.open(AgentStartOptions(cwd = File("/w"), blockedCommands = listOf("git push", "gh pr create")))
            server.push("""{"id":"r1","method":"item/commandExecution/requestApproval","params":{"threadId":"t1","turnId":"turn-1","itemId":"c1","command":"/bin/zsh -lc 'gh pr create --fill'"}}""")
            withTimeout(5_000) { while (server.written.none { it["id"]?.toString() == "\"r1\"" }) kotlinx.coroutines.delay(10) }
            val reply = server.written.last { it["id"]?.toString() == "\"r1\"" }
            assertEquals("decline", reply["result"]!!.jsonObject["decision"]!!.jsonPrimitive.content)
            // Any other command still goes to the user.
            server.push("""{"id":"r2","method":"item/commandExecution/requestApproval","params":{"threadId":"t1","turnId":"turn-1","itemId":"c2","command":"gh pr view 12"}}""")
            val asked = withTimeout(5_000) { codex.events.first { it is AgentEvent.ApprovalRequested } } as AgentEvent.ApprovalRequested
            assertEquals("r2", asked.request.requestId)
            scope.cancel()
        }

    @Test
    fun blockedCommandsAreFoundInsideWrappersAndPastGitOptions() {
        val blocked = listOf("git push", "gh pr create", "ngit send", "amy git pr")
        listOf(
            "git push origin HEAD",
            "/usr/bin/git push",
            "git -C . push",
            "git -c core.hooksPath=/dev/null --no-pager push origin x",
            "/bin/zsh -lc 'cd repo && git push'",
            "bash -c \"npm test; gh pr create --fill\"",
            "echo ${'$'}(ngit send)",
            "amy git pr --title x",
        ).forEach { assertTrue(CodexModes.runsBlocked(it, blocked), it) }
        listOf(
            "git pull",
            "git status",
            "gh pr view 1",
            "git pushd",
            "ngit list",
        ).forEach { assertTrue(!CodexModes.runsBlocked(it, blocked), it) }
        assertTrue(!CodexModes.runsBlocked("git push", emptyList()))
    }

    @Test
    fun approvalsGoBackAsCodexDecisions() =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val server = server()
            val codex = CodexConnection(server, scope)
            codex.open(AgentStartOptions(cwd = File("/w")))
            server.push("""{"method":"item/started","params":{"threadId":"t1","turnId":"turn-1","item":{"type":"fileChange","id":"f1","status":"inProgress","changes":[{"path":"a.txt","kind":{"type":"update"},"diff":"@@ -1 +1 @@\n-a\n+b\n"}]}}}""")
            server.push("""{"id":"r9","method":"item/fileChange/requestApproval","params":{"threadId":"t1","turnId":"turn-1","itemId":"f1","startedAtMs":1}}""")
            val asked = withTimeout(5_000) { codex.events.first { it is AgentEvent.ApprovalRequested } } as AgentEvent.ApprovalRequested
            assertEquals(CodexItems.APPLY_PATCH, asked.request.toolName)
            assertEquals(
                "a.txt",
                asked.request.input["changes"]
                    .toString()
                    .substringAfter("\"path\":\"")
                    .substringBefore('"'),
            )
            // "Don't ask again" is Codex's accept-for-session.
            codex.respond(asked.request, PermissionDecision.Allow(updatedPermissions = asked.request.suggestions))
            val reply = server.written.last()
            assertEquals("\"r9\"", reply["id"].toString())
            assertEquals("acceptForSession", reply["result"]!!.jsonObject["decision"]!!.jsonPrimitive.content)
            // A request this client doesn't handle is refused, not left waiting.
            server.push("""{"id":10,"method":"item/tool/requestUserInput","params":{"threadId":"t1"}}""")
            withTimeout(5_000) { while (server.written.none { it["id"]?.toString() == "10" }) kotlinx.coroutines.delay(10) }
            assertTrue(server.written.last { it["id"]?.toString() == "10" }.containsKey("error"))
            scope.cancel()
        }

    @Test
    fun aCopyForksThroughTheTurnBeforeTheMessage() =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val server = server()
            val codex = CodexConnection(server, scope)
            codex.open(AgentStartOptions(cwd = File("/w"), resumeSessionId = "t1", forkSession = true, forkAt = "turn-1"))
            val fork = server.written.first { it["method"]?.jsonPrimitive?.content == "thread/fork" }["params"]!!.jsonObject
            assertEquals("t1", fork["threadId"]!!.jsonPrimitive.content)
            assertEquals("turn-1", fork["lastTurnId"]!!.jsonPrimitive.content)
            assertEquals("t2", codex.threadId)
            scope.cancel()
        }

    @Test
    fun historyMarksWhereEachPromptCanBeCut() {
        val thread =
            Json
                .parseToJsonElement(
                    """{"id":"t1","turns":[
                {"id":"turn-1","items":[{"type":"userMessage","id":"u1","content":[{"type":"text","text":"list files"}]},
                  {"type":"commandExecution","id":"c1","command":"ls","status":"completed","aggregatedOutput":"a.txt","exitCode":0},
                  {"type":"agentMessage","id":"m1","text":"One file."}]},
                {"id":"turn-2","items":[{"type":"userMessage","id":"u2","content":[{"type":"image","url":"data:image/png;base64,AA"},{"type":"text","text":"and this?"}]}]}]}""",
                ).jsonObject
        val history = CodexItems.history(thread)
        assertEquals(listOf("u1", "c1", "c1-result", "m1", "u2"), history.map { it.uuid })
        assertEquals(null, history[0].parentUuid)
        // The second prompt is cut by forking through the first turn.
        assertEquals("turn-1", history[4].parentUuid)
        assertIs<ContentBlock.Image>(history[4].content[0])
        assertEquals(TranscriptEntry.Role.USER, history[2].role)
    }

    /** A `codex` that answers every request with an empty result, and counts its starts in [launches]. */
    private fun fakeCodex(
        dir: File,
        launches: File,
    ): File {
        val script = File(dir, "codex")
        script.writeText(
            """
            #!/bin/sh
            echo started >> '${launches.absolutePath}'
            while IFS= read -r line; do
              id=${'$'}(printf '%s' "${'$'}line" | sed -n 's/.*"id":\([^,}]*\).*/\1/p')
              [ -n "${'$'}id" ] && printf '{"id":%s,"result":{"data":[]}}\n' "${'$'}id"
            done
            """.trimIndent() + "\n",
        )
        script.setExecutable(true)
        return script
    }

    @Test
    fun refreshesInARowShareOneListing() {
        if (System.getProperty("os.name").startsWith("Windows")) return
        val dir = Files.createTempDirectory("fake-codex").toFile()
        try {
            val launches = File(dir, "launches")
            val exe = fakeCodex(dir, launches)
            var clock = 1_000_000L
            val backend = CodexBackend(findExecutable = { exe }, now = { clock })

            fun starts() = if (launches.isFile) launches.readLines().size else 0

            assertEquals(emptyList(), backend.listSessions())
            assertEquals(1, starts())
            // Every chat's turn ends with a refresh: they don't each start a server.
            repeat(5) { backend.listSessions() }
            assertEquals(1, starts())
            // A change through the app is seen at once.
            backend.rename("t1", "New name")
            backend.listSessions()
            assertEquals(3, starts())
            // And changes made elsewhere within a minute.
            clock += 61_000
            backend.listSessions()
            assertEquals(4, starts())
        } finally {
            dir.deleteRecursively()
        }
    }

    /**
     * Against the real `codex app-server`: set CODEX_LIVE=1. Starts, renames, lists and deletes a
     * thread (no account needed), then sends a turn and checks it ends with an event either way.
     */
    @Test
    fun liveCodex() {
        if (System.getenv("CODEX_LIVE") != "1") return
        runBlocking {
            val backend = CodexBackend()
            assertNotNull(backend.executable(), "codex is installed")
            val dir = Files.createTempDirectory("codex-live").toFile()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val codex = backend.start(AgentStartOptions(cwd = dir), scope) as CodexConnection
            val started = withTimeout(30_000) { codex.events.first() } as AgentEvent.SessionStarted
            assertTrue(codex.info!!.models.isNotEmpty())
            assertTrue(codex.rename("Live Codex test"))
            codex.send("Reply with exactly: pong")
            val end = withTimeout(120_000) { codex.events.first { it is AgentEvent.TurnEnded } } as AgentEvent.TurnEnded
            println("turn ended: $end")
            codex.kill()
            val listed = backend.listSessions(dir.absolutePath)
            println("listed: $listed")
            assertTrue(listed.any { it.sessionId == started.sessionId && it.title == "Live Codex test" }, listed.toString())
            assertTrue(backend.delete(started.sessionId))
            scope.cancel()
        }
    }
}
