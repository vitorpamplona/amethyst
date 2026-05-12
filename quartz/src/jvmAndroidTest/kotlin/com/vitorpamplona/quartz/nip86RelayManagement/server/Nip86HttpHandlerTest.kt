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

import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip11RelayInfo.Nip11RelayInformation
import com.vitorpamplona.quartz.nip86RelayManagement.rpc.Nip86Method
import com.vitorpamplona.quartz.nip86RelayManagement.rpc.Nip86Request
import com.vitorpamplona.quartz.nip98HttpAuth.HTTPAuthorizationEvent
import com.vitorpamplona.quartz.nip98HttpAuth.Nip98AuthVerifier
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Nip86HttpHandlerTest {
    private val now = 1_000L
    private val verifier = Nip98AuthVerifier(now = { now })
    private val adminSigner = NostrSignerSync(KeyPair())
    private val intruderSigner = NostrSignerSync(KeyPair())
    private val url = "http://relay.example.com/"

    private fun handlerWith(adminInList: Boolean = true): Pair<Nip86HttpHandler, Nip86Server> {
        val server =
            Nip86Server(
                banStore = BanStore(),
                infoHolder =
                    object : Nip86Server.InfoHolder {
                        private var doc = Nip11RelayInformation(name = "fixture")

                        override fun get() = doc

                        override fun set(info: Nip11RelayInformation) {
                            doc = info
                        }
                    },
                allowList = if (adminInList) setOf(adminSigner.pubKey) else emptySet(),
            )
        return Nip86HttpHandler(server, verifier) to server
    }

    private fun signedHeader(
        body: ByteArray,
        signer: NostrSignerSync = adminSigner,
    ): String {
        val template = HTTPAuthorizationEvent.build(url = url, method = "POST", file = body, createdAt = now)
        return signer.sign(template).toAuthToken()
    }

    private val supportedMethodsBody: ByteArray
        get() = JsonMapper.toJson(Nip86Request(method = Nip86Method.SUPPORTED_METHODS)).encodeToByteArray()

    @Test
    fun disabledWhenAllowListIsEmpty() {
        runBlocking {
            val (handler, _) = handlerWith(adminInList = false)
            val body = supportedMethodsBody
            val r = handler.handle(signedHeader(body), url, body)
            assertIs<Nip86HttpHandler.Response.Disabled>(r)
        }
    }

    @Test
    fun payloadTooLargeBeforeAuthCheck() {
        runBlocking {
            val (handler, _) = handlerWith()
            val oversized = ByteArray(handler.maxBodyBytes + 1)
            // No need for a valid signature — size check fires first.
            val r = handler.handle("anything", url, oversized)
            assertIs<Nip86HttpHandler.Response.PayloadTooLarge>(r)
            assertEquals(handler.maxBodyBytes, r.cap)
        }
    }

    @Test
    fun missingAuthHeader() {
        runBlocking {
            val (handler, _) = handlerWith()
            val r = handler.handle(authHeader = null, url = url, body = supportedMethodsBody)
            assertIs<Nip86HttpHandler.Response.MissingAuth>(r)
        }
    }

    @Test
    fun malformedAuthIsBadAuth() {
        runBlocking {
            val (handler, _) = handlerWith()
            val r = handler.handle("Bearer not-a-nostr-token", url, supportedMethodsBody)
            assertIs<Nip86HttpHandler.Response.BadAuth>(r)
            assertTrue(r.reason.contains("Nostr"))
        }
    }

    @Test
    fun verifiedButNotAdminIsRejectedAsNotAdmin() {
        runBlocking {
            val (handler, _) = handlerWith() // admin is adminSigner only
            val body = supportedMethodsBody
            val header = signedHeader(body, signer = intruderSigner)
            val r = handler.handle(header, url, body)
            assertIs<Nip86HttpHandler.Response.NotAdmin>(r)
        }
    }

    @Test
    fun verifiedAdminButBadJsonBodyIsBadRequest() {
        runBlocking {
            val (handler, _) = handlerWith()
            val body = "not valid json {".encodeToByteArray()
            val header = signedHeader(body)
            val r = handler.handle(header, url, body)
            assertIs<Nip86HttpHandler.Response.BadRequest>(r)
        }
    }

    @Test
    fun verifiedAdminWithValidRequestDispatches() {
        runBlocking {
            val (handler, _) = handlerWith()
            val body = supportedMethodsBody
            val header = signedHeader(body)
            val r = handler.handle(header, url, body)
            val ok = assertIs<Nip86HttpHandler.Response.Ok>(r)
            assertEquals(adminSigner.pubKey, ok.pubkey)
            assertEquals(Nip86Method.SUPPORTED_METHODS, ok.request.method)
            assertNull(ok.response.error)
            assertNotNull(ok.response.result)
            // Pre-serialized JSON ready to write to the wire.
            assertTrue(ok.json.contains(Nip86Method.SUPPORTED_METHODS))
        }
    }
}
