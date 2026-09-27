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

import com.vitorpamplona.quartz.contextvm.jsonrpc.JsonRpcMessage
import com.vitorpamplona.quartz.contextvm.jsonrpc.JsonRpcSuccess
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * What a coordinator says about itself in the MCP `initialize` handshake.
 *
 * ## Every field is a claim
 *
 * A coordinator's name and version are strings it chose, signed with nothing
 * but the key that was already going to sign the response. They are useful for
 * telling two coordinators apart in a list and for reporting a bug against the
 * right software; they are **not** identity, which is the pubkey and only the
 * pubkey (`spec/00.md` §8.5). A UI must not present them as verified, and
 * nothing in the protocol should branch on them.
 *
 * [protocolVersion] is the one field worth acting on: a coordinator answering
 * a version this client does not implement is a coordinator whose later
 * answers may not mean what they appear to.
 */
data class CoordinatorServerInfo(
    val name: String?,
    val version: String?,
    val protocolVersion: String?,
    /** The capability object verbatim, for a screen that wants to show it raw. */
    val capabilities: JsonObject?,
) {
    /** Nothing usable came back, so there is nothing worth showing. */
    val isEmpty: Boolean get() = name == null && version == null && protocolVersion == null

    companion object {
        /**
         * Reads [response] if it is a successful `initialize` result.
         *
         * Every field is optional and a missing one becomes null rather than an
         * error: MCP allows a server to omit `serverInfo` entirely, and a
         * handshake that worked should not be reported as a failure because
         * the server declined to name itself.
         */
        fun from(response: JsonRpcMessage): CoordinatorServerInfo? {
            val result = (response as? JsonRpcSuccess)?.result as? JsonObject ?: return null
            val info = result["serverInfo"] as? JsonObject
            return CoordinatorServerInfo(
                name = info?.get("name")?.jsonPrimitive?.contentOrNullSafe(),
                version = info?.get("version")?.jsonPrimitive?.contentOrNullSafe(),
                protocolVersion = result["protocolVersion"]?.jsonPrimitive?.contentOrNullSafe(),
                capabilities = result["capabilities"]?.jsonObject,
            )
        }
    }
}

/**
 * The string content, or null where the value is JSON `null`.
 *
 * `jsonPrimitive.content` renders a JSON null as the four-character string
 * "null", which would put the word "null" on a settings screen as a
 * coordinator's name.
 */
private fun JsonPrimitive.contentOrNullSafe(): String? = if (this is JsonNull) null else content.takeIf { it.isNotEmpty() }
