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
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.asset.SoftwareAssetEvent
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.asset.filename
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.asset.platform
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.release.isNip82SoftwareRelease
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.shared.Nip82VersionComparator
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.shared.Platform
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip51Lists.releaseArtifactSet.ReleaseArtifactSetEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Nip82ReleaseTest {
    private val armadaPubKey = "781a1527055f74c1f70230f10384609b34548f8ab6a0a6caa74025827f9fdae5"

    /** Armada 0.64.7 as published on relay.ngit.dev. */
    private val armadaRelease =
        ReleaseArtifactSetEvent(
            id = "5f656f984a17a9d0b715dda4e0f41c0bc4eb0077fa0cba094836ae68503cb087",
            pubKey = armadaPubKey,
            createdAt = 1791319381,
            tags =
                arrayOf(
                    arrayOf("a", "32267:$armadaPubKey:buzz.armada.app", "wss://relay.ngit.dev"),
                    arrayOf("i", "buzz.armada.app"),
                    arrayOf("version", "0.64.7"),
                    arrayOf("d", "buzz.armada.app@0.64.7"),
                    arrayOf("c", "main"),
                    arrayOf("e", "75b40938f21d91e9d835fd737da4285f63d25768a01c45f8b4bc9e889b966afd", "wss://relay.ngit.dev"),
                    arrayOf("e", "aab357053ede0d197fcc157a6fc8ca59626391b5886481c887812ffb7a0ab982", "wss://relay.ngit.dev"),
                    arrayOf("f", "android-arm64-v8a"),
                    arrayOf("f", "android-armeabi-v7a"),
                    arrayOf("f", "darwin-arm64"),
                    arrayOf("f", "darwin-x86_64"),
                    arrayOf("f", "linux-x86_64"),
                    arrayOf("f", "windows-x86_64"),
                    arrayOf("commit", "821591a798580629b2187d08b7f282da8a9a8dad"),
                ),
            content = "Armada's Zapstore listing now carries the desktop downloads.\n\n### Changed\n- Zapstore lists Armada's Linux, Windows and macOS downloads",
            sig = "6b5de31583c53276975f52301409220a07ee3a1a565172bc55e4c5be63024d4834398627c53316f66120c1f3dc6bd00a306e41458ccca6b2321b18d0873f6dec",
        )

    @Test
    fun parsesAppPointerPlatformsAndCommit() {
        assertTrue(armadaRelease.isNip82SoftwareRelease())

        val app = armadaRelease.app()!!
        assertEquals(SoftwareApplicationEvent.KIND, app.kind)
        assertEquals(armadaPubKey, app.pubKeyHex)
        assertEquals("buzz.armada.app", app.dTag)
        assertEquals("wss://relay.ngit.dev/", app.relay?.url)

        assertEquals(Address(SoftwareApplicationEvent.KIND, armadaPubKey, "buzz.armada.app"), armadaRelease.appAddress())
        assertEquals(6, armadaRelease.platforms().size)
        assertEquals("821591a798580629b2187d08b7f282da8a9a8dad", armadaRelease.commit())
        assertEquals(2, armadaRelease.assets().size)
    }

    @Test
    fun appAddressPointsToThePublisherNotTheSigner() {
        val publisher = "2".repeat(64)
        val release =
            ReleaseArtifactSetEvent(
                id = "0".repeat(64),
                pubKey = "1".repeat(64),
                createdAt = 0,
                tags =
                    arrayOf(
                        arrayOf("a", "32267:$publisher:com.example.app"),
                        arrayOf("i", "com.example.app"),
                        arrayOf("version", "1.0"),
                    ),
                content = "",
                sig = "",
            )
        assertEquals(Address(SoftwareApplicationEvent.KIND, publisher, "com.example.app"), release.appAddress())
    }

    @Test
    fun appAddressFallsBackToSignerAndAppIdWithoutATag() {
        val release =
            ReleaseArtifactSetEvent(
                id = "0".repeat(64),
                pubKey = "1".repeat(64),
                createdAt = 0,
                tags = arrayOf(arrayOf("i", "com.example.app"), arrayOf("version", "1.0")),
                content = "",
                sig = "",
            )
        assertNull(release.app())
        assertEquals(Address(SoftwareApplicationEvent.KIND, "1".repeat(64), "com.example.app"), release.appAddress())
    }

    @Test
    fun appIgnoresATagsOfOtherKinds() {
        val release =
            ReleaseArtifactSetEvent(
                id = "0".repeat(64),
                pubKey = "1".repeat(64),
                createdAt = 0,
                tags =
                    arrayOf(
                        arrayOf("a", "30617:${"3".repeat(64)}:repo"),
                        arrayOf("i", "com.example.app"),
                        arrayOf("version", "1.0"),
                    ),
                content = "",
                sig = "",
            )
        assertNull(release.app())
    }

    @Test
    fun buildSoftwareReleaseWritesRequiredATagAndPlatformUnion() {
        val publisher = "2".repeat(64)
        val relay = NormalizedRelayUrl("wss://relay.example.com/")
        val app = ATag(SoftwareApplicationEvent.KIND, publisher, "com.example.app", relay)

        val arm = asset("a", Platform.DARWIN_ARM64)
        val intel = asset("b", Platform.DARWIN_X86_64)
        val armAgain = asset("c", Platform.DARWIN_ARM64)

        val template =
            ReleaseArtifactSetEvent.buildSoftwareRelease(
                app = app,
                version = "2.0.0",
                channel = "beta",
                assets = listOf(EventHintBundle(arm), EventHintBundle(intel), EventHintBundle(armAgain)),
                releaseNotes = "notes",
            )

        val aTags = template.tags.filter { it[0] == "a" }
        assertEquals(1, aTags.size)
        assertEquals(listOf("a", "32267:$publisher:com.example.app", "wss://relay.example.com/"), aTags[0].toList())
        assertEquals("com.example.app@2.0.0", template.tags.first { it[0] == "d" }[1])
        assertEquals("com.example.app", template.tags.first { it[0] == "i" }[1])
        assertEquals(listOf(Platform.DARWIN_ARM64, Platform.DARWIN_X86_64), template.tags.filter { it[0] == "f" }.map { it[1] })
        assertEquals(3, template.tags.count { it[0] == "e" })
    }

    @Test
    fun assetFilename() {
        val template =
            SoftwareAssetEvent.build("com.example.app", "application/zip", "0".repeat(64), "1.0") {
                filename("Example-1.0-mac-arm64.zip")
            }
        assertEquals("Example-1.0-mac-arm64.zip", template.tags.first { it[0] == "filename" }[1])
    }

    @Test
    fun versionOrderMatchesTheSpecExample() {
        val expected = listOf("1.0.0-dev", "1.0.0-alpha", "1.0.0-alpha.2", "1.0.0-beta", "1.0.0-beta.2", "1.0.0-rc1", "1.0.0", "1.0.1")
        assertEquals(expected, expected.reversed().sortedWith(Nip82VersionComparator))
        assertEquals(expected, expected.shuffled().sortedWith(Nip82VersionComparator))
    }

    @Test
    fun versionOrderMissingSegmentsAndIntegers() {
        val cmp = Nip82VersionComparator
        assertTrue(cmp.compare("1.0", "1.0.0") < 0)
        assertTrue(cmp.compare("1.0.0", "1.0.0.1") < 0)
        assertTrue(cmp.compare("9", "10") < 0)
        assertTrue(cmp.compare("119", "121") < 0)
        assertTrue(cmp.compare("0.9.10", "0.10.0") < 0)
        assertTrue(cmp.compare("0.64.7", "0.64.10") < 0)
        assertTrue(cmp.compare("v1.2.3", "1.2.4") < 0)
        assertEquals(0, cmp.compare("v1.2.3", "1.2.3"))
        assertEquals(0, cmp.compare("1.2.3+build.5", "1.2.3"))
        assertTrue(cmp.compare("1.0.0-rc2", "1.0.0-rc10") < 0)
        assertTrue(cmp.compare("2.0.0-beta", "1.9.9") > 0)
        assertTrue(cmp.compare("12345678901234567890", "12345678901234567891") < 0)
    }

    private fun asset(
        idChar: String,
        platform: String,
    ): SoftwareAssetEvent {
        val template =
            SoftwareAssetEvent.build("com.example.app", "application/zip", "0".repeat(64), "2.0.0") {
                platform(platform)
            }
        return SoftwareAssetEvent(idChar.repeat(64), "2".repeat(64), 0, template.tags, "", "")
    }
}
