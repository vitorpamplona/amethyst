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

import com.vitorpamplona.amethyst.commons.napplet.HostProfile
import com.vitorpamplona.amethyst.commons.napplet.NappletSiteServer
import com.vitorpamplona.amethyst.commons.napplet.NappletWebContract
import com.vitorpamplona.amethyst.commons.util.deleteRecursivelyQuietly
import com.vitorpamplona.amethyst.commons.util.platformFileSystem
import com.vitorpamplona.amethyst.commons.util.platformTempDirectory
import com.vitorpamplona.amethyst.commons.util.setLastModifiedMillis
import com.vitorpamplona.crypto.sha2.sha256
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip5aStaticWebsites.resolver.StaticSiteResolution
import com.vitorpamplona.quartz.nip5aStaticWebsites.tags.PathTag
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import okio.buffer
import okio.use
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The blob store and cache on every platform, over a fake [NappletHttp] and the platform's own disk:
 * what the JVM's NappletBlobStoreTest checks through a real server, without one.
 */
class NappletBlobStoreCommonTest {
    private val fs = platformFileSystem
    private val dir = platformTempDirectory / "blob-store-common-${Random.nextLong().toULong()}"
    private val cache = NappletBlobCache(dir)

    @AfterTest
    fun tearDown() {
        fs.deleteRecursivelyQuietly(dir)
    }

    /** Serves [served] by the URL's last segment; anything else is a 404. */
    private class FakeHttp(
        val served: MutableMap<String, ByteArray> = mutableMapOf(),
    ) : NappletHttp {
        val requests = mutableListOf<NappletHttpRequest>()
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun get(request: NappletHttpRequest): NappletHttpResponse {
            requests += request
            gate?.await()
            val body = served[request.url.substringAfterLast('/')] ?: return NappletHttpResponse(404, null, null, tooLarge = false, received = 0)
            if (body.size > request.maxBytes) return NappletHttpResponse(200, null, null, tooLarge = true, received = request.maxBytes + 1)
            return NappletHttpResponse(200, null, body, tooLarge = false, received = body.size.toLong())
        }
    }

    private fun hashOf(bytes: ByteArray) = sha256(bytes).toHexKey()

    @Test
    fun aMissDownloadsVerifiesAndCaches() =
        runTest {
            val page = "<p>hi</p>".encodeToByteArray()
            val http = FakeHttp(mutableMapOf(hashOf(page) to page))
            val store = NappletBlobStore(cache, http, listOf(PathTag("/index.html", hashOf(page))), listOf("https://a.example"), viaTor = true)

            val first = assertIs<StaticSiteResolution.Resolved>(store.resolve("/"))
            assertContentEquals(page, first.bytes)
            val sent = http.requests.single()
            assertEquals("https://a.example/${hashOf(page)}", sent.url)
            assertTrue(sent.viaTor, "the launch's route")
            assertEquals(NappletBlobStore.MAX_BLOB_BYTES, sent.maxBytes)
            assertEquals(NappletBlobStore.BLOB_CALL_TIMEOUT_MS, sent.timeoutMs)

            val second = assertIs<StaticSiteResolution.Resolved>(store.resolve("/index.html"))
            assertEquals(NappletBlobStore.CACHE_SERVER, second.server)
            assertEquals(1, http.requests.size, "the second time comes from the cache")
            assertContentEquals(page, cache.get(hashOf(page)))
        }

    @Test
    fun bytesThatDoNotMatchTheirHashAreNeitherServedNorCached() =
        runTest {
            val signed = "<p>signed</p>".encodeToByteArray()
            val http = FakeHttp(mutableMapOf(hashOf(signed) to "<p>swapped</p>".encodeToByteArray()))
            val store = NappletBlobStore(cache, http, listOf(PathTag("/index.html", hashOf(signed))), listOf("https://a.example"))
            assertIs<StaticSiteResolution.Unresolvable>(store.resolve("/"))
            assertNull(cache.get(hashOf(signed)))
        }

    @Test
    fun aServerThatFailsOrLacksTheFileFallsThroughToTheNext() =
        runTest {
            val page = "<p>third</p>".encodeToByteArray()
            val asked = mutableListOf<String>()
            val http =
                object : NappletHttp {
                    override suspend fun get(request: NappletHttpRequest): NappletHttpResponse {
                        asked += request.url.substringBefore(".example")
                        return when {
                            request.url.startsWith("https://down") -> throw NappletHttpTimeoutException("down")
                            request.url.startsWith("https://has") -> NappletHttpResponse(200, null, page, tooLarge = false, received = page.size.toLong())
                            else -> NappletHttpResponse(404, null, null, tooLarge = false, received = 0)
                        }
                    }
                }
            val store = NappletBlobStore(cache, http, listOf(PathTag("/index.html", hashOf(page))), listOf("https://down.example", "https://empty.example", "https://has.example"))
            assertContentEquals(page, assertNotNull(store.find("/")).readBytes())
            assertEquals(listOf("https://down", "https://empty", "https://has"), asked)
        }

    @Test
    fun aFileOverTheCapIsAMiss() =
        runTest {
            val big = ByteArray((NappletBlobStore.MAX_BLOB_BYTES + 1).toInt())
            val http = FakeHttp(mutableMapOf(hashOf(big) to big))
            val store = NappletBlobStore(cache, http, listOf(PathTag("/big.bin", hashOf(big))), listOf("https://a.example"))
            assertNull(store.find("/big.bin"))
            assertTrue(store.isListed("/big.bin"))
        }

    @Test
    fun concurrentMissesShareOneDownload() =
        runTest {
            val page = "<p>once</p>".encodeToByteArray()
            val http = FakeHttp(mutableMapOf(hashOf(page) to page))
            http.gate = CompletableDeferred()
            val store = NappletBlobStore(cache, http, listOf(PathTag("/index.html", hashOf(page))), listOf("https://a.example"))
            val finds = List(8) { async { store.find("/") } }
            // Let every request reach the store before the one download is allowed to finish.
            while (http.requests.isEmpty()) yield()
            http.gate!!.complete(Unit)
            finds.awaitAll().forEach { assertContentEquals(page, it?.readBytes()) }
            assertEquals(1, http.requests.size, "eight requests, one download")
        }

    @Test
    fun aCachedBlobStreamsFromItsFile() =
        runTest {
            val script = "console.log(1)".encodeToByteArray()
            cache.put(hashOf(script), script)
            val http = FakeHttp()
            val blob = assertNotNull(NappletBlobStore(cache, http, listOf(PathTag("/app.js", hashOf(script))), emptyList()).find("/app.js"))
            assertEquals(script.size.toLong(), blob.length)
            assertContentEquals(script, blob.open().buffer().use { it.readByteArray() })
            assertTrue(http.requests.isEmpty())
        }

    @Test
    fun sitesAndAccountsDoNotShareFiles() =
        runTest {
            val page = "<p>shared?</p>".encodeToByteArray()
            val http = FakeHttp(mutableMapOf(hashOf(page) to page))

            fun site(partition: String) = NappletBlobStore(cache.forSite(partition), http, listOf(PathTag("/index.html", hashOf(page))), listOf("https://a.example"))
            assertNotNull(site(NappletBlobCache.partitionOf("alice", "x", "")).find("/"))
            assertNotNull(site(NappletBlobCache.partitionOf("bob", "x", "")).find("/"))
            assertEquals(2, http.requests.size, "bob's copy is fetched again")
            assertNotNull(site(NappletBlobCache.partitionOf("alice", "x", "")).find("/"))
            assertEquals(2, http.requests.size)
        }

    @Test
    fun trimmingDropsTheLeastRecentlyUsedFirst() {
        val old = ByteArray(1000) { 1 }
        val used = ByteArray(1000) { 2 }
        cache.put(hashOf(old), old)
        cache.put(hashOf(used), used)
        assertTrue(setLastModifiedMillis(dir / hashOf(old), 2_000_000))
        assertTrue(setLastModifiedMillis(dir / hashOf(used), 1_000_000))
        // Written first, but used just now: the other one goes.
        assertNotNull(cache.file(hashOf(used)))
        assertTrue((fs.metadata(dir / hashOf(used)).lastModifiedAtMillis ?: 0) > TimeUtils.nowMillis() - 60_000)
        cache.trimToSize(1500)
        assertNull(cache.get(hashOf(old)))
        assertContentEquals(used, cache.get(hashOf(used)))
    }

    @Test
    fun aNameThatIsNotAHashNeverReachesTheDisk() {
        cache.put("../../escape", "x".encodeToByteArray())
        assertNull(cache.file("../../escape"))
        assertTrue(fs.listOrNull(dir).orEmpty().isEmpty())
    }

    @Test
    fun aSitePageStreamsBehindItsPrelude() =
        runTest {
            val page = "<html><head></head><body>HOME</body></html>".encodeToByteArray()
            cache.put(hashOf(page), page)
            val origin = NappletWebContract.NAPPLET_SCHEME.appOrigin("nsite1")
            val server =
                NappletSiteServer(
                    blobs = NappletBlobStore(cache, FakeHttp(), listOf(PathTag("/index.html", hashOf(page))), emptyList()),
                    shellHtml = "<html><head></head><body>__APP_SANDBOX__ __APP_BOOTSTRAP__</body></html>".encodeToByteArray(),
                    shimJs = "/*shim*/",
                    appOrigin = origin,
                    profile = HostProfile.WEBSITE,
                    declaredDomains = emptyList(),
                    origins = NappletWebContract.NAPPLET_SCHEME,
                )
            val served = assertNotNull(server.serve("GET", "$origin/", acceptsHtml = true))
            val body = served.readBody()
            assertEquals(served.length, body.size.toLong())
            assertTrue(body.decodeToString().endsWith("<html><head></head><body>HOME</body></html>"))
            assertTrue(body.decodeToString().contains("/*shim*/"), "the prelude goes first")
            assertTrue(served.readBody().contentEquals(body), "each open reads from the start")
        }
}
