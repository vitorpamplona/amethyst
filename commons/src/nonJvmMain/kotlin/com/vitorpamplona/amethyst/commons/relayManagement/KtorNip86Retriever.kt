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
package com.vitorpamplona.amethyst.commons.relayManagement

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nip86RelayManagement.Nip86Client
import com.vitorpamplona.quartz.nip86RelayManagement.rpc.Nip86Request
import com.vitorpamplona.quartz.nip86RelayManagement.rpc.Nip86Response
import com.vitorpamplona.quartz.utils.Log
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException

private const val CONTENT_TYPE = "application/nostr+json+rpc"

/**
 * NIP-86 relay management over Ktor, the JVM's Nip86Retriever for the targets without OkHttp: the
 * JSON-RPC call, signed with a NIP-98 header, on the relay's own route ([httpClient], so a
 * Tor-routed relay is managed through Tor too).
 */
class KtorNip86Retriever(
    private val httpClient: (NormalizedRelayUrl) -> HttpClient,
) : Nip86Executor {
    override suspend fun execute(
        client: Nip86Client,
        request: Nip86Request,
    ): Nip86Response {
        val bodyBytes = client.serializeRequest(request).encodeToByteArray()
        val authToken =
            try {
                client.buildAuthHeader(bodyBytes)
            } catch (e: SignerExceptions) {
                // Signing the NIP-98 header is up to the user's signer; a rejection is an answer.
                Log.w("KtorNip86Retriever", "Could not sign the NIP-98 header for ${client.httpUrl}", e)
                return Nip86Response(error = e.message ?: "Could not sign the request")
            }

        return try {
            val response =
                httpClient(client.relayUrl).post(client.httpUrl) {
                    header("Content-Type", CONTENT_TYPE)
                    header("Accept", CONTENT_TYPE)
                    header("Authorization", authToken)
                    setBody(bodyBytes)
                }
            val body = response.bodyAsText()
            when {
                response.status.value == 401 -> Nip86Response(error = "Unauthorized: relay rejected authentication")
                !response.status.isSuccess() -> Nip86Response(error = "HTTP ${response.status.value}: $body")
                else ->
                    try {
                        client.parseResponse(body)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.e("KtorNip86Retriever", "Failed to parse response ${client.httpUrl}: $body", e)
                        Nip86Response(error = "Failed to parse response: ${e.message}")
                    }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("KtorNip86Retriever", "Failed to reach relay ${client.relayUrl.url}", e)
            Nip86Response(error = "Failed to reach relay: ${e.message}")
        }
    }
}
