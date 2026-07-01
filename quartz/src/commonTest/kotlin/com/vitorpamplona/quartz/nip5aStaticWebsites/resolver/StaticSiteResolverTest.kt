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
package com.vitorpamplona.quartz.nip5aStaticWebsites.resolver

import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip5aStaticWebsites.tags.PathTag
import com.vitorpamplona.quartz.utils.sha256.sha256
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class StaticSiteResolverTest {
    private fun bytes(s: String) = s.encodeToByteArray()

    private fun hashOf(s: String) = sha256(bytes(s)).toHexKey()

    @Test
    fun normalizesRootAndDirectoryAndQueryPaths() {
        assertEquals("index.html", normalizeStaticPath(""))
        assertEquals("index.html", normalizeStaticPath("/"))
        assertEquals("app.js", normalizeStaticPath("/app.js"))
        assertEquals("app.js", normalizeStaticPath("./app.js"))
        assertEquals("assets/app.js", normalizeStaticPath("/assets/app.js?v=2#top"))
        assertEquals("docs/index.html", normalizeStaticPath("/docs/"))
    }

    @Test
    fun lookupMatchesRegardlessOfLeadingSlash() {
        val paths =
            listOf(
                PathTag("/index.html", hashOf("home")),
                PathTag("assets/app.js", hashOf("script")),
            )

        assertEquals(hashOf("home"), paths.resolvePath("/")?.hash)
        assertEquals(hashOf("home"), paths.resolvePath("/index.html")?.hash)
        assertEquals(hashOf("script"), paths.resolvePath("assets/app.js")?.hash)
        assertEquals(null, paths.resolvePath("missing.js"))
    }

    @Test
    fun guessesWebContentTypes() {
        assertEquals("text/html; charset=utf-8", guessStaticContentType("index.html"))
        assertEquals("text/javascript; charset=utf-8", guessStaticContentType("app.mjs"))
        assertEquals("application/wasm", guessStaticContentType("core.wasm"))
        assertEquals("application/octet-stream", guessStaticContentType("blob.unknownext"))
    }

    @Test
    fun sniffsBinaryMagicBytesAndIgnoresText() {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0)
        assertEquals("image/png", sniffContentType(png))

        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0)
        assertEquals("image/jpeg", sniffContentType(jpeg))

        val webp = "RIFF".encodeToByteArray() + byteArrayOf(0, 0, 0, 0) + "WEBP".encodeToByteArray()
        assertEquals("image/webp", sniffContentType(webp))

        val wasm = byteArrayOf(0x00, 0x61, 0x73, 0x6D, 1, 0, 0, 0)
        assertEquals("application/wasm", sniffContentType(wasm))

        // Text / markup is never sniffed — HTML detection must stay extension-driven.
        assertEquals(null, sniffContentType("<html>hi</html>".encodeToByteArray()))
        assertEquals(null, sniffContentType(byteArrayOf()))
    }

    @Test
    fun resolveSniffsContentTypeWhenTheExtensionIsUnknown() =
        runTest {
            val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
            val hash = sha256(png).toHexKey()
            val paths = listOf(PathTag("/icon", hash)) // no extension → extension guess is generic

            val resolution = StaticSiteResolver.resolve("/icon", paths, listOf("https://s")) { png }
            assertIs<StaticSiteResolution.Resolved>(resolution)
            assertEquals("image/png", resolution.contentType)
        }

    @Test
    fun verifyAcceptsMatchingAndRejectsTamperedBytes() {
        val good = bytes("<html>napplet</html>")
        val hash = sha256(good).toHexKey()

        assertTrue(StaticSiteResolver.verify(good, hash))
        assertFalse(StaticSiteResolver.verify(bytes("<html>evil</html>"), hash))
    }

    @Test
    fun resolvesFromTheFirstServerThatServesMatchingBytes() =
        runTest {
            val html = "<html>home</html>"
            val paths = listOf(PathTag("/index.html", hashOf(html)))

            val resolution =
                StaticSiteResolver.resolve(
                    requestPath = "/",
                    paths = paths,
                    servers = listOf("https://cdn.example.com", "https://backup.example.com"),
                    fetch = { url -> if (url.startsWith("https://cdn.example.com")) bytes(html) else null },
                )

            val resolved = assertIs<StaticSiteResolution.Resolved>(resolution)
            // Resolved.path echoes the manifest's declared path verbatim, not the normalized request.
            assertEquals("/index.html", resolved.path)
            assertEquals("https://cdn.example.com", resolved.server)
            assertEquals("text/html; charset=utf-8", resolved.contentType)
            assertEquals(html, resolved.bytes.decodeToString())
        }

    @Test
    fun skipsAServerThatTampersWithTheBlobAndFallsThroughToAnHonestOne() =
        runTest {
            val html = "<html>home</html>"
            val paths = listOf(PathTag("/index.html", hashOf(html)))

            var triedMalicious = false
            val resolution =
                StaticSiteResolver.resolve(
                    requestPath = "/",
                    paths = paths,
                    servers = listOf("https://evil.example.com", "https://honest.example.com"),
                    fetch = { url ->
                        if (url.startsWith("https://evil.example.com")) {
                            triedMalicious = true
                            bytes("<html>injected malware</html>")
                        } else {
                            bytes(html)
                        }
                    },
                )

            val resolved = assertIs<StaticSiteResolution.Resolved>(resolution)
            // The tampered server was contacted but its bytes were rejected by hash verification...
            assertTrue(triedMalicious)
            // ...and resolution fell through to the honest server's verified copy.
            assertEquals("https://honest.example.com", resolved.server)
            assertEquals(html, resolved.bytes.decodeToString())
        }

    @Test
    fun reportsUnresolvableWhenEveryServerFailsVerification() =
        runTest {
            val paths = listOf(PathTag("/index.html", hashOf("real")))

            val resolution =
                StaticSiteResolver.resolve(
                    requestPath = "/",
                    paths = paths,
                    servers = listOf("https://a.example.com", "https://b.example.com"),
                    fetch = { _ -> bytes("tampered") },
                )

            assertEquals(StaticSiteResolution.Unresolvable(hashOf("real")), resolution)
        }

    @Test
    fun reportsPathNotInManifestForUndeclaredPaths() =
        runTest {
            val paths = listOf(PathTag("/index.html", hashOf("home")))

            val resolution =
                StaticSiteResolver.resolve(
                    requestPath = "/secret.js",
                    paths = paths,
                    servers = listOf("https://a.example.com"),
                    fetch = { _ -> error("should not fetch for an undeclared path") },
                )

            assertEquals(StaticSiteResolution.PathNotInManifest, resolution)
        }
}
