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
package com.vitorpamplona.amethyst.commons.napplet.blobs

import com.sun.net.httpserver.HttpServer
import com.vitorpamplona.crypto.sha2.sha256
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip5aStaticWebsites.resolver.StaticSiteResolution
import com.vitorpamplona.quartz.nip5aStaticWebsites.tags.PathTag
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NappletBlobStoreTest {
    private val served = ConcurrentHashMap<String, ByteArray>()
    private val requests = AtomicInteger()

    // Serves a blob by hash; "chunked" sends it with no Content-Length, as some servers do.
    private val server =
        HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).apply {
            createContext("/") { exchange ->
                requests.incrementAndGet()
                val name = exchange.requestURI.path.trimStart('/')
                val body = served[name]
                if (body == null) {
                    exchange.sendResponseHeaders(404, -1)
                } else {
                    exchange.sendResponseHeaders(200, if (name.startsWith("chunked")) 0 else body.size.toLong())
                    exchange.responseBody.use { it.write(body) }
                }
                exchange.close()
            }
            start()
        }
    private val serverUrl = "http://127.0.0.1:${server.address.port}"
    private val dir = createTempDirectory("blob-store-test").toFile()
    private val cache = NappletBlobCache(dir)
    private val store = NappletBlobStore(cache, OkHttpClient())

    @AfterTest
    fun tearDown() {
        server.stop(0)
        dir.deleteRecursively()
    }

    private fun hashOf(bytes: ByteArray) = sha256(bytes).toHexKey()

    @Test
    fun aMissDownloadsVerifiesAndCaches() =
        runBlocking {
            val page = "<p>hi</p>".encodeToByteArray()
            served[hashOf(page)] = page
            val paths = listOf(PathTag("/index.html", hashOf(page)))

            val first = store.resolve("/", paths, listOf(serverUrl))
            assertIs<StaticSiteResolution.Resolved>(first)
            assertContentEquals(page, first.bytes)
            assertEquals(1, requests.get())

            // The second time comes from the cache, without the network.
            val second = store.resolve("/index.html", paths, listOf(serverUrl))
            assertIs<StaticSiteResolution.Resolved>(second)
            assertEquals(NappletBlobStore.CACHE_SERVER, second.server)
            assertEquals(1, requests.get())
        }

    @Test
    fun bytesThatDoNotMatchTheirHashAreNeitherServedNorCached() =
        runBlocking {
            val signed = "<p>signed</p>".encodeToByteArray()
            served[hashOf(signed)] = "<p>swapped</p>".encodeToByteArray()
            val paths = listOf(PathTag("/index.html", hashOf(signed)))

            assertIs<StaticSiteResolution.Unresolvable>(store.resolve("/", paths, listOf(serverUrl)))
            assertNull(cache.get(hashOf(signed)))
        }

    @Test
    fun aBodyWithNoLengthIsStillCapped() {
        val big = ByteArray((NappletBlobHttp.MAX_BLOB_BYTES + 1).toInt())
        served["chunked-big"] = big
        assertNull(NappletBlobHttp.download(OkHttpClient(), "$serverUrl/chunked-big"))

        val small = "fits".encodeToByteArray()
        served["chunked-small"] = small
        assertContentEquals(small, NappletBlobHttp.download(OkHttpClient(), "$serverUrl/chunked-small"))
    }

    @Test
    fun trimmingDropsTheOldestBlobsFirst() {
        val old = ByteArray(1000) { 1 }
        val new = ByteArray(1000) { 2 }
        cache.put(hashOf(old), old)
        cache.put(hashOf(new), new)
        assertTrue(File(dir, hashOf(old)).setLastModified(1_000))

        cache.trimToSize(1500)
        assertNull(cache.get(hashOf(old)))
        assertContentEquals(new, cache.get(hashOf(new)))
    }

    @Test
    fun theHashComesFromTheUrlsLastSegment() {
        val hash = "a".repeat(64)
        assertEquals(hash, NappletBlobStore.blobHashOf("https://blossom.example/$hash"))
        assertEquals(hash, NappletBlobStore.blobHashOf("https://blossom.example/${hash.uppercase()}.wad"))
    }
}
