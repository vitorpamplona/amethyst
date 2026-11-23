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

import com.vitorpamplona.agents.CommandGuard
import com.vitorpamplona.agents.PermissionDecision
import com.vitorpamplona.agents.PermissionMode
import com.vitorpamplona.claudecode.process.ClaudeCodeConnection
import com.vitorpamplona.claudecode.protocol.ClaudeMessage
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ClaudeCodeConnectionTest {
    private fun obj(line: String) = Json.parseToJsonElement(line) as JsonObject

    @Test
    fun permissionPromptIsAnsweredWithTheRequestIdAndInput() =
        runTest {
            val transport = FakeTransport()
            val connection = ClaudeCodeConnection(transport, backgroundScope)
            val recorded = Fixtures.lines("turn-approve.jsonl")
            recorded.forEach { transport.emit(it) }
            transport.end()

            val messages = connection.messages.toList()
            val prompt = messages.filterIsInstance<ClaudeMessage.ControlRequest>().single().permission!!
            assertEquals(listOf(prompt), connection.openPermissionRequests())

            connection.respond(prompt, PermissionDecision.Allow())
            assertTrue(connection.openPermissionRequests().isEmpty())

            val answer = obj(transport.written.single())
            assertEquals("control_response", answer["type"]!!.jsonPrimitive.content)
            val response = answer["response"]!!.jsonObject
            assertEquals("success", response["subtype"]!!.jsonPrimitive.content)
            assertEquals(prompt.requestId, response["request_id"]!!.jsonPrimitive.content)
            val decision = response["response"]!!.jsonObject
            assertEquals("allow", decision["behavior"]!!.jsonPrimitive.content)
            assertEquals(prompt.input, decision["updatedInput"])
            assertEquals(prompt.toolUseId, decision["toolUseID"]!!.jsonPrimitive.content)
        }

    @Test
    fun denyCarriesTheMessage() =
        runTest {
            val transport = FakeTransport()
            val connection = ClaudeCodeConnection(transport, backgroundScope)
            Fixtures.lines("turn-deny.jsonl").forEach { transport.emit(it) }
            transport.end()
            val prompt = connection.messages.toList().firstNotNullOf { (it as? ClaudeMessage.ControlRequest)?.permission }

            connection.respond(prompt, PermissionDecision.Deny("Not now"))

            val decision = obj(transport.written.single())["response"]!!.jsonObject["response"]!!.jsonObject
            assertEquals("deny", decision["behavior"]!!.jsonPrimitive.content)
            assertEquals("Not now", decision["message"]!!.jsonPrimitive.content)
        }

    @Test
    fun alwaysAllowSendsTheSuggestionsBack() =
        runTest {
            val transport = FakeTransport()
            val connection = ClaudeCodeConnection(transport, backgroundScope)
            Fixtures.lines("turn-approve.jsonl").forEach { transport.emit(it) }
            transport.end()
            val prompt = connection.messages.toList().firstNotNullOf { (it as? ClaudeMessage.ControlRequest)?.permission }

            connection.respond(prompt, PermissionDecision.Allow(updatedPermissions = prompt.suggestions))

            val decision = obj(transport.written.single())["response"]!!.jsonObject["response"]!!.jsonObject
            assertEquals(prompt.suggestions, decision["updatedPermissions"])
        }

    @Test
    fun controlRequestsWaitForTheirAnswer() =
        runTest {
            val transport = FakeTransport()
            transport.onWrite = { line ->
                val request = obj(line)
                val id = request["request_id"]!!.jsonPrimitive.content
                transport.emit("""{"type":"control_response","response":{"subtype":"success","request_id":"$id","response":{"mode":"plan"}}}""")
            }
            val connection = ClaudeCodeConnection(transport, backgroundScope)

            val answer = assertNotNull(connection.request("set_permission_mode", mapOf("mode" to JsonPrimitive(PermissionMode.PLAN.wire))))
            assertTrue(answer.isSuccess)

            val sent = obj(transport.written.single())
            val request = sent["request"]!!.jsonObject
            assertEquals("set_permission_mode", request["subtype"]!!.jsonPrimitive.content)
            assertEquals("plan", request["mode"]!!.jsonPrimitive.content)
        }

    @Test
    fun unsupportedControlRequestsAreRefusedSoTheCliDoesNotWait() =
        runTest {
            val transport = FakeTransport()
            val connection = ClaudeCodeConnection(transport, backgroundScope)
            transport.emit("""{"type":"control_request","request_id":"h1","request":{"subtype":"hook_callback","callback_id":"x"}}""")
            transport.end()
            connection.messages.toList()

            val answer = obj(transport.written.single())["response"]!!.jsonObject
            assertEquals("error", answer["subtype"]!!.jsonPrimitive.content)
            assertEquals("h1", answer["request_id"]!!.jsonPrimitive.content)
        }

    @Test
    fun theCommandGuardIsAPreToolUseHookTheHostAnswers() =
        runTest {
            val transport = FakeTransport()
            transport.onWrite = { line ->
                val request = obj(line)
                if (request["type"]!!.jsonPrimitive.content == "control_request") {
                    val id = request["request_id"]!!.jsonPrimitive.content
                    transport.emit("""{"type":"control_response","response":{"subtype":"success","request_id":"$id","response":{}}}""")
                }
            }
            val guard = CommandGuard { command -> if (command.startsWith("gh pr merge")) "not here" else null }
            val connection = ClaudeCodeConnection(transport, backgroundScope, commandGuard = guard)
            connection.initialize()

            // The handshake registers the hook on Bash, failing closed.
            val hook =
                obj(transport.written.single())["request"]!!
                    .jsonObject["hooks"]!!
                    .jsonObject["PreToolUse"]!!
                    .jsonArray
                    .single()
                    .jsonObject
            assertEquals("Bash", hook["matcher"]!!.jsonPrimitive.content)
            assertEquals(
                ClaudeCodeConnection.GUARD_HOOK_ID,
                hook["hookCallbackIds"]!!
                    .jsonArray
                    .single()
                    .jsonPrimitive.content,
            )
            assertEquals("block", hook["onFailure"]!!.jsonPrimitive.content)

            fun callback(
                id: String,
                command: String,
            ) = """{"type":"control_request","request_id":"$id","request":{"subtype":"hook_callback","callback_id":"${ClaudeCodeConnection.GUARD_HOOK_ID}","input":{"hook_event_name":"PreToolUse","tool_name":"Bash","tool_input":{"command":"$command"}},"tool_use_id":"t"}}"""
            transport.emit(callback("h1", "gh pr merge 1"))
            transport.emit(callback("h2", "gh pr view 1"))
            transport.end()
            connection.messages.toList()

            val answers = transport.written.drop(1).map { obj(it)["response"]!!.jsonObject }
            assertEquals(listOf("h1", "h2"), answers.map { it["request_id"]!!.jsonPrimitive.content })
            val denied = answers[0]["response"]!!.jsonObject["hookSpecificOutput"]!!.jsonObject
            assertEquals("PreToolUse", denied["hookEventName"]!!.jsonPrimitive.content)
            assertEquals("deny", denied["permissionDecision"]!!.jsonPrimitive.content)
            assertEquals("not here", denied["permissionDecisionReason"]!!.jsonPrimitive.content)
            // Nothing to say about the other: the usual permission checks decide.
            assertEquals(JsonObject(emptyMap()), answers[1]["response"]!!.jsonObject)
        }

    @Test
    fun withoutAGuardTheHandshakeRegistersNoHook() =
        runTest {
            val transport = FakeTransport()
            transport.onWrite = { transport.end() }
            val connection = ClaudeCodeConnection(transport, backgroundScope)
            connection.initialize()
            assertEquals(null, obj(transport.written.single())["request"]!!.jsonObject["hooks"])
        }

    @Test
    fun sendWritesAUserMessage() =
        runTest {
            val transport = FakeTransport()
            val connection = ClaudeCodeConnection(transport, backgroundScope)
            connection.send("hello")
            val sent = obj(transport.written.single())
            assertEquals("user", sent["type"]!!.jsonPrimitive.content)
            assertEquals("hello", sent["message"]!!.jsonObject["content"]!!.jsonPrimitive.content)
        }

    @Test
    fun aProcessThatEndsBeforeAnsweringGivesNoAnswerInsteadOfCancellingTheCaller() =
        runTest {
            val transport = FakeTransport()
            val connection = ClaudeCodeConnection(transport, backgroundScope)
            // The CLI exits during the handshake (not logged in, an unknown session id).
            transport.onWrite = { transport.end() }

            val answer = connection.initialize()

            assertEquals(null, answer)
            // The caller goes on: it can see the process ended and report it.
            assertTrue(connection.reader.isCompleted)
        }
}
