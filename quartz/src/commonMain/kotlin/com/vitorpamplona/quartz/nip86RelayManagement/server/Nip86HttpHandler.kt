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
package com.vitorpamplona.quartz.nip86RelayManagement.server

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import com.vitorpamplona.quartz.nip86RelayManagement.rpc.Nip86Request
import com.vitorpamplona.quartz.nip86RelayManagement.rpc.Nip86Response
import com.vitorpamplona.quartz.nip98HttpAuth.Nip98AuthVerifier

/**
 * Canonical NIP-86-over-HTTP entry point. Drives the complete flow
 * the spec mandates — POST `application/nostr+json+rpc` with a
 * NIP-98 `Authorization: Nostr <base64>` header — so any relay
 * implementation can plug its HTTP framework in without re-deriving
 * the auth / parse / dispatch / serialize sequence:
 *
 *  1. **Gate.** If `server.isEnabled()` is false (empty admin list),
 *     reject with [Response.Disabled] (→ 403, "admin API not
 *     enabled"). No body read, no signature verify.
 *  2. **Size cap.** If `body.size > maxBodyBytes`, reject with
 *     [Response.PayloadTooLarge]. Adapters MUST also bound the read
 *     itself — NIP-98 forces us to compute sha256 over the full
 *     body for signature binding, so an unbounded read is a pre-auth
 *     OOM vector. This check is defense-in-depth.
 *  3. **Verify.** Run [Nip98AuthVerifier.verify] (method = `POST`,
 *     given [url] and [body]). Missing → [Response.MissingAuth]
 *     (→ 401 + `WWW-Authenticate: Nostr`). Malformed →
 *     [Response.BadAuth] (→ 401).
 *  4. **Admin check.** If the verified pubkey is not in
 *     `server.isAuthorized`, reject with [Response.NotAdmin] (→ 403).
 *  5. **Parse.** Decode [Nip86Request] from the body bytes. Invalid
 *     → [Response.BadRequest] (→ 400).
 *  6. **Dispatch.** Call `server.dispatch(pubkey, req)` and wrap the
 *     [Nip86Response] in [Response.Ok], pre-serialized as JSON ready
 *     to write to the wire with `Content-Type: application/nostr+json+rpc`.
 *
 * Transport-agnostic: takes raw primitives ([authHeader], [url],
 * [body]) and returns a sealed [Response]. The adapter maps each
 * variant to its framework's status-code / header API — for Ktor,
 * see `geode/server/Nip86HttpRoute`.
 *
 * @param maxBodyBytes Defense-in-depth cap. NIP-86 RPC payloads are
 *   a few hundred bytes; the 1 MiB default is ~1000× any plausible
 *   request, but small enough that an attacker can't OOM the relay
 *   if the adapter forgets its own bound.
 */
class Nip86HttpHandler(
    private val server: Nip86Server,
    private val verifier: Nip98AuthVerifier = Nip98AuthVerifier(),
    val maxBodyBytes: Int = DEFAULT_MAX_BODY_BYTES,
) {
    suspend fun handle(
        authHeader: String?,
        url: String,
        body: ByteArray,
    ): Response {
        if (!server.isEnabled()) return Response.Disabled
        if (body.size > maxBodyBytes) return Response.PayloadTooLarge(maxBodyBytes)

        val pubkey =
            when (val v = verifier.verify(authHeader, method = "POST", url = url, body = body)) {
                is Nip98AuthVerifier.Result.Verified -> v.pubkey
                Nip98AuthVerifier.Result.Missing -> return Response.MissingAuth
                is Nip98AuthVerifier.Result.Malformed -> return Response.BadAuth(v.reason)
            }

        if (!server.isAuthorized(pubkey)) return Response.NotAdmin

        val req =
            try {
                JsonMapper.fromJson<Nip86Request>(body.decodeToString())
            } catch (e: Exception) {
                return Response.BadRequest("invalid Nip86Request: ${e.message ?: e::class.simpleName}")
            }

        val response = server.dispatch(pubkey, req)
        return Response.Ok(pubkey, req, response, JsonMapper.toJson(response))
    }

    /**
     * Outcome of one canonical NIP-86 HTTP request. Adapters map each
     * variant to a status code + body:
     *
     * | Variant | HTTP | Notes |
     * |---|---|---|
     * | [Disabled] | 403 | "admin API not enabled" |
     * | [PayloadTooLarge] | 413 | adapter SHOULD bound the read itself |
     * | [MissingAuth] | 401 | send `WWW-Authenticate: Nostr` |
     * | [BadAuth] | 401 | NIP-98 signature/binding failed |
     * | [NotAdmin] | 403 | verified pubkey not on allow-list |
     * | [BadRequest] | 400 | body wasn't a valid `Nip86Request` |
     * | [Ok] | 200 | `Content-Type: application/nostr+json+rpc`; write [Ok.json] |
     */
    sealed interface Response {
        data object Disabled : Response

        data class PayloadTooLarge(
            val cap: Int,
        ) : Response

        data object MissingAuth : Response

        data class BadAuth(
            val reason: String,
        ) : Response

        data object NotAdmin : Response

        data class BadRequest(
            val reason: String,
        ) : Response

        /**
         * Successful round-trip. Carries the verified [pubkey] and
         * decoded [request] alongside the serialized response, so
         * adapters can audit / log without re-parsing.
         */
        data class Ok(
            val pubkey: HexKey,
            val request: Nip86Request,
            val response: Nip86Response,
            val json: String,
        ) : Response
    }

    companion object {
        /** 1 MiB — see class KDoc on the cap rationale. */
        const val DEFAULT_MAX_BODY_BYTES: Int = 1 shl 20

        /** `application/nostr+json+rpc` — the wire type for both directions. */
        const val CONTENT_TYPE: String = "application/nostr+json+rpc"

        /** Value for the `WWW-Authenticate` header on 401 responses. Matches NIP-98. */
        const val WWW_AUTHENTICATE: String = "Nostr"
    }
}
