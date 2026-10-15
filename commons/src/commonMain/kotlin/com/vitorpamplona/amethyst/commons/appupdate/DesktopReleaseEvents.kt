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

import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.application.SoftwareApplicationEvent
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.application.icon
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.application.license
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.application.platforms
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.application.repository
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.application.summary
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.application.url
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.asset.SoftwareAssetEvent
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.asset.filename
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.asset.platform
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.release.tags.ReleaseChannel
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.shared.Platform
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip51Lists.releaseArtifactSet.ReleaseArtifactSetEvent
import com.vitorpamplona.quartz.utils.TimeUtils

/** One release file, hashed and ready to be described by a signed asset event. */
data class HashedDesktopAsset(
    val file: DesktopAssetFile,
    val sha256: String,
    val sizeInBytes: Long,
    val url: String,
)

/**
 * The NIP-82 events of a desktop release: the app listing (kind 32267), one asset per file (kind
 * 3063) and the release that points at them (kind 30063). The release must be built from the
 * *signed* assets, since it names them by id.
 */
object DesktopReleaseEvents {
    const val SUMMARY = "The all-in-one Nostr client"
    const val DESCRIPTION =
        "Amethyst for macOS, Windows and Linux: the same feeds, messages, zaps, communities and live " +
            "streams as on Android, with built-in Tor support and full control of your relays."
    const val ICON = "https://raw.githubusercontent.com/vitorpamplona/amethyst/main/desktopApp/packaging/flatpak/icons/512/com.vitorpamplona.amethyst.Amethyst.png"
    const val REPOSITORY = "https://github.com/vitorpamplona/amethyst"
    const val WEBSITE = "https://amethyst.social"
    const val LICENSE = "MIT"

    val PLATFORMS = listOf(Platform.DARWIN_ARM64, Platform.WINDOWS_X86_64, Platform.WINDOWS_AARCH64, Platform.LINUX_X86_64, Platform.LINUX_AARCH64)

    fun listing(createdAt: Long = TimeUtils.now()) =
        SoftwareApplicationEvent.build(DesktopRelease.APP_ID, DesktopRelease.APP_NAME, DESCRIPTION, createdAt) {
            summary(SUMMARY)
            icon(ICON)
            repository(REPOSITORY)
            url(WEBSITE)
            license(LICENSE)
            platforms(PLATFORMS)
        }

    fun asset(
        asset: HashedDesktopAsset,
        version: String,
        createdAt: Long = TimeUtils.now(),
    ) = SoftwareAssetEvent.build(
        appId = DesktopRelease.APP_ID,
        mimeType = asset.file.packageType.mimeType,
        hash = asset.sha256,
        version = version,
        assetUrl = asset.url,
        sizeInBytes = asset.sizeInBytes.toInt(),
        createdAt = createdAt,
    ) {
        platform(asset.file.platform)
        filename(asset.file.filename)
    }

    private val STABLE_VERSION = Regex("""\d+\.\d+\.\d+""")

    /**
     * `main` for a stable version (`X.Y.Z`, as the release workflow classifies tags), `beta` for
     * anything else: the app only installs from `main`.
     */
    fun channelFor(version: String) = if (STABLE_VERSION.matches(version)) ReleaseChannel.MAIN else ReleaseChannel.BETA

    fun release(
        publisher: HexKey,
        version: String,
        releaseNotes: String,
        signedAssets: List<SoftwareAssetEvent>,
        relayHint: NormalizedRelayUrl?,
        createdAt: Long = TimeUtils.now(),
    ) = ReleaseArtifactSetEvent.buildSoftwareRelease(
        app = ATag(SoftwareApplicationEvent.KIND, publisher, DesktopRelease.APP_ID, relayHint),
        version = version,
        channel = channelFor(version),
        assets = signedAssets.map { EventHintBundle(it, relayHint) },
        releaseNotes = releaseNotes,
        createdAt = createdAt,
    )
}
