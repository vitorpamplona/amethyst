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
package com.vitorpamplona.nestsclient.interop

import com.vitorpamplona.nestsclient.NestsAuth
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Phase-1 interop smoke test. Brings up a real nostrnests stack (auth
 * sidecar + MoQ relay + strfry) via Docker Compose, then exercises the
 * `/auth` endpoint with a hand-rolled NIP-98 request that matches what
 * the server actually expects.
 *
 * Doesn't yet use [com.vitorpamplona.nestsclient.NestsClient] —
 * `OkHttpNestsClient`'s wire shape (GET `<base>/<roomId>` with no body,
 * expecting `{endpoint, token}` back) doesn't match the real server
 * (POST `<base>/auth` with `{namespace, publish}` body, returning just
 * `{token}`). Phase 2 of this audit refactors the production client to
 * match; until then this test documents the divergence on the wire.
 *
 * Skipped by default — set `-DnestsInterop=true` to enable.
 */
class NostrNestsAuthInteropTest {
    @Test
    fun auth_endpoint_returns_jwt_for_a_well_formed_nip98_request() =
        runBlocking {
            NostrNestsHarness.assumeNestsInterop()
            val harness = harnessOrNull ?: return@runBlocking

            val keys = KeyPair()
            val signer = NostrSignerInternal(keys)
            val pubkeyHex = signer.pubKey

            val authUrl = "${harness.authBaseUrl}/auth"
            val roomId = "interop-${System.currentTimeMillis()}"
            val namespace = "nests/30312:$pubkeyHex:$roomId"
            val body = """{"namespace":"$namespace","publish":true}"""

            val authHeader =
                NestsAuth.header(
                    signer = signer,
                    url = authUrl,
                    method = "POST",
                    payload = body.toByteArray(),
                )

            val request =
                Request
                    .Builder()
                    .url(authUrl)
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .header("Authorization", authHeader)
                    .build()

            val (status, responseBody) =
                http.newCall(request).execute().use { response ->
                    response.code to (response.body.string())
                }

            assertEquals(
                200,
                status,
                "POST /auth should return 200 with a valid NIP-98 + namespace; got $status: $responseBody",
            )
            // moq-auth's response is `{"token":"<jwt>"}` per its index.ts.
            assertTrue(
                responseBody.contains("\"token\":\""),
                "Expected JWT in `token` field, got: $responseBody",
            )
        }

    companion object {
        private val http = OkHttpClient()
        private var harnessOrNull: NostrNestsHarness? = null

        @BeforeClass
        @JvmStatic
        fun setUpHarness() {
            if (NostrNestsHarness.isEnabled()) {
                harnessOrNull = NostrNestsHarness.start()
            }
        }

        @AfterClass
        @JvmStatic
        fun tearDownHarness() {
            harnessOrNull?.close()
            harnessOrNull = null
        }
    }
}
