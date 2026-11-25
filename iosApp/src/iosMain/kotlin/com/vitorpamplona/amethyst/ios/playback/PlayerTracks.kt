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
package com.vitorpamplona.amethyst.ios.playback

import com.vitorpamplona.amethyst.commons.playback.CaptionFileParser
import com.vitorpamplona.amethyst.commons.playback.Cue
import com.vitorpamplona.amethyst.commons.ui.playback.player.TextTrack
import com.vitorpamplona.amethyst.commons.ui.playback.player.VideoTrack
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.AVFoundation.AVAssetVariant
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.presentationSize
import platform.AVFoundation.variants

/** Caption files published beside a video, fetched and parsed, and the one the user picked. */
class SideLoadedCaptions(
    private val files: List<File>,
) {
    class File(
        val language: String?,
        val label: String?,
        val cues: List<Cue>,
    )

    private var selected: Int? = files.indices.firstOrNull()

    fun tracks(selectedId: String?): List<TextTrack> =
        files.mapIndexed { index, file ->
            TextTrack(id = "side:$index", language = file.language, label = file.label, selected = index == selected)
        }

    /** Picks the file whose track id is [id]; anything else (a stream track, null) turns these off. */
    fun select(id: String?) {
        selected = id?.takeIf { it.startsWith("side:") }?.removePrefix("side:")?.toIntOrNull()
    }

    fun cuesAt(positionMs: Long): List<String> {
        val file = selected?.let { files.getOrNull(it) } ?: return emptyList()
        return CaptionFileParser.linesAt(file.cues, positionMs)
    }
}

/** The renditions an HLS master playlist offers, read from the asset (iOS 15+). */
@OptIn(ExperimentalForeignApi::class)
internal object HlsVariants {
    fun of(
        asset: AVURLAsset?,
        pinned: VideoTrack?,
    ): List<VideoTrack> {
        val variants = asset?.variants?.mapNotNull { it as? AVAssetVariant } ?: return emptyList()
        val tracks =
            variants
                .mapNotNull { variant ->
                    val attributes = variant.videoAttributes ?: return@mapNotNull null
                    val (w, h) = attributes.presentationSize.useContents { width.toInt() to height.toInt() }
                    if (w <= 0 || h <= 0) return@mapNotNull null
                    val bitrate = (variant.peakBitRate.takeIf { it > 0 } ?: variant.averageBitRate).toInt()
                    val id = "${w}x$h@$bitrate"
                    VideoTrack(id = id, width = w, height = h, bitrate = bitrate, selected = pinned?.id == id)
                }.distinctBy { it.id }
                .sortedByDescending { it.height }
        return if (tracks.size < 2) emptyList() else tracks
    }
}
