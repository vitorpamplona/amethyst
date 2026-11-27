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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The service against a real session folder, with a CLI that never starts. */
class ClaudeCodeServiceTest {
    private lateinit var config: File
    private lateinit var work: File
    private lateinit var scope: CoroutineScope
    private lateinit var service: ClaudeCodeService
    private val id = "11111111-2222-3333-4444-555555555555"
    private lateinit var sessionFile: File

    @BeforeTest
    fun setUp() {
        config = Files.createTempDirectory("claude-config").toFile()
        work = Files.createTempDirectory("work").toFile()
        val project = File(config, "projects/-work").apply { mkdirs() }
        val cwd = work.absolutePath.replace("\\", "\\\\")
        sessionFile = File(project, "$id.jsonl")
        sessionFile.writeText(
            (1..40).joinToString("\n", postfix = "\n") { i ->
                val parent = if (i == 1) "null" else "\"m${i - 1}\""
                if (i % 2 == 1) {
                    """{"type":"user","uuid":"m$i","parentUuid":$parent,"sessionId":"$id","cwd":"$cwd","message":{"role":"user","content":"old question $i"}}"""
                } else {
                    """{"type":"assistant","uuid":"m$i","parentUuid":$parent,"sessionId":"$id","cwd":"$cwd","message":{"role":"assistant","content":[{"type":"text","text":"old answer $i"}]}}"""
                }
            },
        )
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        service =
            ClaudeCodeService(
                backend =
                    ClaudeBackend(
                        ClaudeSessionStore(config),
                        ClaudeCodeStarter { _, _ -> throw IOException("not in tests") },
                        findExecutable = { null },
                    ),
                scope = scope,
                agentWorkspaces = AgentWorkspaces(File(config, "agents")),
            )
    }

    @AfterTest
    fun tearDown() {
        scope.cancel()
        config.deleteRecursively()
        work.deleteRecursively()
    }

    private suspend fun waitFor(condition: () -> Boolean) =
        withTimeout(HANG_LIMIT_MS) {
            while (!condition()) delay(10)
        }

    @Test
    fun askingAMessageAgainCutsTheCopyJustBeforeIt() =
        runBlocking {
            // As the CLI saves it: a local command's output is a `system` line, and the next
            // prompt hangs off it. The same prompt is then sent a second time.
            val other = "22222222-2222-3333-4444-555555555555"
            val cwd = work.absolutePath.replace("\\", "\\\\")
            File(config, "projects/-work/$other.jsonl").writeText(
                listOf(
                    """{"type":"user","uuid":"c1","parentUuid":null,"sessionId":"$other","cwd":"$cwd","message":{"role":"user","content":"<command-name>/context</command-name>"}}""",
                    """{"type":"system","subtype":"local_command","uuid":"s1","parentUuid":"c1","sessionId":"$other","content":"<local-command-stdout>usage</local-command-stdout>"}""",
                    """{"type":"user","uuid":"p1","parentUuid":"s1","sessionId":"$other","cwd":"$cwd","message":{"role":"user","content":"which model?"}}""",
                    """{"type":"assistant","uuid":"a1","parentUuid":"p1","sessionId":"$other","cwd":"$cwd","message":{"role":"assistant","content":[{"type":"text","text":"haiku"}]}}""",
                    """{"type":"user","uuid":"p2","parentUuid":"a1","sessionId":"$other","cwd":"$cwd","message":{"role":"user","content":"which model?"}}""",
                    """{"type":"assistant","uuid":"a2","parentUuid":"p2","sessionId":"$other","cwd":"$cwd","message":{"role":"assistant","content":[{"type":"text","text":"still haiku"}]}}""",
                ).joinToString("\n", postfix = "\n"),
            )
            // A CLI that records how it was asked to start.
            val started = mutableListOf<ClaudeCodeOptions>()
            val service =
                ClaudeCodeService(
                    backend =
                        ClaudeBackend(
                            ClaudeSessionStore(config),
                            ClaudeCodeStarter { options, _ ->
                                synchronized(started) { started.add(options) }
                                throw IOException("not in tests")
                            },
                            findExecutable = { null },
                        ),
                    scope = scope,
                    agentWorkspaces = AgentWorkspaces(File(config, "agents")),
                )
            service.refresh()
            val chat = assertNotNull(service.chat(other))
            waitFor { chat.state.value.historyLoaded }
            val prompts =
                chat.state.value.items
                    .filterIsInstance<ChatItem.UserText>()
            assertEquals(listOf("which model?", "which model?"), prompts.map { it.text })

            // The second: the copy keeps the first exchange and is cut at its reply.
            val second = assertNotNull(service.editFrom(other, prompts[1]))
            assertEquals("which model?", second.draft)
            waitFor { second.state.value.historyLoaded }
            assertEquals(
                listOf("which model?", "haiku"),
                second.state.value.items
                    .map { (it as? ChatItem.UserText)?.text ?: (it as ChatItem.AssistantText).text },
            )
            assertTrue(second.state.value.forking)
            second.send("which model, really?")
            waitFor { synchronized(started) { started.size == 1 } }
            assertEquals(Triple(other, true, "a1"), started[0].let { Triple(it.resumeSessionId, it.forkSession, it.resumeSessionAt) })

            // The first: cut at the command's output, which the chat doesn't show.
            val first = assertNotNull(service.editFrom(other, prompts[0]))
            waitFor { first.state.value.historyLoaded }
            assertTrue(
                first.state.value.items
                    .isEmpty(),
            )
            assertTrue(first.state.value.forking)
            first.send("which model, really?")
            waitFor { synchronized(started) { started.size == 2 } }
            assertEquals("s1", started[1].resumeSessionAt)
        }

    @Test
    fun renamingACopyBeforeItStartsLeavesItsSourceAlone() =
        runBlocking {
            service.refresh()
            val fork = assertNotNull(service.fork(id))
            service.renameChat(fork.key, "My copy")
            assertEquals("My copy", fork.state.value.title)
            // The copy still carries the source's id inside: nothing may be written to it.
            assertTrue(sessionFile.readLines().none { it.contains("custom-title") })
        }

    @Test
    fun deletingAForkThatNeverStartedLeavesItsSourceAlone() =
        runBlocking {
            service.refresh()
            val fork = assertNotNull(service.fork(id))
            waitFor { service.rows.value.any { it.key == fork.key } }
            val row = service.rows.value.single { it.key == fork.key }
            // The fork still carries the source's id inside; its row must not.
            assertNull(row.sessionId)

            service.delete(row)
            assertTrue(sessionFile.isFile, "the source session was deleted")
            assertTrue(service.rows.value.none { it.key == fork.key })
        }

    @Test
    fun aMessageSentBeforeTheHistoryLoadsLandsAfterIt() =
        runBlocking {
            service.refresh()
            val chat = assertNotNull(service.chat(id))
            chat.send("new question")
            waitFor {
                chat.state.value.items
                    .any { it is ChatItem.UserText && it.text == "new question" }
            }
            val texts =
                chat.state.value.items
                    .filterIsInstance<ChatItem.UserText>()
                    .map { it.text }
            assertEquals("new question", texts.last())
            assertEquals("old question 1", texts.first())
        }

    @Test
    fun deletingAChatThatWaitsOnTheUserClearsTheCount() =
        runBlocking {
            // A CLI that asks to write a file as soon as it gets a message.
            val asking =
                ClaudeCodeService(
                    backend =
                        ClaudeBackend(
                            ClaudeSessionStore(config),
                            ClaudeCodeStarter { _, scope ->
                                ClaudeCodeConnection(AskingTransport(), scope).also { it.initialize() }
                            },
                            findExecutable = { null },
                        ),
                    scope = scope,
                    agentWorkspaces = AgentWorkspaces(File(config, "agents")),
                )
            val chat = asking.newChat(work)
            chat.send("write it")
            waitFor { asking.needsYou.value == 1 }
            waitFor { asking.rows.value.any { it.key == chat.key } }

            asking.delete(asking.rows.value.single { it.key == chat.key })
            assertEquals(0, asking.needsYou.value)
        }

    private class AskingTransport : LineTransport {
        private val out = Channel<String>(Channel.UNLIMITED)
        override val lines: Flow<String> = out.receiveAsFlow()

        override suspend fun write(line: String) {
            val id = Regex("\"request_id\":\"([^\"]+)\"").find(line)?.groupValues?.get(1)
            when {
                line.contains("\"subtype\":\"initialize\"") ->
                    out.trySend("""{"type":"control_response","response":{"subtype":"success","request_id":"$id","response":{}}}""")
                line.contains("\"type\":\"user\"") -> {
                    out.trySend("""{"type":"system","subtype":"init","cwd":"/work","session_id":"s-ask","permissionMode":"default","tools":["Write"]}""")
                    out.trySend("""{"type":"control_request","request_id":"p1","request":{"subtype":"can_use_tool","tool_name":"Write","input":{"file_path":"/work/a.txt","content":"hi"},"tool_use_id":"t1"}}""")
                }
            }
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

    @Test
    fun aWorkspaceWithoutASessionIsListedAndOpensAChatInIt() =
        runBlocking {
            // A workspace the app made, whose first message was never sent.
            val ws = service.agentWorkspaces.create(listOf(remote("remote")), "Idle one", agentId = "claude")

            service.refresh()
            val row = assertNotNull(service.rows.value.firstOrNull { it.workspace?.id == ws.id })
            assertEquals(ClaudeCodeService.WORKSPACE_KEY + ws.id, row.key)
            assertEquals("Idle one", row.title)

            // Opening it gives a chat in the workspace, the same one each time.
            val chat = assertNotNull(service.chat(row.key))
            assertEquals(ws.dir, chat.state.value.cwd)
            // Hands-off, as a new workstream is.
            assertEquals(PermissionMode.AUTO, chat.state.value.permissionMode)
            assertEquals(chat, service.chat(row.key))
            // The chat's own row stands for the workspace now.
            waitFor { service.rows.value.none { it.key == row.key } }
            assertEquals(
                chat.key,
                service.rows.value
                    .single { it.workspace?.id == ws.id }
                    .key,
            )
        }

    @Test
    fun archivingASessionMovesItUnderArchivedAndBack() =
        runBlocking {
            service.refresh()
            val row = assertNotNull(service.rows.value.firstOrNull { it.sessionId == id })
            assertFalse(row.archived)
            // Claude keeps no archive of its own: the app remembers it.
            service.archive(row, true)
            val archived = service.rows.value.single { it.sessionId == id }
            assertTrue(archived.archived)
            assertTrue(archived.userArchived)
            assertEquals(setOf(id), service.agentWorkspaces.preferences().archivedSessions)
            service.archive(archived, false)
            assertFalse(
                service.rows.value
                    .single { it.sessionId == id }
                    .archived,
            )
            assertTrue(
                service.agentWorkspaces
                    .preferences()
                    .archivedSessions
                    .isEmpty(),
            )
        }

    @Test
    fun aSessionReopensInThePermissionModeItWasLeftIn() =
        runBlocking {
            service.refresh()
            val chat = assertNotNull(service.chat(id))
            assertEquals(PermissionMode.DEFAULT, chat.state.value.permissionMode)
            chat.setPermissionMode(PermissionMode.AUTO)
            waitFor { service.agentWorkspaces.preferences().sessionModes[id] == PermissionMode.AUTO.wire }

            // The app restarts: a new service over the same folders.
            val restarted =
                ClaudeCodeService(
                    backend =
                        ClaudeBackend(
                            ClaudeSessionStore(config),
                            ClaudeCodeStarter { _, _ -> throw IOException("not in tests") },
                            findExecutable = { null },
                        ),
                    scope = scope,
                    agentWorkspaces = AgentWorkspaces(File(config, "agents")),
                )
            restarted.refresh()
            val reopened = assertNotNull(restarted.chat(id))
            assertEquals(PermissionMode.AUTO, reopened.state.value.permissionMode)
            // A copy of it starts in the same mode.
            assertEquals(PermissionMode.AUTO, assertNotNull(restarted.fork(id)).state.value.permissionMode)

            // Back to the default: nothing is kept for it.
            reopened.setPermissionMode(PermissionMode.DEFAULT)
            waitFor { id !in restarted.agentWorkspaces.preferences().sessionModes }
        }

    /** A bare "remote" with one commit on main, by file URL. */
    private fun remote(name: String): String {
        val remote = File(work, "$name.git")
        val seed = File(work, "$name-seed").apply { mkdirs() }
        git(work, "init", "--quiet", "--bare", "--initial-branch=main", remote.absolutePath)
        git(seed, "init", "--quiet", "--initial-branch=main")
        File(seed, "README.md").writeText("app\n")
        git(seed, "add", ".")
        git(seed, "commit", "--quiet", "-m", "init")
        git(remote, "fetch", "--quiet", seed.absolutePath, "main:main")
        return fileUrl(remote)
    }

    /** A checked-out file's text with Windows line endings (git's autocrlf there) read as `\n`. */
    private fun checkedOut(file: File) = file.readText().replace("\r\n", "\n")

    private fun git(
        dir: File,
        vararg args: String,
    ) = check(
        ProcessBuilder(listOf("git", "-c", "user.name=T", "-c", "user.email=t@example.invalid") + args)
            .directory(dir)
            .apply { environment().forgetInheritedRepository() }
            .redirectErrorStream(true)
            .start()
            .waitFor() == 0,
    ) { args.joinToString(" ") }

    /** A saved session that ran in [cwd]. */
    private fun sessionIn(
        sessionId: String,
        cwd: String,
    ) {
        val escaped = cwd.replace("\\", "\\\\")
        File(config, "projects/-ws-$sessionId").apply { mkdirs() }.resolve("$sessionId.jsonl").writeText(
            """{"type":"user","uuid":"u1","parentUuid":null,"sessionId":"$sessionId","cwd":"$escaped","message":{"role":"user","content":"start"}}""" + "\n",
        )
    }

    @Test
    fun cleaningUpFreesFinishedWorkspacesAndKeepsWorkNotCommitted() =
        runBlocking {
            val url = remote("remote")
            val clean = service.agentWorkspaces.create(listOf(url), "Clean", agentId = "claude")
            val committed = service.agentWorkspaces.create(listOf(url), "Committed", agentId = "claude")
            val committedRepo = File(committed.dir, committed.repos.single().name)
            File(committedRepo, "work.txt").writeText("not pushed\n")
            git(committedRepo, "add", ".")
            git(committedRepo, "commit", "--quiet", "-m", "work")
            val dirty = service.agentWorkspaces.create(listOf(url), "Dirty", agentId = "claude")
            val dirtyRepo = File(dirty.dir, dirty.repos.single().name)
            File(dirtyRepo, "work.txt").writeText("not committed\n")

            val cleanSession = "77777777-2222-3333-4444-555555555555"
            val dirtySession = "88888888-2222-3333-4444-555555555555"
            sessionIn(cleanSession, clean.dir)
            sessionIn(dirtySession, dirty.dir)
            service.refresh()

            val freed = service.cleanUp(listOf(clean, committed, dirty))
            // A commit on no remote is kept in the bundle: freeing it loses nothing.
            assertEquals(setOf(clean.id, committed.id), freed.map { it.id }.toSet())
            assertFalse(File(clean.dir, clean.repos.single().name).exists())
            assertFalse(committedRepo.exists())
            assertTrue(File(dirtyRepo, "work.txt").isFile)
            // A freed workstream's sessions go under Archived; the kept one's stay.
            assertEquals(setOf(cleanSession), service.agentWorkspaces.preferences().archivedSessions)
        }

    @Test
    fun archivingAWorkstreamsSessionFreesItsClonesAndUnarchivingListsItAgain() =
        runBlocking {
            val ws = service.agentWorkspaces.create(listOf(remote("archived")), "Archived", branch = "agent/a", agentId = "claude")
            val repo = File(ws.dir, ws.repos.single().name)
            File(repo, "work.txt").writeText("done\n")
            git(repo, "add", ".")
            git(repo, "commit", "--quiet", "-m", "work")
            val session = "33333333-2222-3333-4444-555555555555"
            sessionIn(session, ws.dir)
            service.setAutoCleanup(true)
            service.refresh()

            service.archive(service.rows.value.single { it.sessionId == session }, true)
            assertFalse(repo.exists())
            val archived = service.rows.value.single { it.sessionId == session }
            assertTrue(archived.archived)
            assertTrue(archived.userArchived)
            assertEquals(true, archived.workspace?.isFreed)
            assertNull(archived.workspaceSize)

            // Listed again, still freed: its next message brings the clones back, and shows how it goes.
            service.archive(archived, false)
            val back = service.rows.value.single { it.sessionId == session }
            assertFalse(back.archived)
            assertEquals(true, back.workspace?.isFreed)
            assertFalse(repo.exists())
            service.restoreWorkspace(assertNotNull(back.workspace))
            assertEquals("done\n", checkedOut(File(repo, "work.txt")))
        }

    @Test
    fun aWorkstreamsAgentCanAskToPublishButOtherChatsCannot() =
        runBlocking {
            // A CLI that records how it was asked to start.
            val started = mutableListOf<ClaudeCodeOptions>()
            val recording =
                ClaudeCodeService(
                    backend =
                        ClaudeBackend(
                            ClaudeSessionStore(config),
                            ClaudeCodeStarter { options, _ ->
                                synchronized(started) { started.add(options) }
                                throw IOException("not in tests")
                            },
                            findExecutable = { null },
                        ),
                    scope = scope,
                    agentWorkspaces = AgentWorkspaces(File(config, "agents")),
                )
            val ws = recording.agentWorkspaces.create(listOf(remote("asks")), "Asks", agentId = "claude")
            recording.refresh()
            recording.newChat(File(ws.dir), PermissionMode.AUTO).send("go")
            waitFor { synchronized(started) { started.size == 1 } }
            val tool = "mcp__${PublishToolServer.NAME}__${PublishToolServer.REQUEST}"
            // It only asks the user, so it runs without a prompt; the agent is told it exists.
            assertTrue(started[0].sdkMcpServers.any { it.name == PublishToolServer.NAME })
            assertTrue(tool in started[0].allowedTools)
            assertTrue(started[0].appendSystemPrompt.orEmpty().contains(PublishToolServer.REQUEST))

            recording.newChat(work).send("elsewhere")
            waitFor { synchronized(started) { started.size == 2 } }
            assertTrue(started[1].sdkMcpServers.none { it.name == PublishToolServer.NAME })
            assertFalse(tool in started[1].allowedTools)
        }

    @Test
    fun twoRestoresAtOnceMakeTheClonesOnce() =
        runBlocking {
            val ws = service.agentWorkspaces.create(listOf(remote("twice")), "Twice", branch = "agent/t", agentId = "claude")
            val repo = File(ws.dir, ws.repos.single().name)
            File(repo, "work.txt").writeText("done\n")
            git(repo, "add", ".")
            git(repo, "commit", "--quiet", "-m", "work")
            service.refresh()
            val freed = service.cleanUp(listOf(ws)).single()
            val both = listOf(async(Dispatchers.Default) { service.restoreWorkspace(freed) }, async(Dispatchers.Default) { service.restoreWorkspace(freed) }).awaitAll()
            assertTrue(both.none { it.isFreed })
            assertEquals("done\n", checkedOut(File(repo, "work.txt")))
            assertFalse(
                service.agentWorkspaces
                    .list()
                    .single()
                    .isFreed,
            )
        }

    @Test
    fun aWorkstreamIsFreedOnlyOnceEverySessionInItIsArchived() =
        runBlocking {
            val ws = service.agentWorkspaces.create(listOf(remote("shared")), "Shared", agentId = "claude")
            val first = "44444444-2222-3333-4444-555555555555"
            val second = "55555555-2222-3333-4444-555555555555"
            sessionIn(first, ws.dir)
            sessionIn(second, ws.dir)
            service.refresh()
            service.setAutoCleanup(true)
            val repo = File(ws.dir, ws.repos.single().name)

            service.archive(service.rows.value.single { it.sessionId == first }, true)
            assertTrue(repo.isDirectory, "the other session still works in it")
            service.archive(service.rows.value.single { it.sessionId == second }, true)
            assertFalse(repo.exists())
        }

    @Test
    fun aMessageToAFreedWorkstreamBringsItsClonesBackFirst() =
        runBlocking {
            val ws = service.agentWorkspaces.create(listOf(remote("resumed")), "Resumed", branch = "agent/r", agentId = "claude")
            val repo = File(ws.dir, ws.repos.single().name)
            val session = "66666666-2222-3333-4444-555555555555"
            sessionIn(session, ws.dir)
            service.refresh()
            service.cleanUp(listOf(ws))
            assertFalse(repo.exists())

            val chat = assertNotNull(service.chat(session))
            waitFor { chat.state.value.historyLoaded }
            chat.send("go on")
            // The CLI never starts in tests: the clones are back before it is asked to.
            waitFor { chat.state.value.error != null }
            assertFalse(chat.state.value.error is ChatError.CloneFailed, "${chat.state.value.error}")
            assertEquals("agent/r", File(repo, ".git/HEAD").readText().trim().removePrefix("ref: refs/heads/"))
            assertFalse(
                service.agentWorkspaces
                    .list()
                    .single()
                    .isFreed,
            )
        }

    @Test
    fun aWorkstreamOpensAtOnceAndClonesBehindIt() =
        runBlocking {
            val chat = service.newWorkstream(listOf(remote("workstream")), "Greeting")
            // The chat is there before the clone: in the workstream's folder, hands-off.
            assertEquals(File(service.agentWorkspaces.workstreamsDir, "greeting").absolutePath, chat.state.value.cwd)
            assertEquals(PermissionMode.AUTO, chat.state.value.permissionMode)
            waitFor { chat.state.value.cloning == null }
            assertNull(chat.state.value.error)
            assertTrue(File(chat.state.value.cwd, "workstream/.git").isDirectory)
            // Listed as a workstream, under the chat's own key.
            waitFor { service.rows.value.any { it.key == chat.key && it.workspace != null } }
            assertEquals(
                "Greeting",
                service.agentWorkspaces
                    .list()
                    .single()
                    .title,
            )
        }

    @Test
    fun aWorkstreamThatCannotCloneSaysSoInItsChat() =
        runBlocking {
            val chat = service.newWorkstream(listOf(fileUrl(File(work, "missing.git"))), "Nowhere")
            waitFor { chat.state.value.error != null }
            assertIs<ChatError.CloneFailed>(chat.state.value.error)
            // A URL that didn't clone isn't offered first next time.
            assertTrue(service.agentWorkspaces.groups().none { it.name == ClaudeCodeService.LAST_GROUP })
            assertFalse(File(chat.state.value.cwd).exists())
            assertTrue(service.agentWorkspaces.list().isEmpty())
        }

    @Test
    fun withAutomaticCleanupOffArchivingKeepsTheClones() =
        runBlocking {
            val ws = service.agentWorkspaces.create(listOf(remote("kept")), "Kept", agentId = "claude")
            val session = "99999999-2222-3333-4444-555555555555"
            sessionIn(session, ws.dir)
            service.refresh()
            service.setAutoCleanup(false)

            service.archive(service.rows.value.single { it.sessionId == session }, true)
            assertTrue(
                service.rows.value
                    .single { it.sessionId == session }
                    .archived,
            )
            // Archived only: freeing is the opt-in cleanup's to do.
            assertTrue(File(ws.dir, ws.repos.single().name).isDirectory)
            assertFalse(
                service.agentWorkspaces
                    .list()
                    .single()
                    .isFreed,
            )
        }

    @Test
    fun aWorkstreamUsedSinceItsWorkWasDoneIsNotDueForCleanup() =
        runBlocking {
            val ws = service.agentWorkspaces.create(listOf(remote("busy")), "Busy", agentId = "claude")
            service.agentWorkspaces.recordPullRequest(ws.id, "busy", PullRequestInfo("https://github.com/o/busy/pull/1", "MERGED", isDraft = false))
            val done =
                assertNotNull(
                    service.agentWorkspaces
                        .list()
                        .single()
                        .finishedAt,
                )
            val session = "aaaaaaaa-2222-3333-4444-555555555555"
            sessionIn(session, ws.dir)
            // The user kept working in it a day and a half after the merge.
            assertTrue(File(config, "projects/-ws-$session/$session.jsonl").setLastModified(done + 36 * HOUR))
            service.refresh()

            assertTrue(service.dueForCleanup(done + 48 * HOUR).isEmpty())
            assertEquals(listOf(ws.id), service.dueForCleanup(done + 61 * HOUR).map { it.id })
        }

    @Test
    fun anAgentsRequestToPublishIsSavedAndTellsTheUser() =
        runBlocking {
            val ws = service.agentWorkspaces.create(listOf(remote("tells")), "Tells", branch = "agent/t", agentId = "claude")
            val repo = File(ws.dir, "tells")
            File(repo, "work.txt").writeText("done\n")
            git(repo, "add", ".")
            git(repo, "commit", "--quiet", "-m", "work")
            service.refresh()
            val chat = service.newChat(File(ws.dir), PermissionMode.AUTO)
            val listening = CompletableDeferred<Unit>()
            val heard =
                async(Dispatchers.Default) {
                    withTimeout(HANG_LIMIT_MS) {
                        service.attention
                            .onSubscription { listening.complete(Unit) }
                            .first { it.kind == ChatAttention.Kind.REQUESTS_PUBLISH }
                    }
                }
            listening.await()

            val result = service.publishTool(chat.key, File(ws.dir)).call(PublishToolServer.REQUEST, JsonObject(mapOf("title" to JsonPrimitive("Ship it"))))
            assertFalse(result.isError, result.text)
            val attention = heard.await()
            assertEquals(chat.key, attention.key)
            // Saved with the workstream, and known to the list without reading the disk.
            assertEquals(
                "Ship it",
                service.agentWorkspaces
                    .list()
                    .single()
                    .publishRequests["tells"]
                    ?.title,
            )
            assertEquals(
                "Ship it",
                service
                    .workspaceAt(ws.dir)
                    ?.publishRequests
                    ?.get("tells")
                    ?.title,
            )
        }

    private companion object {
        const val HOUR = 60 * 60_000L

        /**
         * How long a wait may take before the test calls it a hang. Sending, starting and publishing
         * run git several times, and the Windows runner takes 5-15x longer than a laptop to do it: a
         * test that takes 0.3 s locally took 17 s there and ran out the 5 s this used to be.
         */
        const val HANG_LIMIT_MS = 30_000L
    }
}
