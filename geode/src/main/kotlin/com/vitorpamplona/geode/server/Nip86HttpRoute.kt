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
package com.vitorpamplona.geode.server

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import com.vitorpamplona.quartz.nip86RelayManagement.rpc.Nip86Request
import com.vitorpamplona.quartz.nip86RelayManagement.rpc.Nip86Response
import com.vitorpamplona.quartz.nip86RelayManagement.server.Nip86Server
import com.vitorpamplona.quartz.nip98HttpAuth.Nip98AuthVerifier
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respondText
import io.ktor.utils.io.readAvailable

/**
 * NIP-86 admin POST handler. Owns the gating order:
 *  1. 403 if no admin pubkey list is configured (endpoint disabled).
 *  2. 413 if body exceeds [maxBodyBytes] (declared or actual).
 *  3. 401 if the NIP-98 Authorization header is missing/invalid.
 *  4. 403 if the verified pubkey isn't in [allowList].
 *  5. 400 if the body isn't a valid Nip86Request.
 *  6. 200 with a Nip86Response JSON body otherwise.
 *
 * The [signedUrlFor] callback resolves what URL the client must have
 * signed in their NIP-98 token. Operators configure the canonical
 * `publicUrl`; loopback tests fall back to the request's `Host`
 * header. We pass it as a callback rather than a string so the route
 * doesn't need to know about Ktor request internals.
 */
internal class Nip86HttpRoute(
    private val server: Nip86Server,
    private val verifier: Nip98AuthVerifier,
    private val allowList: Set<HexKey>,
    private val maxBodyBytes: Int,
    private val signedUrlFor: (ApplicationCall) -> String,
) {
    suspend fun handle(call: ApplicationCall) {
        if (allowList.isEmpty()) {
            call.respondText(
                "NIP-86 management API is not enabled on this relay.",
                ContentType.Text.Plain,
                HttpStatusCode.Forbidden,
            )
            return
        }

        val body = readBoundedBody(call) ?: return
        val pubkey = verifyAuth(call, body) ?: return
        if (pubkey.lowercase() !in allowList) {
            call.respondText(
                "pubkey is not on the admin list",
                ContentType.Text.Plain,
                HttpStatusCode.Forbidden,
            )
            return
        }

        val req =
            try {
                JsonMapper.fromJson<Nip86Request>(body.decodeToString())
            } catch (e: Exception) {
                call.respondText(
                    "invalid Nip86Request: ${e.message ?: e::class.simpleName}",
                    ContentType.Text.Plain,
                    HttpStatusCode.BadRequest,
                )
                return
            }

        val response: Nip86Response = server.dispatch(req)
        audit(pubkey, req, response)
        call.respondText(
            JsonMapper.toJson(response),
            ContentType.parse("application/nostr+json+rpc"),
            HttpStatusCode.OK,
        )
    }

    private suspend fun readBoundedBody(call: ApplicationCall): ByteArray? {
        val declared = call.request.headers[HttpHeaders.ContentLength]?.toLongOrNull()
        if (declared != null && declared > maxBodyBytes) {
            call.respondText(
                "request body exceeds $maxBodyBytes-byte cap",
                ContentType.Text.Plain,
                HttpStatusCode.PayloadTooLarge,
            )
            return null
        }
        val ch = call.receiveChannel()
        val buf = ByteArray(maxBodyBytes + 1)
        var pos = 0
        while (pos <= maxBodyBytes) {
            val read = ch.readAvailable(buf, pos, buf.size - pos)
            if (read <= 0) break
            pos += read
        }
        if (pos > maxBodyBytes) {
            call.respondText(
                "request body exceeds $maxBodyBytes-byte cap",
                ContentType.Text.Plain,
                HttpStatusCode.PayloadTooLarge,
            )
            return null
        }
        return buf.copyOfRange(0, pos)
    }

    private suspend fun verifyAuth(
        call: ApplicationCall,
        body: ByteArray,
    ): HexKey? {
        val header = call.request.header(HttpHeaders.Authorization)
        val verification = verifier.verify(header, method = "POST", url = signedUrlFor(call), body = body)
        return when (verification) {
            is Nip98AuthVerifier.Result.Verified -> {
                verification.pubkey
            }

            Nip98AuthVerifier.Result.Missing -> {
                call.response.headers.append(HttpHeaders.WWWAuthenticate, Nip98AuthVerifier.SCHEME.trim())
                call.respondText(
                    "missing Authorization header (NIP-98)",
                    ContentType.Text.Plain,
                    HttpStatusCode.Unauthorized,
                )
                null
            }

            is Nip98AuthVerifier.Result.Malformed -> {
                call.respondText(
                    "invalid NIP-98 Authorization: ${verification.reason}",
                    ContentType.Text.Plain,
                    HttpStatusCode.Unauthorized,
                )
                null
            }
        }
    }

    /**
     * Audit log: structured single line so an operator can grep
     * "nip86" / pubkey / method without a logging framework
     * dependency. Best-effort — a missing log line shouldn't fail
     * the response.
     */
    private fun audit(
        pubkey: HexKey,
        req: Nip86Request,
        response: Nip86Response,
    ) {
        runCatching {
            System.err.println(
                "nip86 audit pubkey=$pubkey method=${req.method} ok=${response.error == null}" +
                    (response.error?.let { " error=$it" } ?: ""),
            )
        }
    }
}
