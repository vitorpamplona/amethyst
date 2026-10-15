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

import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.release.tags.AppIdTag
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.release.tags.AssetTag
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.shared.PlatformTag
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAll
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip51Lists.releaseArtifactSet.ReleaseArtifactSetEvent

/** Somewhere the app learns about new versions. */
interface UpdateSource {
    /** The newest version above [currentVersion] for [target], or null when there is none. */
    suspend fun findUpdate(
        currentVersion: String,
        target: UpdateTarget,
    ): AvailableUpdate?
}

/**
 * The trusted source: NIP-82 releases and assets signed by [DesktopRelease.PUBLISHER], read from
 * [relays] through the app's own relay client (so they take its Tor routing).
 *
 * Releases are asked for by app id and platform (`#i`, `#f`), as Zapstore does; their assets by the
 * ids the chosen releases point to.
 */
class Nip82UpdateSource(
    private val client: INostrClient,
    private val relays: List<String> = DesktopRelease.RELAYS,
    private val finder: Nip82UpdateFinder = Nip82UpdateFinder(),
    private val appId: String = DesktopRelease.APP_ID,
    private val publisher: String = DesktopRelease.PUBLISHER,
    private val idleTimeoutMs: Long = 15_000L,
) : UpdateSource {
    override suspend fun findUpdate(
        currentVersion: String,
        target: UpdateTarget,
    ): AvailableUpdate? {
        val targets = relays.mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }
        if (targets.isEmpty()) return null

        val releaseFilter =
            Filter(
                kinds = listOf(ReleaseArtifactSetEvent.KIND),
                authors = listOf(publisher),
                tags = mapOf(AppIdTag.TAG_NAME to listOf(appId), PlatformTag.TAG_NAME to listOf(target.platform)),
                limit = 20,
            )
        val releases = client.fetchAll(filters = targets.associateWith { listOf(releaseFilter) }, idleTimeoutMs = idleTimeoutMs)

        // Only the newest release's assets are needed, but which release that is depends on what
        // passes the finder; asking for every listed release's assets keeps it one round trip.
        val assetIds = releases.flatMap { release -> release.tags.mapNotNull(AssetTag::parseId) }.distinct()
        val assets =
            if (assetIds.isEmpty()) {
                emptyList()
            } else {
                val assetFilter = Filter(ids = assetIds, authors = listOf(publisher))
                client.fetchAll(filters = targets.associateWith { listOf(assetFilter) }, idleTimeoutMs = idleTimeoutMs)
            }

        return finder.find(currentVersion, target, releases, assets)
    }
}
