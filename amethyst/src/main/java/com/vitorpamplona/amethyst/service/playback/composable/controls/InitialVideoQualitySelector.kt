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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.media3.common.Player
import androidx.media3.common.Tracks

/**
 * Applies a default video quality when tracks become available on the given player.
 *
 * - In feed context (`isFullscreen = false`): locks to the lowest-resolution rendition to save
 *   bandwidth. The user can still manually pick any quality (or "Auto") via the quality button.
 * - In fullscreen context (`isFullscreen = true`): clears any video override so the player uses
 *   adaptive bitrate selection (Auto).
 *
 * The initial selection is applied once per media item. If the user later changes the quality
 * manually, or swaps to a different media item, the new choice wins — we don't reapply for the
 * same media id. Selections intentionally don't persist across composable lifecycles, so opening
 * a feed video in fullscreen starts with Auto and returning to the feed starts with lowest again.
 */
@Composable
fun ApplyInitialVideoQuality(
    player: Player,
    isFullscreen: Boolean,
) {
    // Tracks the media id we've already initialized so we don't fight user overrides after the
    // first application. Scoped to this composable instance so fullscreen <-> feed transitions
    // reset the choice as required (they're separate VideoViewInner instances with separate
    // players, so isFullscreen never flips on a given instance).
    val appliedForMediaId = remember(player) { arrayOf<String?>(null) }

    DisposableEffect(player) {
        val listener =
            object : Player.Listener {
                override fun onTracksChanged(tracks: Tracks) {
                    applyInitialQuality(player, tracks, isFullscreen, appliedForMediaId)
                }
            }

        // Tracks might already be available by the time we attach the listener.
        applyInitialQuality(player, player.currentTracks, isFullscreen, appliedForMediaId)
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
}

private fun applyInitialQuality(
    player: Player,
    tracks: Tracks,
    isFullscreen: Boolean,
    appliedForMediaId: Array<String?>,
) {
    val mediaId = player.currentMediaItem?.mediaId ?: return
    if (appliedForMediaId[0] == mediaId) return

    val videoGroup = getVideoTrackGroup(tracks) ?: return
    // No point forcing a choice when there's only one rendition.
    if (videoGroup.length <= 1) {
        appliedForMediaId[0] = mediaId
        return
    }

    if (isFullscreen) {
        // Ensure adaptive selection is active by removing any pre-existing video override.
        if (hasVideoOverride(player)) {
            clearVideoOverride(player)
        }
    } else {
        val lowestIndex = findLowestResolutionTrackIndex(videoGroup)
        if (lowestIndex != null) {
            selectVideoTrack(player, videoGroup, lowestIndex)
        }
    }
    appliedForMediaId[0] = mediaId
}
