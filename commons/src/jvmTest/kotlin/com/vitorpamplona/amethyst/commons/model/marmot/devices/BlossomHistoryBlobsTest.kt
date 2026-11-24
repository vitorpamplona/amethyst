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
package com.vitorpamplona.amethyst.commons.model.marmot.devices

import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class BlossomHistoryBlobsTest {
    private val server = ServerSocket(0)
    private val accepted = ArrayList<Socket>()
    private val client = OkHttpClient.Builder().readTimeout(30, TimeUnit.SECONDS).build()
    private val blobs = BlossomHistoryBlobs({ emptyList() }, NostrSignerInternal(KeyPair())) { client }
    private val url = "http://127.0.0.1:${server.localPort}/" + "ab".repeat(32)

    @After
    fun close() {
        accepted.forEach { it.close() }
        server.close()
    }

    /** A server that takes the request, sends [head] and [body] (or nothing), and holds the line. */
    private fun serve(answer: ByteArray? = null) =
        thread(isDaemon = true) {
            val socket = server.accept()
            synchronized(accepted) { accepted.add(socket) }
            socket.getInputStream().read(ByteArray(4096))
            answer?.let { socket.getOutputStream().apply { write(it) }.flush() }
        }

    private fun response(body: ByteArray) = "HTTP/1.1 200 OK\r\nContent-Length: ${body.size}\r\n\r\n".encodeToByteArray() + body

    @Test
    fun aCancelledFetchStopsWithoutWaitingForTheServer() =
        runBlocking {
            serve()
            val started = System.nanoTime()
            val fetch = async(Dispatchers.Default) { blobs.fetch(url, 1024) }
            delay(300)
            fetch.cancel()
            val ended = withTimeoutOrNull(5_000) { fetch.join() }
            val elapsedMs = (System.nanoTime() - started) / 1_000_000
            assertTrue("the cancel ends the fetch now, not at the 30 s read timeout ($elapsedMs ms)", ended != null && elapsedMs < 5_000)
        }

    @Test
    fun aFetchReadsTheBlobUpToItsLimit() =
        runBlocking {
            val bytes = ByteArray(100) { it.toByte() }
            serve(response(bytes))
            assertArrayEquals(bytes, blobs.fetch(url, 100))
            serve(response(bytes))
            assertNull("longer than allowed", blobs.fetch(url, 99))
        }
}
