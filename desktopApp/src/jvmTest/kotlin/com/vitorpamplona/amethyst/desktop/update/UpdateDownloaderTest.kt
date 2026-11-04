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
package com.vitorpamplona.amethyst.desktop.update

import com.sun.net.httpserver.HttpServer
import com.vitorpamplona.amethyst.commons.appupdate.DesktopPackage
import com.vitorpamplona.amethyst.commons.appupdate.UpdateAsset
import com.vitorpamplona.amethyst.commons.appupdate.UpdateFailure
import com.vitorpamplona.amethyst.commons.appupdate.UpdateInstallException
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdateDownloaderTest {
    private val payload = Random(7).nextBytes(700_000)
    private val sha256 = MessageDigest.getInstance("SHA-256").digest(payload).joinToString("") { "%02x".format(it) }
    private lateinit var server: HttpServer
    private lateinit var dir: File
    private val client = OkHttpClient()
    private val downloader = UpdateDownloader { client }

    @BeforeTest
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/file") { exchange ->
            exchange.sendResponseHeaders(200, payload.size.toLong())
            exchange.responseBody.use { it.write(payload) }
        }
        server.start()
        dir = Files.createTempDirectory("update-download").toFile()
    }

    @AfterTest
    fun stop() {
        server.stop(0)
        dir.deleteRecursively()
    }

    // The finder only passes https URLs; the downloader itself does not care, which lets this test serve plain http.
    private fun asset(
        hash: String = sha256,
        size: Long? = payload.size.toLong(),
    ) = UpdateAsset("http://127.0.0.1:${server.address.port}/file", hash, size, DesktopPackage.APPIMAGE, "a.AppImage")

    @Test
    fun keepsAFileThatMatches() =
        runTest {
            var lastProgress = 0L
            val file = downloader.download(asset(), File(dir, "a.AppImage")) { read, _ -> lastProgress = read }
            assertContentEquals(payload, file.readBytes())
            assertEquals(payload.size.toLong(), lastProgress)
        }

    @Test
    fun discardsAFileWithAnotherHash() =
        runTest {
            val target = File(dir, "a.AppImage")
            val e = assertFailsWith<UpdateInstallException> { downloader.download(asset(hash = "0".repeat(64)), target) { _, _ -> } }
            assertEquals(UpdateFailure.VERIFICATION_FAILED, e.failure)
            assertFalse(target.exists())
            assertTrue(dir.listFiles().isNullOrEmpty(), "no partial file is left behind")
        }

    @Test
    fun discardsAFileOfAnotherSize() =
        runTest {
            val e = assertFailsWith<UpdateInstallException> { downloader.download(asset(size = 10), File(dir, "a.AppImage")) { _, _ -> } }
            assertEquals(UpdateFailure.VERIFICATION_FAILED, e.failure)
        }

    @Test
    fun reportsANetworkFailure() =
        runTest {
            val missing = UpdateAsset("http://127.0.0.1:${server.address.port}/missing", sha256, null, null, null)
            val e = assertFailsWith<UpdateInstallException> { downloader.download(missing, File(dir, "a")) { _, _ -> } }
            assertEquals(UpdateFailure.DOWNLOAD_FAILED, e.failure)
        }
}
