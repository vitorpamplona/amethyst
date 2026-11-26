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
package com.vitorpamplona.amethyst.commons.ui.playback.controls

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import com.vitorpamplona.amethyst.commons.ui.playback.player.AmethystPlayer
import com.vitorpamplona.amethyst.commons.ui.playback.player.PLAYBACK_DIAG_TAG
import com.vitorpamplona.amethyst.commons.ui.playback.player.VideoTrack
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.LogLevel
import kotlin.math.ceil

// The rendition that describes the video being played, for the Cast failure messages. Uses the
// highest-resolution track in the group: that is the one an adaptive stream will climb to, so it is
// the one a receiver with limited codec or resolution support will choke on.
fun highestResolutionTrack(tracks: List<VideoTrack>): VideoTrack? {
    var best: VideoTrack? = null
    for (format in tracks) {
        if (best == null || format.width.toLong() * format.height > best.width.toLong() * best.height) {
            best = format
        }
    }
    return best
}

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
fun Modifier.constrainVideoQualityToViewport(
    player: AmethystPlayer,
    maxShortSidePx: () -> Int = { 0 },
): Modifier =
    onSizeChanged {
        // Evaluated at measure time, not at composition: a caller whose window is still resizing
        // (PiP) needs the answer for *this* layout pass. Always pushes something — a caller that
        // wants to distrust its own measurement caps it rather than skipping, so a pooled player
        // can never keep a viewport left over from the view that had it last.
        val (width, height) = clampViewportShortSide(it.width, it.height, maxShortSidePx())
        applyViewportConstraint(player, width, height)
    }

/**
 * Short side the viewport is capped to on a metered connection.
 *
 * Sizing the ladder to the player is the right default on wifi, but on mobile data it would hand a
 * full-width card most of the ladder with nothing holding it back — the app's other lever is the
 * autoplay `ConnectivityType` gate, which decides *whether* to play, not how much to pull. 480 on
 * the short side keeps a card watchable while staying near the rung the old fixed-lowest policy
 * would have picked.
 */
const val METERED_MAX_SHORT_SIDE_PX = 480

/**
 * Ceiling the PiP player uses until its window has actually shrunk.
 *
 * `processIntentForPiP` calls `enterPictureInPictureMode` from composition, so the first layout
 * pass can measure the activity at full screen. Capping rather than skipping the push means the
 * selector never sees that full-screen size, and never keeps a stale one either when PiP is never
 * entered at all — the window is a few inches wide, so nothing above this is ever wanted here.
 */
const val PIP_PRESHRINK_MAX_SHORT_SIDE_PX = 480

/**
 * Scales a measured player size down until its short side fits [maxShortSidePx], preserving aspect
 * so the viewport still describes the shape of the player and not just its area. Sizes already
 * within the cap, and a non-positive cap, pass through untouched.
 */
fun clampViewportShortSide(
    widthPx: Int,
    heightPx: Int,
    maxShortSidePx: Int,
): Pair<Int, Int> {
    val shortSide = minOf(widthPx, heightPx)
    if (maxShortSidePx <= 0 || shortSide <= 0 || shortSide <= maxShortSidePx) return widthPx to heightPx

    val scale = maxShortSidePx.toDouble() / shortSide
    // Round up so the cap is never undershot into a lower rung by a rounding artifact.
    return ceil(widthPx * scale).toInt() to ceil(heightPx * scale).toInt()
}

// Writing the viewport re-runs track selection and, for a pooled controller, crosses an IPC
// boundary; the player skips a push of the size it already has (see [needsViewportUpdate]).
fun applyViewportConstraint(
    player: AmethystPlayer,
    widthPx: Int,
    heightPx: Int,
) {
    player.constrainToViewport(widthPx, heightPx)
}

/**
 * Logcat tag for the rendition trace: every viewport push, and the rung actually selected against
 * the ladder that was on offer. Replaces the per-media-item trace the old fixed-policy selector
 * emitted — without it, "Amethyst is eating my data" is not a debuggable report.
 *
 * ```
 * adb logcat -s VideoQuality
 * ```
 */
const val VIDEO_QUALITY_TAG = "VideoQuality"

/**
 * Traces which rendition adaptive selection landed on, against the full ladder the manifest
 * offered.
 *
 * The listener is registered only when the trace can actually be emitted. `Log.minLevel` is above
 * `DEBUG` in every variant by default — `INFO` for debug and benchmark builds, `WARN` for release
 * (see [PLAYBACK_DIAG_TAG]) — so this costs nothing until `Amethyst.VERBOSE_LOGS` is turned on,
 * and every build keeps the "no listener per player" property that dropping the old selector
 * bought.
 */
@Composable
fun LogVideoQualitySelection(player: AmethystPlayer) {
    if (Log.minLevel > LogLevel.DEBUG) return

    DisposableEffect(player) {
        val listener =
            object : AmethystPlayer.Listener {
                override fun onTracksChanged() {
                    val group = player.videoTracks
                    if (group.isEmpty()) return
                    val ladder =
                        group.joinToString(", ") { f ->
                            "${f.width}x${f.height}${if (f.selected) "*" else ""}"
                        }
                    Log.d(VIDEO_QUALITY_TAG) {
                        "selected(*) mediaId=${player.mediaId} of $ladder"
                    }
                }
            }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
}

// A zero dimension means the player has not been laid out yet — leaving the previous constraint
// in place is better than declaring a viewport nothing can fill.
fun needsViewportUpdate(
    currentWidth: Int,
    currentHeight: Int,
    newWidth: Int,
    newHeight: Int,
): Boolean = newWidth > 0 && newHeight > 0 && (currentWidth != newWidth || currentHeight != newHeight)
