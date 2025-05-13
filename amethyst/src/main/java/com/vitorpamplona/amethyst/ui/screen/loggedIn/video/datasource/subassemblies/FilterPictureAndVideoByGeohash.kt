/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.video.datasource.subassemblies

import com.vitorpamplona.amethyst.ui.screen.loggedIn.video.datasource.SUPPORTED_VIDEO_FEED_MIME_TYPES
import com.vitorpamplona.ammolite.relays.FeedType
import com.vitorpamplona.ammolite.relays.TypedFilter
import com.vitorpamplona.ammolite.relays.filters.EOSETime
import com.vitorpamplona.ammolite.relays.filters.SincePerRelayFilter
import com.vitorpamplona.quartz.experimental.nip95.header.FileStorageHeaderEvent
import com.vitorpamplona.quartz.nip68Picture.PictureEvent
import com.vitorpamplona.quartz.nip71Video.VideoHorizontalEvent
import com.vitorpamplona.quartz.nip71Video.VideoVerticalEvent
import com.vitorpamplona.quartz.nip94FileMetadata.FileHeaderEvent

fun filterPictureAndVideoByGeohash(
    hashToLoad: Set<String>?,
    since: Map<String, EOSETime>?,
): List<TypedFilter> {
    if (hashToLoad == null || hashToLoad.isEmpty()) return emptyList()

    val geoHashes = hashToLoad.toList()

    return listOf(
        TypedFilter(
            types = setOf(FeedType.GLOBAL),
            filter =
                SincePerRelayFilter(
                    kinds = listOf(PictureEvent.KIND, VideoHorizontalEvent.KIND, VideoVerticalEvent.KIND),
                    tags = mapOf("g" to geoHashes),
                    limit = 100,
                    since = since,
                ),
        ),
        TypedFilter(
            types = setOf(FeedType.GLOBAL),
            filter =
                SincePerRelayFilter(
                    kinds = listOf(FileHeaderEvent.KIND, FileStorageHeaderEvent.KIND),
                    tags =
                        mapOf(
                            "g" to geoHashes,
                            "m" to SUPPORTED_VIDEO_FEED_MIME_TYPES,
                        ),
                    limit = 100,
                    since = since,
                ),
        ),
    )
}
