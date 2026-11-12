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

import com.vitorpamplona.claudecode.process.ClaudeCodeConnection
import com.vitorpamplona.claudecode.protocol.ClaudeMessage
import com.vitorpamplona.claudecode.protocol.PermissionDecision
import com.vitorpamplona.claudecode.protocol.PermissionMode
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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

            val answer = assertNotNull(connection.setPermissionMode(PermissionMode.PLAN))
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
    fun sendWritesAUserMessage() =
        runTest {
            val transport = FakeTransport()
            val connection = ClaudeCodeConnection(transport, backgroundScope)
            connection.send("hello")
            val sent = obj(transport.written.single())
            assertEquals("user", sent["type"]!!.jsonPrimitive.content)
            assertEquals("hello", sent["message"]!!.jsonObject["content"]!!.jsonPrimitive.content)
        }
}
