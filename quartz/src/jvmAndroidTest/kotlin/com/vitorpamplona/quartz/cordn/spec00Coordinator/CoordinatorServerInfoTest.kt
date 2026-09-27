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
package com.vitorpamplona.quartz.cordn.spec00Coordinator

import com.vitorpamplona.quartz.contextvm.jsonrpc.JsonRpcError
import com.vitorpamplona.quartz.contextvm.jsonrpc.JsonRpcFailure
import com.vitorpamplona.quartz.contextvm.jsonrpc.JsonRpcId
import com.vitorpamplona.quartz.contextvm.jsonrpc.JsonRpcNotification
import com.vitorpamplona.quartz.contextvm.jsonrpc.JsonRpcSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `initialize` is the one coordinator call whose answer is pure claim: a name,
 * a version, a capability object, none of it bound to anything but the key
 * that was already signing the response (`spec/00.md` §8.5). So the only
 * things worth asserting are that a claim survives the trip intact, and that
 * every way a server can decline to make one leaves a null rather than a
 * plausible-looking string on a settings screen.
 */
class CoordinatorServerInfoTest {
    @Test
    fun `reads name version protocol and capabilities`() {
        val info =
            CoordinatorServerInfo.from(
                success(
                    """
                    {
                      "protocolVersion": "2025-06-18",
                      "serverInfo": { "name": "cordn", "version": "0.5.7" },
                      "capabilities": { "tools": {} }
                    }
                    """,
                ),
            )!!

        assertEquals("cordn", info.name)
        assertEquals("0.5.7", info.version)
        assertEquals("2025-06-18", info.protocolVersion)
        assertEquals(JsonObject(emptyMap()), info.capabilities!!["tools"])
        assertFalse(info.isEmpty)
    }

    @Test
    fun `a handshake with no serverInfo is still a handshake`() {
        // MCP lets a server omit serverInfo. Reporting that as a failed
        // initialize would tell a user their coordinator is unreachable when
        // it answered perfectly well.
        val info = CoordinatorServerInfo.from(success("""{ "protocolVersion": "2025-06-18" }"""))!!

        assertNull(info.name)
        assertNull(info.version)
        assertEquals("2025-06-18", info.protocolVersion)
        assertFalse(info.isEmpty, "a protocol version alone is worth showing")
    }

    @Test
    fun `an empty result is empty rather than a row of blanks`() {
        val info = CoordinatorServerInfo.from(success("{}"))!!

        assertTrue(info.isEmpty)
        assertNull(info.capabilities)
    }

    @Test
    fun `a JSON null name does not become the word null`() {
        // jsonPrimitive.content renders JSON null as the four-character string
        // "null", which is how a coordinator ends up listed as being called
        // "null". Same for an empty string, which renders as a blank row.
        val info =
            CoordinatorServerInfo.from(
                success("""{ "serverInfo": { "name": null, "version": "" }, "protocolVersion": null }"""),
            )!!

        assertNull(info.name)
        assertNull(info.version)
        assertNull(info.protocolVersion)
        assertTrue(info.isEmpty)
    }

    @Test
    fun `a non-success response yields nothing`() {
        assertNull(
            CoordinatorServerInfo.from(
                JsonRpcFailure(JsonRpcId.Num(1), JsonRpcError(JsonRpcError.METHOD_NOT_FOUND, "no initialize here")),
            ),
        )
        assertNull(CoordinatorServerInfo.from(JsonRpcNotification("notifications/progress")))
    }

    @Test
    fun `a result that is not an object yields nothing`() {
        // A server answering `"result": "ok"` is not an MCP server; reading a
        // name out of it would be inventing one.
        assertNull(CoordinatorServerInfo.from(JsonRpcSuccess(JsonRpcId.Num(1), JsonPrimitive("ok"))))
    }

    private fun success(result: String) = JsonRpcSuccess(JsonRpcId.Num(1), Json.parseToJsonElement(result.trimIndent()))
}
