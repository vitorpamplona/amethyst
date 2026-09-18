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
package com.vitorpamplona.quartz.contextvm.cep08Payments

import com.vitorpamplona.quartz.contextvm.json.toPlainJson
import com.vitorpamplona.quartz.contextvm.jsonrpc.JsonRpcRequest
import com.vitorpamplona.quartz.contextvm.mcp.McpParams
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.utils.jcs.JsonCanonicalization
import com.vitorpamplona.quartz.utils.sha256.sha256
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

/**
 * CEP-8's canonical invocation identity for the `explicit_gating` lifecycle.
 *
 * A successful payment authorizes a *future* execution, and the client is told
 * to retry "the same request". That has to be defined on something stable, so
 * the identity is the client pubkey plus
 * `sha256(JCS({method, params}))` — with `params._meta` removed.
 *
 * The `_meta` exclusion is the load-bearing part. MCP regenerates
 * `progressToken` on every `callTool`, so without it two semantically identical
 * invocations hash differently and a paid authorization could never be matched.
 * The JSON-RPC id, the outer event id, timestamps, signatures and tags are all
 * excluded for the same reason: a retry may legitimately change any of them.
 *
 * The exclusion applies **only** to identity derivation. When an authorization
 * is consumed, the full original `params` — `_meta` included — must still reach
 * the handler, so progress and streaming keep working at execution time.
 */
object CanonicalInvocation {
    /** Derives the invocation identity hash from an MCP request. */
    fun identityOf(request: JsonRpcRequest): HexKey = identityOf(request.method, request.params)

    fun identityOf(
        method: String,
        params: JsonObject?,
    ): HexKey {
        val payload =
            buildMap<String, Any?> {
                put(METHOD, method)
                params?.let { put(PARAMS, semanticParams(it).toPlainJson()) }
            }

        return sha256(JsonCanonicalization.canonicalize(payload).encodeToByteArray()).toHexKey()
    }

    /**
     * The full authorization key: the requesting client plus the invocation.
     *
     * A payment authorizes one client's future execution, not anyone's, so the
     * pubkey is part of the identity rather than context around it.
     */
    fun authorizationKey(
        clientPubKey: HexKey,
        request: JsonRpcRequest,
    ) = clientPubKey.lowercase() + ":" + identityOf(request)

    /** [params] with `_meta` removed; everything else untouched. */
    fun semanticParams(params: JsonObject): JsonObject =
        if (!params.containsKey(McpParams.META)) {
            params
        } else {
            buildJsonObject {
                params.forEach { (key, value) -> if (key != McpParams.META) put(key, value) }
            }
        }

    private const val METHOD = "method"
    private const val PARAMS = "params"
}
