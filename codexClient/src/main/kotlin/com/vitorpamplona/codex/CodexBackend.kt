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

import com.vitorpamplona.agents.AgentBackend
import com.vitorpamplona.agents.AgentCapabilities
import com.vitorpamplona.agents.AgentConnection
import com.vitorpamplona.agents.AgentNotInstalledException
import com.vitorpamplona.agents.AgentSessionInfo
import com.vitorpamplona.agents.AgentStartOptions
import com.vitorpamplona.agents.PermissionMode
import com.vitorpamplona.agents.ProcessTransport
import com.vitorpamplona.agents.TranscriptEntry
import com.vitorpamplona.codex.CodexItems.long
import com.vitorpamplona.codex.CodexItems.text
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.io.IOException

/** Finds the `codex` program: `CODEX_EXECUTABLE`, PATH, then where its installers put it. */
object CodexExecutable {
    fun find(environment: Map<String, String> = System.getenv()): File? {
        environment["CODEX_EXECUTABLE"]?.let { path -> File(path).takeIf { it.canExecute() }?.let { return it } }
        val home = System.getProperty("user.home")
        val windows = System.getProperty("os.name").orEmpty().startsWith("Windows")
        val names = if (windows) listOf("codex.exe", "codex.cmd", "codex") else listOf("codex")
        val dirs =
            environment["PATH"].orEmpty().split(File.pathSeparator).filter { it.isNotBlank() } +
                listOf("$home/.local/bin", "$home/.codex/bin", "/opt/homebrew/bin", "/usr/local/bin", "$home/.npm-global/bin")
        return dirs.firstNotNullOfOrNull { dir -> names.map { File(dir, it) }.firstOrNull { it.isFile && it.canExecute() } }
    }
}

/** Codex as an [AgentBackend]: the user's `codex`, its saved threads, and how to start it. */
class CodexBackend(
    private val findExecutable: () -> File? = { CodexExecutable.find() },
    private val now: () -> Long = System::currentTimeMillis,
) : AgentBackend {
    override val id = ID
    override val displayName = "Codex"

    override val capabilities =
        AgentCapabilities(
            slashCommands = false,
            editFromMessage = true,
            subagents = false,
            hostTools = false,
            modes = listOf(PermissionMode.DEFAULT, PermissionMode.ACCEPT_EDITS, PermissionMode.PLAN),
            liveSessions = false,
            archive = true,
        )

    override fun executable(): File? = findExecutable()

    override suspend fun start(
        options: AgentStartOptions,
        scope: CoroutineScope,
    ): AgentConnection {
        val connection = launch(options.cwd, options.environment, scope)
        try {
            connection.open(options)
        } catch (e: IOException) {
            connection.kill()
            throw e
        }
        return connection
    }

    private suspend fun launch(
        cwd: File,
        environment: Map<String, String>,
        scope: CoroutineScope,
    ): CodexConnection {
        val exe = executable() ?: throw AgentNotInstalledException("The codex executable was not found")
        val process =
            withContext(Dispatchers.IO) {
                ProcessBuilder(exe.absolutePath, "app-server")
                    .directory(cwd.takeIf { it.isDirectory } ?: File(System.getProperty("user.home")))
                    .apply { environment().putAll(environment) }
                    .start()
            }
        return CodexConnection(ProcessTransport(process), scope)
    }

    /** Runs [block] against a short-lived `codex app-server`, for the calls outside a chat. Blocking. */
    private fun <T> withServer(block: suspend (CodexConnection) -> T): T? {
        if (executable() == null) return null
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        return try {
            runBlocking {
                val connection = launch(File(System.getProperty("user.home")), emptyMap(), scope)
                try {
                    connection.open(AgentStartOptions(cwd = File(System.getProperty("user.home")), probeOnly = true))
                    block(connection)
                } finally {
                    connection.kill()
                }
            }
        } catch (e: IOException) {
            null
        } finally {
            scope.cancel()
        }
    }

    /**
     * The last listing per folder (null: all), and when it was made. The app re-lists after
     * every turn of every chat, and each listing starts a whole `codex app-server`: once a
     * minute is enough, since a chat open in the app shows before its thread is listed. A change
     * made through this backend (rename, delete, archive) drops them.
     */
    private val listings = HashMap<String?, Pair<Long, List<AgentSessionInfo>>>()

    override fun listSessions(cwd: String?): List<AgentSessionInfo> =
        // Refreshes that come together share one server instead of starting one each.
        synchronized(listings) {
            listings[cwd]?.takeIf { now() - it.first < LIST_TTL_MS }?.let { return it.second }
            val listed = listFromServer(cwd) ?: return emptyList()
            listings[cwd] = now() to listed
            listed
        }

    private fun forgetListings() = synchronized(listings) { listings.clear() }

    private fun listFromServer(cwd: String?): List<AgentSessionInfo>? =
        withServer { codex ->
            // Codex lists archived threads apart: both, so archived ones stay in reach.
            listOf(false, true).flatMap { archived ->
                val params =
                    buildJsonObject {
                        put("limit", LIST_LIMIT)
                        put("archived", archived)
                        cwd?.let { put("cwd", it) }
                    }
                (codex.call("thread/list", params)["data"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::info)?.copy(archived = archived) }
            }
        }

    override fun session(sessionId: String): AgentSessionInfo? = withServer { codex -> (read(codex, sessionId, turns = false))?.let(::info) }

    override fun transcript(sessionId: String): List<TranscriptEntry> = withServer { codex -> read(codex, sessionId, turns = true)?.let { CodexItems.history(it) } }.orEmpty()

    override fun rename(
        sessionId: String,
        title: String,
    ) {
        withServer { codex ->
            codex.call(
                "thread/name/set",
                buildJsonObject {
                    put("threadId", sessionId)
                    put("name", title)
                },
            )
        }
        // After the change: a listing made meanwhile would hold the old state.
        forgetListings()
    }

    override fun delete(sessionId: String): Boolean {
        val deleted = withServer { codex -> codex.call("thread/delete", buildJsonObject { put("threadId", sessionId) }) } != null
        forgetListings()
        return deleted
    }

    override fun archive(
        sessionId: String,
        archived: Boolean,
    ): Boolean {
        val done = withServer { codex -> codex.call(if (archived) "thread/archive" else "thread/unarchive", buildJsonObject { put("threadId", sessionId) }) } != null
        forgetListings()
        return done
    }

    private suspend fun read(
        codex: CodexConnection,
        threadId: String,
        turns: Boolean,
    ): JsonObject? =
        codex.call(
            "thread/read",
            buildJsonObject {
                put("threadId", threadId)
                put("includeTurns", turns)
            },
        )["thread"] as? JsonObject

    companion object {
        const val ID = "codex"
        private const val LIST_LIMIT = 200
        private const val LIST_TTL_MS = 60_000L

        /** A thread as the session list shows it. Codex's times are seconds. */
        fun info(thread: JsonObject): AgentSessionInfo? {
            val id = thread.text("id") ?: return null
            val name = thread.text("name")?.takeIf { it.isNotBlank() }
            val preview =
                thread
                    .text("preview")
                    ?.lineSequence()
                    ?.firstOrNull { it.isNotBlank() }
                    ?.trim()
            return AgentSessionInfo(
                sessionId = id,
                title = name ?: preview ?: id,
                customTitle = name,
                firstPrompt = preview,
                lastPrompt = null,
                cwd = thread.text("cwd"),
                gitBranch = (thread["gitInfo"] as? JsonObject)?.text("branch"),
                createdAt = thread.long("createdAt")?.times(1000),
                lastModified = (thread.long("updatedAt") ?: thread.long("createdAt") ?: 0L) * 1000,
            )
        }
    }
}
