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
package com.vitorpamplona.amethyst.desktop.service.upload

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BlossomClientTest {
    private fun mockOkHttp(
        responseCode: Int,
        body: String = "",
        headers: Headers = Headers.headersOf(),
    ): OkHttpClient {
        val requestSlot = slot<Request>()
        val mockCall = mockk<Call>()
        val mockClient = mockk<OkHttpClient>()

        every { mockClient.newCall(capture(requestSlot)) } returns mockCall
        every { mockCall.execute() } returns
            Response
                .Builder()
                .request(Request.Builder().url("https://example.com").build())
                .protocol(Protocol.HTTP_1_1)
                .code(responseCode)
                .message(if (responseCode == 200) "OK" else "Error")
                .headers(headers)
                .body(body.toResponseBody())
                .build()

        return mockClient
    }

    @Test
    fun uploadSuccessReturnsResult() =
        runTest {
            val json =
                """{"url":"https://blossom.example.com/abc123.png","sha256":"abc123","size":1024}"""
            val client = BlossomClient(mockOkHttp(200, json))

            val file = File.createTempFile("test_", ".png")
            file.deleteOnExit()
            file.writeBytes(byteArrayOf(1, 2, 3))

            try {
                val result =
                    client.upload(
                        file = file,
                        contentType = "image/png",
                        serverBaseUrl = "https://blossom.example.com",
                        authHeader = "Nostr abc",
                    )

                assertEquals("https://blossom.example.com/abc123.png", result.url)
                assertEquals("abc123", result.sha256)
                assertEquals(1024L, result.size)
            } finally {
                file.delete()
            }
        }

    @Test
    fun uploadFailureThrowsException() =
        runTest {
            val headers = Headers.headersOf("X-Reason", "File too large")
            val client = BlossomClient(mockOkHttp(413, "", headers))

            val file = File.createTempFile("test_", ".png")
            file.deleteOnExit()
            file.writeBytes(byteArrayOf(1, 2, 3))

            try {
                val ex =
                    assertFailsWith<RuntimeException> {
                        client.upload(
                            file = file,
                            contentType = "image/png",
                            serverBaseUrl = "https://blossom.example.com",
                            authHeader = null,
                        )
                    }
                assertTrue(ex.message!!.contains("File too large"))
            } finally {
                file.delete()
            }
        }

    @Test
    fun uploadFailureUsesStatusCodeWhenNoXReason() =
        runTest {
            val client = BlossomClient(mockOkHttp(500))

            val file = File.createTempFile("test_", ".png")
            file.deleteOnExit()
            file.writeBytes(byteArrayOf(1))

            try {
                val ex =
                    assertFailsWith<RuntimeException> {
                        client.upload(
                            file = file,
                            contentType = "image/png",
                            serverBaseUrl = "https://blossom.example.com",
                            authHeader = null,
                        )
                    }
                assertTrue(ex.message!!.contains("500"))
            } finally {
                file.delete()
            }
        }

    @Test
    fun uploadSendsAuthorizationHeader() =
        runTest {
            val requestSlot = slot<Request>()
            val mockCall = mockk<Call>()
            val mockClient = mockk<OkHttpClient>()

            every { mockClient.newCall(capture(requestSlot)) } returns mockCall
            every { mockCall.execute() } returns
                Response
                    .Builder()
                    .request(Request.Builder().url("https://example.com").build())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("""{"url":"https://example.com/hash"}""".toResponseBody())
                    .build()

            val client = BlossomClient(mockClient)
            val file = File.createTempFile("test_", ".png")
            file.deleteOnExit()
            file.writeBytes(byteArrayOf(1))

            try {
                client.upload(
                    file = file,
                    contentType = "image/png",
                    serverBaseUrl = "https://blossom.example.com",
                    authHeader = "Nostr base64token",
                )

                val sentRequest = requestSlot.captured
                assertEquals("Nostr base64token", sentRequest.header("Authorization"))
                assertEquals("https://blossom.example.com/upload", sentRequest.url.toString())
                assertEquals("PUT", sentRequest.method)
            } finally {
                file.delete()
            }
        }

    @Test
    fun uploadUrlStripsTrailingSlash() =
        runTest {
            val requestSlot = slot<Request>()
            val mockCall = mockk<Call>()
            val mockClient = mockk<OkHttpClient>()

            every { mockClient.newCall(capture(requestSlot)) } returns mockCall
            every { mockCall.execute() } returns
                Response
                    .Builder()
                    .request(Request.Builder().url("https://example.com").build())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("""{"url":"https://example.com/hash"}""".toResponseBody())
                    .build()

            val client = BlossomClient(mockClient)
            val file = File.createTempFile("test_", ".png")
            file.deleteOnExit()
            file.writeBytes(byteArrayOf(1))

            try {
                client.upload(
                    file = file,
                    contentType = "image/png",
                    serverBaseUrl = "https://blossom.example.com/",
                    authHeader = null,
                )

                // Should not have double slash
                assertEquals("https://blossom.example.com/upload", requestSlot.captured.url.toString())
            } finally {
                file.delete()
            }
        }
}
