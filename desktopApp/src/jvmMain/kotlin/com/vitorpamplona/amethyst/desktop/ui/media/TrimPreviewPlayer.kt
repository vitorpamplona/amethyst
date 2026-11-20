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
package com.vitorpamplona.amethyst.desktop.ui.media

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.error_video_playback_failed
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.desktop.service.media.GlobalMediaPlayer
import com.vitorpamplona.amethyst.desktop.service.media.MediaPlaybackState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.File
import java.net.URI

/**
 * The media Studio's trim preview on the shared video engine: the picked file at [videoUri] from
 * [startMs], sent back there whenever it reaches [endMs] (or the file's end), with its sound. The
 * engine is the one the feed uses, so the Studio takes it like a card the user pressed play on,
 * and stops it when it leaves. The sound is on for this video only: the feed's mute default is
 * put back afterwards.
 */
@Composable
fun DesktopTrimPreviewPlayer(
    videoUri: String,
    startMs: Long,
    endMs: Long,
    durationMs: Long,
    onPositionMs: (Long) -> Unit,
    modifier: Modifier,
) {
    // The engine opens a path as it opens a decrypted file's.
    val url = remember(videoUri) { if (videoUri.startsWith("file:")) File(URI(videoUri)).absolutePath else videoUri }
    // Marks the engine's video as this preview, which the now-playing bar leaves out.
    val owner = remember { StudioPreviewOwner() }

    DisposableEffect(url) {
        val wasMuted = GlobalMediaPlayer.defaultMuted
        GlobalMediaPlayer.setDefaultMute(false)
        val from = if (durationMs > 0) startMs.toFloat() / durationMs else 0f
        GlobalMediaPlayer.playVideo(url, seekPosition = from, owner = owner, chosen = true)
        onDispose {
            GlobalMediaPlayer.releaseVideoSurface(owner)
            if (GlobalMediaPlayer.videoState.value.url == url) GlobalMediaPlayer.stopVideo()
            GlobalMediaPlayer.setDefaultMute(wasMuted)
        }
    }

    val latestOnPosition by rememberUpdatedState(onPositionMs)
    LaunchedEffect(url, startMs, endMs, durationMs) {
        GlobalMediaPlayer.videoState.collect { state ->
            if (state.url != url || state.errorReason != null) return@collect
            val total = state.duration.takeIf { it > 0L } ?: durationMs
            if (total <= 0L) return@collect
            val at = state.currentTime
            latestOnPosition(at)
            val ended = !state.isPlaying && !state.isBuffering && state.position >= GlobalMediaPlayer.ENDED_POSITION
            when {
                // Past the kept part, or before it (the engine restored where this file was last time).
                state.isPlaying && (at >= endMs || at < startMs - SLACK_MS) -> GlobalMediaPlayer.seekVideo(startMs.toFloat() / total)
                // A trim that keeps the end runs into the file's end, where the engine stops.
                ended -> GlobalMediaPlayer.playVideo(url, seekPosition = startMs.toFloat() / total, owner = owner, chosen = true)
            }
        }
    }

    val surfaceOwner by GlobalMediaPlayer.videoSurfaceOwner.collectAsState()

    // Only whether the engine has this file and failed on it: the position ticks many times a second.
    fun isLoaded(state: MediaPlaybackState) = state.url == url

    fun hasFailed(state: MediaPlaybackState) = state.url == url && state.errorReason != null
    val videoState = GlobalMediaPlayer.videoState
    val loaded by remember(url) { videoState.map(::isLoaded).distinctUntilChanged() }.collectAsState(isLoaded(videoState.value))
    val failed by remember(url) { videoState.map(::hasFailed).distinctUntilChanged() }.collectAsState(hasFailed(videoState.value))
    Box(modifier) {
        val engine = GlobalMediaPlayer.activeVideoPlayerState
        if (!failed && engine != null && loaded && (surfaceOwner == null || surfaceOwner === owner)) {
            VideoSurface(playerState = engine, url = url, modifier = Modifier.fillMaxSize())
        }
        // No engine here (Linux without GStreamer reports "Video playback unavailable") or one that
        // can't read the file: say so over the Studio's still, rather than a Play that seems to do nothing.
        if (failed) {
            Text(
                stringRes(Res.string.error_video_playback_failed),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

/** The owner of the engine's video while the media Studio previews a trim: never "now playing". */
class StudioPreviewOwner

// How far before the kept part the engine may report without being sent back: a seek lands on the
// keyframe before the asked moment.
private const val SLACK_MS = 1_000L
