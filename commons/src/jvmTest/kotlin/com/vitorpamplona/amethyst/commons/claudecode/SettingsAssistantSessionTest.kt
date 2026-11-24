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

import com.vitorpamplona.amethyst.commons.model.AccountSettings
import com.vitorpamplona.amethyst.commons.model.UiSettingsFlow
import com.vitorpamplona.amethyst.commons.settings.registry.AmethystSettings
import com.vitorpamplona.claudecode.ClaudeBackend
import com.vitorpamplona.claudecode.process.ClaudeCodeStarter
import com.vitorpamplona.claudecode.session.ClaudeSessionStore
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The settings assistant's sessions: listed, reopened as the assistant, and resumed after a restart. */
class SettingsAssistantSessionTest {
    private lateinit var config: File
    private lateinit var assistantDir: File
    private lateinit var scope: CoroutineScope
    private val older = "11111111-2222-3333-4444-555555555555"
    private val newer = "66666666-7777-8888-9999-000000000000"
    private val tools = SettingsToolServer({ AmethystSettings.registry(UiSettingsFlow(), AccountSettings(KeyPair())) }, { })

    @BeforeTest
    fun setUp() {
        config = Files.createTempDirectory("claude-config").toFile()
        assistantDir = File(config, "app/claude-settings-assistant").apply { mkdirs() }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        save(older, "how do I get dark mode", modified = 1_000_000)
        save(newer, "make the text bigger", modified = 2_000_000)
    }

    @AfterTest
    fun tearDown() {
        scope.cancel()
        config.deleteRecursively()
    }

    private fun save(
        id: String,
        prompt: String,
        modified: Long,
    ) {
        val cwd = assistantDir.absolutePath.replace("\\", "\\\\")
        val file = File(config, "projects/-assistant/$id.jsonl").apply { parentFile.mkdirs() }
        file.writeText(
            """{"type":"user","uuid":"u1","parentUuid":null,"sessionId":"$id","cwd":"$cwd","message":{"role":"user","content":"$prompt"}}""" + "\n" +
                """{"type":"assistant","uuid":"a1","parentUuid":"u1","sessionId":"$id","cwd":"$cwd","message":{"role":"assistant","content":[{"type":"text","text":"ok"}]}}""" + "\n",
        )
        assertTrue(file.setLastModified(modified))
    }

    /** A fresh service on the same folders: the app after a restart. */
    private fun service() =
        ClaudeCodeService(
            backends =
                listOf(
                    ClaudeBackend(
                        ClaudeSessionStore(config),
                        ClaudeCodeStarter { _, _ -> throw IOException("not in tests") },
                        findExecutable = { null },
                    ),
                ),
            scope = scope,
            agentWorkspaces = AgentWorkspaces(File(config, "agents")),
            assistantDir = assistantDir,
        )

    @Test
    fun theAssistantsSessionsAreListed() =
        runBlocking {
            val service = service()
            service.refresh()
            assertEquals(listOf(newer, older), service.rows.value.map { it.sessionId })
        }

    @Test
    fun aSavedAssistantSessionReopensAsTheAssistant() =
        runBlocking {
            val service = service()
            service.refresh()
            val chat = assertNotNull(service.chat(older))
            // Not a coding chat with files and a shell in the assistant's folder.
            assertEquals(ChatKind.SETTINGS, chat.state.value.kind)
        }

    @Test
    fun afterARestartTheAssistantResumesItsNewestSession() =
        runBlocking {
            val service = service()
            val chat = service.settingsAssistant(tools, "Settings assistant")
            assertEquals(newer, chat.state.value.sessionId)
            assertEquals(ChatKind.SETTINGS, chat.state.value.kind)
            // The same chat while the app runs.
            assertSame(chat, service.settingsAssistant(tools, "Settings assistant"))
        }

    @Test
    fun withNoSavedSessionTheAssistantStartsANewOne() =
        runBlocking {
            File(config, "projects").deleteRecursively()
            val chat = service().settingsAssistant(tools, "Settings assistant")
            assertEquals(null, chat.state.value.sessionId)
            assertEquals(assistantDir.path, chat.state.value.cwd)
        }

    @Test
    fun toolsReachWhicheverScreenBoundThemLast() =
        runBlocking {
            val bound = BoundSettingsTools()
            val search = JsonObject(mapOf("query" to JsonPrimitive("dark mode")))
            val unbound = bound.call(SettingsToolServer.SEARCH, search)
            assertTrue(unbound.isError)
            bound.target = tools
            val found = bound.call(SettingsToolServer.SEARCH, search)
            assertTrue(!found.isError && found.text.contains("app.theme"), found.text)
            assertEquals(tools.tools.map { it.name }, bound.tools.map { it.name })
        }
}
