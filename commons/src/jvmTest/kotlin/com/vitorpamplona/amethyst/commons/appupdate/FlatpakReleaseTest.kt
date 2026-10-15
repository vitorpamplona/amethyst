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
package com.vitorpamplona.amethyst.commons.appupdate

import com.sun.net.httpserver.HttpServer
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import kotlinx.coroutines.test.runTest
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Orchard's side of `amy releases publish`. The fixture is the manifest of Amethyst 1.19.0's x86_64
 * image on ghcr, which orchard-publish built and Orchard installs; the expected tags are the events
 * orchard-publish signed for it on relay.orchard.rocks.
 */
class FlatpakReleaseTest {
    private val manifest = requireNotNull(javaClass.getResource("/appupdate/orchard-manifest-1.19.0-x86_64.json")).readBytes()
    private val digest = "sha256:693bb6f75e3abe032e026acd01413fe68f084c68bb7ded90a2bfe7c45ed131ae"
    private val repository = "ghcr.io/vitorpamplona/amethyst-flatpak"

    @Test
    fun theFixtureIsTheLiveImage() {
        assertEquals(digest, "sha256:" + MessageDigest.getInstance("SHA-256").digest(manifest).joinToString("") { "%02x".format(it) })
    }

    @Test
    fun readsAndChecksTheImage() {
        val image = OciRegistry.checkedImage(manifest.decodeToString(), digest, repository, "linux-x86_64", "1.19.0")
        assertEquals("org.freedesktop.Platform/x86_64/26.08", image.runtime)
        assertEquals("docker://$repository@$digest", image.location)

        assertFailsWith<ReleaseFilesException> { OciRegistry.checkedImage(manifest.decodeToString(), digest, repository, "linux-x86_64", "1.20.0") }
        assertFailsWith<ReleaseFilesException> { OciRegistry.checkedImage(manifest.decodeToString(), digest, repository, "linux-aarch64", "1.19.0") }
    }

    /** Tag for tag, in order, what orchard-publish signed for 1.19.0 x86_64. */
    @Test
    fun theEventsAreOrchardPublishs() {
        val signer = NostrSignerSync()
        val image = OciRegistry.checkedImage(manifest.decodeToString(), digest, repository, "linux-x86_64", "1.19.0")
        val asset = signer.sign(FlatpakReleaseEvents.asset(image))
        assertEquals(
            listOf(
                listOf("i", "com.vitorpamplona.amethyst.Amethyst"),
                listOf("x", digest),
                listOf("version", "1.19.0"),
                listOf("f", "linux-x86_64"),
                listOf("oci", "docker://ghcr.io/vitorpamplona/amethyst-flatpak@$digest"),
                listOf("runtime", "org.freedesktop.Platform/x86_64/26.08"),
            ),
            asset.tags.map { it.toList() },
        )
        assertEquals("", asset.content)

        val release = signer.sign(FlatpakReleaseEvents.release(image, asset, "Release notes: https://github.com/vitorpamplona/amethyst/releases"))
        assertEquals(
            listOf(
                listOf("d", "com.vitorpamplona.amethyst.Amethyst@1.19.0@linux-x86_64"),
                listOf("i", "com.vitorpamplona.amethyst.Amethyst"),
                listOf("version", "1.19.0"),
                listOf("f", "linux-x86_64"),
                listOf("c", "stable"),
                listOf("e", asset.id),
            ),
            release.tags.map { it.toList() },
        )

        val config =
            OrchardConfig.fromMap(
                mapOf(
                    "app" to
                        mapOf(
                            "id" to "com.vitorpamplona.amethyst.Amethyst",
                            "name" to "Amethyst",
                            "summary" to "Join the social network you control",
                            "description-file" to "description.md",
                            "icon" to "https://example.com/icon.png",
                            "categories" to listOf("Network", "InstantMessaging"),
                            "license" to "MIT",
                            "repository" to "https://github.com/vitorpamplona/amethyst",
                            "homepage" to "https://amethyst.social",
                        ),
                    "release" to mapOf("platforms" to listOf("linux-x86_64", "linux-aarch64"), "notes" to "notes"),
                    "publish" to mapOf("relays" to listOf("wss://relay.orchard.rocks", "wss://nos.lol")),
                ),
            ) { if (it == "description.md") "Amethyst is a client for Nostr." else null }
        assertEquals(listOf("wss://relay.orchard.rocks", "wss://nos.lol"), config.relays)

        val listing = signer.sign(FlatpakReleaseEvents.listing(config))
        assertEquals("Amethyst is a client for Nostr.", listing.content)
        assertEquals(
            listOf(
                listOf("d", "com.vitorpamplona.amethyst.Amethyst"),
                listOf("name", "Amethyst"),
                listOf("summary", "Join the social network you control"),
                listOf("icon", "https://example.com/icon.png"),
                listOf("license", "MIT"),
                listOf("repository", "https://github.com/vitorpamplona/amethyst"),
                listOf("h", "https://amethyst.social"),
                listOf("t", "Network"),
                listOf("t", "InstantMessaging"),
                listOf("f", "linux-x86_64"),
                listOf("f", "linux-aarch64"),
            ),
            listing.tags.map { it.toList() },
        )
    }

    @Test
    fun aPrereleaseIsOnTheBetaChannel() {
        assertEquals("stable", FlatpakRelease.channelFor("1.20.0"))
        assertEquals("beta", FlatpakRelease.channelFor("1.20.0-rc1"))
    }

    @Test
    fun readsBundleNames() {
        assertEquals("linux-x86_64", FlatpakRelease.platformOfBundle("amethyst-desktop-1.20.0-linux-x64.flatpak", "1.20.0"))
        assertEquals("linux-aarch64", FlatpakRelease.platformOfBundle("amethyst-desktop-1.20.0-linux-arm64.flatpak", "1.20.0"))
        assertEquals(null, FlatpakRelease.platformOfBundle("amethyst-desktop-1.20.0-linux-x64.deb", "1.20.0"))
        assertEquals("1.20.0-linux-aarch64", FlatpakRelease.tagFor("1.20.0", "linux-aarch64"))
    }

    /** The registry protocol as ghcr speaks it: a 401 with a Bearer challenge, a token, then the manifest. */
    @Test
    fun fetchesThroughATokenChallengeAndChecksTheBytes() =
        runTest {
            var served = manifest
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            val base = "127.0.0.1:${server.address.port}"
            server.createContext("/token") { exchange ->
                val body = """{"token":"abc"}""".toByteArray()
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            server.createContext("/v2/") { exchange ->
                if (exchange.requestHeaders.getFirst("Authorization") != "Bearer abc") {
                    exchange.responseHeaders.add("WWW-Authenticate", """Bearer realm="http://$base/token",service="test",scope="repository:x:pull"""")
                    exchange.sendResponseHeaders(401, -1)
                    exchange.close()
                    return@createContext
                }
                exchange.responseHeaders.add("Docker-Content-Digest", digest)
                exchange.sendResponseHeaders(200, served.size.toLong())
                exchange.responseBody.use { it.write(served) }
            }
            server.start()
            try {
                val registry = OciRegistry(scheme = "http")
                val image = registry.flatpakImage("$base/vitorpamplona/amethyst-flatpak", "1.19.0-linux-x86_64", "linux-x86_64", "1.19.0")
                assertEquals(digest, image.digest)
                assertEquals("docker://$base/vitorpamplona/amethyst-flatpak@$digest", image.location)

                // A registry whose bytes do not hash to the digest it names is not believed.
                served = manifest + ' '.code.toByte()
                assertFailsWith<ReleaseFilesException> { registry.flatpakImage("$base/vitorpamplona/amethyst-flatpak", "1.19.0-linux-x86_64", "linux-x86_64", "1.19.0") }
            } finally {
                server.stop(0)
            }
        }

    /** The bundles in the release folder say which images to read; their tags are the version's. */
    @Test
    fun signsTheImagesOfTheBundlesInTheFolder() =
        runTest {
            val dir = Files.createTempDirectory("flatpak-bundles").toFile()
            val asked = mutableListOf<String>()
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            server.createContext("/v2/") { exchange ->
                asked += exchange.requestURI.path
                if (exchange.requestURI.path.endsWith("/manifests/1.19.0-linux-x86_64")) {
                    exchange.sendResponseHeaders(200, manifest.size.toLong())
                    exchange.responseBody.use { it.write(manifest) }
                } else {
                    exchange.sendResponseHeaders(404, -1)
                    exchange.close()
                }
            }
            server.start()
            try {
                File(dir, "amethyst-desktop-1.19.0-linux-x64.flatpak").writeText("bundle")
                File(dir, "amethyst-desktop-1.19.0-linux-x64.deb").writeText("deb")
                val repo = "127.0.0.1:${server.address.port}/vitorpamplona/amethyst-flatpak"
                val images = ReleaseBundles.flatpakImages(dir, "1.19.0", OciRegistry(scheme = "http"), repo)
                assertEquals(listOf("/v2/vitorpamplona/amethyst-flatpak/manifests/1.19.0-linux-x86_64"), asked)
                assertEquals(listOf(digest), images.map { it.digest })

                val config = OrchardConfig.fromMap(mapOf("app" to mapOf("name" to "Amethyst"), "release" to mapOf("notes" to "from the toml"))) { null }
                val signer = NostrSignerInternal(KeyPair())
                val signed = ReleaseBundles.signFlatpak(signer, "", images, config, withListing = false, relays = listOf("wss://relay.orchard.rocks"))
                assertEquals(null, signed.listing)
                assertEquals("from the toml", signed.releases.single().content)
                assertEquals(
                    listOf(signed.assets.single().id),
                    signed.releases
                        .single()
                        .assets()
                        .map { it.eventId },
                )
                assertEquals(signed.assets + signed.releases, signed.events)

                // The arm64 bundle with no image pushed for it stops the run before anything is signed.
                File(dir, "amethyst-desktop-1.19.0-linux-arm64.flatpak").writeText("bundle")
                assertFailsWith<ReleaseFilesException> { ReleaseBundles.flatpakImages(dir, "1.19.0", OciRegistry(scheme = "http"), repo) }
            } finally {
                server.stop(0)
                dir.deleteRecursively()
            }
        }

    /** Against ghcr itself, when asked (`AMETHYST_LIVE_REGISTRY=1`): the 1.19.0 image by digest. */
    @Test
    fun readsTheLiveImage() =
        runTest {
            if (System.getenv("AMETHYST_LIVE_REGISTRY") != "1") return@runTest
            val image = OciRegistry().flatpakImage(repository, digest, "linux-x86_64", "1.19.0")
            println("live ghcr image: ${image.location} ${image.runtime}")
            assertEquals("org.freedesktop.Platform/x86_64/26.08", image.runtime)
        }
}
