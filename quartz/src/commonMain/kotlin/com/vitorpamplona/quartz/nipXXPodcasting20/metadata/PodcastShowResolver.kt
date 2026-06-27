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
package com.vitorpamplona.quartz.nipXXPodcasting20.metadata

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip78AppData.AppSpecificDataEvent
import com.vitorpamplona.quartz.nipF4Podcasts.metadata.PodcastMetadataEvent
import com.vitorpamplona.quartz.podcasts.PodcastShow

/**
 * Cheap type/`d`-tag gate for whether [event] represents a podcast show — used by feeds to decide
 * inclusion without parsing the Podcasting-2.0 JSON content. Matches NIP-F4 `kind:10154` and the
 * Podcasting-2.0 `kind:30078` app-data event with `d="podcast-metadata"`.
 */
fun isPodcastShowEvent(event: Event?): Boolean =
    event is PodcastMetadataEvent ||
        (event is AppSpecificDataEvent && event.dTag() == Podcasting20PodcastMetadata.PODCAST_METADATA_D_TAG)

/**
 * Adapts [event] to the spec-neutral [PodcastShow], or returns null if it is not a podcast show
 * (or its Podcasting-2.0 JSON content fails to parse). NIP-F4 metadata events implement
 * [PodcastShow] directly; Podcasting-2.0 app-data events are wrapped via
 * [Podcasting20PodcastMetadata.parse].
 */
fun resolvePodcastShow(event: Event?): PodcastShow? =
    when (event) {
        is PodcastMetadataEvent -> event
        is AppSpecificDataEvent -> Podcasting20PodcastMetadata.parse(event)
        else -> null
    }
