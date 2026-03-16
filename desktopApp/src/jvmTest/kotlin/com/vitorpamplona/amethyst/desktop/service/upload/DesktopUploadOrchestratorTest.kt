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

import com.vitorpamplona.quartz.nipB7Blossom.BlossomUploadResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DesktopUploadOrchestratorTest {
    @BeforeTest
    fun setup() {
        mockkObject(DesktopBlossomAuth)
        coEvery {
            DesktopBlossomAuth.createUploadAuth(any(), any(), any(), any())
        } returns "Nostr fakeAuthToken"
    }

    @AfterTest
    fun teardown() {
        unmockkObject(DesktopBlossomAuth)
    }

    @Test
    fun uploadCallsClientWithCorrectParameters() =
        runTest {
            val mockClient = mockk<DesktopBlossomClient>()
            val fileSlot = slot<File>()
            val contentTypeSlot = slot<String>()
            val urlSlot = slot<String>()

            coEvery {
                mockClient.upload(
                    file = capture(fileSlot),
                    contentType = capture(contentTypeSlot),
                    serverBaseUrl = capture(urlSlot),
                    authHeader = any(),
                )
            } returns
                BlossomUploadResult(
                    url = "https://blossom.example.com/abc123.png",
                    sha256 = "abc123",
                    size = 100,
                )

            val orchestrator = DesktopUploadOrchestrator(mockClient)

            val file = File.createTempFile("test_", ".png")
            file.deleteOnExit()
            val img = java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB)
            javax.imageio.ImageIO.write(img, "png", file)

            val mockSigner = mockk<com.vitorpamplona.quartz.nip01Core.signers.NostrSigner>(relaxed = true)

            try {
                val result =
                    orchestrator.upload(
                        file = file,
                        alt = "test upload",
                        serverBaseUrl = "https://blossom.example.com",
                        signer = mockSigner,
                        stripExif = false,
                    )

                coVerify(exactly = 1) {
                    mockClient.upload(
                        file = any(),
                        contentType = any(),
                        serverBaseUrl = any(),
                        authHeader = any(),
                    )
                }

                assertEquals("https://blossom.example.com", urlSlot.captured)
                assertEquals("image/png", contentTypeSlot.captured)
                assertNotNull(result.metadata)
                assertEquals("image/png", result.metadata.mimeType)
            } finally {
                file.delete()
            }
        }

    @Test
    fun uploadPassesSameFileWhenNoStripExif() =
        runTest {
            val mockClient = mockk<DesktopBlossomClient>()
            val fileSlot = slot<File>()

            coEvery {
                mockClient.upload(
                    file = capture(fileSlot),
                    contentType = any(),
                    serverBaseUrl = any(),
                    authHeader = any(),
                )
            } returns BlossomUploadResult(url = "https://example.com/hash")

            val orchestrator = DesktopUploadOrchestrator(mockClient)

            val file = File.createTempFile("test_", ".txt")
            file.deleteOnExit()
            file.writeText("content")

            val mockSigner = mockk<com.vitorpamplona.quartz.nip01Core.signers.NostrSigner>(relaxed = true)

            try {
                orchestrator.upload(
                    file = file,
                    alt = null,
                    serverBaseUrl = "https://example.com",
                    signer = mockSigner,
                    stripExif = false,
                )

                assertEquals(file.absolutePath, fileSlot.captured.absolutePath)
            } finally {
                file.delete()
            }
        }

    @Test
    fun uploadComputesMetadata() =
        runTest {
            val mockClient = mockk<DesktopBlossomClient>()

            coEvery {
                mockClient.upload(any(), any(), any(), any())
            } returns BlossomUploadResult(url = "https://example.com/hash")

            val orchestrator = DesktopUploadOrchestrator(mockClient)

            val file = File.createTempFile("test_", ".txt")
            file.deleteOnExit()
            file.writeText("hello world")

            val mockSigner = mockk<com.vitorpamplona.quartz.nip01Core.signers.NostrSigner>(relaxed = true)

            try {
                val result =
                    orchestrator.upload(
                        file = file,
                        alt = null,
                        serverBaseUrl = "https://example.com",
                        signer = mockSigner,
                        stripExif = false,
                    )

                assertEquals(11L, result.metadata.size)
                assertTrue(result.metadata.sha256.length == 64)
                assertEquals("application/octet-stream", result.metadata.mimeType)
            } finally {
                file.delete()
            }
        }

    @Test
    fun uploadPassesAuthHeaderToClient() =
        runTest {
            val mockClient = mockk<DesktopBlossomClient>()
            val authSlot = slot<String?>()

            coEvery {
                mockClient.upload(
                    file = any(),
                    contentType = any(),
                    serverBaseUrl = any(),
                    authHeader = captureNullable(authSlot),
                )
            } returns BlossomUploadResult(url = "https://example.com/hash")

            val orchestrator = DesktopUploadOrchestrator(mockClient)

            val file = File.createTempFile("test_", ".txt")
            file.deleteOnExit()
            file.writeText("data")

            val mockSigner = mockk<com.vitorpamplona.quartz.nip01Core.signers.NostrSigner>(relaxed = true)

            try {
                orchestrator.upload(
                    file = file,
                    alt = null,
                    serverBaseUrl = "https://example.com",
                    signer = mockSigner,
                    stripExif = false,
                )

                assertEquals("Nostr fakeAuthToken", authSlot.captured)
            } finally {
                file.delete()
            }
        }
}
