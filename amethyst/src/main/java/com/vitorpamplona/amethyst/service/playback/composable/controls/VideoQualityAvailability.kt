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

import androidx.media3.common.C
import androidx.media3.common.Tracks

/**
 * Checks if the current tracks contain multiple video renditions (HLS/DASH adaptive streams).
 * Returns false for single-rendition MP4s to avoid showing a useless quality menu.
 */
fun hasMultipleRenditions(tracks: Tracks): Boolean {
    val videoGroup = getVideoTrackGroup(tracks) ?: return false
    return videoGroup.length > 1
}

/**
 * Returns the first video track group from the tracks, or null if none exists.
 */
fun getVideoTrackGroup(tracks: Tracks): Tracks.Group? =
    tracks.groups.firstOrNull { group ->
        group.type == C.TRACK_TYPE_VIDEO && group.length > 0
    }
