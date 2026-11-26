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
package com.vitorpamplona.amethyst.commons.napplet.webxdc

import com.vitorpamplona.amethyst.commons.napplet.HostProfile
import com.vitorpamplona.amethyst.commons.napplet.NappletSiteServer
import com.vitorpamplona.amethyst.commons.napplet.NappletWebContract
import com.vitorpamplona.amethyst.commons.napplet.blobs.NappletHttp
import com.vitorpamplona.amethyst.commons.napplet.blobs.NappletHttpRequest
import com.vitorpamplona.amethyst.commons.napplet.blobs.NappletHttpResponse
import com.vitorpamplona.amethyst.commons.util.deleteRecursivelyQuietly
import com.vitorpamplona.amethyst.commons.util.platformFileSystem
import com.vitorpamplona.amethyst.commons.util.platformTempDirectory
import com.vitorpamplona.crypto.sha2.sha256
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A webxdc launch off the JVM: [OkioWebxdcBundles] downloads through the launch's [NappletHttp],
 * checks the hash, unpacks under the shared rules, and [WebxdcFiles] serves the result, as the JVM's
 * WebxdcFilesTest checks `WebxdcBundles`.
 */
class OkioWebxdcBundlesTest {
    private val fs = platformFileSystem
    private val root = platformTempDirectory / "webxdc-common-${Random.nextLong().toULong()}"

    @AfterTest
    fun tearDown() {
        fs.deleteRecursivelyQuietly(root)
    }

    private class FakeHttp(
        val served: Map<String, ByteArray>,
    ) : NappletHttp {
        val requests = mutableListOf<NappletHttpRequest>()

        override suspend fun get(request: NappletHttpRequest): NappletHttpResponse {
            requests += request
            val body = served[request.url] ?: return NappletHttpResponse(404, null, null, tooLarge = false, received = 0)
            return NappletHttpResponse(200, null, body, tooLarge = false, received = body.size.toLong())
        }
    }

    private val archive = Base64.decode(ZIP)
    private val hash = sha256(archive).toHexKey()

    private fun files(
        http: NappletHttp,
        url: String = "https://files.example/game.xdc",
        expected: String? = hash,
        viaTor: Boolean = true,
    ) = WebxdcFiles(OkioWebxdcBundles(root, http, viaTor), url, expected, emptyList(), WebxdcRuntime.script("npub1me", "Me", isAppSender = false))

    @Test
    fun anArchiveIsFetchedOnTheLaunchsRouteUnpackedAndServed() =
        runTest {
            val http = FakeHttp(mapOf("https://files.example/game.xdc" to archive))
            val files = files(http)
            val page = assertNotNull(files.find("/index.html"))
            assertEquals("<p>xdc home</p>", page.readBytes().decodeToString())
            assertEquals("text/html", page.contentType.substringBefore(';'))
            assertEquals("console.log(\"xdc\")", assertNotNull(files.find("/js/app.js")).readBytes().decodeToString())
            assertContains(assertNotNull(files.find("/webxdc.js")).readBytes().decodeToString(), "npub1me")
            val sent = http.requests.single()
            assertTrue(sent.viaTor)
            assertEquals(WebxdcArchiveRules.MAX_ARCHIVE_BYTES, sent.maxBytes)

            // A second launch finds it unpacked: no download.
            assertNotNull(files(http).find("/index.html"))
            assertEquals(1, http.requests.size)
        }

    @Test
    fun entriesOutsideTheArchiveAndTheMarkerAreNeverServed() =
        runTest {
            val files = files(FakeHttp(mapOf("https://files.example/game.xdc" to archive)))
            assertNotNull(files.find("/index.html"))
            assertNull(files.find("/" + WebxdcArchiveRules.COMPLETE), "the marker is ours, not the archive's")
            // okio's zip reader resolves `../../escaped.txt` inside the archive's own root, so the entry
            // lands inside the app (the JVM's reader skips it): either way nothing leaves the app's folder.
            assertFalse(fs.exists(root.parent!! / "escaped.txt"))
            assertFalse(fs.exists(root / "escaped.txt"))
            assertNull(files.find("/__MACOSX/._index.html"))
            assertNull(files.find("/missing.js"))
        }

    @Test
    fun anArchiveThatIsNotTheOneThePostNamesIsRefused() =
        runTest {
            val files = files(FakeHttp(mapOf("https://files.example/game.xdc" to archive)), expected = "a".repeat(64))
            assertNull(files.find("/index.html"))
            assertFalse(fs.exists(root / "a".repeat(64)))
        }

    @Test
    fun theWebxdcPostureServesNothingOffOrigin() =
        runTest {
            val origin = NappletWebContract.NAPPLET_SCHEME.appOrigin("xdc1", HostProfile.WEBXDC)
            val server =
                NappletSiteServer(
                    blobs = files(FakeHttp(mapOf("https://files.example/game.xdc" to archive))),
                    shellHtml = "<html><head></head><body></body></html>".encodeToByteArray(),
                    shimJs = "",
                    appOrigin = origin,
                    profile = HostProfile.WEBXDC,
                    declaredDomains = emptyList(),
                    origins = NappletWebContract.NAPPLET_SCHEME,
                )
            val page = assertNotNull(server.serve("GET", "$origin/index.html", acceptsHtml = true))
            assertEquals(200, page.status)
            assertEquals(HostProfile.WEBXDC.appCsp, page.headers["Content-Security-Policy"])
            assertEquals(404, server.serve("GET", "https://example.com/track.js", acceptsHtml = false)?.status)
        }

    private companion object {
        // index.html, js/app.js, ../../escaped.txt, __MACOSX/._index.html and a file named like the
        // marker, deflated (Python's zipfile).
        const val ZIP =
            "UEsDBBQAAAAIAFhxel1KN+HPEQAAAA8AAAAKAAAAaW5kZXguaHRtbLMpsKtISVbIyM9NtdEvsAMAUEsDBBQAAAAIAFhxel1uIqYNFAAAABIAAAAJAAAAanMvYXBwLmpzS87PK87PSdXLyU/XUKpISVbSBABQSwMEFAAAAAgAWHF6XX5TBNkHAAAABQAAABEAAAAuLi8uLi9lc2NhcGVkLnR4dCsoz0tNAQBQSwMEFAAAAAgAWHF6XTmc+wYGAAAABAAAABUAAABfX01BQ09TWC8uX2luZGV4Lmh0bWzLKs3LBgBQSwMEFAAAAAgAWHF6XZPTqGkGAAAABAAAABIAAAAuYW1ldGh5c3QtY29tcGxldGXLzcxLBQBQSwECFAMUAAAACABYcXpdSjfhzxEAAAAPAAAACgAAAAAAAAAAAAAAgAEAAAAAaW5kZXguaHRtbFBLAQIUAxQAAAAIAFhxel1uIqYNFAAAABIAAAAJAAAAAAAAAAAAAACAATkAAABqcy9hcHAuanNQSwECFAMUAAAACABYcXpdflME2QcAAAAFAAAAEQAAAAAAAAAAAAAAgAF0AAAALi4vLi4vZXNjYXBlZC50eHRQSwECFAMUAAAACABYcXpdOZz7BgYAAAAEAAAAFQAAAAAAAAAAAAAAgAGqAAAAX19NQUNPU1gvLl9pbmRleC5odG1sUEsBAhQDFAAAAAgAWHF6XZPTqGkGAAAABAAAABIAAAAAAAAAAAAAAIAB4wAAAC5hbWV0aHlzdC1jb21wbGV0ZVBLBQYAAAAABQAFADEBAAAZAQAAAAA="
    }
}
