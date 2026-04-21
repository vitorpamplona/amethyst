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

import androidx.annotation.MainThread
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import com.vitorpamplona.quartz.utils.Log

/**
 * Which HLS rendition to pick automatically the first time tracks become available for a media
 * item. Expressed as an explicit policy instead of a `Boolean` so call sites must commit to one
 * value at construction and any future dynamic-state caller is forced to key the effect on it.
 */
enum class VideoQualityPolicy {
    /** Lock to the lowest-resolution rendition (feed and PiP: save bandwidth). */
    LOWEST,

    /** Clear any video override so the player uses adaptive bitrate (fullscreen). */
    AUTO,
}

/**
 * Applies a default video quality when tracks become available on the given player.
 *
 * The initial selection is applied once per media item. If the user later changes the quality
 * manually, or swaps to a different media item, the new choice wins — we don't reapply for the
 * same media id. Selections intentionally don't persist across composable lifecycles, so opening
 * a feed video in fullscreen starts with [VideoQualityPolicy.AUTO] and returning to the feed
 * starts with [VideoQualityPolicy.LOWEST] again.
 */
@Composable
fun ApplyInitialVideoQuality(
    player: Player,
    policy: VideoQualityPolicy,
) {
    // Tracks the media id we've already initialized so we don't fight user overrides after the
    // first application.
    val appliedForMediaId = remember(player) { mutableStateOf<String?>(null) }

    DisposableEffect(player, policy) {
        // Re-arm the guard whenever the player or the policy changes so a new policy gets a
        // chance to apply even if the same media id has already been handled under the old one.
        appliedForMediaId.value = null

        val listener =
            object : Player.Listener {
                override fun onTracksChanged(tracks: Tracks) {
                    applyInitialQuality(player, tracks, policy, appliedForMediaId)
                }
            }

        // Tracks might already be available by the time we attach the listener.
        applyInitialQuality(player, player.currentTracks, policy, appliedForMediaId)
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
}

// Invoked from Player.Listener callbacks and DisposableEffect bodies, both of which run on
// the player's application looper (main thread for ExoPlayer). The body mutates Compose state
// and trackSelectionParameters; both are main-thread-only.
@MainThread
private fun applyInitialQuality(
    player: Player,
    tracks: Tracks,
    policy: VideoQualityPolicy,
    appliedForMediaId: MutableState<String?>,
) {
    val mediaId = player.currentMediaItem?.mediaId ?: return
    if (appliedForMediaId.value == mediaId) return

    val videoGroup = getVideoTrackGroup(tracks) ?: return
    // No point forcing a choice when there's only one rendition, and no future update will
    // change that for this media id, so mark it as settled.
    if (videoGroup.length <= 1) {
        Log.d("VideoQuality") {
            val f = videoGroup.getTrackFormat(0)
            "policy=$policy mediaId=$mediaId SINGLE rendition ${f.width}x${f.height} (no choice)"
        }
        appliedForMediaId.value = mediaId
        return
    }

    val renditions =
        (0 until videoGroup.length).joinToString(", ") {
            val f = videoGroup.getTrackFormat(it)
            "${f.width}x${f.height}"
        }

    when (policy) {
        VideoQualityPolicy.AUTO -> {
            if (hasVideoOverride(player)) clearVideoOverride(player)
            Log.d("VideoQuality") {
                "policy=AUTO mediaId=$mediaId cleared override (from ${videoGroup.length} renditions: [$renditions])"
            }
            appliedForMediaId.value = mediaId
        }

        VideoQualityPolicy.LOWEST -> {
            // If no supported track has a positive short side yet, leave the guard unset so we
            // retry on the next onTracksChanged when real video dimensions arrive.
            val lowestIndex = findLowestResolutionTrackIndex(videoGroup) ?: return
            selectVideoTrack(player, videoGroup, lowestIndex)
            Log.d("VideoQuality") {
                val f = videoGroup.getTrackFormat(lowestIndex)
                "policy=LOWEST mediaId=$mediaId selected=${f.width}x${f.height} (from ${videoGroup.length} renditions: [$renditions])"
            }
            appliedForMediaId.value = mediaId
        }
    }
}
