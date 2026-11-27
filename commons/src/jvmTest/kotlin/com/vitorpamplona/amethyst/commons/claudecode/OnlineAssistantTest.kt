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

import com.vitorpamplona.agents.AgentEvent
import com.vitorpamplona.agents.ImageInput
import com.vitorpamplona.amethyst.commons.model.AccountSettings
import com.vitorpamplona.amethyst.commons.model.UiSettingsFlow
import com.vitorpamplona.amethyst.commons.settings.registry.AmethystSettings
import com.vitorpamplona.claudecode.ClaudeBackend
import com.vitorpamplona.claudecode.process.ClaudeCodeStarter
import com.vitorpamplona.claudecode.session.ClaudeSessionStore
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nipFAAgentKeys.tags.AgentKeyTag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The assistant on the user's own AI key: no agent program installed, a scripted provider instead. */
class OnlineAssistantTest {
    private lateinit var root: File
    private lateinit var scope: CoroutineScope
    private val key = AgentKeyTag("sk-test", "https://api.example.com/v1")
    private val tools = SettingsToolServer({ AmethystSettings.registry(UiSettingsFlow(), AccountSettings(KeyPair())) }, { })

    /** Answers each request with the next scripted response, and keeps what was sent. */
    private class ScriptedProvider(
        vararg responses: String,
    ) : ChatCompletionsApi {
        private val left = ArrayDeque(responses.toList())
        val sent = CopyOnWriteArrayList<String>()

        override suspend fun models(key: AgentKeyTag) = listOf("big-model-opus", "vendor/claude-sonnet-9")

        override suspend fun complete(
            key: AgentKeyTag,
            body: String,
        ): String {
            sent.add(body)
            return left.removeFirstOrNull() ?: throw IOException("no more scripted answers")
        }
    }

    @BeforeTest
    fun setUp() {
        root = Files.createTempDirectory("assistant").toFile()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @AfterTest
    fun tearDown() {
        scope.cancel()
        root.deleteRecursively()
    }

    private fun service() =
        ClaudeCodeService(
            backends =
                listOf(
                    ClaudeBackend(
                        ClaudeSessionStore(File(root, "claude")),
                        ClaudeCodeStarter { _, _ -> throw IOException("not installed") },
                        // No `claude` on this computer: the assistant runs on the key.
                        findExecutable = { null },
                    ),
                ),
            scope = scope,
            agentWorkspaces = AgentWorkspaces(File(root, "agents")),
            assistantDir = File(root, "assistant"),
            online = OnlineAgentBackend(OnlineSessionStore(File(root, "online"))),
        )

    private suspend fun waitFor(condition: () -> Boolean) =
        withTimeout(10_000) {
            while (!condition()) delay(10)
        }

    private val postCall =
        """{"choices":[{"message":{"role":"assistant","content":"I'll post it.","tool_calls":[{"id":"c1","type":"function","function":{"name":"mcp__amethyst-account__post_note","arguments":"{\"text\":\"gm\"}"}}]}}],"usage":{"prompt_tokens":1200}}"""
    private val done = """{"choices":[{"message":{"role":"assistant","content":"Done."}}]}"""

    @Test
    fun aPostAsksEveryTimeAndNeverOffersToStopAsking() =
        runBlocking {
            val provider = ScriptedProvider(postCall, done)
            val service = service()
            val chat = service.settingsAssistant(tools, "Amethyst assistant", null, OnlineAgentBackend.Access({ key }, provider))
            assertEquals(OnlineAgentBackend.ID, chat.backend.id)

            chat.send("post gm")
            waitFor {
                chat.state.value.pendingPermissions
                    .isNotEmpty()
            }
            val card =
                chat.state.value.pendingPermissions
                    .single()
            assertTrue(card.suppressAlwaysAllow, "a post must not offer 'don't ask again'")
            assertTrue(card.defaultToNo, "a post must default to No")
            assertTrue(card.parsedSuggestions.isEmpty())
            assertEquals("gm", (card.input["text"] as JsonPrimitive).content)

            chat.approve(card, always = true)
            waitFor { chat.state.value.status == ChatStatus.READY }
            val call =
                chat.state.value.items
                    .filterIsInstance<ChatItem.ToolCall>()
                    .single()
            // No account is bound here: the tool says so rather than pretending it posted.
            assertTrue(call.isError)
            assertEquals(BoundToolServer.UNBOUND, call.result)
            assertEquals(
                "Done.",
                chat.state.value.items
                    .filterIsInstance<ChatItem.AssistantText>()
                    .last()
                    .text,
            )

            // The mid-size model is the default, the tools went out, and the result went back.
            assertTrue(provider.sent.first().contains("\"model\":\"vendor/claude-sonnet-9\""), provider.sent.first())
            assertTrue(provider.sent.first().contains("mcp__amethyst-account__zap_note"))
            assertTrue(provider.sent.last().contains("\"tool_call_id\":\"c1\""))
            assertFalse(provider.sent.last().contains("is_error"))
        }

    @Test
    fun theSessionIsListedAndResumesAfterARestart() =
        runBlocking {
            val provider = ScriptedProvider(done, done)
            val access = OnlineAgentBackend.Access({ key }, provider)
            val first = service().settingsAssistant(tools, "Amethyst assistant", null, access)
            first.send("hello")
            waitFor { first.state.value.status == ChatStatus.READY && first.state.value.sessionId != null }
            val id = assertNotNull(first.state.value.sessionId)

            val again = service()
            again.bindAssistant(tools, null, access)
            again.refresh()
            val row = again.rows.value.single()
            assertEquals(id, row.sessionId)
            assertEquals(OnlineAgentBackend.ID, row.agentId)

            val resumed = assertNotNull(again.chat(row.key))
            assertEquals(ChatKind.SETTINGS, resumed.state.value.kind)
            assertEquals(OnlineAgentBackend.ID, resumed.backend.id)
            assertEquals(
                id,
                again
                    .settingsAssistant(tools, "Amethyst assistant", null, access)
                    .state.value.sessionId,
            )

            resumed.send("again")
            waitFor { provider.sent.size == 2 && resumed.state.value.status == ChatStatus.READY }
            // The second turn carried the first one's history.
            assertTrue(provider.sent.last().contains("hello"), provider.sent.last())
        }

    @Test
    fun aScreenshotAsksFirstReachesTheModelAsAPictureAndIsNotSaved() =
        runBlocking {
            val shot = """{"choices":[{"message":{"role":"assistant","content":"","tool_calls":[{"id":"s1","type":"function","function":{"name":"mcp__amethyst-screen__screenshot","arguments":"{}"}}]}}]}"""
            val provider = ScriptedProvider(shot, done)
            val picture = ImageInput.fromImage("window.png", BufferedImage(40, 20, BufferedImage.TYPE_INT_ARGB))
            val screen = ScreenToolServer(onScreen = { "Main window:\n- Home" }, capture = { listOf(picture) })
            val service = service()
            val chat = service.settingsAssistant(tools, "Amethyst assistant", null, OnlineAgentBackend.Access({ key }, provider), screen)

            chat.send("what does my screen look like?")
            waitFor {
                chat.state.value.pendingPermissions
                    .isNotEmpty()
            }
            val card =
                chat.state.value.pendingPermissions
                    .single()
            assertTrue(card.suppressAlwaysAllow && card.defaultToNo, "a screenshot must be asked about each time")
            assertTrue(
                chat.state.value.permissionNotes[card.requestId]!!
                    .contains("picture of the Amethyst window"),
            )

            chat.approve(card, always = false)
            waitFor { chat.state.value.status == ChatStatus.READY }
            val sent = provider.sent.last()
            assertTrue(sent.contains("\"image_url\"") && sent.contains("data:image/png;base64,${picture.base64}"), sent)
            assertFalse(sent.contains("app_pictures"), "the app's own mark must not reach the provider")

            val saved = File(root, "online").listFiles()!!.single().readText()
            assertFalse(saved.contains(picture.base64), "a picture is never written to disk")
        }

    @Test
    fun whatIsOnScreenRunsWithoutAsking() =
        runBlocking {
            val call = """{"choices":[{"message":{"role":"assistant","content":"","tool_calls":[{"id":"w1","type":"function","function":{"name":"mcp__amethyst-screen__what_is_on_screen","arguments":"{}"}}]}}]}"""
            val provider = ScriptedProvider(call, done)
            val screen = ScreenToolServer(onScreen = { "Main window:\n- {\"type\":\"Note\",\"id\":\"abc\"}" })
            val chat = service().settingsAssistant(tools, "Amethyst assistant", null, OnlineAgentBackend.Access({ key }, provider), screen)
            chat.send("summarize this")
            waitFor { chat.state.value.status == ChatStatus.READY && provider.sent.size == 2 }
            assertTrue(
                chat.state.value.pendingPermissions
                    .isEmpty(),
            )
            assertTrue(provider.sent.last().contains("Main window"), provider.sent.last())
        }

    @Test
    fun aCallStoppedBeforeItRanIsAnsweredSoTheNextTurnWorks() =
        runBlocking {
            val provider = ScriptedProvider(postCall, done)
            val chat = service().settingsAssistant(tools, "Amethyst assistant", null, OnlineAgentBackend.Access({ key }, provider))
            chat.send("post gm")
            waitFor {
                chat.state.value.pendingPermissions
                    .isNotEmpty()
            }

            chat.interrupt()
            waitFor { chat.state.value.status == ChatStatus.READY }

            chat.send("never mind")
            waitFor { provider.sent.size == 2 && chat.state.value.status == ChatStatus.READY }
            // The stopped call has its answer before the next message, or the provider refuses the history.
            val next = provider.sent.last()
            assertTrue(next.indexOf("\"tool_call_id\":\"c1\"") in 0 until next.indexOf("never mind"), next)
            assertEquals(
                "Done.",
                chat.state.value.items
                    .filterIsInstance<ChatItem.AssistantText>()
                    .last()
                    .text,
            )
        }

    /**
     * A new chat names its session as the first turn starts: the rename runs on another thread
     * while the turn appends the model's reply, and must not put back the history it read (CI once
     * sent a second turn without the assistant message that proposed the post).
     */
    @Test
    fun aRenameDuringATurnKeepsEveryMessage() =
        runBlocking {
            val turns = 200
            val provider = ScriptedProvider(*Array(turns) { done })
            val connection =
                OnlineAgentConnection(
                    session = OnlineSession("s1", null, root.path, 0L, "vendor/claude-sonnet-9", emptyList()),
                    key = key,
                    api = provider,
                    store = OnlineSessionStore(File(root, "online")),
                    tools = emptyList(),
                    preApproved = emptySet(),
                    instructions = null,
                    models = emptyList(),
                    // In memory only: a rename then is a bare read-copy-write, as fast as it can race.
                    persist = false,
                    scope = scope,
                )
            val ended = AtomicInteger(0)
            val events = scope.launch { connection.events.collect { if (it is AgentEvent.TurnEnded) ended.incrementAndGet() } }
            val renaming =
                List(4) { n ->
                    scope.launch {
                        var i = 0
                        while (isActive) {
                            connection.rename("title ${n * 1_000_000 + i++}")
                            yield()
                        }
                    }
                }
            repeat(turns) { connection.send("prompt $it", emptyList()) }
            waitFor { ended.get() == turns }
            renaming.forEach { it.cancelAndJoin() }
            events.cancel()

            // Each turn saw every earlier prompt and reply.
            val last = provider.sent.last()
            repeat(turns - 1) { assertTrue(last.contains("\"prompt $it\""), "prompt $it is missing from the last request") }
            assertEquals(turns - 1, Regex("\"role\":\"assistant\"").findAll(last).count(), "a reply is missing from the last request")
        }

    @Test
    fun answeredLeavesACompleteHistoryAlone() {
        val history =
            listOf(postCall, done).map { (Json.parseToJsonElement(it).jsonObject["choices"] as JsonArray)[0].jsonObject["message"]!!.jsonObject }
        val withResult =
            listOf(
                history[0],
                JsonObject(mapOf("role" to JsonPrimitive("tool"), "tool_call_id" to JsonPrimitive("c1"), "content" to JsonPrimitive("ok"))),
                history[1],
            )
        assertEquals(withResult, answered(withResult))
        // Without its result, the call gets one right after it.
        val fixed = answered(listOf(history[0], history[1]))
        assertEquals(3, fixed.size)
        assertEquals("c1", (fixed[1]["tool_call_id"] as JsonPrimitive).content)
    }

    @Test
    fun withoutAKeyTheAssistantStaysOnClaudeCode() =
        runBlocking {
            val chat = service().settingsAssistant(tools, "Amethyst assistant", null, OnlineAgentBackend.Access({ null }, ScriptedProvider()))
            assertEquals(ClaudeBackend.ID, chat.backend.id)
        }
}
