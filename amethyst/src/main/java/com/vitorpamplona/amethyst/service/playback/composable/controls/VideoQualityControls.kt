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
package com.vitorpamplona.amethyst.service.playback.composable.controls

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi

internal fun getVideoTrackGroup(tracks: Tracks): Tracks.Group? = tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_VIDEO && it.length > 0 }

// Finds the track with the smallest short side (min(width, height)) in the given video group.
// Returns null if no track has a positive short side. Used to force lowest-resolution playback
// in feeds to save bandwidth. Skips tracks the device can't decode — ExoPlayer would silently
// reject an override pointing at an unsupported track and fall back to adaptive selection.
@OptIn(UnstableApi::class)
internal fun findLowestResolutionTrackIndex(group: Tracks.Group): Int? {
    var bestIndex: Int? = null
    var bestShortSide = Int.MAX_VALUE
    for (i in 0 until group.length) {
        if (!group.isTrackSupported(i)) continue
        val format = group.getTrackFormat(i)
        val shortSide = minOf(format.width, format.height)
        if (shortSide > 0 && shortSide < bestShortSide) {
            bestShortSide = shortSide
            bestIndex = i
        }
    }
    return bestIndex
}

@OptIn(UnstableApi::class)
internal fun hasVideoOverride(player: Player): Boolean = player.trackSelectionParameters.overrides.any { (key, _) -> key.type == C.TRACK_TYPE_VIDEO }

internal fun clearVideoOverride(player: Player) {
    player.trackSelectionParameters =
        player.trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
            .build()
}

@OptIn(UnstableApi::class)
internal fun selectVideoTrack(
    player: Player,
    group: Tracks.Group,
    trackIndex: Int,
) {
    player.trackSelectionParameters =
        player.trackSelectionParameters
            .buildUpon()
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, trackIndex))
            .build()
}
