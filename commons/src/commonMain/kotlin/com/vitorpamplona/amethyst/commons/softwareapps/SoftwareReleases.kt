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
package com.vitorpamplona.amethyst.commons.softwareapps

import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.application.SoftwareApplicationEvent
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.release.isNip82SoftwareRelease
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.release.tags.AppIdTag
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.release.tags.ReleaseChannel
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.shared.Nip82VersionComparator
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip51Lists.releaseArtifactSet.ReleaseArtifactSetEvent

/**
 * Which NIP-82 releases (kind 30063) belong to an application (kind 32267), and in what order.
 *
 * A release names its app with the required `a` tag, whose pubkey is the app's publisher and
 * need not be the release's signer. Anyone can sign a release pointing at any app, so a release
 * only counts for an app when its signer is the app's publisher or a pubkey the publisher lists
 * as a `p` tag on the app (NIP-82 Appendix E). Without that, a stranger could publish a newer
 * "version" with their own download links and it would surface as the app's latest release.
 */
object SoftwareReleases {
    /** The app's publisher, then the pubkeys it credits with `p` tags. */
    fun trustedSigners(app: SoftwareApplicationEvent): List<HexKey> = (listOf(app.pubKey) + app.tags.mapNotNull(PTag::parseKey)).distinct()

    /** True when [release] is a NIP-82 release of [app] signed by one of its [trustedSigners]. */
    fun isReleaseOf(
        release: ReleaseArtifactSetEvent,
        app: SoftwareApplicationEvent,
    ): Boolean {
        if (!release.isNip82SoftwareRelease()) return false
        if (release.appAddress()?.toValue() != app.addressTag()) return false
        return release.pubKey == app.pubKey || release.pubKey in trustedSigners(app)
    }

    /**
     * The cache/relay filter that can hold [app]'s releases. `i` is required on every release and
     * equals the app's `d`; [isReleaseOf] then checks the `a` address and the signer.
     */
    fun filter(app: SoftwareApplicationEvent) =
        Filter(
            kinds = listOf(ReleaseArtifactSetEvent.KIND),
            authors = trustedSigners(app),
            tags = mapOf(AppIdTag.TAG_NAME to listOf(app.appId())),
        )

    /** Highest version first (NIP-82 Appendix D); the newer event breaks ties. */
    val NewestFirst: Comparator<ReleaseArtifactSetEvent> =
        Comparator { a, b ->
            val byVersion = Nip82VersionComparator.compare(b.version().orEmpty(), a.version().orEmpty())
            if (byVersion != 0) byVersion else b.createdAt.compareTo(a.createdAt)
        }

    /** [releases] of one app, newest version first. */
    fun sorted(releases: Collection<ReleaseArtifactSetEvent>): List<ReleaseArtifactSetEvent> = releases.sortedWith(NewestFirst)

    /**
     * The release to offer as current: the highest `main` version, since `main` is the default
     * channel and a nightly should not replace it. Falls back to the highest of any channel for
     * apps that publish no `main` release.
     */
    fun latest(releases: Collection<ReleaseArtifactSetEvent>): ReleaseArtifactSetEvent? =
        releases.filter { isMainChannel(it.channel()) }.minWithOrNull(NewestFirst)
            ?: releases.minWithOrNull(NewestFirst)

    /** A missing channel is treated as `main`, the spec's default. */
    fun isMainChannel(channel: String?) = channel == null || channel.equals(ReleaseChannel.MAIN, ignoreCase = true)
}
