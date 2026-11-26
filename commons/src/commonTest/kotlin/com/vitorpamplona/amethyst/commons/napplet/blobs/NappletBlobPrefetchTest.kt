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

import com.vitorpamplona.amethyst.commons.util.deleteRecursivelyQuietly
import com.vitorpamplona.amethyst.commons.util.platformFileSystem
import com.vitorpamplona.amethyst.commons.util.platformTempDirectory
import com.vitorpamplona.crypto.sha2.sha256
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip5aStaticWebsites.tags.PathTag
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The shared prefetch ([NappletBlobPrefetch]) over a [NappletHttp], as iOS runs it. */
class NappletBlobPrefetchTest {
    private val fs = platformFileSystem
    private val dir = platformTempDirectory / "blob-prefetch-${Random.nextLong().toULong()}"
    private val cache = NappletBlobCache(dir).forSite("site")

    @BeforeTest
    fun setUp() = NappletBlobPrefetch.resetBudget()

    @AfterTest
    fun tearDown() {
        NappletBlobPrefetch.resetBudget()
        fs.deleteRecursivelyQuietly(dir)
    }

    private class FakeHttp(
        val served: Map<String, ByteArray>,
    ) : NappletHttp {
        val requests = mutableListOf<NappletHttpRequest>()

        override suspend fun get(request: NappletHttpRequest): NappletHttpResponse {
            requests += request
            val body = served[request.url.substringAfterLast('/')] ?: return NappletHttpResponse(404, null, null, tooLarge = false, received = 0)
            if (body.size > request.maxBytes) return NappletHttpResponse(200, null, null, tooLarge = true, received = request.maxBytes + 1)
            return NappletHttpResponse(200, null, body, tooLarge = false, received = body.size.toLong())
        }
    }

    private fun hashOf(bytes: ByteArray) = sha256(bytes).toHexKey()

    @Test
    fun warmsTheCacheWithVerifiedFilesOnly() =
        runTest {
            val page = "<html>ok</html>".encodeToByteArray()
            val junk = "not what the hash says".encodeToByteArray()
            val junkHash = hashOf("the real file".encodeToByteArray())
            val http = FakeHttp(mapOf(hashOf(page) to page, junkHash to junk))
            val paths = listOf(PathTag("/index.html", hashOf(page)), PathTag("/app.js", junkHash))

            assertTrue(NappletBlobPrefetch.prefetchWith(paths, listOf("https://a.example"), cache, "site", http, viaTor = true))

            assertTrue(cache.has(hashOf(page)))
            assertFalse(cache.has(junkHash), "a blob that doesn't match its hash is not kept")
            // A stranger's server: public addresses only, and the route the caller chose.
            assertTrue(http.requests.all { it.publicOnly && it.viaTor && it.maxBytes == NappletBlobPrefetch.MAX_PREFETCH_BLOB_BYTES })
            // Cached now: a second card downloads nothing.
            val before = http.requests.size
            NappletBlobPrefetch.prefetchWith(paths.take(1), listOf("https://a.example"), cache, "site", http, viaTor = true)
            assertEquals(before, http.requests.size)
        }

    @Test
    fun aCardWarmsOnlyTheStartOfASite() =
        runTest {
            val files = List(NappletBlobPrefetch.MAX_FILES_PER_CARD + 5) { "file $it".encodeToByteArray() }
            val http = FakeHttp(files.associateBy { hashOf(it) })
            NappletBlobPrefetch.prefetchWith(files.map { PathTag("/f${files.indexOf(it)}", hashOf(it)) }, listOf("https://a.example"), cache, "site", http, viaTor = false)
            assertEquals(NappletBlobPrefetch.MAX_FILES_PER_CARD, http.requests.size)
        }

    @Test
    fun onlyHttpsServersAndOnlyTheFirstFew() {
        val servers = listOf("http://plain.example", "https://a.example", "ftp://x", "https://", "https:///path", "HTTPS://b.example", "https://c.example", "https://d.example", "https://e.example")
        assertEquals(listOf("https://a.example", "HTTPS://b.example", "https://c.example", "https://d.example"), NappletBlobPrefetch.prefetchServers(servers))
    }
}
