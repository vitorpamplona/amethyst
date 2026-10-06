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
package com.vitorpamplona.amethyst.commons.ui.note.types

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.softwareapps.SoftwareAssetDownloads
import com.vitorpamplona.amethyst.commons.softwareapps.SoftwareOs
import com.vitorpamplona.amethyst.commons.softwareapps.SoftwarePlatforms
import com.vitorpamplona.amethyst.commons.softwareapps.SoftwareReleases
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.theme.QuoteBorder
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonRow
import com.vitorpamplona.amethyst.commons.ui.theme.subtleBorder
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.application.SoftwareApplicationEvent
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.asset.SoftwareAssetEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip51Lists.releaseArtifactSet.ReleaseArtifactSetEvent
import io.mockk.mockk
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Renders real NIP-82 releases (fetched with amy from relay.zapstore.dev, relay.ngit.dev and
 * others; `resources/nip82/releases.jsonl`) offscreen through the shared release composables, the
 * header, the OS chips and the grouped downloads, into `commonsUI/build/nip82-release/`. The release
 * notes are left out: they go through the rich-text viewer, which needs an account.
 *
 * The device is a 64-bit ARM Android phone.
 */
class SoftwareReleaseRenderTest {
    private val outDir = File("build/nip82-release").apply { mkdirs() }
    private val phone = listOf("android-arm64-v8a", "android-armeabi-v7a")

    private val events =
        javaClass
            .getResource("/nip82/releases.jsonl")!!
            .readText()
            .lines()
            .filter { it.isNotBlank() }
            .map { Event.fromJson(it) }
    private val releases = events.filterIsInstance<ReleaseArtifactSetEvent>()
    private val assets = events.filterIsInstance<SoftwareAssetEvent>().associateBy { it.id }
    private val apps = events.filterIsInstance<SoftwareApplicationEvent>().associateBy { it.addressTag() }

    private fun release(appId: String) = releases.first { it.appId() == appId }

    private fun appOf(release: ReleaseArtifactSetEvent) = release.appAddress()?.toValue()?.let { apps[it] }

    private fun assetsOf(release: ReleaseArtifactSetEvent) = release.assets().mapNotNull { assets[it.eventId] }

    @Test
    fun armadaGroupsEveryOsAndPicksTheApkForThePhone() {
        val armada = release("buzz.armada.app")
        val groups = SoftwareAssetDownloads.group(assetsOf(armada), phone)

        // The APK is the phone's pick, shown on top, so the Android group it would be alone in is dropped.
        assertEquals("application/vnd.android.package-archive", groups.forThisDevice?.mimeType())
        assertEquals(listOf(SoftwareOs.MACOS, SoftwareOs.WINDOWS, SoftwareOs.LINUX), groups.byOs.map { it.first })
        val titles = groups.byOs.flatMap { (os, list) -> list.map { SoftwareAssetDownloads.describe(it, os, armada.version()).title } }
        assertEquals(listOf("ZIP", "ZIP", "EXE · installer", "EXE · portable", "AppImage", "DEB", "Flatpak"), titles)
        assertTrue(SoftwareReleases.canShow(armada, appOf(armada)))
    }

    @Test
    fun checksumsAndReadmesAreTitledByFileName() {
        val provider = release("xyz.privateprovider")
        val titles = assetsOf(provider).map { asset -> SoftwareAssetDownloads.describe(asset, SoftwarePlatforms.osesOf(asset).first(), provider.version()).title }
        assertTrue("APK" in titles)
        assertTrue(titles.none { it.startsWith("application/") || it.startsWith("text/") }, "raw MIME types as titles: $titles")
        assertEquals(titles.size, titles.toSet().size, "two rows read the same: $titles")
    }

    @Test
    fun truncatedFileNamesKeepTheirTypeInTheDetails() {
        val archipelago = release("archipelago")
        val kinds =
            assetsOf(archipelago)
                .map { SoftwareAssetDownloads.describe(it, SoftwarePlatforms.osesOf(it).first(), archipelago.version()) }
                .filter { it.titleIsFileName }
                .map { it.fileKind }
        // archipelago (no extension), then the .iso, .iso.sha256 and .iso.sha256.json siblings.
        assertEquals(listOf(null, "ISO", "SHA256", "JSON"), kinds)
    }

    @Test
    fun aReleaseWithoutFTagsBorrowsTheAppsPlatforms() {
        val pyblockwatch = release("com.kilombino.pyblockwatch")
        assertTrue(pyblockwatch.platforms().isEmpty())
        assertEquals(listOf(SoftwareOs.ANDROID), SoftwareReleases.oses(pyblockwatch, appOf(pyblockwatch)))
    }

    @Test
    fun ngitTarballsAreLabelledTarGz() {
        val ngit = release("ngit")
        val titles = assetsOf(ngit).map { SoftwareAssetDownloads.formatLabel(it) }
        assertTrue(titles.all { it == "TAR.GZ" || it == "ZIP" }, "$titles")
    }

    @Test
    fun render() {
        val nav = mockk<INav>(relaxed = true)
        releases.forEach { release ->
            val app = appOf(release)
            val shownApp = app.takeIf { SoftwareReleases.canShow(release, it) }
            val groups = SoftwareAssetDownloads.group(assetsOf(release), phone)
            val rows = groups.byOs.sumOf { it.second.size } + if (groups.forThisDevice != null) 1 else 0

            val density = 2f
            val widthDp = 840
            val heightDp = 220 + rows * 58 + groups.byOs.size * 30
            val scene =
                ImageComposeScene(
                    width = (widthDp * density).toInt(),
                    height = (heightDp * density).toInt(),
                    density = Density(density),
                ) {
                    ThemeComparisonRow {
                        Column(
                            Modifier
                                .padding(12.dp)
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.subtleBorder, QuoteBorder)
                                .padding(12.dp),
                        ) {
                            ReleaseAppHeader(release, shownApp, release.appAddress().takeIf { shownApp != null }, nav)
                            ReleaseSummaryFooter(release, shownApp)
                            Spacer(Modifier.height(12.dp))
                            Text("— release notes (rich text, not rendered here) —", style = MaterialTheme.typography.labelSmall)
                            Spacer(Modifier.height(12.dp))
                            DownloadsSection(groups, release.version()) { DownloadAction(onClick = {}) }
                        }
                    }
                }
            try {
                var image = scene.render(0)
                repeat(SETTLE_FRAMES) { frame ->
                    Thread.sleep(FRAME_MILLIS)
                    image = scene.render((frame + 1) * FRAME_MILLIS * 1_000_000L)
                }
                val png = image.encodeToData(EncodedImageFormat.PNG) ?: error("could not encode")
                File(outDir, "${release.appId()}.png").writeBytes(png.bytes)

                val pixels = image.peekPixels() ?: error("no pixels")
                val distinct = HashSet<Int>()
                for (y in 0 until image.height step 7) for (x in 0 until image.width step 7) distinct += pixels.getColor(x, y)
                assertTrue(distinct.size > 20, "${release.appId()} rendered almost nothing (${distinct.size} colours)")
            } finally {
                scene.close()
            }
        }
    }

    private companion object {
        const val SETTLE_FRAMES = 6
        const val FRAME_MILLIS = 60L
    }
}
