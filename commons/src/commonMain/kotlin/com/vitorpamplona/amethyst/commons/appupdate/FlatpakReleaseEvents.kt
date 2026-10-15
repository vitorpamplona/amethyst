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
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.asset.SoftwareAssetEvent
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip51Lists.releaseArtifactSet.ReleaseArtifactSetEvent
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * The NIP-82 events orchard-publish signs for a Flatpak, tag for tag and in its order
 * (`orchard-nostr/src/events.rs` at v0.1.2), so Orchard reads them as before:
 *
 * - per architecture, an asset whose `x` is the image's manifest digest, with the `oci` location
 *   (digest form) and the runtime it needs, and a release `<id>@<version>@<platform>` naming it
 *   (one release per arch, so the two do not replace each other);
 * - the listing, whose `h` tag is the homepage in Orchard's reading (Zapstore reads `h` as its
 *   community, which is why the two listings can never share an app id).
 */
object FlatpakReleaseEvents {
    fun asset(
        image: FlatpakImage,
        createdAt: Long = TimeUtils.now(),
    ) = eventTemplate<SoftwareAssetEvent>(SoftwareAssetEvent.KIND, "", createdAt) {
        add(arrayOf("i", FlatpakRelease.APP_ID))
        add(arrayOf("x", image.digest))
        add(arrayOf("version", image.version))
        add(arrayOf("f", image.platform))
        add(arrayOf("oci", image.location))
        image.runtime?.let { add(arrayOf("runtime", it)) }
    }

    fun release(
        image: FlatpakImage,
        signedAsset: SoftwareAssetEvent,
        notes: String,
        createdAt: Long = TimeUtils.now(),
    ) = eventTemplate<ReleaseArtifactSetEvent>(ReleaseArtifactSetEvent.KIND, notes, createdAt) {
        add(arrayOf("d", "${FlatpakRelease.APP_ID}@${image.version}@${image.platform}"))
        add(arrayOf("i", FlatpakRelease.APP_ID))
        add(arrayOf("version", image.version))
        add(arrayOf("f", image.platform))
        add(arrayOf("c", FlatpakRelease.channelFor(image.version)))
        add(arrayOf("e", signedAsset.id))
    }

    fun listing(
        config: OrchardConfig,
        createdAt: Long = TimeUtils.now(),
    ) = eventTemplate<SoftwareApplicationEvent>(SoftwareApplicationEvent.KIND, config.description.orEmpty(), createdAt) {
        add(arrayOf("d", FlatpakRelease.APP_ID))
        add(arrayOf("name", config.name))
        add(arrayOf("summary", config.summary))
        config.icon?.let { add(arrayOf("icon", it)) }
        config.license?.let { add(arrayOf("license", it)) }
        config.repository?.let { add(arrayOf("repository", it)) }
        config.homepage?.let { add(arrayOf("h", it)) }
        config.categories.forEach { add(arrayOf("t", it)) }
        config.platforms.forEach { add(arrayOf("f", it)) }
    }
}
