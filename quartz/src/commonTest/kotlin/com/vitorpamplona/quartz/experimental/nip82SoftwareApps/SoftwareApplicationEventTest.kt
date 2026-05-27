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
package com.vitorpamplona.quartz.experimental.nip82SoftwareApps

import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.application.SoftwareApplicationEvent
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.application.icon
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.application.images
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.application.license
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.application.platform
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.application.repository
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.application.summary
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.application.url
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.asset.SoftwareAssetEvent
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.asset.apkCertificateHash
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.asset.platform
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.asset.versionCode
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.release.SoftwareReleaseEvent
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.release.asSoftwareRelease
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.release.isNip82SoftwareRelease
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.shared.Platform
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SoftwareApplicationEventTest {
    @Test
    fun buildApplicationEvent_setsRequiredAndOptionalTags() {
        val tmpl =
            SoftwareApplicationEvent.build(
                appId = "com.example.app",
                name = "Example",
                description = "Demo app",
            ) {
                summary("A short summary")
                icon("https://cdn.example.com/icon.png")
                url("https://example.com")
                repository("https://github.com/example/app")
                license("MIT")
                platform(Platform.ANDROID_ARM64_V8A)
                hashtag("nostr")
            }

        assertEquals(SoftwareApplicationEvent.KIND, tmpl.kind)
        assertEquals("Demo app", tmpl.content)

        // Verify some key tags exist
        val tagsByName = tmpl.tags.associate { it[0] to it.drop(1) }
        assertEquals("com.example.app", tagsByName["d"]?.firstOrNull())
        assertEquals("Example", tagsByName["name"]?.firstOrNull())
        assertEquals("A short summary", tagsByName["summary"]?.firstOrNull())
        assertEquals("MIT", tagsByName["license"]?.firstOrNull())
        assertEquals(Platform.ANDROID_ARM64_V8A, tagsByName["f"]?.firstOrNull())
        assertEquals("https://github.com/example/app", tagsByName["repository"]?.firstOrNull())
    }

    @Test
    fun buildAssetEvent_setsRequiredTags() {
        val tmpl =
            SoftwareAssetEvent.build(
                appId = "com.example.app",
                mimeType = "application/vnd.android.package-archive",
                hash = "deadbeef".repeat(8),
                version = "1.2.3",
                assetUrl = "https://example.com/app.apk",
                sizeInBytes = 1024,
            ) {
                versionCode(42L)
                platform(Platform.ANDROID_ARM64_V8A)
                apkCertificateHash("abc123".repeat(10).take(64))
            }

        assertEquals(SoftwareAssetEvent.KIND, tmpl.kind)

        val tagsByName = tmpl.tags.associate { it[0] to it.drop(1) }
        assertEquals("com.example.app", tagsByName["i"]?.firstOrNull())
        assertEquals("1.2.3", tagsByName["version"]?.firstOrNull())
        assertEquals("42", tagsByName["version_code"]?.firstOrNull())
        assertEquals("application/vnd.android.package-archive", tagsByName["m"]?.firstOrNull())
    }

    @Test
    fun isNip82SoftwareRelease_distinguishesFromNip51ReleaseArtifactSet() {
        val nip82Release =
            Event(
                id = "0".repeat(64),
                pubKey = "1".repeat(64),
                createdAt = 0,
                kind = SoftwareReleaseEvent.KIND,
                tags =
                    arrayOf(
                        arrayOf("d", "com.example.app@1.0.0"),
                        arrayOf("i", "com.example.app"),
                        arrayOf("version", "1.0.0"),
                        arrayOf("c", "main"),
                    ),
                content = "",
                sig = "",
            )
        assertTrue(nip82Release.isNip82SoftwareRelease())

        // NIP-51 release artifact set: same kind, no `i`/`version` tags
        val nip51Set =
            Event(
                id = "0".repeat(64),
                pubKey = "1".repeat(64),
                createdAt = 0,
                kind = SoftwareReleaseEvent.KIND,
                tags = arrayOf(arrayOf("d", "some-uuid"), arrayOf("title", "My Release")),
                content = "",
                sig = "",
            )
        assertFalse(nip51Set.isNip82SoftwareRelease())
    }

    @Test
    fun asSoftwareRelease_exposesParsedFields() {
        val event =
            Event(
                id = "0".repeat(64),
                pubKey = "1".repeat(64),
                createdAt = 0,
                kind = SoftwareReleaseEvent.KIND,
                tags =
                    arrayOf(
                        arrayOf("d", "com.example.app@1.0.0"),
                        arrayOf("i", "com.example.app"),
                        arrayOf("version", "1.0.0"),
                        arrayOf("c", "main"),
                    ),
                content = "Initial release",
                sig = "",
            )
        val release = event.asSoftwareRelease()
        assertEquals("com.example.app", release.appId())
        assertEquals("1.0.0", release.version())
        assertEquals("main", release.channel())
    }

    @Test
    fun parses_realWorldAmethystEvent() {
        // Real NIP-82 event published for Amethyst — used as a regression fixture so renderer
        // changes are caught against a known-good event seen on the wire.
        val event =
            SoftwareApplicationEvent(
                id = "1c59e61478a39a963a6e513b8483b2d429ce1d9d98d1c7dfcd46b6a03dbfc6ea",
                pubKey = "aa9047325603dacd4f8142093567973566de3b1e20a89557b728c3be4c6a844b",
                createdAt = 1778966309L,
                tags =
                    arrayOf(
                        arrayOf("d", "com.vitorpamplona.amethyst"),
                        arrayOf("name", "Amethyst"),
                        arrayOf("summary", "The all-in-one Nostr client"),
                        arrayOf("icon", "https://cdn.zapstore.dev/8064b9cb04395b8039390c51b13095b54f1958fcfe7a2515f6ff202a7567d049"),
                        arrayOf("image", "https://cdn.zapstore.dev/a1dfb3c5fada19c39c85049d6cd97fbf5813feca62b52839a269a2c459c726d2"),
                        arrayOf("image", "https://cdn.zapstore.dev/ff224b4887cba187238bbf10428523006374dea6692eb6f1dd7256aeff322a78"),
                        arrayOf("image", "https://cdn.zapstore.dev/9b088b467b2f217f0b3eee6f12afa907cd3fce54b63766ff93e03fb1f891e8b5"),
                        arrayOf("image", "https://cdn.zapstore.dev/d4580b70f4e741be2d95e6f51c608aac133db77868e8060c2d12dd6350168320"),
                        arrayOf("t", "social-network"),
                        arrayOf("t", "nostr"),
                        arrayOf("url", "https://amethyst.social/"),
                        arrayOf("repository", "https://github.com/vitorpamplona/amethyst"),
                        arrayOf("f", "android-arm64-v8a"),
                        arrayOf("license", "MIT"),
                        arrayOf("h", "acfeaea6e51420e8068fac446ca9d17d7a9ef6a5d20d93894e50fee3d4902a84"),
                    ),
                content = "A privacy-focused Nostr client for Android. Built-in TOR support, the most configurable relay system,\nencrypted messaging, zaps, marketplaces, live streams and complete data sovereignty.\n",
                sig = "c209c6d97a7b94acc827755e615f729ff8c9bc6c9eacd9c92e35685b5a89ba95f24cbbc7f5696683bc19f813cd7443c60900acf13ba6b621834e804b1079d130",
            )

        assertEquals("com.vitorpamplona.amethyst", event.appId())
        assertEquals("Amethyst", event.name())
        assertEquals("The all-in-one Nostr client", event.summary())
        assertEquals("https://cdn.zapstore.dev/8064b9cb04395b8039390c51b13095b54f1958fcfe7a2515f6ff202a7567d049", event.icon())
        assertEquals(4, event.images().size)
        assertEquals(listOf("social-network", "nostr"), event.topics())
        assertEquals("https://amethyst.social/", event.url())
        assertEquals("https://github.com/vitorpamplona/amethyst", event.repository())
        assertEquals(listOf(Platform.ANDROID_ARM64_V8A), event.platforms())
        assertEquals("MIT", event.license())
    }

    @Test
    fun applicationParses_imagesAndPlatforms() {
        val tmpl =
            SoftwareApplicationEvent.build(
                appId = "com.example.app",
                name = "Example",
            ) {
                images(listOf("https://example.com/a.png", "https://example.com/b.png"))
                platform(Platform.LINUX_X86_64)
                platform(Platform.DARWIN_ARM64)
            }
        val event =
            SoftwareApplicationEvent(
                id = "0".repeat(64),
                pubKey = "1".repeat(64),
                createdAt = 0,
                tags = tmpl.tags,
                content = tmpl.content,
                sig = "",
            )
        assertEquals(2, event.images().size)
        assertEquals(2, event.platforms().size)
        assertNotNull(event.platforms().firstOrNull { it == Platform.LINUX_X86_64 })
    }
}
