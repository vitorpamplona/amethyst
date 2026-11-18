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
package com.vitorpamplona.amethyst.service.playback.composable

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import kotlinx.coroutines.delay

/**
 * The media Studio's trim preview: the picked file at [videoUri] played from [startMs] to [endMs]
 * by a player of its own, over and over, with its sound. Not one of the feed's pooled players: a
 * local file the user is editing needs no cache, session or notification. Released when it leaves.
 */
@OptIn(UnstableApi::class)
@Composable
fun TrimPreviewPlayer(
    videoUri: String,
    startMs: Long,
    endMs: Long,
    onPositionMs: (Long) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val player = remember(context) { ExoPlayer.Builder(context).build().apply { repeatMode = Player.REPEAT_MODE_ONE } }
    DisposableEffect(player) { onDispose { player.release() } }

    LaunchedEffect(player, videoUri, startMs, endMs) {
        // A clip, so the loop wraps at the trim's end and the position starts at its start.
        val clip =
            MediaItem.ClippingConfiguration
                .Builder()
                .setStartPositionMs(startMs)
                .setEndPositionMs(endMs)
                .build()
        player.setMediaItem(
            MediaItem
                .Builder()
                .setUri(videoUri)
                .setClippingConfiguration(clip)
                .build(),
        )
        player.prepare()
        player.playWhenReady = true
    }

    val latestOnPosition by rememberUpdatedState(onPositionMs)
    LaunchedEffect(player, startMs) {
        while (true) {
            latestOnPosition(startMs + player.currentPosition)
            delay(POSITION_STEP_MS)
        }
    }

    // No shutter: the Studio's still of the trim's start shows through until the first frame, not a black box.
    ContentFrame(player = player, modifier = modifier, surfaceType = SURFACE_TYPE_TEXTURE_VIEW, contentScale = ContentScale.Fit, shutter = {})
}

/** How often the playhead moves: smooth enough for a filmstrip a phone wide. */
private const val POSITION_STEP_MS = 50L
