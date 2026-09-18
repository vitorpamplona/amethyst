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
package com.vitorpamplona.contextvm.mcp

/**
 * The MCP methods ContextVM carries.
 *
 * ContextVM is a transport: these names are MCP's, not ContextVM's, and travel
 * unmodified inside the event `content`.
 */
object McpMethods {
    const val INITIALIZE = "initialize"
    const val INITIALIZED = "notifications/initialized"
    const val PING = "ping"

    const val TOOLS_LIST = "tools/list"
    const val TOOLS_CALL = "tools/call"
    const val RESOURCES_LIST = "resources/list"
    const val RESOURCE_TEMPLATES_LIST = "resources/templates/list"
    const val PROMPTS_LIST = "prompts/list"

    /**
     * The envelope CEP-22 and CEP-41 both ride.
     *
     * Their frames are ordinary MCP progress notifications with an extra `cvm`
     * object in `params`; a peer that does not understand ContextVM transfer
     * profiles still sees valid MCP.
     */
    const val PROGRESS = "notifications/progress"

    /** CEP-8 transparent lifecycle notifications. */
    const val PAYMENT_REQUIRED = "notifications/payment_required"
    const val PAYMENT_ACCEPTED = "notifications/payment_accepted"
    const val PAYMENT_REJECTED = "notifications/payment_rejected"
}

/**
 * Well-known member names inside MCP `params`.
 *
 * `_meta` matters beyond convenience: CEP-8 excludes it from the canonical
 * invocation identity (because MCP regenerates `progressToken` on every call)
 * while still requiring it to reach the handler at execution time.
 */
object McpParams {
    const val META = "_meta"
    const val PROGRESS_TOKEN = "progressToken"
    const val NAME = "name"
    const val ARGUMENTS = "arguments"

    /** CEP-16: the caller identity a server transport injects into `_meta`. */
    const val CLIENT_PUBKEY = "clientPubkey"
}
