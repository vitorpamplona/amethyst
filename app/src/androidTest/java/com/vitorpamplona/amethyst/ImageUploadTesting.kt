/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.amethyst

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.service.FileHeader
import com.vitorpamplona.amethyst.service.Nip96MediaServers
import com.vitorpamplona.amethyst.service.Nip96Retriever
import com.vitorpamplona.amethyst.service.Nip96Uploader
import com.vitorpamplona.amethyst.ui.actions.ImageDownloader
import com.vitorpamplona.quartz.crypto.KeyPair
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.fail
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class ImageUploadTesting {
    private suspend fun testBase(server: Nip96MediaServers.ServerName) {
        val serverInfo =
            Nip96Retriever()
                .loadInfo(
                    server.baseUrl,
                )

        val bitmap = Bitmap.createBitmap(200, 300, Bitmap.Config.ARGB_8888)
        for (x in 0 until bitmap.width) {
            for (y in 0 until bitmap.height) {
                bitmap.setPixel(x, y, Color.rgb(Random.nextInt(), Random.nextInt(), Random.nextInt()))
            }
        }
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
        val bytes = baos.toByteArray()
        val inputStream = bytes.inputStream()

        val account = Account(KeyPair())

        val result =
            Nip96Uploader(account)
                .uploadImage(
                    inputStream,
                    bytes.size.toLong(),
                    "image/png",
                    alt = null,
                    sensitiveContent = null,
                    serverInfo,
                    onProgress = {},
                )

        val url = result.tags!!.first { it[0] == "url" }.get(1)
        val size = result.tags!!.firstOrNull { it[0] == "size" }?.get(1)?.ifBlank { null }
        val dim = result.tags!!.firstOrNull { it[0] == "dim" }?.get(1)?.ifBlank { null }
        val hash = result.tags!!.firstOrNull { it[0] == "x" }?.get(1)?.ifBlank { null }
        val contentType = result.tags!!.first { it[0] == "m" }.get(1)
        val ox = result.tags!!.first { it[0] == "ox" }.get(1)

        Assert.assertTrue("${server.name}: Invalid result url", url.startsWith("http"))

        val imageData: ByteArray =
            ImageDownloader().waitAndGetImage(url)
                ?: run {
                    fail("${server.name}: Should not be null")
                    return
                }

        FileHeader.prepare(
            imageData,
            "image/png",
            null,
            onReady = {
                if (dim != null) {
                    // assertEquals("${server.name}: Invalid dimensions", it.dim, dim)
                }
                if (size != null) {
                    // assertEquals("${server.name}: Invalid size", it.size.toString(), size)
                }
                if (hash != null) {
                    assertEquals("${server.name}: Invalid hash", it.hash, hash)
                }
            },
            onError = { fail("${server.name}: It should not fail") },
        )

        // delay(1000)

        // assertTrue(Nip96Uploader(account).delete(ox, contentType, serverInfo))
    }

    @Test
    fun runTestOnDefaultServers() =
        runBlocking {
            Nip96MediaServers.DEFAULT.forEach {
                testBase(it)
            }
        }

    @Test()
    fun testNostrCheck() =
        runBlocking {
            testBase(Nip96MediaServers.ServerName("nostrcheck.me", "https://nostrcheck.me"))
        }

    @Test()
    @Ignore("Not Working anymore")
    fun testNostrage() =
        runBlocking {
            testBase(Nip96MediaServers.ServerName("nostrage", "https://nostrage.com"))
        }

    @Test()
    @Ignore("Not Working anymore")
    fun testSove() =
        runBlocking {
            testBase(Nip96MediaServers.ServerName("sove", "https://sove.rent"))
        }

    @Test()
    fun testNostrBuild() =
        runBlocking {
            testBase(Nip96MediaServers.ServerName("nostr.build", "https://nostr.build"))
        }

    @Test()
    @Ignore("Not Working anymore")
    fun testSovbit() =
        runBlocking {
            testBase(Nip96MediaServers.ServerName("sovbit", "https://files.sovbit.host"))
        }

    @Test()
    fun testVoidCat() =
        runBlocking {
            testBase(Nip96MediaServers.ServerName("void.cat", "https://void.cat"))
        }

    @Test()
    fun testNostrPic() =
        runBlocking {
            testBase(Nip96MediaServers.ServerName("nostpic.com", "https://nostpic.com"))
        }

    @Test()
    @Ignore("Not Working anymore")
    fun testNostrOnch() =
        runBlocking {
            testBase(Nip96MediaServers.ServerName("nostr.onch.services", "https://nostr.onch.services"))
        }
}
