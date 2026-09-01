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
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi

internal fun getVideoTrackGroup(tracks: Tracks): Tracks.Group? = tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_VIDEO && it.length > 0 }

/**
 * Constrains adaptive selection to the area the player is actually drawn in.
 *
 * ExoPlayer already filters renditions by a viewport, but its default viewport is the **physical
 * display** (`TrackSelectionParameters.Builder.init` sets
 * `isViewportSizeLimitedByPhysicalDisplaySize`), so every player — a thumbnail in a note, a
 * full-bleed short, a PiP window — is told it has the whole screen to fill and picks whatever
 * bandwidth allows up to display resolution. Handing it the measured size instead is the whole
 * quality policy: a full-width video gets the top of the ladder, a small one gets the rung that
 * matches its pixels, and both still adapt to the connection underneath that ceiling.
 *
 * Safe by construction, which is why this uses `setViewportSize` rather than `setMaxVideoSize`:
 * DefaultTrackSelector derives its retain threshold from an actual rendition ("the smallest to
 * exceed the maximum size at which it can be displayed within the viewport") and leaves the group
 * untouched when no rendition covers the viewport, so a tiny player can never filter every track
 * away. A manual pick from the quality menu still wins — overrides are re-applied after
 * constraint-based selection runs.
 */
internal fun Modifier.constrainVideoQualityToViewport(player: Player): Modifier = onSizeChanged { applyViewportConstraint(player, it.width, it.height) }

// Runs from onSizeChanged on the player's application looper (main thread), which is where
// trackSelectionParameters must be written. Writing them re-runs track selection and, for a
// pooled controller, crosses an IPC boundary, so [needsViewportUpdate] keeps a no-op layout pass
// from churning either.
internal fun applyViewportConstraint(
    player: Player,
    widthPx: Int,
    heightPx: Int,
) {
    val current = player.trackSelectionParameters
    if (!needsViewportUpdate(current.viewportWidth, current.viewportHeight, widthPx, heightPx)) return

    player.trackSelectionParameters =
        current
            .buildUpon()
            // orientationMayChange mirrors media3's own default: it keeps a landscape rendition in
            // a portrait box (a letterboxed live stream) from being judged against the short edge.
            .setViewportSize(widthPx, heightPx, true)
            .build()
}

// A zero dimension means the player has not been laid out yet — leaving the previous constraint
// in place is better than declaring a viewport nothing can fill.
internal fun needsViewportUpdate(
    currentWidth: Int,
    currentHeight: Int,
    newWidth: Int,
    newHeight: Int,
): Boolean = newWidth > 0 && newHeight > 0 && (currentWidth != newWidth || currentHeight != newHeight)

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
