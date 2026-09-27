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
package com.vitorpamplona.quartz.experimental.nip82SoftwareApps.release

import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.asset.SoftwareAssetEvent
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip51Lists.releaseArtifactSet.ReleaseArtifactSetEvent
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * NIP-82 software releases share kind 30063 with NIP-51 release artifact sets, and
 * [ReleaseArtifactSetEvent] now parses both. This used to be a second class for the same
 * kind that `EventFactory` never built.
 */
@Deprecated(
    "Kind 30063 is parsed by ReleaseArtifactSetEvent, which also exposes the NIP-82 fields. Use ReleaseArtifactSetEvent.buildSoftwareRelease to build one.",
    ReplaceWith("ReleaseArtifactSetEvent", "com.vitorpamplona.quartz.nip51Lists.releaseArtifactSet.ReleaseArtifactSetEvent"),
)
typealias SoftwareReleaseEvent = ReleaseArtifactSetEvent

/** The old `SoftwareReleaseEvent.buildDTag`. */
@Deprecated(
    "Use ReleaseArtifactSetEvent.buildSoftwareReleaseDTag.",
    ReplaceWith("ReleaseArtifactSetEvent.buildSoftwareReleaseDTag(appId, version)", "com.vitorpamplona.quartz.nip51Lists.releaseArtifactSet.ReleaseArtifactSetEvent"),
)
fun ReleaseArtifactSetEvent.Companion.buildDTag(
    appId: String,
    version: String,
) = buildSoftwareReleaseDTag(appId, version)

/** The old `SoftwareReleaseEvent.build`: a NIP-82 release, not the NIP-51 set builder. */
@Deprecated(
    "Use ReleaseArtifactSetEvent.buildSoftwareRelease.",
    ReplaceWith(
        "ReleaseArtifactSetEvent.buildSoftwareRelease(appId, version, channel, assets, releaseNotes, createdAt, initializer)",
        "com.vitorpamplona.quartz.nip51Lists.releaseArtifactSet.ReleaseArtifactSetEvent",
    ),
)
fun ReleaseArtifactSetEvent.Companion.build(
    appId: String,
    version: String,
    channel: String,
    assets: List<EventHintBundle<SoftwareAssetEvent>>,
    releaseNotes: String = "",
    createdAt: Long = TimeUtils.now(),
    initializer: TagArrayBuilder<ReleaseArtifactSetEvent>.() -> Unit = {},
) = buildSoftwareRelease(appId, version, channel, assets, releaseNotes, createdAt, initializer)
