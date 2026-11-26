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
package com.vitorpamplona.amethyst.commons.service.upload

import com.vitorpamplona.amethyst.commons.notices.UploadNotice
import com.vitorpamplona.quartz.nipB7Blossom.BlossomServerUrl
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class KtorBlossomClientTest {
    private val hash = "a".repeat(64)
    private val bytes = byteArrayOf(1, 2, 3, 4)

    private fun descriptor(server: String) = """{"url":"$server/$hash.jpg","sha256":"$hash","size":4,"type":"image/jpeg"}"""

    // What each request sent, read as an engine reads it (which is what drives upload progress).
    private val sentBodies = mutableListOf<ByteArray>()

    private fun client(handler: suspend (HttpRequestData) -> Pair<HttpStatusCode, Pair<String, Map<String, String>>>): Pair<KtorBlossomClient, MutableList<HttpRequestData>> {
        val seen = mutableListOf<HttpRequestData>()
        val engine =
            MockEngine { request ->
                seen += request
                sentBodies += request.body.toByteArray()
                val (status, answer) = handler(request)
                respond(answer.first, status, headersOf(*answer.second.map { it.key to listOf(it.value) }.toTypedArray()))
            }
        val http = HttpClient(engine)
        return KtorBlossomClient { http } to seen
    }

    @Test
    fun uploadPutsTheBytesWithAuthAndTypeAndReadsTheDescriptor() =
        runTest {
            val (blossom, seen) = client { HttpStatusCode.OK to (descriptor("https://b.example") to emptyMap()) }
            var lastSent = 0L
            val result = blossom.upload(bytes, "image/jpeg", "https://b.example/", "Nostr abc") { sent, _ -> lastSent = sent }

            assertEquals("https://b.example/$hash.jpg", result.url)
            assertEquals(hash, result.sha256)
            val put = seen.single()
            assertEquals(HttpMethod.Put, put.method)
            assertEquals(BlossomServerUrl.upload("https://b.example/"), put.url.toString())
            assertEquals("Nostr abc", put.headers[HttpHeaders.Authorization])
            assertEquals("image/jpeg", put.body.contentType.toString())
            assertContentEquals(bytes, sentBodies.single())
            assertEquals(4L, lastSent)
        }

    @Test
    fun paymentRequiredIsTyped() =
        runTest {
            val (blossom, _) = client { HttpStatusCode.PaymentRequired to ("" to mapOf(BlossomServerUrl.X_LIGHTNING_HEADER to "lnbc1")) }
            val e = assertFailsWith<BlossomPaymentException> { blossom.upload(bytes, "image/jpeg", "https://b.example", null) }
            assertEquals("lnbc1", e.payment.lightning)
        }

    @Test
    fun refusalCarriesTheServerReason() =
        runTest {
            val (blossom, _) = client { HttpStatusCode.UnsupportedMediaType to ("" to mapOf(BlossomServerUrl.REASON_HEADER to "no octet streams")) }
            val e = assertFailsWith<RuntimeException> { blossom.upload(bytes, "application/octet-stream", "https://b.example", null) }
            assertTrue(e.message!!.contains("no octet streams"), e.message)
        }

    @Test
    fun aRefusedBlobTheServerAlreadyHoldsCountsAsUploaded() =
        runTest {
            val (blossom, seen) =
                client { request ->
                    if (request.method == HttpMethod.Head) HttpStatusCode.OK to ("" to emptyMap()) else HttpStatusCode.Conflict to ("" to emptyMap())
                }
            val result =
                blossom.uploadOrHeld(hash, 4, "image/jpeg", "https://b.example") {
                    blossom.upload(bytes, "image/jpeg", "https://b.example", null)
                }
            assertEquals(hash, result.sha256)
            assertEquals(BlossomServerUrl.blob("https://b.example", hash), result.url)
            assertEquals(listOf(HttpMethod.Put, HttpMethod.Head), seen.map { it.method })
        }

    @Test
    fun aRefusalFallsBackToTheNextServerAndSaysSo() =
        runTest {
            val (blossom, seen) =
                client { request ->
                    if (request.url.host == "first.example") {
                        HttpStatusCode.UnsupportedMediaType to ("" to mapOf(BlossomServerUrl.REASON_HEADER to "nope"))
                    } else {
                        HttpStatusCode.OK to (descriptor("https://second.example") to emptyMap())
                    }
                }
            val accepted =
                uploadToFirstAccepting("https://first.example", listOf("https://first.example/", "https://second.example")) { server ->
                    blossom.upload(bytes, "image/jpeg", server, null)
                }
            assertEquals("https://second.example", accepted.server)
            assertEquals(listOf("first.example", "second.example"), seen.map { it.url.host })
            val notice = assertIs<UploadNotice.UploadedToAnotherServer>(accepted.notice("https://first.example"))
            assertEquals("second.example", notice.usedServer)
        }
}
